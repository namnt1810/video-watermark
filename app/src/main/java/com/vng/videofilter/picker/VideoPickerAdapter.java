package com.vng.videofilter.picker;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.vng.videofilter.LocalVideoProperty;
import com.vng.videofilter.R;
import com.vng.videofilter.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author namnt4
 * @since 6/26/2018
 */
public class VideoPickerAdapter extends RecyclerView.Adapter<VideoPickerAdapter.VideoHolder> {

    private final List<LocalVideoProperty> mItems = new ArrayList<>();

    private int mSelectedPosition = 0;

    private OnVideoClickListener mVideoClickListener;

    private View mLastSelectedView;

    @Override
    public VideoHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_video_picker, parent, false);
        return new VideoHolder(view);
    }

    @Override
    public void onBindViewHolder(VideoHolder holder, int position) {
        holder.bindData(mItems.get(position));
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    public LocalVideoProperty getItem(int position) {
        if (0 <= position && position < getItemCount()) {
            return mItems.get(position);
        }

        return null;
    }

    public void setData(List<LocalVideoProperty> items) {
        mItems.clear();

        if (items != null) {
            mItems.addAll(items);
        }

        notifyDataSetChanged();
    }

    public void setVideoClickListener(OnVideoClickListener videoClickListener) {
        mVideoClickListener = videoClickListener;
    }

    private void deselectLastSelectedItem() {
        if (mLastSelectedView != null) {
            mLastSelectedView.setSelected(false);
        }
    }

    /**
     * {@link VideoHolder}
     */
    protected final class VideoHolder extends RecyclerView.ViewHolder {

        private ImageView mThumbnailView;

        private TextView mDurationView;

        public VideoHolder(View itemView) {
            super(itemView);

            mThumbnailView = itemView.findViewById(R.id.thumbnail);
            mDurationView = itemView.findViewById(R.id.duration);

            itemView.setOnClickListener(view -> {
                deselectLastSelectedItem();
                view.setSelected(!view.isSelected());
                mLastSelectedView = view;
                mSelectedPosition = getAdapterPosition();
                invokeVideoClickListener();
            });
        }

        public void bindData(LocalVideoProperty videoProperty) {
            mThumbnailView.setImageBitmap(videoProperty.getThumbnail());
            mDurationView.setText(StringUtils.formatVideoDuration(videoProperty.getDuration()));

            if (mSelectedPosition == getAdapterPosition()) {
                itemView.setSelected(true);
                mLastSelectedView = itemView;
                invokeVideoClickListener();
            } else {
                itemView.setSelected(false);
            }
        }

        private void invokeVideoClickListener() {
            if (mVideoClickListener != null) {
                mVideoClickListener.onVideoClick(getItem(mSelectedPosition));
            }
        }
    }

    /**
     * {@link OnVideoClickListener}
     */
    public interface OnVideoClickListener {
        void onVideoClick(LocalVideoProperty videoProperty);
    }
}
