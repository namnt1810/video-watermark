package com.vng.videofilter.picker;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.MediaStore;

import com.vng.videofilter.App;
import com.vng.videofilter.BasePresenter;
import com.vng.videofilter.IoUtils;
import com.vng.videofilter.LocalVideoProperty;
import com.vng.videofilter.SimpleSubscriber;

import java.util.ArrayList;
import java.util.List;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

/**
 * @author namnt4
 * @since 6/26/2018
 */
public class VideoPickerPresenter extends BasePresenter<VideoPickerView> {

    private static final String[] mProjection = {
            MediaStore.Video.VideoColumns._ID,
            MediaStore.MediaColumns.DATA,
            MediaStore.Video.VideoColumns.SIZE,
            MediaStore.Video.VideoColumns.DURATION};

    private CompositeSubscription mCompositeSubscription;

    public VideoPickerPresenter() {
        mCompositeSubscription = new CompositeSubscription();
    }

    public void getAllVideoFiles() {
        Subscription subscription = Observable.defer(() -> Observable.just(getListVideoProperty()))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new ListVideoSubscriber());

        mCompositeSubscription.add(subscription);
    }

    private List<LocalVideoProperty> getListVideoProperty() {
        List<LocalVideoProperty> ret = new ArrayList<>();

        List<LocalVideoProperty> listVideos = getListVideos();
        Observable.from(listVideos)
                .map(this::getThumbnailOfVideo)
                .filter(this::isMp4Video)
                .subscribe(new SimpleSubscriber<LocalVideoProperty>() {
                    @Override
                    public void onNext(LocalVideoProperty videoProperty) {
                        ret.add(videoProperty);
                    }
                });

        return ret;
    }

    private List<LocalVideoProperty> getListVideos() {
        List<LocalVideoProperty> listVideoProperty = new ArrayList<>();

        final String orderBy = MediaStore.Video.Media.DATE_MODIFIED + " DESC";

        Cursor cursor;
        cursor = App.getInstance().getApplicationContext().getContentResolver()
                .query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        mProjection,
                        null,
                        null,
                        orderBy);

        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    LocalVideoProperty videoProperty = Mappers.CURSOR_TO_VIDEO_PROPERTY_MAPPER.map(cursor);
                    listVideoProperty.add(videoProperty);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                IoUtils.safeClose(cursor);
            }
        }

        return listVideoProperty;
    }

    private LocalVideoProperty getThumbnailOfVideo(LocalVideoProperty videoProperty) {
        Bitmap thumbnail = MediaStore.Video.Thumbnails.getThumbnail(App.getInstance().getContentResolver(),
                videoProperty.getVideoId(),
                MediaStore.Video.Thumbnails.MINI_KIND,
                new BitmapFactory.Options());
        videoProperty.setThumbnail(thumbnail);

        return videoProperty;
    }

    private boolean isMp4Video(LocalVideoProperty videoProperty) {
        String path = videoProperty.getPath();
        int lastIndexOf = path.lastIndexOf(".");
        String extension = path.substring(lastIndexOf + 1, path.length());
        boolean isMp4Video = "mp4".equals(extension);
        return isMp4Video;
    }

    /**
     * {@link ListVideoSubscriber}
     */
    private class ListVideoSubscriber extends SimpleSubscriber<List<LocalVideoProperty>> {
        @Override
        public void onNext(List<LocalVideoProperty> localVideoProperties) {
            if (isViewAttached()) {
                mView.showListVideos(localVideoProperties);
            }
        }
    }
}
