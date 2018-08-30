package com.vng.videofilter.watermark;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.opengl.GLES20;

import com.vng.videofilter.gles.GlUtil;

import java.nio.ByteBuffer;

/**
 * Copyright (C) 2017, VNG Corporation.
 *
 * @author namnt4
 * @since 24/08/2018
 */

public class WatermarkImageProvider implements WatermarkProvider {

    private int mWidth;

    private int mHeight;

    private int mTextSize;

    public void setWidth(int width) {
        mWidth = width;
    }

    public void setHeight(int height) {
        mHeight = height;
    }

    public void setTextSize(int textSize) {
        mTextSize = textSize;
    }

    @Override
    public Integer provide() {
        final Bitmap watermark = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(watermark);
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        paint.setColor(Color.WHITE);
        paint.setTextSize(mTextSize);

        canvas.drawText("360Live\n12345", 0, 0, paint);

        return GlUtil.createImageTexture(ByteBuffer.allocate(watermark.getByteCount()), mWidth, mHeight, GLES20.GL_TEXTURE_2D);
    }
}
