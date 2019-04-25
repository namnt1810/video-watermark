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
import java.util.LinkedList;

/**
 * @author namnt4
 * @since 7/26/2018
 */
public class MuxerWrapper2 {
    public static final boolean VERBOSE = false;

    public static final String TAG = MuxerWrapper.class.getSimpleName();

    private final DispatchQueue mDispatchQueue;

    private final LinkedList<Runnable> mPendingTask = new LinkedList<>();

    private MediaMuxer mMuxer;

    private int mVideoTrack = -1;
    private int mAudioTrack = -1;

    private int mTracksAwaitCount = -1;

    private boolean mIsMuxerStarted = false;

    public MuxerWrapper2(String filePath, boolean hasAudio, boolean hasVideo) {
        try {
            mMuxer = new MediaMuxer(filePath,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mDispatchQueue = new DispatchQueue(TAG, Process.THREAD_PRIORITY_BACKGROUND, new MuxerCallback(this));
        int trackCount = 0;
        trackCount = hasAudio ? ++trackCount : trackCount;
        trackCount = hasVideo ? ++trackCount : trackCount;
        mDispatchQueue.dispatch(mDispatchQueue.obtain(MuxerCallback.MSG_CONFIGURE, trackCount));
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

    private void configureInternal(int tracksAwaitCount) {
        mTracksAwaitCount = tracksAwaitCount;
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
            executePendingTasks();
            mIsMuxerStarted = true;
        }
    }

    private void executePendingTasks() {
        if (VERBOSE) Log.d(TAG, "executePendingTasks(), count = " + mPendingTask.size());
        while (!mPendingTask.isEmpty()) {
            mPendingTask.poll().run();
        }
    }

    private void writeVideoDataInternal(ByteBuffer realData, MediaCodec.BufferInfo bufferInfo) {
//        if (VERBOSE)  Log.d(TAG, "writeVideoDataInternal()");
        if (mMuxer != null && mVideoTrack != -1) {
            Runnable task = () -> mMuxer.writeSampleData(mVideoTrack, realData, bufferInfo);
            if (!mIsMuxerStarted) {
                mPendingTask.add(task);
            } else {
                task.run();
            }
        }
    }

    private void writeAudioDataInternal(ByteBuffer realData, MediaCodec.BufferInfo bufferInfo) {
//        if (VERBOSE)  Log.d(TAG, "writeAudioDataInternal()");
        if (mMuxer != null && mAudioTrack != -1) {
            Runnable task = () -> mMuxer.writeSampleData(mAudioTrack, realData, bufferInfo);
            if (!mIsMuxerStarted) {
                mPendingTask.add(task);
            } else {
                task.run();
            }
        }
    }

    private void releaseMuxerInternal() {
        if (VERBOSE) Log.d(TAG, "releaseMuxerInternal()");
        if (mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }

        mDispatchQueue.clear();
        mDispatchQueue.quit();
    }

    private boolean isMuxerReady() {
        return mTracksAwaitCount == 0;
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

        private final WeakReference<MuxerWrapper2> mRef;

        private MuxerCallback(MuxerWrapper2 muxerWrapper) {
            mRef = new WeakReference<>(muxerWrapper);
        }


        @Override
        public boolean handleMessage(Message message) {
            final MuxerWrapper2 muxerWrapper = mRef.get();
            if (muxerWrapper == null) {
                return false;
            }

            switch (message.what) {
                case MSG_CONFIGURE:
                    muxerWrapper.configureInternal(message.arg1);
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
}
