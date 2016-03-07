package net.kajos.holokilo;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;
import net.kajos.holokilo.Orientation.Tracker;
import net.kajos.holokilo.Processors.BlobFinder;
import net.kajos.holokilo.Processors.GPUProcessor;
import net.kajos.holokilo.Util.LowPassFilter;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.List;

public class GLRenderer implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener
{
    private GPUProcessor gpuProcessor;
    private int cameraTexture = -1;
    private Camera camera;
    private SurfaceTexture surface;
    private volatile boolean isRunning = false;

    private Tracker tracker = null;
    private GLSurfaceView view;

    public GLRenderer(GLSurfaceView view, Tracker tracker)
    {
        this.view = view;
        this.tracker = tracker;

        tracker.startTracking();

        Log.d(Config.TAG, "Created renderer");
    }

    public GPUProcessor getGPUProcessor() {
        return gpuProcessor;
    }

    public int getCameraTexture() {
        return cameraTexture;
    }

    public void onSurfaceCreated(GL10 unused, EGLConfig config)
    {
        Log.d(Config.TAG, "Surface created");
    }

    private LowPassFilter fpsLP = new LowPassFilter(0.1f);
    public int getFPS() {
        return (int)(1000f / fpsLP.get());
    }

    private int counter = 0;
    private long prevTime = -1;

    private volatile boolean frameAvailable = false;

    public void onDrawFrame(GL10 unused) {
        if (Config.RENDER_WHEN_DIRTY || frameAvailable) {
            frameAvailable = false;

            surface.updateTexImage();
            gpuProcessor.cameraToFramebuffer();
            gpuProcessor.doProcessing();
        }

        drawScene();

        OpenGLLog.checkGLError();

        // Draw result
        long time = System.currentTimeMillis();
        fpsLP.get(time - prevTime);
        prevTime = time;
        if (counter++ > 10) {
            Log.d(Config.TAG, "FPS: " + getFPS());
            counter = 0;
        }
    }

    protected void drawScene() {
        setViewport();
        if (Config.USE_CARDBOARD) {
            gpuProcessor.drawStereo();
        } else {
            gpuProcessor.draw();
        }
    }

    public int getScreenWidth() {
        return width;
    }

    public int getScreenHeight() {
        return height;
    }

    protected int width, height;
    public void onSurfaceChanged(GL10 unused, int width, int height)
    {
        Log.d(Config.TAG, "Surface changed");

        this.width = Config.SCREEN_OVERRIDE_WIDTH > 0 ? Config.SCREEN_OVERRIDE_WIDTH : width;
        this.height = Config.SCREEN_OVERRIDE_HEIGHT > 0 ? Config.SCREEN_OVERRIDE_HEIGHT : height;

        setViewport();

        if (isCameraStopped()) {
            cameraTexture = createCameraTexture();
            startCamera(getCameraTexture(), this.width, this.height);
        }
        if (gpuProcessor != null)
            gpuProcessor.destroy();
        BlobFinder.reset();
        gpuProcessor = new GPUProcessor(tracker, this.width, this.height, getCameraWidth(), getCameraHeight(), getCameraHFov(), getCameraVFov(), cameraTexture);
    }

    public void initBase() {
        if (gpuProcessor == null)
            return;

        gpuProcessor.initBase();
    }

    public void setViewport() {
        GLES20.glViewport(0, 0, width, height);
    }

    static public int loadShader(int type, String shaderCode)
    {
        int shader = GLES20.glCreateShader(type);

        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        IntBuffer compile = IntBuffer.allocate(1);
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compile);
        if (compile.get(0) == GLES20.GL_FALSE) {
            Log.e(Config.TAG, "Error:");
            Log.e(Config.TAG, shaderCode);
            Log.e(Config.TAG, "Fault:");
            printLog(shader);
            return 0;
        }

        return shader;
    }

    static void printLog(int shader) {

        IntBuffer logLength = IntBuffer.allocate(1);
        if (GLES20.glIsShader(shader)) {
            GLES20.glGetShaderiv(shader, GLES20.GL_INFO_LOG_LENGTH, logLength);
        } else if (GLES20.glIsProgram(shader)) {
            GLES20.glGetProgramiv(shader, GLES20.GL_INFO_LOG_LENGTH, logLength);
        } else {
            Log.e(Config.TAG, "printlog: Not a shader or a program");
            return;
        }

        String result = "";
        if (GLES20.glIsShader(shader))
            result = GLES20.glGetShaderInfoLog(shader);
        else if (GLES20.glIsProgram(shader))
            result = GLES20.glGetProgramInfoLog(shader);

        Log.e(Config.TAG, result);

    }

    public static int createCameraTexture()
    {
        int[] texture = new int[1];

        GLES20.glGenTextures(1,texture, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0]);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MIN_FILTER,GL10.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);

        return texture[0];
    }

    public void frameAvailable() {
        frameAvailable = true;
    }

    public void pause() {
        tracker.stopTracking();
        gpuProcessor.destroy();
        closeCamera();
    }

    public void resume() {
        tracker.startTracking();
        // Camera is started by onSurfaceChanged
    }

    public void destroy() {
        tracker.stopTracking();
        gpuProcessor.destroy();
        closeCamera();
    }

    // Camera
    
    private int framerate = 0;
    private LowPassFilter framerateFilter = new LowPassFilter(0.1f);
    private boolean exposureLockSupported = false;
    private boolean whiteBalanceLockSupported = false;
    private Camera.Parameters params = null;
    public void startCamera(int texture, int screenWidth, int screenHeight) {
        surface = new SurfaceTexture(texture);
        surface.setOnFrameAvailableListener(this);

        camera = Camera.open();
        params = camera.getParameters();

        int[] range = params.getSupportedPreviewFpsRange().get(params.getSupportedPreviewFpsRange().size()-1);
        framerate = range[1];
        params.setPreviewFpsRange(range[0], range[1]);
        framerate = 1000000 / framerate;
        framerateFilter.set(framerate);
        Log.d(Config.TAG, "Max framerate camera: " + framerate);


        // Need minimal exposure, as else the RR will not stand out enough.
        params.setRecordingHint(true);
        List<Camera.Size> sizes = params.getSupportedPreviewSizes();
        Size frameSize = maxCameraFrameSize(sizes, screenWidth / Config.CAMERA_SCALE, screenHeight);
        camera.setParameters(params);

        Log.d(Config.TAG, "Set video size to " + frameSize.width + "x" + frameSize.height);
        params.setPreviewSize(frameSize.width, frameSize.height);

        params.setVideoStabilization(true);
        // For my purpose I don't need antibanding..
        params.setAntibanding(Camera.Parameters.ANTIBANDING_OFF);
        params.setZoom(0);
        // Focus mode
        params.setFocusMode(Config.FOCUS_MODE);
        params.setWhiteBalance(Config.WHITE_BALANCE);

        params.setExposureCompensation(params.getMinExposureCompensation());
        if (params.isVideoStabilizationSupported())
            params.setVideoStabilization(true);

        exposureLockSupported = params.isAutoExposureLockSupported();
        whiteBalanceLockSupported = params.isAutoWhiteBalanceLockSupported();

        if (Config.EXPOSURE_LOCK) {
            if (exposureLockSupported)
                params.setAutoExposureLock(true);
            if (whiteBalanceLockSupported)
                params.setAutoWhiteBalanceLock(true);
        }

        try {
            camera.setParameters(params);
            camera.setPreviewTexture(surface);
            camera.startPreview();
        } catch (IOException ioe) {
            Log.e(Config.TAG, "Camera failure");
        }

        Log.d(Config.TAG, params.flatten());

        if (hasFlash()) {
            flashOn();
            startFlash();
        }
    }

    private static volatile boolean doFlash = false;
    public static void requestFlash() {
        doFlash = true;
    }

    private Thread flashThread = null;
    private void startFlash() {
        isRunning = true;

        flashThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    doFlash = false;
                    Thread.sleep((int) (Config.BLINK_TIME * (double) framerate));
                    int counter = 0;
                    while(isRunning) {
                        if (doFlash) {
                            gpuProcessor.doStableFrame(false);
                            gpuProcessor.doCompareSizes(true);
                            flashOff();
                            Thread.sleep((int) (Config.BLINK_TIME * (double) framerate));
                            if (!isRunning)
                                return;

                            gpuProcessor.catchFlashlessFrame();
                            if (!isRunning)
                                return;

                            flashOn();
                            Thread.sleep((int) (Config.BLINK_TIME * (double) framerate));

                            if (!isRunning)
                                return;

                            gpuProcessor.doStableFrame(true);
                            gpuProcessor.doCompareSizes(true);
                            Thread.sleep((int) (Config.MIN_STABLE_TIME * (double) framerate));
                            doFlash = false;
                            counter = 0;
                        } else {
                            Thread.sleep(10);
                            counter++;
                        }
                        // Forced flash interval
                        if (gpuProcessor != null && counter == Config.FORCED_FLASH_INTERVAL) {
                            requestFlash();
                        }
                    }
                } catch (Exception e) {
                    Log.e(Config.TAG, "Camera: " + e.toString());
                    e.printStackTrace();
                }
                Log.d(Config.TAG, "Flashthread ended.");
            }
        });
        flashThread.setPriority(Thread.MAX_PRIORITY);
        flashThread.start();
    }

    public float getCameraHFov() {
        return camera.getParameters().getHorizontalViewAngle();
    }

    public float getCameraVFov() {
        return camera.getParameters().getVerticalViewAngle();
    }

    private boolean hasFlash(){
        Camera.Parameters params = camera.getParameters();
        List<String> flashModes = params.getSupportedFlashModes();
        if(flashModes == null) {
            Log.e(Config.TAG, "No flash!");
            return false;
        }

        for(String flashMode : flashModes) {
            if(Camera.Parameters.FLASH_MODE_TORCH.equals(flashMode)) {
                return true;
            }
        }

        Log.e(Config.TAG, "No flash!");
        return false;
    }

    private void flashOn() {
        params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
        camera.setParameters(params);
    }

    private void flashOff() {
        params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        camera.setParameters(params);
    }

    protected int calcWidth = 0;
    protected int calcHeight = 0;

    protected Camera getCamera() { return camera; }

    public int getCameraWidth() {
        return calcWidth;
    }

    public int getCameraHeight() {
        return calcHeight;
    }

    protected Size maxCameraFrameSize(List<Camera.Size> supportedSizes, int maxWidth, int maxHeight) {
        calcWidth = 0;
        calcHeight = 0;

        for (Camera.Size size : supportedSizes) {
            int width = size.width;
            int height = size.height;

            if (width <= maxWidth && height <= maxHeight && width > calcWidth && height > calcHeight) {
                calcWidth = width;
                calcHeight = height;
            }
        }

        return new Size(calcWidth, calcHeight);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        if (gpuProcessor != null && !gpuProcessor.blobFinderFramerate.isEmpty())
            framerate = (int)gpuProcessor.blobFinderFramerate.get();

        frameAvailable();
        if (Config.RENDER_WHEN_DIRTY)
            view.requestRender();

    }

    public boolean isCameraStopped() {
        return camera == null;
    }

    private void closeCamera() {
        Log.d(Config.TAG, "closeCamera");
        isRunning = false;
        if (camera != null) {
            flashOff();
            camera.stopPreview();
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
        }
    }
}
