package com.vng.videofilter;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * @author namnt4
 * @since 6/26/2018
 */
public class LocalVideoProperty implements Parcelable {
    private String mPath;

    private int mSize;

    private int mDuration;

    private long mVideoId;

    private Bitmap mThumbnail;

    public LocalVideoProperty() {
    }

    public void setPath(String path) {
        mPath = path;
    }

    public void setSize(int size) {
        mSize = size;
    }

    public void setDuration(int duration) {
        mDuration = duration;
    }

    public void setVideoId(long videoId) {
        mVideoId = videoId;
    }

    public String getPath() {
        return mPath;
    }

    public int getSize() {
        return mSize;
    }

    public int getDuration() {
        return mDuration;
    }

    public long getVideoId() {
        return mVideoId;
    }

    public Bitmap getThumbnail() {
        return mThumbnail;
    }

    public void setThumbnail(Bitmap thumbnail) {
        mThumbnail = thumbnail;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.mPath);
        dest.writeInt(this.mSize);
        dest.writeInt(this.mDuration);
        dest.writeLong(this.mVideoId);
        dest.writeParcelable(this.mThumbnail, flags);
    }

    protected LocalVideoProperty(Parcel in) {
        this.mPath = in.readString();
        this.mSize = in.readInt();
        this.mDuration = in.readInt();
        this.mVideoId = in.readLong();
        this.mThumbnail = in.readParcelable(Bitmap.class.getClassLoader());
    }

    public static final Creator<LocalVideoProperty> CREATOR = new Creator<LocalVideoProperty>() {
        @Override
        public LocalVideoProperty createFromParcel(Parcel source) {
            return new LocalVideoProperty(source);
        }

        @Override
        public LocalVideoProperty[] newArray(int size) {
            return new LocalVideoProperty[size];
        }
    };
}
