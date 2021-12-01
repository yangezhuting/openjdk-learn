/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea, Bill Scherer, and Michael Scott with
 * assistance from members of JCP JSR-166 Expert Group and released to
 * the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.*;
import java.util.Spliterator;
import java.util.Spliterators;

/**
 * A {@linkplain BlockingQueue blocking queue} in which each insert
 * operation must wait for a corresponding remove operation by another
 * thread, and vice versa.  A synchronous queue does not have any
 * internal capacity, not even a capacity of one.  You cannot
 * {@code peek} at a synchronous queue because an element is only
 * present when you try to remove it; you cannot insert an element
 * (using any method) unless another thread is trying to remove it;
 * you cannot iterate as there is nothing to iterate.  The
 * <em>head</em> of the queue is the element that the first queued
 * inserting thread is trying to add to the queue; if there is no such
 * queued thread then no element is available for removal and
 * {@code poll()} will return {@code null}.  For purposes of other
 * {@code Collection} methods (for example {@code contains}), a
 * {@code SynchronousQueue} acts as an empty collection.  This queue
 * does not permit {@code null} elements.
 *
 * <p>Synchronous queues are similar to rendezvous channels used in
 * CSP and Ada. They are well suited for handoff designs, in which an
 * object running in one thread must sync up with an object running
 * in another thread in order to hand it some information, event, or
 * task.
 *
 * <p>This class supports an optional fairness policy for ordering
 * waiting producer and consumer threads.  By default, this ordering
 * is not guaranteed. However, a queue constructed with fairness set
 * to {@code true} grants threads access in FIFO order.
 *
 * <p>This class and its iterator implement all of the
 * <em>optional</em> methods of the {@link Collection} and {@link
 * Iterator} interfaces.
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @since 1.5
 * @author Doug Lea and Bill Scherer and Michael Scott
 * @param <E> the type of elements held in this collection
 */
// 同步阻塞队列，每一次put操作，必须等待其他线性的take操作，反之亦然；队列不存储任何元素，也就没有容
// 量概念。对应的|peek,contains,clear...|等方法是无效的。队列有公平(TransferQueue FIFO)与非
// 公平(TransferStack LIFO 默认)两种策略模式
// 注：虽然在多线程场景中，我们可以同时添加多个生产者或者消费者节点（队列中只能有一个类型的节点）到队列
// 中，但本质上，消费者和生产者还是遵循一一"匹配消耗"（一个生产者数据被一个消费者捕获消费）的
// 注：如果你想到了GOLANG中的|chan|机制就对了！他们工作模式、使用场景都很相似。不过GOLANG中还可指定
// 数据投递转移（线程间同步）的并发个数，而|SynchronousQueue|只有一个，类似"停等"模式
public class SynchronousQueue<E> extends AbstractQueue<E>
    implements BlockingQueue<E>, java.io.Serializable {
    private static final long serialVersionUID = -3223113410248163686L;

    /*
     * This class implements extensions of the dual stack and dual
     * queue algorithms described in "Nonblocking Concurrent Objects
     * with Condition Synchronization", by W. N. Scherer III and
     * M. L. Scott.  18th Annual Conf. on Distributed Computing,
     * Oct. 2004 (see also
     * http://www.cs.rochester.edu/u/scott/synchronization/pseudocode/duals.html).
     * The (Lifo) stack is used for non-fair mode, and the (Fifo)
     * queue for fair mode. The performance of the two is generally
     * similar. Fifo usually supports higher throughput under
     * contention but Lifo maintains higher thread locality in common
     * applications.
     *
     * A dual queue (and similarly stack) is one that at any given
     * time either holds "data" -- items provided by put operations,
     * or "requests" -- slots representing take operations, or is
     * empty. A call to "fulfill" (i.e., a call requesting an item
     * from a queue holding data or vice versa) dequeues a
     * complementary node.  The most interesting feature of these
     * queues is that any operation can figure out which mode the
     * queue is in, and act accordingly without needing locks.
     *
     * Both the queue and stack extend abstract class Transferer
     * defining the single method transfer that does a put or a
     * take. These are unified into a single method because in dual
     * data structures, the put and take operations are symmetrical,
     * so nearly all code can be combined. The resulting transfer
     * methods are on the long side, but are easier to follow than
     * they would be if broken up into nearly-duplicated parts.
     *
     * The queue and stack data structures share many conceptual
     * similarities but very few concrete details. For simplicity,
     * they are kept distinct so that they can later evolve
     * separately.
     *
     * The algorithms here differ from the versions in the above paper
     * in extending them for use in synchronous queues, as well as
     * dealing with cancellation. The main differences include:
     *
     *  1. The original algorithms used bit-marked pointers, but
     *     the ones here use mode bits in nodes, leading to a number
     *     of further adaptations.
     *  2. SynchronousQueues must block threads waiting to become
     *     fulfilled.
     *  3. Support for cancellation via timeout and interrupts,
     *     including cleaning out cancelled nodes/threads
     *     from lists to avoid garbage retention and memory depletion.
     *
     * Blocking is mainly accomplished using LockSupport park/unpark,
     * except that nodes that appear to be the next ones to become
     * fulfilled first spin a bit (on multiprocessors only). On very
     * busy synchronous queues, spinning can dramatically improve
     * throughput. And on less busy ones, the amount of spinning is
     * small enough not to be noticeable.
     *
     * Cleaning is done in different ways in queues vs stacks.  For
     * queues, we can almost always remove a node immediately in O(1)
     * time (modulo retries for consistency checks) when it is
     * cancelled. But if it may be pinned as the current tail, it must
     * wait until some subsequent cancellation. For stacks, we need a
     * potentially O(n) traversal to be sure that we can remove the
     * node, but this can run concurrently with other threads
     * accessing the stack.
     *
     * While garbage collection takes care of most node reclamation
     * issues that otherwise complicate nonblocking algorithms, care
     * is taken to "forget" references to data, other nodes, and
     * threads that might be held on to long-term by blocked
     * threads. In cases where setting to null would otherwise
     * conflict with main algorithms, this is done by changing a
     * node's link to now point to the node itself. This doesn't arise
     * much for Stack nodes (because blocked threads do not hang on to
     * old head pointers), but references in Queue nodes must be
     * aggressively forgotten to avoid reachability of everything any
     * node has ever referred to since arrival.
     */

    /**
     * Shared internal API for dual stacks and queues.
     */
    abstract static class Transferer<E> {
        /**
         * Performs a put or take.
         *
         * @param e if non-null, the item to be handed to a consumer;
         *          if null, requests that transfer return an item
         *          offered by producer.
         * @param timed if this operation should timeout
         * @param nanos the timeout, in nanoseconds
         * @return if non-null, the item provided or received; if null,
         *         the operation failed due to timeout or interrupt --
         *         the caller can distinguish which of these occurred
         *         by checking Thread.interrupted.
         */
        abstract E transfer(E e, boolean timed, long nanos);
    }

    /** The number of CPUs, for spin control */
    static final int NCPUS = Runtime.getRuntime().availableProcessors();

    /**
     * The number of times to spin before blocking in timed waits.
     * The value is empirically derived -- it works well across a
     * variety of processors and OSes. Empirically, the best value
     * seems not to vary with number of CPUs (beyond 2) so is just
     * a constant.
     */
    // 单核自旋无意义
    static final int maxTimedSpins = (NCPUS < 2) ? 0 : 32;

    /**
     * The number of times to spin before blocking in untimed waits.
     * This is greater than timed value because untimed waits spin
     * faster since they don't need to check times on each spin.
     */
    // 不限时阻塞时，最大自旋计数器
    static final int maxUntimedSpins = maxTimedSpins * 16;

    /**
     * The number of nanoseconds for which it is faster to spin
     * rather than to use timed park. A rough estimate suffices.
     */
    static final long spinForTimeoutThreshold = 1000L;

    /** Dual stack */
    // 注：一个LIFO队列，头部生产新增（头插），头部匹配消耗
    // 精华：将一个节点的|match|字段指向相邻的|FULFILLING|类型的前驱节点，表示该节点被匹配
    // 精华：不同于|TransferQueue|队列中的"匹配消耗"算法，|TransferStack|采用了两个相邻
    // 的不同类型的节点相互抵消法，来进行生产者与消费者的一一匹配。这使得匹配算法很非常灵活，甚
    // 至可以让另一个线程辅助执行匹配算法。当然主要还是：在栈上设计一个匹配算法较为方便
    static final class TransferStack<E> extends Transferer<E> {
        /*
         * This extends Scherer-Scott dual stack algorithm, differing,
         * among other ways, by using "covering" nodes rather than
         * bit-marked pointers: Fulfilling operations push on marker
         * nodes (with FULFILLING bit set in mode) to reserve a spot
         * to match a waiting node.
         */

        /* Modes for SNodes, ORed together in node fields */
        /** Node represents an unfulfilled consumer */
        // 消费者
        static final int REQUEST    = 0;
        /** Node represents an unfulfilled producer */
        // 生产者
        static final int DATA       = 1;
        /** Node is fulfilling another unfulfilled DATA or REQUEST */
        // 匹配另一个生产者或消费者
        // 注：将一个节点的|match|字段指向相邻的|FULFILLING|前驱节点，表明该节点已被匹配
        static final int FULFILLING = 2;

        /** Returns true if m has fulfilling bit set. */
        static boolean isFulfilling(int m) { return (m & FULFILLING) != 0; }

        /** Node class for TransferStacks. */
        static final class SNode {
            volatile SNode next;        // next node in stack
            volatile SNode match;       // the node matched to this
            volatile Thread waiter;     // to control park/unpark
            Object item;                // data; or null for REQUESTs
            int mode;
            // Note: item and mode fields don't need to be volatile
            // since they are always written before, and read after,
            // other volatile/atomic operations.

            SNode(Object item) {
                this.item = item;
            }

            boolean casNext(SNode cmp, SNode val) {
                return cmp == next &&
                    UNSAFE.compareAndSwapObject(this, nextOffset, cmp, val);
            }

            /**
             * Tries to match node s to this node, if so, waking up thread.
             * Fulfillers call tryMatch to identify their waiters.
             * Waiters block until they have been matched.
             *
             * @param s the node to match
             * @return true if successfully matched to s
             */
            boolean tryMatch(SNode s) {
                if (match == null &&
                    UNSAFE.compareAndSwapObject(this, matchOffset, null, s)) {
                    Thread w = waiter;
                    if (w != null) {    // waiters need at most one unpark
                        waiter = null;
                        LockSupport.unpark(w);
                    }
                    return true;
                }
                return match == s;
            }

            /**
             * Tries to cancel a wait by matching node to itself.
             */
            void tryCancel() {
                UNSAFE.compareAndSwapObject(this, matchOffset, null, this);
            }

            boolean isCancelled() {
                return match == this;
            }

            // Unsafe mechanics
            private static final sun.misc.Unsafe UNSAFE;
            private static final long matchOffset;
            private static final long nextOffset;

            static {
                try {
                    UNSAFE = sun.misc.Unsafe.getUnsafe();
                    Class<?> k = SNode.class;
                    matchOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("match"));
                    nextOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("next"));
                } catch (Exception e) {
                    throw new Error(e);
                }
            }
        }

        /** The head (top) of the stack */
        volatile SNode head;

        boolean casHead(SNode h, SNode nh) {
            return h == head &&
                UNSAFE.compareAndSwapObject(this, headOffset, h, nh);
        }

        /**
         * Creates or resets fields of a node. Called only from transfer
         * where the node to push on stack is lazily created and
         * reused when possible to help reduce intervals between reads
         * and CASes of head and to avoid surges of garbage when CASes
         * to push nodes fail due to contention.
         */
        static SNode snode(SNode s, Object e, SNode next, int mode) {
            if (s == null) s = new SNode(e);
            s.mode = mode;
            s.next = next;
            return s;
        }

        /**
         * Puts or takes an item.
         */
        // 同步的生产或消费一个元素。即，每一次|put|操作，必须等待其他线程的|take|操作，反之亦然
        // 典型场景：先添加一个消费者，在超时时间前，添加一个生产者（任务）就会被该消费者"匹配消耗"
        // 注：默认的|offer,poll|方法，调用|transfer|时，其中|timed==true&&nanos==0|，两
        // 者都会立即返回，若配对使用，意义不大。必须有一个消费者在阻塞等待一个生产者，反之亦然
        // 注：将当前节点的|match|字段指向自身，以表明该节点已被取消、待删除
        // 注：将当前节点的|match|字段指向相邻的|FULFILLING|前驱节点，以表明该节点已被匹配
        @SuppressWarnings("unchecked")
        E transfer(E e, boolean timed, long nanos) {
            /*
             * Basic algorithm is to loop trying one of three actions:
             *
             * 1. If apparently empty or already containing nodes of same
             *    mode, try to push node on stack and wait for a match,
             *    returning it, or null if cancelled.
             *
             * 2. If apparently containing node of complementary mode,
             *    try to push a fulfilling node on to stack, match
             *    with corresponding waiting node, pop both from
             *    stack, and return matched item. The matching or
             *    unlinking might not actually be necessary because of
             *    other threads performing action 3:
             *
             * 3. If top of stack already holds another fulfilling node,
             *    help it out by doing its match and/or pop
             *    operations, and then continue. The code for helping
             *    is essentially the same as for fulfilling, except
             *    that it doesn't return the item.
             */

            SNode s = null; // constructed/reused as needed
            int mode = (e == null) ? REQUEST : DATA;

            // 同步的生产或消费一个元素。即，每一次|put|操作，必须等待其他线程的|take|操作，反之亦然
            // 典型场景：先添加一个消费者，在超时时间前，添加一个生产者（任务）就会被该消费者"匹配消耗"
            for (;;) {
                SNode h = head;

                // 链表为空，或者队列中有多个相同类型的节点
                // 注：在多线程场景中，就会存在多个生产者或消费者，但链表中只会有多个相同类型的节点。因
                // 为不同类型的节点会相互"匹配消耗"
                if (h == null || h.mode == mode) {  // empty or same-mode
                    if (timed && nanos <= 0) {      // can't wait
                        // 若使用了超时特性，但|nanos|时限已到，立即返回null
                        // 注：默认的|offer(), poll()|方法，|timed==true&&nanos==0|，都会立即返回，配对使用意义不大
                        // 必须至少有一个消费者在阻塞等待一个生产者，反之亦可
                        if (h != null && h.isCancelled())   // 其他线程修改了头节点，后又被取消了
                            casHead(h, h.next);     // pop cancelled node
                        else
                            return null;
                    } else if (casHead(h, s = snode(s, e, h, mode))) {  // 头插法新增节点
                        // 阻塞、等待|s|节点被"匹配消耗"
                        SNode m = awaitFulfill(s, timed, nanos);

                        // 待"匹配消耗"的|s|节点数据字段指向了自身，说明|s|节点已被取消
                        if (m == s) {               // wait was cancelled
                            clean(s);   // 删除被取消的节点
                            return null;
                        }

                        // 待"匹配消耗"的节点是头节点，更新|head|引用指针
                        if ((h = head) != null && h.next == s)
                            casHead(h, s.next);     // help s's fulfiller

                        // 1.当一个生产者节点，需要等待消费者来匹配消耗时，返回等待者节点（生产者）提供的数据
                        // 2.当一个消费者节点，需要等待生产者来匹配消耗时，返回被匹配节点（生产者）提供的数据
                        return (E) ((mode == REQUEST) ? m.item : s.item);
                    }
                } else if (!isFulfilling(h.mode)) { // try to fulfill
                    // 队列头部，暂不存在"主动匹配"的节点，尝试创建，用此去匹配队列中另一类型的节点

                    // 头节点已被取消，删除头节点
                    if (h.isCancelled())            // already cancelled
                        casHead(h, h.next);         // pop and retry

                    else if (casHead(h, s=snode(s, e, h, FULFILLING|mode))) {   // 先创建一个节点，插入头部
                        for (;;) { // loop until matched or waiters disappear
                            // 待匹配的节点
                            SNode m = s.next;       // m is s's match
                            // 没有后续的等待匹配节点，删除该新增的|FULFILLING|节点（将要主动匹配的节点），跳出循环
                            // 注：原始头节点（|s|下一个节点）被其他线程匹配，跳出循环，让该|s|当作首节点加入队列，以等待另一个节点来匹配
                            if (m == null) {        // all waiters are gone
                                casHead(s, null);   // pop fulfill node
                                s = null;           // use new node next time
                                break;              // restart main loop
                            }

                            SNode mn = m.next;
                            // 尝试将|s|和|m|这两个相邻节点进行匹配，以此来消耗掉|m|节点
                            // 注：|m|为待匹配节点，|s|为主动匹配节点
                            // 注：将|m|节点的|match|字段设置为主动匹配节点的指针；如果有|m|有等待线程，唤醒它
                            if (m.tryMatch(s)) {
                                // 删除这两个相邻的已匹配的节点
                                casHead(s, mn);     // pop both s and m

                                // 1.当一个生产者节点，需要等待消费者来匹配消耗时，返回等待者节点（生产者）提供的数据
                                // 2.当一个消费者节点，需要等待生产者来匹配消耗时，返回被匹配节点（生产者）提供的数据
                                return (E) ((mode == REQUEST) ? m.item : s.item);
                            } else                  // lost match
                                // 已被其他线程匹配，辅助执行删除
                                s.casNext(m, mn);   // help unlink
                        }
                    }
                } else {                            // help a fulfiller
                    // 辅助|FULFILLING|节点匹配相邻的节点
                    // 注：具体匹配逻辑的注释和上面一致

                    SNode m = h.next;               // m is h's match
                    if (m == null)                  // waiter is gone
                        casHead(h, null);           // pop fulfilling node
                    else {
                        SNode mn = m.next;
                        if (m.tryMatch(h))          // help match
                            casHead(h, mn);         // pop both h and m
                        else                        // lost match
                            h.casNext(m, mn);       // help unlink
                    }
                }
            }
        }

        /**
         * Spins/blocks until node s is matched by a fulfill operation.
         *
         * @param s the waiting node
         * @param timed true if timed wait
         * @param nanos timeout value
         * @return matched node, or s if cancelled
         */
        SNode awaitFulfill(SNode s, boolean timed, long nanos) {
            /*
             * When a node/thread is about to block, it sets its waiter
             * field and then rechecks state at least one more time
             * before actually parking, thus covering race vs
             * fulfiller noticing that waiter is non-null so should be
             * woken.
             *
             * When invoked by nodes that appear at the point of call
             * to be at the head of the stack, calls to park are
             * preceded by spins to avoid blocking when producers and
             * consumers are arriving very close in time.  This can
             * happen enough to bother only on multiprocessors.
             *
             * The order of checks for returning out of main loop
             * reflects fact that interrupts have precedence over
             * normal returns, which have precedence over
             * timeouts. (So, on timeout, one last check for match is
             * done before giving up.) Except that calls from untimed
             * SynchronousQueue.{poll/offer} don't check interrupts
             * and don't wait at all, so are trapped in transfer
             * method rather than calling awaitFulfill.
             */
            final long deadline = timed ? System.nanoTime() + nanos : 0L;
            Thread w = Thread.currentThread();

            // 若节点|s|是第一个等待节点、或队列中没有节点、或是一个主动匹配的节点，则设置一个自旋计数器
            /// 注：因为|TransferStack|是一个"栈"队列，在通常情况下，首节点是最可能被"匹配消耗"的，故而自旋
            int spins = (shouldSpin(s) ?
                         (timed ? maxTimedSpins : maxUntimedSpins) : 0);

            // 阻塞等待|s|节点被"匹配消耗"
            // 注：首节点起始会自旋32次，在最后1ms时，若是活动的（可能是被提前唤醒、也可能超时设置小于1ms），会不停的自旋
            for (;;) {
                // 当前线程已经被中断，尝试取消该节点
                // 注：将该节点的|match|字段指向自身，以表明该节点被取消
                if (w.isInterrupted())
                    s.tryCancel();

                // 自旋、阻塞等待，直到节点|s|中的匹配字段|match|被其他线程修改为止
                // 注：当节点|s|被取消，等式|m != null|也将成立，此时需要外部|transfer()|处理该场景
                SNode m = s.match;
                if (m != null)
                    return m;

                // 开启超时限制
                if (timed) {
                    nanos = deadline - System.nanoTime();
                    // 已经超时，尝试取消该节点
                    // 注：将该节点的数据字段指向自身，以表明该节点被取消
                    if (nanos <= 0L) {
                        s.tryCancel();
                        continue;
                    }
                }

                // 自旋、阻塞等待，直到节点|s|中的数据字段被其他线程修改为止
                // 注：当节点|s|被取消，等式|x != e|也将成立，此时需要外部|transfer()|处理该场景
                if (spins > 0)
                    spins = shouldSpin(s) ? (spins-1) : 0;
                // 自旋结束后，数据仍未获取到，将当前线程加入该节点的等待者队列，等待其他线程匹配唤醒
                else if (s.waiter == null)
                    s.waiter = w; // establish waiter so can park next iter
                // 阻塞等待，直到其他线程主动唤醒
                else if (!timed)
                    LockSupport.park(this);
                // 当超时剩余时间大于1000ns，挂起该等待线程；若小于1ms，自旋等待即可。此时挂起，将无意义（刚挂起就要被唤醒）
                else if (nanos > spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanos);
            }
        }

        /**
         * Returns true if node s is at head or there is an active
         * fulfiller.
         */
        boolean shouldSpin(SNode s) {
            SNode h = head;
            return (h == s || h == null || isFulfilling(h.mode));
        }

        /**
         * Unlinks s from the stack.
         */
        void clean(SNode s) {
            s.item = null;   // forget item
            s.waiter = null; // forget thread

            /*
             * At worst we may need to traverse entire stack to unlink
             * s. If there are multiple concurrent calls to clean, we
             * might not see s if another thread has already removed
             * it. But we can stop when we see any node known to
             * follow s. We use s.next unless it too is cancelled, in
             * which case we try the node one past. We don't check any
             * further because we don't want to doubly traverse just to
             * find sentinel.
             */

            SNode past = s.next;
            if (past != null && past.isCancelled())
                past = past.next;

            // Absorb cancelled nodes at head
            SNode p;
            while ((p = head) != null && p != past && p.isCancelled())
                casHead(p, p.next);

            // Unsplice embedded nodes
            while (p != null && p != past) {
                SNode n = p.next;
                if (n != null && n.isCancelled())
                    p.casNext(n, n.next);
                else
                    p = n;
            }
        }

        // Unsafe mechanics
        private static final sun.misc.Unsafe UNSAFE;
        private static final long headOffset;
        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class<?> k = TransferStack.class;
                headOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("head"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    /** Dual Queue */
    // 注：一个FIFO队列，尾部生产新增，头部匹配消耗
    // 精华：将一个节点的|next|字段指向自身，表明该节点被匹配
    // 精华：头引用始终指向一个dummy node节点（元素是null），它可以非常巧妙的实现线程安全
    static final class TransferQueue<E> extends Transferer<E> {
        /*
         * This extends Scherer-Scott dual queue algorithm, differing,
         * among other ways, by using modes within nodes rather than
         * marked pointers. The algorithm is a little simpler than
         * that for stacks because fulfillers do not need explicit
         * nodes, and matching is done by CAS'ing QNode.item field
         * from non-null to null (for put) or vice versa (for take).
         */

        /** Node class for TransferQueue. */
        static final class QNode {
            volatile QNode next;          // next node in queue
            volatile Object item;         // CAS'ed to or from null
            volatile Thread waiter;       // to control park/unpark
            final boolean isData;   // 为真：节点为生产者；反之为消费者

            QNode(Object item, boolean isData) {
                this.item = item;
                this.isData = isData;
            }

            boolean casNext(QNode cmp, QNode val) {
                return next == cmp &&
                    UNSAFE.compareAndSwapObject(this, nextOffset, cmp, val);
            }

            boolean casItem(Object cmp, Object val) {
                return item == cmp &&
                    UNSAFE.compareAndSwapObject(this, itemOffset, cmp, val);
            }

            /**
             * Tries to cancel by CAS'ing ref to this as item.
             */
            // 注：将当前节点的数据字段指向自身，以表明该节点被取消
            void tryCancel(Object cmp) {
                UNSAFE.compareAndSwapObject(this, itemOffset, cmp, this);
            }

            boolean isCancelled() {
                return item == this;
            }

            /**
             * Returns true if this node is known to be off the queue
             * because its next pointer has been forgotten due to
             * an advanceHead operation.
             */
            boolean isOffList() {
                return next == this;
            }

            // Unsafe mechanics
            private static final sun.misc.Unsafe UNSAFE;
            private static final long itemOffset;
            private static final long nextOffset;

            static {
                try {
                    UNSAFE = sun.misc.Unsafe.getUnsafe();
                    Class<?> k = QNode.class;
                    itemOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("item"));
                    nextOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("next"));
                } catch (Exception e) {
                    throw new Error(e);
                }
            }
        }

        /** Head of queue */
        // 头引用指向的节点中的元素始终指向null，是一个"dummy node"
        // 注：该"dummy node"可以让线程安全简单化：当队列中存在一个以上的节点，|head|和|tail|指向
        // 就不可能相同，而生产、消费算法中，会去修改各自的指针，也就不会有线程安全问题
        transient volatile QNode head;
        /** Tail of queue */
        transient volatile QNode tail;
        /**
         * Reference to a cancelled node that might not yet have been
         * unlinked from queue because it was the last inserted node
         * when it was cancelled.
         */
        transient volatile QNode cleanMe;

        TransferQueue() {
            // 初始化设置头、尾节点指向一个"dummy node"
            // 注：该"dummy node"可以让线程安全简单化：当队列中存在一个以上的节点，|head|和|tail|指向
            // 就不可能相同，而生产、消费算法中，会去修改各自的指针，也就不会有线程安全问题
            QNode h = new QNode(null, false); // initialize to dummy node.
            head = h;
            tail = h;
        }

        /**
         * Tries to cas nh as new head; if successful, unlink
         * old head's next node to avoid garbage retention.
         */
        // 删除元素的头节点|h|，并将|nh|设置为新的头节点
        // 注：将当前节点的|next|字段指向自身，以表明该节点已被匹配、并被删除
        void advanceHead(QNode h, QNode nh) {
            if (h == head &&
                UNSAFE.compareAndSwapObject(this, headOffset, h, nh))
                // help gc的关键
                h.next = h; // forget old next
        }

        /**
         * Tries to cas nt as new tail.
         */
        void advanceTail(QNode t, QNode nt) {
            if (tail == t)
                UNSAFE.compareAndSwapObject(this, tailOffset, t, nt);
        }

        /**
         * Tries to CAS cleanMe slot.
         */
        boolean casCleanMe(QNode cmp, QNode val) {
            return cleanMe == cmp &&
                UNSAFE.compareAndSwapObject(this, cleanMeOffset, cmp, val);
        }

        /**
         * Puts or takes an item.
         */
        // 同步的生产或消费一个元素。即，每一次|put|操作，必须等待其他线程的|take|操作，反之亦然
        // 典型场景：先添加一个消费者，在超时时间前，添加一个生产者（任务）就会被该消费者"匹配消耗"
        // 注：默认的|offer,poll|方法，调用|transfer|时，其中|timed==true&&nanos==0|，两
        // 者都会立即返回，若配对使用，意义不大。必须有一个消费者在阻塞等待一个生产者，反之亦然
        // 注：将当前节点的|item|字段指向自身，以表明该节点已被取消、待删除
        // 注：将当前节点的|next|字段指向自身，以表明该节点已被匹配、并被删除
        @SuppressWarnings("unchecked")
        E transfer(E e, boolean timed, long nanos) {
            /* Basic algorithm is to loop trying to take either of
             * two actions:
             *
             * 1. If queue apparently empty or holding same-mode nodes,
             *    try to add node to queue of waiters, wait to be
             *    fulfilled (or cancelled) and return matching item.
             *
             * 2. If queue apparently contains waiting items, and this
             *    call is of complementary mode, try to fulfill by CAS'ing
             *    item field of waiting node and dequeuing it, and then
             *    returning matching item.
             *
             * In each case, along the way, check for and try to help
             * advance head and tail on behalf of other stalled/slow
             * threads.
             *
             * The loop starts off with a null check guarding against
             * seeing uninitialized head or tail values. This never
             * happens in current SynchronousQueue, but could if
             * callers held non-volatile/final ref to the
             * transferer. The check is here anyway because it places
             * null checks at top of loop, which is usually faster
             * than having them implicitly interspersed.
             */

            QNode s = null; // constructed/reused as needed
            boolean isData = (e != null);   // 若|e!=null|为生产者，反之为消费者

            // 同步的生产或消费一个元素。即，每一次|put|操作，必须等待其他线程的|take|操作，反之亦然
            // 典型场景：先添加一个消费者，在超时时间前，添加一个生产者（任务）就会被该消费者"匹配消耗"
            for (;;) {
                QNode t = tail;
                QNode h = head;
                if (t == null || h == null)         // saw uninitialized value
                    continue;                       // spin

                // 链表为空，或者队列中有多个相同类型的节点
                // 注：在多线程场景中，就会存在多个生产者或消费者，但链表中只会有多个相同类型的节点。因
                // 为不同类型的节点会相互"匹配消耗"
                if (h == t || t.isData == isData) { // empty or same-mode
                    QNode tn = t.next;
                    // 引用不一致：|tail|已被其他线程修改
                    if (t != tail)                  // inconsistent read
                        continue;

                    // 尾部引用更新滞后：尾部已新增了节点，但|tail|还未更新
                    if (tn != null) {               // lagging tail
                        advanceTail(t, tn);
                        continue;
                    }

                    // 若使用了超时特性，但|nanos|时限已到，立即返回null
                    // 注：默认的|offer(), poll()|方法，|timed==true&&nanos==0|，都会立即返回，配对使用意义不大
                    // 必须至少有一个消费者在阻塞等待一个生产者，反之亦可
                    if (timed && nanos <= 0)        // can't wait
                        return null;

                    // 创建、并初始化一个|QNode|节点，将其添加至链表尾部
                    // 注：添加失败，重试。如并发场景
                    if (s == null)
                        s = new QNode(e, isData);
                    if (!t.casNext(null, s))        // failed to link in
                        continue;

                    // 修改|tail|尾部指针到新增的|s|节点。这也是为什么|tail|会滞后链表新增节点
                    advanceTail(t, s);              // swing tail and wait

                    // 阻塞、等待|s|节点被"匹配消耗"
                    Object x = awaitFulfill(s, e, timed, nanos);

                    // 待"匹配消耗"的|s|节点数据字段指向了自身，说明|s|节点已被取消
                    if (x == s) {                   // wait was cancelled
                        clean(t, s);    // 删除被取消的节点
                        return null;
                    }

                    // 通常情况下，刚被"匹配消耗"的节点，是不会被删除的，它会被充当成新的dummy node
                    // 例外：其他线程执行了该|s|节点的删除，此时由其他线程设置新的dummy node
                    if (!s.isOffList()) {           // not already unlinked
                        advanceHead(t, s);          // unlink if head
                        if (x != null)              // and forget fields
                            s.item = s;
                        // 删除线程句柄
                        s.waiter = null;
                    }

                    // 1.当一个生产者节点，需要等待消费者来匹配消耗时，会将该生产者节点的数据字段置为null，返回生产者提供的数据
                    // 2.当一个消费者节点，需要等待生产者来匹配消耗时，会将该消费者节点的数据字段置为生产者提供的数据，直接返回
                    return (x != null) ? (E)x : e;

                } else {                            // complementary-mode
                    // 当队列中已存在消费者或生产者，从队列头部开始"匹配消耗"这些节点
                    QNode m = h.next;               // node to fulfill

                    // 引用不一致：|tail|已被其他线程修改；或者队列中的节点都已经被消耗；或者|head|已被其他线程修改
                    if (t != tail || m == null || h != head)
                        continue;                   // inconsistent read

                    Object x = m.item;
                    // 1.等式|isData==(x!=null)|表明待"匹配消耗"的|m|节点与当前节点类型相同，说明该节点已被匹配
                    // 2.等式|x == m|表明待"匹配消耗"的|m|节点数据字段指向了自身，说明|m|节点已被取消
                    // 3.将待"匹配消耗"的|m|节点数据设置为|e|，执行实际的"匹配消耗"动作。失败说明有并发，移动头指针
                    if (isData == (x != null) ||    // m already fulfilled
                        x == m ||                   // m cancelled
                        !m.casItem(x, e)) {         // lost CAS
                        // CAS失败，说明有并发，移动头指针
                        advanceHead(h, m);          // dequeue and retry
                        continue;
                    }

                    // 重新设置头指针|head|到下一个|m|节点；并且将原先头中的next指针重置，有利于help gc
                    advanceHead(h, m);              // successfully fulfilled

                    // 唤醒该节点的等待者线程
                    LockSupport.unpark(m.waiter);

                    // 1.当一个生产者节点，需要等待消费者来匹配消耗时，会将该生产者节点的数据字段置为null，返回生产者提供的数据
                    // 2.当一个消费者节点，需要等待生产者来匹配消耗时，会将该消费者节点的数据字段置为生产者提供的数据，直接返回
                    return (x != null) ? (E)x : e;
                }
            }
        }

        /**
         * Spins/blocks until node s is fulfilled.
         *
         * @param s the waiting node
         * @param e the comparison value for checking match
         * @param timed true if timed wait
         * @param nanos timeout value
         * @return matched item, or s if cancelled
         */
        Object awaitFulfill(QNode s, E e, boolean timed, long nanos) {
            /* Same idea as TransferStack.awaitFulfill */
            final long deadline = timed ? System.nanoTime() + nanos : 0L;
            Thread w = Thread.currentThread();

            // 若节点|s|是第一个等待节点，则设置一个自旋计数器
            // 注：因为|TransferQueue|是一个FIFO队列，在通常情况下，首节点是最可能被"匹配消耗"的，故而自旋
            int spins = ((head.next == s) ?
                         (timed ? maxTimedSpins : maxUntimedSpins) : 0);

            // 阻塞等待|s|节点被"匹配消耗"
            // 注：首节点起始会自旋32次，在最后1ms时，若是活动的（可能是被提前唤醒、也可能超时设置小于1ms），会不停的自旋
            for (;;) {
                // 当前线程已经被中断，尝试取消该节点
                // 注：将该节点的数据字段指向自身，以表明该节点被取消
                if (w.isInterrupted())
                    s.tryCancel(e);

                // 自旋、阻塞等待，直到节点|s|中的数据字段被其他线程修改为止
                // 注：当节点|s|被取消，等式|x != e|也将成立，此时需要外部|transfer()|处理该场景
                Object x = s.item;
                if (x != e)
                    return x;

                // 开启超时限制
                if (timed) {
                    nanos = deadline - System.nanoTime();
                    if (nanos <= 0L) {
                        // 已经超时，尝试取消该节点
                        // 注：将该节点的数据字段指向自身，以表明该节点被取消
                        s.tryCancel(e);
                        continue;
                    }
                }

                // 自旋等待
                if (spins > 0)
                    --spins;
                // 自旋结束后，数据仍未获取到，将当前线程加入该节点的等待者队列，等待其他线程匹配唤醒
                else if (s.waiter == null)
                    s.waiter = w;
                // 阻塞等待，直到其他线程主动唤醒
                else if (!timed)
                    LockSupport.park(this);
                // 当超时剩余时间大于1000ns，挂起该等待线程；若小于1ms，自旋等待即可。此时挂起，将无意义（刚挂起就要被唤醒）
                else if (nanos > spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanos);
            }
        }

        /**
         * Gets rid of cancelled node s with original predecessor pred.
         */
        // 删除已取消的节点|s|，其前驱节点为|pred|
        void clean(QNode pred, QNode s) {
            // 先将其线程引用置空 help gc
            s.waiter = null; // forget thread
            /*
             * At any given time, exactly one node on list cannot be
             * deleted -- the last inserted node. To accommodate this,
             * if we cannot delete s, we save its predecessor as
             * "cleanMe", deleting the previously saved version
             * first. At least one of node s or the node previously
             * saved can always be deleted, so this always terminates.
             */

            // 无锁、循环的删除|s|节点
            // 注：当等式|pred.next==s|不成立时，说明其他线程将节点|s|删除、或者节点|pred|被删除
            while (pred.next == s) { // Return early if already unlinked
                QNode h = head;
                QNode hn = h.next;   // Absorb cancelled first node as head
                // 第一个有效的节点（头节点的下一个节点）是一个已被取消的节点，先删除它
                // 注：队列|TransferQueue|是一个FIFO，头节点是被取消最频繁的节点。有优化效果
                // 注：若被取消的节点|s|恰好是|hn|，删除后，会立即退出循环
                if (hn != null && hn.isCancelled()) {
                    advanceHead(h, hn);
                    continue;
                }

                QNode t = tail;      // Ensure consistent read for tail
                // 队列已经为空，立即返回
                if (t == h)
                    return;

                QNode tn = t.next;
                // 引用不一致：其他线程已修改了|tail|指针
                if (t != tail)
                    continue;

                // 尾部引用更新滞后：尾部已新增了节点，但|tail|还未更新
                if (tn != null) {
                    advanceTail(t, tn);
                    continue;
                }

                // 待删除节点不是尾节点，尝试直接删除
                if (s != t) {        // If not tail, try to unsplice
                    QNode sn = s.next;
                    // 节点|s|已经被删除；或者执行删除|s|成功，立即返回
                    // 注：此处执行删除|s|时，可能存在竞争，比如其他线程把|s|被删除了；或者|pred|被删除
                    if (sn == s || pred.casNext(s, sn))
                        return;
                }

                // 延迟删除：当删除失败、或者|s|为尾部节点时，将|s|的前驱|prev|节点添加至待删除|cleanMe|中后返回
                // 注：之所以，当|s|为尾部节点时，要延迟删除：当出现此场景，说明前驱的节点也是无效的（将被删除），此
                // 时再操作前驱节点无意义。要谨记|TransferQueue|是一个FIFO队列
                QNode dp = cleanMe;
                if (dp != null) {    // Try unlinking previous cancelled node
                    QNode d = dp.next;
                    QNode dn;

                    // 尝试执行删除"延迟删除|cleanMe|"节点的下一个节点，并清除|cleanMe|指针
                    if (d == null ||               // d is gone or
                        d == dp ||                 // d is off list or
                        !d.isCancelled() ||        // d not cancelled or
                        (d != t &&                 // d not tail and
                         (dn = d.next) != null &&  //   has successor
                         dn != d &&                //   that is on list
                         dp.casNext(d, dn)))       // d unspliced
                        casCleanMe(dp, null);

                    // 节点|s|的前驱|prev|节点已添加至待删除|cleanMe|中，直接返回
                    if (dp == pred)
                        return;      // s is already saved node
                } else if (casCleanMe(null, pred))
                    return;          // Postpone cleaning s
            }
        }

        private static final sun.misc.Unsafe UNSAFE;
        private static final long headOffset;
        private static final long tailOffset;
        private static final long cleanMeOffset;
        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class<?> k = TransferQueue.class;
                headOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("head"));
                tailOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("tail"));
                cleanMeOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("cleanMe"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    /**
     * The transferer. Set only in constructor, but cannot be declared
     * as final without further complicating serialization.  Since
     * this is accessed only at most once per public method, there
     * isn't a noticeable performance penalty for using volatile
     * instead of final here.
     */
    private transient volatile Transferer<E> transferer;

    /**
     * Creates a {@code SynchronousQueue} with nonfair access policy.
     */
    // 默认使用非公平策略创建一个SynchronousQueue
    public SynchronousQueue() {
        this(false);
    }

    /**
     * Creates a {@code SynchronousQueue} with the specified fairness policy.
     *
     * @param fair if true, waiting threads contend in FIFO order for
     *        access; otherwise the order is unspecified.
     */
    // 使用指定的公平策略创建一个SynchronousQueue
    // 队列有公平(TransferQueue FIFO)与非公平(TransferStack LIFO)两种策略模式
    public SynchronousQueue(boolean fair) {
        transferer = fair ? new TransferQueue<E>() : new TransferStack<E>();
    }

    /**
     * Adds the specified element to this queue, waiting if necessary for
     * another thread to receive it.
     *
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public void put(E e) throws InterruptedException {
        if (e == null) throw new NullPointerException();
        if (transferer.transfer(e, false, 0) == null) {
            Thread.interrupted();
            throw new InterruptedException();
        }
    }

    /**
     * Inserts the specified element into this queue, waiting if necessary
     * up to the specified wait time for another thread to receive it.
     *
     * @return {@code true} if successful, or {@code false} if the
     *         specified waiting time elapses before a consumer appears
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean offer(E e, long timeout, TimeUnit unit)
        throws InterruptedException {
        if (e == null) throw new NullPointerException();
        if (transferer.transfer(e, true, unit.toNanos(timeout)) != null)
            return true;
        if (!Thread.interrupted())
            return false;
        throw new InterruptedException();
    }

    /**
     * Inserts the specified element into this queue, if another thread is
     * waiting to receive it.
     *
     * @param e the element to add
     * @return {@code true} if the element was added to this queue, else
     *         {@code false}
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e) {
        if (e == null) throw new NullPointerException();
        return transferer.transfer(e, true, 0) != null;
    }

    /**
     * Retrieves and removes the head of this queue, waiting if necessary
     * for another thread to insert it.
     *
     * @return the head of this queue
     * @throws InterruptedException {@inheritDoc}
     */
    public E take() throws InterruptedException {
        E e = transferer.transfer(null, false, 0);
        if (e != null)
            return e;
        Thread.interrupted();
        throw new InterruptedException();
    }

    /**
     * Retrieves and removes the head of this queue, waiting
     * if necessary up to the specified wait time, for another thread
     * to insert it.
     *
     * @return the head of this queue, or {@code null} if the
     *         specified waiting time elapses before an element is present
     * @throws InterruptedException {@inheritDoc}
     */
    // 阻塞消费一个元素。除非中断、或者阻塞|timeout|时长后队列仍然是空的，否则方法会阻塞直到能消费一个元素为止
    // 如果队列为空，阻塞等待|timeout|时长，若仍然是空的，则返回false
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E e = transferer.transfer(null, true, unit.toNanos(timeout));
        if (e != null || !Thread.interrupted())
            return e;
        throw new InterruptedException();
    }

    /**
     * Retrieves and removes the head of this queue, if another thread
     * is currently making an element available.
     *
     * @return the head of this queue, or {@code null} if no
     *         element is available
     */
    public E poll() {
        return transferer.transfer(null, true, 0);
    }

    /**
     * Always returns {@code true}.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @return {@code true}
     */
    public boolean isEmpty() {
        return true;
    }

    /**
     * Always returns zero.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @return zero
     */
    public int size() {
        return 0;
    }

    /**
     * Always returns zero.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @return zero
     */
    public int remainingCapacity() {
        return 0;
    }

    /**
     * Does nothing.
     * A {@code SynchronousQueue} has no internal capacity.
     */
    public void clear() {
    }

    /**
     * Always returns {@code false}.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @param o the element
     * @return {@code false}
     */
    public boolean contains(Object o) {
        return false;
    }

    /**
     * Always returns {@code false}.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @param o the element to remove
     * @return {@code false}
     */
    public boolean remove(Object o) {
        return false;
    }

    /**
     * Returns {@code false} unless the given collection is empty.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @param c the collection
     * @return {@code false} unless given collection is empty
     */
    public boolean containsAll(Collection<?> c) {
        return c.isEmpty();
    }

    /**
     * Always returns {@code false}.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @param c the collection
     * @return {@code false}
     */
    public boolean removeAll(Collection<?> c) {
        return false;
    }

    /**
     * Always returns {@code false}.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @param c the collection
     * @return {@code false}
     */
    public boolean retainAll(Collection<?> c) {
        return false;
    }

    /**
     * Always returns {@code null}.
     * A {@code SynchronousQueue} does not return elements
     * unless actively waited on.
     *
     * @return {@code null}
     */
    public E peek() {
        return null;
    }

    /**
     * Returns an empty iterator in which {@code hasNext} always returns
     * {@code false}.
     *
     * @return an empty iterator
     */
    public Iterator<E> iterator() {
        return Collections.emptyIterator();
    }

    /**
     * Returns an empty spliterator in which calls to
     * {@link java.util.Spliterator#trySplit()} always return {@code null}.
     *
     * @return an empty spliterator
     * @since 1.8
     */
    public Spliterator<E> spliterator() {
        return Spliterators.emptySpliterator();
    }

    /**
     * Returns a zero-length array.
     * @return a zero-length array
     */
    public Object[] toArray() {
        return new Object[0];
    }

    /**
     * Sets the zeroeth element of the specified array to {@code null}
     * (if the array has non-zero length) and returns it.
     *
     * @param a the array
     * @return the specified array
     * @throws NullPointerException if the specified array is null
     */
    public <T> T[] toArray(T[] a) {
        if (a.length > 0)
            a[0] = null;
        return a;
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c) {
        if (c == null)
            throw new NullPointerException();
        if (c == this)
            throw new IllegalArgumentException();
        int n = 0;
        for (E e; (e = poll()) != null;) {
            c.add(e);
            ++n;
        }
        return n;
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c, int maxElements) {
        if (c == null)
            throw new NullPointerException();
        if (c == this)
            throw new IllegalArgumentException();
        int n = 0;
        for (E e; n < maxElements && (e = poll()) != null;) {
            c.add(e);
            ++n;
        }
        return n;
    }

    /*
     * To cope with serialization strategy in the 1.5 version of
     * SynchronousQueue, we declare some unused classes and fields
     * that exist solely to enable serializability across versions.
     * These fields are never used, so are initialized only if this
     * object is ever serialized or deserialized.
     */

    @SuppressWarnings("serial")
    static class WaitQueue implements java.io.Serializable { }
    static class LifoWaitQueue extends WaitQueue {
        private static final long serialVersionUID = -3633113410248163686L;
    }
    static class FifoWaitQueue extends WaitQueue {
        private static final long serialVersionUID = -3623113410248163686L;
    }
    private ReentrantLock qlock;
    private WaitQueue waitingProducers;
    private WaitQueue waitingConsumers;

    /**
     * Saves this queue to a stream (that is, serializes it).
     * @param s the stream
     * @throws java.io.IOException if an I/O error occurs
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {
        boolean fair = transferer instanceof TransferQueue;
        if (fair) {
            qlock = new ReentrantLock(true);
            waitingProducers = new FifoWaitQueue();
            waitingConsumers = new FifoWaitQueue();
        }
        else {
            qlock = new ReentrantLock();
            waitingProducers = new LifoWaitQueue();
            waitingConsumers = new LifoWaitQueue();
        }
        s.defaultWriteObject();
    }

    /**
     * Reconstitutes this queue from a stream (that is, deserializes it).
     * @param s the stream
     * @throws ClassNotFoundException if the class of a serialized object
     *         could not be found
     * @throws java.io.IOException if an I/O error occurs
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        if (waitingProducers instanceof FifoWaitQueue)
            transferer = new TransferQueue<E>();
        else
            transferer = new TransferStack<E>();
    }

    // Unsafe mechanics
    static long objectFieldOffset(sun.misc.Unsafe UNSAFE,
                                  String field, Class<?> klazz) {
        try {
            return UNSAFE.objectFieldOffset(klazz.getDeclaredField(field));
        } catch (NoSuchFieldException e) {
            // Convert Exception to corresponding Error
            NoSuchFieldError error = new NoSuchFieldError(field);
            error.initCause(e);
            throw error;
        }
    }

}
