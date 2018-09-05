/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vng.videofilter.gles;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.Matrix;
import android.util.Log;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_COLOR_ATTACHMENT0;
import static android.opengl.GLES20.GL_FRAMEBUFFER;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_NEAREST;
import static android.opengl.GLES20.GL_NO_ERROR;
import static android.opengl.GLES20.GL_RGBA;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.GL_TEXTURE_MAG_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_MIN_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_S;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_T;
import static android.opengl.GLES20.GL_UNSIGNED_BYTE;
import static android.opengl.GLES20.glBindFramebuffer;
import static android.opengl.GLES20.glBindTexture;
import static android.opengl.GLES20.glFramebufferTexture2D;
import static android.opengl.GLES20.glGenFramebuffers;
import static android.opengl.GLES20.glGenTextures;
import static android.opengl.GLES20.glGetError;
import static android.opengl.GLES20.glTexImage2D;
import static android.opengl.GLES20.glTexParameterf;

/**
 * Some OpenGL utility functions.
 */
public class GlUtil {
    public static final String TAG = "Grafika";

    /** Identity matrix for general use.  Don't modify or life will get weird. */
    public static final float[] IDENTITY_MATRIX;
    static {
        IDENTITY_MATRIX = new float[16];
        Matrix.setIdentityM(IDENTITY_MATRIX, 0);
    }

    private static final int SIZEOF_FLOAT = 4;


    private GlUtil() {}     // do not instantiate

    /**
     * Creates a new program from the supplied vertex and fragment shaders.
     *
     * @return A handle to the program, or 0 on failure.
     */
    public static int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) {
            return 0;
        }
        int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (pixelShader == 0) {
            return 0;
        }

        int program = GLES20.glCreateProgram();
        checkGlError("glCreateProgram");
        if (program == 0) {
            Log.e(TAG, "Could not create program");
        }
        GLES20.glAttachShader(program, vertexShader);
        checkGlError("glAttachShader");
        GLES20.glAttachShader(program, pixelShader);
        checkGlError("glAttachShader");
        GLES20.glLinkProgram(program);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link program: ");
            Log.e(TAG, GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            program = 0;
        }
        return program;
    }

    /**
     * Compiles the provided shader source.
     *
     * @return A handle to the shader, or 0 on failure.
     */
    public static int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        checkGlError("glCreateShader type=" + shaderType);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "Could not compile shader " + shaderType + ":");
            Log.e(TAG, " " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }
        return shader;
    }

    /**
     * Checks to see if a GLES error has been raised.
     */
    public static void checkGlError(String op) {
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            String msg = op + ": glError 0x" + Integer.toHexString(error);
            Log.e(TAG, msg);
            throw new RuntimeException(msg);
        }
    }

    /**
     * Checks to see if the location we obtained is valid.  GLES returns -1 if a label
     * could not be found, but does not set the GL error.
     * <p>
     * Throws a RuntimeException if the location is invalid.
     */
    public static void checkLocation(int location, String label) {
        if (location < 0) {
            throw new RuntimeException("Unable to locate '" + label + "' in program");
        }
    }

    /**
     * Creates a texture from raw data.
     *
     * @param data Image data, in a "direct" ByteBuffer.
     * @param width Texture width, in pixels (not bytes).
     * @param height Texture height, in pixels.
     * @param format Image data format (use constant appropriate for glTexImage2D(), e.g. GL_RGBA).
     * @return Handle to texture.
     */
    public static int createImageTexture(ByteBuffer data, int width, int height, int format) {
        int[] textureHandles = new int[1];
        int textureHandle;

        GLES20.glGenTextures(1, textureHandles, 0);
        textureHandle = textureHandles[0];
        GlUtil.checkGlError("glGenTextures");

        // Bind the texture handle to the 2D texture target.
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle);

        // Configure min/mag filtering, i.e. what scaling method do we use if what we're rendering
        // is smaller or larger than the source image.
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GlUtil.checkGlError("loadImageTexture");

        // Load the data from the buffer into the texture handle.
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, /*level*/ 0, format,
                width, height, /*border*/ 0, format, GLES20.GL_UNSIGNED_BYTE, data);
        GlUtil.checkGlError("loadImageTexture");

        return textureHandle;
    }

    public static int createTextureOES() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);

        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, textures[0]);
        GlUtil.checkGlError("glBindTexture mTextureID");

        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        return textures[0];
    }

    /**
     * Allocates a direct float buffer, and populates it with the float array data.
     */
    public static FloatBuffer createFloatBuffer(float[] coords) {
        // Allocate a direct ByteBuffer, using 4 bytes per float, and copy coords into it.
        ByteBuffer bb = ByteBuffer.allocateDirect(coords.length * SIZEOF_FLOAT);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(coords);
        fb.position(0);
        return fb;
    }

    public static void createFrameBuffer(int[] framebuffers, int[] textures, int with, int height) {
        glGenTextures(1, textures, 0);

        glBindTexture(GL_TEXTURE_2D, textures[0]);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, with, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, null);
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glGenFramebuffers(1, framebuffers, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffers[0]);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textures[0], 0);

        glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        int error = glGetError();
        if (error != GL_NO_ERROR) {
            throw new RuntimeException("createCamFrameBuff: glError 0x" + Integer.toHexString(error));
        }
    }

    /**
     * Writes GL version info to the log.
     */
    public static void logVersionInfo() {
        Log.i(TAG, "vendor  : " + GLES20.glGetString(GLES20.GL_VENDOR));
        Log.i(TAG, "renderer: " + GLES20.glGetString(GLES20.GL_RENDERER));
        Log.i(TAG, "version : " + GLES20.glGetString(GLES20.GL_VERSION));

        if (false) {
            int[] values = new int[1];
            GLES30.glGetIntegerv(GLES30.GL_MAJOR_VERSION, values, 0);
            int majorVersion = values[0];
            GLES30.glGetIntegerv(GLES30.GL_MINOR_VERSION, values, 0);
            int minorVersion = values[0];
            if (GLES30.glGetError() == GLES30.GL_NO_ERROR) {
                Log.i(TAG, "iversion: " + majorVersion + "." + minorVersion);
            }
        }
    }

    // my utils
    public static float[] getTextureMatrix(int degree, boolean flipX, boolean flipY) {
        float[] rs = new float[16];
        Matrix.setIdentityM(rs, 0);
        switch (degree) {
            case 90:
                Matrix.rotateM(rs, 0, 90, 0, 0, 1);
                Matrix.translateM(rs, 0, 0, -1, 0);
                break;
            case 180:
                Matrix.rotateM(rs, 0, 180, 0, 0, 1);
                Matrix.translateM(rs, 0, -1, -1, 0);
                break;
            case 270:
                Matrix.rotateM(rs, 0, 270, 0, 0, 1);
                Matrix.translateM(rs, 0, -1, 0, 0);
                break;
        }
        if (flipX) {
            Matrix.rotateM(rs, 0, 180, 1, 0, 0);
            Matrix.translateM(rs, 0, 0, -1, 0);
        }
        if (flipY) {
            Matrix.rotateM(rs, 0, 180, 0, 1, 0);
            Matrix.translateM(rs, 0, -1, 0, 0);
        }
        return rs;
    }

    public static void flipMatrixY(float[] matrix) {
        Matrix.rotateM(matrix, 0, 180, 0, 1, 0);
        Matrix.translateM(matrix, 0, -1, 0, 0);
    }

    public static int genFrameBufferTexture(int width, int height) {
        int[] rs = new int[1];
        GLES20.glGenTextures(1, rs, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, rs[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GlUtil.checkGlError("genFrameBufferTexture");
        return rs[0];
    }

    public static int genFrameBuffer(int textureId) {
        int[] rs = new int[1];
        GLES20.glGenFramebuffers(1, rs, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, rs[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textureId, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GlUtil.checkGlError("genFrameBuffer");
        return rs[0];
    }

    public static void deleteFrameBuffer(int frameBuffer, int frameBufferTexture) {
        GLES20.glDeleteFramebuffers(1, new int[]{frameBuffer}, 0);
        GLES20.glDeleteTextures(1, new int[]{frameBufferTexture}, 0);
    }

    /**
     * Draws a box.
     */
    public static void drawBox() {
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(0, 0, 2, 2);
        GLES20.glClearColor(1.0f, 0.0f, 1.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }

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
            safelyClose(bufferedReader);
            safelyClose(inputStreamReader);
            safelyClose(inputStream);
        }
        return body.toString();
    }

    private static void safelyClose(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                // ignored
            }
        }
    }
}
