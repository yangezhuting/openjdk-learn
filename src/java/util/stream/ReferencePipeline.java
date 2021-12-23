/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package java.util.stream;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/**
 * Abstract base class for an intermediate pipeline stage or pipeline source
 * stage implementing whose elements are of type {@code U}.
 *
 * @param <P_IN> type of elements in the upstream source
 * @param <P_OUT> type of elements in produced by this stage
 *
 * @since 1.8
 */
// 管道流是流框架的核心之一：在流对象上进行的链式调用的每个表达式，最后都将被转换为管道流链表上
// 的一个节点，这主要用于收集用户对该流的所有操作方法的目的，将它们构建成一个管道流链表。等到用
// 户真正需要获取结果时（调用终结方法），遍历链表中的表达式进行计算获取。另外，在最后遍历计算时，
// 流框架真正依赖的是|Sink.Chained|算法链表
// 流框架的总体流程：首先，管道流链表顺序地记录用户操作的表达式，它是一个双向链表；然后，在遇到
// 终结方法时，流框架从管道流链表尾部节点向前遍历，直到起始管道流，构建一个|Sink.Chained|算法
// 链表，该算法链表的每个槽的数据、表达式都来源于管道流链表"同位置"的节点；最后，流框架再正向的
// 遍历算法链表执行真正的计算获取结果（注：管道流链表遍历是反向的，当遍历到起始管道流后，构建算
// 法链表也就完成了，若从该算法链表头节点向后遍历，表达式的计算顺序也将和流链式调用是一致的）
// 精华：流框架将表达式的收集与执行分开，不仅使结构清新，还可以使处理并行流时，仅将执行期并行化
// 注：流的泛型，始终和流的输出类型，也就是和流的产出数据的类型是一致的
// 注：管道流|ReferencePipeline|表示的是引用管道流，主要用于指向以对象类型为数据源的流
abstract class ReferencePipeline<P_IN, P_OUT>
        extends AbstractPipeline<P_IN, P_OUT, Stream<P_OUT>>
        implements Stream<P_OUT>  {

    /**
     * Constructor for the head of a stream pipeline.
     *
     * @param source {@code Supplier<Spliterator>} describing the stream source
     * @param sourceFlags the source flags for the stream source, described in
     *        {@link StreamOpFlag}
     * @param parallel {@code true} if the pipeline is parallel
     */
    ReferencePipeline(Supplier<? extends Spliterator<?>> source,
                      int sourceFlags, boolean parallel) {
        super(source, sourceFlags, parallel);
    }

    /**
     * Constructor for the head of a stream pipeline.
     *
     * @param source {@code Spliterator} describing the stream source
     * @param sourceFlags The source flags for the stream source, described in
     *        {@link StreamOpFlag}
     * @param parallel {@code true} if the pipeline is parallel
     */
    ReferencePipeline(Spliterator<?> source,
                      int sourceFlags, boolean parallel) {
        super(source, sourceFlags, parallel);
    }

    /**
     * Constructor for appending an intermediate operation onto an existing
     * pipeline.
     *
     * @param upstream the upstream element source.
     */
    ReferencePipeline(AbstractPipeline<?, P_IN, ?> upstream, int opFlags) {
        super(upstream, opFlags);
    }

    // Shape-specific methods

    @Override
    final StreamShape getOutputShape() {
        return StreamShape.REFERENCE;
    }

    @Override
    final <P_IN> Node<P_OUT> evaluateToNode(PipelineHelper<P_OUT> helper,
                                        Spliterator<P_IN> spliterator,
                                        boolean flattenTree,
                                        IntFunction<P_OUT[]> generator) {
        return Nodes.collect(helper, spliterator, flattenTree, generator);
    }

    @Override
    final <P_IN> Spliterator<P_OUT> wrap(PipelineHelper<P_OUT> ph,
                                     Supplier<Spliterator<P_IN>> supplier,
                                     boolean isParallel) {
        return new StreamSpliterators.WrappingSpliterator<>(ph, supplier, isParallel);
    }

    @Override
    final Spliterator<P_OUT> lazySpliterator(Supplier<? extends Spliterator<P_OUT>> supplier) {
        return new StreamSpliterators.DelegatingSpliterator<>(supplier);
    }

    @Override
    final void forEachWithCancel(Spliterator<P_OUT> spliterator, Sink<P_OUT> sink) {
        do { } while (!sink.cancellationRequested() && spliterator.tryAdvance(sink));
    }

    @Override
    final Node.Builder<P_OUT> makeNodeBuilder(long exactSizeIfKnown, IntFunction<P_OUT[]> generator) {
        return Nodes.builder(exactSizeIfKnown, generator);
    }


    // BaseStream

    @Override
    public final Iterator<P_OUT> iterator() {
        return Spliterators.iterator(spliterator());
    }


    // Stream

    // Stateless intermediate operations from Stream

    @Override
    public Stream<P_OUT> unordered() {
        if (!isOrdered())
            return this;
        return new StatelessOp<P_OUT, P_OUT>(this, StreamShape.REFERENCE, StreamOpFlag.NOT_ORDERED) {
            @Override
            Sink<P_OUT> opWrapSink(int flags, Sink<P_OUT> sink) {
                return sink;
            }
        };
    }

    // 过滤器。接收一个布尔计算表达式，遍历分割器中每个元素，找出符合条件的元素（表达式返回true）转移到新的集合中，并返回该集合的流
    // 注：过滤器方法的参数限定表示，其参数必须和流的泛型相同，或是它的超类
    @Override
    public final Stream<P_OUT> filter(Predicate<? super P_OUT> predicate) {
        Objects.requireNonNull(predicate);
        // 创建一个无状态的管道流，将其附加到管道流链表的当前节点的后驱节点。它的输入、输出类型与当前节点流泛型一致
        // 注：当前的管道流，与新创建管道流会被设置为管道流链表的相邻节点；新管道流是后驱节点
        // 注：|StreamShape.REFERENCE|参数用于：断言当前|this|一定是一个引用类型的管道流
        return new StatelessOp<P_OUT, P_OUT>(this, StreamShape.REFERENCE,
                                     StreamOpFlag.NOT_SIZED) {
            @Override
            Sink<P_OUT> opWrapSink(int flags, Sink<P_OUT> sink) {
                return new Sink.ChainedReference<P_OUT, P_OUT>(sink) {
                    @Override
                    public void begin(long size) {
                        downstream.begin(-1);
                    }

                    @Override
                    public void accept(P_OUT u) {
                        if (predicate.test(u))
                            downstream.accept(u);
                    }
                };
            }
        };
    }

    // 映射器。接收一个一元计算表达式，遍历分割器中每个元素，顺序映射成另一个元素（表达式返回结果）转移到新的集合中，并返回该集合的流
    // 注：映射器方法的参数限定表示，其参数必须和流的泛型相同，或是它的超类；其返回类型为新流的泛型，或是他的子类
    @Override
    @SuppressWarnings("unchecked")
    public final <R> Stream<R> map(Function<? super P_OUT, ? extends R> mapper) {
        Objects.requireNonNull(mapper);
        return new StatelessOp<P_OUT, R>(this, StreamShape.REFERENCE,
                                     StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT) {
            @Override
            Sink<P_OUT> opWrapSink(int flags, Sink<R> sink) {
                return new Sink.ChainedReference<P_OUT, R>(sink) {
                    @Override
                    public void accept(P_OUT u) {
                        downstream.accept(mapper.apply(u));
                    }
                };
            }
        };
    }

    @Override
    public final IntStream mapToInt(ToIntFunction<? super P_OUT> mapper) {
        Objects.requireNonNull(mapper);
        // 创建一个无状态的管道流，将其附加到管道流链表的当前节点的后驱节点。它的输入和当前节点流泛型一致，输出为整型
        // 注：当前的管道流，与新创建管道流会被设置为管道流链表的相邻节点；新管道流是后驱节点
        // 注：|StreamShape.REFERENCE|参数用于：断言当前|this|一定是一个引用类型的管道流
        return new IntPipeline.StatelessOp<P_OUT>(this, StreamShape.REFERENCE,
                                              StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT) {
            @Override
            Sink<P_OUT> opWrapSink(int flags, Sink<Integer> sink) {
                return new Sink.ChainedReference<P_OUT, Integer>(sink) {
                    @Override
                    public void accept(P_OUT u) {
                        downstream.accept(mapper.applyAsInt(u));
                    }
                };
            }
        };
    }

    @Override
    public final LongStream mapToLong(ToLongFunction<? super P_OUT> mapper) {
        Objects.requireNonNull(mapper);
        // 创建一个无状态的管道流，将其附加到管道流链表的当前节点的后驱节点。它的输入和当前节点流泛型一致，输出为长整型
        // 注：当前的管道流，与新创建管道流会被设置为管道流链表的相邻节点；新管道流是后驱节点
        // 注：|StreamShape.REFERENCE|参数用于：断言当前|this|一定是一个引用类型的管道流
        return new LongPipeline.StatelessOp<P_OUT>(this, StreamShape.REFERENCE,
                                      StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT) {
            @Override
            Sink<P_OUT> opWrapSink(int flags, Sink<Long> sink) {
                return new Sink.ChainedReference<P_OUT, Long>(sink) {
                    @Override
                    public void accept(P_OUT u) {
                        downstream.accept(mapper.applyAsLong(u));
                    }
                };
            }
        };
    }

    @Override
    public final DoubleStream mapToDouble(ToDoubleFunction<? super P_OUT> mapper) {
        Objects.requireNonNull(mapper);
        // 创建一个无状态的管道流，将其附加到管道流链表的当前节点的后驱节点。它的输入和当前节点流泛型一致，输出为双精度
        // 注：当前的管道流，与新创建管道流会被设置为管道流链表的相邻节点；新管道流是后驱节点
        // 注：|StreamShape.REFERENCE|参数用于：断言当前|this|一定是一个引用类型的管道流
        return new DoublePipeline.StatelessOp<P_OUT>(this, StreamShape.REFERENCE,
                                        StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT) {
            @Override
            Sink<P_OUT> opWrapSink(int flags, Sink<Double> sink) {
                return new Sink.ChainedReference<P_OUT, Double>(sink) {
                    @Override
                    public void accept(P_OUT u) {
                        downstream.accept(mapper.applyAsDouble(u));
                    }
                };
            }
        };
    }

    // 平铺映射器。接收一个一元计算表达式，遍历分割器中每个元素，顺序映射成一个流（需在|mapper|表达式
    // 中，将元素转换成流），返回的流类型就是表达式|mapper|的返回
    // 注1：通常用于将一个泛型为列表、数组的原始流转换成一个一维的流，该新流的泛型为原始流的列表的元素类
    // 型。如同将一个拥有二维元素的流平铺、展开成一维元素的流一样！
    // 注2：底层使用双重循环实现了将二维元素当作一维处理。即，|flatMap()|对应的算法链节点，会把表达式
    // |mapper|的返回值当作一个数据源，遍历该数据源，执行原始流的后续的链式表达式（|flatMap()|之后的
    // 表达式）。效果就是，原始流中元素在被遍历的同时，|mapper|的返回流中的元素再次被遍历
    // 注3：平铺映射器方法的参数限定表示，其参数必须和流的泛型相同，或是它的超类；其返回类型为流
    @Override
    public final <R> Stream<R> flatMap(Function<? super P_OUT, ? extends Stream<? extends R>> mapper) {
        Objects.requireNonNull(mapper);
        // We can do better than this, by polling cancellationRequested when stream is infinite
        return new StatelessOp<P_OUT, R>(this, StreamShape.REFERENCE,
                                     StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT | StreamOpFlag.NOT_SIZED) {
            @Override
            Sink<P_OUT> opWrapSink(int flags, Sink<R> sink) {
                return new Sink.ChainedReference<P_OUT, R>(sink) {
                    @Override
                    public void begin(long size) {
                        downstream.begin(-1);
                    }

                    @Override
                    public void accept(P_OUT u) {
                        // 把表达式|mapper|的返回值当作一个数据源，遍历该数据源，执行原始流的后续的链式表达式（|flatMap()|之后的表达式）
                        try (Stream<? extends R> result = mapper.apply(u)) {
                            // We can do better that this too; optimize for depth=0 case and just grab spliterator and forEach it
                            if (result != null)
                                result.sequential().forEach(downstream);
                        }
                    }
                };
            }
        };
    }

    @Override
    public final IntStream flatMapToInt(Function<? super P_OUT, ? extends IntStream> mapper) {
        Objects.requireNonNull(mapper);
        // We can do better than this, by polling cancellationRequested when stream is infinite
        return new IntPipeline.StatelessOp<P_OUT>(this, StreamShape.REFERENCE,
                                              StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT | StreamOpFlag.NOT_SIZED) {
            @Override
            Sink<P_OUT> opWrapSink(int flags, Sink<Integer> sink) {
                return new Sink.ChainedReference<P_OUT, Integer>(sink) {
                    IntConsumer downstreamAsInt = downstream::accept;
                    @Override
                    public void begin(long size) {
                        downstream.begin(-1);
                    }

                    @Override
                    public void accept(P_OUT u) {
                        try (IntStream result = mapper.apply(u)) {
                            // We can do better that this too; optimize for depth=0 case and just grab spliterator and forEach it
                            if (result != null)
                                result.sequential().forEach(downstreamAsInt);
                        }
                    }
                };
            }
        };
    }

    @Override
    public final DoubleStream flatMapToDouble(Function<? super P_OUT, ? extends DoubleStream> mapper) {
        Objects.requireNonNull(mapper);
        // We can do better than this, by polling cancellationRequested when stream is infinite
        return new DoublePipeline.StatelessOp<P_OUT>(this, StreamShape.REFERENCE,
                                                     StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT | StreamOpFlag.NOT_SIZED) {
            @Override
            Sink<P_OUT> opWrapSink(int flags, Sink<Double> sink) {
                return new Sink.ChainedReference<P_OUT, Double>(sink) {
                    DoubleConsumer downstreamAsDouble = downstream::accept;
                    @Override
                    public void begin(long size) {
                        downstream.begin(-1);
                    }

                    @Override
                    public void accept(P_OUT u) {
                        try (DoubleStream result = mapper.apply(u)) {
                            // We can do better that this too; optimize for depth=0 case and just grab spliterator and forEach it
                            if (result != null)
                                result.sequential().forEach(downstreamAsDouble);
                        }
                    }
                };
            }
        };
    }

    @Override
    public final LongStream flatMapToLong(Function<? super P_OUT, ? extends LongStream> mapper) {
        Objects.requireNonNull(mapper);
        // We can do better than this, by polling cancellationRequested when stream is infinite
        return new LongPipeline.StatelessOp<P_OUT>(this, StreamShape.REFERENCE,
                                                   StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT | StreamOpFlag.NOT_SIZED) {
            @Override
            Sink<P_OUT> opWrapSink(int flags, Sink<Long> sink) {
                return new Sink.ChainedReference<P_OUT, Long>(sink) {
                    LongConsumer downstreamAsLong = downstream::accept;
                    @Override
                    public void begin(long size) {
                        downstream.begin(-1);
                    }

                    @Override
                    public void accept(P_OUT u) {
                        try (LongStream result = mapper.apply(u)) {
                            // We can do better that this too; optimize for depth=0 case and just grab spliterator and forEach it
                            if (result != null)
                                result.sequential().forEach(downstreamAsLong);
                        }
                    }
                };
            }
        };
    }

    @Override
    public final Stream<P_OUT> peek(Consumer<? super P_OUT> action) {
        Objects.requireNonNull(action);
        return new StatelessOp<P_OUT, P_OUT>(this, StreamShape.REFERENCE,
                                     0) {
            @Override
            Sink<P_OUT> opWrapSink(int flags, Sink<P_OUT> sink) {
                return new Sink.ChainedReference<P_OUT, P_OUT>(sink) {
                    @Override
                    public void accept(P_OUT u) {
                        action.accept(u);
                        downstream.accept(u);
                    }
                };
            }
        };
    }

    // Stateful intermediate operations from Stream

    // 返回原始流中元素去重后组成的流
    @Override
    public final Stream<P_OUT> distinct() {
        return DistinctOps.makeRef(this);
    }

    // 返回原始流中元素排序后组成的流
    @Override
    public final Stream<P_OUT> sorted() {
        return SortedOps.makeRef(this);
    }

    @Override
    public final Stream<P_OUT> sorted(Comparator<? super P_OUT> comparator) {
        return SortedOps.makeRef(this, comparator);
    }

    @Override
    public final Stream<P_OUT> limit(long maxSize) {
        if (maxSize < 0)
            throw new IllegalArgumentException(Long.toString(maxSize));
        return SliceOps.makeRef(this, 0, maxSize);
    }

    @Override
    public final Stream<P_OUT> skip(long n) {
        if (n < 0)
            throw new IllegalArgumentException(Long.toString(n));
        if (n == 0)
            return this;
        else
            return SliceOps.makeRef(this, n, -1);
    }

    // Terminal operations from Stream

    @Override
    public void forEach(Consumer<? super P_OUT> action) {
        evaluate(ForEachOps.makeRef(action, false));
    }

    @Override
    public void forEachOrdered(Consumer<? super P_OUT> action) {
        evaluate(ForEachOps.makeRef(action, true));
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <A> A[] toArray(IntFunction<A[]> generator) {
        // Since A has no relation to U (not possible to declare that A is an upper bound of U)
        // there will be no static type checking.
        // Therefore use a raw type and assume A == U rather than propagating the separation of A and U
        // throughout the code-base.
        // The runtime type of U is never checked for equality with the component type of the runtime type of A[].
        // Runtime checking will be performed when an element is stored in A[], thus if A is not a
        // super type of U an ArrayStoreException will be thrown.
        @SuppressWarnings("rawtypes")
        IntFunction rawGenerator = (IntFunction) generator;
        return (A[]) Nodes.flatten(evaluateToArrayNode(rawGenerator), rawGenerator)
                              .asArray(rawGenerator);
    }

    @Override
    public final Object[] toArray() {
        return toArray(Object[]::new);
    }

    // 匹配任意元素。即，只要有一个条件满足即返回true
    // 注：只要有一个匹配成功，立即返回true，短路策略
    @Override
    public final boolean anyMatch(Predicate<? super P_OUT> predicate) {
        return evaluate(MatchOps.makeRef(predicate, MatchOps.MatchKind.ANY));
    }

    // 匹配所有元素。即，必须全部都满足才会返回true
    // 注：只要有一个匹配不成功，立即返回false，短路策略
    @Override
    public final boolean allMatch(Predicate<? super P_OUT> predicate) {
        return evaluate(MatchOps.makeRef(predicate, MatchOps.MatchKind.ALL));
    }

    // 不匹配所有元素。即，全都不满足才会返回true
    // 注：只要有一个匹配成功，立即返回false，短路策略
    @Override
    public final boolean noneMatch(Predicate<? super P_OUT> predicate) {
        return evaluate(MatchOps.makeRef(predicate, MatchOps.MatchKind.NONE));
    }

    // 返回流中的第一个元素。短路策略
    @Override
    public final Optional<P_OUT> findFirst() {
        return evaluate(FindOps.makeRef(true));
    }

    // 返回流中的随机的一个元素。短路策略
    @Override
    public final Optional<P_OUT> findAny() {
        return evaluate(FindOps.makeRef(false));
    }

    @Override
    public final P_OUT reduce(final P_OUT identity, final BinaryOperator<P_OUT> accumulator) {
        return evaluate(ReduceOps.makeRef(identity, accumulator, accumulator));
    }

    @Override
    public final Optional<P_OUT> reduce(BinaryOperator<P_OUT> accumulator) {
        return evaluate(ReduceOps.makeRef(accumulator));
    }

    @Override
    public final <R> R reduce(R identity, BiFunction<R, ? super P_OUT, R> accumulator, BinaryOperator<R> combiner) {
        return evaluate(ReduceOps.makeRef(identity, accumulator, combiner));
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <R, A> R collect(Collector<? super P_OUT, A, R> collector) {
        A container;
        if (isParallel()
                && (collector.characteristics().contains(Collector.Characteristics.CONCURRENT))
                && (!isOrdered() || collector.characteristics().contains(Collector.Characteristics.UNORDERED))) {
            container = collector.supplier().get();
            BiConsumer<A, ? super P_OUT> accumulator = collector.accumulator();
            forEach(u -> accumulator.accept(container, u));
        }
        else {
            container = evaluate(ReduceOps.makeRef(collector));
        }
        return collector.characteristics().contains(Collector.Characteristics.IDENTITY_FINISH)
               ? (R) container
               : collector.finisher().apply(container);
    }

    @Override
    public final <R> R collect(Supplier<R> supplier,
                               BiConsumer<R, ? super P_OUT> accumulator,
                               BiConsumer<R, R> combiner) {
        return evaluate(ReduceOps.makeRef(supplier, accumulator, combiner));
    }

    @Override
    public final Optional<P_OUT> max(Comparator<? super P_OUT> comparator) {
        return reduce(BinaryOperator.maxBy(comparator));
    }

    @Override
    public final Optional<P_OUT> min(Comparator<? super P_OUT> comparator) {
        return reduce(BinaryOperator.minBy(comparator));

    }

    // 统计流中元素个数
    // 算法大意是：将流中的每个元素都映射成1，然后将他们求和，即可统计出元素的个数
    @Override
    public final long count() {
        // 方法|mapToLong()|返回了长整型的流|LongStream|，其输出类型被限定为|long|。即，流
        // 被转化成了长整型流
        return mapToLong(e -> 1L).sum();
    }


    //

    /**
     * Source stage of a ReferencePipeline.
     *
     * @param <E_IN> type of elements in the upstream source
     * @param <E_OUT> type of elements in produced by this stage
     * @since 1.8
     */
    // 起始的管道流。它是管道流链表的第一个节点，主要用于指向以对象类型为数据源的流
    // 注：流框架依赖递归算法，|Head|为管道流链表的起始节点，标识算法终止节点
    static class Head<E_IN, E_OUT> extends ReferencePipeline<E_IN, E_OUT> {
        /**
         * Constructor for the source stage of a Stream.
         *
         * @param source {@code Supplier<Spliterator>} describing the stream
         *               source
         * @param sourceFlags the source flags for the stream source, described
         *                    in {@link StreamOpFlag}
         */
        Head(Supplier<? extends Spliterator<?>> source,
             int sourceFlags, boolean parallel) {
            super(source, sourceFlags, parallel);
        }

        /**
         * Constructor for the source stage of a Stream.
         *
         * @param source {@code Spliterator} describing the stream source
         * @param sourceFlags the source flags for the stream source, described
         *                    in {@link StreamOpFlag}
         */
        // 创建一个起始的管道流。该流的泛型，也就是它第二个泛型参数|E_OUT|为分割器中元素类型（由
        // 外部创建时指定）；由于，当前类是一个起始管道流，它只负责将自己的输出，与后续的管道流的
        // 输入进行连接即可。因而，对于分割器参数的泛型并不关心
        // 注：尽管实际看来，起始管道流是负责将源分割器中元素输出给后一个管道流的，在多数场景下，输
        // 入和输出类型基本相同！不过，此处通配符解决：流的泛型可能只想"限定到"分割器元素的超类。即，
        // 分割器将元素对象赋值到下一个管道流时，可仅提供超类的泛型限定。 谨记：泛型不存在继承关系
        Head(Spliterator<?> source,
             int sourceFlags, boolean parallel) {
            super(source, sourceFlags, parallel);
        }

        @Override
        final boolean opIsStateful() {
            throw new UnsupportedOperationException();
        }

        @Override
        final Sink<E_IN> opWrapSink(int flags, Sink<E_OUT> sink) {
            throw new UnsupportedOperationException();
        }

        // Optimized sequential terminal operations for the head of the pipeline

        @Override
        public void forEach(Consumer<? super E_OUT> action) {
            if (!isParallel()) {
                sourceStageSpliterator().forEachRemaining(action);
            }
            else {
                super.forEach(action);
            }
        }

        @Override
        public void forEachOrdered(Consumer<? super E_OUT> action) {
            if (!isParallel()) {
                sourceStageSpliterator().forEachRemaining(action);
            }
            else {
                super.forEachOrdered(action);
            }
        }
    }

    /**
     * Base class for a stateless intermediate stage of a Stream.
     *
     * @param <E_IN> type of elements in the upstream source
     * @param <E_OUT> type of elements in produced by this stage
     * @since 1.8
     */
    abstract static class StatelessOp<E_IN, E_OUT>
            extends ReferencePipeline<E_IN, E_OUT> {
        /**
         * Construct a new Stream by appending a stateless intermediate
         * operation to an existing stream.
         *
         * @param upstream The upstream pipeline stage
         * @param inputShape The stream shape for the upstream pipeline stage
         * @param opFlags Operation flags for the new stage
         */
        // 创建一个无状态的管道流，将其附加到管道流链表的当前节点的后驱节点
        // 注：|upstream|参数限定表明：上一个管道流的输出类型（第二个类型参数），就是当前管道流的输入类型
        StatelessOp(AbstractPipeline<?, E_IN, ?> upstream,
                    StreamShape inputShape,
                    int opFlags) {
            super(upstream, opFlags);

            // 断言前一个管道流（输入流）的流的类型
            assert upstream.getOutputShape() == inputShape;
        }

        @Override
        final boolean opIsStateful() {
            return false;
        }
    }

    /**
     * Base class for a stateful intermediate stage of a Stream.
     *
     * @param <E_IN> type of elements in the upstream source
     * @param <E_OUT> type of elements in produced by this stage
     * @since 1.8
     */
    abstract static class StatefulOp<E_IN, E_OUT>
            extends ReferencePipeline<E_IN, E_OUT> {
        /**
         * Construct a new Stream by appending a stateful intermediate operation
         * to an existing stream.
         * @param upstream The upstream pipeline stage
         * @param inputShape The stream shape for the upstream pipeline stage
         * @param opFlags Operation flags for the new stage
         */
        StatefulOp(AbstractPipeline<?, E_IN, ?> upstream,
                   StreamShape inputShape,
                   int opFlags) {
            super(upstream, opFlags);
            assert upstream.getOutputShape() == inputShape;
        }

        @Override
        final boolean opIsStateful() {
            return true;
        }

        @Override
        abstract <P_IN> Node<E_OUT> opEvaluateParallel(PipelineHelper<E_OUT> helper,
                                                       Spliterator<P_IN> spliterator,
                                                       IntFunction<E_OUT[]> generator);
    }
}
