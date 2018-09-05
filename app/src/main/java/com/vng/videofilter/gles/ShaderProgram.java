package com.vng.videofilter.gles;

import android.opengl.GLES20;
import android.renderscript.Matrix3f;
import android.renderscript.Matrix4f;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;

/**
 * A shader program encapsulates a vertex and fragment shader pair linked to form a shader program useable with OpenGL ES 2.0.
 * {@see https://github.com/libgdx/libgdx/blob/master/gdx/src/com/badlogic/gdx/graphics/glutils/ShaderProgram.java }
 */
public class ShaderProgram {

    private int mVertexShaderHandle;

    private int mFragmentShaderHandle;

    private int mProgramHandle;

    private final StringBuilder mLogBuilder = new StringBuilder();

    private boolean mIsCompiled;

    private final HashMap<String, Integer> mUniforms = new HashMap<>();

    private final HashMap<String, Integer> mAttributes = new HashMap<>();

    public ShaderProgram(String vertexShader, String fragmentShader) {
        if(vertexShader == null) {
            throw new IllegalArgumentException("vertex shader must not be null");
        }

        if(fragmentShader == null) {
            throw new IllegalArgumentException("fragment shader must not be null");
        }

        compileShaders(vertexShader, fragmentShader);

        if(isCompiled()) {
            fetchAttributes();
            fetchUniforms();
        }
    }

    public int getProgramHandle() {
        return mProgramHandle;
    }

    // is this valid shader program?
    public boolean isValid() {
        return mIsCompiled;
    }

    // start to use shader program
    public void begin() {
        GLES20.glUseProgram(mProgramHandle);
    }

    // end of using shader program
    public void end() {
        GLES20.glUseProgram(0);
    }

    // destroy the shader program
    public void destroy() {
        GLES20.glUseProgram(0);
        GLES20.glDeleteShader(mVertexShaderHandle);
        GLES20.glDeleteShader(mFragmentShaderHandle);
        GLES20.glDeleteProgram(mProgramHandle);
        mLogBuilder.setLength(0);
    }

    private void compileShaders(String vertexShader, String fragmentShader) {
        mVertexShaderHandle = GlUtil.loadShader(GLES20.GL_VERTEX_SHADER, vertexShader);
        mFragmentShaderHandle = GlUtil.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader);

        if(mVertexShaderHandle == -1 || mFragmentShaderHandle == -1) {
            mIsCompiled = false;
            return;
        }

        mProgramHandle = linkProgram(createProgram());
        if(mProgramHandle == -1) {
            mIsCompiled = false;
            return;
        }

        mIsCompiled = true;
    }

    private int createProgram() {
        int program = GLES20.glCreateProgram();
        return program != 0 ? program : -1;
    }

    private int linkProgram(int program) {
        if(program == -1) {
            return -1;
        }

        GLES20.glAttachShader(program, mVertexShaderHandle);
        GLES20.glAttachShader(program, mFragmentShaderHandle);
        GLES20.glLinkProgram(program);

        int[] linked = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
        if(linked[0] == GLES20.GL_FALSE) {
            mLogBuilder.append(GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            return  -1;
        }
        return program;
    }

    private int loadShader(int shaderType, String shaderCode) {
        int shader = GLES20.glCreateShader(shaderType);
        if(shader == 0) {
            return -1;
        }

        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);

        if(compiled[0] == GLES20.GL_FALSE) {
            mLogBuilder.append(GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            return -1;
        }
        return shader;
    }

    public String getLog() {
        if(mIsCompiled) {
            mLogBuilder.append(GLES20.glGetProgramInfoLog(mProgramHandle));
        }
        return mLogBuilder.toString();
    }

    public boolean isCompiled() {
        return mIsCompiled;
    }

    private int fetchAttributeLocation(String name) {
        int location = mAttributes.get(name);
        if(location == -1) {
            location = GLES20.glGetAttribLocation(mProgramHandle, name);
            if(location != -1) {
                mAttributes.put(name, location);
            }
        }
        return location;
    }

    private int fetchUniformLocation(String name) {
        int location = mUniforms.get(name);
        if(location == -1) {
            location = GLES20.glGetUniformLocation(mProgramHandle, name);
            if(location != -1) {
                mUniforms.put(name, location);
            }
        }
        return location;
    }


    private void fetchAttributes() {
        mAttributes.clear();

        IntBuffer params = IntBuffer.allocate(1);
        IntBuffer type = IntBuffer.allocate(1);
        GLES20.glGetProgramiv(mProgramHandle, GLES20.GL_ACTIVE_ATTRIBUTES, params);
        int numAttributes = params.get(0);
        for(int i=0; i<numAttributes; i++) {
            params.compact();
            params.put(0, 1);
            type.clear();
            String name = GLES20.glGetActiveAttrib(mProgramHandle, i, params, type);
            int location = GLES20.glGetAttribLocation(mProgramHandle, name);
            mAttributes.put(name, location);
        }
    }

    private void fetchUniforms() {
        mUniforms.clear();

        IntBuffer params = IntBuffer.allocate(1);
        IntBuffer type = IntBuffer.allocate(1);
        GLES20.glGetProgramiv(mProgramHandle, GLES20.GL_ACTIVE_UNIFORMS, params);
        int numUniform = params.get(0);

        for(int i=0; i<numUniform; i++) {
            params.compact();
            params.put(0, 1);
            type.clear();
            String name = GLES20.glGetActiveUniform(mProgramHandle, i, params, type);
            int location = GLES20.glGetUniformLocation(mProgramHandle, name);
            mUniforms.put(name, location);
        }
    }

    /**
     * @param name name the name of the uniform
     * @return the location of the uniform or -1.
     */
    public int getUniformLocation(String name) {
        return mUniforms.get(name) == null ? -1 : mUniforms.get(name);
    }

    /**
     * @param name the name of the attribute
     * @return the location of the attribute or -1.
     */
    public int getAttributeLocation(String name) {
        return mAttributes.get(name) == null ? -1 : mAttributes.get(name);
    }

    /** @param name the name of the uniform
     * @return whether the uniform is available in the shader */
    public boolean hasUniform(String name) {
        return mUniforms.containsKey(name);
    }

    /** @param name the name of the attribute
     * @return whether the attribute is available in the shader */
    public boolean hasAttribute(String name) {
        return mAttributes.containsKey(name);
    }


    /***********************************************************************************************
     * Set Uniforms and Attributes
     ***********************************************************************************************/

    /** Sets the uniform with the given name.
     *
     * @param name the name of the uniform
     * @param value the value */
    public void setUniformi(String name, int value) {
        int location = fetchUniformLocation(name);
        if(location == -1) {
            return;
        }
        GLES20.glUniform1i(location, value);
    }

    public void setUniformi(int location, int value) {
        if(location == -1) {
            return;
        }
        GLES20.glUniform1i(location, value);
    }

    /** Sets the uniform with the given name.
     *
     * @param name the name of the uniform
     * @param value1 the first value
     * @param value2 the second value */
    public void setUniformi (String name, int value1, int value2) {
        int location = fetchUniformLocation(name);
        if(location == -1) {
            return;
        }
        GLES20.glUniform2i(location, value1, value2);
    }

    public void setUniformi (int location, int value1, int value2) {
        if(location == -1) {
            return;
        }
        GLES20.glUniform2i(location, value1, value2);
    }

    /** Sets the uniform with the given name.
     *
     * @param name the name of the uniform
     * @param value1 the first value
     * @param value2 the second value
     * @param value3 the third value */
    public void glUniform3i (String name, int value1, int value2, int value3) {
        int location = fetchUniformLocation(name);
        if(location == -1) {
            return;
        }
        GLES20.glUniform3i(location, value1, value2, value3);

    }

    public void glUniform3i (int location, int value1, int value2, int value3) {
        if(location != -1) {
            GLES20.glUniform3i(location, value1, value2, value3);
        }
    }

    /** Sets the uniform with the given name.
     *
     * @param name the name of the uniform
     * @param value1 the first value
     * @param value2 the second value
     * @param value3 the third value
     * @param value4 the fourth value */
    public void glUniform4i (String name, int value1, int value2, int value3, int value4) {
        int location = fetchUniformLocation(name);
        if(location == -1) {
            return;
        }
        GLES20.glUniform4i(location, value1, value2, value3, value4);
    }

    public void glUniform4i (int location, int value1, int value2, int value3, int value4) {
        if(location == -1) {
            return;
        }
        GLES20.glUniform4i(location, value1, value2, value3, value4);
    }

    /** Sets the uniform with the given name.
     *
     * @param name the name of the uniform
     * @param value the value */
    public void glUniform1f (String name, float value) {
        int location = fetchUniformLocation(name);
        if(location == -1) {
            return;
        }
        GLES20.glUniform1f(location, value);
    }

    public void glUniform1f (int location, float value) {
        if(location == -1) {
            return;
        }
        GLES20.glUniform1f(location, value);
    }

    /** Sets the uniform with the given name.
     *
     * @param name the name of the uniform
     * @param value1 the first value
     * @param value2 the second value */
    public void glUniform2f (String name, float value1, float value2) {
        int location = fetchUniformLocation(name);
        if(location == -1) {
            return;
        }
        GLES20.glUniform2f(location, value1, value2);
    }

    public void glUniform2f (int location, float value1, float value2) {
        if(location == -1) {
            return;
        }
        GLES20.glUniform2f(location, value1, value2);
    }

    /** Sets the uniform with the given name.
     *
     * @param name the name of the uniform
     * @param value1 the first value
     * @param value2 the second value
     * @param value3 the third value */
    public void glUniform3f (String name, float value1, float value2, float value3) {
        int location = fetchUniformLocation(name);
        if(location == -1) {
            return;
        }
        GLES20.glUniform3f(location, value1, value2, value3);
    }

    public void glUniform3f (int location, float value1, float value2, float value3) {
        if(location == -1) {
            return;
        }
        GLES20.glUniform3f(location, value1, value2, value3);
    }

    /** Sets the uniform with the given name.
     *
     * @param name the name of the uniform
     * @param value1 the first value
     * @param value2 the second value
     * @param value3 the third value
     * @param value4 the fourth value */
    public void glUniform4f (String name, float value1, float value2, float value3, float value4) {
        int location = fetchUniformLocation(name);
        if(location == -1) {
            return;
        }
        GLES20.glUniform4f(location, value1, value2, value3, value4);
    }

    public void glUniform4f (int location, float value1, float value2, float value3, float value4) {
        if(location == -1) {
            return;
        }
        GLES20.glUniform4f(location, value1, value2, value3, value4);
    }

    public void setUniform1fv (String name, float[] values, int offset, int length) {
        int location = fetchUniformLocation(name);
        if(location == -1) {
            return;
        }
        GLES20.glUniform1fv(location, length, values, offset);
    }

    public void setUniform1fv (int location, float[] values, int offset, int length) {
        if(location == -1) {
            return;
        }
        GLES20.glUniform1fv(location, length, values, offset);
    }

    public void setUniform2fv (String name, float[] values, int offset, int length) {
        int location = fetchUniformLocation(name);
        if(location == -1) {
            return;
        }
        GLES20.glUniform2fv(location, length / 2, values, offset);
    }

    public void setUniform2fv (int location, float[] values, int offset, int length) {
        if(location == -1) {
            return;
        }
        GLES20.glUniform2fv(location, length / 2, values, offset);
    }

    public void setUniform3fv (String name, float[] values, int offset, int length) {
        int location = fetchUniformLocation(name);
        if(location == -1) {
            return;
        }
        GLES20.glUniform3fv(location, length / 3, values, offset);
    }

    public void setUniform3fv (int location, float[] values, int offset, int length) {
        if(location == -1) {
            return;
        }
        GLES20.glUniform3fv(location, length / 3, values, offset);
    }

    public void setUniform4fv (String name, float[] values, int offset, int length) {
        int location = fetchUniformLocation(name);
        if(location == -1) {
            return;
        }
        GLES20.glUniform4fv(location, length / 4, values, offset);
    }

    public void setUniform4fv (int location, float[] values, int offset, int length) {
        if(location == -1) {
            return;
        }
        GLES20.glUniform4fv(location, length / 4, values, offset);
    }

    /** Sets the uniform matrix with the given name.
     *
     * @param name the name of the uniform
     * @param matrix the matrix */
    public void setUniformMatrix (String name, Matrix4f matrix) {
        setUniformMatrix(name, matrix, false);
    }

    /** Sets the uniform matrix with the given name.
     *
     * @param name the name of the uniform
     * @param matrix the matrix
     * @param transpose whether the matrix should be transposed */
    public void setUniformMatrix (String name, Matrix4f matrix, boolean transpose) {
        setUniformMatrix(fetchUniformLocation(name), matrix, transpose);
    }

    public void setUniformMatrix (int location, Matrix4f matrix) {
        setUniformMatrix(location, matrix, false);
    }

    public void setUniformMatrix (int location, Matrix4f matrix, boolean transpose) {
        if(location == -1) {
            return;
        }
        GLES20.glUniformMatrix4fv(location, 1, transpose, matrix.getArray(), 0);
    }


    /** Sets the uniform matrix with the given name.
     *
     * @param name the name of the uniform
     * @param matrix the matrix */
    public void setUniformMatrix (String name, Matrix3f matrix) {
        setUniformMatrix(name, matrix, false);
    }

    /** Sets the uniform matrix with the given name.
     *
     * @param name the name of the uniform
     * @param matrix the matrix
     * @param transpose whether the uniform matrix should be transposed */
    public void setUniformMatrix (String name, Matrix3f matrix, boolean transpose) {
        setUniformMatrix(fetchUniformLocation(name), matrix, transpose);
    }

    public void setUniformMatrix (int location, Matrix3f matrix) {
        setUniformMatrix(location, matrix, false);
    }

    public void setUniformMatrix (int location, Matrix3f matrix, boolean transpose) {
        if(location == -1) {
            return;
        }
        GLES20.glUniformMatrix3fv(location, 1, transpose, matrix.getArray(), 0);
    }

    /** Sets an array of uniform matrices with the given name.
     *
     * @param name the name of the uniform
     * @param buffer buffer containing the matrix data
     * @param transpose whether the uniform matrix should be transposed */
    public void setUniformMatrix3fv (String name, FloatBuffer buffer, int count, boolean transpose) {
        buffer.position(0);
        int location = fetchUniformLocation(name);
        if(location == -1) {
            return;
        }
        GLES20.glUniformMatrix3fv(location, count, transpose, buffer);
    }

    /** Sets an array of uniform matrices with the given name.
     *
     * @param name the name of the uniform
     * @param buffer buffer containing the matrix data
     * @param transpose whether the uniform matrix should be transposed */
    public void setUniformMatrix4fv (String name, FloatBuffer buffer, int count, boolean transpose) {
        buffer.position(0);
        int location = fetchUniformLocation(name);
        if(location == -1) {
            return;
        }
        GLES20.glUniformMatrix4fv(location, count, transpose, buffer);
    }

    public void setUniformMatrix4fv (int location, float[] values, int offset, int length) {
        if(location == -1) {
            return;
        }
        GLES20.glUniformMatrix4fv(location, length / 16, false, values, offset);
    }

    public void setUniformMatrix4fv (String name, float[] values, int offset, int length) {
        setUniformMatrix4fv(fetchUniformLocation(name), values, offset, length);
    }

    /** Sets the vertex attribute with the given name.
     *
     * @param name the attribute name
     * @param size the number of components, must be >= 1 and <= 4
     * @param type the type, must be one of GL20.GL_BYTE, GL20.GL_UNSIGNED_BYTE, GL20.GL_SHORT,
     *           GL20.GL_UNSIGNED_SHORT,GL20.GL_FIXED, or GL20.GL_FLOAT. GL_FIXED will not work on the desktop
     * @param normalize whether fixed point data should be normalized. Will not work on the desktop
     * @param stride the stride in bytes between successive mAttributes
     * @param buffer the buffer containing the vertex mAttributes. */
    public void setVertexAttribute (String name, int size, int type, boolean normalize, int stride, Buffer buffer) {
        int location = fetchAttributeLocation(name);
        if (location == -1) {
            return;
        }
        GLES20.glVertexAttribPointer(location, size, type, normalize, stride, buffer);
    }

    public void setVertexAttribute (int location, int size, int type, boolean normalize, int stride, Buffer buffer) {
        if(location == -1) return;
        GLES20.glVertexAttribPointer(location, size, type, normalize, stride, buffer);
    }

    /** Sets the vertex attribute with the given name.
     *
     * @param name the attribute name
     * @param size the number of components, must be >= 1 and <= 4
     * @param type the type, must be one of GL20.GL_BYTE, GL20.GL_UNSIGNED_BYTE, GL20.GL_SHORT,
     *           GL20.GL_UNSIGNED_SHORT,GL20.GL_FIXED, or GL20.GL_FLOAT. GL_FIXED will not work on the desktop
     * @param normalize whether fixed point data should be normalized. Will not work on the desktop
     * @param stride the stride in bytes between successive mAttributes
     * @param offset byte offset into the vertex buffer object bound to GL20.GL_ARRAY_BUFFER. */
    public void setVertexAttribute (String name, int size, int type, boolean normalize, int stride, int offset) {
        int location = fetchAttributeLocation(name);
        if (location == -1) {
            return;
        }
        GLES20.glVertexAttribPointer(location, size, type, normalize, stride, offset);
    }

    public void setVertexAttribute (int location, int size, int type, boolean normalize, int stride, int offset) {
        if(location == -1) {
            return;
        }
        GLES20.glVertexAttribPointer(location, size, type, normalize, stride, offset);
    }

    /** Disables the vertex attribute with the given name
     *
     * @param name the vertex attribute name */
    public void disableVertexAttribute (String name) {
        int location = fetchAttributeLocation(name);
        if (location == -1) {
            return;
        }
        GLES20.glDisableVertexAttribArray(location);
    }

    public void disableVertexAttribute (int location) {
        if(location == -1) {
            return;
        }
        GLES20.glDisableVertexAttribArray(location);
    }

    /** Enables the vertex attribute with the given name
     *
     * @param name the vertex attribute name */
    public void enableVertexAttribute (String name) {
        int location = fetchAttributeLocation(name);
        if (location == -1) return;
        GLES20.glEnableVertexAttribArray(location);
    }

    public void enableVertexAttribute (int location) {
        if(location == -1) return;
        GLES20.glEnableVertexAttribArray(location);
    }

    /** Sets the given attribute
     *
     * @param name the name of the attribute
     * @param value1 the first value
     * @param value2 the second value
     * @param value3 the third value
     * @param value4 the fourth value */
    public void setAttributef (String name, float value1, float value2, float value3, float value4) {
        int location = fetchAttributeLocation(name);
        if(location == -1) return;
        GLES20.glVertexAttrib4f(location, value1, value2, value3, value4);
    }

    public void setAttributef (String name, float value1, float value2, float value3) {
        int location = fetchAttributeLocation(name);
        if(location == -1) return;
        GLES20.glVertexAttrib3f(location, value1, value2, value3);
    }

    public void setAttributef (String name, float value1, float value2) {
        int location = fetchAttributeLocation(name);
        if(location == -1) return;
        GLES20.glVertexAttrib2f(location, value1, value2);
    }
}

