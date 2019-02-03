package com.topjohnwu.superuser.internal;

public final class WaitRunnable implements Runnable {

    private Runnable r;
    private boolean done = false;

    public WaitRunnable(Runnable run) {
        r = run;
    }

    public synchronized void waitUntilDone() {
        while (!done) {
            try {
                wait();
            } catch (InterruptedException ignored) {}
        }
    }

    @Override
    public synchronized void run() {
        r.run();
        done = true;
        notifyAll();
    }
}
