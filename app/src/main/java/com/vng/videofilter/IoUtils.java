package com.vng.videofilter;

import java.io.Closeable;

/**
 * Copyright (C) 2017, VNG Corporation.
 *
 * @author thuannv
 * @since 11/08/2017
 */
public final class IoUtils {

    private IoUtils() {
        throw new IllegalStateException("Cannot instantiate object of utility class");
    }

    public static void safeClose(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                // ignored
            }
        }
    }
}