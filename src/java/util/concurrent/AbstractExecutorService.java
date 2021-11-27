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
import java.util.*;

/**
 * Provides default implementations of {@link ExecutorService}
 * execution methods. This class implements the {@code submit},
 * {@code invokeAny} and {@code invokeAll} methods using a
 * {@link RunnableFuture} returned by {@code newTaskFor}, which defaults
 * to the {@link FutureTask} class provided in this package.  For example,
 * the implementation of {@code submit(Runnable)} creates an
 * associated {@code RunnableFuture} that is executed and
 * returned. Subclasses may override the {@code newTaskFor} methods
 * to return {@code RunnableFuture} implementations other than
 * {@code FutureTask}.
 *
 * <p><b>Extension example</b>. Here is a sketch of a class
 * that customizes {@link ThreadPoolExecutor} to use
 * a {@code CustomTask} class instead of the default {@code FutureTask}:
 *  <pre> {@code
 * public class CustomThreadPoolExecutor extends ThreadPoolExecutor {
 *
 *   static class CustomTask<V> implements RunnableFuture<V> {...}
 *
 *   protected <V> RunnableFuture<V> newTaskFor(Callable<V> c) {
 *       return new CustomTask<V>(c);
 *   }
 *   protected <V> RunnableFuture<V> newTaskFor(Runnable r, V v) {
 *       return new CustomTask<V>(r, v);
 *   }
 *   // ... add constructors, etc.
 * }}</pre>
 *
 * @since 1.5
 * @author Doug Lea
 */
public abstract class AbstractExecutorService implements ExecutorService {

    /**
     * Returns a {@code RunnableFuture} for the given runnable and default
     * value.
     *
     * @param runnable the runnable task being wrapped
     * @param value the default value for the returned future
     * @param <T> the type of the given value
     * @return a {@code RunnableFuture} which, when run, will run the
     * underlying runnable and which, as a {@code Future}, will yield
     * the given value as its result and provide for cancellation of
     * the underlying task
     * @since 1.6
     */
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return new FutureTask<T>(runnable, value);
    }

    /**
     * Returns a {@code RunnableFuture} for the given callable task.
     *
     * @param callable the callable task being wrapped
     * @param <T> the type of the callable's result
     * @return a {@code RunnableFuture} which, when run, will call the
     * underlying callable and which, as a {@code Future}, will yield
     * the callable's result as its result and provide for
     * cancellation of the underlying task
     * @since 1.6
     */
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return new FutureTask<T>(callable);
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public Future<?> submit(Runnable task) {
        if (task == null) throw new NullPointerException();
        RunnableFuture<Void> ftask = newTaskFor(task, null);
        execute(ftask);
        return ftask;
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public <T> Future<T> submit(Runnable task, T result) {
        if (task == null) throw new NullPointerException();
        // 由|Runnable|和|result|新建一个|FutureTask|对象，并将其返回
        // 注：其中|Runnable|会被|FutureTask|重写的run()方法执行，然后直接将|result|广播给所有"结果等待者"
        // 这就意味着：任务的结果（|Runnable|没有返回值），必须由客户端（用户）在|task|中设置到|result|引用上
        RunnableFuture<T> ftask = newTaskFor(task, result);
        // 该task会自动交给线程池，立即执行
        execute(ftask);
        return ftask;
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public <T> Future<T> submit(Callable<T> task) {
        if (task == null) throw new NullPointerException();
        // 由|Callable|新建一个|FutureTask|对象，并将其返回
        // 注：其中|Callable|会被|FutureTask|重写的run()方法执行，其返回值广播给所有"结果等待者"
        RunnableFuture<T> ftask = newTaskFor(task);
        // 该task会自动交给线程池，立即执行
        execute(ftask);
        return ftask;
    }

    /**
     * the main mechanics of invokeAny.
     */
    private <T> T doInvokeAny(Collection<? extends Callable<T>> tasks,
                              boolean timed, long nanos)
        throws InterruptedException, ExecutionException, TimeoutException {
        if (tasks == null)
            throw new NullPointerException();
        int ntasks = tasks.size();
        if (ntasks == 0)
            throw new IllegalArgumentException();
        ArrayList<Future<T>> futures = new ArrayList<Future<T>>(ntasks);
        ExecutorCompletionService<T> ecs =
            new ExecutorCompletionService<T>(this);

        // For efficiency, especially in executors with limited
        // parallelism, check to see if previously submitted tasks are
        // done before submitting more of them. This interleaving
        // plus the exception mechanics account for messiness of main
        // loop.

        try {
            // Record exceptions so that if we fail to obtain any
            // result, we can throw the last exception we got.
            ExecutionException ee = null;
            final long deadline = timed ? System.nanoTime() + nanos : 0L;
            Iterator<? extends Callable<T>> it = tasks.iterator();

            // Start one task for sure; the rest incrementally
            // 立即将首个任务添加至执行器中
            futures.add(ecs.submit(it.next()));
            --ntasks;
            int active = 1; // 被提交到执行器中的任务的数量

            // 提交任务到执行器中执行，并尝试获取任一已完成任务的运行结果
            // 注：若有任务运行非常快，快过了任务的提交速度，将会出现有的任务不会被提交到执行器中
            for (;;) {
                // 阻塞的获取一个已完成的任务
                // 注：相比|take()|，|poll()|在没有检测到任何已完成的任务时，立即返回null；该方法也不会抛出检查异常
                Future<T> f = ecs.poll();
                if (f == null) {    // 没有检测到任何已完成的任务
                    if (ntasks > 0) {
                        // 在提交一个任务到执行器中去运行
                        --ntasks;
                        futures.add(ecs.submit(it.next()));
                        ++active;
                    }
                    else if (active == 0)   // 所有任务都运行完成，并且都抛出了异常，直接跳出循环
                        break;
                    else if (timed) {   // 设置了超时
                        // 任务已经全部提交，进入限时等待获取任一完成的任务
                        // 注：相比不带超时的|poll()|，此处在没有检测到任何已完成的任务时，会等待直到超时；该方法也可能会抛出中断异常
                        f = ecs.poll(nanos, TimeUnit.NANOSECONDS);
                        if (f == null)
                            throw new TimeoutException();   // 超时后，仍没有检测到有完成的任务
                        nanos = deadline - System.nanoTime();
                    }
                    else
                        // 阻塞获取任一完成的任务
                        // 注：相比|poll()|，|take()|会阻塞直到能获取到一个结果为止；该方法也可能会抛出中断异常
                        f = ecs.take();
                }
                if (f != null) {    // 获取到了一个已完成的任务
                    --active;
                    // 任务运行出现异常时，将其保存，再运行下一个任务，直到有一个能正常返回结果
                    try {
                        return f.get();
                    } catch (ExecutionException eex) {
                        ee = eex;
                    } catch (RuntimeException rex) {
                        ee = new ExecutionException(rex);
                    }
                }
            }

            // 所有任务都运行完成，并全部抛出了异常
            if (ee == null)
                ee = new ExecutionException();
            throw ee;

        } finally {
            // 当有任务已经完成，取消其余已提交的任务
            // 注：要想真正的实现：当任意一个任务完成时，其他任务不在执行，需要客户端（用户）在任务实现中去检查线程中断状态
            for (int i = 0, size = futures.size(); i < size; i++)
                futures.get(i).cancel(true);
        }
    }

    // 提交所有任务，当其中有任一任务完成了工作，立即返回，其他已提交的任务会被取消
    // 注：若有任务运行非常快，快过了任务的提交速度，将会出现有的任务不会被提交到执行器中
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException {
        try {
            return doInvokeAny(tasks, false, 0);
        } catch (TimeoutException cannotHappen) {
            assert false;
            return null;
        }
    }

    // 限时版的invokeAny
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                           long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        return doInvokeAny(tasks, true, unit.toNanos(timeout));
    }

    // 提交所有任务，等待所有任务执行完成后返回
    // 注：任务方法中抛出的异常将会被忽略；任务取消的异常也会被忽略
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
        throws InterruptedException {
        if (tasks == null)
            throw new NullPointerException();
        ArrayList<Future<T>> futures = new ArrayList<Future<T>>(tasks.size());
        boolean done = false;
        try {
            // 将所有任务添加到队列，并使用执行器立即执行
            for (Callable<T> t : tasks) {
                RunnableFuture<T> f = newTaskFor(t);
                futures.add(f);
                execute(f);
            }
            // 顺序遍历所有任务列表，获取执行结果
            // 注：异常将被忽略
            for (int i = 0, size = futures.size(); i < size; i++) {
                Future<T> f = futures.get(i);
                if (!f.isDone()) {
                    try {
                        f.get();
                    } catch (CancellationException ignore) {    // 忽略任务被取消的异常
                    } catch (ExecutionException ignore) {   // 忽略任务执行的异常
                    }
                }
            }
            done = true;
            return futures;
        } finally {
            // 执行中有失败，比如抛出了中断异常。取消所有任务，包括执行中的任务
            // 注：要想真正的实现：中断任务后，任务不在执行，需要客户端（用户）在任务实现中去检查线程中断状态
            if (!done)
                for (int i = 0, size = futures.size(); i < size; i++)
                    futures.get(i).cancel(true);
        }
    }

    // 限时版|invokeAll|，其中|unit.toNanos(timeout)|是每个任务执行的超时限制
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                         long timeout, TimeUnit unit)
        throws InterruptedException {
        if (tasks == null)
            throw new NullPointerException();
        long nanos = unit.toNanos(timeout);
        ArrayList<Future<T>> futures = new ArrayList<Future<T>>(tasks.size());
        boolean done = false;
        try {
            for (Callable<T> t : tasks)
                futures.add(newTaskFor(t));

            final long deadline = System.nanoTime() + nanos;
            final int size = futures.size();

            // Interleave time checks and calls to execute in case
            // executor doesn't have any/much parallelism.
            for (int i = 0; i < size; i++) {
                execute((Runnable)futures.get(i));
                nanos = deadline - System.nanoTime();
                if (nanos <= 0L)
                    return futures;
            }

            // 顺序遍历所有任务列表，带超时的获取执行结果
            // 注：参数|timeout|为每个任务的超时限制
            // 注：异常将被忽略
            for (int i = 0; i < size; i++) {
                Future<T> f = futures.get(i);
                if (!f.isDone()) {
                    if (nanos <= 0L)
                        return futures;
                    try {
                        // 限时等待一个任务结果。超时设置的是，每个任务执行的超时限制
                        f.get(nanos, TimeUnit.NANOSECONDS);
                    } catch (CancellationException ignore) {
                    } catch (ExecutionException ignore) {
                    } catch (TimeoutException toe) {
                        return futures;
                    }
                    nanos = deadline - System.nanoTime();
                }
            }
            done = true;
            return futures;
        } finally {
            // 执行中有失败，比如抛出了中断异常。取消所有任务，包括执行中的任务
            // 注：要想真正的实现：中断任务后，任务不在执行，需要客户端（用户）在任务实现中去检查线程中断状态
            if (!done)
                for (int i = 0, size = futures.size(); i < size; i++)
                    futures.get(i).cancel(true);
        }
    }

}
