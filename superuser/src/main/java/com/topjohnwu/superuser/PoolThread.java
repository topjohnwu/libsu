package com.topjohnwu.superuser;

import android.os.AsyncTask;

/**
 * Created by topjohnwu on 2018/1/22.
 */

public abstract class PoolThread {

    private static final int NOT_STARTED = 0;
    private static final int RUNNING = 1;
    private static final int DONE = 2;

    private int status;

    PoolThread() {
        status = NOT_STARTED;
    }

    abstract void run();

    PoolThread start() {
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                notifyRunning();
                PoolThread.this.run();
                notifyDone();
            }
        });
        return this;
    }

    synchronized void waitTillStart() {
        while (status == NOT_STARTED) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void join() throws InterruptedException {
        join(0, 0);
    }

    public void join(long millis) throws InterruptedException {
        join(millis, 0);
    }

    public synchronized void join(long millis, int nanos) throws InterruptedException {
        while (status != DONE)
            wait(millis, nanos);
    }

    private synchronized void notifyRunning() {
        status = RUNNING;
        notifyAll();
    }

    private synchronized void notifyDone() {
        status = DONE;
        notifyAll();
    }
}
