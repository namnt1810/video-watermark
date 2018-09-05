package com.vng.videofilter.gles;

import java.util.Arrays;

import static android.opengl.GLES20.GL_ARRAY_BUFFER;
import static android.opengl.GLES20.GL_ELEMENT_ARRAY_BUFFER;
import static android.opengl.GLES20.GL_STATIC_DRAW;
import static android.opengl.GLES20.glBindBuffer;
import static android.opengl.GLES20.glBufferData;
import static android.opengl.GLES20.glDeleteBuffers;
import static android.opengl.GLES20.glGenBuffers;
import static com.vng.videofilter.gles.BufferUtils.SIZE_OF_FLOAT;
import static com.vng.videofilter.gles.BufferUtils.SIZE_OF_SHORT;
import static com.vng.videofilter.gles.BufferUtils.asFloatBuffer;
import static com.vng.videofilter.gles.BufferUtils.asShortBuffer;

/**
 * @author thuannv
 * @since 21/06/2017
 */
public abstract class GLModel {

    protected float[] mVertices;

    protected short[] mIndices;

    protected int mVertexBufferId;

    protected int mIndexBufferId;

    public GLModel(float[] vertices, short[] indices) {
        mVertices = Arrays.copyOfRange(vertices, 0, vertices.length);
        mIndices = Arrays.copyOfRange(indices, 0, indices.length);
        setupVertexBuffer();
        setupIndexBuffer();
    }

    private void setupVertexBuffer() {
        int[] buffers = new int[1];
        glGenBuffers(1, buffers, 0);
        mVertexBufferId = buffers[0];
        glBindBuffer(GL_ARRAY_BUFFER, mVertexBufferId);
        glBufferData(GL_ARRAY_BUFFER, mVertices.length * SIZE_OF_FLOAT, asFloatBuffer(mVertices), GL_STATIC_DRAW);
    }

    private void setupIndexBuffer() {
        int[] buffers = new int[1];
        glGenBuffers(1, buffers, 0);
        mIndexBufferId = buffers[0];
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, mIndexBufferId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, mIndices.length * SIZE_OF_SHORT, asShortBuffer(mIndices), GL_STATIC_DRAW);
    }

    public void releaseBuffers() {
        glDeleteBuffers(2, new int[] { mVertexBufferId, mIndexBufferId }, 0);
    }

    protected abstract int vertexStride();

    protected abstract int coordsPerVertex();
}
