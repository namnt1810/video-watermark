package com.vng.videofilter.picker;

import android.database.Cursor;
import android.provider.MediaStore;

import com.vng.videofilter.LocalVideoProperty;
import com.vng.videofilter.Mapper;

/**
 * @author namnt4
 * @since 6/26/2018
 */
final class Mappers {
    public static final Mapper<Cursor, LocalVideoProperty> CURSOR_TO_VIDEO_PROPERTY_MAPPER = cursor -> {
        LocalVideoProperty videoProperty = new LocalVideoProperty();

        try {
            long videoId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns._ID));
            videoProperty.setVideoId(videoId);
            String path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA));
            videoProperty.setPath(path);
            int size = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.SIZE));
            videoProperty.setSize(size);
            int duration = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION));
            videoProperty.setDuration(duration);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return videoProperty;
    };
}
