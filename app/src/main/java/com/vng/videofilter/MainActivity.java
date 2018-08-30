package com.vng.videofilter;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.widget.ImageView;

public class MainActivity extends AppCompatActivity {
    private static final int SCREEN_PERMISSION_CODE = 2637;

    private static final String DISPLAY_NAME = "screenrecording";

    private MediaProjectionManager mProjectionManager;

    private MediaProjection mProjection;

    private VirtualDisplay mVirtualDisplay;

    private Surface mSurface;

    private SurfaceTexture mSurfaceTexture;

    private ImageView mImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mImageView = findViewById(R.id.image);
//
//        mSurfaceTexture = new SurfaceTexture(0);
//        mSurface = new Surface(mSurfaceTexture);
//
//        MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
//        startActivityForResult(projectionManager.createScreenCaptureIntent(), SCREEN_PERMISSION_CODE);

        final DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        final Bitmap bitmap = Bitmap.createBitmap(displayMetrics.widthPixels, displayMetrics.heightPixels, Bitmap.Config.ARGB_8888);
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.RED);
        paint.setTextSize(displayMetrics.scaledDensity * 30);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawText("360Live", displayMetrics.widthPixels / 2, displayMetrics.heightPixels / 2, paint);

        mImageView.setImageBitmap(bitmap);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//        if (SCREEN_PERMISSION_CODE == requestCode) {
//            mProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
//            mProjection = mProjectionManager.getMediaProjection(resultCode, data);
//            mVirtualDisplay = mProjection.createVirtualDisplay(DISPLAY_NAME,
//                    width, height, density,
//                    VIRTUAL_DISPLAY_FLAG_PRESENTATION,
//                    mSurface, null, null);
//        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onDestroy() {
        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
        }
        super.onDestroy();
    }
}
