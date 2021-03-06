package com.vng.videofilter.watermark;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;

import com.vng.videofilter.util.DispatchQueue;
import com.vng.videofilter.util.Utils;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Copyright (C) 2017, VNG Corporation.
 *
 * @author namnt4
 * @since 27/08/2018
 */

public class DecoderWrapper {

    private static final String TAG = DecoderWrapper.class.getSimpleName();

//    protected final DispatchQueue mQueue;

    protected final Handler mQueue;

    private MediaExtractor mMediaExtractor;

    /**
     * The {@link MediaCodec} that is managed by this class.
     */
    private MediaCodec mDecoder;

    private String mTag = "";

    public final Queue<Integer> mInputBufferIndices = new LinkedList<>();

    public final Queue<Long> mInputDataQueue = new LinkedList<>();

    // References to the internal buffers managed by the codec. The codec
    // refers to these buffers by index, never by reference so it's up to us
    // to keep track of which buffer is which.
    private ByteBuffer[] mInputBuffers;
    private ByteBuffer[] mOutputBuffers;

    private ZMediaCodecCallback mMediaCodecCallback;

//    public DecoderWrapper(DispatchQueue queue) {
//        mQueue = queue;
//    }

    public DecoderWrapper(Handler queue) {
        mQueue = queue;
    }

    public void configure(MediaExtractor extractor, Surface surface) throws IOException {
        if (extractor == null) {
            return;
        }

        mMediaExtractor = extractor;

        final int trackCount = mMediaExtractor.getTrackCount();
        int videoTrackIndex = -1;
        for (int i = 0; i < trackCount; i++) {
            final MediaFormat trackFormat = mMediaExtractor.getTrackFormat(i);
            final String mime = trackFormat.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                videoTrackIndex = i;
            }
            mMediaExtractor.unselectTrack(i);
        }

        if (videoTrackIndex == -1) {
            return;
        }

        mMediaExtractor.selectTrack(videoTrackIndex);

        createDecoder(mMediaExtractor.getTrackFormat(videoTrackIndex), surface);
    }

    private void createDecoder(MediaFormat trackFormat, Surface surface) throws IOException {
        Log.d(TAG, "createDecoder");
        final String mimeType = trackFormat.getString(MediaFormat.KEY_MIME);

        mDecoder = MediaCodec.createDecoderByType(mimeType);
            mMediaCodecCallback = Utils.hasLollipop()
                    ? (new ZMediaCodeCallbackV21(mDecoder))
                    : (new ZMediaCodecCallbackPreV21(mDecoder));
//        mDecoder.setCallback(new MediaCodec.Callback() {
//            @Override
//            public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
//                Log.d(TAG, "onInputBufferAvailable");
//                DecoderWrapper.this.onInputBufferAvailable(codec, index);
//            }
//
//            @Override
//            public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
//                DecoderWrapper.this.onOutputBufferAvailable(codec, index, info);
//            }
//
//            @Override
//            public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
//
//            }
//
//            @Override
//            public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
//
//            }
//        });
        mDecoder.configure(trackFormat, surface, null, 0);
        mDecoder.start();
    }

    private String getTag() {
        if (TextUtils.isEmpty(mTag)) {
            mTag = getClass().getSimpleName();
        }
        return mTag;
    }

    private void onInputBufferAvailable(@NonNull final MediaCodec codec, final int index) {
        mQueue.post(() -> {
            if (codec != mDecoder) {
                return;
            }
            mInputBufferIndices.add(index);
            execute();
        });
    }

    public void execute() {
        int index;
        ByteBuffer buffer;
        while (!mInputBufferIndices.isEmpty()) {
            index = mInputBufferIndices.poll();
            buffer = getInputBuffer(index);
            int size = mMediaExtractor.readSampleData(buffer, 0);
            if (size == -1) {
                release();
                break;
            }
            tryQueueInputBuffer(mDecoder, index, 0, size, mMediaExtractor.getSampleTime(), mMediaExtractor.getSampleFlags());
        }
    }

    private void onOutputBufferAvailable(@NonNull final MediaCodec codec, final int index, @NonNull final MediaCodec.BufferInfo info) {
        mQueue.post(() -> {
            if (codec != mDecoder) {
                return;
            }

            tryReleaseOutputBuffer(codec, index, true);
        });
    }

    public void release() {
        releaseCodecAndCallback();
        mMediaExtractor = null;
    }

    private void releaseCodecAndCallback() {
        if (mMediaCodecCallback != null) {
            mMediaCodecCallback.stop();
        }

        releaseCodec();

        if (mMediaCodecCallback != null) {
            try {
                mMediaCodecCallback.join();
            } catch (InterruptedException e) {
            } finally {
                mMediaCodecCallback = null;
            }
        }
    }

    public void releaseCodec() {
        mInputBuffers = null;
        mOutputBuffers = null;

        if (mDecoder == null) {
            return;
        }

        try {
            mDecoder.release();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mDecoder = null;
        }
    }

    private ByteBuffer getInputBuffer(int index) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return mDecoder.getInputBuffer(index);
        }

        if (mInputBuffers == null) {
            mInputBuffers = mDecoder.getInputBuffers();
        }
        return mInputBuffers[index];
    }

    protected ByteBuffer getOutputBuffer(int index) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return mDecoder.getOutputBuffer(index);
        }

        if (mOutputBuffers == null) {
            mOutputBuffers = mDecoder.getOutputBuffers();
        }
        return mOutputBuffers[index];
    }

    protected void tryQueueInputBuffer(MediaCodec codec,
                                       int index,
                                       int offset,
                                       int size,
                                       long presentationTimeUs,
                                       int flags) {
        if (codec == null) {
            return;
        }

        try {
            codec.queueInputBuffer(index, offset, size, presentationTimeUs, flags);
        } catch (IllegalStateException e) {
            Log.e(getTag(), e.toString());
            e.printStackTrace();
        }
    }

    private void tryReleaseOutputBuffer(MediaCodec codec, int index, boolean render) {
        if (codec == null) {
            return;
        }

        try {
            codec.releaseOutputBuffer(index, render);
        } catch (IllegalStateException e) {
            Log.e(getTag(), e.toString());
            e.printStackTrace();
        }
    }

    @TargetApi(21)
    protected void tryReleaseOutputBuffer(MediaCodec codec, int index, long presentationNs) {
        if (codec == null) {
            return;
        }

        try {
            codec.releaseOutputBuffer(index, presentationNs);
        } catch (IllegalStateException e) {
            Log.e(getTag(), e.toString());
            e.printStackTrace();
        }
    }

    /**
     * {@link ZMediaCodecCallback}
     */
    public interface ZMediaCodecCallback {

        void onInputBufferAvailable(@NonNull MediaCodec codec, final int index);

        void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info);

        void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format);

        void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e);

        void start();

        void join() throws InterruptedException;

        void stop();
    }

    /**
     * {@link ZMediaCodeCallbackV21}
     */
    @TargetApi(21)
    private final class ZMediaCodeCallbackV21 extends MediaCodec.Callback implements ZMediaCodecCallback {

        public ZMediaCodeCallbackV21(MediaCodec codec) {
            codec.setCallback(this);
        }

        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
            DecoderWrapper.this.onInputBufferAvailable(codec, index);
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
            DecoderWrapper.this.onOutputBufferAvailable(codec, index, info);
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
        }

        @Override
        public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
        }

        @Override
        public void start() {
        }

        @Override
        public void join() throws InterruptedException {
        }

        @Override
        public void stop() {
        }
    }

    /**
     * {@link ZMediaCodecCallbackPreV21}
     */
    private final class ZMediaCodecCallbackPreV21 implements ZMediaCodecCallback {

        private final Thread mInputThread;

        private final Thread mOutputThread;

        private final WeakReference<MediaCodec> mCodecRef;

        public ZMediaCodecCallbackPreV21(MediaCodec codec) {
            mCodecRef = new WeakReference<>(codec);

            mInputThread = new Thread() {
                @Override
                public void run() {
                    while (!isInterrupted()) {
                        try {
                            MediaCodec codec = mCodecRef.get();
                            if (codec == null) {
                                break;
                            }

                            int index = codec.dequeueInputBuffer(-1);
                            if (index >= 0) {
                                onInputBufferAvailable(codec, index);
                            }
                        } catch (IllegalStateException e) {
                            break;
                        }
                    }
                }
            };

            mOutputThread = new Thread() {
                @Override
                public void run() {
                    while (!isInterrupted()) {
                        try {
                            MediaCodec codec = mCodecRef.get();
                            if (codec == null) {
                                break;
                            }

                            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                            int index = codec.dequeueOutputBuffer(info, -1);
                            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                onOutputFormatChanged(codec, codec.getOutputFormat());
                            } else if (index >= 0) {
                                onOutputBufferAvailable(codec, index, info);
                            }
                        } catch (IllegalStateException | IllegalArgumentException e) {
                            break;
                        }
                    }
                }
            };
        }

        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
            DecoderWrapper.this.onInputBufferAvailable(codec, index);
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
            DecoderWrapper.this.onOutputBufferAvailable(codec, index, info);
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
        }

        @Override
        public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
        }

        @Override
        public void start() {
            mInputThread.start();
            mOutputThread.start();
        }

        @Override
        public void join() throws InterruptedException {
            mInputThread.join();
            mOutputThread.join();
        }

        @Override
        public void stop() {
            if (mInputThread.isAlive()) {
                mInputThread.interrupt();
            }
            if (mOutputThread.isAlive()) {
                mOutputThread.interrupt();
            }
        }
    }
}
