/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.util;

import java.util.function.Consumer;

/**
 * An unbounded priority {@linkplain Queue queue} based on a priority heap.
 * The elements of the priority queue are ordered according to their
 * {@linkplain Comparable natural ordering}, or by a {@link Comparator}
 * provided at queue construction time, depending on which constructor is
 * used.  A priority queue does not permit {@code null} elements.
 * A priority queue relying on natural ordering also does not permit
 * insertion of non-comparable objects (doing so may result in
 * {@code ClassCastException}).
 *
 * <p>The <em>head</em> of this queue is the <em>least</em> element
 * with respect to the specified ordering.  If multiple elements are
 * tied for least value, the head is one of those elements -- ties are
 * broken arbitrarily.  The queue retrieval operations {@code poll},
 * {@code remove}, {@code peek}, and {@code element} access the
 * element at the head of the queue.
 *
 * <p>A priority queue is unbounded, but has an internal
 * <i>capacity</i> governing the size of an array used to store the
 * elements on the queue.  It is always at least as large as the queue
 * size.  As elements are added to a priority queue, its capacity
 * grows automatically.  The details of the growth policy are not
 * specified.
 *
 * <p>This class and its iterator implement all of the
 * <em>optional</em> methods of the {@link Collection} and {@link
 * Iterator} interfaces.  The Iterator provided in method {@link
 * #iterator()} is <em>not</em> guaranteed to traverse the elements of
 * the priority queue in any particular order. If you need ordered
 * traversal, consider using {@code Arrays.sort(pq.toArray())}.
 *
 * <p><strong>Note that this implementation is not synchronized.</strong>
 * Multiple threads should not access a {@code PriorityQueue}
 * instance concurrently if any of the threads modifies the queue.
 * Instead, use the thread-safe {@link
 * java.util.concurrent.PriorityBlockingQueue} class.
 *
 * <p>Implementation note: this implementation provides
 * O(log(n)) time for the enqueuing and dequeuing methods
 * ({@code offer}, {@code poll}, {@code remove()} and {@code add});
 * linear time for the {@code remove(Object)} and {@code contains(Object)}
 * methods; and constant time for the retrieval methods
 * ({@code peek}, {@code element}, and {@code size}).
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @since 1.5
 * @author Josh Bloch, Doug Lea
 * @param <E> the type of elements held in this collection
 */
// 高效的最小堆实现的优先队列。实现了 Queue 接口
// 注：最小堆是任一非终端节点的数据值均不大于其子节点的值；添加、删除的时间复杂度为 O(logn)
public class PriorityQueue<E> extends AbstractQueue<E>
    implements java.io.Serializable {

    private static final long serialVersionUID = -7720805057305804111L;

    // 队列默认容量
    private static final int DEFAULT_INITIAL_CAPACITY = 11;

    /**
     * Priority queue represented as a balanced binary heap: the two
     * children of queue[n] are queue[2*n+1] and queue[2*(n+1)].  The
     * priority queue is ordered by comparator, or by the elements'
     * natural ordering, if comparator is null: For each node n in the
     * heap and each descendant d of n, n <= d.  The element with the
     * lowest value is in queue[0], assuming the queue is nonempty.
     */
    transient Object[] queue; // non-private to simplify nested class access

    /**
     * The number of elements in the priority queue.
     */
    private int size = 0;

    /**
     * The comparator, or null if priority queue uses elements'
     * natural ordering.
     */
    // 比较器实例。为空时，使用队列元素的自身的比较方法
    private final Comparator<? super E> comparator;

    /**
     * The number of times this priority queue has been
     * <i>structurally modified</i>.  See AbstractList for gory details.
     */
    // 容器结构是否有变化的标志位
    transient int modCount = 0; // non-private to simplify nested class access

    /**
     * Creates a {@code PriorityQueue} with the default initial
     * capacity (11) that orders its elements according to their
     * {@linkplain Comparable natural ordering}.
     */
    public PriorityQueue() {
        this(DEFAULT_INITIAL_CAPACITY, null);
    }

    /**
     * Creates a {@code PriorityQueue} with the specified initial
     * capacity that orders its elements according to their
     * {@linkplain Comparable natural ordering}.
     *
     * @param initialCapacity the initial capacity for this priority queue
     * @throws IllegalArgumentException if {@code initialCapacity} is less
     *         than 1
     */
    public PriorityQueue(int initialCapacity) {
        this(initialCapacity, null);
    }

    /**
     * Creates a {@code PriorityQueue} with the default initial capacity and
     * whose elements are ordered according to the specified comparator.
     *
     * @param  comparator the comparator that will be used to order this
     *         priority queue.  If {@code null}, the {@linkplain Comparable
     *         natural ordering} of the elements will be used.
     * @since 1.8
     */
    // 构造一个带有自定义比较器的优先队列
    public PriorityQueue(Comparator<? super E> comparator) {
        this(DEFAULT_INITIAL_CAPACITY, comparator);
    }

    /**
     * Creates a {@code PriorityQueue} with the specified initial capacity
     * that orders its elements according to the specified comparator.
     *
     * @param  initialCapacity the initial capacity for this priority queue
     * @param  comparator the comparator that will be used to order this
     *         priority queue.  If {@code null}, the {@linkplain Comparable
     *         natural ordering} of the elements will be used.
     * @throws IllegalArgumentException if {@code initialCapacity} is
     *         less than 1
     */
    // 构造一个指定初始容量、带有自定义比较器的优先队列
    public PriorityQueue(int initialCapacity,
                         Comparator<? super E> comparator) {
        // Note: This restriction of at least one is not actually needed,
        // but continues for 1.5 compatibility
        if (initialCapacity < 1)
            throw new IllegalArgumentException();
        this.queue = new Object[initialCapacity];
        this.comparator = comparator;
    }

    /**
     * Creates a {@code PriorityQueue} containing the elements in the
     * specified collection.  If the specified collection is an instance of
     * a {@link SortedSet} or is another {@code PriorityQueue}, this
     * priority queue will be ordered according to the same ordering.
     * Otherwise, this priority queue will be ordered according to the
     * {@linkplain Comparable natural ordering} of its elements.
     *
     * @param  c the collection whose elements are to be placed
     *         into this priority queue
     * @throws ClassCastException if elements of the specified collection
     *         cannot be compared to one another according to the priority
     *         queue's ordering
     * @throws NullPointerException if the specified collection or any
     *         of its elements are null
     */
    // 构造一个优先队列，其中的数据来自集合|c|中的元素
    @SuppressWarnings("unchecked")
    public PriorityQueue(Collection<? extends E> c) {
        if (c instanceof SortedSet<?>) {
            // 数据来自已排序的容器
            SortedSet<? extends E> ss = (SortedSet<? extends E>) c;
            this.comparator = (Comparator<? super E>) ss.comparator();
            initElementsFromCollection(ss);
        }
        else if (c instanceof PriorityQueue<?>) {
            // 数据来自另一个优先队列
            PriorityQueue<? extends E> pq = (PriorityQueue<? extends E>) c;
            this.comparator = (Comparator<? super E>) pq.comparator();
            initFromPriorityQueue(pq);
        }
        else {
            // 数据来自普通集合
            this.comparator = null;
            initFromCollection(c);
        }
    }

    /**
     * Creates a {@code PriorityQueue} containing the elements in the
     * specified priority queue.  This priority queue will be
     * ordered according to the same ordering as the given priority
     * queue.
     *
     * @param  c the priority queue whose elements are to be placed
     *         into this priority queue
     * @throws ClassCastException if elements of {@code c} cannot be
     *         compared to one another according to {@code c}'s
     *         ordering
     * @throws NullPointerException if the specified priority queue or any
     *         of its elements are null
     */
    @SuppressWarnings("unchecked")
    public PriorityQueue(PriorityQueue<? extends E> c) {
        this.comparator = (Comparator<? super E>) c.comparator();
        initFromPriorityQueue(c);
    }

    /**
     * Creates a {@code PriorityQueue} containing the elements in the
     * specified sorted set.   This priority queue will be ordered
     * according to the same ordering as the given sorted set.
     *
     * @param  c the sorted set whose elements are to be placed
     *         into this priority queue
     * @throws ClassCastException if elements of the specified sorted
     *         set cannot be compared to one another according to the
     *         sorted set's ordering
     * @throws NullPointerException if the specified sorted set or any
     *         of its elements are null
     */
    @SuppressWarnings("unchecked")
    public PriorityQueue(SortedSet<? extends E> c) {
        this.comparator = (Comparator<? super E>) c.comparator();
        initElementsFromCollection(c);
    }

    // 初始化优先队列，其中的数据来自优先队列|c|中的元素
    private void initFromPriorityQueue(PriorityQueue<? extends E> c) {
        if (c.getClass() == PriorityQueue.class) {
            this.queue = c.toArray();
            this.size = c.size();
        } else {
            initFromCollection(c);
        }
    }

    // 初始化优先队列，其中的数据来自集合|c|中的元素
    // 注：若|c|时普通集合，需要调用heapify()进行重建最小堆
    private void initElementsFromCollection(Collection<? extends E> c) {
        Object[] a = c.toArray();
        // If c.toArray incorrectly doesn't return Object[], copy it.
        // 获取容器|c|中所有元素，类型通常为Object[].class
        // 注：Arrays.asList()创建的列表，其c.toArray()返回的不是Object[]数组，而是T[]数组
        if (a.getClass() != Object[].class)
            a = Arrays.copyOf(a, a.length, Object[].class);
        int len = a.length;
        // 比较器不为空时，队列中所有数据不能为null
        if (len == 1 || this.comparator != null)
            for (int i = 0; i < len; i++)
                if (a[i] == null)
                    throw new NullPointerException();
        this.queue = a;
        this.size = a.length;
    }

    /**
     * Initializes queue array with elements from the given Collection.
     *
     * @param c the collection
     */
    // 初始化优先队列，其中的数据来自普通集合|c|中的元素
    private void initFromCollection(Collection<? extends E> c) {
        initElementsFromCollection(c);
        // 重建最小堆
        heapify();
    }

    /**
     * The maximum size of array to allocate.
     * Some VMs reserve some header words in an array.
     * Attempts to allocate larger arrays may result in
     * OutOfMemoryError: Requested array size exceeds VM limit
     */
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    /**
     * Increases the capacity of the array.
     *
     * @param minCapacity the desired minimum capacity
     */
    private void grow(int minCapacity) {
        int oldCapacity = queue.length;
        // Double size if small; else grow by 50%
        // 队列较短时，每次增加2个数据空间；当队列超过64个，每次分配增加一倍容量
        int newCapacity = oldCapacity + ((oldCapacity < 64) ?
                                         (oldCapacity + 2) :
                                         (oldCapacity >> 1));
        // overflow-conscious code
        // 新的数组容量超过MAX_ARRAY_SIZE大小，进入大容量扩容逻辑。当newCapacity为负数时，结果将会大于0
        // 注：这里的 hugeCapacity() 也是考虑溢出风险的最终处理函数
        if (newCapacity - MAX_ARRAY_SIZE > 0)
            newCapacity = hugeCapacity(minCapacity);
        // 返回的是重新申请的一块内存，他的数据拷贝自原始的数组数据（底层使用System.arraycopy进行按字节拷贝）
        queue = Arrays.copyOf(queue, newCapacity);
    }

    private static int hugeCapacity(int minCapacity) {
        // 如果|minCapacity|小于0，抛出溢出异常
        if (minCapacity < 0) // overflow
            throw new OutOfMemoryError();
        // 数组容量最大不会超过Integer.MAX_VALUE
        return (minCapacity > MAX_ARRAY_SIZE) ?
            Integer.MAX_VALUE :
            MAX_ARRAY_SIZE;
    }

    /**
     * Inserts the specified element into this priority queue.
     *
     * @return {@code true} (as specified by {@link Collection#add})
     * @throws ClassCastException if the specified element cannot be
     *         compared with elements currently in this priority queue
     *         according to the priority queue's ordering
     * @throws NullPointerException if the specified element is null
     */
    // 将数据|e|添加到队列容器
    public boolean add(E e) {
        return offer(e);
    }

    /**
     * Inserts the specified element into this priority queue.
     *
     * @return {@code true} (as specified by {@link Queue#offer})
     * @throws ClassCastException if the specified element cannot be
     *         compared with elements currently in this priority queue
     *         according to the priority queue's ordering
     * @throws NullPointerException if the specified element is null
     */
    // 将数据|e|添加到队列容器
    public boolean offer(E e) {
        if (e == null)
            throw new NullPointerException();
        // 调整容量时，需设置容器结构有变化的标志位
        modCount++;
        int i = size;
        // 队列容量已满，增加分配内存
        if (i >= queue.length)
            grow(i + 1);
        size = i + 1;
        // 1. 新增的元素是首元素
        // 2. 从新增的叶节点开始，向上移动小的元素至父节点，使其符合最小堆特性
        if (i == 0)
            queue[0] = e;
        else
            siftUp(i, e);
        return true;
    }

    // 查看队列头元素。即该队列最小的元素
    @SuppressWarnings("unchecked")
    public E peek() {
        return (size == 0) ? null : (E) queue[0];
    }

    // 查找对象|o|在列表中的索引值，查找失败返回-1
    private int indexOf(Object o) {
        if (o != null) {
            for (int i = 0; i < size; i++)
                if (o.equals(queue[i]))
                    return i;
        }
        return -1;
    }

    /**
     * Removes a single instance of the specified element from this queue,
     * if it is present.  More formally, removes an element {@code e} such
     * that {@code o.equals(e)}, if this queue contains one or more such
     * elements.  Returns {@code true} if and only if this queue contained
     * the specified element (or equivalently, if this queue changed as a
     * result of the call).
     *
     * @param o element to be removed from this queue, if present
     * @return {@code true} if this queue changed as a result of the call
     */
    // 删除容器中第一个等于对象|o|的元素。元素不存在，返回false
    public boolean remove(Object o) {
        int i = indexOf(o);
        if (i == -1)
            return false;
        else {
            removeAt(i);
            return true;
        }
    }

    /**
     * Version of remove using reference equality, not equals.
     * Needed by iterator.remove.
     *
     * @param o element to be removed from this queue, if present
     * @return {@code true} if removed
     */
    // 删除容器中第一个等于（引用等于）对象|o|的元素。元素不存在，返回false
    boolean removeEq(Object o) {
        for (int i = 0; i < size; i++) {
            if (o == queue[i]) {
                removeAt(i);
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if this queue contains the specified element.
     * More formally, returns {@code true} if and only if this queue contains
     * at least one element {@code e} such that {@code o.equals(e)}.
     *
     * @param o object to be checked for containment in this queue
     * @return {@code true} if this queue contains the specified element
     */
    // 查找对象|o|是否在列表中
    public boolean contains(Object o) {
        return indexOf(o) != -1;
    }

    /**
     * Returns an array containing all of the elements in this queue.
     * The elements are in no particular order.
     *
     * <p>The returned array will be "safe" in that no references to it are
     * maintained by this queue.  (In other words, this method must allocate
     * a new array).  The caller is thus free to modify the returned array.
     *
     * <p>This method acts as bridge between array-based and collection-based
     * APIs.
     *
     * @return an array containing all of the elements in this queue
     */
    // 将队列容器中的数据转换成数组返回，这个方法返回的是Object[]的数组类型
    public Object[] toArray() {
        // 返回的是重新申请的一块内存，他的数据拷贝自原始的列队数据（底层使用System.arraycopy进行按字节拷贝）
        return Arrays.copyOf(queue, size);
    }

    /**
     * Returns an array containing all of the elements in this queue; the
     * runtime type of the returned array is that of the specified array.
     * The returned array elements are in no particular order.
     * If the queue fits in the specified array, it is returned therein.
     * Otherwise, a new array is allocated with the runtime type of the
     * specified array and the size of this queue.
     *
     * <p>If the queue fits in the specified array with room to spare
     * (i.e., the array has more elements than the queue), the element in
     * the array immediately following the end of the collection is set to
     * {@code null}.
     *
     * <p>Like the {@link #toArray()} method, this method acts as bridge between
     * array-based and collection-based APIs.  Further, this method allows
     * precise control over the runtime type of the output array, and may,
     * under certain circumstances, be used to save allocation costs.
     *
     * <p>Suppose {@code x} is a queue known to contain only strings.
     * The following code can be used to dump the queue into a newly
     * allocated array of {@code String}:
     *
     *  <pre> {@code String[] y = x.toArray(new String[0]);}</pre>
     *
     * Note that {@code toArray(new Object[0])} is identical in function to
     * {@code toArray()}.
     *
     * @param a the array into which the elements of the queue are to
     *          be stored, if it is big enough; otherwise, a new array of the
     *          same runtime type is allocated for this purpose.
     * @return an array containing all of the elements in this queue
     * @throws ArrayStoreException if the runtime type of the specified array
     *         is not a supertype of the runtime type of every element in
     *         this queue
     * @throws NullPointerException if the specified array is null
     */
    // 将队列容器中的数据转换成数组返回，这个方法返回的是T[]的数组类型。当数组|a|长度足够时，直接使用它
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        final int size = this.size;
        if (a.length < size)
            // Make a new array of a's runtime type, but my contents:
            // 返回的是重新申请的一块内存，他的数据拷贝自原始的队列数据（底层使用System.arraycopy进行按字节拷贝）
            // 数组的类型使用泛型的实际类型
            return (T[]) Arrays.copyOf(queue, size, a.getClass());
        // 当数组|a|长度足够时，直接使用它，将原始的队列数据拷贝到|a|数组中返回
        System.arraycopy(queue, 0, a, 0, size);
        // 将多余的数组元素置为null
        if (a.length > size)
            a[size] = null;
        return a;
    }

    /**
     * Returns an iterator over the elements in this queue. The iterator
     * does not return the elements in any particular order.
     *
     * @return an iterator over the elements in this queue
     */
    public Iterator<E> iterator() {
        return new Itr();
    }

    private final class Itr implements Iterator<E> {
        /**
         * Index (into queue array) of element to be returned by
         * subsequent call to next.
         */
        private int cursor = 0;

        /**
         * Index of element returned by most recent call to next,
         * unless that element came from the forgetMeNot list.
         * Set to -1 if element is deleted by a call to remove.
         */
        private int lastRet = -1;

        /**
         * A queue of elements that were moved from the unvisited portion of
         * the heap into the visited portion as a result of "unlucky" element
         * removals during the iteration.  (Unlucky element removals are those
         * that require a siftup instead of a siftdown.)  We must visit all of
         * the elements in this list to complete the iteration.  We do this
         * after we've completed the "normal" iteration.
         *
         * We expect that most iterations, even those involving removals,
         * will not need to store elements in this field.
         */
        private ArrayDeque<E> forgetMeNot = null;

        /**
         * Element returned by the most recent call to next iff that
         * element was drawn from the forgetMeNot list.
         */
        private E lastRetElt = null;

        /**
         * The modCount value that the iterator believes that the backing
         * Queue should have.  If this expectation is violated, the iterator
         * has detected concurrent modification.
         */
        private int expectedModCount = modCount;

        public boolean hasNext() {
            return cursor < size ||
                (forgetMeNot != null && !forgetMeNot.isEmpty());
        }

        @SuppressWarnings("unchecked")
        public E next() {
            if (expectedModCount != modCount)
                throw new ConcurrentModificationException();
            if (cursor < size)
                return (E) queue[lastRet = cursor++];
            if (forgetMeNot != null) {
                lastRet = -1;
                lastRetElt = forgetMeNot.poll();
                if (lastRetElt != null)
                    return lastRetElt;
            }
            throw new NoSuchElementException();
        }

        public void remove() {
            if (expectedModCount != modCount)
                throw new ConcurrentModificationException();
            if (lastRet != -1) {
                E moved = PriorityQueue.this.removeAt(lastRet);
                lastRet = -1;
                if (moved == null)
                    cursor--;
                else {
                    if (forgetMeNot == null)
                        forgetMeNot = new ArrayDeque<>();
                    forgetMeNot.add(moved);
                }
            } else if (lastRetElt != null) {
                PriorityQueue.this.removeEq(lastRetElt);
                lastRetElt = null;
            } else {
                throw new IllegalStateException();
            }
            expectedModCount = modCount;
        }
    }

    public int size() {
        return size;
    }

    /**
     * Removes all of the elements from this priority queue.
     * The queue will be empty after this call returns.
     */
    public void clear() {
        modCount++;
        for (int i = 0; i < size; i++)
            queue[i] = null;
        size = 0;
    }

    @SuppressWarnings("unchecked")
    public E poll() {
        if (size == 0)
            return null;
        int s = --size;
        modCount++;
        E result = (E) queue[0];
        E x = (E) queue[s];
        queue[s] = null;
        if (s != 0)
            siftDown(0, x);
        return result;
    }

    /**
     * Removes the ith element from queue.
     *
     * Normally this method leaves the elements at up to i-1,
     * inclusive, untouched.  Under these circumstances, it returns
     * null.  Occasionally, in order to maintain the heap invariant,
     * it must swap a later element of the list with one earlier than
     * i.  Under these circumstances, this method returns the element
     * that was previously at the end of the list and is now at some
     * position before i. This fact is used by iterator.remove so as to
     * avoid missing traversing elements.
     */
    // 删除索引为|i|的节点，向上移动小的元素至父节点，使其符合最小堆特性
    // 注：算法形象化描述：将新增的数据冒泡到它应该在的节点位置
    @SuppressWarnings("unchecked")
    private E removeAt(int i) {
        // 索引越界合法性断言
        // assert i >= 0 && i < size;

        // 调整容量时，需设置容器结构有变化的标志位
        modCount++;
        int s = --size;
        // 当|i|为叶节点，直接清除该节点
        if (s == i) // removed last element
            queue[i] = null;
        else {
            E moved = (E) queue[s];
            queue[s] = null;
            siftDown(i, moved);
            if (queue[i] == moved) {
                siftUp(i, moved);
                if (queue[i] != moved)
                    return moved;
            }
        }
        return null;
    }

    /**
     * Inserts item x at position k, maintaining heap invariant by
     * promoting x up the tree until it is greater than or equal to
     * its parent, or is the root.
     *
     * To simplify and speed up coercions and comparisons. the
     * Comparable and Comparator versions are separated into different
     * methods that are otherwise identical. (Similarly for siftDown.)
     *
     * @param k the position to fill
     * @param x the item to insert
     */
    // 从新增的叶节点|e|、索引为|k|的数据开始，向上移动小的元素至父节点，使其符合最小堆特性
    // 算法描述：将新增的数据冒泡到第一个大于等于它的父节点上
    private void siftUp(int k, E x) {
        if (comparator != null)
            siftUpUsingComparator(k, x);
        else
            siftUpComparable(k, x);
    }

    // 从新增的叶节点|e|、其索引为|k|的数据开始，向上移动小的元素至父节点，使其符合最小堆特性
    // 算法描述：将新增的数据冒泡到第一个大于等于它的父节点上
    // 注：不使用自定义比较器的版本
    @SuppressWarnings("unchecked")
    private void siftUpComparable(int k, E x) {
        // 使用元素自身比较函数
        Comparable<? super E> key = (Comparable<? super E>) x;
        // 从该新增的叶节点开始，向上遍历所有父节点
        while (k > 0) {
            // 比较当前节点与父节点的大小，若当前节点小于父节点，就与父节点交换，直到大于等于父节点
            int parent = (k - 1) >>> 1;
            Object e = queue[parent];
            if (key.compareTo((E) e) >= 0)
                break;
            queue[k] = e;
            k = parent;
        }
        // 将新增的数据冒泡到它应该在的节点位置
        queue[k] = key;
    }

    // 从新增的叶节点|e|、其索引为|k|的数据开始，向上移动小的元素至父节点，使其符合最小堆特性
    // 算法描述：将新增的数据冒泡到第一个大于等于它的父节点上
    // 注：使用自定义比较器的版本
    @SuppressWarnings("unchecked")
    private void siftUpUsingComparator(int k, E x) {
        // 从该新增的叶节点开始，向上遍历所有父节点
        while (k > 0) {
            // 比较当前节点与父节点的大小，若当前节点小于父节点，就与父节点交换，直到大于等于父节点
            int parent = (k - 1) >>> 1;
            Object e = queue[parent];
            if (comparator.compare(x, (E) e) >= 0)
                break;
            queue[k] = e;
            k = parent;
        }
        // 将新增的数据冒泡到它应该在的节点位置
        queue[k] = x;
    }

    /**
     * Inserts item x at position k, maintaining heap invariant by
     * demoting x down the tree repeatedly until it is less than or
     * equal to its children or is a leaf.
     *
     * @param k the position to fill
     * @param x the item to insert
     */
    // 从待删除索引为|k|的数据开始，向下遍历子树，将左右子节点中较小的节点替换到父节点，直到第一个大于|x|节点结束，用|x|替换到该父节点，完成删除。其中|x|为叶节点
    // 算法描述：将删除元素的动作发生在叶节点上，并将该叶节点移动到实际要删除节点的子树中第一个小于该叶节点的父节点上
    // 1. 若是删除操作，则|x|为叶节点。即被删除的节点使用叶节点来填充
    // 2. 若是重建操作，则|x|节点的索引就是|k|。即将索引为|k|的父节点中较小的左右节点移动到该节点
    private void siftDown(int k, E x) {
        if (comparator != null)
            siftDownUsingComparator(k, x);
        else
            siftDownComparable(k, x);
    }

    // 从待删除索引为|k|的数据开始，向下遍历子树，将左右子节点中较小的节点替换到父节点，直到第一个大于等于|x|节点结束，用|x|替换到该父节点，完成删除。
    // 算法描述：将删除元素的动作发生在叶节点上，并将该叶节点移动到实际要删除节点的子树中第一个小于该叶节点的父节点上
    // 1. 若是删除操作，则|x|为叶节点。即被删除的节点使用叶节点来填充
    // 2. 若是重建操作，则|x|节点的索引就是|k|。即将索引为|k|的父节点中较小的左右节点移动到该节点
    // 注：不使用自定义比较器的版本
    @SuppressWarnings("unchecked")
    private void siftDownComparable(int k, E x) {
        Comparable<? super E> key = (Comparable<? super E>)x;
        // 遍历一颗子树
        int half = size >>> 1;        // loop while a non-leaf
        while (k < half) {
            // 获取|k|节点的左右子节点
            int child = (k << 1) + 1; // assume left child is least
            Object c = queue[child];
            int right = child + 1;
            // 获取左右子节点中较小的一个节点
            if (right < size &&
                ((Comparable<? super E>) c).compareTo((E) queue[right]) > 0)
                c = queue[child = right];
            // 若待替换的|x|数据小于待删除的节点，直接替换为|x|就不会破坏原始的最小堆特性
            if (key.compareTo((E) c) <= 0)
                break;
            // 左右子节点中较小的一个节点移动至父节点
            queue[k] = c;
            k = child;
        }
        // 将叶节点|x|存放到它应该在的节点位置
        queue[k] = key;
    }

    // 从待删除索引为|k|的数据开始，向下遍历子树，将左右子节点中较小的节点替换到父节点，直到第一个大于等于|x|节点结束，用|x|替换到该父节点，完成删除。其中|x|为已删除的叶节点
    // 算法描述：将删除元素的动作发生在叶节点上，并将该叶节点移动到实际要删除节点的子树中第一个小于该叶节点的父节点上
    // 1. 若是删除操作，则|x|为叶节点。即被删除的节点使用叶节点来填充
    // 2. 若是重建操作，则|x|节点的索引就是|k|。即将索引为|k|的父节点中较小的左右节点移动到该节点
    // 注：使用自定义比较器的版本
    @SuppressWarnings("unchecked")
    private void siftDownUsingComparator(int k, E x) {
        // 遍历一颗子树
        int half = size >>> 1;
        while (k < half) {
            // 获取|k|节点的左右子节点
            int child = (k << 1) + 1;
            Object c = queue[child];
            int right = child + 1;
            // 获取左右子节点中较小的一个节点
            if (right < size &&
                comparator.compare((E) c, (E) queue[right]) > 0)
                c = queue[child = right];
            // 若待替换的|x|数据小于待删除的节点，直接替换为|x|就不会破坏原始的最小堆特性
            if (comparator.compare(x, (E) c) <= 0)
                break;
            // 左右子节点中较小的一个节点移动至父节点
            queue[k] = c;
            k = child;
        }
        // 将叶节点|x|存放到它应该在的节点位置
        queue[k] = x;
    }

    /**
     * Establishes the heap invariant (described above) in the entire tree,
     * assuming nothing about the order of the elements prior to the call.
     */
    // 重建最小堆
    @SuppressWarnings("unchecked")
    private void heapify() {
        // 遍历所有节点，构建每颗子树使其符合最小堆特性
        for (int i = (size >>> 1) - 1; i >= 0; i--)
            siftDown(i, (E) queue[i]);
    }

    /**
     * Returns the comparator used to order the elements in this
     * queue, or {@code null} if this queue is sorted according to
     * the {@linkplain Comparable natural ordering} of its elements.
     *
     * @return the comparator used to order this queue, or
     *         {@code null} if this queue is sorted according to the
     *         natural ordering of its elements
     */
    public Comparator<? super E> comparator() {
        return comparator;
    }

    /**
     * Saves this queue to a stream (that is, serializes it).
     *
     * @serialData The length of the array backing the instance is
     *             emitted (int), followed by all of its elements
     *             (each an {@code Object}) in the proper order.
     * @param s the stream
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {
        // Write out element count, and any hidden stuff
        s.defaultWriteObject();

        // Write out array length, for compatibility with 1.5 version
        s.writeInt(Math.max(2, size + 1));

        // Write out all elements in the "proper order".
        for (int i = 0; i < size; i++)
            s.writeObject(queue[i]);
    }

    /**
     * Reconstitutes the {@code PriorityQueue} instance from a stream
     * (that is, deserializes it).
     *
     * @param s the stream
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        // Read in size, and any hidden stuff
        s.defaultReadObject();

        // Read in (and discard) array length
        s.readInt();

        queue = new Object[size];

        // Read in all elements.
        for (int i = 0; i < size; i++)
            queue[i] = s.readObject();

        // Elements are guaranteed to be in "proper order", but the
        // spec has never explained what that might be.
        heapify();
    }

    /**
     * Creates a <em><a href="Spliterator.html#binding">late-binding</a></em>
     * and <em>fail-fast</em> {@link Spliterator} over the elements in this
     * queue.
     *
     * <p>The {@code Spliterator} reports {@link Spliterator#SIZED},
     * {@link Spliterator#SUBSIZED}, and {@link Spliterator#NONNULL}.
     * Overriding implementations should document the reporting of additional
     * characteristic values.
     *
     * @return a {@code Spliterator} over the elements in this queue
     * @since 1.8
     */
    // 获取PriorityQueue容器的分割器对象实例。用于Stream流中
    public final Spliterator<E> spliterator() {
        return new PriorityQueueSpliterator<E>(this, 0, -1, 0);
    }

    static final class PriorityQueueSpliterator<E> implements Spliterator<E> {
        /*
         * This is very similar to ArrayList Spliterator, except for
         * extra null checks.
         */
        // 原始容器的引用
        private final PriorityQueue<E> pq;
        // 分割器的起始索引，也是分割器的正向消费元素进度索引
        private int index;            // current index, modified on advance/split
        // 分割器的中含有元素个数，也是一个超尾索引
        private int fence;            // -1 until first use
        // 第一次分割、消费分隔器时，立即保存原始容器的修改次数。在使用分割器过程中，原始容器不能再进行结构性修改
        // 注：modCount属性字段非常重要，可以有效的防止分割器非法访问的问题
        private int expectedModCount; // initialized when fence set

        /** Creates new spliterator covering the given range */
        PriorityQueueSpliterator(PriorityQueue<E> pq, int origin, int fence,
                             int expectedModCount) {
            this.pq = pq;
            this.index = origin;
            this.fence = fence;
            this.expectedModCount = expectedModCount;
        }

        private int getFence() { // initialize fence to size on first use
            int hi;
            if ((hi = fence) < 0) {
                // 第一次使用分割器时，立即保存原始容器的修改次数。在使用分割器过程中，原始容器不能再进行结构性修改
                // 注：modCount属性字段非常重要，可以有效的防止分割器非法访问的问题
                expectedModCount = pq.modCount;
                hi = fence = pq.size;
            }
            return hi;
        }

        // 二分切割分割器，返回的新分割器起始索引等于原始分隔器，结尾索引是原始容器的二分之一；而原始分割器的起始索引被重置为二分之一
        // 即：切分后，原始的分割器引用后一半数据，返回的新分割器引用前一半数据
        public PriorityQueueSpliterator<E> trySplit() {
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            return (lo >= mid) ? null :
                new PriorityQueueSpliterator<E>(pq, lo, index = mid,
                                                expectedModCount);
        }

        // 消费分割器中剩余元素，执行指定方法
        @SuppressWarnings("unchecked")
        public void forEachRemaining(Consumer<? super E> action) {
            int i, hi, mc; // hoist accesses and checks from loop
            PriorityQueue<E> q; Object[] a;
            if (action == null)
                throw new NullPointerException();
            // 获取原始容器的元素
            if ((q = pq) != null && (a = q.queue) != null) {
                // 获取结束索引hi。首个分割器首次时fence==-1，将其设置为原始容器的结尾索引
                // 注：区分首个分隔器，是因为分割器在使用过程中会不停的二分递归切分
                if ((hi = fence) < 0) {
                    mc = q.modCount;
                    hi = q.size;
                }
                else
                    mc = expectedModCount;
                // 消费分隔器中剩余元素（|index=hi|代表消费剩余所有元素）
                if ((i = index) >= 0 && (index = hi) <= a.length) {
                    // 遍历分割器中剩余元素，执行指定方法
                    for (E e;; ++i) {
                        if (i < hi) {
                            // 优先队列不能有空元素
                            if ((e = (E) a[i]) == null) // must be CME
                                break;
                            action.accept(e);
                        }
                        else if (q.modCount != mc)
                            break;
                        else
                            // 遍历成功，直接返回
                            return;
                    }
                }
            }
            throw new ConcurrentModificationException();
        }

        // 消费分割器中首个元素，执行指定方法
        public boolean tryAdvance(Consumer<? super E> action) {
            if (action == null)
                throw new NullPointerException();
            int hi = getFence(), lo = index;
            if (lo >= 0 && lo < hi) {
                // 设置下一次tryAdvance()的元素索引。即，当前元素被消费
                index = lo + 1;
                @SuppressWarnings("unchecked") E e = (E)pq.queue[lo];
                if (e == null)
                    throw new ConcurrentModificationException();
                action.accept(e);
                // 在使用分割器过程中，原始容器不能再进行结构性修改
                if (pq.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                return true;
            }
            return false;
        }

        public long estimateSize() {
            return (long) (getFence() - index);
        }

        public int characteristics() {
            return Spliterator.SIZED | Spliterator.SUBSIZED | Spliterator.NONNULL;
        }
    }
}
