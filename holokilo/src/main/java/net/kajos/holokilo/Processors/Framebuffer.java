package net.kajos.holokilo.Processors;

import android.opengl.GLES20;
import android.util.Log;
import net.kajos.holokilo.Config;

import java.nio.IntBuffer;

/**
 * Created by kajos on 10-9-15.
 */
public class Framebuffer {
    public int width;
    public int height;

    public IntBuffer textureId = IntBuffer.allocate(1);
    public IntBuffer framebufferId = IntBuffer.allocate(1);

    public Framebuffer(int width, int height) {
        this(width, height, GLES20.GL_RGBA);
    }

    public Framebuffer(int width, int height, int textureType) {
        Log.d(Config.TAG, "Framebuffer created.");

        this.width = width;
        this.height = height;

        GLES20.glGenFramebuffers(1, framebufferId);

        GLES20.glGenTextures(1, textureId);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId.get(0));
        // Width and height do not have to be a power of two
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, textureType,
                width, height,
                0, textureType, GLES20.GL_UNSIGNED_BYTE, null);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebufferId.get(0));
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, textureId.get(0), 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);

        if ( status != GLES20.GL_FRAMEBUFFER_COMPLETE )
        {
            Log.d(Config.TAG, "Framebuffer incomplete");
        }
    }

    public void bind() {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebufferId.get(0));
        GLES20.glViewport(0, 0, width, height);
    }

    public void unbind() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }
}
