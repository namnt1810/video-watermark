package com.vng.videofilter.util;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

/**
 * @author thuannv
 * @since 04/01/2018
 */
public final class DispatchQueue {

    private final Looper mLooper;

    private final Handler mHandler;

    public DispatchQueue(String threadName) {
        HandlerThread thread = new HandlerThread(threadName);
        thread.start();
        mLooper = thread.getLooper();
        mHandler = new Handler(mLooper);
    }

    public DispatchQueue(String threadName, int priority) {
        this(threadName, priority, null);
    }

    public DispatchQueue(String threadName, int priority, Handler.Callback callback) {
        HandlerThread thread = new HandlerThread(threadName, priority);
        thread.start();
        mLooper = thread.getLooper();
        if (callback == null) {
            mHandler = new Handler(mLooper);
        } else {
            mHandler = new Handler(mLooper, callback);
        }
    }

    public DispatchQueue(Looper looper) {
        mLooper = looper;
        mHandler = new Handler(looper);
    }

    public DispatchQueue(Looper looper, Handler.Callback callback) {
        mLooper = looper == null ? Looper.getMainLooper() : looper;
        if (callback == null) {
            mHandler = new Handler(looper);
        } else {
            mHandler = new Handler(looper, callback);
        }
    }

    public synchronized void dispatch(Runnable task) {
        dispatch(task, 0);
    }

    public synchronized void dispatch(Runnable task, long after) {
        if (isAlive() && task != null) {
            try {
                mHandler.postDelayed(task, after > 0 ? after : 0);
            } catch (Exception e) {
            }
        }
    }

    public synchronized void clear() {
        if (isAlive()) {
            try {
                mHandler.removeCallbacksAndMessages(null);
            } catch (Exception e) {
            }
        }
    }

    public synchronized void cancel(Runnable task) {
        if (isAlive() && task != null) {
            try {
                mHandler.removeCallbacks(task);
            } catch (Exception e) {
            }
        }
    }

    public synchronized void dispatch(Message message) {
        if (isAlive() && message != null) {
            mHandler.sendMessage(message);
        }
    }

    public synchronized void quit() {
        if (isAlive()) {
            try {
                mLooper.quit();
            } catch (Exception e) {
            }
        }
    }

    private synchronized boolean isAlive() {
        return mLooper.getThread().isAlive();
    }

    public Message obtain(int what, Object object) {
        return mHandler.obtainMessage(what, object);
    }

    public Message obtain(int what) {
        return mHandler.obtainMessage(what);
    }

    public Message obtain(int what, int arg1) {
        Message message = mHandler.obtainMessage(what);
        message.arg1 = arg1;
        return message;
    }

    public Message obtain(int what, int arg1, int arg2) {
        Message message = mHandler.obtainMessage(what);
        message.arg1 = arg1;
        message.arg2 = arg2;
        return message;
    }

    public Message obtain(int what, int arg1, int arg2, Object object) {
        Message message = mHandler.obtainMessage(what);
        message.arg1 = arg1;
        message.arg2 = arg2;
        message.obj = object;
        return message;
    }
}
