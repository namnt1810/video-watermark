package com.vng.videofilter.codec;

/**
 * Created by taitt on 15/02/2017.
 */

public abstract class ZQuitJoinThread extends Thread {

    private boolean isRunning = true;

    public ZQuitJoinThread(String name) {
        super(name);
        isRunning = true;
    }

    public void quitThenJoin() {
        isRunning = false;
        this.interrupt();
        try {
            join();
        } catch (InterruptedException ignored) {
            ignored.printStackTrace();
        }
    }

    public abstract void run0() throws Exception;

    public void begin() {
    }

    public void end() {
    }

    @Override
    public final void run() {
        begin();
        while (isRunning) {
            try {
                run0();
            } catch (Exception ignore) {
            }
        }
        end();
    }
}