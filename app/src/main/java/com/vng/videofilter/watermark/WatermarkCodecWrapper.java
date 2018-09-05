package com.vng.videofilter.watermark;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.view.Surface;

import com.vng.videofilter.App;
import com.vng.videofilter.gles.EglCore;
import com.vng.videofilter.gles.GlUtil;
import com.vng.videofilter.gles.TextureProgram;
import com.vng.videofilter.gles.WindowSurface;
import com.vng.videofilter.util.DispatchQueue;

import java.io.IOException;
import java.nio.ByteBuffer;

import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_FRAMEBUFFER;
import static android.opengl.GLES20.GL_TEXTURE0;
import static android.opengl.GLES20.GL_TEXTURE1;
import static android.opengl.GLES20.GL_TEXTURE2;
import static android.opengl.GLES20.glBindFramebuffer;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glClearColor;
import static android.opengl.GLES20.glViewport;
import static com.vng.videofilter.gles.TextureProgram.TEXTURE_2D;

/**
 * Copyright (C) 2017, VNG Corporation.
 *
 * @author namnt4
 * @since 27/08/2018
 */

public class WatermarkCodecWrapper implements SurfaceTexture.OnFrameAvailableListener, WatermarkVideoEncoder.EncoderCallback {
    private static final String TAG = WatermarkCodecWrapper.class.getSimpleName();

    private final MediaMuxer mMuxer;

    private final WatermarkProvider mWatermarkProvider;

    private final DispatchQueue mQueue;

    private final int mTextureId;

    private final int mWidth;

    private final int mHeight;

    private DecoderWrapper mDecoder;

    private WatermarkVideoEncoder mEncoder;

    private EglCore mEglCore;

    private Surface mInputSurface;

    private SurfaceTexture mSurfaceTexture;

    private WindowSurface mOutputSurface;

    private VideoTexture mVideoTexture;

    private WatermarkTexture mWatermarkTexture;

    public WatermarkCodecWrapper(MediaExtractor extractor, MediaMuxer muxer, MediaFormat format, WatermarkProvider watermarkProvider, DispatchQueue queue) {
        mMuxer = muxer;
        mWatermarkProvider = watermarkProvider;
        mQueue = queue;
        mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);
        mWidth = format.getInteger(MediaFormat.KEY_WIDTH);
        mHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
        mTextureId = GlUtil.genFrameBufferTexture(mWidth, mHeight);
        mSurfaceTexture = new SurfaceTexture(mTextureId);
        mSurfaceTexture.setOnFrameAvailableListener(this);
        mInputSurface = new Surface(mSurfaceTexture);

        createEncoder(format);

        mOutputSurface = new WindowSurface(mEglCore, mEncoder.getInputSurface(), true);

        mDecoder = new DecoderWrapper(queue);
        try {
            mDecoder.configure(extractor, mInputSurface);
        } catch (IOException e) {
            e.printStackTrace();
        }

//        mVideoTexture = new VideoTexture(App.getInstance());
//        mVideoTexture.init(mWidth, mHeight);
//
//        watermarkProvider.setWidth(mWidth);
//        watermarkProvider.setHeight(mHeight);
//        mWatermarkTexture = new WatermarkTexture(App.getInstance(), watermarkProvider.provide());
//        mWatermarkTexture.init(mWidth, mHeight);
    }

    private void createEncoder(MediaFormat format) {
        ensureValidFormat(format);

        mEncoder = new WatermarkVideoEncoder(mQueue,
                format.getInteger(MediaFormat.KEY_WIDTH),
                format.getInteger(MediaFormat.KEY_HEIGHT),
                format.getInteger(MediaFormat.KEY_BIT_RATE),
                format.getInteger(MediaFormat.KEY_PROFILE),
                format.getInteger(MediaFormat.KEY_FRAME_RATE),
                format.getInteger(MediaFormat.KEY_I_FRAME_INTERVAL));

        mEncoder.setCallback(this);
    }

    private void ensureValidFormat(MediaFormat format) {
        if (!format.containsKey(MediaFormat.KEY_BIT_RATE)) {
            format.setInteger(MediaFormat.KEY_BIT_RATE, 4 * 1024 * 1024);
        }

        if (!format.containsKey(MediaFormat.KEY_PROFILE)) {
            format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
        }

        if (!format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        }

        if (!format.containsKey(MediaFormat.KEY_I_FRAME_INTERVAL)) {
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        Log.d(TAG, "onFrameAvailable");
        mQueue.dispatch(() -> {
            mOutputSurface.makeCurrent();

            if (mVideoTexture == null || mWatermarkTexture == null) {
                mVideoTexture = new VideoTexture(App.getInstance());
                mVideoTexture.init(mWidth, mHeight);

                mWatermarkProvider.setWidth(mWidth);
                mWatermarkProvider.setHeight(mHeight);
                mWatermarkTexture = new WatermarkTexture(App.getInstance(), mWatermarkProvider.provide());
                mWatermarkTexture.init(mWidth, mHeight);
            }

            surfaceTexture.updateTexImage();
            mVideoTexture.draw();
            mWatermarkTexture.draw();
            mOutputSurface.swapBuffers();
        });
    }

    public void release() {
        mDecoder.release();
        mSurfaceTexture.release();
        mInputSurface.release();
    }

    @Override
    public void onOutputFormatChanged(MediaFormat outputFormat) {
        // no-op
    }

    @Override
    public void onOutputData(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
        Log.d(TAG, "onOutputData");
    }

    private static class VideoTexture {
        private static final short[] INDICES = {0, 1, 2, 0, 2, 3};

        private static final float[] TEX_VERTICES = {
                -1.0f, -1.0f, 0.0f, 0.0f,
                -1.0f, 1.0f, 0.0f, 1.0f,
                1.0f, 1.0f, 1.0f, 1.0f,
                1.0f, -1.0f, 1.0f, 0.0f
        };

        private TextureProgram mTextureProgram;

        private Context mContext;

        private int[] mFrameBuffer = new int[1];

        private int[] mFrameBufferTexture = new int[1];

        private int mWidth;

        private int mHeight;

        public VideoTexture(Context context) {
            mContext = context.getApplicationContext();
        }

        public void init(int width, int height) {
            mWidth = width;
            mHeight = height;

            GlUtil.createFrameBuffer(mFrameBuffer, mFrameBufferTexture, mWidth, mHeight);

            mTextureProgram = new TextureProgram(mContext, TEXTURE_2D, TEX_VERTICES, INDICES);
            mTextureProgram.setTextureName(GL_TEXTURE0);
            mTextureProgram.setTextureNameId(0);
        }

        public void draw() {
            glBindFramebuffer(GL_FRAMEBUFFER, mFrameBuffer[0]);
            glClearColor(0f, 0f, 0f, 1f);
            glClear(GL_COLOR_BUFFER_BIT);
            glViewport(0, 0, mWidth, mHeight);
            mTextureProgram.draw(mFrameBufferTexture[0]);
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
        }
    }

    private static class WatermarkTexture {
        private static final short[] INDICES = {0, 1, 2, 0, 2, 3};

        private static final float[] VERTICES = {
                -1.0f, -1.0f, 0.0f, 0.0f,
                -1.0f, 1.0f, 0.0f, 1.0f,
                1.0f, 1.0f, 1.0f, 1.0f,
                1.0f, -1.0f, 1.0f, 0.0f
        };

        private TextureProgram mTextureProgram;

        private Context mContext;

        private int mWatermarkTextureId;

        private int mWidth;

        private int mHeight;

        public WatermarkTexture(Context context, int watermarkTextureId) {
            mContext = context.getApplicationContext();
            mWatermarkTextureId = watermarkTextureId;
        }

        public void init(int width, int height) {
            mWidth = width;
            mHeight = height;

            mTextureProgram = new TextureProgram(mContext, TEXTURE_2D, VERTICES, INDICES);
            mTextureProgram.setTextureName(GL_TEXTURE1);
            mTextureProgram.setTextureNameId(1);
        }

        public void draw() {
            glClearColor(0f, 0f, 0f, 1f);
            glClear(GL_COLOR_BUFFER_BIT);
            glViewport(0, 0, mWidth, mHeight);
            mTextureProgram.draw(mWatermarkTextureId);
        }
    }
}
