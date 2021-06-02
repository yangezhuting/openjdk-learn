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

/**
 * Doubly-linked list implementation of the {@code List} and {@code Deque}
 * interfaces.  Implements all optional list operations, and permits all
 * elements (including {@code null}).
 *
 * <p>All of the operations perform as could be expected for a doubly-linked
 * list.  Operations that index into the list will traverse the list from
 * the beginning or the end, whichever is closer to the specified index.
 *
 * <p><strong>Note that this implementation is not synchronized.</strong>
 * If multiple threads access a linked list concurrently, and at least
 * one of the threads modifies the list structurally, it <i>must</i> be
 * synchronized externally.  (A structural modification is any operation
 * that adds or deletes one or more elements; merely setting the value of
 * an element is not a structural modification.)  This is typically
 * accomplished by synchronizing on some object that naturally
 * encapsulates the list.
 *
 * If no such object exists, the list should be "wrapped" using the
 * {@link Collections#synchronizedList Collections.synchronizedList}
 * method.  This is best done at creation time, to prevent accidental
 * unsynchronized access to the list:<pre>
 *   List list = Collections.synchronizedList(new LinkedList(...));</pre>
 *
 * <p>The iterators returned by this class's {@code iterator} and
 * {@code listIterator} methods are <i>fail-fast</i>: if the list is
 * structurally modified at any time after the iterator is created, in
 * any way except through the Iterator's own {@code remove} or
 * {@code add} methods, the iterator will throw a {@link
 * ConcurrentModificationException}.  Thus, in the face of concurrent
 * modification, the iterator fails quickly and cleanly, rather than
 * risking arbitrary, non-deterministic behavior at an undetermined
 * time in the future.
 *
 * <p>Note that the fail-fast behavior of an iterator cannot be guaranteed
 * as it is, generally speaking, impossible to make any hard guarantees in the
 * presence of unsynchronized concurrent modification.  Fail-fast iterators
 * throw {@code ConcurrentModificationException} on a best-effort basis.
 * Therefore, it would be wrong to write a program that depended on this
 * exception for its correctness:   <i>the fail-fast behavior of iterators
 * should be used only to detect bugs.</i>
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @author  Josh Bloch
 * @see     List
 * @see     ArrayList
 * @since 1.2
 * @param <E> the type of elements held in this collection
 */

// 可高效的进行插入、删除的列表结构。可作为队列结构（头尾操作）使用，用 List,Deque 标识
public class LinkedList<E>
    extends AbstractSequentialList<E>
    implements List<E>, Deque<E>, Cloneable, java.io.Serializable
{
    // 容器中元素的实际个数
    transient int size = 0;

    /**
     * Pointer to first node.
     * Invariant: (first == null && last == null) ||
     *            (first.prev == null && first.item != null)
     */
    // 链表首节点的指针
    transient Node<E> first;

    /**
     * Pointer to last node.
     * Invariant: (first == null && last == null) ||
     *            (last.next == null && last.item != null)
     */
    // 链表尾节点的指针
    transient Node<E> last;

    /**
     * Constructs an empty list.
     */
    public LinkedList() {
    }

    /**
     * Constructs a list containing the elements of the specified
     * collection, in the order they are returned by the collection's
     * iterator.
     *
     * @param  c the collection whose elements are to be placed into this list
     * @throws NullPointerException if the specified collection is null
     */
    // 构造一个列表容器，其中的元素来自容器|c|中的元素
    public LinkedList(Collection<? extends E> c) {
        this();
        addAll(c);
    }

    /**
     * Links e as first element.
     */
    // 将数据|e|插入链表头部
    private void linkFirst(E e) {
        final Node<E> f = first;
        // 将数据|e|包装成链表的节点，其前驱节点指针为null，后驱节点指针为原始的头部节点的指针
        final Node<E> newNode = new Node<>(null, e, f);
        // 将头部节点指针设置为新节点
        first = newNode;
        // 1. 若原始头部节点指针为空，将链表尾节点指针设置为新节点
        // 2. 若原始头部节点指针不为空，将原始头部节点指针指向的节点的前驱指针设置为新节点
        if (f == null)
            last = newNode;
        else
            f.prev = newNode;
        size++;
        // 添加元素时，需设置容器结构有变化的标志位
        modCount++;
    }

    /**
     * Links e as last element.
     */
    // 将数据|e|插入链表尾部
    void linkLast(E e) {
        final Node<E> l = last;
        // 将数据|e|包装成链表的节点，其后驱节点指针为null，前驱节点指针为原始的尾部节点的指针
        final Node<E> newNode = new Node<>(l, e, null);
        // 将尾部节点指针设置为新节点
        last = newNode;
        // 1. 若原始尾部节点指针为空，将链表尾节点指针设置为新节点
        // 2. 若原始尾部节点指针不为空，将原始尾部节点指针指向的节点的后驱指针设置为新节点
        if (l == null)
            first = newNode;
        else
            l.next = newNode;
        size++;
        // 添加元素时，需设置容器结构有变化的标志位
        modCount++;
    }

    /**
     * Inserts element e before non-null Node succ.
     */
    // 将数据|e|插入链表节点|succ|的前面
    // 注：|succ|参数不能为null
    void linkBefore(E e, Node<E> succ) {
        // assert succ != null;
        final Node<E> pred = succ.prev;
        // 将数据|e|包装成链表的节点，其前驱节点指针为|succ|的前驱节点的指针，后驱节点指针为|succ|
        final Node<E> newNode = new Node<>(pred, e, succ);
        // 将|succ|的前驱节点指针设置为新节点
        succ.prev = newNode;
        // 1. 若|succ|的前驱节点指针为空，说明|succ|节点就是头节点，将链表头节点指针设置为新节点
        // 2. 若|succ|的前驱节点指针不为空，将|succ|的前驱节点的后驱节点指针设置为新节点
        if (pred == null)
            first = newNode;
        else
            pred.next = newNode;
        size++;
        // 添加元素时，需设置容器结构有变化的标志位
        modCount++;
    }

    /**
     * Unlinks non-null first node f.
     */
    // 从链表中删除头节点|f|，返回头节点中存放的数据
    // 注：|f|不能为空，且必须是头节点
    private E unlinkFirst(Node<E> f) {
        // assert f == first && f != null;
        final E element = f.item;
        // 删除头节点|f|，将头节点指针设置为原始头节点的后驱节点
        final Node<E> next = f.next;
        f.item = null;
        f.next = null; // help GC
        first = next;
        // 1. 若原始头节点的后驱节点指针为空，说明链表只有一个元素，将链表尾节点指针也设置为null
        // 2. 若原始头节点的后驱节点指针不为空，将原始头节点的后驱节点的前驱节点指针设置为null
        if (next == null)
            last = null;
        else
            next.prev = null;
        size--;
        // 删除元素时，需设置容器结构有变化的标志位
        modCount++;
        return element;
    }

    /**
     * Unlinks non-null last node l.
     */
    // 从链表中删除尾节点|l|，返回尾节点中存放的数据
    // 注：|l|不能为空，且必须是尾节点
    private E unlinkLast(Node<E> l) {
        // assert l == last && l != null;
        final E element = l.item;
        // 删除尾节点|f|，将尾节点指针设置为原始头节点的前驱节点
        final Node<E> prev = l.prev;
        l.item = null;
        l.prev = null; // help GC
        last = prev;
        // 1. 若原始尾节点的前驱节点指针为空，说明链表只有一个元素，将链表头节点指针也设置为null
        // 2. 若原始尾节点的前驱节点指针不为空，将原始尾节点的前驱节点的后驱节点指针设置为null
        if (prev == null)
            first = null;
        else
            prev.next = null;
        size--;
        // 删除元素时，需设置容器结构有变化的标志位
        modCount++;
        return element;
    }

    /**
     * Unlinks non-null node x.
     */
    // 从链表中删除节点|x|，返回|x|节点中存放的数据
    // 注：|x|不能为空
    E unlink(Node<E> x) {
        // assert x != null;
        final E element = x.item;
        final Node<E> next = x.next;
        final Node<E> prev = x.prev;

        // 1. 若|x|节点的前驱节点指针为空，说明链表只有一个元素，将链表头节点指针也设置为|x|的后驱节点指针
        // 2. 若|x|节点的前驱节点指针不为空，将|x|节点的前驱节点的后驱节点指针设置为|x|的后驱节点指针
        if (prev == null) {
            first = next;
        } else {
            prev.next = next;
            // 使GC功能有效
            x.prev = null;
        }

        // 1. 若|x|节点的后驱节点指针为空，说明链表只有一个元素，将链表尾节点指针设置为|x|的前驱节点指针
        // 2. 若|x|节点的后驱节点指针不为空，将|x|节点的后驱节点的前驱节点指针设置为|x|的前驱节点指针
        if (next == null) {
            last = prev;
        } else {
            next.prev = prev;
            // 使GC功能有效
            x.next = null;
        }

        // 使GC功能有效
        x.item = null;
        size--;
        // 删除元素时，需设置容器结构有变化的标志位
        modCount++;
        return element;
    }

    /**
     * Returns the first element in this list.
     *
     * @return the first element in this list
     * @throws NoSuchElementException if this list is empty
     */
    // 获取头节点中的数据
    public E getFirst() {
        final Node<E> f = first;
        if (f == null)
            throw new NoSuchElementException();
        return f.item;
    }

    /**
     * Returns the last element in this list.
     *
     * @return the last element in this list
     * @throws NoSuchElementException if this list is empty
     */
    // 获取尾节点中的数据
    public E getLast() {
        final Node<E> l = last;
        if (l == null)
            throw new NoSuchElementException();
        return l.item;
    }

    /**
     * Removes and returns the first element from this list.
     *
     * @return the first element from this list
     * @throws NoSuchElementException if this list is empty
     */
    // 从链表中删除头节点，返回头节点中存放的数据
    // 注：链表无节点，立即抛出异常
    public E removeFirst() {
        final Node<E> f = first;
        if (f == null)
            throw new NoSuchElementException();
        return unlinkFirst(f);
    }

    /**
     * Removes and returns the last element from this list.
     *
     * @return the last element from this list
     * @throws NoSuchElementException if this list is empty
     */
    // 从链表中删除尾节点，返回尾节点中存放的数据
    // 注：链表无节点，立即抛出异常
    public E removeLast() {
        final Node<E> l = last;
        if (l == null)
            throw new NoSuchElementException();
        return unlinkLast(l);
    }

    /**
     * Inserts the specified element at the beginning of this list.
     *
     * @param e the element to add
     */
    // 将数据|e|插入链表头部
    public void addFirst(E e) {
        linkFirst(e);
    }

    /**
     * Appends the specified element to the end of this list.
     *
     * <p>This method is equivalent to {@link #add}.
     *
     * @param e the element to add
     */
    // 将数据|e|插入链表尾部
    public void addLast(E e) {
        linkLast(e);
    }

    /**
     * Returns {@code true} if this list contains the specified element.
     * More formally, returns {@code true} if and only if this list contains
     * at least one element {@code e} such that
     * <tt>(o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e))</tt>.
     *
     * @param o element whose presence in this list is to be tested
     * @return {@code true} if this list contains the specified element
     */
    // 查找对象|o|是否在列表中
    public boolean contains(Object o) {
        return indexOf(o) != -1;
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
     * Appends the specified element to the end of this list.
     *
     * <p>This method is equivalent to {@link #addLast}.
     *
     * @param e element to be appended to this list
     * @return {@code true} (as specified by {@link Collection#add})
     */
    // 默认的添加方法，将数据|e|插入链表尾部
    public boolean add(E e) {
        linkLast(e);
        return true;
    }

    /**
     * Removes the first occurrence of the specified element from this list,
     * if it is present.  If this list does not contain the element, it is
     * unchanged.  More formally, removes the element with the lowest index
     * {@code i} such that
     * <tt>(o==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;o.equals(get(i)))</tt>
     * (if such an element exists).  Returns {@code true} if this list
     * contained the specified element (or equivalently, if this list
     * changed as a result of the call).
     *
     * @param o element to be removed from this list, if present
     * @return {@code true} if this list contained the specified element
     */
    // 删除容器中第一个等于对象|o|的元素。元素不存在，返回false
    public boolean remove(Object o) {
        if (o == null) {
            for (Node<E> x = first; x != null; x = x.next) {
                if (x.item == null) {
                    unlink(x);
                    return true;
                }
            }
        } else {
            for (Node<E> x = first; x != null; x = x.next) {
                if (o.equals(x.item)) {
                    unlink(x);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Appends all of the elements in the specified collection to the end of
     * this list, in the order that they are returned by the specified
     * collection's iterator.  The behavior of this operation is undefined if
     * the specified collection is modified while the operation is in
     * progress.  (Note that this will occur if the specified collection is
     * this list, and it's nonempty.)
     *
     * @param c collection containing elements to be added to this list
     * @return {@code true} if this list changed as a result of the call
     * @throws NullPointerException if the specified collection is null
     */
    // 将容器|c|中的所有元素顺序的添加到当前容器的尾部
    public boolean addAll(Collection<? extends E> c) {
        return addAll(size, c);
    }

    /**
     * Inserts all of the elements in the specified collection into this
     * list, starting at the specified position.  Shifts the element
     * currently at that position (if any) and any subsequent elements to
     * the right (increases their indices).  The new elements will appear
     * in the list in the order that they are returned by the
     * specified collection's iterator.
     *
     * @param index index at which to insert the first element
     *              from the specified collection
     * @param c collection containing elements to be added to this list
     * @return {@code true} if this list changed as a result of the call
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @throws NullPointerException if the specified collection is null
     */
    // 将容器|c|中的所有元素顺序的添加到当前容器从|index|索引的起始处，|c|中的首元素存放在当前容器的|index|索引之中
    // 注：若|index|是超尾索引值，则相当于将容器|c|中的所有元素顺序的添加到当前容器的尾部
    public boolean addAll(int index, Collection<? extends E> c) {
        // 添加逻辑中的访问越界判定。索引可以为最后一个元素值（超尾索引），也可以是0
        checkPositionIndex(index);

        // 将容器|c|中的元素转换至数组
        Object[] a = c.toArray();
        int numNew = a.length;
        if (numNew == 0)
            return false;

        // 获取插入点的前驱指针、|index|索引的节点
        Node<E> pred, succ;
        if (index == size) {
            succ = null;
            pred = last;
        } else {
            succ = node(index);
            pred = succ.prev;
        }

        // 遍历数组，将他们顺序的添加到当前容器从|index|索引的起始处
        for (Object o : a) {
            @SuppressWarnings("unchecked") E e = (E) o;
            Node<E> newNode = new Node<>(pred, e, null);
            if (pred == null)
                first = newNode;
            else
                pred.next = newNode;
            // 重置前驱节点，保持正向添加。即，新容器中元素的顺序与原始容器|c|中顺序一致
            pred = newNode;
        }

        // 1. 若|succ|指针为空，说明是尾部添加元素，将链表尾节点指针设置为|c|的容器中最后一个节点
        // 2. 若|succ|指针不为空，联通|succ|节点的前后节点的指针
        if (succ == null) {
            last = pred;
        } else {
            pred.next = succ;
            succ.prev = pred;
        }

        size += numNew;
        // 添加元素时，需设置容器结构有变化的标志位
        modCount++;
        return true;
    }

    /**
     * Removes all of the elements from this list.
     * The list will be empty after this call returns.
     */
    // 清除容器中所有元素
    public void clear() {
        // Clearing all of the links between nodes is "unnecessary", but:
        // - helps a generational GC if the discarded nodes inhabit
        //   more than one generation
        // - is sure to free memory even if there is a reachable Iterator
        // 逐个将容器中的元素置为null，而不是简单的将size置0，主要为了GC
        for (Node<E> x = first; x != null; ) {
            Node<E> next = x.next;
            x.item = null;
            x.next = null;
            x.prev = null;
            x = next;
        }
        first = last = null;
        size = 0;
        // 清除容器时，需设置容器结构有变化的标志位
        modCount++;
    }


    // Positional Access Operations

    /**
     * Returns the element at the specified position in this list.
     *
     * @param index index of the element to return
     * @return the element at the specified position in this list
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public E get(int index) {
        // 访问越界判定
        checkElementIndex(index);
        // 获取|index|索引位置的链表节点中实际数据
        return node(index).item;
    }

    /**
     * Replaces the element at the specified position in this list with the
     * specified element.
     *
     * @param index index of the element to replace
     * @param element element to be stored at the specified position
     * @return the element previously at the specified position
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public E set(int index, E element) {
        // 访问越界判定
        checkElementIndex(index);
        // 获取并设置|index|索引位置的链表节点
        Node<E> x = node(index);
        E oldVal = x.item;
        x.item = element;
        return oldVal;
    }

    /**
     * Inserts the specified element at the specified position in this list.
     * Shifts the element currently at that position (if any) and any
     * subsequent elements to the right (adds one to their indices).
     *
     * @param index index at which the specified element is to be inserted
     * @param element element to be inserted
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public void add(int index, E element) {
        // 添加方法中访问越界判定
        checkPositionIndex(index);

        // 1. 将数据|element|添加到尾部
        // 2. 将数据|element|插入|index|链表节点的前面（自己成为|index|索引节点）
        if (index == size)
            linkLast(element);
        else
            linkBefore(element, node(index));
    }

    /**
     * Removes the element at the specified position in this list.  Shifts any
     * subsequent elements to the left (subtracts one from their indices).
     * Returns the element that was removed from the list.
     *
     * @param index the index of the element to be removed
     * @return the element previously at the specified position
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public E remove(int index) {
        // 访问越界判定
        checkElementIndex(index);
        // 从链表中删除|index|索引节点，返回|index|索引节点中存放的数据
        return unlink(node(index));
    }

    /**
     * Tells if the argument is the index of an existing element.
     */
    private boolean isElementIndex(int index) {
        return index >= 0 && index < size;
    }

    /**
     * Tells if the argument is the index of a valid position for an
     * iterator or an add operation.
     */
    private boolean isPositionIndex(int index) {
        return index >= 0 && index <= size;
    }

    /**
     * Constructs an IndexOutOfBoundsException detail message.
     * Of the many possible refactorings of the error handling code,
     * this "outlining" performs best with both server and client VMs.
     */
    private String outOfBoundsMsg(int index) {
        return "Index: "+index+", Size: "+size;
    }

    // 访问越界判定
    private void checkElementIndex(int index) {
        if (!isElementIndex(index))
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    // 添加方法中访问越界判定。索引可以为最后一个元素值（超尾索引），也可以是0
    private void checkPositionIndex(int index) {
        if (!isPositionIndex(index))
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    /**
     * Returns the (non-null) Node at the specified element index.
     */
    // 获取|index|索引位置的链表节点
    Node<E> node(int index) {
        // assert isElementIndex(index);

        // 当|index|索引小于链表长度的一半时，正向遍历，否则从尾部逆向遍历
        if (index < (size >> 1)) {
            Node<E> x = first;
            for (int i = 0; i < index; i++)
                x = x.next;
            return x;
        } else {
            Node<E> x = last;
            for (int i = size - 1; i > index; i--)
                x = x.prev;
            return x;
        }
    }

    // Search Operations

    /**
     * Returns the index of the first occurrence of the specified element
     * in this list, or -1 if this list does not contain the element.
     * More formally, returns the lowest index {@code i} such that
     * <tt>(o==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;o.equals(get(i)))</tt>,
     * or -1 if there is no such index.
     *
     * @param o element to search for
     * @return the index of the first occurrence of the specified element in
     *         this list, or -1 if this list does not contain the element
     */
    // 正向查找对象|o|在列表中的索引值，查找失败返回-1
    public int indexOf(Object o) {
        int index = 0;
        if (o == null) {
            for (Node<E> x = first; x != null; x = x.next) {
                if (x.item == null)
                    return index;
                index++;
            }
        } else {
            for (Node<E> x = first; x != null; x = x.next) {
                if (o.equals(x.item))
                    return index;
                index++;
            }
        }
        return -1;
    }

    /**
     * Returns the index of the last occurrence of the specified element
     * in this list, or -1 if this list does not contain the element.
     * More formally, returns the highest index {@code i} such that
     * <tt>(o==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;o.equals(get(i)))</tt>,
     * or -1 if there is no such index.
     *
     * @param o element to search for
     * @return the index of the last occurrence of the specified element in
     *         this list, or -1 if this list does not contain the element
     */
    // 逆向查找对象|o|在列表中的索引值，查找失败返回-1
    public int lastIndexOf(Object o) {
        int index = size;
        if (o == null) {
            for (Node<E> x = last; x != null; x = x.prev) {
                index--;
                if (x.item == null)
                    return index;
            }
        } else {
            for (Node<E> x = last; x != null; x = x.prev) {
                index--;
                if (o.equals(x.item))
                    return index;
            }
        }
        return -1;
    }

    // Queue operations.

    /**
     * Retrieves, but does not remove, the head (first element) of this list.
     *
     * @return the head of this list, or {@code null} if this list is empty
     * @since 1.5
     */
    // 检索但不删除列表的头节点中的数据
    // 注：空链表，返回null
    public E peek() {
        final Node<E> f = first;
        return (f == null) ? null : f.item;
    }

    /**
     * Retrieves, but does not remove, the head (first element) of this list.
     *
     * @return the head of this list
     * @throws NoSuchElementException if this list is empty
     * @since 1.5
     */
    // 检索但不删除列表的头节点中的数据
    // 注：空链表，抛出异常
    public E element() {
        return getFirst();
    }

    /**
     * Retrieves and removes the head (first element) of this list.
     *
     * @return the head of this list, or {@code null} if this list is empty
     * @since 1.5
     */
    // 检索并删除列表的头节点中的数据
    // 注：空链表，返回null
    public E poll() {
        final Node<E> f = first;
        return (f == null) ? null : unlinkFirst(f);
    }

    /**
     * Retrieves and removes the head (first element) of this list.
     *
     * @return the head of this list
     * @throws NoSuchElementException if this list is empty
     * @since 1.5
     */
    // 检索并删除列表的头节点中的数据
    // 注：空链表，抛出异常
    public E remove() {
        return removeFirst();
    }

    /**
     * Adds the specified element as the tail (last element) of this list.
     *
     * @param e the element to add
     * @return {@code true} (as specified by {@link Queue#offer})
     * @since 1.5
     */
    // 将数据|e|插入链表尾部
    public boolean offer(E e) {
        return add(e);
    }

    // Deque operations
    /**
     * Inserts the specified element at the front of this list.
     *
     * @param e the element to insert
     * @return {@code true} (as specified by {@link Deque#offerFirst})
     * @since 1.6
     */
    // 将数据|e|插入链表头部
    public boolean offerFirst(E e) {
        addFirst(e);
        return true;
    }

    /**
     * Inserts the specified element at the end of this list.
     *
     * @param e the element to insert
     * @return {@code true} (as specified by {@link Deque#offerLast})
     * @since 1.6
     */
    // 将数据|e|插入链表尾部
    public boolean offerLast(E e) {
        addLast(e);
        return true;
    }

    /**
     * Retrieves, but does not remove, the first element of this list,
     * or returns {@code null} if this list is empty.
     *
     * @return the first element of this list, or {@code null}
     *         if this list is empty
     * @since 1.6
     */
    // 检索但不删除列表的头节点中的数据
    // 注：空链表，返回null
    public E peekFirst() {
        final Node<E> f = first;
        return (f == null) ? null : f.item;
     }

    /**
     * Retrieves, but does not remove, the last element of this list,
     * or returns {@code null} if this list is empty.
     *
     * @return the last element of this list, or {@code null}
     *         if this list is empty
     * @since 1.6
     */
    // 检索但不删除列表的尾节点中的数据
    // 注：空链表，返回null
    public E peekLast() {
        final Node<E> l = last;
        return (l == null) ? null : l.item;
    }

    /**
     * Retrieves and removes the first element of this list,
     * or returns {@code null} if this list is empty.
     *
     * @return the first element of this list, or {@code null} if
     *     this list is empty
     * @since 1.6
     */
    // 检索并删除列表的头节点中的数据
    // 注：空链表，返回null
    public E pollFirst() {
        final Node<E> f = first;
        return (f == null) ? null : unlinkFirst(f);
    }

    /**
     * Retrieves and removes the last element of this list,
     * or returns {@code null} if this list is empty.
     *
     * @return the last element of this list, or {@code null} if
     *     this list is empty
     * @since 1.6
     */
    // 检索并删除列表的尾节点中的数据
    // 注：空链表，返回null
    public E pollLast() {
        final Node<E> l = last;
        return (l == null) ? null : unlinkLast(l);
    }

    /**
     * Pushes an element onto the stack represented by this list.  In other
     * words, inserts the element at the front of this list.
     *
     * <p>This method is equivalent to {@link #addFirst}.
     *
     * @param e the element to push
     * @since 1.6
     */
    // 将数据|e|插入链表头部
    public void push(E e) {
        addFirst(e);
    }

    /**
     * Pops an element from the stack represented by this list.  In other
     * words, removes and returns the first element of this list.
     *
     * <p>This method is equivalent to {@link #removeFirst()}.
     *
     * @return the element at the front of this list (which is the top
     *         of the stack represented by this list)
     * @throws NoSuchElementException if this list is empty
     * @since 1.6
     */
    // 从链表中删除头节点，返回头节点中存放的数据
    // 注：链表无节点，立即抛出异常
    public E pop() {
        return removeFirst();
    }

    /**
     * Removes the first occurrence of the specified element in this
     * list (when traversing the list from head to tail).  If the list
     * does not contain the element, it is unchanged.
     *
     * @param o element to be removed from this list, if present
     * @return {@code true} if the list contained the specified element
     * @since 1.6
     */
    // 删除容器中第一个等于对象|o|的元素。元素不存在，返回false
    public boolean removeFirstOccurrence(Object o) {
        return remove(o);
    }

    /**
     * Removes the last occurrence of the specified element in this
     * list (when traversing the list from head to tail).  If the list
     * does not contain the element, it is unchanged.
     *
     * @param o element to be removed from this list, if present
     * @return {@code true} if the list contained the specified element
     * @since 1.6
     */
    // 删除容器中逆向的第一个等于对象|o|的元素。元素不存在，返回false
    public boolean removeLastOccurrence(Object o) {
        if (o == null) {
            for (Node<E> x = last; x != null; x = x.prev) {
                if (x.item == null) {
                    unlink(x);
                    return true;
                }
            }
        } else {
            for (Node<E> x = last; x != null; x = x.prev) {
                if (o.equals(x.item)) {
                    unlink(x);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns a list-iterator of the elements in this list (in proper
     * sequence), starting at the specified position in the list.
     * Obeys the general contract of {@code List.listIterator(int)}.<p>
     *
     * The list-iterator is <i>fail-fast</i>: if the list is structurally
     * modified at any time after the Iterator is created, in any way except
     * through the list-iterator's own {@code remove} or {@code add}
     * methods, the list-iterator will throw a
     * {@code ConcurrentModificationException}.  Thus, in the face of
     * concurrent modification, the iterator fails quickly and cleanly, rather
     * than risking arbitrary, non-deterministic behavior at an undetermined
     * time in the future.
     *
     * @param index index of the first element to be returned from the
     *              list-iterator (by a call to {@code next})
     * @return a ListIterator of the elements in this list (in proper
     *         sequence), starting at the specified position in the list
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @see List#listIterator(int)
     */
    // 获取列表迭代器。迭代起始位置为原始容器的|index|索引
    public ListIterator<E> listIterator(int index) {
        checkPositionIndex(index);
        return new ListItr(index);
    }

    // 列表迭代器。主要应用在与位置相关的容器上，比如 ArrayList, LinkedList, SubList
    // 1. 提供增、删、改、查接口，如 set(E e), add(E e)；不管概念还是实现上，这两个接口都与位置相关
    // 2. 相比Iterator接口，增加了前向迭代接口，如 hasPrevious(),previous(),previousIndex(),nextIndex()
    // 注：迭代器的工作是辅助具体的容器进行索引的自动化管理，它不持有实际的数据
    private class ListItr implements ListIterator<E> {
        // 当前迭代节点。即，调用remove()方法后，迭代器回退索引
        private Node<E> lastReturned;
        // 下一次迭代节点。即，调用next()方法返回的元素
        private Node<E> next;
        // 下一次迭代索引
        private int nextIndex;
        // 生成迭代器时，立即保存原始容器的修改次数。在使用迭代器过程中，原始容器不能再进行结构性修改
        // 注：modCount属性字段非常重要，可以有效的防止迭代器失效问题
        private int expectedModCount = modCount;

        ListItr(int index) {
            // assert isPositionIndex(index);
            next = (index == size) ? null : node(index);
            nextIndex = index;
        }

        public boolean hasNext() {
            return nextIndex < size;
        }

        public E next() {
            checkForComodification();
            // 迭代越界时，立即抛出异常。调用next()前，强烈建议先调用hasNext()
            if (!hasNext())
                throw new NoSuchElementException();

            // 设置上一次迭代节点，并返回本次迭代的元素
            lastReturned = next;
            // 设置下一次next()迭代节点
            next = next.next;
            nextIndex++;
            return lastReturned.item;
        }

        public boolean hasPrevious() {
            return nextIndex > 0;
        }

        public E previous() {
            checkForComodification();
            // 迭代越界时，立即抛出异常。调用previous()前，强烈建议先调用hasPrevious()
            if (!hasPrevious())
                throw new NoSuchElementException();

            lastReturned = next = (next == null) ? last : next.prev;
            nextIndex--;
            return lastReturned.item;
        }

        public int nextIndex() {
            return nextIndex;
        }

        public int previousIndex() {
            return nextIndex - 1;
        }

        public void remove() {
            checkForComodification();
            // 调用remove()前，需调用next()
            if (lastReturned == null)
                throw new IllegalStateException();

            Node<E> lastNext = lastReturned.next;
            unlink(lastReturned);
            if (next == lastReturned)
                next = lastNext;
            else
                nextIndex--;
            // 将上一次迭代索引置为无效
            lastReturned = null;
            // 自增迭代次数。原始容器的mc在unlink()方法中更新
            expectedModCount++;
        }

        public void set(E e) {
            if (lastReturned == null)
                throw new IllegalStateException();
            checkForComodification();
            lastReturned.item = e;
        }

        public void add(E e) {
            checkForComodification();
            // 将上一次迭代索引置为无效
            lastReturned = null;
            if (next == null)
                linkLast(e);
            else
                linkBefore(e, next);
            nextIndex++;
            // 自增迭代次数。原始容器的mc在linkLast()/linkBefore()方法中更新
            expectedModCount++;
        }

        // 从当前迭代索引，批量遍历消费剩余的元素
        public void forEachRemaining(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            // 从当前迭代位置，遍历消费容器剩余元素。注：在遍历过程中，原始容器不能再进行结构性修改
            while (modCount == expectedModCount && nextIndex < size) {
                action.accept(next.item);
                lastReturned = next;
                next = next.next;
                nextIndex++;
            }
            // 在使用迭代器过程中，原始容器不能再进行结构性修改
            checkForComodification();
        }

        // 在使用迭代器过程中，原始容器不能再进行结构性修改
        final void checkForComodification() {
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
        }
    }

    // 链表节点。双向链表
    private static class Node<E> {
        // 节点中存放的实际元素的指针
        E item;
        // 后驱节点指针
        Node<E> next;
        // 前驱节点指针
        Node<E> prev;

        Node(Node<E> prev, E element, Node<E> next) {
            this.item = element;
            this.next = next;
            this.prev = prev;
        }
    }

    /**
     * @since 1.6
     */
    // 获取降序迭代器。即，向前迭代遍历
    public Iterator<E> descendingIterator() {
        return new DescendingIterator();
    }

    /**
     * Adapter to provide descending iterators via ListItr.previous
     */
    // 降序迭代器，即，向前迭代遍历。主要应用在与位置相关的容器上，比如 ArrayList, LinkedList, SubList
    private class DescendingIterator implements Iterator<E> {
        private final ListItr itr = new ListItr(size());
        public boolean hasNext() {
            return itr.hasPrevious();
        }
        public E next() {
            return itr.previous();
        }
        public void remove() {
            itr.remove();
        }
    }

    @SuppressWarnings("unchecked")
    private LinkedList<E> superClone() {
        try {
            return (LinkedList<E>) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    /**
     * Returns a shallow copy of this {@code LinkedList}. (The elements
     * themselves are not cloned.)
     *
     * @return a shallow copy of this {@code LinkedList} instance
     */
    public Object clone() {
        LinkedList<E> clone = superClone();

        // Put clone into "virgin" state
        clone.first = clone.last = null;
        clone.size = 0;
        clone.modCount = 0;

        // Initialize clone with our elements
        for (Node<E> x = first; x != null; x = x.next)
            clone.add(x.item);

        return clone;
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
     * @return an array containing all of the elements in this list
     *         in proper sequence
     */
    // 将列表容器中的数据转换成数组返回，这个方法返回的是Object[]的数组类型
    public Object[] toArray() {
        Object[] result = new Object[size];
        int i = 0;
        for (Node<E> x = first; x != null; x = x.next)
            result[i++] = x.item;
        return result;
    }

    /**
     * Returns an array containing all of the elements in this list in
     * proper sequence (from first to last element); the runtime type of
     * the returned array is that of the specified array.  If the list fits
     * in the specified array, it is returned therein.  Otherwise, a new
     * array is allocated with the runtime type of the specified array and
     * the size of this list.
     *
     * <p>If the list fits in the specified array with room to spare (i.e.,
     * the array has more elements than the list), the element in the array
     * immediately following the end of the list is set to {@code null}.
     * (This is useful in determining the length of the list <i>only</i> if
     * the caller knows that the list does not contain any null elements.)
     *
     * <p>Like the {@link #toArray()} method, this method acts as bridge between
     * array-based and collection-based APIs.  Further, this method allows
     * precise control over the runtime type of the output array, and may,
     * under certain circumstances, be used to save allocation costs.
     *
     * <p>Suppose {@code x} is a list known to contain only strings.
     * The following code can be used to dump the list into a newly
     * allocated array of {@code String}:
     *
     * <pre>
     *     String[] y = x.toArray(new String[0]);</pre>
     *
     * Note that {@code toArray(new Object[0])} is identical in function to
     * {@code toArray()}.
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
            a = (T[])java.lang.reflect.Array.newInstance(
                                a.getClass().getComponentType(), size);
        int i = 0;
        Object[] result = a;
        for (Node<E> x = first; x != null; x = x.next)
            result[i++] = x.item;

        // 将多余的数组元素置为null
        if (a.length > size)
            a[size] = null;

        return a;
    }

    private static final long serialVersionUID = 876323262645176354L;

    /**
     * Saves the state of this {@code LinkedList} instance to a stream
     * (that is, serializes it).
     *
     * @serialData The size of the list (the number of elements it
     *             contains) is emitted (int), followed by all of its
     *             elements (each an Object) in the proper order.
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {
        // Write out any hidden serialization magic
        s.defaultWriteObject();

        // Write out size
        s.writeInt(size);

        // Write out all elements in the proper order.
        for (Node<E> x = first; x != null; x = x.next)
            s.writeObject(x.item);
    }

    /**
     * Reconstitutes this {@code LinkedList} instance from a stream
     * (that is, deserializes it).
     */
    @SuppressWarnings("unchecked")
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        // Read in any hidden serialization magic
        s.defaultReadObject();

        // Read in size
        int size = s.readInt();

        // Read in all elements in the proper order.
        for (int i = 0; i < size; i++)
            linkLast((E)s.readObject());
    }

    /**
     * Creates a <em><a href="Spliterator.html#binding">late-binding</a></em>
     * and <em>fail-fast</em> {@link Spliterator} over the elements in this
     * list.
     *
     * <p>The {@code Spliterator} reports {@link Spliterator#SIZED} and
     * {@link Spliterator#ORDERED}.  Overriding implementations should document
     * the reporting of additional characteristic values.
     *
     * @implNote
     * The {@code Spliterator} additionally reports {@link Spliterator#SUBSIZED}
     * and implements {@code trySplit} to permit limited parallelism..
     *
     * @return a {@code Spliterator} over the elements in this list
     * @since 1.8
     */
    // 获取LinkedList容器的分隔器对象实例。用于Stream流中
    @Override
    public Spliterator<E> spliterator() {
        return new LLSpliterator<E>(this, -1, 0);
    }

    /** A customized variant of Spliterators.IteratorSpliterator */
    // 算法核心是对容器按指数级数量进行切割。场景：将大数据反复“裂变”成一系列的小数据。多应用在stream流处理中
    // 注：切割容器时，切割数量从|1<<10|开始，每切割一次，数量增加一倍（即，切割得到的ArraySpliterator中元素增加一倍），最大切割的数量为|1<<25|
    // 注：分隔器是一种特殊的迭代器
    static final class LLSpliterator<E> implements Spliterator<E> {
        static final int BATCH_UNIT = 1 << 10;  // batch array size increment
        static final int MAX_BATCH = 1 << 25;  // max batch array size;
        // 原始容器的引用
        final LinkedList<E> list; // null OK unless traversed
        // 分隔器的起始节点的指针
        Node<E> current;      // current node; null until initialized
        // 分隔器的中含有元素个数
        int est;              // size estimate; -1 until first needed
        // 第一次分隔、消费分隔器时，立即保存原始容器的修改次数。在使用分隔器过程中，原始容器不能再进行结构性修改
        // 注：modCount属性字段非常重要，可以有效的防止分隔器非法访问的问题
        int expectedModCount; // initialized when est set
        // 分隔容器是切割数量
        int batch;            // batch size for splits

        LLSpliterator(LinkedList<E> list, int est, int expectedModCount) {
            this.list = list;
            this.est = est;
            this.expectedModCount = expectedModCount;
        }

        // 获取分隔器的中含有元素个数。原始分隔器首次调用 getEst() 时 est==-1，将其设置为原始容器的长度
        // 注：仅区分原始分隔器，是因为trySplit()返回了Spliterators.ArraySpliterator分隔器，其再次分隔是不会调用getEst()方法
        final int getEst() {
            int s; // force initialization
            final LinkedList<E> lst;
            if ((s = est) < 0) {
                if ((lst = list) == null)
                    s = est = 0;
                else {
                    // 第一次使用分隔器时，立即保存原始容器的修改次数。在使用分隔器过程中，原始容器不能再进行结构性修改
                    // 注：modCount属性字段非常重要，可以有效的防止分隔器非法访问的问题
                    expectedModCount = lst.modCount;
                    current = lst.first;
                    s = est = lst.size;
                }
            }
            return s;
        }

        // 评估剩余元素数量的大小
        public long estimateSize() { return (long) getEst(); }

        // 按指数级数量进行切割分隔器，返回的新分隔器包含元素按指数级增长；而原始分隔器按指数级数量向后缩短
        // 即：切分后，原始的分隔器引用后面数据(LinkedList.LLSpliterator)；返回的新分隔器引用后面的数据(Spliterators.ArraySpliterator)
        public Spliterator<E> trySplit() {
            Node<E> p;
            // 分隔器中剩余的元素个数
            int s = getEst();
            if (s > 1 && (p = current) != null) {
                // 切割数量，指数级增长
                int n = batch + BATCH_UNIT;
                if (n > s)
                    n = s;
                if (n > MAX_BATCH)
                    n = MAX_BATCH;
                // 存放本次分隔数据至数组容器中，分隔的前面部分
                Object[] a = new Object[n];
                int j = 0;
                do { a[j++] = p.item; } while ((p = p.next) != null && j < n);
                // 设置当前分隔器为新的起始点
                current = p;
                // 下一次分隔数量的增量，指数级增长算法
                batch = j;
                est = s - j;
                // 返回数组分隔迭代器Spliterators.ArraySpliterator对象实例
                // 注：ORDERED表示该子分隔器的迭代顺序是按照原本容器中的顺序
                return Spliterators.spliterator(a, 0, j, Spliterator.ORDERED);
            }
            return null;
        }

        // 消费分隔器中剩余元素，执行指定方法
        public void forEachRemaining(Consumer<? super E> action) {
            Node<E> p; int n;
            if (action == null) throw new NullPointerException();
            if ((n = getEst()) > 0 && (p = current) != null) {
                // 消费分隔器中剩余元素（|current=null|代表消费剩余所有元素）
                current = null;
                est = 0;
                // 遍历分隔器中剩余元素，执行指定方法
                do {
                    E e = p.item;
                    p = p.next;
                    action.accept(e);
                } while (p != null && --n > 0);
            }
            // 在使用分隔器过程中，原始容器不能再进行结构性修改
            if (list.modCount != expectedModCount)
                throw new ConcurrentModificationException();
        }

        // 消费分隔器中首个元素，执行指定方法
        public boolean tryAdvance(Consumer<? super E> action) {
            Node<E> p;
            if (action == null) throw new NullPointerException();
            if (getEst() > 0 && (p = current) != null) {
                --est;
                E e = p.item;
                // 设置下一次tryAdvance()的元素指针。即，当前元素被消费
                current = p.next;
                action.accept(e);
                // 在使用分隔器过程中，原始容器不能再进行结构性修改
                if (list.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                return true;
            }
            return false;
        }

        // 分隔器特征
        public int characteristics() {
            // ORDERED表示该分隔器的迭代顺序是按照原本容器中的顺序
            // SIZED表示该分隔器的大小是有限的
            // SUBSIZED表示该分隔器所分割得到的子分隔器也是有限的
            // 注：因为原始分隔器（父分隔器）是基于LinkedList的有序列表容器，故以上三个特征容易推出
            return Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED;
        }
    }

}
