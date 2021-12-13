/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.ref;

import sun.misc.Cleaner;

/**
 * Abstract base class for reference objects.  This class defines the
 * operations common to all reference objects.  Because reference objects are
 * implemented in close cooperation with the garbage collector, this class may
 * not be subclassed directly.
 *
 * @author   Mark Reinhold
 * @since    1.2
 */

// Reference的引入和GC有关。它可以帮组JVM GC来实现更细致的控制各类Reference引用的对象的内存管理
//
// GC的基本思想：从GC Root开始向下搜索，若对象与GC Root之间存在引用链（表示该对象可达的）、会再根
// 据对象的可到达性决定它是否应该被回收。其中，对象可达性与引用类型密切相关：
// 1.强可到达：对象与GC Root之间存在强引用链，则为强可到达
// 2.软可到达：对象与GC Root之间不存在强引用链，但存在软引用链，则为软可到达
// 3.弱可到达：对象与GC Root之间不存在强引用链与软引用链，但有弱引用链，则为弱可到达
// 4.虚可到达：对象与GC Root之间只存在虚引用链则为虚可到达
// 5.不可达：对象与GC Root之间不存在引用链，则为不可到达
// 注：引用链的强弱依次是：强引用>软引用>弱引用>虚引用。一个对象的引用链类型，由更强的关系决定
//
// GC的基本流程：JVM在GC时，会根据对象引用链的Reference实际类型与堆内存的使用情况，决定是否把对应的
// 对象回收，并将其加入到pending链表上（一个静态字段，虚拟机共用）。如果能加入pending链表，JVM会再唤
// 醒|ReferenceHandler|线程进行处理：比如，调用Cleaner#clean或ReferenceQueue#enqueue方法
// 注：如果引用的对象的引用链类型为WeakReference、且堆内存不足，那么JVM就会把它加入到pending链表上，
// 然后|ReferenceHandler|线程收到通知后，会异步地做入队列操作；而我们的应用程序，便可以不断地去获取
// 队列中的元素，来感知JVM的堆内存是否出现了不足的情况，最终达到根据堆内存的情况来做一些处理的操作
public abstract class Reference<T> {

    /* A Reference instance is in one of four possible internal states:
     *
     *     Active: Subject to special treatment by the garbage collector.  Some
     *     time after the collector detects that the reachability of the
     *     referent has changed to the appropriate state, it changes the
     *     instance's state to either Pending or Inactive, depending upon
     *     whether or not the instance was registered with a queue when it was
     *     created.  In the former case it also adds the instance to the
     *     pending-Reference list.  Newly-created instances are Active.
     *
     *     Pending: An element of the pending-Reference list, waiting to be
     *     enqueued by the Reference-handler thread.  Unregistered instances
     *     are never in this state.
     *
     *     Enqueued: An element of the queue with which the instance was
     *     registered when it was created.  When an instance is removed from
     *     its ReferenceQueue, it is made Inactive.  Unregistered instances are
     *     never in this state.
     *
     *     Inactive: Nothing more to do.  Once an instance becomes Inactive its
     *     state will never change again.
     *
     * The state is encoded in the queue and next fields as follows:
     *
     *     Active: queue = ReferenceQueue with which instance is registered, or
     *     ReferenceQueue.NULL if it was not registered with a queue; next =
     *     null.
     *
     *     Pending: queue = ReferenceQueue with which instance is registered;
     *     next = this
     *
     *     Enqueued: queue = ReferenceQueue.ENQUEUED; next = Following instance
     *     in queue, or this if at end of list.
     *
     *     Inactive: queue = ReferenceQueue.NULL; next = this.
     *
     * With this scheme the collector need only examine the next field in order
     * to determine whether a Reference instance requires special treatment: If
     * the next field is null then the instance is active; if it is non-null,
     * then the collector should treat the instance normally.
     *
     * To ensure that a concurrent collector can discover active Reference
     * objects without interfering with application threads that may apply
     * the enqueue() method to those objects, collectors should link
     * discovered objects through the discovered field. The discovered
     * field is also used for linking Reference objects in the pending list.
     */

    /* Reference实例有四种内部的状态
     *      Active: 新建Reference的实例，状态为Active。当GC检测到Reference引用referent的可达到状态
     *      发生了改变时，更新Reference的状态为Pending或Inactive。这个取决于创建Reference实例时是否注
     *      册过ReferenceQueue。若注册过，其状态会转换为Pending，同时GC会将其加入pending-Reference链
     *      表中，否则为转换为Inactive状态
     *
     *      Pending: 代表Reference是pending链表的成员。等待ReferenceHandler线程调用Cleaner#clean
     *      或ReferenceQueue#enqueue操作。 未注册过ReferenceQueue的实例不会达到这个状态
     *
     *      Enqueued: 代表pending队列中元素已经被加入ReferenceQueue队列。当其从队列消费后，其状态会变
     *      为Inactive。 未注册过ReferenceQueue的实例不会达到这个状态
     *
     *      Inactive: 什么也不会做，一旦处理该状态，就不可再转换
     *
     * 不同状态时，Reference对应的queue与成员next变量值(next可理解为ReferenceQueue中的下个结点的引用):
     *
     *      Active: queue为Reference实例被创建时注册的ReferenceQueue，如果没注册为null。此时，next为null，
     *      Reference实例与queue真正产生关系。
     *      Pending: queue为Reference实例被创建时注册的ReferenceQueue。next为当前实例本身。
     *      Enqueued: queue为ReferenceQueue.ENQUEUED代表当前实例已入队列。next为queue中的下一实列结点，
     *
     * 如果是queue尾部则为当前实例本身
     *      Inactive: queue为ReferenceQueue.NULL，当前实例已从queue中移除与queue无关联。next为当前实例本身
     */

    // 被引用的对象
    // 注：当JVM发生GC时，将被回收的对象引用在被加入到|pending|队列前，由GC将其设置为null
    private T referent;         /* Treated specially by GC */

    // 用户提供的用于收集被GC回收的对象引用队列
    // 注：在|ReferenceHandler|线程中，会不断的尝试将|pending|队列中的元素，转移至该用户队列
    volatile ReferenceQueue<? super T> queue;

    /* When active:   NULL
     *     pending:   this
     *    Enqueued:   next reference in queue (or this if last)
     *    Inactive:   this
     */
    // 可理解为用户队列的下一个结点的引用
    @SuppressWarnings("rawtypes")
    Reference next;

    /* When active:   next element in a discovered reference list maintained by GC (or this if last)
     *     pending:   next element in the pending list (or null if last)
     *   otherwise:   NULL
     */
    // 由VM维护。取值会根据Reference不同状态发生改变：
    // 状态为active时，代表由GC维护的discovered-Reference链表的下个节点，如果是尾部则为当前实例本身
    // 状态为pending时，代表pending-Reference的下个节点的引用。否则为null
    transient private Reference<T> discovered;  /* used by VM */


    /* Object used to synchronize with the garbage collector.  The collector
     * must acquire this lock at the beginning of each collection cycle.  It is
     * therefore critical that any code holding this lock complete as quickly
     * as possible, allocate no new objects, and avoid calling user code.
     */
    // 在Young gc时，会尝试获取该静态的全局锁。它被用于确保静态字段|pending|的线程安全
    static private class Lock { };
    private static Lock lock = new Lock();


    /* List of References waiting to be enqueued.  The collector adds
     * References to this list, while the Reference-handler thread removes
     * them.  This list is protected by the above lock object. The
     * list uses the discovered field to link its elements.
     */
    // 当JVM GC回收|referent|后，会将其加入到该全局的链表中，同时再唤醒|ReferenceHandler|线程
    // 注：Native层，在GC时，将需要被回收的Reference对象加入到DiscoveredList中，然后将DiscoveredList的元素移动
    // 到PendingList中，PendingList的队首就是|Reference.pending|字段
    // 注：Java层，线程|ReferenceHandler|流程是：提取|pending|队列中的元素，将其加入到用户的ReferenceQueue队列
    // 注：总流程：reference-JVM->pending-JAVA->referenceQueue
    private static Reference<Object> pending = null;

    /* High-priority thread to enqueue pending References
     */
    private static class ReferenceHandler extends Thread {

        ReferenceHandler(ThreadGroup g, String name) {
            super(g, name);
        }

        public void run() {
            for (;;) {
                Reference<Object> r;
                synchronized (lock) {
                    if (pending != null) {
                        // 获取|pending|一个元素
                        r = pending;
                        // 重置|pending|下一个元素指针，同时将r从pending链上断开
                        // 注：当状态为pending时，discovered代表pending的下个节点的引用
                        pending = r.discovered;
                        r.discovered = null;
                    } else {
                        // The waiting on the lock may cause an OOME because it may try to allocate
                        // exception objects, so also catch OOME here to avoid silent exit of the
                        // reference handler thread.
                        //
                        // Explicitly define the order of the two exceptions we catch here
                        // when waiting for the lock.
                        //
                        // We do not want to try to potentially load the InterruptedException class
                        // (which would be done if this was its first use, and InterruptedException
                        // were checked first) in this situation.
                        //
                        // This may lead to the VM not ever trying to load the InterruptedException
                        // class again.
                        try {
                            try {
                                // 等待通知
                                lock.wait();
                            } catch (OutOfMemoryError x) { }
                        } catch (InterruptedException x) { }
                        continue;
                    }
                }

                // Fast path for cleaners
                // 如果是Cleaner类型的Reference，调用其clean方法并退出
                if (r instanceof Cleaner) {
                    ((Cleaner)r).clean();
                    continue;
                }

                // 若注册过ReferenceQueue，将|pending|队列中的元素|r|，加入到用户的|ReferenceQueue|队列
                ReferenceQueue<Object> q = r.queue;
                if (q != ReferenceQueue.NULL) q.enqueue(r);
            }
        }
    }

    // 启动|ReferenceHandler|后台线程
    static {
        ThreadGroup tg = Thread.currentThread().getThreadGroup();
        for (ThreadGroup tgn = tg;
             tgn != null;
             tg = tgn, tgn = tg.getParent());
        Thread handler = new ReferenceHandler(tg, "Reference Handler");
        /* If there were a special system-only priority greater than
         * MAX_PRIORITY, it would be used here
         */
        // 设置为最高优先级
        handler.setPriority(Thread.MAX_PRIORITY);
        handler.setDaemon(true);
        handler.start();
    }


    /* -- Referent accessor and setters -- */

    /**
     * Returns this reference object's referent.  If this reference object has
     * been cleared, either by the program or by the garbage collector, then
     * this method returns <code>null</code>.
     *
     * @return   The object to which this reference refers, or
     *           <code>null</code> if this reference object has been cleared
     */
    public T get() {
        return this.referent;
    }

    /**
     * Clears this reference object.  Invoking this method will not cause this
     * object to be enqueued.
     *
     * <p> This method is invoked only by Java code; when the garbage collector
     * clears references it does so directly, without invoking this method.
     */
    public void clear() {
        this.referent = null;
    }


    /* -- Queue operations -- */

    /**
     * Tells whether or not this reference object has been enqueued, either by
     * the program or by the garbage collector.  If this reference object was
     * not registered with a queue when it was created, then this method will
     * always return <code>false</code>.
     *
     * @return   <code>true</code> if and only if this reference object has
     *           been enqueued
     */
    public boolean isEnqueued() {
        return (this.queue == ReferenceQueue.ENQUEUED);
    }

    /**
     * Adds this reference object to the queue with which it is registered,
     * if any.
     *
     * <p> This method is invoked only by Java code; when the garbage collector
     * enqueues references it does so directly, without invoking this method.
     *
     * @return   <code>true</code> if this reference object was successfully
     *           enqueued; <code>false</code> if it was already enqueued or if
     *           it was not registered with a queue when it was created
     */
    public boolean enqueue() {
        return this.queue.enqueue(this);
    }


    /* -- Constructors -- */

    // 指定被引用的对象
    Reference(T referent) {
        this(referent, null);
    }

    // 指定被引用的对象、和用于存储可达到性变更的对象的队列
    Reference(T referent, ReferenceQueue<? super T> queue) {
        this.referent = referent;
        this.queue = (queue == null) ? ReferenceQueue.NULL : queue;
    }

}
