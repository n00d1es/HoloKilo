package net.kajos.holokilo;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * Created by kajos on 28-9-15.
 */
// A texture meant for stricly uploading data to GPU
public class UploadTexture {
    public int width, height;

    public IntBuffer textureId = IntBuffer.allocate(1);
    public UploadTexture(int width, int height) {
        this.width = width;
        this.height = height;

        GLES20.glGenTextures(1, textureId);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId.get(0));
        // Width and height do not have to be a power of two
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_ALPHA,
                width, height,
                0, GLES20.GL_ALPHA, GLES20.GL_UNSIGNED_BYTE, null);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
    }

    public void upload(ByteBuffer buffer) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId.get(0));
        // Width and height do not have to be a power of two
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_ALPHA,
                width, height,
                0, GLES20.GL_ALPHA, GLES20.GL_UNSIGNED_BYTE, buffer);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }
}
