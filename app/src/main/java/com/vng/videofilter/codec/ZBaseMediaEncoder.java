package com.vng.videofilter.codec;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

import java.nio.ByteBuffer;

/**
 * Created by taitt on 13/02/2017.
 */

public abstract class ZBaseMediaEncoder extends ZQuitJoinThread {

    private static final String TAG = "ZBaseMediaEncoder";
    private static final long DEQUEUE_TIMEOUT = 10000;
    private MediaCodec.BufferInfo bufferInfo;
    protected MediaCodec encoder;
    protected long startTime = 0;

    public ZBaseMediaEncoder(String name) {
        super(name);
        bufferInfo = new MediaCodec.BufferInfo();
        startTime = 0;
    }

    public final void process(byte[] inputData, long timeMs) {
        int eibIndex = encoder.dequeueInputBuffer(-1);
        if (eibIndex >= 0) {
            ByteBuffer dstVideoEncoderIBuffer = encoder.getInputBuffers()[eibIndex];
            dstVideoEncoderIBuffer.position(0);
            dstVideoEncoderIBuffer.put(inputData, 0, inputData.length);
            encoder.queueInputBuffer(eibIndex, 0, inputData.length, timeMs * 1000, 0);
        } else {
            Log.d(TAG, "encoder.dequeueInputBuffer(-1)<0");
        }
    }

    public final void release() {
        quitThenJoin();
        bufferInfo = null;
        if (encoder != null) {
            encoder.stop();
            encoder.release();
            encoder = null;
        }
    }

    @Override
    public void run0() {
        int eobIndex = encoder.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT);
        switch (eobIndex) {
            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                break;
            case MediaCodec.INFO_TRY_AGAIN_LATER:
                break;
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                onOutputFormatChanged(encoder.getOutputFormat());
                break;
            default:
                if (startTime == 0) {
                    startTime = bufferInfo.presentationTimeUs / 1000;
                }
                if (bufferInfo.flags != MediaCodec.BUFFER_FLAG_CODEC_CONFIG && bufferInfo.size != 0) {
                    ByteBuffer realData = encoder.getOutputBuffers()[eobIndex];
                    onOutputData(realData, bufferInfo);
                }

                encoder.releaseOutputBuffer(eobIndex, false);
                break;
        }
    }

    protected abstract MediaFormat getFormat();

    protected abstract void onOutputFormatChanged(MediaFormat outputFormat);

    protected abstract void onOutputData(ByteBuffer realData, MediaCodec.BufferInfo bufferInfo);

    public void printInfo() {
        int numCodecs = MediaCodecList.getCodecCount();
        MediaCodecInfo codecInfo = null;
        for (int i = 0; i < numCodecs && codecInfo == null; i++) {
            MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
            if (info.isEncoder()) {
                continue;
            }
            String[] types = info.getSupportedTypes();
            for (String s : types) {
                Log.d(TAG, "mime: " + s + " decoder: " + info.getName());
            }
        }
    }
}