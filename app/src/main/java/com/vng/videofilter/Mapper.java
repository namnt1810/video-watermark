package com.vng.videofilter;

/**
 * @author thuannv
 * @since 17/08/2017
 */
public interface Mapper<From, To> {
    To map(From from);
}
