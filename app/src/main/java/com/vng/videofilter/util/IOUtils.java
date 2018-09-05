package com.vng.videofilter.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Copyright (C) 2017, VNG Corporation.
 *
 * @author thuannv
 * @since 21/06/2017
 */

public final class IOUtils {

    private IOUtils() {}

    public static void copy(InputStream is, OutputStream os) throws IOException {
        int read;
        final int size = 4096;
        final byte[] buffer = new byte[size];
        while ((read = is.read(buffer, 0, size)) > 0) {
            os.write(buffer, 0, read);
        }
        os.flush();
    }

    public static void safelyClose(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                // ignored
            }
        }
    }
}