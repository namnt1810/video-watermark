package com.vng.videofilter.watermark;

import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import android.view.Surface;

import com.vng.videofilter.App;
import com.vng.videofilter.codec.MuxerWrapper2;
import com.vng.videofilter.gles.AddBlendProgram;
import com.vng.videofilter.gles.EglCore;
import com.vng.videofilter.gles.FullFrameRect;
import com.vng.videofilter.gles.GlUtil;
import com.vng.videofilter.gles.Texture2dProgram;
import com.vng.videofilter.gles.TextureProgram;
import com.vng.videofilter.gles.WindowSurface;
import com.vng.videofilter.util.DispatchQueue;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_TEXTURE1;
import static android.opengl.GLES20.GL_TEXTURE2;
import static android.opengl.GLES20.GL_TEXTURE3;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glClearColor;
import static android.opengl.GLES20.glViewport;
import static com.vng.videofilter.util.Utils.isAudioFormat;
import static com.vng.videofilter.util.Utils.isVideoFormat;

public class WatermarkGenerator2 implements SurfaceTexture.OnFrameAvailableListener,
        ExtractDecoderWrapper.DecoderCallback {
    private static final String TAG = WatermarkGeneratorNew.class.getSimpleName();

    private static final String YES = "yes";

    // parameters for the video encoder
    private static final String OUTPUT_VIDEO_MIME_TYPE = "video/avc"; // H.264 Advanced Video Coding
    private static final int OUTPUT_VIDEO_BIT_RATE = 4 * 1000 * 1000; // 4Mbps
    private static final int OUTPUT_VIDEO_FRAME_RATE = 60; // 60fps
    private static final int OUTPUT_VIDEO_IFRAME_INTERVAL = 10; // 10 seconds between I-frames
    private static final int OUTPUT_VIDEO_COLOR_FORMAT = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;

    private final DispatchQueue mQueue;

    private final MediaMetadataRetriever mRetriever = new MediaMetadataRetriever();

    private int mAudioTrackIndex = -1;
    private MediaExtractor mAudioExtractor;
    private ExtractDecoderWrapper mAudioDecoder;
    private AudioEncodeMuxWrapper mAudioEncoder;

    private int mVideoTrackIndex = -1;
    private MediaExtractor mVideoExtractor;
    private ExtractDecoderWrapper mVideoDecoder;
    private VideoEncodeMuxWrapper mVideoEncoder;

    private String mOutputFilePath;
    private MuxerWrapper2 mMuxerWrapper;

    private EglCore mEglCore;
    private Surface mInputSurface;
    private SurfaceTexture mSurfaceTexture;
    private WindowSurface mOutputSurface;
    private WatermarkProvider mWatermarkProvider;

    private VideoTexture mVideoTexture;
    private WatermarkTexture mWatermarkTexture;
    private OutputTexture mOutputTexture;

    private int mTextureId;

    private int mWidth;

    private int mHeight;

    private int mStreamId = 0;

    public WatermarkGenerator2() {
        mQueue = new DispatchQueue("render_thread", Process.THREAD_PRIORITY_BACKGROUND, new GeneratorCallback(this));
    }

    public void generate(int streamId, Uri inputSource) {
        mQueue.dispatch(mQueue.obtain(GeneratorCallback.MSG_START_GENERATING, streamId, 0, inputSource));
    }

    public void release() {
        mQueue.dispatch(mQueue.obtain(GeneratorCallback.MSG_RELEASE));
    }

    private void generateInternal(int streamId, Uri source) {
        mStreamId = streamId;
        configure(source);
    }

    private void configure(Uri source) {
        setSource(source);
        setupExtractors(source);
        setupMuxer();
        setupCodec();
        setupRenderer();
        execute();
    }

    private void execute() {
        if (mVideoTrackIndex != -1) {
            final HandlerThread videoThread = new HandlerThread("video_decoder", Process.THREAD_PRIORITY_BACKGROUND);
//            Thread videoThread = new Thread(() -> {
//                try {
//                    MediaFormat videoInputFormat = mVideoExtractor.getTrackFormat(mVideoTrackIndex);
//                    mVideoDecoder.configure(mVideoExtractor, videoInputFormat, mInputSurface);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            });

            videoThread.start();
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final Handler handler = new Handler(videoThread.getLooper());
            handler.post(() -> {
                try {
                    MediaFormat videoInputFormat = mVideoExtractor.getTrackFormat(mVideoTrackIndex);
                    mVideoDecoder.configure(mVideoExtractor, videoInputFormat, mInputSurface, this);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                countDownLatch.countDown();
            });

            try {
                countDownLatch.await();
                mVideoEncoder.start();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (mAudioTrackIndex != -1) {
            final HandlerThread audioThread = new HandlerThread("audio_decoder", Process.THREAD_PRIORITY_BACKGROUND);
//            Thread audioThread = new Thread(() -> {
//                try {
//                    MediaFormat audioInputFormat = mAudioExtractor.getTrackFormat(mAudioTrackIndex);
//                    mAudioDecoder.configure(mAudioExtractor, audioInputFormat, null);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            });

            audioThread.start();
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final Handler handler = new Handler(audioThread.getLooper());
            handler.post(() -> {
                try {
                    MediaFormat audioInputFormat = mAudioExtractor.getTrackFormat(mAudioTrackIndex);
                    mAudioDecoder.configure(mAudioExtractor, audioInputFormat, null, null);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                countDownLatch.countDown();
            });

            try {
                countDownLatch.await();
                mAudioEncoder.start();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void setupMuxer() {
        mMuxerWrapper = new MuxerWrapper2(mOutputFilePath, mAudioTrackIndex != -1, mVideoTrackIndex != -1);
    }

    private void setupCodec() {
        if (mVideoTrackIndex == -1) {
            return;
        }

        setupVideoCodec();

        if (mAudioTrackIndex == -1) {
            return;
        }

        setupAudioCodec();
    }

    private void setupVideoCodec() {
        MediaFormat videoInputFormat = mVideoExtractor.getTrackFormat(mVideoTrackIndex);

        mWidth = videoInputFormat.getInteger(MediaFormat.KEY_WIDTH);
        mHeight = videoInputFormat.getInteger(MediaFormat.KEY_HEIGHT);

        mVideoEncoder = new VideoEncodeMuxWrapper(mMuxerWrapper,
                mWidth,
                mHeight,
                OUTPUT_VIDEO_BIT_RATE,
                MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline,
                OUTPUT_VIDEO_FRAME_RATE,
                OUTPUT_VIDEO_IFRAME_INTERVAL);

        mVideoDecoder = new ExtractDecoderWrapper(mQueue);
    }

    private void setupAudioCodec() {
        mAudioEncoder = new AudioEncodeMuxWrapper(mMuxerWrapper);
        mAudioEncoder.start();

        mAudioDecoder = new ExtractDecoderWrapper(mQueue);
        mAudioDecoder.setOutputCallback((byteBuffer, bufferInfo) -> {
            if (mAudioEncoder != null) {
                mAudioEncoder.feedData(byteBuffer, bufferInfo);
            }
        });
    }

    private void setupRenderer() {
        mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);
        mTextureId = GlUtil.genFrameBufferTexture(mWidth, mHeight);
        mSurfaceTexture = new SurfaceTexture(mTextureId);
        mSurfaceTexture.setOnFrameAvailableListener(this);
        mInputSurface = new Surface(mSurfaceTexture);

        mOutputSurface = new WindowSurface(mEglCore, mVideoEncoder.getInputSurface(), true);
        mOutputSurface.makeCurrent();

        mVideoTexture = new VideoTexture(App.getInstance(), mTextureId);
        mVideoTexture.init(mWidth, mHeight);

//        mWatermarkProvider = new WatermarkImageProvider(mStreamId);
//        mWatermarkProvider.setWidth(mWidth);
//        mWatermarkProvider.setHeight(mHeight);
//        Integer watermarkTextureId = mWatermarkProvider.provide();
//
//        mWatermarkTexture = new WatermarkTexture(App.getInstance(), watermarkTextureId);
//        mWatermarkTexture.init(mWidth, mHeight);
//
//        mOutputTexture = new OutputTexture(App.getInstance(), mTextureId, watermarkTextureId);
//        mOutputTexture.init(mWidth, mHeight);
    }

    private void setupExtractors(Uri source) {
        String hasAudio = mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO);
        if (YES.equals(hasAudio)) {
            try {
                mAudioExtractor = new MediaExtractor();
                mAudioExtractor.setDataSource(source.getPath());
                mAudioTrackIndex = getAndSelectAudioTrackIndex(mAudioExtractor);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        String hasVideo = mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO);
        if (YES.equals(hasVideo)) {
            try {
                mVideoExtractor = new MediaExtractor();
                mVideoExtractor.setDataSource(source.getPath());
                mVideoTrackIndex = getAndSelectVideoTrackIndex(mVideoExtractor);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private int getAndSelectAudioTrackIndex(MediaExtractor extractor) {
        int trackCount = extractor.getTrackCount();
        for (int index = 0; index < trackCount; ++index) {
            if (isAudioFormat(extractor.getTrackFormat(index))) {
                extractor.selectTrack(index);
                return index;
            }
        }
        return -1;
    }

    private int getAndSelectVideoTrackIndex(MediaExtractor extractor) {
        int trackCount = extractor.getTrackCount();
        for (int index = 0; index < trackCount; ++index) {
            if (isVideoFormat(extractor.getTrackFormat(index))) {
                extractor.selectTrack(index);
                return index;
            }
        }
        return -1;
    }

    private void releaseInternal() {
        if (mRetriever != null) {
            mRetriever.release();
        }

        if (mAudioExtractor != null) {
            mAudioExtractor.release();
        }

        if (mVideoExtractor != null) {
            mVideoExtractor.release();
        }

        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
        }

        if (mInputSurface != null) {
            mInputSurface.release();
        }

        if (mOutputSurface != null) {
            mOutputSurface.release();
        }

        if (mEglCore != null) {
            mEglCore.release();
        }

        if (mAudioEncoder != null) {
            mAudioEncoder.release();
        }

        if (mAudioDecoder != null) {
            mAudioDecoder.release();
        }

        if (mVideoEncoder != null) {
            mVideoEncoder.release();
        }

        if (mVideoDecoder != null) {
            mVideoDecoder.release();
        }

        if (mMuxerWrapper != null) {
            mMuxerWrapper.releaseMuxer();
        }

        if (mOutputFilePath != null) {
            Intent intent =
                    new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            File file = new File(mOutputFilePath);
            if (file.exists()) {
                intent.setData(Uri.fromFile(file));
                App.getInstance().sendBroadcast(intent);
            }
        }
    }

    private void setSource(Uri sourceUri) {
        mRetriever.setDataSource(sourceUri.getPath());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_hhmmss", Locale.getDefault());
        mOutputFilePath = Environment.getExternalStorageDirectory().getPath() + "/360Live/watermark_" + sdf.format(new Date()) + ".mp4";
    }

    private float[] transformMat = new float[16];

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
//        mVideoDecoder.markExecuting();
        long pts = surfaceTexture.getTimestamp();
        Log.d(TAG, "onFrameAvailable(), pts = " + pts / 1000);
        surfaceTexture.updateTexImage();
        surfaceTexture.getTransformMatrix(transformMat);
        mVideoTexture.draw(transformMat);
//        mWatermarkTexture.draw();
//        mOutputTexture.draw();
        mOutputSurface.setPresentationTime(pts);
        mOutputSurface.swapBuffers();
        try {
            Log.d(TAG, "sleep");
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Log.d(TAG, String.format("onFrameAvailable(), done render frame, pts = %d", pts / 1000));
        mVideoDecoder.advance();
    }

    @Override
    public void onDecodeDone() {
        mQueue.dispatch(() -> {
            mVideoEncoder.signalEndOfInputStream();
        });
    }

    /**
     * {@link GeneratorCallback}
     */
    private static final class GeneratorCallback implements Handler.Callback {

        private static final int MSG_BASE = 0;

        private static final int MSG_START_GENERATING = MSG_BASE + 1;

        private static final int MSG_RELEASE = MSG_BASE + 2;

        private final WeakReference<WatermarkGenerator2> mRef;

        private GeneratorCallback(WatermarkGenerator2 generator) {
            mRef = new WeakReference<>(generator);
        }

        @Override
        public boolean handleMessage(Message msg) {
            final WatermarkGenerator2 generator = mRef.get();
            if (generator == null) {
                return false;
            }

            switch (msg.what) {
                case MSG_START_GENERATING:
                    generator.generateInternal(msg.arg1, ((Uri) msg.obj));
                    return true;

                case MSG_RELEASE:
                    generator.releaseInternal();
                    return true;
            }

            return false;
        }
    }

    /**
     * {@link OutputTexture}
     */
    private static class OutputTexture {
        private static final short[] INDICES = {0, 1, 2, 0, 2, 3};

        private static final float[] TEX_VERTICES = {
                1.0f, -1.0f, 1.0f, 1.0f,
                -1.0f, -1.0f, 0.0f, 1.0f,
                -1.0f, 1.0f, 0.0f, 0.0f,
                1.0f, 1.0f, 1.0f, 0.0f
        };

        private AddBlendProgram mProgram;

        private final Context mContext;

        private final int mVideoTextureId;

        private final int mWatermarkTextureId;

        private int mWidth;

        private int mHeight;

        public OutputTexture(Context context, int videoTextureId, int watermarkTextureId) {
            mContext = context.getApplicationContext();
            mVideoTextureId = videoTextureId;
            mWatermarkTextureId = watermarkTextureId;
        }

        public void init(int width, int height) {
            mWidth = width;
            mHeight = height;

            mProgram = new AddBlendProgram(mContext, TEX_VERTICES, INDICES);
            mProgram.setFirstTextureName(GL_TEXTURE2);
            mProgram.setFirstTextureNameId(2);
            mProgram.setSecondTextureName(GL_TEXTURE3);
            mProgram.setSecondTextureNameId(3);
        }

        public void draw() {
            glClearColor(0f, 0f, 0f, 0f);
            glClear(GL_COLOR_BUFFER_BIT);
            glViewport(0, 0, mWidth, mHeight);
            mProgram.draw(mVideoTextureId, mWatermarkTextureId);
        }
    }

    /**
     * {@link WatermarkGeneratorNew.VideoTexture}
     */
    private static class VideoTexture {
        private static final short[] INDICES = {0, 1, 2, 0, 2, 3};

        private static final float[] TEX_VERTICES = {
                1.0f, -1.0f, 1.0f, 1.0f,
                -1.0f, -1.0f, 0.0f, 1.0f,
                -1.0f, 1.0f, 0.0f, 0.0f,
                1.0f, 1.0f, 1.0f, 0.0f
        };

        //        private TextureProgram mTextureProgram;
        private FullFrameRect mFullFrameRect;

        private Context mContext;

        private final int mVideoTextureId;

        private int mWidth;

        private int mHeight;

        public VideoTexture(Context context, int videoTextureId) {
            mContext = context.getApplicationContext();
            mVideoTextureId = videoTextureId;
        }

        public void init(int width, int height) {
            mWidth = width;
            mHeight = height;

//            mTextureProgram = new TextureProgram(mContext, TEXTURE_2D, TEX_VERTICES, INDICES);
//            mTextureProgram.setTextureName(GL_TEXTURE0);
//            mTextureProgram.setTextureNameId(0);

            mFullFrameRect = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
        }

        public void draw(float[] transform) {
//            glClearColor(0f, 0f, 0f, 1f);
//            glClear(GL_COLOR_BUFFER_BIT);
//            glViewport(0, 0, mWidth, mHeight);
            mFullFrameRect.drawFrame(mVideoTextureId, transform);
//            glBindFramebuffer(GL_FRAMEBUFFER, 0);
        }
    }

    /**
     * {@link WatermarkGeneratorNew.WatermarkTexture}
     */
    private static class WatermarkTexture {
        private static final short[] INDICES = {0, 1, 2, 0, 2, 3};

        private static final float[] VERTICES = {
                1.0f, -1.0f, 1.0f, 1.0f,
                -1.0f, -1.0f, 0.0f, 1.0f,
                -1.0f, 1.0f, 0.0f, 0.0f,
                1.0f, 1.0f, 1.0f, 0.0f
        };

        private TextureProgram mTextureProgram;

        private Context mContext;

        private int mWatermarkTextureId;

        private int mWidth;

        private int mHeight;

        public WatermarkTexture(Context context, int watermarkTextureId) {
            mContext = context.getApplicationContext();
            mWatermarkTextureId = watermarkTextureId;
        }

        public void init(int width, int height) {
            mWidth = width;
            mHeight = height;

            mTextureProgram = new TextureProgram(mContext, TextureProgram.TEXTURE_EXT, VERTICES, INDICES);
            mTextureProgram.setTextureName(GL_TEXTURE1);
            mTextureProgram.setTextureNameId(1);
        }

        public void draw() {
            glClearColor(0f, 0f, 0f, 0f);
            glClear(GL_COLOR_BUFFER_BIT);
            glViewport(0, 0, mWidth, mHeight);
            mTextureProgram.draw(mWatermarkTextureId);
        }
    }
}
