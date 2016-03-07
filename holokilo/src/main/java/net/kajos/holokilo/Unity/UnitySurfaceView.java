package net.kajos.holokilo.Unity;

import android.opengl.EGL14;
import android.util.Log;
import net.kajos.holokilo.Config;
import net.kajos.holokilo.Orientation.Tracker;
import net.kajos.holokilo.Orientation.Trackers.DummyTracker;
import net.kajos.holokilo.Orientation.Trackers.GoogleHeadTracker;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

/**
 * Created by Kajos on 17-12-2015.
 */
public class UnitySurfaceView extends android.opengl.GLSurfaceView
{
    private EGLContext eglContext = null;

    private UnityRenderer renderer;

    public EGLContext getEglContext() {
        return eglContext;
    }

    public UnitySurfaceView(UnityARPlayerActivity context, EGLContext sharedContext, final int version)
    {
        super(context);
        this.eglContext = sharedContext;

        Tracker tracker = Config.DUMMY_TRACKER ? new DummyTracker() : GoogleHeadTracker.createFromContext(context);
        renderer = new UnityRenderer(this, tracker, context.getUnityPlayer());

        setEGLContextFactory(new EGLContextFactory () {
            @Override
            public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig eglConfig) {
                int[] attrib_list = { EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE };
                EGLContext context = egl.eglCreateContext(display, eglConfig, eglContext,
                        attrib_list);

                return context;
            }

            @Override
            public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
                if (!egl.eglDestroyContext(display, context)) {
                    Log.e(Config.TAG, "display:" + display + " context: " + context);
                }
            }
        });

        setRenderer(renderer);
        setRenderMode(Config.RENDER_WHEN_DIRTY ? android.opengl.GLSurfaceView.RENDERMODE_WHEN_DIRTY :  android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    public UnityRenderer getRenderer()
    {
        return renderer;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (renderer != null)
            renderer.pause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (renderer != null)
            renderer.resume();
    }
}
