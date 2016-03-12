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

    // Function accessible to unity
    public static UnityRenderer getInstance() {
        // Holokilo renderer not yet ready
        if (instance.getGPUProcessor() == null)
            return null;

        // If a semaphore is used, the unity renderer will
        // not render faster than the holokilo renderer.
        if (Config.UNITY_SEMAPHORE) {
            try {
                waitSem.acquire();
            } catch (InterruptedException e) {
                // Fine, render anyway then
            }
        }
        return instance;
    }

    // Function accessible to unity
    public boolean isStopped() {
        return isStopped;
    }

    private int unityTextureID = -1;

    // Function accessible to unity
    public void setUnityTextureID(int tex) {
        unityTextureID = tex;
        Log.d(Config.TAG, "Received unity texture: " + tex);
    }

    // Function accessible to unity
    public float[] getAmbientColor() {
        return getGPUProcessor().getAmbientColor();
    }

    // Function accessible to unity
    public boolean hasCardboard() {
        return Config.USE_CARDBOARD;
    }

    private static Semaphore waitSem = new Semaphore(0);
    public void allowRender() {
        waitSem.drainPermits();
        waitSem.release();
    }

    // Function accessible to unity
    // View matrix of left eye
    public float[] getViewMatrixLeft() {
        return getGPUProcessor().getViewMatrixLeft();
    }

    // Function accessible to unity
    // Viewmatrices of left and right eyes.
    public float[] getViewMatrixBoth() {
        return getGPUProcessor().getViewMatrixBoth();
    }

    // Function accessible to unity
    // Get translations of blobs relative to camera.
    // Can be used for handheld trackers for example.
    public float[] getTranslations() {
        return getGPUProcessor().getTranslations();
    }

    // Function accessible to unity
    // Give fov for unity to render with.
    public float getSceneVFov() {
        return getGPUProcessor().getSceneVFov();
    }

    // Function accessible to unity
    // Give fov for unity to render with.
    public float getSceneAspect() {
        return getGPUProcessor().getSceneAspect();
    }

    @Override
    protected void drawScene() {
        setViewport();
        // Draw the camera image as sbs or fullscreen.
        if (Config.USE_CARDBOARD) {
            getGPUProcessor().drawStereo();
        } else {
            getGPUProcessor().draw();
        }
        if (getGPUProcessor() != null && unityPlayer.surfaceReady()) {
            // Draw the texture retrieved from unity, as an overlay
            // over the camera image.
            if (unityTextureID != -1) {
                getGPUProcessor().drawTexture(unityTextureID);
            }
            // Allow unity to render another frame
            if (Config.UNITY_SEMAPHORE) {
                allowRender();
            }
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
