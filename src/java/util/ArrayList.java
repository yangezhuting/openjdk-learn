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

package java.util;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Resizable-array implementation of the <tt>List</tt> interface.  Implements
 * all optional list operations, and permits all elements, including
 * <tt>null</tt>.  In addition to implementing the <tt>List</tt> interface,
 * this class provides methods to manipulate the size of the array that is
 * used internally to store the list.  (This class is roughly equivalent to
 * <tt>Vector</tt>, except that it is unsynchronized.)
 *
 * <p>The <tt>size</tt>, <tt>isEmpty</tt>, <tt>get</tt>, <tt>set</tt>,
 * <tt>iterator</tt>, and <tt>listIterator</tt> operations run in constant
 * time.  The <tt>add</tt> operation runs in <i>amortized constant time</i>,
 * that is, adding n elements requires O(n) time.  All of the other operations
 * run in linear time (roughly speaking).  The constant factor is low compared
 * to that for the <tt>LinkedList</tt> implementation.
 *
 * <p>Each <tt>ArrayList</tt> instance has a <i>capacity</i>.  The capacity is
 * the size of the array used to store the elements in the list.  It is always
 * at least as large as the list size.  As elements are added to an ArrayList,
 * its capacity grows automatically.  The details of the growth policy are not
 * specified beyond the fact that adding an element has constant amortized
 * time cost.
 *
 * <p>An application can increase the capacity of an <tt>ArrayList</tt> instance
 * before adding a large number of elements using the <tt>ensureCapacity</tt>
 * operation.  This may reduce the amount of incremental reallocation.
 *
 * <p><strong>Note that this implementation is not synchronized.</strong>
 * If multiple threads access an <tt>ArrayList</tt> instance concurrently,
 * and at least one of the threads modifies the list structurally, it
 * <i>must</i> be synchronized externally.  (A structural modification is
 * any operation that adds or deletes one or more elements, or explicitly
 * resizes the backing array; merely setting the value of an element is not
 * a structural modification.)  This is typically accomplished by
 * synchronizing on some object that naturally encapsulates the list.
 *
 * If no such object exists, the list should be "wrapped" using the
 * {@link Collections#synchronizedList Collections.synchronizedList}
 * method.  This is best done at creation time, to prevent accidental
 * unsynchronized access to the list:<pre>
 *   List list = Collections.synchronizedList(new ArrayList(...));</pre>
 *
 * <p><a name="fail-fast">
 * The iterators returned by this class's {@link #iterator() iterator} and
 * {@link #listIterator(int) listIterator} methods are <em>fail-fast</em>:</a>
 * if the list is structurally modified at any time after the iterator is
 * created, in any way except through the iterator's own
 * {@link ListIterator#remove() remove} or
 * {@link ListIterator#add(Object) add} methods, the iterator will throw a
 * {@link ConcurrentModificationException}.  Thus, in the face of
 * concurrent modification, the iterator fails quickly and cleanly, rather
 * than risking arbitrary, non-deterministic behavior at an undetermined
 * time in the future.
 *
 * <p>Note that the fail-fast behavior of an iterator cannot be guaranteed
 * as it is, generally speaking, impossible to make any hard guarantees in the
 * presence of unsynchronized concurrent modification.  Fail-fast iterators
 * throw {@code ConcurrentModificationException} on a best-effort basis.
 * Therefore, it would be wrong to write a program that depended on this
 * exception for its correctness:  <i>the fail-fast behavior of iterators
 * should be used only to detect bugs.</i>
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @author  Josh Bloch
 * @author  Neal Gafter
 * @see     Collection
 * @see     List
 * @see     LinkedList
 * @see     Vector
 * @since   1.2
 */

// 可高效的进行随机访问（按索引访问）的列表结构。实现了 List,RandomAccess 接口
// 注：与LinkedList相比，随机访问效率更高，但频繁的插入、删除效率欠佳，因为这可能会导致底层数组的扩容（内存要重新分配及拷贝）
public class ArrayList<E> extends AbstractList<E>
        implements List<E>, RandomAccess, Cloneable, java.io.Serializable
{
    private static final long serialVersionUID = 8683452581122892189L;

    /**
     * Default initial capacity.
     */
    // 默认构造的容器，默认容量为10
    private static final int DEFAULT_CAPACITY = 10;

    /**
     * Shared empty array instance used for empty instances.
     */
    // 全局共享的空元素数组
    private static final Object[] EMPTY_ELEMENTDATA = {};

    /**
     * Shared empty array instance used for default sized empty instances. We
     * distinguish this from EMPTY_ELEMENTDATA to know how much to inflate when
     * first element is added.
     */
    // 默认构造的容器设置的全局共享的空元素数组
    private static final Object[] DEFAULTCAPACITY_EMPTY_ELEMENTDATA = {};

    /**
     * The array buffer into which the elements of the ArrayList are stored.
     * The capacity of the ArrayList is the length of this array buffer. Any
     * empty ArrayList with elementData == DEFAULTCAPACITY_EMPTY_ELEMENTDATA
     * will be expanded to DEFAULT_CAPACITY when the first element is added.
     */
    // 实际元素存储容器
    transient Object[] elementData; // non-private to simplify nested class access

    /**
     * The size of the ArrayList (the number of elements it contains).
     *
     * @serial
     */
    // 容器中元素的实际个数
    private int size;

    /**
     * Constructs an empty list with the specified initial capacity.
     *
     * @param  initialCapacity  the initial capacity of the list
     * @throws IllegalArgumentException if the specified initial capacity
     *         is negative
     */
    public ArrayList(int initialCapacity) {
        if (initialCapacity > 0) {
            // 申请指定容量的内存，全部元素被初始化为null
            this.elementData = new Object[initialCapacity];
        } else if (initialCapacity == 0) {
            // 全局的空元素的数组。与DEFAULTCAPACITY_EMPTY_ELEMENTDATA区分开，是因为此处是用户手动指定了容量为0
            // 区分开两者，主要是为了处理DEFAULT_CAPACITY默认容量。即：如果用户手动指定容量为0，则DEFAULT_CAPACITY将不再有效
            this.elementData = EMPTY_ELEMENTDATA;
        } else {
            throw new IllegalArgumentException("Illegal Capacity: "+
                                               initialCapacity);
        }
    }

    /**
     * Constructs an empty list with an initial capacity of ten.
     */
    public ArrayList() {
        // 全局的空元素的数组。与 EMPTY_ELEMENTDATA 区分开，是因为此处是用户并未手动指定了容量
        // 区分开两者，主要是为了处理 DEFAULT_CAPACITY 默认容量。即：如果用户手动指定容量为0，则 DEFAULT_CAPACITY 将不再有效
        this.elementData = DEFAULTCAPACITY_EMPTY_ELEMENTDATA;
    }

    /**
     * Constructs a list containing the elements of the specified
     * collection, in the order they are returned by the collection's
     * iterator.
     *
     * @param c the collection whose elements are to be placed into this list
     * @throws NullPointerException if the specified collection is null
     */
    // 构造一个列表容器，其中的元素来自容器|c|中的元素
    public ArrayList(Collection<? extends E> c) {
        elementData = c.toArray();
        if ((size = elementData.length) != 0) {
            // c.toArray might (incorrectly) not return Object[] (see 6260652)
            // 获取容器|c|中所有元素，类型通常为Object[].class
            // 注：Arrays.asList()创建的列表，其c.toArray()返回的不是Object[]数组，而是T[]数组
            if (elementData.getClass() != Object[].class)
                elementData = Arrays.copyOf(elementData, size, Object[].class); // 将 T[] 类型的数组转换成 Object[]
        } else {
            // replace with empty array.
            this.elementData = EMPTY_ELEMENTDATA;
        }
    }

    /**
     * Trims the capacity of this <tt>ArrayList</tt> instance to be the
     * list's current size.  An application can use this operation to minimize
     * the storage of an <tt>ArrayList</tt> instance.
     */
    // 将容量缩小到实际元素个数
    public void trimToSize() {
        // 缩容时，需设置容器结构有变化的标志位
        modCount++;
        // 当实际元素个数小于容量时，释放多余的内存
        // 注：返回的是重新申请的一块内存，他的数据拷贝自原始的数组数据（底层使用System.arraycopy进行按字节拷贝）
        if (size < elementData.length) {
            elementData = (size == 0)
              ? EMPTY_ELEMENTDATA
              : Arrays.copyOf(elementData, size);
        }
    }

    /**
     * Increases the capacity of this <tt>ArrayList</tt> instance, if
     * necessary, to ensure that it can hold at least the number of elements
     * specified by the minimum capacity argument.
     *
     * @param   minCapacity   the desired minimum capacity
     */
    // 系统在确保能容纳|minCapacity|个元素时，自行判断容量是否需要扩容
    public void ensureCapacity(int minCapacity) {
        // 容器的最小容量值。DEFAULTCAPACITY_EMPTY_ELEMENTDATA 来标识容器是默认构造的，其最小容量为10
        int minExpand = (elementData != DEFAULTCAPACITY_EMPTY_ELEMENTDATA)
            // any size if not default element table
            ? 0
            // larger than default for default empty table. It's already
            // supposed to be at default size.
            : DEFAULT_CAPACITY;

        // 指定|minCapacity|个数大小超过最小容量值时，进一步判断容量是否需要扩容
        if (minCapacity > minExpand) {
            ensureExplicitCapacity(minCapacity);
        }
    }

    // 系统在确保能容纳|minCapacity|个元素时，自行判断容量是否需要扩容
    private void ensureCapacityInternal(int minCapacity) {
        // 默认构造的容器，默认容量为10（不能低于10）
        // 相比ensureCapacity()公共的方法，minCapacity不可能为0
        if (elementData == DEFAULTCAPACITY_EMPTY_ELEMENTDATA) {
            minCapacity = Math.max(DEFAULT_CAPACITY, minCapacity);
        }

        ensureExplicitCapacity(minCapacity);
    }

    // 系统在确保能容纳|minCapacity|个元素时，自行判断容量是否需要扩容
    private void ensureExplicitCapacity(int minCapacity) {
        // 调整容量时，需设置容器结构有变化的标志位
        modCount++;

        // overflow-conscious code
        // 此处拷贝了溢出风险。即，除了真正的需要扩容场景中，当|minCapacity|为负数，两个整数之和溢出4字节，结果也将大于0
        if (minCapacity - elementData.length > 0)
            grow(minCapacity);
    }

    /**
     * The maximum size of array to allocate.
     * Some VMs reserve some header words in an array.
     * Attempts to allocate larger arrays may result in
     * OutOfMemoryError: Requested array size exceeds VM limit
     */
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    /**
     * Increases the capacity to ensure that it can hold at least the
     * number of elements specified by the minimum capacity argument.
     *
     * @param minCapacity the desired minimum capacity
     */
    private void grow(int minCapacity) {
        // overflow-conscious code
        int oldCapacity = elementData.length;

        // 新的数组容量为老容量的1.5倍。考虑了溢出风险，即，当扩容到1.5的整数溢出，它将变成负数
        int newCapacity = oldCapacity + (oldCapacity >> 1);
        // 新数组容量为手动设置的最小容量与1.5倍老容量中的较大值。考虑了溢出风险，即，当minCapacity为负数，两个整数之和溢出4字节，结果也将小于0
        if (newCapacity - minCapacity < 0)
            newCapacity = minCapacity;

        // 新的数组容量超过MAX_ARRAY_SIZE大小，进入大容量扩容逻辑。当newCapacity为负数时，结果将会大于0
        // 注：这里的 hugeCapacity() 也是考虑溢出风险的最终处理函数
        if (newCapacity - MAX_ARRAY_SIZE > 0)
            newCapacity = hugeCapacity(minCapacity);
        // minCapacity is usually close to size, so this is a win:
        // 返回的是重新申请的一块内存，他的数据拷贝自原始的数组数据（底层使用System.arraycopy进行按字节拷贝）
        elementData = Arrays.copyOf(elementData, newCapacity);
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
     * Returns the number of elements in this list.
     *
     * @return the number of elements in this list
     */
    public int size() {
        return size;
    }

    /**
     * Returns <tt>true</tt> if this list contains no elements.
     *
     * @return <tt>true</tt> if this list contains no elements
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns <tt>true</tt> if this list contains the specified element.
     * More formally, returns <tt>true</tt> if and only if this list contains
     * at least one element <tt>e</tt> such that
     * <tt>(o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e))</tt>.
     *
     * @param o element whose presence in this list is to be tested
     * @return <tt>true</tt> if this list contains the specified element
     */
    // 查找对象|o|是否在列表中
    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    /**
     * Returns the index of the first occurrence of the specified element
     * in this list, or -1 if this list does not contain the element.
     * More formally, returns the lowest index <tt>i</tt> such that
     * <tt>(o==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;o.equals(get(i)))</tt>,
     * or -1 if there is no such index.
     */
    // 正向查找对象|o|在列表中的索引值，查找失败返回-1
    public int indexOf(Object o) {
        if (o == null) {
            // null值查找处理
            for (int i = 0; i < size; i++)
                if (elementData[i]==null)
                    return i;
        } else {
            // 对象相等查找。使用参数作为 equals() 的调用对象
            for (int i = 0; i < size; i++)
                if (o.equals(elementData[i]))
                    return i;
        }
        return -1;
    }

    /**
     * Returns the index of the last occurrence of the specified element
     * in this list, or -1 if this list does not contain the element.
     * More formally, returns the highest index <tt>i</tt> such that
     * <tt>(o==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;o.equals(get(i)))</tt>,
     * or -1 if there is no such index.
     */
    // 逆向查找对象|o|在列表中的索引值，查找失败返回-1
    public int lastIndexOf(Object o) {
        if (o == null) {
            // null值查找处理
            for (int i = size-1; i >= 0; i--)
                if (elementData[i]==null)
                    return i;
        } else {
            // 对象相等查找。使用参数作为 equals() 的调用对象
            for (int i = size-1; i >= 0; i--)
                if (o.equals(elementData[i]))
                    return i;
        }
        return -1;
    }

    /**
     * Returns a shallow copy of this <tt>ArrayList</tt> instance.  (The
     * elements themselves are not copied.)
     *
     * @return a clone of this <tt>ArrayList</tt> instance
     */
    // 返回列表容器的一个副本。内部Object[]数组会被自动拷贝。即，新建后复制，是按字节拷贝原始元素
    public Object clone() {
        try {
            ArrayList<?> v = (ArrayList<?>) super.clone();
            // 返回的是重新申请的一块内存，他的数据拷贝自原始的数组数据（底层使用System.arraycopy进行按字节拷贝）
            // 注：对象类型拷贝中需要自行处理
            v.elementData = Arrays.copyOf(elementData, size);
            v.modCount = 0;
            return v;
        } catch (CloneNotSupportedException e) {
            // this shouldn't happen, since we are Cloneable
            // 永远不能发生，因为我们实现了Cloneable接口
            throw new InternalError(e);
        }
    }

    /**
     * Returns an array containing all of the elements in this list
     * in proper sequence (from first to last element).
     *
     * <p>The returned array will be "safe" in that no references to it are
     * maintained by this list.  (In other words, this method must allocate
     * a new array).  The caller is thus free to modify the returned array.
     *
     * <p>This method acts as bridge between array-based and collection-based
     * APIs.
     *
     * @return an array containing all of the elements in this list in
     *         proper sequence
     */
    // 将列表容器中的数据转换成数组返回，这个方法返回的是Object[]的数组类型
    public Object[] toArray() {
        // 返回的是重新申请的一块内存，他的数据拷贝自原始的数组数据（底层使用System.arraycopy进行按字节拷贝）
        return Arrays.copyOf(elementData, size);
    }

    /**
     * Returns an array containing all of the elements in this list in proper
     * sequence (from first to last element); the runtime type of the returned
     * array is that of the specified array.  If the list fits in the
     * specified array, it is returned therein.  Otherwise, a new array is
     * allocated with the runtime type of the specified array and the size of
     * this list.
     *
     * <p>If the list fits in the specified array with room to spare
     * (i.e., the array has more elements than the list), the element in
     * the array immediately following the end of the collection is set to
     * <tt>null</tt>.  (This is useful in determining the length of the
     * list <i>only</i> if the caller knows that the list does not contain
     * any null elements.)
     *
     * @param a the array into which the elements of the list are to
     *          be stored, if it is big enough; otherwise, a new array of the
     *          same runtime type is allocated for this purpose.
     * @return an array containing the elements of the list
     * @throws ArrayStoreException if the runtime type of the specified array
     *         is not a supertype of the runtime type of every element in
     *         this list
     * @throws NullPointerException if the specified array is null
     */
    // 将列表容器中的数据转换成数组返回，这个方法返回的是T[]的数组类型。当数组|a|长度足够时，直接使用它
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        if (a.length < size)
            // Make a new array of a's runtime type, but my contents:
            // 返回的是重新申请的一块内存，他的数据拷贝自原始的数组数据（底层使用System.arraycopy进行按字节拷贝）
            // 数组的类型使用泛型的实际类型
            return (T[]) Arrays.copyOf(elementData, size, a.getClass());
        // 当数组|a|长度足够时，直接使用它，将原始的数组数据拷贝到|a|数组中返回
        System.arraycopy(elementData, 0, a, 0, size);
        // 将多余的数组元素置为null
        if (a.length > size)
            a[size] = null;
        return a;
    }

    // Positional Access Operations

    @SuppressWarnings("unchecked")
    E elementData(int index) {
        return (E) elementData[index];
    }

    /**
     * Returns the element at the specified position in this list.
     *
     * @param  index index of the element to return
     * @return the element at the specified position in this list
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public E get(int index) {
        // 访问越界判定
        rangeCheck(index);

        return elementData(index);
    }

    /**
     * Replaces the element at the specified position in this list with
     * the specified element.
     *
     * @param index index of the element to replace
     * @param element element to be stored at the specified position
     * @return the element previously at the specified position
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public E set(int index, E element) {
        // 访问越界判定
        rangeCheck(index);

        E oldValue = elementData(index);
        elementData[index] = element;
        return oldValue;
    }

    /**
     * Appends the specified element to the end of this list.
     *
     * @param e element to be appended to this list
     * @return <tt>true</tt> (as specified by {@link Collection#add})
     */
    // 将数据|e|添加到容器尾部
    public boolean add(E e) {
        // 必要时，扩容内存
        ensureCapacityInternal(size + 1);  // Increments modCount!!
        elementData[size++] = e;
        return true;
    }

    /**
     * Inserts the specified element at the specified position in this
     * list. Shifts the element currently at that position (if any) and
     * any subsequent elements to the right (adds one to their indices).
     *
     * @param index index at which the specified element is to be inserted
     * @param element element to be inserted
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    // 将|element|元素添加到容器的第|index|索引中
    public void add(int index, E element) {
        // 添加逻辑中的访问越界判定。索引可以为最后一个元素值（超尾索引），也可以是0
        rangeCheckForAdd(index);

        // 必要时，扩容内存
        ensureCapacityInternal(size + 1);  // Increments modCount!!
        // 将包括|index|后的元素向后移动一个索引
        System.arraycopy(elementData, index, elementData, index + 1,
                         size - index);
        elementData[index] = element;
        size++;
    }

    /**
     * Removes the element at the specified position in this list.
     * Shifts any subsequent elements to the left (subtracts one from their
     * indices).
     *
     * @param index the index of the element to be removed
     * @return the element that was removed from the list
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    // 删除容器中第一个等于对象|o|的元素。元素不存在，返回false
    public E remove(int index) {
        // 访问越界判定
        rangeCheck(index);

        // 删除元素时，需设置容器结构有变化的标志位
        modCount++;
        E oldValue = elementData(index);

        // 将不包括|index|后的元素向前移动一个索引
        int numMoved = size - index - 1;
        if (numMoved > 0)
            System.arraycopy(elementData, index+1, elementData, index,
                             numMoved);
        // 清除最后一个元素，让GC自动清理
        elementData[--size] = null; // clear to let GC do its work

        return oldValue;
    }

    /**
     * Removes the first occurrence of the specified element from this list,
     * if it is present.  If the list does not contain the element, it is
     * unchanged.  More formally, removes the element with the lowest index
     * <tt>i</tt> such that
     * <tt>(o==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;o.equals(get(i)))</tt>
     * (if such an element exists).  Returns <tt>true</tt> if this list
     * contained the specified element (or equivalently, if this list
     * changed as a result of the call).
     *
     * @param o element to be removed from this list, if present
     * @return <tt>true</tt> if this list contained the specified element
     */
    // 删除容器中第一个等于对象|o|的元素。元素不存在，返回false
    public boolean remove(Object o) {
        if (o == null) {
            for (int index = 0; index < size; index++)
                if (elementData[index] == null) {
                    fastRemove(index);
                    return true;
                }
        } else {
            for (int index = 0; index < size; index++)
                if (o.equals(elementData[index])) {
                    fastRemove(index);
                    return true;
                }
        }
        return false;
    }

    /*
     * Private remove method that skips bounds checking and does not
     * return the value removed.
     */
    // 快速删除指定索引元素。相比remove(int index)，少了越界检测和返回值
    private void fastRemove(int index) {
        // 删除元素时，需设置容器结构有变化的标志位
        modCount++;
        // 将不包括|index|后的元素向前移动一个索引
        int numMoved = size - index - 1;
        if (numMoved > 0)
            System.arraycopy(elementData, index+1, elementData, index,
                             numMoved);
        elementData[--size] = null; // clear to let GC do its work
    }

    /**
     * Removes all of the elements from this list.  The list will
     * be empty after this call returns.
     */
    // 清除容器中所有元素
    public void clear() {
        // 清除容器时，需设置容器结构有变化的标志位
        modCount++;

        // clear to let GC do its work
        // 逐个将容器中的元素置为null，而不是简单的将size置0，主要为了GC
        for (int i = 0; i < size; i++)
            elementData[i] = null;

        size = 0;
    }

    /**
     * Appends all of the elements in the specified collection to the end of
     * this list, in the order that they are returned by the
     * specified collection's Iterator.  The behavior of this operation is
     * undefined if the specified collection is modified while the operation
     * is in progress.  (This implies that the behavior of this call is
     * undefined if the specified collection is this list, and this
     * list is nonempty.)
     *
     * @param c collection containing elements to be added to this list
     * @return <tt>true</tt> if this list changed as a result of the call
     * @throws NullPointerException if the specified collection is null
     */
    // 将容器|c|中的所有元素添加到当前容器的尾部
    // 注：在处理拷贝时，这个方法与ArrayList(Collection<? extends E> c)最大的不同是，拷贝构造中尽力想复用c.toArray()的返回值
    public boolean addAll(Collection<? extends E> c) {
        // 返回容器c中所有元素的数组形式的数据
        Object[] a = c.toArray();
        int numNew = a.length;
        ensureCapacityInternal(size + numNew);  // Increments modCount
        // 将容器|c|中的元素拷贝到当前容器的尾部
        System.arraycopy(a, 0, elementData, size, numNew);
        size += numNew;
        return numNew != 0;
    }

    /**
     * Inserts all of the elements in the specified collection into this
     * list, starting at the specified position.  Shifts the element
     * currently at that position (if any) and any subsequent elements to
     * the right (increases their indices).  The new elements will appear
     * in the list in the order that they are returned by the
     * specified collection's iterator.
     *
     * @param index index at which to insert the first element from the
     *              specified collection
     * @param c collection containing elements to be added to this list
     * @return <tt>true</tt> if this list changed as a result of the call
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @throws NullPointerException if the specified collection is null
     */
    // 将容器|c|中的所有元素顺序的添加到当前容器从|index|索引的起始处，|c|中的首元素存放在当前容器的|index|索引之中
    // 注：若|index|是超尾索引值，则相当于将容器|c|中的所有元素顺序的添加到当前容器的尾部
    public boolean addAll(int index, Collection<? extends E> c) {
        // 添加逻辑中的访问越界判定。索引可以为最后一个元素值（超尾索引），也可以是0
        rangeCheckForAdd(index);

        Object[] a = c.toArray();
        int numNew = a.length;
        ensureCapacityInternal(size + numNew);  // Increments modCount

        // 将包括|index|后的元素向后移动numNew个索引
        int numMoved = size - index;
        if (numMoved > 0)
            System.arraycopy(elementData, index, elementData, index + numNew,
                             numMoved);

        // 将数组|a|中的元素顺序的添加到当前容器从|index|索引的起始处
        System.arraycopy(a, 0, elementData, index, numNew);
        size += numNew;
        return numNew != 0;
    }

    /**
     * Removes from this list all of the elements whose index is between
     * {@code fromIndex}, inclusive, and {@code toIndex}, exclusive.
     * Shifts any succeeding elements to the left (reduces their index).
     * This call shortens the list by {@code (toIndex - fromIndex)} elements.
     * (If {@code toIndex==fromIndex}, this operation has no effect.)
     *
     * @throws IndexOutOfBoundsException if {@code fromIndex} or
     *         {@code toIndex} is out of range
     *         ({@code fromIndex < 0 ||
     *          fromIndex >= size() ||
     *          toIndex > size() ||
     *          toIndex < fromIndex})
     */
    protected void removeRange(int fromIndex, int toIndex) {
        // 删除元素时，需设置容器结构有变化的标志位
        modCount++;
        // 将包括|toIndex|后的元素向前移动到|fromIndex|索引，即可覆盖[fromIndex,toIndex]之前的元素
        int numMoved = size - toIndex;
        System.arraycopy(elementData, toIndex, elementData, fromIndex,
                         numMoved);

        // clear to let GC do its work
        // 逐个将容器尾部|toIndex-fromIndex|个元素置为null，而不是简单的将size置newSize，主要为了GC
        int newSize = size - (toIndex-fromIndex);
        for (int i = newSize; i < size; i++) {
            elementData[i] = null;
        }
        size = newSize;
    }

    /**
     * Checks if the given index is in range.  If not, throws an appropriate
     * runtime exception.  This method does *not* check if the index is
     * negative: It is always used immediately prior to an array access,
     * which throws an ArrayIndexOutOfBoundsException if index is negative.
     */
    private void rangeCheck(int index) {
        // 访问越界判定
        if (index >= size)
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    /**
     * A version of rangeCheck used by add and addAll.
     */
    private void rangeCheckForAdd(int index) {
        // 添加逻辑中的访问越界判定。索引可以为最后一个元素值（超尾索引），也可以是0
        if (index > size || index < 0)
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    /**
     * Constructs an IndexOutOfBoundsException detail message.
     * Of the many possible refactorings of the error handling code,
     * this "outlining" performs best with both server and client VMs.
     */
    private String outOfBoundsMsg(int index) {
        return "Index: "+index+", Size: "+size;
    }

    /**
     * Removes from this list all of its elements that are contained in the
     * specified collection.
     *
     * @param c collection containing elements to be removed from this list
     * @return {@code true} if this list changed as a result of the call
     * @throws ClassCastException if the class of an element of this list
     *         is incompatible with the specified collection
     * (<a href="Collection.html#optional-restrictions">optional</a>)
     * @throws NullPointerException if this list contains a null element and the
     *         specified collection does not permit null elements
     * (<a href="Collection.html#optional-restrictions">optional</a>),
     *         or if the specified collection is null
     * @see Collection#contains(Object)
     */
    // 删除容器中所有与容器|c|中相等的元素
    public boolean removeAll(Collection<?> c) {
        Objects.requireNonNull(c);
        return batchRemove(c, false);
    }

    /**
     * Retains only the elements in this list that are contained in the
     * specified collection.  In other words, removes from this list all
     * of its elements that are not contained in the specified collection.
     *
     * @param c collection containing elements to be retained in this list
     * @return {@code true} if this list changed as a result of the call
     * @throws ClassCastException if the class of an element of this list
     *         is incompatible with the specified collection
     * (<a href="Collection.html#optional-restrictions">optional</a>)
     * @throws NullPointerException if this list contains a null element and the
     *         specified collection does not permit null elements
     * (<a href="Collection.html#optional-restrictions">optional</a>),
     *         or if the specified collection is null
     * @see Collection#contains(Object)
     */
    // 删除容器中除了与容器|c|中相等的元素
    public boolean retainAll(Collection<?> c) {
        Objects.requireNonNull(c);
        return batchRemove(c, true);
    }

    // 当|complement=false|时，删除容器中所有与容器|c|中相等的元素
    // 当|complement=true|时，删除容器中除了与容器|c|中相等的元素
    private boolean batchRemove(Collection<?> c, boolean complement) {
        final Object[] elementData = this.elementData;
        int r = 0, w = 0;
        boolean modified = false;
        try {
            for (; r < size; r++)
                if (c.contains(elementData[r]) == complement)
                    elementData[w++] = elementData[r];
        } finally {
            // Preserve behavioral compatibility with AbstractCollection,
            // even if c.contains() throws.
            // 当c.contains()抛出异常，说明在迭代删除过程中出现了意外
            if (r != size) {
                // 将未处理的元素附加到尾部
                System.arraycopy(elementData, r,
                                 elementData, w,
                                 size - r);
                w += size - r;
            }
            if (w != size) {    // 有元素被删除
                // clear to let GC do its work
                for (int i = w; i < size; i++)
                    elementData[i] = null;
                // 删除元素时，需设置容器结构有变化的标志位
                modCount += size - w;
                size = w;
                modified = true;
            }
        }
        return modified;
    }

    /**
     * Save the state of the <tt>ArrayList</tt> instance to a stream (that
     * is, serialize it).
     *
     * @serialData The length of the array backing the <tt>ArrayList</tt>
     *             instance is emitted (int), followed by all of its elements
     *             (each an <tt>Object</tt>) in the proper order.
     */
    // 序列化容器中的所有状态
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException{
        // Write out element count, and any hidden stuff
        int expectedModCount = modCount;
        s.defaultWriteObject();

        // Write out size as capacity for behavioural compatibility with clone()
        s.writeInt(size);

        // Write out all elements in the proper order.
        for (int i=0; i<size; i++) {
            s.writeObject(elementData[i]);
        }

        // 序列化过程中，容器结构被修改会立即抛出异常。多发生与并发场景中
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException();
        }
    }

    /**
     * Reconstitute the <tt>ArrayList</tt> instance from a stream (that is,
     * deserialize it).
     */
    // 反序列化容器中的所有状态
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        elementData = EMPTY_ELEMENTDATA;

        // Read in size, and any hidden stuff
        s.defaultReadObject();

        // Read in capacity
        s.readInt(); // ignored

        if (size > 0) {
            // be like clone(), allocate array based upon size not capacity
            ensureCapacityInternal(size);

            Object[] a = elementData;
            // Read in all elements in the proper order.
            for (int i=0; i<size; i++) {
                a[i] = s.readObject();
            }
        }
    }

    /**
     * Returns a list iterator over the elements in this list (in proper
     * sequence), starting at the specified position in the list.
     * The specified index indicates the first element that would be
     * returned by an initial call to {@link ListIterator#next next}.
     * An initial call to {@link ListIterator#previous previous} would
     * return the element with the specified index minus one.
     *
     * <p>The returned list iterator is <a href="#fail-fast"><i>fail-fast</i></a>.
     *
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    // 获取列表迭代器。迭代起始位置为原始容器的|index|索引
    public ListIterator<E> listIterator(int index) {
        if (index < 0 || index > size)
            throw new IndexOutOfBoundsException("Index: "+index);
        return new ListItr(index);
    }

    /**
     * Returns a list iterator over the elements in this list (in proper
     * sequence).
     *
     * <p>The returned list iterator is <a href="#fail-fast"><i>fail-fast</i></a>.
     *
     * @see #listIterator(int)
     */
    // 获取列表迭代器。迭代起始位置为原始容器的首元素
    public ListIterator<E> listIterator() {
        return new ListItr(0);
    }

    /**
     * Returns an iterator over the elements in this list in proper sequence.
     *
     * <p>The returned iterator is <a href="#fail-fast"><i>fail-fast</i></a>.
     *
     * @return an iterator over the elements in this list in proper sequence
     */
    // 获取通用的迭代器
    public Iterator<E> iterator() {
        return new Itr();
    }

    /**
     * An optimized version of AbstractList.Itr
     */
    // 通用迭代器类。它只包含 next(), hasNext(), remove(), forEachRemaining() 接口；它没有新增/更新接口
    // 调用remove()前，需调用next()；调用next()前，强烈建议先调用hasNext()
    // 注：迭代器的工作是辅助具体的容器进行索引的自动化管理，它不持有实际的数据
    private class Itr implements Iterator<E> {
        // 当前迭代位置。即，调用next()方法返回的元素
        int cursor;       // index of next element to return
        // 上一次迭代位置。即，调用remove()方法后，迭代器回退索引
        int lastRet = -1; // index of last element returned; -1 if no such

        // 生成迭代器时，立即保存原始容器的修改次数。在使用迭代器过程中，原始容器不能再进行结构性修改
        // 注：modCount属性字段非常重要，可以有效的防止迭代器失效问题
        int expectedModCount = modCount;

        public boolean hasNext() {
            return cursor != size;
        }

        @SuppressWarnings("unchecked")
        public E next() {
            // 在使用迭代器过程中，原始容器不能再进行结构性修改
            checkForComodification();
            int i = cursor;
            // 迭代越界时，立即抛出异常。调用next()前，强烈建议先调用hasNext()
            if (i >= size)
                throw new NoSuchElementException();

            // 访问原始容器中的元素
            Object[] elementData = ArrayList.this.elementData;
            // 迭代越界时，立即抛出异常。此处多发于并发场景中
            if (i >= elementData.length)
                throw new ConcurrentModificationException();

            // 设置下一次next()迭代位置
            cursor = i + 1;
            // 设置上一次迭代位置，并返回本次迭代的元素
            return (E) elementData[lastRet = i];
        }

        // 删除上一次迭代next()方法返回的那个元素
        public void remove() {
            // 调用remove()前，需调用next()
            if (lastRet < 0)
                throw new IllegalStateException();
            // 在使用迭代器过程中，原始容器不能再进行结构性修改
            checkForComodification();

            try {
                // 从原始容器中删除上一次迭代next()方法返回的那个元素
                ArrayList.this.remove(lastRet);
                // 迭代器回退到上一次迭代的索引
                cursor = lastRet;
                // 将上一次迭代索引置为无效
                lastRet = -1;
                // 重新设置结构性修改的次数
                expectedModCount = modCount;
            } catch (IndexOutOfBoundsException ex) {
                throw new ConcurrentModificationException();
            }
        }

        // 从当前迭代索引，批量遍历消费剩余的元素
        @Override
        @SuppressWarnings("unchecked")
        public void forEachRemaining(Consumer<? super E> consumer) {
            Objects.requireNonNull(consumer);
            final int size = ArrayList.this.size;
            int i = cursor;
            // 迭代索引不能超过容器总的元素个数
            if (i >= size) {
                return;
            }
            // 再次判定当前迭代索引不能超过原始容器中元素的个数。多发生于并发场景中
            final Object[] elementData = ArrayList.this.elementData;
            if (i >= elementData.length) {
                throw new ConcurrentModificationException();
            }
            // 从当前迭代位置，遍历消费容器剩余元素。注：在遍历过程中，原始容器不能再进行结构性修改
            while (i != size && modCount == expectedModCount) {
                consumer.accept((E) elementData[i++]);
            }
            // update once at end of iteration to reduce heap write traffic
            // 设置当前迭代索引，即上一次迭代索引
            cursor = i;
            lastRet = i - 1;

            // 在使用迭代器过程中，原始容器不能再进行结构性修改
            checkForComodification();
        }

        // 在使用迭代器过程中，原始容器不能再进行结构性修改
        final void checkForComodification() {
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
        }
    }

    /**
     * An optimized version of AbstractList.ListItr
     */
    // 列表迭代器。主要应用在与位置相关的容器上，比如 ArrayList, LinkedList, SubList
    // 1. 提供增、删、改、查接口，如 set(E e), add(E e)；不管概念还是实现上，这两个接口都与位置相关
    // 2. 相比Iterator接口，增加了前向迭代接口，如 hasPrevious(),previous(),previousIndex(),nextIndex()
    // 注：迭代器的工作是辅助具体的容器进行索引的自动化管理，它不持有实际的数据
    private class ListItr extends Itr implements ListIterator<E> {
        ListItr(int index) {
            super();
            cursor = index;
        }

        public boolean hasPrevious() {
            return cursor != 0;
        }

        public int nextIndex() {
            return cursor;
        }

        public int previousIndex() {
            return cursor - 1;
        }

        @SuppressWarnings("unchecked")
        public E previous() {
            checkForComodification();
            int i = cursor - 1;
            if (i < 0)
                throw new NoSuchElementException();
            Object[] elementData = ArrayList.this.elementData;
            if (i >= elementData.length)
                throw new ConcurrentModificationException();
            cursor = i;
            return (E) elementData[lastRet = i];
        }

        public void set(E e) {
            if (lastRet < 0)
                throw new IllegalStateException();
            checkForComodification();

            try {
                ArrayList.this.set(lastRet, e);
            } catch (IndexOutOfBoundsException ex) {
                throw new ConcurrentModificationException();
            }
        }

        public void add(E e) {
            checkForComodification();

            try {
                int i = cursor;
                ArrayList.this.add(i, e);
                cursor = i + 1;
                lastRet = -1;
                expectedModCount = modCount;
            } catch (IndexOutOfBoundsException ex) {
                throw new ConcurrentModificationException();
            }
        }
    }

    /**
     * Returns a view of the portion of this list between the specified
     * {@code fromIndex}, inclusive, and {@code toIndex}, exclusive.  (If
     * {@code fromIndex} and {@code toIndex} are equal, the returned list is
     * empty.)  The returned list is backed by this list, so non-structural
     * changes in the returned list are reflected in this list, and vice-versa.
     * The returned list supports all of the optional list operations.
     *
     * <p>This method eliminates the need for explicit range operations (of
     * the sort that commonly exist for arrays).  Any operation that expects
     * a list can be used as a range operation by passing a subList view
     * instead of a whole list.  For example, the following idiom
     * removes a range of elements from a list:
     * <pre>
     *      list.subList(from, to).clear();
     * </pre>
     * Similar idioms may be constructed for {@link #indexOf(Object)} and
     * {@link #lastIndexOf(Object)}, and all of the algorithms in the
     * {@link Collections} class can be applied to a subList.
     *
     * <p>The semantics of the list returned by this method become undefined if
     * the backing list (i.e., this list) is <i>structurally modified</i> in
     * any way other than via the returned list.  (Structural modifications are
     * those that change the size of this list, or otherwise perturb it in such
     * a fashion that iterations in progress may yield incorrect results.)
     *
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
     */
    // 获取当前容器的一个子列表视图，元素范围：[fromIndex, toIndex)。从使用上，|toIndex|相当于是一个超尾索引
    public List<E> subList(int fromIndex, int toIndex) {
        subListRangeCheck(fromIndex, toIndex, size);
        return new SubList(this, 0, fromIndex, toIndex);
    }

    // 子列表视图范围检查。从使用上，|toIndex|相当于是一个超尾索引
    static void subListRangeCheck(int fromIndex, int toIndex, int size) {
        if (fromIndex < 0)
            throw new IndexOutOfBoundsException("fromIndex = " + fromIndex);
        if (toIndex > size)
            throw new IndexOutOfBoundsException("toIndex = " + toIndex);
        if (fromIndex > toIndex)
            throw new IllegalArgumentException("fromIndex(" + fromIndex +
                                               ") > toIndex(" + toIndex + ")");
    }

    // 容器的子列表视图。它提供增、删、查接口。它是一个可高效随机访问的列表结构容器
    // 注：子列表主要是对外部提供访问原始容器部分元素的一个视图，它不持有实际的数据
    private class SubList extends AbstractList<E> implements RandomAccess {
        // 当前子列表视图的直接父列表容器的引用。注：SubList可递归生成子列表视图
        private final AbstractList<E> parent;
        // 当前子列表视图的首元素在直接父列表容器中的索引偏移
        private final int parentOffset;
        // 当前子列表视图的首元素在原始容器中的索引偏移
        private final int offset;
        // 当前子列表视图中元素个数
        int size;

        SubList(AbstractList<E> parent,
                int offset, int fromIndex, int toIndex) {
            this.parent = parent;
            this.parentOffset = fromIndex;
            this.offset = offset + fromIndex;
            // 视图的元素范围：[fromIndex, toIndex)，从使用上，|toIndex|相当于是一个超尾索引
            this.size = toIndex - fromIndex;
            // 生成子列表视图时，立即保存原始容器的修改次数。在使用视图过程中，原始容器不能再进行结构性修改
            // 注：modCount属性字段非常重要，可以有效的防止视图非法访问的问题
            this.modCount = ArrayList.this.modCount;
        }

        public E set(int index, E e) {
            rangeCheck(index);
            checkForComodification();
            // 直接更新原始容器中指定索引的元素
            E oldValue = ArrayList.this.elementData(offset + index);
            ArrayList.this.elementData[offset + index] = e;
            return oldValue;
        }

        public E get(int index) {
            rangeCheck(index);
            checkForComodification();
            // 直接返回原始容器中指定索引的元素
            return ArrayList.this.elementData(offset + index);
        }

        public int size() {
            checkForComodification();
            return this.size;
        }

        public void add(int index, E e) {
            rangeCheckForAdd(index);
            checkForComodification();
            // 调用父容器的新增接口，如果父容器是一个SubList类实例，将会有递归调用
            // 注：此处不直接操作原始容器，主要是为了修改父容器的modCount与size字段
            parent.add(parentOffset + index, e);
            this.modCount = parent.modCount;
            this.size++;
        }

        public E remove(int index) {
            rangeCheck(index);
            checkForComodification();
            // 调用父容器的删除接口，如果父容器是一个SubList类实例，将会有递归调用
            // 注：此处不直接操作原始容器，主要是为了修改父容器的modCount与size字段
            E result = parent.remove(parentOffset + index);
            this.modCount = parent.modCount;
            this.size--;
            return result;
        }

        protected void removeRange(int fromIndex, int toIndex) {
            checkForComodification();
            // 调用父容器的范围删除接口，如果父容器是一个SubList类实例，将会有递归调用
            // 注：此处不直接操作原始容器，主要是为了修改父容器的modCount与size字段
            parent.removeRange(parentOffset + fromIndex,
                               parentOffset + toIndex);
            this.modCount = parent.modCount;
            this.size -= toIndex - fromIndex;
        }

        public boolean addAll(Collection<? extends E> c) {
            // 在尾部附加|c|容器中的所有元素
            return addAll(this.size, c);
        }

        public boolean addAll(int index, Collection<? extends E> c) {
            rangeCheckForAdd(index);
            int cSize = c.size();
            if (cSize==0)
                return false;

            checkForComodification();
            // 调用父容器的批量添加接口，如果父容器是一个SubList类实例，将会有递归调用
            // 注：此处不直接操作原始容器，主要是为了修改父容器的modCount与size字段
            parent.addAll(parentOffset + index, c);
            this.modCount = parent.modCount;
            this.size += cSize;
            return true;
        }

        public Iterator<E> iterator() {
            return listIterator();
        }

        // 返回列表迭代器实例（SubList内部类），起始偏移为|index|
        public ListIterator<E> listIterator(final int index) {
            checkForComodification();
            rangeCheckForAdd(index);
            final int offset = this.offset;

            // 创建并返回列表迭代的内部类对象实例
            return new ListIterator<E>() {
                int cursor = index;
                int lastRet = -1;
                int expectedModCount = ArrayList.this.modCount;

                public boolean hasNext() {
                    return cursor != SubList.this.size;
                }

                @SuppressWarnings("unchecked")
                public E next() {
                    checkForComodification();
                    int i = cursor;
                    if (i >= SubList.this.size)
                        throw new NoSuchElementException();
                    // 直接返回原始容器中指定索引的元素
                    Object[] elementData = ArrayList.this.elementData;
                    if (offset + i >= elementData.length)
                        throw new ConcurrentModificationException();
                    cursor = i + 1;
                    return (E) elementData[offset + (lastRet = i)];
                }

                public boolean hasPrevious() {
                    return cursor != 0;
                }

                @SuppressWarnings("unchecked")
                public E previous() {
                    checkForComodification();
                    int i = cursor - 1;
                    if (i < 0)
                        throw new NoSuchElementException();
                    // 直接返回原始容器中指定索引的元素
                    Object[] elementData = ArrayList.this.elementData;
                    if (offset + i >= elementData.length)
                        throw new ConcurrentModificationException();
                    cursor = i;
                    return (E) elementData[offset + (lastRet = i)];
                }

                @SuppressWarnings("unchecked")
                public void forEachRemaining(Consumer<? super E> consumer) {
                    Objects.requireNonNull(consumer);
                    final int size = SubList.this.size;
                    int i = cursor;
                    if (i >= size) {
                        return;
                    }
                    // 直接遍历原始容器中的元素
                    final Object[] elementData = ArrayList.this.elementData;
                    if (offset + i >= elementData.length) {
                        throw new ConcurrentModificationException();
                    }
                    while (i != size && modCount == expectedModCount) {
                        consumer.accept((E) elementData[offset + (i++)]);
                    }
                    // update once at end of iteration to reduce heap write traffic
                    lastRet = cursor = i;
                    checkForComodification();
                }

                public int nextIndex() {
                    return cursor;
                }

                public int previousIndex() {
                    return cursor - 1;
                }

                public void remove() {
                    if (lastRet < 0)
                        throw new IllegalStateException();
                    checkForComodification();

                    try {
                        // 调用SubList容器的删除接口
                        // 注：此处不直接操作原始容器，主要是为了修改SubList容器的modCount与size字段
                        SubList.this.remove(lastRet);
                        cursor = lastRet;
                        lastRet = -1;
                        expectedModCount = ArrayList.this.modCount;
                    } catch (IndexOutOfBoundsException ex) {
                        throw new ConcurrentModificationException();
                    }
                }

                public void set(E e) {
                    if (lastRet < 0)
                        throw new IllegalStateException();
                    checkForComodification();

                    try {
                        // 直接更新原始容器中指定索引的元素
                        ArrayList.this.set(offset + lastRet, e);
                    } catch (IndexOutOfBoundsException ex) {
                        throw new ConcurrentModificationException();
                    }
                }

                public void add(E e) {
                    checkForComodification();

                    try {
                        int i = cursor;
                        // 调用SubList容器的新增接口
                        // 注：此处不直接操作原始容器，主要是为了修改SubList容器的modCount与size字段
                        SubList.this.add(i, e);
                        cursor = i + 1;
                        lastRet = -1;
                        expectedModCount = ArrayList.this.modCount;
                    } catch (IndexOutOfBoundsException ex) {
                        throw new ConcurrentModificationException();
                    }
                }

                final void checkForComodification() {
                    if (expectedModCount != ArrayList.this.modCount)
                        throw new ConcurrentModificationException();
                }
            };
        }

        // 递归生成子列表视图。视图的元素范围：[fromIndex, toIndex)，从使用上，|toIndex|相当于是一个超尾索引
        // 注：|fromIndex|与|toIndex|是相对于父容器（调用者）的索引偏移，而非原始容器
        public List<E> subList(int fromIndex, int toIndex) {
            subListRangeCheck(fromIndex, toIndex, size);
            return new SubList(this, offset, fromIndex, toIndex);
        }

        // 索引越界检查。不能使用超尾索引
        private void rangeCheck(int index) {
            if (index < 0 || index >= this.size)
                throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
        }

        // 添加接口的索引越界检查。可以使用超尾索引
        private void rangeCheckForAdd(int index) {
            if (index < 0 || index > this.size)
                throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
        }

        private String outOfBoundsMsg(int index) {
            return "Index: "+index+", Size: "+this.size;
        }

        // 在使用视图过程中，原始容器不能再进行结构性修改
        private void checkForComodification() {
            if (ArrayList.this.modCount != this.modCount)
                throw new ConcurrentModificationException();
        }

        // 获取子列表视图的分隔器对象实例。用于Stream流中
        public Spliterator<E> spliterator() {
            checkForComodification();
            return new ArrayListSpliterator<E>(ArrayList.this, offset,
                                               offset + this.size, this.modCount);
        }
    }

    // 遍历消费容器中所有元素
    @Override
    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        // 保存遍历前容器修改次数
        final int expectedModCount = modCount;
        // 保存遍历前容器的所有元素
        @SuppressWarnings("unchecked")
        final E[] elementData = (E[]) this.elementData;
        // 保存遍历前容器元素个数
        final int size = this.size;
        // 遍历消费容器元素。注：在遍历过程中，原始容器不能再进行结构性修改
        for (int i=0; modCount == expectedModCount && i < size; i++) {
            action.accept(elementData[i]);
        }
        // 在遍历过程中，原始容器不能再进行结构性修改
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException();
        }
    }

    /**
     * Creates a <em><a href="Spliterator.html#binding">late-binding</a></em>
     * and <em>fail-fast</em> {@link Spliterator} over the elements in this
     * list.
     *
     * <p>The {@code Spliterator} reports {@link Spliterator#SIZED},
     * {@link Spliterator#SUBSIZED}, and {@link Spliterator#ORDERED}.
     * Overriding implementations should document the reporting of additional
     * characteristic values.
     *
     * @return a {@code Spliterator} over the elements in this list
     * @since 1.8
     */
    // 获取ArrayList容器的分割器对象实例。用于Stream流中
    @Override
    public Spliterator<E> spliterator() {
        return new ArrayListSpliterator<>(this, 0, -1, 0);
    }

    /** Index-based split-by-two, lazily initialized Spliterator */
    // 算法核心是对容器进行二分切割。场景：将大数据反复“裂变”成一系列小数据。多应用在stream流处理中
    // 注：分割器是一种特殊的迭代器
    static final class ArrayListSpliterator<E> implements Spliterator<E> {

        /*
         * If ArrayLists were immutable, or structurally immutable (no
         * adds, removes, etc), we could implement their spliterators
         * with Arrays.spliterator. Instead we detect as much
         * interference during traversal as practical without
         * sacrificing much performance. We rely primarily on
         * modCounts. These are not guaranteed to detect concurrency
         * violations, and are sometimes overly conservative about
         * within-thread interference, but detect enough problems to
         * be worthwhile in practice. To carry this out, we (1) lazily
         * initialize fence and expectedModCount until the latest
         * point that we need to commit to the state we are checking
         * against; thus improving precision.  (This doesn't apply to
         * SubLists, that create spliterators with current non-lazy
         * values).  (2) We perform only a single
         * ConcurrentModificationException check at the end of forEach
         * (the most performance-sensitive method). When using forEach
         * (as opposed to iterators), we can normally only detect
         * interference after actions, not before. Further
         * CME-triggering checks apply to all other possible
         * violations of assumptions for example null or too-small
         * elementData array given its size(), that could only have
         * occurred due to interference.  This allows the inner loop
         * of forEach to run without any further checks, and
         * simplifies lambda-resolution. While this does entail a
         * number of checks, note that in the common case of
         * list.stream().forEach(a), no checks or other computation
         * occur anywhere other than inside forEach itself.  The other
         * less-often-used methods cannot take advantage of most of
         * these streamlinings.
         */

        // 原始容器的引用
        private final ArrayList<E> list;
        // 分割器的起始索引，也是分割器的正向消费元素进度索引
        private int index; // current index, modified on advance/split
        // 分割器的中含有元素个数，也是一个超尾索引（one past last index）
        private int fence; // -1 until used; then one past last index
        // 第一次分割、消费分隔器时，立即保存原始容器的修改次数。在使用分割器过程中，原始容器不能再进行结构性修改
        // 注：modCount属性字段非常重要，可以有效的防止分割器非法访问的问题
        private int expectedModCount; // initialized when fence set

        /** Create new spliterator covering the given  range */
        ArrayListSpliterator(ArrayList<E> list, int origin, int fence,
                             int expectedModCount) {
            this.list = list; // OK if null unless traversed
            this.index = origin;
            this.fence = fence;
            this.expectedModCount = expectedModCount;
        }

        // 获取分割器的中含有元素个数。首个分隔器首次调用 getFence() 时 fence==-1，将其设置为原始容器的长度
        // 注：区分首个分割器，是因为分割器在使用过程中会不停的二分递归切分
        private int getFence() { // initialize fence to size on first use
            int hi; // (a specialized variant appears in method forEach)
            ArrayList<E> lst;
            if ((hi = fence) < 0) {
                if ((lst = list) == null)
                    hi = fence = 0;
                else {
                    // 第一次使用分割器时，立即保存原始容器的修改次数。在使用分割器过程中，原始容器不能再进行结构性修改
                    // 注：modCount属性字段非常重要，可以有效的防止分割器非法访问的问题
                    expectedModCount = lst.modCount;
                    hi = fence = lst.size;
                }
            }
            return hi;
        }

        // 二分切割分割器，返回的新分割器起始索引等于原始分隔器，结尾索引是原始容器的二分之一；而原始分割器的起始索引被重置为二分之一
        // 即：切分后，原始的分割器引用后一半数据，返回的新分割器引用前一半数据
        public ArrayListSpliterator<E> trySplit() {
            // 分割器中剩余的元素个数
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            return (lo >= mid) ? null : // divide range in half unless too small
                new ArrayListSpliterator<E>(list, lo, index = mid,
                                            expectedModCount);
        }

        // 消费分割器中首个元素，执行指定方法
        public boolean tryAdvance(Consumer<? super E> action) {
            if (action == null)
                throw new NullPointerException();
            int hi = getFence(), i = index;
            if (i < hi) {
                // 设置下一次tryAdvance()的元素索引。即，当前元素被消费
                index = i + 1;
                @SuppressWarnings("unchecked") E e = (E)list.elementData[i];
                action.accept(e);
                // 在使用分割器过程中，原始容器不能再进行结构性修改
                if (list.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                return true;
            }
            return false;
        }

        // 消费分割器中剩余元素，执行指定方法
        public void forEachRemaining(Consumer<? super E> action) {
            int i, hi, mc; // hoist accesses and checks from loop
            ArrayList<E> lst; Object[] a;
            if (action == null)
                throw new NullPointerException();
            // 获取原始容器的元素
            if ((lst = list) != null && (a = lst.elementData) != null) {
                // 获取结束索引hi。首个分割器首次时fence==-1，将其设置为原始容器的结尾索引
                // 注：区分首个分隔器，是因为分割器在使用过程中会不停的二分递归切分
                if ((hi = fence) < 0) {
                    mc = lst.modCount;
                    hi = lst.size;
                }
                else
                    mc = expectedModCount;
                // 消费分隔器中剩余元素（|index=hi|代表消费剩余所有元素）
                if ((i = index) >= 0 && (index = hi) <= a.length) {
                    // 遍历分割器中剩余元素，执行指定方法
                    for (; i < hi; ++i) {
                        @SuppressWarnings("unchecked") E e = (E) a[i];
                        action.accept(e);
                    }
                    // 遍历成功，直接返回
                    if (lst.modCount == mc)
                        return;
                }
            }
            // 在使用分割器过程中，原始容器不能再进行结构性修改
            throw new ConcurrentModificationException();
        }

        // 评估剩余元素数量的大小
        public long estimateSize() {
            return (long) (getFence() - index);
        }
        
        // 分割器特征
        public int characteristics() {
            // ORDERED表示该分割器的迭代顺序是按照原本容器中的顺序
            // SIZED表示该分割器的大小是有限的
            // SUBSIZED表示该分割器所分割得到的子分隔器也是有限的
            // 注：因为原始分割器（父分割器）是基于ArrayList的有序列表容器，故以上三个特征容易推出
            return Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED;
        }
    }

    // 按指定删除方法，删除ArrayList中的元素
    // 注：该方法将删除分成：查找待删除元素、删除元素两个步骤。可保证查找元素时，若有异常发生或原始容器结构发生了变化，容器将不做任何修改
    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        Objects.requireNonNull(filter);
        // figure out which elements are to be removed
        // any exception thrown from the filter predicate at this stage
        // will leave the collection unmodified

        // 待删除元素个数
        int removeCount = 0;
        // 待删除的元素索引的集合
        final BitSet removeSet = new BitSet(size);
        final int expectedModCount = modCount;
        final int size = this.size;
        // 遍历查找待删除的元素索引
        for (int i=0; modCount == expectedModCount && i < size; i++) {
            @SuppressWarnings("unchecked")
            final E element = (E) elementData[i];
            if (filter.test(element)) {
                removeSet.set(i);
                removeCount++;
            }
        }
        // 在查找待删除元素过程中，容器不能再进行结构性修改
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException();
        }

        // shift surviving elements left over the spaces left by removed elements
        final boolean anyToRemove = removeCount > 0;
        if (anyToRemove) {
            final int newSize = size - removeCount;
            // 顺序保存"除待删除"元素到容器前面
            for (int i=0, j=0; (i < size) && (j < newSize); i++, j++) {
                i = removeSet.nextClearBit(i);
                elementData[j] = elementData[i];
            }
            // 清除容器后面的"待删除"元素
            for (int k=newSize; k < size; k++) {
                elementData[k] = null;  // Let gc do its work
            }
            this.size = newSize;
            // 在删除过程中，容器不能再进行结构性修改
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
            modCount++;
        }

        return anyToRemove;
    }

    // 按指定替换方法，替换ArrayList中的元素
    @Override
    @SuppressWarnings("unchecked")
    public void replaceAll(UnaryOperator<E> operator) {
        Objects.requireNonNull(operator);
        final int expectedModCount = modCount;
        final int size = this.size;
        // 遍历替换容器中所有元素
        for (int i=0; modCount == expectedModCount && i < size; i++) {
            elementData[i] = operator.apply((E) elementData[i]);
        }
        // 在替换过程中，容器不能再进行结构性修改
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException();
        }
        modCount++;
    }

    // 按指定排序方法，排序ArrayList中的元素
    @Override
    @SuppressWarnings("unchecked")
    public void sort(Comparator<? super E> c) {
        final int expectedModCount = modCount;
        Arrays.sort((E[]) elementData, 0, size, c);
        // 在排序过程中，容器不能再进行结构性修改
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException();
        }
        modCount++;
    }
}
