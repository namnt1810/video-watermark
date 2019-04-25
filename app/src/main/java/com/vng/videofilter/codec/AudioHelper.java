package com.vng.videofilter.codec;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.util.Log;

/**
 * Created by taitt on 11/09/2017.
 */

public class AudioHelper {

    public static byte[] mono2Stereo(byte[] left, byte[] right) {
        if (right == null || left.length != right.length) right = left;

        byte[] rs = new byte[left.length * 2];

        for (int i = 0; i < left.length - 1; i = i + 2) {
            rs[2 * i] = left[i];
            rs[2 * i + 1] = left[i + 1];
            rs[2 * i + 2] = right[i];
            rs[2 * i + 3] = right[i + 1];
        }
        return rs;
    }

    public static byte[] mix(byte[] track1, byte[] track2) {
        if (track2 == null || track1.length != track2.length) return track1;

        for (int i = 0; i < track1.length - 1; i = i + 2) {
            short short1 = (short) ((track1[i] & 0xff) << 8 | (track1[i + 1] & 0xff));
            short short2 = (short) ((track2[i] & 0xff) << 8 | (track2[i + 1] & 0xff));
            if (short2 == 0) continue;

            float samplef1 = short1 / 32768.0f;
            float samplef2 = short2 / 32768.0f;
            float mixed = samplef1 + samplef2;

            // reduce the volume a bit:
            mixed *= 0.8;

            // hard clipping
            if (mixed > 1.0f) mixed = 1.0f;
            if (mixed < -1.0f) mixed = -1.0f;
            short outputSample = (short) (mixed * 32768.0f);

            track1[i] = (byte) ((outputSample >> 8) & 0xFF);
            track1[i + 1] = (byte) ((outputSample >> 0) & 0xFF);
        }

        return track1;
    }

    public static void testValidSampleRates(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        String rate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        String size = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
        Log.e("AudioHelper", "PROPERTY_OUTPUT_FRAMES_PER_BUFFER :" + size + " & PROPERTY_OUTPUT_SAMPLE_RATE: " + rate);

        final int validSampleRates[] = new int[] { 8000, 11025, 16000, 22050,
                32000, 37800, 44056, 44100, 48000, 47250, 4800, 50000, 50400, 88200,
                96000, 176400, 192000, 352800, 2822400, 5644800 };
        for (int r : validSampleRates) {
            int bufferSize = AudioRecord.getMinBufferSize(r, AudioFormat.CHANNEL_CONFIGURATION_DEFAULT, AudioFormat.ENCODING_PCM_16BIT);
            if (bufferSize > 0) {
                Log.e("AudioHelper", r + "Hz is supported by the hardware");
            } else {
                Log.e("AudioHelper", r + "Hz is not supported by the hardware");
            }
        }
    }
}
