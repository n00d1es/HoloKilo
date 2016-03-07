package net.kajos.holokilo.Unity;

import android.opengl.EGL14;
import android.opengl.GLES10;
import android.opengl.GLES20;
import android.util.Log;
import android.view.Display;
import android.widget.FrameLayout;
import net.kajos.holokilo.Config;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

/**
 * Created by Kajos on 21-12-2015.
 */
public class UnityPlayer extends com.unity3d.player.UnityPlayer {
    private UnityARPlayerActivity activity;
    private UnitySurfaceView surface = null;
    private FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(1, 1);

    public UnityPlayer(UnityARPlayerActivity activity) {
        super(activity);
        this.activity = activity;
    }

    @Override
    protected int[] initCamera(int var1, int var2, int var3, int var4) {
        return null;
    }

    @Override
    public void init(int var1, boolean var2) {
        Log.d(Config.TAG, "Test");
    }

    int majorVersionUnity = -1;

    @Override
    protected void executeGLThreadJobs() {
        EGL10 egl = ((EGL10)EGLContext.getEGL());
        final EGLContext con = egl.eglGetCurrentContext();

        if (majorVersionUnity == -1 && !con.equals(EGL10.EGL_NO_CONTEXT)) {
            String versionString = GLES10.glGetString(GLES10.GL_VERSION);

            for (int i = 0; i < versionString.length(); i++) {
                int cha = versionString.charAt(i);
                if (Character.isDigit(cha)) {
                    majorVersionUnity = Character.getNumericValue(cha);
                    break;
                }
            }
            Log.d(Config.TAG, "GL Version: " + majorVersionUnity);
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (con.equals(EGL10.EGL_NO_CONTEXT)) {
                    // no current context.
                } else if (surface == null) {
                    surface = new UnitySurfaceView(activity, con, majorVersionUnity);

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // Do nothing
                    }

                    activity.getLayout().addView(surface);
                    UnityPlayer.this.setLayoutParams(layoutParams);
                } else if (surface.getEglContext().hashCode() != con.hashCode()) {
                    removeView(surface);
                    surface.onPause();
                    activity.getLayout().removeView(surface);

                    surface = new UnitySurfaceView(activity, con, majorVersionUnity);
                    activity.getLayout().addView(surface, 0);
                }
            }
        });
    }

    public boolean surfaceReady() {
        return surface != null;
    }

    public void initBase() {
        if (surface != null) {
            surface.getRenderer().initBase();
        }
    }

    @Override
    public void pause() {
        if (surface != null) {
            surface.onPause();
        }
        super.pause();
    }

    @Override
    public void quit() {
        if (surface != null) {
            surface.onPause();
        }
        super.quit();
    }

    @Override
    public void resume() {
        if (surface != null) {
            surface.onResume();
        }
        super.resume();
    }
}
