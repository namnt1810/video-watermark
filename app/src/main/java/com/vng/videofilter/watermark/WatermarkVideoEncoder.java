package com.vng.videofilter.watermark;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.view.Surface;

import com.vng.videofilter.codec.ZBaseMediaEncoder;
import com.vng.videofilter.util.DispatchQueue;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Copyright (C) 2017, VNG Corporation.
 *
 * @author namnt4
 * @since 27/07/2018
 */

public class WatermarkVideoEncoder extends ZBaseMediaEncoder {

    private static final String VIDEO_FORMAT = MediaFormat.MIMETYPE_VIDEO_AVC;
    private DispatchQueue mDispatchQueue;
    private Surface surface;
    private int mWidth;
    private int mHeight;
    private int mBps;
    private int mProfile;
    private int mFps;
    private int mIFrameInterval;
    private EncoderCallback mCallback;

    public WatermarkVideoEncoder(DispatchQueue dispatchQueue, int width, int height, int bps, int profile, int fps, int keyFrameInterval) {
        super("VideoEncoder");
        mDispatchQueue = dispatchQueue;
        mWidth = width;
        mHeight = height;
        mBps = bps;
        mProfile = profile;
        mFps = fps;
        mIFrameInterval = keyFrameInterval;
        try {
            encoder = MediaCodec.createEncoderByType(VIDEO_FORMAT);
            try {
                encoder.configure(getFormat(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            } catch (Exception e) {
                encoder.release();
                encoder = MediaCodec.createEncoderByType(VIDEO_FORMAT);
                encoder.configure(getSimpleFormat(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            }
            surface = encoder.createInputSurface();
            encoder.start();
        } catch (IOException e) {
            throw new RuntimeException("can not create video encoder ", e);
        } catch (IllegalStateException ise) {
            throw new RuntimeException("configure codec error " + getSimpleFormat(), ise);
        }
    }

    @Override
    protected MediaFormat getFormat() {
        MediaCodecInfo.CodecCapabilities capabilities = encoder.getCodecInfo().getCapabilitiesForType(VIDEO_FORMAT);
        MediaFormat format = MediaFormat.createVideoFormat(VIDEO_FORMAT, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mBps);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, mFps);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mIFrameInterval);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            MediaCodecInfo.EncoderCapabilities encoderCapabilities = capabilities.getEncoderCapabilities();
            if (encoderCapabilities.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)) {
                format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
            } else if (encoderCapabilities.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)) {
                format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            format.setInteger(MediaFormat.KEY_PROFILE, mProfile);
            format.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31);
        }
        return format;
    }

    private MediaFormat getSimpleFormat() {
        MediaFormat format = MediaFormat.createVideoFormat(VIDEO_FORMAT, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mBps);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, mFps);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mIFrameInterval);
        return format;
    }

    protected void onOutputFormatChanged(MediaFormat outputFormat) {
        if (mDispatchQueue != null && mCallback != null) {
            mDispatchQueue.dispatch(() -> mCallback.onOutputFormatChanged(outputFormat));
        }
    }

    protected void onOutputData(ByteBuffer realData, MediaCodec.BufferInfo bufferInfo) {
        if (mDispatchQueue != null && mCallback != null) {
            mDispatchQueue.dispatch(() -> mCallback.onOutputData(realData, bufferInfo));
        }
    }

    public boolean setBitrate(int bps) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Bundle bitrateBundle = new Bundle();
            bitrateBundle.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bps);
            encoder.setParameters(bitrateBundle);
            return true;
        } else {
            return false;
        }
    }

    public void setCallback(EncoderCallback callback) {
        mCallback = callback;
    }

    public Surface getInputSurface() {
        return surface;
    }

    interface EncoderCallback {
        void onOutputFormatChanged(MediaFormat outputFormat);

        void onOutputData(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo);
    }
}
