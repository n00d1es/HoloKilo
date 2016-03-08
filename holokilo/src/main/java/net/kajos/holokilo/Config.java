package net.kajos.holokilo;

import android.hardware.Camera;
import net.kajos.holokilo.Processors.BlobFinder;

/**
 * Created by kajos on 31-10-15.
 */
public class Config {
    public static final String TAG = "holokilo";

    // Number of blobs allowed: Currently set to red, green and blue
    public static final int MAX_BLOBS = 3;

    // OpenGL error checking
    public static final boolean ENABLE_OPENGL_ERROR = true;

    // Use SBS Cardboard mode
    public static final boolean USE_CARDBOARD = true;

    /*  Frame capture options */

    // Override screen size with set width and height. Used for Unity support
    public static int SCREEN_OVERRIDE_WIDTH = -1;
    public static int SCREEN_OVERRIDE_HEIGHT = -1;
    // Camera width and height is set by screen width and height.
    // Camera scale of 2, gets maximum camera size of half screen width and half screen height.
    public static int CAMERA_SCALE = USE_CARDBOARD ? 2 : 1;
    // Scaler for framebuffer size. Camera width and height is divided by this.
    public static int FBO_SCALE = USE_CARDBOARD ? 1 : 2; // power of two preferably

    // Static value for scaling distance
    public static final float FAR_METER = 10f;

    // Checked when a color is regarded overexposed. Used in shader.
    public static final double OVER_EXPOSURE_PROTECTION = 0.9;

    // Adjust for camera smearing of the blob with use of the gyroscope
    public static final double SMEAR_ADJUSTER = 1000.0;

    // Boundary check for bright values. If cap is fixed CAP_REF is used as boundary.
    public static final boolean CAP_FIXED = false;
    public static final float CAP_REF = 0;
    // Absolute values of dynamic cap. Dynamic cap is determined by the "average" of the frame (sort of).
    public static final float CAP_MIN = 8;
    public static final float CAP_MAX = 254;

    /*  Camera options */

    // Set exposure lock for better results in most conditions, at cost of brightness of image.
    // Setting exposure lock can lead to overexposure upclose.
    // If exposure lock is disabled, minimal exposure compensation is applied.
    public static final boolean EXPOSURE_LOCK = true;
    // Force a flash to find reflective objects, every ... * 10 ms.
    public static final int FORCED_FLASH_INTERVAL = 300; //  * 10 ms
    // Time the flash is turned off. As short as possible but take into account rolling shutter.
    public static final double BLINK_TIME = 1.5;
    // Minimal time in frames the flash is turned on. This prevents excessive flickering.
    public static final double MIN_STABLE_TIME = 10;
    // Sample frames are a sequence of FBOs containing camera images for persistent tracking,
    // when flash is turned off in between flashes.
    public static final int SAMPLE_FRAMES = (int)Math.ceil(BLINK_TIME * 2.0 + 0.99);

    // glReadPixels benefits if FBOs are read out a while later than writing to.
    // However that can introduce extra latency, so turned off now.
    public static final int READ_BUFFER_SIZE = 2;

    /* View options */

    // Following three settings are for debugging views. Ony options enabled at a time.
    // Shows the pixels read back to blobfinder and then reuploaded to texture.
    public static final boolean SHOW_DEBUG = false;
    // Shows the camera output, set true for release.
    // Set to false to show reflectivity.
    public static boolean SHOW_CAMERA = true;
    // Shows texture packed as rgba. Not very useful anymore.
    public static final boolean SHOW_RGBA_RESULT = false;

    // Draw cubes on the markers. Only green possible right now.
    public static boolean DRAW_CUBES = true;
    // Draw a point where the green marker is found. Is handy when debugging latency.
    public static final boolean DRAW_POINT = true;

    // Force to fill the screen for SBS mode.
    public static final boolean CARDBOARD_FILL_SCREEN = true;
    // Cardboard FOV settings. Will be ignoed if fillscreen is true.
    public static final float CARDBOARD_HFOV = 90f;
    public static final float CARDBOARD_VFOV = 90f;

    // Show linear reflectivity. If false, every color above cap is 1 and below is 0 (black).
    public static final boolean SHOW_REFLECTIVITY = false;

    /*  Position tracking options */

    // Use the dummy tracker (handy for outside in tracking)
    public static final boolean DUMMY_TRACKER = false;

    // Only track one color; set to null to disable
    public static final BlobFinder.ColorCode TRACK_CODE = BlobFinder.ColorCode.GREEN;
    // Enable extrapolating with gyroscope.
    public static final boolean DO_GYRO_CORRECTION = !DUMMY_TRACKER && true;
    // Filters for x,y positions
    public static final int SOFTEN_MEDIAN = 1;
    public static final float SOFTEN_LOWPASS = 1f;
    // Filter if AccCompasTracker is used for tracker
    public static final float SOFTEN_NON_GYRO_ROTATION = 0.1f;
    // Filters for distance measurement.
    public static final float DISTANCE_LOWPASS = .5f;
    public static final int DISTANCE_MEDIAN = 3;

    /* Post exposure options */

    // Enable post exposure compensation in shader.
    public static boolean DO_AUTO_EXPOSURE = true;
    // How often glReadPixels is used to read out average of frame, etc.
    public static final int SKIP_EXPOSURE_CHECK = 10;
    // Exposure target
    public static final float EXPOSURE_BRIGHTNESS = 64f;
    // Maximum exposure multiplier
    public static final float MAX_EXPOSURE = 8f;
    // Minimum exposure multiplier
    public static final float MIN_EXPOSURE = .5f;
    // Gradually change exposure between readings.
    public static final float SOFTEN_EXPOSURE = 1f / (float)SKIP_EXPOSURE_CHECK / 4f;
    // Read glReadPixels delayed by x frames.
    public static final int EXPOSURE_BUFFER = 3;

    /* Result options */

    /* Post display options */

    // Flip the display in X axis. Experimental
    public static final boolean FLIP_X = false;

    /*  Stereo and scene options */

    public static final float IPD = .05f;

    // Perspective near and far values
    public static final float NEAR = 1f;
    public static final float FAR = 100f;

    /*  Android options */

    // Enable maximum brightness option
    public static final boolean MAX_BRIGHT = true;
    // Whitebalance setting. Changing this may very well impact tracking.
    public static final String WHITE_BALANCE = Camera.Parameters.WHITE_BALANCE_DAYLIGHT;
    // Focus mode to infinity as it can be quite annoying in AR if autofocus happens.
    public static final String FOCUS_MODE = Camera.Parameters.FOCUS_MODE_INFINITY;
    // Only render on new camera frame.
    public static final boolean RENDER_WHEN_DIRTY = true;
    // Gyroscope fix for Moto G2nd.
    public static final float LP_FOR_GYRO = 1f;

    /*  Blobfinder options */

    // Require a summed value of colored pixels.
    public static final int PIXELS_REQUIRED_DIV = 100 * 100;
    // Don't look at edges as it will lead to a partial blob,
    // giving faulty distance.
    public static final int EDGE = 4;
}
