package com.vng.videofilter.codec;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.util.Log;

import com.vng.videofilter.util.DispatchQueue;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

/**
 * @author namnt4
 * @since 7/26/2018
 */
public class MuxerWrapper {
    public static final boolean VERBOSE = false;

    public static final String TAG = MuxerWrapper.class.getSimpleName();

    private MediaMuxer mMuxer;

    private final DispatchQueue mDispatchQueue;

    private int mVideoTrack = -1;
    private int mAudioTrack = -1;

    private int mTracksAwaitCount = 0;

    private boolean mIsMuxerStarted = false;

    public MuxerWrapper(String filePath, MuxerConfiguration configuration) {
        try {
            mMuxer = new MediaMuxer(filePath,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mDispatchQueue = new DispatchQueue(TAG, Process.THREAD_PRIORITY_BACKGROUND, new MuxerCallback(this));
        mDispatchQueue.dispatch(mDispatchQueue.obtain(MuxerCallback.MSG_CONFIGURE, configuration));
    }

    public void addVideoTrack(MediaFormat format) {
        mDispatchQueue.dispatch(mDispatchQueue.obtain(MuxerCallback.MSG_ADD_VIDEO_TRACK, 0, 0, format));
    }

    public void addAudioTrack(MediaFormat format) {
        mDispatchQueue.dispatch(mDispatchQueue.obtain(MuxerCallback.MSG_ADD_AUDIO_TRACK, 0, 0, format));
    }

    public void start() {
        mDispatchQueue.dispatch(mDispatchQueue.obtain(MuxerCallback.MSG_START_MUXER));
    }

    public void writeVideoData(ByteBuffer realData, MediaCodec.BufferInfo bufferInfo) {
        Object[] data = {realData, bufferInfo};
        mDispatchQueue.dispatch(mDispatchQueue.obtain(MuxerCallback.MSG_WRITE_VIDEO_DATA, 0, 0, data));
    }

    public void writeAudioData(ByteBuffer realData, MediaCodec.BufferInfo bufferInfo) {
        Object[] data = {realData, bufferInfo};
        mDispatchQueue.dispatch(mDispatchQueue.obtain(MuxerCallback.MSG_WRITE_AUDIO_DATA, 0, 0, data));
    }

    public void releaseMuxer() {
        mDispatchQueue.dispatch(mDispatchQueue.obtain(MuxerCallback.MSG_RELEASE_MUXER));
    }

    public boolean isMuxerStarted() {
        return mIsMuxerStarted;
    }

    private void configureInternal(MuxerConfiguration configuration) {
        if (configuration.hasAudio()) {
            mTracksAwaitCount++;
        }

        if (configuration.hasVideo()) {
            mTracksAwaitCount++;
        }
    }

    private void addVideoTrackInternal(MediaFormat format) {
        if (VERBOSE) Log.d(TAG, "addVideoTrackInternal()");
        if (mMuxer != null) {
            mVideoTrack = mMuxer.addTrack(format);
            --mTracksAwaitCount;
            startInternal();
        }
    }

    private void addAudioTrackInternal(MediaFormat format) {
        if (VERBOSE) Log.d(TAG, "addAudioTrackInternal()");
        if (mMuxer != null) {
            mAudioTrack = mMuxer.addTrack(format);
            --mTracksAwaitCount;
            startInternal();
        }
    }

    private void startInternal() {
        if (VERBOSE) Log.d(TAG, "startInternal()");
        if (mMuxer != null && isMuxerReady()) {
            mMuxer.start();
            mIsMuxerStarted = true;
        }
    }

    private void writeVideoDataInternal(ByteBuffer realData, MediaCodec.BufferInfo bufferInfo) {
//        if (VERBOSE)  Log.d(TAG, "writeVideoDataInternal()");
        if (mMuxer != null && mVideoTrack != -1) {
            if (!mIsMuxerStarted) {
                mMuxer.start();
            }
            mMuxer.writeSampleData(mVideoTrack, realData, bufferInfo);
        }
    }

    private void writeAudioDataInternal(ByteBuffer realData, MediaCodec.BufferInfo bufferInfo) {
//        if (VERBOSE)  Log.d(TAG, "writeAudioDataInternal()");
        if (mMuxer != null && mAudioTrack != -1) {
            if (!mIsMuxerStarted) {
                mMuxer.start();
            }
            mMuxer.writeSampleData(mAudioTrack, realData, bufferInfo);
        }
    }

    private void releaseMuxerInternal() {
        if (VERBOSE) Log.d(TAG, "releaseMuxerInternal()");
        if (mMuxer != null) {
            if (mIsMuxerStarted) {
                mMuxer.stop();
            }
            mMuxer.release();
            mMuxer = null;
            mIsMuxerStarted = false;
        }

        mDispatchQueue.clear();
        mDispatchQueue.quit();
    }

    private boolean isMuxerReady() {
        return mTracksAwaitCount <= 0;
    }

    /**
     * {@link MuxerCallback}
     */
    private static class MuxerCallback implements Handler.Callback {

        public static final int MSG_CONFIGURE = 0;

        public static final int MSG_START_MUXER = 1;

        public static final int MSG_RELEASE_MUXER = 2;

        public static final int MSG_ADD_AUDIO_TRACK = 3;

        public static final int MSG_ADD_VIDEO_TRACK = 4;

        public static final int MSG_WRITE_AUDIO_DATA = 5;

        public static final int MSG_WRITE_VIDEO_DATA = 6;

        private final WeakReference<MuxerWrapper> mRef;

        private MuxerCallback(MuxerWrapper muxerWrapper) {
            mRef = new WeakReference<>(muxerWrapper);
        }


        @Override
        public boolean handleMessage(Message message) {
            final MuxerWrapper muxerWrapper = mRef.get();
            if (muxerWrapper == null) {
                return false;
            }

            switch (message.what) {
                case MSG_CONFIGURE:
                    muxerWrapper.configureInternal(((MuxerConfiguration) message.obj));
                    return true;

                case MSG_START_MUXER:
                    muxerWrapper.startInternal();
                    return true;

                case MSG_RELEASE_MUXER:
                    muxerWrapper.releaseMuxerInternal();
                    return true;

                case MSG_ADD_AUDIO_TRACK:
                    muxerWrapper.addAudioTrackInternal((MediaFormat) message.obj);
                    return true;

                case MSG_ADD_VIDEO_TRACK:
                    muxerWrapper.addVideoTrackInternal((MediaFormat) message.obj);
                    return true;

                case MSG_WRITE_AUDIO_DATA:
                    final Object[] audioData = (Object[]) message.obj;
                    muxerWrapper.writeAudioDataInternal(((ByteBuffer) audioData[0]), ((MediaCodec.BufferInfo) audioData[1]));
                    return true;

                case MSG_WRITE_VIDEO_DATA:
                    final Object[] videoData = (Object[]) message.obj;
                    muxerWrapper.writeVideoDataInternal(((ByteBuffer) videoData[0]), ((MediaCodec.BufferInfo) videoData[1]));
                    return true;
            }

            return false;
        }
    }

    public static class MuxerConfiguration {
        private boolean mHasVideo = false;

        private boolean mHasAudio = false;

        public MuxerConfiguration() {
        }

        public MuxerConfiguration setHasVideo(boolean hasVideo) {
            mHasVideo = hasVideo;
            return this;
        }

        public MuxerConfiguration setHasAudio(boolean hasAudio) {
            mHasAudio = hasAudio;
            return this;
        }

        public boolean hasAudio() {
            return mHasAudio;
        }

        public boolean hasVideo() {
            return mHasVideo;
        }
    }
}
