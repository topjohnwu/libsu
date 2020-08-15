/*
 * Copyright 2020 John "topjohnwu" Wu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.topjohnwu.superuser.internal;

import androidx.annotation.RestrictTo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.topjohnwu.superuser.Shell.EXECUTOR;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class SerialExecutorService extends AbstractExecutorService {

    private boolean isShutdown = false;
    private ArrayDeque<Runnable> mTasks = new ArrayDeque<>();
    private FutureTask<Void> scheduleTask = null;

    @Override
    public synchronized void execute(Runnable r) {
        if (isShutdown) {
            throw new RejectedExecutionException(
                    "Task " + r.toString() + " rejected from " + toString());
        }
        mTasks.offer(r);
        if (scheduleTask == null) {
            scheduleTask = new FutureTask<>(this::executeTasks);
            EXECUTOR.execute(scheduleTask);
        }
    }

    @Override
    public synchronized void shutdown() {
        isShutdown = true;
        mTasks.clear();
    }

    @Override
    public synchronized List<Runnable> shutdownNow() {
        isShutdown = true;
        if (scheduleTask != null)
            scheduleTask.cancel(true);
        try {
            return new ArrayList<>(mTasks);
        } finally {
            mTasks.clear();
        }
    }

    @Override
    public synchronized boolean isShutdown() {
        return isShutdown;
    }

    @Override
    public synchronized boolean isTerminated() {
        return isShutdown && scheduleTask == null;
    }

    @Override
    public synchronized boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        if (scheduleTask == null)
            return true;
        try {
            scheduleTask.get(timeout, unit);
        } catch (TimeoutException e) {
            return false;
        } catch (ExecutionException ignored) {}
        return true;
    }

    private Void executeTasks() {
        for (;;) {
            Runnable task;
            synchronized (this) {
                if ((task = mTasks.poll()) == null) {
                    scheduleTask = null;
                    return null;
                }
            }
            task.run();
        }
    }
}
