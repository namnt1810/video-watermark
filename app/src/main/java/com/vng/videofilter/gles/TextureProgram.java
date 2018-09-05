package com.vng.videofilter.gles;

import android.content.Context;
import android.opengl.GLES11Ext;
import android.support.annotation.IntDef;

import com.vng.videofilter.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static android.opengl.GLES20.GL_ARRAY_BUFFER;
import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_ELEMENT_ARRAY_BUFFER;
import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.GL_TEXTURE_MAG_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_MIN_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_S;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_T;
import static android.opengl.GLES20.GL_TRIANGLES;
import static android.opengl.GLES20.GL_UNSIGNED_SHORT;
import static android.opengl.GLES20.glActiveTexture;
import static android.opengl.GLES20.glBindBuffer;
import static android.opengl.GLES20.glBindTexture;
import static android.opengl.GLES20.glDrawElements;
import static android.opengl.GLES20.glGenTextures;
import static android.opengl.GLES20.glTexParameterf;
import static com.vng.videofilter.gles.BufferUtils.SIZE_OF_FLOAT;
import static com.vng.videofilter.gles.GlUtil.readRawResourceShaderFile;

/**
 * @author thuannv
 * @since 26/06/2017
 */
public class TextureProgram extends GLModel {

    public static final int TEXTURE_2D = 0;

    public static final int TEXTURE_EXT = 1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef( {TEXTURE_2D, TEXTURE_EXT} )
    public @interface ProgramType {}


    private @ProgramType int mProgramType;

    private int mTextureTarget;

    private ShaderProgram mShader;

    private int mTextureName;

    private int mTextureNameId;

    public TextureProgram(Context context, @ProgramType int programType, float[] vertices, short[] indices) {
        super(vertices, indices);

        mProgramType = programType;
        String vertexShader, fragmentShader;
        switch (mProgramType) {
            case TEXTURE_2D:
                mTextureTarget = GL_TEXTURE_2D;
                vertexShader = readRawResourceShaderFile(context, R.raw.texture_2d_vertex_shader);
                fragmentShader = readRawResourceShaderFile(context, R.raw.texture_2d_fragment_shader);
                break;

            case TEXTURE_EXT:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                vertexShader = readRawResourceShaderFile(context, R.raw.texture_ext_vertex_shader);
                fragmentShader = readRawResourceShaderFile(context, R.raw.texture_ext_fragment_shader);
                break;

            default:
                throw new IllegalArgumentException("Unknown program type. Acceptable program types is: TEXTURE_2D or TEXTURE_EXT");
        }
        mShader = new ShaderProgram(vertexShader, fragmentShader);
    }

    public void setTextureName(int textureName) {
        mTextureName = textureName;
    }

    public void setTextureNameId(int textureNameId) {
        mTextureNameId = textureNameId;
    }

    public int getProgramHandle() {
        return mShader.getProgramHandle();
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

    public int createTextureOES() {
        int[] textures = new int[1];
        glGenTextures(1, textures, 0);
        glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);
        glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return textures[0];
    }

    public void draw(int textureId) {
        mShader.begin();

        mShader.enableVertexAttribute("a_Position");
        mShader.enableVertexAttribute("a_TextureCoord");

        glActiveTexture(mTextureName);
        glBindTexture(mTextureTarget, textureId);
        mShader.setUniformi("s_Texture", mTextureNameId);

        mShader.setVertexAttribute("a_Position", coordsPerVertex(), GL_FLOAT, false, vertexStride(), 0);
        mShader.setVertexAttribute("a_TextureCoord", texCoordsPerVertext(), GL_FLOAT, false, vertexStride(), coordsPerVertex() * SIZE_OF_FLOAT);

        glBindBuffer(GL_ARRAY_BUFFER, mVertexBufferId);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, mIndexBufferId);
        glDrawElements(GL_TRIANGLES, mIndices.length, GL_UNSIGNED_SHORT, 0);

        glBindTexture(mTextureTarget, 0);

        mShader.disableVertexAttribute("a_Position");
        mShader.disableVertexAttribute("a_TextureCoord");

        mShader.end();
    }

    public void release() {
        if (mShader != null) {
            mShader.destroy();
            mShader = null;
        }
    }
}
