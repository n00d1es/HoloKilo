package net.kajos.holokilo.Unity;

import android.opengl.GLSurfaceView;
import android.util.Log;
import net.kajos.holokilo.Config;
import net.kajos.holokilo.GLRenderer;
import net.kajos.holokilo.Orientation.Tracker;

import java.util.concurrent.Semaphore;

/**
 * Created by Kajos on 18-12-2015.
 */
public class UnityRenderer extends GLRenderer {
    private static UnityRenderer instance = null;

    private UnityPlayer unityPlayer;
    private boolean isStopped = false;

    public UnityRenderer(GLSurfaceView view, Tracker tracker, UnityPlayer unityPlayer) {
        super(view, tracker);
        this.unityPlayer = unityPlayer;
        instance = this;
    }

    public static UnityRenderer getInstance() {
        try {
            waitSem.acquire();
        } catch (InterruptedException e) {
            // Fine, render anyway then
        }
        return instance;
    }

    public boolean isStopped() {
        return isStopped;
    }

    private int unityTextureID = -1;

    public void setUnityTextureID(int tex) {
        unityTextureID = tex;
        Log.d(Config.TAG, "Received unity texture: " + tex);
    }

    public float[] getAmbientColor() {
        return getGPUProcessor().getAmbientColor();
    }

    public boolean hasCardboard() {
        return Config.USE_CARDBOARD;
    }

    private static Semaphore waitSem = new Semaphore(0);
    public void allowRender() {
        waitSem.drainPermits();
        waitSem.release();
    }

    public float[] getViewMatrixLeft() {
        return getGPUProcessor().getViewMatrixLeft();
    }

    public float[] getViewMatrixBoth() {
        return getGPUProcessor().getViewMatrixBoth();
    }

    public float[] getTranslations() {
        return getGPUProcessor().getTranslations();
    }

    public float getSceneVFov() {
        return getGPUProcessor().getSceneVFov();
    }

    public float getSceneAspect() {
        return getGPUProcessor().getSceneAspect();
    }

    @Override
    protected void drawScene() {
        setViewport();
        if (Config.USE_CARDBOARD) {
            getGPUProcessor().drawStereo();
        } else {
            getGPUProcessor().draw();
        }
        if (getGPUProcessor() != null && unityPlayer.surfaceReady()) {
            if (unityTextureID != -1) {
                getGPUProcessor().drawTexture(unityTextureID);
            }
            allowRender();
        }
    }

    @Override
    public void resume() {
        super.resume();
        isStopped = false;
    }

    @Override
    public void pause() {
        super.pause();
        isStopped = true;
        waitSem.release();
    }

    @Override
    public void destroy() {
        super.destroy();
        isStopped = true;
        waitSem.release();
    }
}
