package com.vng.videofilter.gles;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

/**
 * @author thuannv
 * @since 21/06/2017
 */
public final class BufferUtils {

    public static final int SIZE_OF_SHORT = 2;

    public static final int SIZE_OF_INT = 4;

    public static final int SIZE_OF_FLOAT = 4;

    public static final int SIZE_OF_DOUBLE = 8;

    public static final int SIZE_OF_LONG = 8;


    private BufferUtils() {
    }

    public static FloatBuffer newFloatBuffer(int numFloats) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(numFloats * SIZE_OF_FLOAT);
        buffer.order(ByteOrder.nativeOrder());
        return buffer.asFloatBuffer();
    }

    public static DoubleBuffer newDoubleBuffer(int numDoubles) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(numDoubles * SIZE_OF_DOUBLE);
        buffer.order(ByteOrder.nativeOrder());
        return buffer.asDoubleBuffer();
    }

    public static ByteBuffer newByteBuffer(int numBytes) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(numBytes);
        buffer.order(ByteOrder.nativeOrder());
        return buffer;
    }

    public static ShortBuffer newShortBuffer(int numShorts) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(numShorts * SIZE_OF_SHORT);
        buffer.order(ByteOrder.nativeOrder());
        return buffer.asShortBuffer();
    }

    public static CharBuffer newCharBuffer(int numChars) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(numChars * 2);
        buffer.order(ByteOrder.nativeOrder());
        return buffer.asCharBuffer();
    }

    public static IntBuffer newIntBuffer(int numInts) {
        ByteBuffer buffer = ByteBuffer.allocate(numInts * SIZE_OF_INT);
        buffer.order(ByteOrder.nativeOrder());
        return buffer.asIntBuffer();
    }

    public static LongBuffer newLongBuffer(int numLongs) {
        ByteBuffer buffer = ByteBuffer.allocate(numLongs * SIZE_OF_LONG);
        buffer.order(ByteOrder.nativeOrder());
        return buffer.asLongBuffer();
    }

    public static FloatBuffer asFloatBuffer(float[] buffer) {
        FloatBuffer fb = ByteBuffer.allocateDirect(buffer.length * SIZE_OF_FLOAT)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        fb.put(buffer);
        fb.position(0);
        return fb;
    }

    public static ShortBuffer asShortBuffer(short[] buffer) {
        ShortBuffer sb = ByteBuffer.allocateDirect(buffer.length * SIZE_OF_SHORT)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer();
        sb.put(buffer);
        sb.position(0);
        return sb;
    }

    public static IntBuffer asIntBuffer(int[] buffer) {
        IntBuffer ib = ByteBuffer.allocateDirect(buffer.length * SIZE_OF_INT)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer();
        ib.put(buffer);
        ib.position(0);
        return ib;
    }

    public static LongBuffer asLongBuffer(long[] buffer) {
        LongBuffer lb = ByteBuffer.allocateDirect(buffer.length * SIZE_OF_LONG)
                .order(ByteOrder.nativeOrder())
                .asLongBuffer();
        lb.put(buffer);
        lb.position(0);
        return lb;
    }

    public static DoubleBuffer asDoubleBuffer(double[] buffer) {
        DoubleBuffer lb = ByteBuffer.allocateDirect(buffer.length * SIZE_OF_LONG)
                .order(ByteOrder.nativeOrder())
                .asDoubleBuffer();
        lb.put(buffer);
        lb.position(0);
        return lb;
    }
}
