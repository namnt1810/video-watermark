package com.vng.videofilter.watermark;

import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import android.view.Surface;

import com.vng.videofilter.App;
import com.vng.videofilter.codec.MuxerWrapper;
import com.vng.videofilter.gles.AddBlendProgram;
import com.vng.videofilter.gles.EglCore;
import com.vng.videofilter.gles.GlUtil;
import com.vng.videofilter.gles.TextureProgram;
import com.vng.videofilter.gles.WindowSurface;
import com.vng.videofilter.util.DispatchQueue;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_FRAMEBUFFER;
import static android.opengl.GLES20.GL_TEXTURE0;
import static android.opengl.GLES20.GL_TEXTURE1;
import static android.opengl.GLES20.GL_TEXTURE2;
import static android.opengl.GLES20.GL_TEXTURE3;
import static android.opengl.GLES20.glBindFramebuffer;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glClearColor;
import static android.opengl.GLES20.glViewport;
import static com.vng.videofilter.gles.TextureProgram.TEXTURE_2D;

public class WatermarkGeneratorNew implements SurfaceTexture.OnFrameAvailableListener,
        WatermarkVideoEncoder.EncoderCallback {
    private static final String TAG = WatermarkGeneratorNew.class.getSimpleName();

    private final DispatchQueue mQueue;

    private final MediaMetadataRetriever mRetriever = new MediaMetadataRetriever();

    private final MediaExtractor mExtractor = new MediaExtractor();

    private DecoderWrapper mDecoder;

    private WatermarkVideoEncoder mEncoder;

    private EglCore mEglCore;

    private Surface mInputSurface;

    private SurfaceTexture mSurfaceTexture;

    private WindowSurface mOutputSurface;

    private VideoTexture mVideoTexture;

    private WatermarkTexture mWatermarkTexture;

    private OutputTexture mOutputTexture;

    private int mTextureId;

    private int mWidth;

    private int mHeight;

    private MuxerWrapper mMuxerWrapper;

    private WatermarkProvider mWatermarkProvider;

    private String mOutputFilePath;

    private int mVideoTrackIndex = -1;

    private int mStreamId = 0;

    private int mBufferSize = -1;

    public WatermarkGeneratorNew() {
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
        setSource(source);
        setupMuxer();
//        processNonVideoTracks();
        processVideoTrack();
    }

    private void setSource(Uri sourceUri) {
        try {
            mRetriever.setDataSource(sourceUri.getPath());
            mExtractor.setDataSource(App.getInstance(), sourceUri, null);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_hhmmss", Locale.getDefault());
            mOutputFilePath = Environment.getExternalStorageDirectory().getPath() + "/360Live/watermark_" + sdf.format(new Date()) + ".mp4";
            mMuxerWrapper = new MuxerWrapper(mOutputFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setupMuxer() {
        // Set up the orientation and starting time for extractor.
        String degreesString = mRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        if (degreesString != null) {
            int degrees = Integer.parseInt(degreesString);
            if (degrees >= 0) {
                mMuxerWrapper.setOrientationHint(degrees);
            }
        }
    }

    private void processNonVideoTracks() {
        int trackCount = mExtractor.getTrackCount();
        boolean hasNonVideoTracks = false;

        // Set up the tracks and retrieve the max buffer size for selected
        // tracks.
        List<Integer> tracksList = new ArrayList<>();
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = mExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (!mime.startsWith("video/")) {
                hasNonVideoTracks = true;
                tracksList.add(i);
//                mExtractor.selectTrack(i);
                mMuxerWrapper.addTrack(i, format);
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    int newSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                    mBufferSize = newSize > mBufferSize ? newSize : mBufferSize;
                }
            }
        }

        if (!hasNonVideoTracks) {
            return;
        }

        if (mBufferSize < 0) {
            mBufferSize = 1024;
        }

        //TODO: write others tracks after write video data

        // Copy the samples from MediaExtractor to MediaMuxer. We will loop
        // for copying each sample and stop when we get to the end of the source
        // file or exceed the end time of the trimming.
//        int offset = 0;
//        int sourceTrackIndex;
//        ByteBuffer dstBuf = ByteBuffer.allocate(mBufferSize);
//        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
//
//        while (true) {
//            bufferInfo.offset = offset;
//            bufferInfo.size = mExtractor.readSampleData(dstBuf, offset);
//            if (bufferInfo.size < 0) {
//                bufferInfo.size = 0;
//                break;
//            } else {
//                bufferInfo.presentationTimeUs = mExtractor.getSampleTime();
//                bufferInfo.flags = mExtractor.getSampleFlags();
//                sourceTrackIndex = mExtractor.getSampleTrackIndex();
//
//                mMuxerWrapper.writeSampleData(sourceTrackIndex, dstBuf, bufferInfo);
//                mExtractor.advance();
//            }
//        }
//
//        for (Integer index : tracksList) {
//            mExtractor.unselectTrack(index);
//        }
    }

    private void processVideoTrack() {
        boolean hasVideoTrack = false;

        int trackCount = mExtractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            MediaFormat trackFormat = mExtractor.getTrackFormat(i);
            String mime = trackFormat.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                hasVideoTrack = true;
                mVideoTrackIndex = i;
            }
        }

        if (hasVideoTrack) {
            mExtractor.selectTrack(mVideoTrackIndex);
            MediaFormat trackFormat = mExtractor.getTrackFormat(mVideoTrackIndex);
            prepareCodec(trackFormat);
        } else {
            releaseInternal();
        }
    }


    private void prepareCodec(MediaFormat format) {
        mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);
        mWidth = format.getInteger(MediaFormat.KEY_WIDTH);
        mHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
        mTextureId = GlUtil.genFrameBufferTexture(mWidth, mHeight);
        mSurfaceTexture = new SurfaceTexture(mTextureId);
        mSurfaceTexture.setOnFrameAvailableListener(this);
        mInputSurface = new Surface(mSurfaceTexture);

        createEncoder(format);

        mOutputSurface = new WindowSurface(mEglCore, mEncoder.getInputSurface(), true);
        mOutputSurface.makeCurrent();

//        mVideoTexture = new VideoTexture(App.getInstance(), mTextureId);
//        mVideoTexture.init(mWidth, mHeight);

        mWatermarkProvider = new WatermarkImageProvider(mStreamId);
        mWatermarkProvider.setWidth(mWidth);
        mWatermarkProvider.setHeight(mHeight);
        Integer watermarkTextureId = mWatermarkProvider.provide();

        mWatermarkTexture = new WatermarkTexture(App.getInstance(), watermarkTextureId);
        mWatermarkTexture.init(mWidth, mHeight);
//
//        mOutputTexture = new OutputTexture(App.getInstance(), mTextureId, watermarkTextureId);
//        mOutputTexture.init(mWidth, mHeight);

        mDecoder = new DecoderWrapper(mQueue);
        Thread decoderThread = new Thread(() -> {
            try {
                mDecoder.configure(mExtractor, format, mInputSurface);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        decoderThread.start();
        try {
            decoderThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void createEncoder(MediaFormat format) {
        MediaFormat encoderFormat = createEncoderFormat(format);

        mEncoder = new WatermarkVideoEncoder(mQueue,
                encoderFormat.getInteger(MediaFormat.KEY_WIDTH),
                encoderFormat.getInteger(MediaFormat.KEY_HEIGHT),
                (int) (4 * 1000 * 1000),
                MediaCodecInfo.CodecProfileLevel.AVCProfileHigh444,
                60,
                10);
//        mEncoder = new WatermarkVideoEncoder(mQueue, format);

        mEncoder.setCallback(this);
        mEncoder.start();
    }

    private MediaFormat createEncoderFormat(MediaFormat format) {
        MediaFormat encoderFormat = new MediaFormat();

        int bps = format.containsKey(MediaFormat.KEY_BIT_RATE) ?
                format.getInteger(MediaFormat.KEY_BIT_RATE) : 0;

        int profile = format.containsKey(MediaFormat.KEY_PROFILE) ?
                format.getInteger(MediaFormat.KEY_PROFILE) : MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline;

        int fps = format.containsKey(MediaFormat.KEY_FRAME_RATE) ?
                Math.max(format.getInteger(MediaFormat.KEY_FRAME_RATE), 30) : 30;

        int keyFrameInterval = format.containsKey(MediaFormat.KEY_I_FRAME_INTERVAL) ?
                format.getInteger(MediaFormat.KEY_I_FRAME_INTERVAL) : 5;

        encoderFormat.setString(MediaFormat.KEY_MIME, format.getString(MediaFormat.KEY_MIME));
        encoderFormat.setInteger(MediaFormat.KEY_WIDTH, format.getInteger(MediaFormat.KEY_WIDTH));
        encoderFormat.setInteger(MediaFormat.KEY_HEIGHT, format.getInteger(MediaFormat.KEY_HEIGHT));
        encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, bps);
        encoderFormat.setInteger(MediaFormat.KEY_PROFILE, profile);
        encoderFormat.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        encoderFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, keyFrameInterval);
//        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
//        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        return encoderFormat;
    }

    private void releaseInternal() {
        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
        }

        if (mDecoder != null) {
            mDecoder.release();
        }

        if (mEncoder != null) {
            mEncoder.release();
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

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        mDecoder.markExecuting();
        long pts = surfaceTexture.getTimestamp();
        Log.d(TAG, "onFrameAvailable(), pts = " + pts / 1000);
        surfaceTexture.updateTexImage();
//        mVideoTexture.draw();
        mWatermarkTexture.draw();
//        mOutputTexture.draw();
        mOutputSurface.setPresentationTime(pts);
        boolean b = mOutputSurface.swapBuffers();
    }

    @Override
    public void onOutputFormatChanged(MediaFormat outputFormat) {
        Log.d(TAG, "onOutputFormatChanged(), format = " + outputFormat.toString());
        mMuxerWrapper.addTrack(mVideoTrackIndex, outputFormat);
    }

    @Override
    public void onOutputData(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
        Log.d(TAG, String.format("onOutputData(), size = %d, pts = %d, flag = %d", bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags));
//        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            // The codec config data was pulled out and fed to the muxer when we got
            // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
//            bufferInfo.size = 0;
//        }

        mMuxerWrapper.writeSampleData(mVideoTrackIndex, byteBuffer, bufferInfo);
        if (mDecoder.hasReachEOS()  && bufferInfo.presentationTimeUs == mDecoder.getLastPts()) {
            releaseInternal();
        } else {
            mDecoder.advance();
        }

    }

    /**
     * {@link GeneratorCallback}
     */
    private static final class GeneratorCallback implements Handler.Callback {

        private static final int MSG_BASE = 0;

        private static final int MSG_START_GENERATING = MSG_BASE + 1;

        private static final int MSG_RELEASE = MSG_BASE + 2;

        private final WeakReference<WatermarkGeneratorNew> mRef;

        private GeneratorCallback(WatermarkGeneratorNew generator) {
            mRef = new WeakReference<>(generator);
        }

        @Override
        public boolean handleMessage(Message msg) {
            final WatermarkGeneratorNew generator = mRef.get();
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
     * {@link VideoTexture}
     */
    private static class VideoTexture {
        private static final short[] INDICES = {0, 1, 2, 0, 2, 3};

        private static final float[] TEX_VERTICES = {
                1.0f, -1.0f, 1.0f, 1.0f,
                -1.0f, -1.0f, 0.0f, 1.0f,
                -1.0f, 1.0f, 0.0f, 0.0f,
                1.0f, 1.0f, 1.0f, 0.0f
        };

        private TextureProgram mTextureProgram;

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

            mTextureProgram = new TextureProgram(mContext, TEXTURE_2D, TEX_VERTICES, INDICES);
            mTextureProgram.setTextureName(GL_TEXTURE0);
            mTextureProgram.setTextureNameId(0);
        }

        public void draw() {
            glClearColor(0f, 0f, 0f, 1f);
            glClear(GL_COLOR_BUFFER_BIT);
            glViewport(0, 0, mWidth, mHeight);
            mTextureProgram.draw(mVideoTextureId);
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
        }
    }

    /**
     * {@link WatermarkTexture}
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
