package com.vng.videofilter.picker;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import com.vng.videofilter.LocalVideoProperty;
import com.vng.videofilter.R;
import com.vng.videofilter.watermark.WatermarkGenerator;

import java.util.List;

/**
 * @author namnt4
 * @since 6/25/2018
 */
public class VideoPickerActivity extends AppCompatActivity implements VideoPickerView {

    private RecyclerView mRecyclerView;

    private View mNextButton;

    private VideoPickerAdapter mAdapter;

    private VideoPickerPresenter mPresenter;

    private LocalVideoProperty mCurrentSelectedVideo;

    private WatermarkGenerator mWatermarkGenerator;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_picker);

        initView();

        initPresenter();

        mWatermarkGenerator = WatermarkGenerator.with(null);
    }

    @Override
    protected void onDestroy() {
        mWatermarkGenerator.release();
        super.onDestroy();
    }

    @Override
    public void showListVideos(List<LocalVideoProperty> localVideoProperties) {
        mAdapter.setData(localVideoProperties);
    }

    public void onNextClick() {
        if (mCurrentSelectedVideo != null) {
            mWatermarkGenerator.setSource(Uri.parse(mCurrentSelectedVideo.getPath()));
            mWatermarkGenerator.generate();
        }
    }

    private void initView() {
        mAdapter = new VideoPickerAdapter();
        mAdapter.setVideoClickListener(this::setCurrentSelectedVideo);

        mRecyclerView = findViewById(R.id.videos_list);
        mRecyclerView.setLayoutManager(new GridLayoutManager(this, 3, LinearLayoutManager.VERTICAL, false));
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setWillNotDraw(false);
        mRecyclerView.setAdapter(mAdapter);

        mNextButton = findViewById(R.id.next);
        mNextButton.setOnClickListener(v -> onNextClick());
    }

    private void setCurrentSelectedVideo(LocalVideoProperty videoProperty) {
        mCurrentSelectedVideo = videoProperty;
    }

    private void initPresenter() {
        mPresenter = new VideoPickerPresenter();
        mPresenter.attachView(this);
        mPresenter.getAllVideoFiles();
    }
}
