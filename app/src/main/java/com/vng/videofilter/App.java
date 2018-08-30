package com.vng.videofilter;

import android.app.Application;
import android.os.StrictMode;

/**
 * Copyright (C) 2017, VNG Corporation.
 *
 * @author namnt4
 * @since 22/08/2018
 */

public class App extends Application {

    private static App sInstance;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;

        StrictMode.enableDefaults();
    }

    public static App getInstance() {
        return sInstance;
    }
}
