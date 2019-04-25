package com.vng.videofilter.gles;

import android.content.Context;

import com.vng.videofilter.R;

import static android.opengl.GLES20.*;
import static com.vng.videofilter.gles.BufferUtils.SIZE_OF_FLOAT;

public class AddBlendProgram extends GLModel {

    private int mFirstTextureName;

    private int mFirstTextureNameId;

    private int mSecondTextureName;

    private int mSecondTextureNameId;

    private ShaderProgram mShader;

    public AddBlendProgram(Context context, float[] vertices, short[] indices) {
        super(vertices, indices);

        String fragmentShader = GlUtil.readRawResourceShaderFile(context, R.raw.texture_2d_add_blend_fragment_shader);
        String vertexShader = GlUtil.readRawResourceShaderFile(context, R.raw.texture_2d_add_blend_vertex_shader);

        mShader = new ShaderProgram(vertexShader, fragmentShader);
    }

    public void setFirstTextureName(int firstTextureName) {
        mFirstTextureName = firstTextureName;
    }

    public void setFirstTextureNameId(int firstTextureNameId) {
        mFirstTextureNameId = firstTextureNameId;
    }

    public void setSecondTextureName(int secondTextureName) {
        mSecondTextureName = secondTextureName;
    }

    public void setSecondTextureNameId(int secondTextureNameId) {
        mSecondTextureNameId = secondTextureNameId;
    }

    @Override
    protected int vertexStride() {
        return (coordsPerVertex() + texCoordsPerVertext()) * SIZE_OF_FLOAT;
    }

    @Override
    protected int coordsPerVertex() {
        return 2;
    }

    protected int texCoordsPerVertext() {
        return 2;
    }

//    public void draw(int firstTextureId, int secondTextureId) {
//        mShader.begin();
//
//        glActiveTexture(mFirstTextureName);
//        glBindTexture(GL_TEXTURE_2D, firstTextureId);
//
//        glActiveTexture(mSecondTextureName);
//        glBindTexture(GL_TEXTURE_2D, secondTextureId);
//
//        mShader.setUniformi("inputImageTexture", mFirstTextureNameId);
//        mShader.setUniformi("inputImageTexture2", mSecondTextureNameId);
//
//        glDisable(GL_CULL_FACE);
//        glDisable(GL_DEPTH_TEST);
//        glEnable(GL_BLEND);
//        glBlendFunc(GL_ONE, GL_ONE);
//
//        glBindBuffer(GL_ARRAY_BUFFER, mVertexBufferId);
//        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, mIndexBufferId);
//        glDrawElements(GL_TRIANGLES, mIndices.length, GL_UNSIGNED_SHORT, 0);
//
//        mShader.end();
//    }

    public void draw(int firstTextureId, int secondTextureId) {
        mShader.begin();

        glActiveTexture(mFirstTextureName);
        glBindTexture(GL_TEXTURE_2D, firstTextureId);

        glActiveTexture(mSecondTextureName);
        glBindTexture(GL_TEXTURE_2D, secondTextureId);

        mShader.setUniformi("inputImageTexture", mFirstTextureNameId);
        mShader.setUniformi("inputImageTexture2", mSecondTextureNameId);

        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE, GL_ONE);

        glBindBuffer(GL_ARRAY_BUFFER, mVertexBufferId);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, mIndexBufferId);
        glDrawElements(GL_TRIANGLES, mIndices.length, GL_UNSIGNED_SHORT, 0);

        glBindTexture(GL_TEXTURE_2D, 0);

        mShader.end();
    }
}
