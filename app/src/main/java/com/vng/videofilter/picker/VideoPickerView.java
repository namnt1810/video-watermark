package com.vng.videofilter.picker;

import com.vng.videofilter.LocalVideoProperty;

import java.util.List;

/**
 * @author namnt4
 * @since 6/26/2018
 */
public interface VideoPickerView {
    void showListVideos(List<LocalVideoProperty> localVideoProperties);
}
