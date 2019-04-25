package com.vng.videofilter.util;

import android.media.MediaFormat;
import android.os.Build;

import static android.os.Build.VERSION.SDK_INT;

/**
 * Copyright (C) 2017, VNG Corporation.
 *
 * @author namnt4
 * @since 28/08/2018
 */

public final class Utils {
    public static boolean hasLollipop() {
        return SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    public static boolean isVideoFormat(MediaFormat format) {
        return getMimeTypeFor(format).startsWith("video/");
    }

    public static boolean isAudioFormat(MediaFormat format) {
        return getMimeTypeFor(format).startsWith("audio/");
    }

    public static String getMimeTypeFor(MediaFormat format) {
        return format.getString(MediaFormat.KEY_MIME);
    }
}
