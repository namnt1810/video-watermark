package com.vng.videofilter.watermark;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.support.v4.util.Pair;

import com.vng.videofilter.codec.MuxerWrapper2;
import com.vng.videofilter.codec.ZBaseMediaEncoder;
import com.vng.videofilter.codec.ZQuitJoinThread;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;

/**
 * Copyright (C) 2017, VNG Corporation.
 * @author namnt4
 * @since 27/07/2018
 */

public class AudioEncodeMuxWrapper extends ZBaseMediaEncoder {

    private static final String AUDIO_FORMAT = MediaFormat.MIMETYPE_AUDIO_AAC;
    private static final int SAMPLE_RATE = 44100;
    private MuxerWrapper2 muxer;
    private ZQuitJoinThread recordThread;
    private ZQuitJoinThread drainThread;
    private BlockingQueue<Pair<ByteBuffer, MediaCodec.BufferInfo>> mInputData;

    public AudioEncodeMuxWrapper(MuxerWrapper2 muxer) {
        super("ZAudioEncoder");
        this.muxer = muxer;
        try {
            encoder = MediaCodec.createEncoderByType(AUDIO_FORMAT);
            encoder.configure(getFormat(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();
        } catch (IOException e) {
            throw new RuntimeException("can not create audio encoder ", e);
        }
    }

    @Override
    protected MediaFormat getFormat() {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, AUDIO_FORMAT);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 2);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 32 * 1024);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, SAMPLE_RATE);
        return format;
    }

    @Override
    protected void onOutputFormatChanged(MediaFormat outputFormat) {
        muxer.addAudioTrack(outputFormat);
    }

    @Override
    protected void onOutputData(ByteBuffer realData, MediaCodec.BufferInfo bufferInfo) {
        muxer.writeAudioData(realData, bufferInfo);
    }

    public void feedData(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
        mInputData.add(new Pair<>(byteBuffer, bufferInfo));
        int eibIndex = encoder.dequeueInputBuffer(-1);
        while (eibIndex != -1 && !mInputData.isEmpty()) {
            try {
                Pair<ByteBuffer, MediaCodec.BufferInfo> data = mInputData.take();
                byte[] inputData = data.first.array();
                ByteBuffer dstVideoEncoderIBuffer = encoder.getInputBuffers()[eibIndex];
                dstVideoEncoderIBuffer.position(0);
                dstVideoEncoderIBuffer.put(inputData, 0, inputData.length);
                encoder.queueInputBuffer(eibIndex, 0, inputData.length, data.second.presentationTimeUs * 1000, 0);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
