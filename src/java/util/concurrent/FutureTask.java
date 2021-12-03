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
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;
import java.util.concurrent.locks.LockSupport;

/**
 * A cancellable asynchronous computation.  This class provides a base
 * implementation of {@link Future}, with methods to start and cancel
 * a computation, query to see if the computation is complete, and
 * retrieve the result of the computation.  The result can only be
 * retrieved when the computation has completed; the {@code get}
 * methods will block if the computation has not yet completed.  Once
 * the computation has completed, the computation cannot be restarted
 * or cancelled (unless the computation is invoked using
 * {@link #runAndReset}).
 *
 * <p>A {@code FutureTask} can be used to wrap a {@link Callable} or
 * {@link Runnable} object.  Because {@code FutureTask} implements
 * {@code Runnable}, a {@code FutureTask} can be submitted to an
 * {@link Executor} for execution.
 *
 * <p>In addition to serving as a standalone class, this class provides
 * {@code protected} functionality that may be useful when creating
 * customized task classes.
 *
 * @since 1.5
 * @author Doug Lea
 * @param <V> The result type returned by this FutureTask's {@code get} methods
 */
// 实现了Runner接口，可由线程池execute方法执行；重写的run方法，会将结果广播给所有等待者
// 精华：操作"等待结果"链表时，不使用互斥锁。因为代码实现的目的很单一，即，清除所有待删除的
// 节点。也就无需担心链表在多线程中执行删除时，即使一个节点指向了被其他线程删除的节点，在下
// 次循环也会被删除；更不会出现一个有效的节点被勿删除了，因为新增的节点总是在头部，这也是只
// 有在操作头节点时，使用CAS计算
public class FutureTask<V> implements RunnableFuture<V> {
    /*
     * Revision notes: This differs from previous versions of this
     * class that relied on AbstractQueuedSynchronizer, mainly to
     * avoid surprising users about retaining interrupt status during
     * cancellation races. Sync control in the current design relies
     * on a "state" field updated via CAS to track completion, along
     * with a simple Treiber stack to hold waiting threads.
     *
     * Style note: As usual, we bypass overhead of using
     * AtomicXFieldUpdaters and instead directly use Unsafe intrinsics.
     */

    /**
     * The run state of this task, initially NEW.  The run state
     * transitions to a terminal state only in methods set,
     * setException, and cancel.  During completion, state may take on
     * transient values of COMPLETING (while outcome is being set) or
     * INTERRUPTING (only while interrupting the runner to satisfy a
     * cancel(true)). Transitions from these intermediate to final
     * states use cheaper ordered/lazy writes because values are unique
     * and cannot be further modified.
     *
     * Possible state transitions:
     * NEW -> COMPLETING -> NORMAL
     * NEW -> COMPLETING -> EXCEPTIONAL
     * NEW -> CANCELLED
     * NEW -> INTERRUPTING -> INTERRUPTED
     */
    private volatile int state; // 任务状态标志位。|volatile|解决线程可见行
    private static final int NEW          = 0;  // 任务新建，并正在被执行
    private static final int COMPLETING   = 1;  // 任务执行完成，准备设置返回值、并广播它
    private static final int NORMAL       = 2;  // 任务执行完成，返回值已被设置到|outcome|中、正在广播。任务正常结束的最终状态
    private static final int EXCEPTIONAL  = 3;  // 任务执行抛出异常。任务异常结束的最终状态
    private static final int CANCELLED    = 4;  // 任务被取消，但无需中断的最终状态
    private static final int INTERRUPTING = 5;  // 任务被取消，且需要中断时，正在被中断
    private static final int INTERRUPTED  = 6;  // 任务被取消，且需要中断时的最终状态

    /** The underlying callable; nulled out after running */
    // 可调用任务的方法引用
    private Callable<V> callable;
    /** The result to return or exception to throw from get() */
    // 任务执行结果；也可能是任务抛出的异常的引用
    private Object outcome; // non-volatile, protected by state reads/writes
    /** The thread running the callable; CASed during run() */
    // 任务被执行时，将被置为当前线程的引用。一个任务只能由一个线程执行
    private volatile Thread runner;
    /** Treiber stack of waiting threads */
    // 任务结果等待的队列
    private volatile WaitNode waiters;

    /**
     * Returns result or throws exception for completed task.
     *
     * @param s completed state value
     */
    @SuppressWarnings("unchecked")
    private V report(int s) throws ExecutionException {
        Object x = outcome;
        // 任务正常结束，直接返回结果
        if (s == NORMAL)
            return (V)x;
        // 任务被取消，抛出指定的"取消异常"
        if (s >= CANCELLED)
            throw new CancellationException();
        // 其他执行异常（比如任务方法执行中抛出了异常），统一封装到"执行异常"中
        throw new ExecutionException((Throwable)x);
    }

    /**
     * Creates a {@code FutureTask} that will, upon running, execute the
     * given {@code Callable}.
     *
     * @param  callable the callable task
     * @throws NullPointerException if the callable is null
     */
    public FutureTask(Callable<V> callable) {
        if (callable == null)
            throw new NullPointerException();
        // 可调用任务的方法引用
        this.callable = callable;
        // 默认为任务"新建"状态。该状态持续到任务被执行完成，设置返回值之前
        this.state = NEW;       // ensure visibility of callable
    }

    /**
     * Creates a {@code FutureTask} that will, upon running, execute the
     * given {@code Runnable}, and arrange that {@code get} will return the
     * given result on successful completion.
     *
     * @param runnable the runnable task
     * @param result the result to return on successful completion. If
     * you don't need a particular result, consider using
     * constructions of the form:
     * {@code Future<?> f = new FutureTask<Void>(runnable, null)}
     * @throws NullPointerException if the runnable is null
     */
    public FutureTask(Runnable runnable, V result) {
        // 将|Runnable|适配成|Callable|类型的对象，此时外部必须提供"返回值"的参数
        // 注：这意味着，任务结果（|Runnable|没有返回值），必须由客户端（用户）在|runnable|中设置到|result|引用上
        this.callable = Executors.callable(runnable, result);
        // 任务新建，该状态持续到任务执行完成，设置返回值之前
        this.state = NEW;       // ensure visibility of callable
    }

    public boolean isCancelled() {
        return state >= CANCELLED;
    }

    public boolean isDone() {
        return state != NEW;
    }

    // 取消一个正在执行的任务。|mayInterruptIfRunning|表示：是否发送"中断请求"给正在执行的任务
    // 注：要想真正的实现：当一个任务被取消后，不再执行，需要客户端（用户）在任务实现中去检查线程中断状态
    public boolean cancel(boolean mayInterruptIfRunning) {
        // 被取消的任务必须正在执行。若任务需要中断，则设置任务的状态为|INTERRUPTING|，表示正在中断
        if (!(state == NEW &&
              UNSAFE.compareAndSwapInt(this, stateOffset, NEW,
                  mayInterruptIfRunning ? INTERRUPTING : CANCELLED)))
            return false;
        try {    // in case call to interrupt throws exception
            if (mayInterruptIfRunning) {
                try {
                    // 中断任务线程
                    Thread t = runner;
                    if (t != null)
                        t.interrupt();
                } finally { // final state
                    // 把任务线程设置到最终的|INTERRUPTED|，已中断完成的最终状态
                    UNSAFE.putOrderedInt(this, stateOffset, INTERRUPTED);
                }
            }
        } finally {
            // 唤醒等待该任务的等待线程
            finishCompletion();
        }
        return true;
    }

    /**
     * @throws CancellationException {@inheritDoc}
     */
    public V get() throws InterruptedException, ExecutionException {
        int s = state;
        // 任务还未执行完成，进入等待队列。直到结果返回后，才可结束线程的等待阻塞状态
        if (s <= COMPLETING)
            s = awaitDone(false, 0L);
        // 任务已经执行完成，直接获取任务的结果
        return report(s);
    }

    /**
     * @throws CancellationException {@inheritDoc}
     */
    public V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        if (unit == null)
            throw new NullPointerException();
        int s = state;
        if (s <= COMPLETING &&
            (s = awaitDone(true, unit.toNanos(timeout))) <= COMPLETING)
            throw new TimeoutException();   // 任务执行到期后，仍未获得结果，抛出超时异常
        // 任务已经执行完成，直接获取任务的结果
        return report(s);
    }

    /**
     * Protected method invoked when this task transitions to state
     * {@code isDone} (whether normally or via cancellation). The
     * default implementation does nothing.  Subclasses may override
     * this method to invoke completion callbacks or perform
     * bookkeeping. Note that you can query status inside the
     * implementation of this method to determine whether this task
     * has been cancelled.
     */
    protected void done() { }

    /**
     * Sets the result of this future to the given value unless
     * this future has already been set or has been cancelled.
     *
     * <p>This method is invoked internally by the {@link #run} method
     * upon successful completion of the computation.
     *
     * @param v the value
     */
    protected void set(V v) {
        // CAS设置任务为完成|COMPLETING|状态，把值设置到|outcome|后，再把任务线程设置到最终的|NORMAL|，正常结束
        // 注：若此时任务已经非新建状态（比如线程被取消中断了），则状态设置不成功
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = v;
            UNSAFE.putOrderedInt(this, stateOffset, NORMAL); // final state
            // 唤醒等待该任务的等待线程
            finishCompletion();
        }
    }

    /**
     * Causes this future to report an {@link ExecutionException}
     * with the given throwable as its cause, unless this future has
     * already been set or has been cancelled.
     *
     * <p>This method is invoked internally by the {@link #run} method
     * upon failure of the computation.
     *
     * @param t the cause of failure
     */
    protected void setException(Throwable t) {
        // CAS设置任务为完成|COMPLETING|状态，把异常设置到|outcome|后，再把任务线程设置到最终的|EXCEPTIONAL|，异常结束
        // 注：若此时任务已经非新建状态（比如线程被取消中断了），则状态设置不成功
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = t;
            UNSAFE.putOrderedInt(this, stateOffset, EXCEPTIONAL); // final state
            // 唤醒等待该任务的等待线程
            finishCompletion();
        }
    }

    // 任务执行入口方法。任务结束后，|state|会自动流转到下一个状态；并且在任务结束后，|callable|会被清空
    // 任务不可以重复运行
    public void run() {
        // 任务非新建状态（说明已完成，或被终止）；或设置任务所有权失败（说明被其他线程执行中），直接返回
        // 注：一个任务只能被执行一次，并且只能由一个线程执行
        if (state != NEW ||
            !UNSAFE.compareAndSwapObject(this, runnerOffset,
                                         null, Thread.currentThread()))
            return;
        // 以下为单线程逻辑
        try {
            Callable<V> c = callable;
            if (c != null && state == NEW) {
                V result;
                boolean ran;
                try {
                    // 执行任务
                    result = c.call();
                    ran = true;
                } catch (Throwable ex) {
                    // 任务执行异常
                    result = null;
                    ran = false;
                    setException(ex);
                }
                // 执行成功。将返回值设置到|outcome|中，并广播所有等待者
                if (ran)
                    set(result);
            }
        } finally {
            // runner must be non-null until state is settled to
            // prevent concurrent calls to run()
            runner = null;  // 释放任务的执行线程所有权
            // state must be re-read after nulling runner to prevent
            // leaked interrupts
            int s = state;
            // 任务收到了中断请求，等待直到中断完成
            if (s >= INTERRUPTING)
                handlePossibleCancellationInterrupt(s);
        }
    }

    /**
     * Executes the computation without setting its result, and then
     * resets this future to initial state, failing to do so if the
     * computation encounters an exception or is cancelled.  This is
     * designed for use with tasks that intrinsically execute more
     * than once.
     *
     * @return {@code true} if successfully run and reset
     */
    // 运行一个任务，任务非异常结束后，|state|不会变更状态；并且在任务非异常结束后，|callable|不会
    // 被清空
    // 注：任务可以重复运行，只要任务不抛出异常
    protected boolean runAndReset() {
        if (state != NEW ||
            !UNSAFE.compareAndSwapObject(this, runnerOffset,
                                         null, Thread.currentThread()))
            return false;
        boolean ran = false;
        int s = state;
        try {
            Callable<V> c = callable;
            if (c != null && s == NEW) {
                try {
                    c.call(); // don't set result
                    ran = true;
                } catch (Throwable ex) {
                    setException(ex);
                }
            }
        } finally {
            // runner must be non-null until state is settled to
            // prevent concurrent calls to run()
            runner = null;  // 释放任务的执行线程所有权
            // state must be re-read after nulling runner to prevent
            // leaked interrupts
            s = state;
            // 任务收到了中断请求，等待直到中断完成
            if (s >= INTERRUPTING)
                handlePossibleCancellationInterrupt(s);
        }
        return ran && s == NEW;
    }

    /**
     * Ensures that any interrupt from a possible cancel(true) is only
     * delivered to a task while in run or runAndReset.
     */
    private void handlePossibleCancellationInterrupt(int s) {
        // It is possible for our interrupter to stall before getting a
        // chance to interrupt us.  Let's spin-wait patiently.

        // 等待，直到发送中断请求的线程，设置本线程中断状态完成
        if (s == INTERRUPTING)
            while (state == INTERRUPTING)
                Thread.yield(); // wait out pending interrupt

        // assert state == INTERRUPTED;

        // We want to clear any interrupt we may have received from
        // cancel(true).  However, it is permissible to use interrupts
        // as an independent mechanism for a task to communicate with
        // its caller, and there is no way to clear only the
        // cancellation interrupt.
        //
        // Thread.interrupted();
    }

    /**
     * Simple linked list nodes to record waiting threads in a Treiber
     * stack.  See other classes such as Phaser and SynchronousQueue
     * for more detailed explanation.
     */
    // 结果等待队列的节点，等待线程为当前线程（计算完成时，被唤醒的线程）。是一个单链表
    static final class WaitNode {
        volatile Thread thread;
        volatile WaitNode next;
        WaitNode() { thread = Thread.currentThread(); }
    }

    /**
     * Removes and signals all waiting threads, invokes done(), and
     * nulls out callable.
     */
    // 唤醒等待当前任务的等待线程
    private void finishCompletion() {
        // assert state > COMPLETING;

        // 注：以下的链表操作，并没有使用互斥锁。理由和|removeWaiter()|方法一致。逻辑非常单一，即，遍
        // 历所有节点，里面的写入操作，也仅仅是更新的指针引用，而被更新的节点也已经被通知唤醒

        // 等待队列不为空，遍历队列，唤醒对应的等待线程
        for (WaitNode q; (q = waiters) != null;) {
            if (UNSAFE.compareAndSwapObject(this, waitersOffset, q, null)) {
                // 遍历等待的链表队列
                for (;;) {
                    Thread t = q.thread;
                    if (t != null) {
                        // 清除|q.thread|，意味着该节点也将被从等待链表中清除。也有help gc作用
                        q.thread = null;
                        // 唤醒对应线程
                        LockSupport.unpark(t);
                    }
                    WaitNode next = q.next;
                    // 链表已遍历完成，立即退出
                    if (next == null)
                        break;
                    // 向前遍历链表，并清除已遍历的节点的引用
                    q.next = null; // unlink to help gc
                    q = next;
                }
                break;
            }
        }

        // 任务执行完成后的收尾方法
        // 注：在|QueueingFuture|类中，重写了该|done()|方法。而|QueueingFuture|主要用于|ExecutorCompletionService|中
        done();

        // 请求任务方法的引用。也有help gc作用
        callable = null;        // to reduce footprint
    }

    /**
     * Awaits completion or aborts on interrupt or timeout.
     *
     * @param timed true if use timed waits
     * @param nanos time to wait, if timed
     * @return state upon completion
     */
    private int awaitDone(boolean timed, long nanos)
        throws InterruptedException {
        // 当需要超时等待，|deadline|即是等待的最终时间点
        final long deadline = timed ? System.nanoTime() + nanos : 0L;
        WaitNode q = null;
        boolean queued = false; // 等待节点是否已被压入队列
        for (;;) {
            // 等待者线程被中断，移除该等待者节点
            if (Thread.interrupted()) {
                removeWaiter(q);
                throw new InterruptedException();
            }

            // 注：此处没有进行线程间同步操作，因为|state|是一个简单标志位，并且它不会出现"回退"到之前的状态

            int s = state;
            if (s > COMPLETING) {
                // 任务已完成，并已设置了|outcome|结果，直接返回
                if (q != null)
                    q.thread = null;    // 也有help gc作用
                return s;
            }
            else if (s == COMPLETING) // cannot time out yet
                Thread.yield(); // 任务刚刚执行完成，正在设置运行结果
            else if (q == null)
                q = new WaitNode(); // 生成一个等待节点，等待线程为当前线程（计算完成时，被唤醒的线程）
            else if (!queued)
                // 头插法，将等待节点压入队列
                queued = UNSAFE.compareAndSwapObject(this, waitersOffset,
                                                     q.next = waiters, q);
            else if (timed) {
                // 设置了超时等待模式
                nanos = deadline - System.nanoTime();
                if (nanos <= 0L) {  // 已超时
                    removeWaiter(q);
                    return state;
                }
                // 超时阻塞等待，直到被其他线程唤醒
                LockSupport.parkNanos(this, nanos);
            }
            else
                // 阻塞等待，直到被其他线程唤醒
                LockSupport.park(this);
        }
    }

    /**
     * Tries to unlink a timed-out or interrupted wait node to avoid
     * accumulating garbage.  Internal nodes are simply unspliced
     * without CAS since it is harmless if they are traversed anyway
     * by releasers.  To avoid effects of unsplicing from already
     * removed nodes, the list is retraversed in case of an apparent
     * race.  This is slow when there are a lot of nodes, but we don't
     * expect lists to be long enough to outweigh higher-overhead
     * schemes.
     */
    private void removeWaiter(WaitNode node) {
        if (node != null) {
            node.thread = null; // 将待移除的节点，线程引用置空。也有help gc作用

            // 注：以下的链表操作，并没有使用互斥锁。因为代码实现的目的很单一，即，清除所有待删除的节点。也就无
            // 需担心链表在多线程中执行删除时，即使一个节点指向了被其他线程删除的节点，在下次循环也会被删除；更
            // 不会出现一个有效的节点被勿删除了，因为新增的节点总是在头部，这也是为什么下面代码，只有在操作头节
            // 点时，使用CAS计算

            retry:
            for (;;) {          // restart on removeWaiter race
                // 从头遍历一次等待链表，清除所有节点中|thread|字段为空的节点
                for (WaitNode pred = null, q = waiters, s; q != null; q = s) {
                    s = q.next;
                    if (q.thread != null)   // 未迭代到待删除的节点，继续向前遍历链表。注：待删除节点，线程引用被置空
                        pred = q;
                    else if (pred != null) {    // 找到待删除的节点，并且它不是头节点
                        pred.next = s;  // 删除node节点
                        // 若检测到|pred.thread|字段为空，则说明它已经被其他线程删除了，需要从头遍历链表，而不是继续向前遍历
                        if (pred.thread == null) // check for race
                            continue retry; // 从头遍历链表
                    }
                    // 找到待删除的节点，并且它是头节点，需要CAS设置头引用
                    // 注：链表使用了头插法来新增节点，必须使用CAS来解决竞争问题
                    else if (!UNSAFE.compareAndSwapObject(this, waitersOffset,
                                                          q, s))
                        continue retry; // 从头遍历链表
                }
                break;
            }
        }
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long stateOffset;
    private static final long runnerOffset;
    private static final long waitersOffset;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> k = FutureTask.class;
            stateOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("state"));
            runnerOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("runner"));
            waitersOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("waiters"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
