package net.kajos.holokilo;

import android.opengl.GLES20;

/**
 * Created by kajos on 10-9-15.
 */
public class OpenGLLog {
    public static void checkGLError() {
        if (!Config.ENABLE_OPENGL_ERROR) return;

        int errorValue = GLES20.glGetError();

        if (errorValue != GLES20.GL_NO_ERROR) {
            //if (Display.isCreated()) Display.destroy();
            throw new RuntimeException("OpenGL error: " + errorValue);
        }
    }
}
