/*
 * Copyright 2019 John "topjohnwu" Wu
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

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.topjohnwu.superuser.Shell.EXECUTOR;

class SerialExecutorService extends AbstractExecutorService {

    private ArrayDeque<FutureTask> mTasks = new ArrayDeque<>();
    private FutureTask mActive;
    private boolean isShutdown = false;

    public synchronized void execute(Runnable r) {
        if (isShutdown) {
            throw new RejectedExecutionException("Task " + r.toString() +
                    " rejected from " + toString());
        }
        FutureTask next = new FutureTask<Void>(() -> {
            r.run();
            scheduleNext();
        }, null);
        if (mActive == null) {
            mActive = next;
            EXECUTOR.execute(next);
        } else {
            mTasks.offer(next);
        }
    }

    private synchronized void scheduleNext() {
        if ((mActive = mTasks.poll()) != null) {
            EXECUTOR.execute(mActive);
        }
    }

    @Override
    public synchronized void shutdown() {
        isShutdown = true;
    }

    @Override
    public synchronized List<Runnable> shutdownNow() {
        isShutdown = true;
        mActive.cancel(true);
        List<Runnable> ret = Arrays.asList(mTasks.toArray(new Runnable[]{}));
        mTasks.clear();
        return ret;
    }

    @Override
    public synchronized boolean isShutdown() {
        return isShutdown;
    }

    @Override
    public synchronized boolean isTerminated() {
        return isShutdown && mTasks.isEmpty();
    }

    @Override
    public synchronized boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        try {
            mActive.get(timeout, unit);
        } catch (TimeoutException e) {
            return false;
        } catch (ExecutionException ignored) {}
        return true;
    }
}
