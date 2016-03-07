package net.kajos.holokilo;

import android.content.Context;
import android.view.MotionEvent;
import net.kajos.holokilo.Orientation.Tracker;
import net.kajos.holokilo.Orientation.Trackers.DummyTracker;
import net.kajos.holokilo.Orientation.Trackers.GoogleHeadTracker;

public class GLSurfaceView extends android.opengl.GLSurfaceView
{
    public GLRenderer renderer = null;

    public GLSurfaceView(Context context, Tracker tracker)
    {
        super(context);

        setEGLContextClientVersion(2);

        renderer = new GLRenderer(this, tracker);
        setRenderer(renderer);
        setRenderMode(Config.RENDER_WHEN_DIRTY ? android.opengl.GLSurfaceView.RENDERMODE_WHEN_DIRTY :  android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    public GLSurfaceView(Context context)
    {
        super(context);

        setEGLContextClientVersion(2);

        Tracker tracker = Config.DUMMY_TRACKER ? new DummyTracker() : GoogleHeadTracker.createFromContext(context);

        renderer = new GLRenderer(this, tracker);
        setRenderer(renderer);
        setRenderMode(Config.RENDER_WHEN_DIRTY ? android.opengl.GLSurfaceView.RENDERMODE_WHEN_DIRTY :  android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    public GLRenderer getRenderer()
    {
        return renderer;
    }

    public boolean touched = false;
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getAction() & MotionEvent.ACTION_MASK)
        {
            case MotionEvent.ACTION_DOWN:
                if (!touched) {
                }
                touched = true;
                break;

            case MotionEvent.ACTION_UP:
                touched = false;
                renderer.initBase();
        }
        return true;
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
