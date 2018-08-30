package com.vng.videofilter.watermark;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;

import com.vng.videofilter.App;
import com.vng.videofilter.gles.GlUtil;
import com.vng.videofilter.util.DispatchQueue;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

/**
 * Copyright (C) 2017, VNG Corporation.
 *
 * @author namnt4
 * @since 24/08/2018
 */

public class WatermarkGenerator {
    private static final String TAG = WatermarkGenerator.class.getSimpleName();

    private final WatermarkProvider mWatermarkProvider;

//    private final DispatchQueue mDispatchQueue;

    private final HandlerThread mHandlerThread;

    private final GeneratorCallback mDispatchQueue;

    private final MediaExtractor mMediaExtractor = new MediaExtractor();

    private final MediaMetadataRetriever mMetadataRetriever = new MediaMetadataRetriever();

    private MediaMuxer mMuxer;

    private WatermarkCodecWrapper mCodecWrapper;

    private volatile boolean mIsReady = false;

    public static WatermarkGenerator with(WatermarkProvider provider) {
        return new WatermarkGenerator(provider);
    }

    private WatermarkGenerator(WatermarkProvider provider) {
        mWatermarkProvider = provider;
//        mDispatchQueue = new DispatchQueue("watermark_generator", Process.THREAD_PRIORITY_BACKGROUND, new GeneratorCallback(this));
        mHandlerThread = new HandlerThread("watermark_generator");
        mHandlerThread.start();
        mDispatchQueue = new GeneratorCallback(mHandlerThread.getLooper(), this);

    }

    public void setSource(Uri sourceUri) {
//        mDispatchQueue.dispatch(mDispatchQueue.obtain(GeneratorCallback.MSG_SET_SOURCE, sourceUri));
        mDispatchQueue.sendMessage(mDispatchQueue.obtainMessage(GeneratorCallback.MSG_SET_SOURCE, sourceUri));
    }

    public void generate() {
//        mDispatchQueue.dispatch(mDispatchQueue.obtain(GeneratorCallback.MSG_START_GENERATING));
        mDispatchQueue.sendMessage(mDispatchQueue.obtainMessage(GeneratorCallback.MSG_START_GENERATING));
    }

    public void release() {
        mDispatchQueue.post(this::releaseInternal);
        mHandlerThread.quitSafely();
    }

    private void setSourceInternal(Uri sourceUri) {
        try {
            mMetadataRetriever.setDataSource(sourceUri.getPath());
            mMediaExtractor.setDataSource(App.getInstance(), sourceUri, null);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_hhmmss", Locale.getDefault());
            String destPath = Environment.getExternalStorageDirectory().getPath() + "/360Live/watermark_" + sdf.format(new Date()) + ".mp4";
            mMuxer = new MediaMuxer(destPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mIsReady = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void generateInternal() {
        Log.d(TAG, "generateInternal()");
        if (!mIsReady) {
            throw new IllegalStateException("Generator is not configured well. Please read log for more information.");
        }

//        final CountDownLatch countDownLatch = new CountDownLatch(1);
//
//        int trackCount = mMediaExtractor.getTrackCount();
//
//        // Set up the tracks and retrieve the max buffer size for selected
//        // tracks.
//        final SparseIntArray muxerTrackIndices = new SparseIntArray(trackCount);
//        int bufferSize = -1;
//        int sourceVideoTrackIndex = -1;
//        for (int i = 0; i < trackCount; i++) {
//            MediaFormat format = mMediaExtractor.getTrackFormat(i);
//            String mime = format.getString(MediaFormat.KEY_MIME);
//            if (mime.startsWith("video/")) {
//                // Initialize video codec
//                sourceVideoTrackIndex = i;
//            } else {
//                mMediaExtractor.selectTrack(i);
//            }
//            int dstIndex = mMuxer.addTrack(format);
//            muxerTrackIndices.put(i, dstIndex);
//            if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
//                int newSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
//                bufferSize = newSize > bufferSize ? newSize : bufferSize;
//            }
//        }
//
//        if (bufferSize < 0) {
//            bufferSize = 1024;
//        }
//
//        // Set up the orientation and starting time for extractor.
//        String degreesString = mMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
//        if (degreesString != null) {
//            int degrees = Integer.parseInt(degreesString);
//            if (degrees >= 0) {
//                mMuxer.setOrientationHint(degrees);
//            }
//        }
//
//        // Copy the samples from MediaExtractor to MediaMuxer. We will loop
//        // for copying each sample and stop when we get to the end of the source
//        // file or exceed the end time of the trimming.
//        int offset = 0;
//        int sourceTrackIndex;
//        ByteBuffer dstBuf = ByteBuffer.allocate(bufferSize);
//        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
//
//        try {
//            mMuxer.start();
//            while (true) {
//                bufferInfo.offset = offset;
//                bufferInfo.size = mMediaExtractor.readSampleData(dstBuf, offset);
//                if (bufferInfo.size < 0) {
//                    bufferInfo.size = 0;
//                    break;
//                } else {
//                    bufferInfo.presentationTimeUs = mMediaExtractor.getSampleTime();
//                    bufferInfo.flags = mMediaExtractor.getSampleFlags();
//                    sourceTrackIndex = mMediaExtractor.getSampleTrackIndex();
//
//                    mMuxer.writeSampleData(muxerTrackIndices.get(sourceTrackIndex), dstBuf, bufferInfo);
//                    mMediaExtractor.advance();
//                }
//            }
//
//            if (sourceVideoTrackIndex != -1) {
//                final MediaFormat format = mMediaExtractor.getTrackFormat(sourceVideoTrackIndex);
//                mCodecWrapper = new WatermarkCodecWrapper(mMediaExtractor, mMuxer, format, mDispatchQueue);
//            }
//
//            mDispatchQueue.postDelayed(countDownLatch::countDown, 10000);
////            countDownLatch.countDown();
//            countDownLatch.await();
//            mMuxer.stop();
//            Log.d(TAG, "generate(): done");
//        } catch (Exception e) {
//            e.printStackTrace();
//        } finally {
//            mMuxer.release();
//        }

                int trackCount = mMediaExtractor.getTrackCount();

        // Set up the tracks and retrieve the max buffer size for selected
        // tracks.
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = mMediaExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                // Initialize video codec
                final SurfaceTexture surfaceTexture = new SurfaceTexture(GlUtil.genFrameBufferTexture(format.getInteger(MediaFormat.KEY_WIDTH), format.getInteger(MediaFormat.KEY_HEIGHT)));
//                surfaceTexture.setOnFrameAvailableListener(this);
                final Surface surface = new Surface(surfaceTexture);
                final DecoderWrapper decoderWrapper = new DecoderWrapper(mDispatchQueue);

                try {
                    decoderWrapper.configure(mMediaExtractor, surface);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void releaseInternal() {
        if (mMuxer != null) {
            try {
                mCodecWrapper.release();
                mMuxer.stop();
                mMuxer.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static final class GeneratorCallback extends Handler {

        private static final int MSG_SET_SOURCE = 0;

        private static final int MSG_START_GENERATING = 1;

        private final WeakReference<WatermarkGenerator> mRef;

        private GeneratorCallback(Looper looper, WatermarkGenerator generator) {
            super(looper);
            mRef = new WeakReference<>(generator);
        }

        @Override
        public void handleMessage(Message msg) {
            final WatermarkGenerator generator = mRef.get();
            if (generator == null) {
                return;
            }

            switch (msg.what) {
                case MSG_START_GENERATING:
                    generator.generateInternal();
                    break;

                case MSG_SET_SOURCE:
                    generator.setSourceInternal(((Uri) msg.obj));
                    break;
            }
        }
    }

//    private static final class GeneratorCallback implements Handler.Callback {
//
//        private static final int MSG_SET_SOURCE = 0;
//
//        private static final int MSG_START_GENERATING = 1;
//
//        private final WeakReference<WatermarkGenerator> mRef;
//
//        private GeneratorCallback(WatermarkGenerator generator) {
//            mRef = new WeakReference<>(generator);
//        }
//
//        @Override
//        public boolean handleMessage(Message msg) {
//            final WatermarkGenerator generator = mRef.get();
//            if (generator == null) {
//                return false;
//            }
//
//            switch (msg.what) {
//                case MSG_START_GENERATING:
//                    generator.generateInternal();
//                    return true;
//
//                case MSG_SET_SOURCE:
//                    generator.setSourceInternal(((Uri) msg.obj));
//                    return true;
//            }
//
//            return false;
//        }
//    }
}
