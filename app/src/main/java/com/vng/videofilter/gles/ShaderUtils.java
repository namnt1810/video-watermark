package com.vng.videofilter.gles;

import android.content.Context;

import com.vng.videofilter.util.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * @author thuannv
 * @since 21/06/2017
 */
public final class ShaderUtils {

    private ShaderUtils() {}

    public static String readRawResourceShaderFile(final Context context, final int resId) {
        InputStream inputStream = null;
        InputStreamReader inputStreamReader = null;
        BufferedReader bufferedReader = null;

        final StringBuilder body = new StringBuilder();
        String line;
        try {
            inputStream = context.getResources().openRawResource(resId);
            inputStreamReader = new InputStreamReader(inputStream);
            bufferedReader = new BufferedReader(inputStreamReader);
            while ((line = bufferedReader.readLine()) != null) {
                body.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            IOUtils.safelyClose(bufferedReader);
            IOUtils.safelyClose(inputStreamReader);
            IOUtils.safelyClose(inputStream);
        }
        return body.toString();
    }
}
