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

        // Initialize cameratexture and start camera
        if (isCameraStopped()) {
            cameraTexture = createCameraTexture();
            startCamera(getCameraTexture(), this.width, this.height, Config.EXPOSURE_LOCK);
        }
        if (gpuProcessor != null)
            gpuProcessor.destroy();
        // Remove found blobs, they're useless now
        BlobFinder.reset();
        // Create gpuprocessor with the created cameratexture
        // Use screensize and camera size as parameters, fov as well
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
        // External texture so that camera image is directly
        // fed in GPU. Needs a GLES1.1 extention.
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
    public void startCamera(int texture, int screenWidth, int screenHeight, boolean exposureLock) {
        surface = new SurfaceTexture(texture);
        surface.setOnFrameAvailableListener(this);

        camera = Camera.open();
        params = camera.getParameters();

        int[] range = params.getSupportedPreviewFpsRange().get(params.getSupportedPreviewFpsRange().size()-1);
        framerate = range[1];
        params.setPreviewFpsRange(range[0], range[1]);
        // Android cameraframerate is given in framerate * 1000
        framerate = 1000000 / framerate;
        framerateFilter.set(framerate);
        Log.d(Config.TAG, "Max framerate camera: " + framerate);


        // Need minimal exposure, as else the RR will not stand out enough.
        params.setRecordingHint(true);
        List<Camera.Size> sizes = params.getSupportedPreviewSizes();
        // Find a resolution supported by camera with maximum size of given params.
        // Take the highest resolution below given max.
        Size frameSize = maxCameraFrameSize(sizes, screenWidth / Config.CAMERA_SCALE, screenHeight);
        camera.setParameters(params);

        Log.d(Config.TAG, "Set video size to " + frameSize.width + "x" + frameSize.height);
        params.setPreviewSize(frameSize.width, frameSize.height);

        // No autobanding, it's bad for this purpose.
        params.setAntibanding(Camera.Parameters.ANTIBANDING_OFF);
        // No zoom of course
        params.setZoom(0);
        // Focus mode set by config. Don't want to change focus mode when
        // viewing in AR at least.
        params.setFocusMode(Config.FOCUS_MODE);
        // Set whitebalance according config. Might be worth trying to poke it
        // a bit, but wont help much.
        params.setWhiteBalance(Config.WHITE_BALANCE);

        // Minimum exposure compensation is best in my experience.
        // Used only when exposure lock is off.
        // When exposurelock is off, holokilo might work better in daylight.
        params.setExposureCompensation(params.getMinExposureCompensation());

        // Video stabilization might help with performance.
        // It might give wrong results in accordance with non corrected gyro,
        // but haven't had notable problems yet.
        if (params.isVideoStabilizationSupported())
            params.setVideoStabilization(true);

        // Check first, required or might crash on some devices probably.
        exposureLockSupported = params.isAutoExposureLockSupported();
        whiteBalanceLockSupported = params.isAutoWhiteBalanceLockSupported();

        if (exposureLock) {
            if (exposureLockSupported)
                params.setAutoExposureLock(true);
            if (whiteBalanceLockSupported)
                params.setAutoWhiteBalanceLock(true);
        }

        try {
            // Set parameters before preview, this is important
            // for exposurelock.
            camera.setParameters(params);
            // Give camera to opengl texture
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

    // Set exposurelock while running app.
    // Might be handy to have a day/night mode toggle as preference option.
    public void setExposureLock(boolean state) {
        closeCamera();
        startCamera(getCameraTexture(), this.width, this.height, state);
    }

    private static volatile boolean doFlash = false;
    // Call this function when another flash is required.
    // This will happen when blobs are lost or gained.
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
                        // A flash is required.
                        if (doFlash) {
                            // A stable frame is a frame that is not on the edge of a flash (i.e. half frame is lighted).
                            // A non stable frame is a frame where light is not certain to be on or off. These frames
                            // will be coupled and used together which will result in a lighted but smeared frame, but still
                            // useful for tracking x,y of blobs (not for blob size so much).
                            gpuProcessor.doStableFrame(false);
                            gpuProcessor.doCompareSizes(true);
                            // Turn flash off
                            flashOff();
                            // Sleep to make sure they captured frame with flash off will not not be half lighted.
                            Thread.sleep((int) (Config.BLINK_TIME * (double) framerate));
                            if (!isRunning)
                                return;

                            // Catch the frame where flash is certain to be completely off.
                            gpuProcessor.catchFlashlessFrame();
                            if (!isRunning)
                                return;

                            // Turn flash on.
                            flashOn();

                            // Sleep to make sure frame will be completely lighted.
                            Thread.sleep((int) (Config.BLINK_TIME * (double) framerate));

                            if (!isRunning)
                                return;

                            // From now on frames are stably lit again.
                            gpuProcessor.doStableFrame(true);
                            gpuProcessor.doCompareSizes(true);

                            // Put a minimum time between flashes to ensure there will be no
                            // stroboscopic effect.
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
        // Torch mode allows settings flash fast
        params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
        // Use previous params so getParameters will not be called,
        // it will add latency.
        camera.setParameters(params);
    }

    private void flashOff() {
        params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        camera.setParameters(params);
    }

    // Camera size that is used.
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
