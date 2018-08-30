package com.vng.videofilter;

import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.Normalizer;
import java.util.Locale;

/**
 * @author thuannv
 * @since 18/08/2017
 */
public final class StringUtils {

    private StringUtils() {
        throw new UnsupportedOperationException();
    }

    public static String format(String fmt, Object... args) {
        return String.format(Locale.US, fmt, args);
    }

    public static String formatVideoDuration(int duration) {
        int seconds = duration / 1000;
        int numOfSecond = seconds % 60;
        int numOfMinute = seconds / 60;
        return format("%02d:%02d", numOfMinute, numOfSecond);
    }
}
