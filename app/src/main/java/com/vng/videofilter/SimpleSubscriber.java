package com.vng.videofilter;

import rx.Subscriber;

/**
 * @author thuannv
 * @since 19/07/2017
 */
public class SimpleSubscriber<T> extends Subscriber<T> {

    @Override
    public void onCompleted() {
    }

    @Override
    public void onError(Throwable e) {
    }

    @Override
    public void onNext(T t) {
    }
}
