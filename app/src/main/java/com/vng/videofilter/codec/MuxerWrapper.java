package com.vng.videofilter.codec;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import android.util.SparseIntArray;

import com.vng.videofilter.util.DispatchQueue;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

/**
 * @author namnt4
 * @since 7/26/2018
 */
public class MuxerWrapper {
    public static final boolean VERBOSE = true;

    public static final String TAG = MuxerWrapper.class.getSimpleName();

    private MediaMuxer mMuxer;

    private final DispatchQueue mDispatchQueue;

    private final SparseIntArray mIndicesMap = new SparseIntArray();

    private boolean mIsMuxerStarted = false;

    public MuxerWrapper(String filePath) {
        try {
            mMuxer = new MediaMuxer(filePath,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mDispatchQueue = new DispatchQueue(TAG, Process.THREAD_PRIORITY_BACKGROUND, new MuxerCallback(this));
    }

    public void start() {
        mDispatchQueue.dispatch(mDispatchQueue.obtain(MuxerCallback.MSG_START_MUXER));
    }

    public void addTrack(int sourceTrackIndex, MediaFormat format) {
        mDispatchQueue.dispatch(mDispatchQueue.obtain(MuxerCallback.MSG_ADD_TRACK, sourceTrackIndex, 0, format));
    }

    public void setOrientationHint(int degree) {
        mDispatchQueue.dispatch(mDispatchQueue.obtain(MuxerCallback.MSG_SET_ORIENTATION_HINT, degree));
    }

    public void writeSampleData(int sourceTrackIndex, ByteBuffer realData, MediaCodec.BufferInfo bufferInfo) {
        Object[] data = {realData, bufferInfo};
        mDispatchQueue.dispatch(mDispatchQueue.obtain(MuxerCallback.MSG_WRITE_SAMPLE_DATA, sourceTrackIndex, 0, data));
    }

    public void releaseMuxer() {
        mDispatchQueue.dispatch(mDispatchQueue.obtain(MuxerCallback.MSG_RELEASE_MUXER));
    }

    public boolean isMuxerStarted() {
        return mIsMuxerStarted;
    }

    private void startInternal() {
        if (VERBOSE) Log.d(TAG, "startInternal()");
        if (mMuxer != null) {
            mMuxer.start();
            mIsMuxerStarted = true;
        }
    }

    private void addTrackInternal(int sourceTrackIndex, MediaFormat format) {
        if (VERBOSE) Log.d(TAG, "addTrackInternal(), format: " + format.toString());
        int destTrackIndex = mMuxer.addTrack(format);
        mIndicesMap.put(sourceTrackIndex, destTrackIndex);
    }

    private void writeSampleDataInternal(int sourceTrackIndex, ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
        if (VERBOSE) Log.d(TAG, "writeSampleDataInternal()");
        int destTrackIndex = mIndicesMap.get(sourceTrackIndex, -1);
        if (destTrackIndex > -1) {
            if (!isMuxerStarted()) {
                mMuxer.start();
                mIsMuxerStarted = true;
            }
            mMuxer.writeSampleData(destTrackIndex, byteBuffer, bufferInfo);
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

    private void setOrientationHintInternal(int degree) {
        if (!isMuxerStarted()) {
            mMuxer.setOrientationHint(degree);
        }
    }

    /**
     * {@link MuxerCallback}
     */
    private static class MuxerCallback implements Handler.Callback {

        private static final int MSG_BASE = 0;

        public static final int MSG_START_MUXER = MSG_BASE + 2;

        public static final int MSG_RELEASE_MUXER = MSG_BASE + 3;

        public static final int MSG_ADD_TRACK = MSG_BASE + 4;

        public static final int MSG_WRITE_SAMPLE_DATA = MSG_BASE + 5;

        public static final int MSG_SET_ORIENTATION_HINT = MSG_BASE + 6;

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
                case MSG_START_MUXER:
                    muxerWrapper.startInternal();
                    return true;

                case MSG_RELEASE_MUXER:
                    muxerWrapper.releaseMuxerInternal();
                    return true;

                case MSG_ADD_TRACK:
                    muxerWrapper.addTrackInternal(message.arg1, ((MediaFormat) message.obj));
                    return true;

                case MSG_WRITE_SAMPLE_DATA:
                    final Object[] videoData = (Object[]) message.obj;
                    muxerWrapper.writeSampleDataInternal(message.arg1, ((ByteBuffer) videoData[0]), ((MediaCodec.BufferInfo) videoData[1]));
                    return true;

                case MSG_SET_ORIENTATION_HINT:
                    muxerWrapper.setOrientationHintInternal(message.arg1);
                    return true;
            }

            return false;
        }
    }
}
