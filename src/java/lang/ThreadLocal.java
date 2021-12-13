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

package java.lang;
import java.lang.ref.*;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * This class provides thread-local variables.  These variables differ from
 * their normal counterparts in that each thread that accesses one (via its
 * {@code get} or {@code set} method) has its own, independently initialized
 * copy of the variable.  {@code ThreadLocal} instances are typically private
 * static fields in classes that wish to associate state with a thread (e.g.,
 * a user ID or Transaction ID).
 *
 * <p>For example, the class below generates unique identifiers local to each
 * thread.
 * A thread's id is assigned the first time it invokes {@code ThreadId.get()}
 * and remains unchanged on subsequent calls.
 * <pre>
 * import java.util.concurrent.atomic.AtomicInteger;
 *
 * public class ThreadId {
 *     // Atomic integer containing the next thread ID to be assigned
 *     private static final AtomicInteger nextId = new AtomicInteger(0);
 *
 *     // Thread local variable containing each thread's ID
 *     private static final ThreadLocal&lt;Integer&gt; threadId =
 *         new ThreadLocal&lt;Integer&gt;() {
 *             &#64;Override protected Integer initialValue() {
 *                 return nextId.getAndIncrement();
 *         }
 *     };
 *
 *     // Returns the current thread's unique ID, assigning it if necessary
 *     public static int get() {
 *         return threadId.get();
 *     }
 * }
 * </pre>
 * <p>Each thread holds an implicit reference to its copy of a thread-local
 * variable as long as the thread is alive and the {@code ThreadLocal}
 * instance is accessible; after a thread goes away, all of its copies of
 * thread-local instances are subject to garbage collection (unless other
 * references to these copies exist).
 *
 * @author  Josh Bloch and Doug Lea
 * @since   1.2
 */
// 每个线程都会保存着一份线程私有变量的副本，任何一个线程内对该线程私有变量的操作都只是针对这
// 个副本；也因此，它并不适用于值的更新需要对多线程可见的场景
// 注：每个线程对象都有一个|Thread.threadLocals|属性，它保存了当前线程中所有的线程私有变
// 量。它是一个轻量级的|HashMap|的实现（使用开放定址法解决冲突）。其中键为线程私有变量的引用，
// 值为线程私有变量需要设置的值
// 亮点：每个线程私有变量的键值对，会被包装成|Entry|实体，保存至|Thread.threadLocals|的
// 哈希表里。其|Entry.key|是对|ThreadLocal|的弱引用，当线程退出时，会让|key==null|，若
// 再次操作线程私有变量（不管是set、get），都可能会触发哈希表过时数据的清理，解决内存泄漏
// 亮点：在添加、更新、甚至是查找一个线程私有变量时，都有可能会触发当前线程对哈希表对过时条目
// 的清理算法。算法中扫描规则多样，特别是启发式扫描，兼顾内存与性能
//
// 注：线程私有变量在有些场景下存在"内存泄露"问题。比如以线程复用为主的线程池场景中，一个线程
// 的寿命很长，大对象长期不被回收，可能会影响系统运行效率与安全。所以在使用线程私有变量时，我
// 们应该要养成操作完后就立即删除它的习惯，并且最好在将线程返回到线程池之前，用|remove()|进
// 行删除。 注：如果线程用完即销毁，是不会有内存泄露问题
//
// 注：同一个线程私有变量在父线程中设置后，在子线程中是获取不到的。|InheritableThreadLocal|
public class ThreadLocal<T> {
    /**
     * ThreadLocals rely on per-thread linear-probe hash maps attached
     * to each thread (Thread.threadLocals and
     * inheritableThreadLocals).  The ThreadLocal objects act as keys,
     * searched via threadLocalHashCode.  This is a custom hash code
     * (useful only within ThreadLocalMaps) that eliminates collisions
     * in the common case where consecutively constructed ThreadLocals
     * are used by the same threads, while remaining well-behaved in
     * less common cases.
     */
    // 用于将一个线程私有变量、映射到、当前线程私有变量哈希表的某个桶的、哈希值
    private final int threadLocalHashCode = nextHashCode();

    /**
     * The next hash code to be given out. Updated atomically. Starts at
     * zero.
     */
    // 下一个线程私有变量的哈希值。是一个类级的、静态的、原子整型
    private static AtomicInteger nextHashCode =
        new AtomicInteger();

    /**
     * The difference between successively generated hash codes - turns
     * implicit sequential thread-local IDs into near-optimally spread
     * multiplicative hash values for power-of-two-sized tables.
     */
    // 每个线程私有变量生成哈希值的增量（步进、间隙）
    // 注：这个增量主要是为了让线程私有变量生成的哈希值，能均匀的分布在2^n次方的数组里
    // 注：这个魔数的选取与斐波那契散列法以及黄金分割有关。即：|0x61c88647=2^32*((Math.sqrt(5)-1)/2)|
    // @see https://www.javaspecialists.eu/archive/Issue164-Why-0x61c88647.html
    private static final int HASH_INCREMENT = 0x61c88647;

    /**
     * Returns the next hash code.
     */
    // 获取线程私有变量哈希值
    private static int nextHashCode() {
        return nextHashCode.getAndAdd(HASH_INCREMENT);
    }

    /**
     * Returns the current thread's "initial value" for this
     * thread-local variable.  This method will be invoked the first
     * time a thread accesses the variable with the {@link #get}
     * method, unless the thread previously invoked the {@link #set}
     * method, in which case the {@code initialValue} method will not
     * be invoked for the thread.  Normally, this method is invoked at
     * most once per thread, but it may be invoked again in case of
     * subsequent invocations of {@link #remove} followed by {@link #get}.
     *
     * <p>This implementation simply returns {@code null}; if the
     * programmer desires thread-local variables to have an initial
     * value other than {@code null}, {@code ThreadLocal} must be
     * subclassed, and this method overridden.  Typically, an
     * anonymous inner class will be used.
     *
     * @return the initial value for this thread-local
     */
    // 初始化线程私有变量的值的初始化方法。主要用于子类覆盖重写此方法
    protected T initialValue() {
        return null;
    }

    /**
     * Creates a thread local variable. The initial value of the variable is
     * determined by invoking the {@code get} method on the {@code Supplier}.
     *
     * @param <S> the type of the thread local's value
     * @param supplier the supplier to be used to determine the initial value
     * @return a new thread local variable
     * @throws NullPointerException if the specified supplier is null
     * @since 1.8
     */
    // 获取一个带有初始化线程私有变量对象方法的线程私有变量对象
    public static <S> ThreadLocal<S> withInitial(Supplier<? extends S> supplier) {
        return new SuppliedThreadLocal<>(supplier);
    }

    /**
     * Creates a thread local variable.
     * @see #withInitial(java.util.function.Supplier)
     */
    public ThreadLocal() {
    }

    /**
     * Returns the value in the current thread's copy of this
     * thread-local variable.  If the variable has no value for the
     * current thread, it is first initialized to the value returned
     * by an invocation of the {@link #initialValue} method.
     *
     * @return the current thread's value of this thread-local
     */
    public T get() {
        // 获取当前线程的线程私有变量哈希表
        Thread t = Thread.currentThread();
        ThreadLocalMap map = getMap(t);

        if (map != null) {
            // 获取当前线程私有变量对应的键值对实体
            ThreadLocalMap.Entry e = map.getEntry(this);

            // 找到线程私有变量实体，返回其中的值
            if (e != null) {
                @SuppressWarnings("unchecked")
                T result = (T)e.value;
                return result;
            }
        }

        // 调用用户提供的线程私有变量的初始化方法，并将私有变量键值对实体添加值哈希表中
        return setInitialValue();
    }

    /**
     * Variant of set() to establish initialValue. Used instead
     * of set() in case user has overridden the set() method.
     *
     * @return the initial value
     */
    // 自动调用线程私有变量的初始化值方法版本的|set()|方法
    private T setInitialValue() {
        // 调用线程私有变量的初始化值的方法
        // 注：通常情况下，用户会重写该方法；或者若用户使用|withInitial()|返回的对象，调
        // 用线程私有变量的设置方法，就会自动调用用户提供的初始化方法
        T value = initialValue();

        // 以下逻辑同|set()|方法
        Thread t = Thread.currentThread();
        ThreadLocalMap map = getMap(t);
        if (map != null)
            map.set(this, value);
        else
            createMap(t, value);
        return value;
    }

    /**
     * Sets the current thread's copy of this thread-local variable
     * to the specified value.  Most subclasses will have no need to
     * override this method, relying solely on the {@link #initialValue}
     * method to set the values of thread-locals.
     *
     * @param value the value to be stored in the current thread's copy of
     *        this thread-local.
     */
    public void set(T value) {
        // 获取当前线程的线程私有变量哈希表
        Thread t = Thread.currentThread();
        ThreadLocalMap map = getMap(t);

        if (map != null)
            // 添加、更新一个线程私有变量和值的键值对实体到哈希表中
            map.set(this, value);
        else
            // 初始化当前线程用于存放私有变量的哈希表，并添加当前私有变量的键值对实体
            createMap(t, value);
    }

    /**
     * Removes the current thread's value for this thread-local
     * variable.  If this thread-local variable is subsequently
     * {@linkplain #get read} by the current thread, its value will be
     * reinitialized by invoking its {@link #initialValue} method,
     * unless its value is {@linkplain #set set} by the current thread
     * in the interim.  This may result in multiple invocations of the
     * {@code initialValue} method in the current thread.
     *
     * @since 1.5
     */
    // 手动从当前线程的线程私有变量哈希表中，清除当前线程私有变量和值的键值对实体
    public void remove() {
         ThreadLocalMap m = getMap(Thread.currentThread());
         if (m != null)
             m.remove(this);
     }

    /**
     * Get the map associated with a ThreadLocal. Overridden in
     * InheritableThreadLocal.
     *
     * @param  t the current thread
     * @return the map
     */
    ThreadLocalMap getMap(Thread t) {
        return t.threadLocals;
    }

    /**
     * Create the map associated with a ThreadLocal. Overridden in
     * InheritableThreadLocal.
     *
     * @param t the current thread
     * @param firstValue value for the initial entry of the map
     */
    // 初始化当前线程用于存放所有私有变量的哈希表，并添加当前私有变量|t->firstValue|键值对
    void createMap(Thread t, T firstValue) {
        t.threadLocals = new ThreadLocalMap(this, firstValue);
    }

    /**
     * Factory method to create map of inherited thread locals.
     * Designed to be called only from Thread constructor.
     *
     * @param  parentMap the map associated with parent thread
     * @return a map containing the parent's inheritable bindings
     */
    // 在|Thread.init()|方法中，会将父线程中不为|null|的|inheritableThreadLocal|属性作
    // 为|createInheritedMap()|参数，创建一个线程私有变量哈希表，将其赋值给子线程|inheritableThreadLocal|
    // 有了它，我们就可以递归的访问父线程的私有变量了
    static ThreadLocalMap createInheritedMap(ThreadLocalMap parentMap) {
        return new ThreadLocalMap(parentMap);
    }

    /**
     * Method childValue is visibly defined in subclass
     * InheritableThreadLocal, but is internally defined here for the
     * sake of providing createInheritedMap factory method without
     * needing to subclass the map class in InheritableThreadLocal.
     * This technique is preferable to the alternative of embedding
     * instanceof tests in methods.
     */
    T childValue(T parentValue) {
        throw new UnsupportedOperationException();
    }

    /**
     * An extension of ThreadLocal that obtains its initial value from
     * the specified {@code Supplier}.
     */
    // 一个带有初始化线程私有变量对象方法的线程私有变量对象
    static final class SuppliedThreadLocal<T> extends ThreadLocal<T> {

        private final Supplier<? extends T> supplier;

        SuppliedThreadLocal(Supplier<? extends T> supplier) {
            this.supplier = Objects.requireNonNull(supplier);
        }

        @Override
        protected T initialValue() {
            return supplier.get();
        }
    }

    /**
     * ThreadLocalMap is a customized hash map suitable only for
     * maintaining thread local values. No operations are exported
     * outside of the ThreadLocal class. The class is package private to
     * allow declaration of fields in class Thread.  To help deal with
     * very large and long-lived usages, the hash table entries use
     * WeakReferences for keys. However, since reference queues are not
     * used, stale entries are guaranteed to be removed only when
     * the table starts running out of space.
     */
    static class ThreadLocalMap {

        /**
         * The entries in this hash map extend WeakReference, using
         * its main ref field as the key (which is always a
         * ThreadLocal object).  Note that null keys (i.e. entry.get()
         * == null) mean that the key is no longer referenced, so the
         * entry can be expunged from table.  Such entries are referred to
         * as "stale entries" in the code that follows.
         */
        // 存储线程私有变量的数据结构
        static class Entry extends WeakReference<ThreadLocal<?>> {
            /** The value associated with this ThreadLocal. */
            Object value;

            Entry(ThreadLocal<?> k, Object v) {
                super(k);
                value = v;
            }
        }

        /**
         * The initial capacity -- MUST be a power of two.
         */
        private static final int INITIAL_CAPACITY = 16;

        /**
         * The table, resized as necessary.
         * table.length MUST always be a power of two.
         */
        // 哈希表
        private Entry[] table;

        /**
         * The number of entries in the table.
         */
        private int size = 0;

        /**
         * The next size value at which to resize.
         */
        private int threshold; // Default to 0

        /**
         * Set the resize threshold to maintain at worst a 2/3 load factor.
         */
        // 哈希表清除过期条目、rehash的阈值
        // 注：当|size>=threshold|时，每次新增、修改线程私有地变量时，会触发哈希表清除、rehash所
        // 有过时条目。当清除后，|size>=threshold*3/4|时，将触发扩容
        private void setThreshold(int len) {
            threshold = len * 2 / 3;
        }

        /**
         * Increment i modulo len.
         */
        private static int nextIndex(int i, int len) {
            return ((i + 1 < len) ? i + 1 : 0);
        }

        /**
         * Decrement i modulo len.
         */
        private static int prevIndex(int i, int len) {
            return ((i - 1 >= 0) ? i - 1 : len - 1);
        }

        /**
         * Construct a new map initially containing (firstKey, firstValue).
         * ThreadLocalMaps are constructed lazily, so we only create
         * one when we have at least one entry to put in it.
         */
        // 初始化当前线程用于存放所有私有变量的哈希表，并添加当前私有变量|t->firstValue|键值对
        ThreadLocalMap(ThreadLocal<?> firstKey, Object firstValue) {
            table = new Entry[INITIAL_CAPACITY];
            int i = firstKey.threadLocalHashCode & (INITIAL_CAPACITY - 1);

            // 将映射桶设置为线程私有变量和值的键值对
            table[i] = new Entry(firstKey, firstValue);
            size = 1;

            // 设置扩容阈值
            setThreshold(INITIAL_CAPACITY);
        }

        /**
         * Construct a new map including all Inheritable ThreadLocals
         * from given parent map. Called only by createInheritedMap.
         *
         * @param parentMap the map associated with parent thread.
         */
        // 遍历拷贝父线程中的线程私有变量哈希表
        private ThreadLocalMap(ThreadLocalMap parentMap) {
            Entry[] parentTable = parentMap.table;
            int len = parentTable.length;
            setThreshold(len);
            table = new Entry[len];

            // 遍历拷贝父线程中的线程私有变量哈希表
            for (int j = 0; j < len; j++) {
                Entry e = parentTable[j];
                if (e != null) {
                    @SuppressWarnings("unchecked")
                    ThreadLocal<Object> key = (ThreadLocal<Object>) e.get();
                    if (key != null) {
                        Object value = key.childValue(e.value);
                        Entry c = new Entry(key, value);
                        int h = key.threadLocalHashCode & (len - 1);
                        while (table[h] != null)
                            h = nextIndex(h, len);
                        table[h] = c;
                        size++;
                    }
                }
            }
        }

        /**
         * Get the entry associated with key.  This method
         * itself handles only the fast path: a direct hit of existing
         * key. It otherwise relays to getEntryAfterMiss.  This is
         * designed to maximize performance for direct hits, in part
         * by making this method readily inlinable.
         *
         * @param  key the thread local object
         * @return the entry associated with key, or null if no such
         */
        private Entry getEntry(ThreadLocal<?> key) {
            int i = key.threadLocalHashCode & (table.length - 1);
            Entry e = table[i];
            if (e != null && e.get() == key)
                return e;
            else
                return getEntryAfterMiss(key, i, e);
        }

        /**
         * Version of getEntry method for use when key is not found in
         * its direct hash slot.
         *
         * @param  key the thread local object
         * @param  i the table index for key's hash code
         * @param  e the entry at table[i]
         * @return the entry associated with key, or null if no such
         */
        private Entry getEntryAfterMiss(ThreadLocal<?> key, int i, Entry e) {
            Entry[] tab = table;
            int len = tab.length;

            while (e != null) {
                ThreadLocal<?> k = e.get();
                if (k == key)
                    return e;
                if (k == null)
                    expungeStaleEntry(i);
                else
                    i = nextIndex(i, len);
                e = tab[i];
            }
            return null;
        }

        /**
         * Set the value associated with key.
         *
         * @param key the thread local object
         * @param value the value to be set
         */
        // 添加、更新一个线程私有变量和值的键值对到哈希表中
        private void set(ThreadLocal<?> key, Object value) {

            // We don't use a fast path as with get() because it is at
            // least as common to use set() to create new entries as
            // it is to replace existing ones, in which case, a fast
            // path would fail more often than not.

            Entry[] tab = table;
            int len = tab.length;
            int i = key.threadLocalHashCode & (len-1);

            // 从映射位置|i|偏移开始，遍历哈希表所有映射的槽
            // 注：使用开放定址法解决哈希冲突
            for (Entry e = tab[i];
                 e != null;
                 e = tab[i = nextIndex(i, len)]) {
                // 取出映射的槽中元素对应的本地变量对象引用
                ThreadLocal<?> k = e.get();

                // 若与当前线程私有变量相同（引用地址比较），则覆盖掉之前保存的值
                if (k == key) {
                    e.value = value;
                    return;
                }

                // 若映射的槽不为空，但线程私有变量对象已经为空，说明|e|是一个过时的条目
                // 注：从映射位置|i|偏移开始，遍历删除所有|entry.key==null|的条目，并插入新的条目
                if (k == null) {
                    replaceStaleEntry(key, value, i);
                    return;
                }
            }

            // 将线程私有变量的键值对|key->value|存放值空槽中
            tab[i] = new Entry(key, value);
            int sz = ++size;

            // 清除部分过时条目。如果没有清除任何条目，且当前线程私有变量个数超过容量的2/3，尝试清除
            // 哈希表中所有过时条目；若当前线程私有变量个数超过容量的3/4，进行扩容
            if (!cleanSomeSlots(i, sz) && sz >= threshold)
                rehash();
        }

        /**
         * Remove the entry for key.
         */
        // 手动从当前线程的线程私有变量哈希表中，清除线程私有变量|key|的条目
        private void remove(ThreadLocal<?> key) {
            Entry[] tab = table;
            int len = tab.length;
            int i = key.threadLocalHashCode & (len-1);
            for (Entry e = tab[i];
                 e != null;
                 e = tab[i = nextIndex(i, len)]) {
                if (e.get() == key) {
                    // 手动清除线程对象的引用值
                    e.clear();
                    // 清除该|i|位置的已过时的条目
                    expungeStaleEntry(i);
                    return;
                }
            }
        }

        /**
         * Replace a stale entry encountered during a set operation
         * with an entry for the specified key.  The value passed in
         * the value parameter is stored in the entry, whether or not
         * an entry already exists for the specified key.
         *
         * As a side effect, this method expunges all stale entries in the
         * "run" containing the stale entry.  (A run is a sequence of entries
         * between two null slots.)
         *
         * @param  key the key
         * @param  value the value to be associated with key
         * @param  staleSlot index of the first stale entry encountered while
         *         searching for key.
         */
        // 从位置|staleSlot|偏移开始，查找线程私有变量在哈希表中原始位置，将其更新为新的值；如果
        // 不存在该私有变量，新的私有变量键值对将被放入|staleSlot|槽。
        // 注：此方法还会清除部分过期条目。清除起始索引规则为：从|staleSlot|开始向前遍历，直至遇
        // 到第一个过时条目为止的索引；若前面无过时条目，则向后遍历查找，此时就会清除全部过期条目
        private void replaceStaleEntry(ThreadLocal<?> key, Object value,
                                       int staleSlot) {
            Entry[] tab = table;
            int len = tab.length;
            Entry e;

            // Back up to check for prior stale entry in current run.
            // We clean out whole runs at a time to avoid continual
            // incremental rehashing due to garbage collector freeing
            // up refs in bunches (i.e., whenever the collector runs).
            // 备份从位置|i|偏移开始的前一个过时条目索引
            int slotToExpunge = staleSlot;
            for (int i = prevIndex(staleSlot, len);
                 (e = tab[i]) != null;
                 i = prevIndex(i, len))
                if (e.get() == null)
                    slotToExpunge = i;

            // Find either the key or trailing null slot of run, whichever
            // occurs first
            // 向后遍历哈希表，直到遇见空槽为止，找到第一个与|key|相同的槽
            // 注：开放地址法解决冲突
            for (int i = nextIndex(staleSlot, len);
                 (e = tab[i]) != null;
                 i = nextIndex(i, len)) {
                ThreadLocal<?> k = e.get();

                // If we find key, then we need to swap it
                // with the stale entry to maintain hash table order.
                // The newly stale slot, or any other stale slot
                // encountered above it, can then be sent to expungeStaleEntry
                // to remove or rehash all of the other entries in run.
                // 更新线程私有变量中的值
                if (k == key) {
                    e.value = value;

                    // 将当前的线程私有变量条目，替换到|staleSlot|对应的槽中
                    // 注：参数|staleSlot|映射槽是原本就当前的线程私有变量条目应该在映射的位
                    // 置，因为它本来就是一个过时的条目，替换没有副作用
                    tab[i] = tab[staleSlot];
                    tab[staleSlot] = e;

                    // Start expunge at preceding stale entry if it exists
                    // 如果|staleSlot|就是该哈希表第一个过时的条目，则从该条目开始清除
                    // 注：该条目是刚被替换过来的待清除的过期条目
                    if (slotToExpunge == staleSlot)
                        slotToExpunge = i;

                    // 从第一个过时条目开始清除，内部有遍历逻辑
                    cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
                    return;
                }

                // If we didn't find stale entry on backward scan, the
                // first stale entry seen while scanning for key is the
                // first still present in the run.
                // 如果|staleSlot|就是该哈希表第一个过时的条目，且在向后遍历过程中发现新的过
                // 期条目，更新清除过期条目的开始索引
                // 注：索引|staleSlot|的对应的槽，一定会被新的私有变量填充。所以在没有前向的
                // 过时条目场景下，将过期条目向后更新。此时就会清除全部过期条目
                if (k == null && slotToExpunge == staleSlot)
                    slotToExpunge = i;
            }

            // If key not found, put new entry in stale slot
            // 线程私有变量在哈希表中不存在，在|staleSlot|位置新建该键值对
            tab[staleSlot].value = null;
            tab[staleSlot] = new Entry(key, value);

            // If there are any other stale entries in run, expunge them
            // 如果第一个过时条目的索引和|staleSlot|索引不同，说明哈希表中至少有一个过时条目
            // 注：索引|staleSlot|的对应的槽，一定会被新的私有变量填充。所以要判断哈希表是否
            // 有过时条目，需要有此判断逻辑
            if (slotToExpunge != staleSlot)
                cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
        }

        /**
         * Expunge a stale entry by rehashing any possibly colliding entries
         * lying between staleSlot and the next null slot.  This also expunges
         * any other stale entries encountered before the trailing null.  See
         * Knuth, Section 6.4
         *
         * @param staleSlot index of slot known to have null key
         * @return the index of the next null slot after staleSlot
         * (all between staleSlot and this slot will have been checked
         * for expunging).
         */
        // 清除|staleSlot|位置的过时条目；并从|staleSlot|下一个位置开始遍历，直到遇到空槽为止，重
        // 新rehash哈希表中的条目
        // 注：键|key|被GC回收删除，此方法用于删除与之关联的|value|
        private int expungeStaleEntry(int staleSlot) {
            Entry[] tab = table;
            int len = tab.length;

            // expunge entry at staleSlot
            // 清除位于|staleSlot|的条目
            tab[staleSlot].value = null;
            tab[staleSlot] = null;
            size--;

            // Rehash until we encounter null
            Entry e;
            int i;
            for (i = nextIndex(staleSlot, len);
                 (e = tab[i]) != null;
                 i = nextIndex(i, len)) {
                // 取出映射的槽中元素对应的本地变量对象引用
                ThreadLocal<?> k = e.get();

                if (k == null) {    // 清除过时条目
                    e.value = null;
                    tab[i] = null;
                    size--;
                } else {
                    int h = k.threadLocalHashCode & (len - 1);
                    if (h != i) {   // rehash后，发现槽为已变更
                        tab[i] = null;  // 清除原始条目，以准备迁移到新的槽中

                        // Unlike Knuth 6.4 Algorithm R, we must scan until
                        // null because multiple entries could have been stale.
                        // 从新的槽为开始向后遍历，找到一个空槽位，以防止|e|条目
                        // 注：开放地址法解决冲突
                        while (tab[h] != null)
                            h = nextIndex(h, len);
                        tab[h] = e;
                    }
                }
            }

            // 返回|staleSlot|后第一个空槽索引
            return i;
        }

        /**
         * Heuristically scan some cells looking for stale entries.
         * This is invoked when either a new element is added, or
         * another stale one has been expunged. It performs a
         * logarithmic number of scans, as a balance between no
         * scanning (fast but retains garbage) and a number of scans
         * proportional to number of elements, that would find all
         * garbage but would cause some insertions to take O(n) time.
         *
         * @param i a position known NOT to hold a stale entry. The
         * scan starts at the element after i.
         *
         * @param n scan control: {@code log2(n)} cells are scanned,
         * unless a stale entry is found, in which case
         * {@code log2(table.length)-1} additional cells are scanned.
         * When called from insertions, this parameter is the number
         * of elements, but when from replaceStaleEntry, it is the
         * table length. (Note: all this could be changed to be either
         * more or less aggressive by weighting n instead of just
         * using straight log n. But this version is simple, fast, and
         * seems to work well.)
         *
         * @return true if any stale entries have been removed.
         */
        // 启发式扫描一些槽，以清除过时的条目。在添加新元素、或删除另一个过时元素时会调用此方法
        // 注：从一个已知的不会过时条目|i|位置开始扫描
        // 注：扫描|log2(n)|个槽位；除非找到过时的条目，此时将扫描|log2(table.length)-1|个
        // 附加槽。从插入中调用时，|n|是元素数，但从|replaceStaleEntry|中调用时，它是表长度
        // 注：设计如此扫描机制，主要是为了让扫描次数与元素数量成正比之间，让数据清理与扫描做个平
        // 衡。如果每次都需要找到所有垃圾，就会导致某些插入将花费O(n)
        private boolean cleanSomeSlots(int i, int n) {
            // 至少清理了一个过时数据
            boolean removed = false;
            Entry[] tab = table;
            int len = tab.length;
            do {
                i = nextIndex(i, len);
                Entry e = tab[i];
                // 发现了过时的条目
                if (e != null && e.get() == null) {
                    // 重置n值，以附加扫描|log2(table.length)-1|个槽
                    n = len;
                    removed = true;
                    // 清除|i|位置的过时条目，并重新rehash哈希表部分条目，返回下一个空槽索引
                    i = expungeStaleEntry(i);
                }
            } while ( (n >>>= 1) != 0);

            // 如果删除了任何过时条目，返回true
            return removed;
        }

        /**
         * Re-pack and/or re-size the table. First scan the entire
         * table removing stale entries. If this doesn't sufficiently
         * shrink the size of the table, double the table size.
         */
        private void rehash() {
            // 清除哈希表中的所有过时条目，并rehash表中所有条目
            expungeStaleEntries();

            // Use lower threshold for doubling to avoid hysteresis
            // 若当前线程私有变量个数超过容量的3/4，执行扩容
            if (size >= threshold - threshold / 4)
                resize();
        }

        /**
         * Double the capacity of the table.
         */
        private void resize() {
            Entry[] oldTab = table;
            int oldLen = oldTab.length;
            int newLen = oldLen * 2;
            Entry[] newTab = new Entry[newLen];
            int count = 0;

            for (int j = 0; j < oldLen; ++j) {
                Entry e = oldTab[j];
                if (e != null) {
                    ThreadLocal<?> k = e.get();
                    if (k == null) {
                        e.value = null; // Help the GC
                    } else {
                        int h = k.threadLocalHashCode & (newLen - 1);
                        while (newTab[h] != null)
                            h = nextIndex(h, newLen);
                        newTab[h] = e;
                        count++;
                    }
                }
            }

            setThreshold(newLen);
            size = count;
            table = newTab;
        }

        /**
         * Expunge all stale entries in the table.
         */
        // 清除哈希表中的所有过时条目，并rehash表中所有条目
        // 注：键|key|被GC回收删除，此方法用于删除与之关联的|value|
        private void expungeStaleEntries() {
            Entry[] tab = table;
            int len = tab.length;
            for (int j = 0; j < len; j++) {
                Entry e = tab[j];
                if (e != null && e.get() == null)
                    expungeStaleEntry(j);
            }
        }
    }
}
