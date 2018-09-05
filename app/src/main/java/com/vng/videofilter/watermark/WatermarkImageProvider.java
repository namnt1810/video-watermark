package com.vng.videofilter.watermark;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.opengl.GLES20;
import android.text.TextPaint;

import com.vng.videofilter.gles.GlUtil;
import com.vng.videofilter.util.AndroidUtilities;
import com.vng.videofilter.util.TextureUtils;

import java.nio.ByteBuffer;

/**
 * Copyright (C) 2017, VNG Corporation.
 *
 * @author namnt4
 * @since 24/08/2018
 */

public class WatermarkImageProvider implements WatermarkProvider {

    private final int mBorderColor;

    private final int mAppNameTextSize;

    private final int mIdTextSize;

    private int mStreamId;

    private int mWidth;

    private int mHeight;

    private int[] mTextureId = new int[1];

    public WatermarkImageProvider(int streamId) {
        mStreamId = streamId;
        mBorderColor = Color.parseColor("#80000000");
        mAppNameTextSize = AndroidUtilities.sp(18);
        mIdTextSize = AndroidUtilities.sp(12);
        GLES20.glGenTextures(mTextureId.length, mTextureId, 0);
    }

    public void setWidth(int width) {
        mWidth = width;
    }

    public void setHeight(int height) {
        mHeight = height;
    }

    public void setStreamId(int id) {
        mStreamId = id;
    }

    @Override
    public Integer provide() {
        final Bitmap watermark = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(watermark);

        drawAppName(watermark, canvas);
        drawStreamId(watermark, mStreamId, canvas);

        TextureUtils.loadTexture(watermark, mTextureId[0], GLES20.GL_CLAMP_TO_EDGE, GLES20.GL_CLAMP_TO_EDGE);
        return mTextureId[0];
    }

    private void drawAppName(Bitmap source, Canvas canvas) {
        final float xPosition = source.getWidth() - AndroidUtilities.dp(100);

        final String appName = "360Live";

        final TextPaint paint = new TextPaint();

        // draw app name text
        paint.setAlpha(Math.round(255 * 0.9f));
        paint.setColor(Color.WHITE);
        paint.setTextSize(mAppNameTextSize);
//        paint.setTypeface(mAppNameTypeface);
        canvas.drawText(appName, xPosition, AndroidUtilities.dp(130), paint);

        // draw app name text border
        paint.reset();
        paint.setColor(mBorderColor);
//        paint.setTypeface(mAppNameTypeface);
        paint.setTextSize(mAppNameTextSize);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(0.6f));
        canvas.drawText(appName, xPosition, AndroidUtilities.dp(130), paint);
    }

    private void drawStreamId(Bitmap source, int streamId, Canvas canvas) {
        final float xPosition = source.getWidth() - AndroidUtilities.dp(100);

        String id = "id:" + streamId;

        final TextPaint paint = new TextPaint();

        // draw stream id text
        paint.setAlpha(Math.round(255 * 0.8f));
        paint.setTextSize(mIdTextSize);
        paint.setColor(Color.WHITE);
//        paint.setTypeface(mStreanIdTypeface);
        canvas.drawText(id, xPosition, AndroidUtilities.dp(145), paint);

        // draw stream id text border
        paint.reset();
        paint.setColor(mBorderColor);
//        paint.setTypeface(mStreanIdTypeface);
        paint.setTextSize(mIdTextSize);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(0.1f));
        canvas.drawText(id, xPosition, AndroidUtilities.dp(145), paint);
    }
}
