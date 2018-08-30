package com.vng.videofilter.watermark;

import android.graphics.SurfaceTexture;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.EGL14;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import com.vng.videofilter.gles.EglCore;
import com.vng.videofilter.gles.GlUtil;
import com.vng.videofilter.util.DispatchQueue;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * Copyright (C) 2017, VNG Corporation.
 *
 * @author namnt4
 * @since 27/08/2018
 */

public class WatermarkCodecWrapper implements SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = WatermarkCodecWrapper.class.getSimpleName();

    private final MediaMuxer mMuxer;

//    private final DispatchQueue mQueue;

    private final Handler mQueue;

    private DecoderWrapper mDecoder;

//    private EglCore mEglCore;

    private Surface mSurface;

    private SurfaceTexture mSurfaceTexture;

    private CountDownLatch mCountDownLatch;

    public WatermarkCodecWrapper(MediaExtractor extractor, MediaMuxer muxer, MediaFormat format, Handler queue) {
        mMuxer = muxer;
        mQueue = queue;
//        mEglCore = new EglCore(EGL14.eglGetCurrentContext(), EglCore.FLAG_RECORDABLE);
        mSurfaceTexture = new SurfaceTexture(GlUtil.genFrameBufferTexture(format.getInteger(MediaFormat.KEY_WIDTH), format.getInteger(MediaFormat.KEY_HEIGHT)));
        mSurfaceTexture.setOnFrameAvailableListener(this);
        mSurface = new Surface(mSurfaceTexture);
        mDecoder = new DecoderWrapper(queue);

        try {
            mDecoder.configure(extractor, mSurface);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        Log.d(TAG, "onFrameAvailable");
    }

    public void release() {
        mDecoder.release();
        mSurfaceTexture.release();
        mSurface.release();
    }
}
