package net.kajos.holokilo.Processors;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;
import net.kajos.holokilo.*;
import net.kajos.holokilo.Orientation.*;
import net.kajos.holokilo.Orientation.Matrix3x3;
import net.kajos.holokilo.Orientation.Vector3;
import net.kajos.holokilo.Util.LowPassFilter;
import net.kajos.holokilo.Util.MedianFilter;

import java.nio.*;
import java.util.LinkedList;
import java.util.concurrent.*;

public class GPUProcessor {
    private String fragmentShaderSubtractProcessing;
    private String fragmentShaderSubtractSingleProcessing;

    private final String vertexShaderCodeUnity =
            "attribute vec4 position;\n" +
                    "attribute vec2 inputTextureCoordinate;\n" +
                    "varying vec2 textureCoordinate;\n" +
                    "void main()\n" +
                    "{\n"+
                    "gl_Position = position;\n"+
                    "textureCoordinate = inputTextureCoordinate;\n" +
                    "}\n";

    private final String fragmentShaderCodeCamera =
            "#extension GL_OES_EGL_image_external : require\n"+
                    "precision mediump float;\n" +
                    "varying vec2 textureCoordinate;\n" +
                    "uniform samplerExternalOES texture;\n" +
                    "void main() {\n" +
                    "  gl_FragColor = texture2D( texture, textureCoordinate );\n" +
                    "}\n";

    private final String fragmentShaderCodeUnity =
            "#extension GL_OES_EGL_image_external : require\n"+
                    "precision mediump float;\n" +
                    "varying vec2 textureCoordinate;\n" +
                    "uniform sampler2D texture;\n" +
                    "void main() {\n" +
                    "gl_FragColor = texture2D( texture, textureCoordinate ).rgba;\n" +
                    "}\n";

    private String fragmentShaderCodeFinal;

    // Final shader that will pack the results in rgba in a smaller rgba texture.
    // Makes for faster downloading with gl_ReadPixels
    private final String fragmentShaderCodePost =
            "precision mediump float;\n" +
                    "uniform float width;\n" +
                    "uniform float height;\n" +
                    "varying vec2 textureCoordinate;\n" +
                    "uniform sampler2D texture;\n" +

                    "float get(float cx, float cy) {\n" +
                    "return texture2D(texture, vec2(cx, cy)).a;\n" +
                    "}\n" +

                    "void main() {\n" +
                    "vec4 orig = vec4(0.0);\n" +
                    "float x = floor(textureCoordinate.x / 4.0 * width) * 4.0 / width;\n" +
                    "orig.r = get(x, textureCoordinate.y);\n" +
                    "orig.g = get(x + 1.0 / width, textureCoordinate.y);\n" +
                    "orig.b = get(x + 2.0 / width, textureCoordinate.y);\n" +
                    "orig.a = get(x + 3.0 / width, textureCoordinate.y);\n" +
                    "gl_FragColor = orig;\n" +
                    "}\n";

    private ShortBuffer drawListBuffer;
    private int programUnity;
    private int programCamera;
    private int programSubtract;
    private int programSubtractSingle;
    private int programPost;
    private int programFinal;

    private int positionHandle[] = new int[6];
    private int textureCoordHandle[] = new int[6];
    private int textureUnityHandle;
    private int cameraHandle;
    private int boundHandle;
    private int boundSingleHandle;
    private int pointHandle;
    private int finalHandle;
    private int exposureHandle;
    private int postHandle;
    private int widthHandle;
    private int heightHandle;
    private int textureHandles;
    private int textureSingleHandle;

    // number of coordinates per vertex in this array
    static final int COORDS_PER_VERTEX = 2;

    // Used for vertices for quad rendering
    static float squareVertices[] = { -1.0f, -1.0f, 1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f,  };
    static float textureVertices[] = { 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, };
    static float flipSquareVertices[] = {  -1.0f, 1.0f, 1.0f, 1.0f, 1.0f, -1.0f, -1.0f, -1.0f, };

    private short drawOrder[] = { 0, 1, 2, 0, 2, 3 }; // order to draw vertices

    private final int vertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex

    private int cameraTexture;

    private FramebufferGyro[] framebuffers = new FramebufferGyro[Config.SAMPLE_FRAMES];
    private Framebuffer previousFb;

    private FloatBuffer vertexBuffer, textureVertexBuffer, flipVertexBuffer;

    private int screenWidth;
    private int screenHeight;

    private LowPassFilter dynamicBound = new LowPassFilter(Config.SOFTEN_EXPOSURE);
    private LowPassFilter exposureValue = new LowPassFilter(Config.SOFTEN_EXPOSURE);

    private float rawDistance = 0f;

    // Camera size
    private int cameraWidth, cameraHeight;
    // Size of framebuffers; these can be smaller than the actual
    // camera size, for faster processing and faster gl_ReadPixels.
    // There is a loss of precision when using a smaller fbo, but
    // supersampling makes up for it a bit.
    private int subWidth, subHeight;
    private UploadTexture uploadTexture;

    private CubeRenderer cubeRenderer;

    private float[] projectionViewMatrix = new float[16];
    private float[] inverseProjectionMatrix = new float[16];
    private float[] viewMatrix = new float[16];
    private float[] viewMatrixLeft = new float[16];
    private float[] viewMatrixRight = new float[16];
    private float[] cameraPerspective = new float[16];
    private float[] cardboardPerspective = new float[16];
    private float[] modelMatrix = new float[16];
    private float[] resultMatrixLeft = new float[16];
    private float[] resultMatrixRight = new float[16];
    private float sceneVFov, sceneAspect;

    public float getSceneVFov() {
        return sceneVFov;
    }

    public float getSceneAspect() {
        return sceneAspect;
    }

    public LowPassFilter ambientR = new LowPassFilter(Config.SOFTEN_EXPOSURE);
    public LowPassFilter ambientG = new LowPassFilter(Config.SOFTEN_EXPOSURE);
    public LowPassFilter ambientB = new LowPassFilter(Config.SOFTEN_EXPOSURE);

    private float[] ambient = new float[3];
    // The color read from the average of camera frames colors.
    // Can be used to influence directional lighting for example.
    public float[] getAmbientColor() {
        ambient[0] = ambientR.get() * exposureValue.get();
        ambient[1] = ambientG.get() * exposureValue.get();
        ambient[2] = ambientB.get() * exposureValue.get();
        return ambient;
    }

    private final float[] tmpCameraviewMatrixLeft = new float[16];
    public float[] getViewMatrixLeft() {
        try {
            semMatrix.acquire();
            System.arraycopy(resultMatrixLeft, 0, tmpCameraviewMatrixLeft, 0, 16);
            semMatrix.release();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return tmpCameraviewMatrixLeft;
    }

    private final float[] tmpCameraviewMatrixBoth = new float[32];
    public float[] getViewMatrixBoth() {
        try {
            semMatrix.acquire();
            System.arraycopy(resultMatrixLeft, 0, tmpCameraviewMatrixBoth, 0, 16);
            System.arraycopy(resultMatrixRight, 0, tmpCameraviewMatrixBoth, 16, 16);
            semMatrix.release();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return tmpCameraviewMatrixBoth;
    }

    private final float[] tmpTranslations = new float[3 * Config.MAX_BLOBS];
    public float[] getTranslations() {
        try {
            semMatrix.acquire();
            for (int i = 0, p = 0; i < Config.MAX_BLOBS; i++, p += 3) {
                System.arraycopy(translations[i], 0, tmpTranslations, p, 3);
            }
            semMatrix.release();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return tmpTranslations;
    }

    private Tracker tracker = null;

    private Vector3 tmpVector = new Vector3();
    private Vector3 tmpVector2 = new Vector3();
    private Vector3 tmpVector3 = new Vector3();
    private Semaphore semMatrix = new Semaphore(1);

    private Vector3[] saveVectors = new Vector3[Config.MAX_BLOBS];
    private boolean[] trackerLoss = new boolean[Config.MAX_BLOBS];
    private MedianFilter[] distanceMFilter = new MedianFilter[Config.MAX_BLOBS];
    private LowPassFilter[] vecXLPFilter = new LowPassFilter[Config.MAX_BLOBS];
    private LowPassFilter[] vecYLPFilter = new LowPassFilter[Config.MAX_BLOBS];
    private LowPassFilter[] distanceLPFilter = new LowPassFilter[Config.MAX_BLOBS];
    private float[][] translationsRaw = new float[Config.MAX_BLOBS][3];
    private float[][] translations = new float[Config.MAX_BLOBS][3];
    private Matrix3x3[] foundFbMatrix = new Matrix3x3[Config.MAX_BLOBS];

    private float pixelsFar;

    private float hfov, vfov;

    public GPUProcessor(Tracker tracker, int screenWidth, int screenHeight, int cameraWidth, int cameraHeight, float horizontalFov, float verticalFov, int cameraTexture)
    {
        // Initialize variety of median and lowpass filters. For each blob one filter (for instance; red blob, green blob, blue blob)
        for (int i = 0; i < Config.MAX_BLOBS; i++) {
            vecXLPFilter[i] = new LowPassFilter(Config.XY_LOWPASS);
            vecYLPFilter[i] = new LowPassFilter(Config.XY_LOWPASS);
            distanceLPFilter[i] = new LowPassFilter(Config.DISTANCE_LOWPASS);
            distanceMFilter[i] = new MedianFilter(Config.DISTANCE_MEDIAN);
            saveVectors[i] = new Vector3();
            foundFbMatrix[i] = new Matrix3x3();
        }

        this.tracker = tracker;

        Log.d(Config.TAG, "Camera cameraWidth: " + cameraWidth);
        Log.d(Config.TAG, "Vfov: " + verticalFov);

        cubeRenderer = new CubeRenderer();

        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;

        // Calculate aspect etc.
        float screenAspect = (float)screenWidth / 2f / (float)screenHeight;
        float cameraAspect = (float)cameraWidth / (float)cameraHeight;

        hfov =  horizontalFov / Config.CARDBOARD_HFOV;
        vfov = verticalFov / Config.CARDBOARD_VFOV;

        // If the fullscreen must be used, this will go at cost of
        // natural camera aspect, but it's possible. Else
        // black borders will show.
        if (Config.CARDBOARD_FILL_SCREEN) {
            hfov = 1f;
            vfov = screenAspect / cameraAspect;
        }

        // Matrices
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 1f, 0f);
        Matrix.perspectiveM(cameraPerspective, 0, verticalFov, cameraAspect, Config.NEAR, Config.FAR);
        Matrix.multiplyMM(projectionViewMatrix, 0, cameraPerspective, 0, viewMatrix, 0);
        Matrix.invertM(inverseProjectionMatrix, 0, projectionViewMatrix, 0);

        // If the fullscreen must be used, this will go at cost of
        // natural camera aspect, but it's possible. Else
        // black borders will show.
        float f = Config.CARDBOARD_VFOV;
        if (Config.CARDBOARD_FILL_SCREEN) {
            f = verticalFov / vfov;
        }
        Matrix.perspectiveM(cardboardPerspective, 0, f, screenAspect, Config.NEAR, Config.FAR);

        if (Config.USE_CARDBOARD) {
            sceneVFov = f;
            sceneAspect = screenAspect;
        } else {
            sceneVFov = verticalFov;
            sceneAspect = cameraAspect;
        }
        Matrix.setLookAtM(viewMatrixLeft, 0, Config.IPD, 0f, 0f, Config.IPD, 0f, 1f, 0f, 1f, 0f);
        Matrix.setLookAtM(viewMatrixRight, 0, -Config.IPD, 0f, 0f, -Config.IPD, 0f, 1f, 0f, 1f, 0f);

        // The shaders are set up to be dynamic dependent on config variables.
        fragmentShaderCodeFinal =
                (Config.SHOW_CAMERA ? "#extension GL_OES_EGL_image_external : require\n" : "") +
                        "precision mediump float;\n" +
                        "varying vec2 textureCoordinate;\n" +
                        "uniform " + (Config.SHOW_CAMERA ? "samplerExternalOES" : "sampler2D") + " texture;\n";
        if (Config.DRAW_POINT) {
            fragmentShaderCodeFinal += "uniform vec2 point;\n";
        }

        fragmentShaderCodeFinal += "uniform float exposure;\n" +
                        "void main() {\n";

        if (Config.FLIP_X)
            fragmentShaderCodeFinal += "vec2 coord = vec2(1.0 - textureCoordinate.x, textureCoordinate.y);\n";
        else
            fragmentShaderCodeFinal += "vec2 coord = textureCoordinate;\n";

        // Draw point will show a red dot where the last found position of the important blob is found.
        // You can verify latency with it while debugging.
        if (Config.DRAW_POINT) {
            fragmentShaderCodeFinal += "if (distance(coord, point) < 0.01) {\n" +
                    "  gl_FragColor = vec4(1.0, 0.0, 0.0, 0.0);\n" +
                    "  } else {\n";
            if (!Config.SHOW_DEBUG) {
                fragmentShaderCodeFinal += "gl_FragColor = texture2D( texture, coord ).rgba * exposure;\n";
            } else {
                fragmentShaderCodeFinal += "gl_FragColor = texture2D( texture, coord ).aaaa;\n";
            }
            fragmentShaderCodeFinal += "  }\n";
        } else {
            if (!Config.SHOW_DEBUG) {
                fragmentShaderCodeFinal += "gl_FragColor = texture2D( texture, coord ).rgba * exposure;\n";
            } else {
                fragmentShaderCodeFinal += "  gl_FragColor = texture2D( texture, coord ).aaaa;\n";
            }
        }
        fragmentShaderCodeFinal += "}\n";

        // The following shader is used when the flash is turned on
        // but the frames are half lit, due to timing inefficiencies.
        // Multiple frames are taken into account to construct a better lit image, at
        // cost of smear (max color over multiple frames).
        fragmentShaderSubtractProcessing =
                "precision highp float;\n" +
                        "varying vec2 textureCoordinate;\n";

        fragmentShaderSubtractProcessing += "uniform sampler2D texture[" + Config.SAMPLE_FRAMES + "];\n";

        fragmentShaderSubtractProcessing += "uniform float bound;\n";
        fragmentShaderSubtractProcessing += "uniform float width;\n";
        fragmentShaderSubtractProcessing += "uniform float height;\n";

        fragmentShaderSubtractProcessing += "void main() {\n";
        fragmentShaderSubtractProcessing += "float resbr["+ Config.SAMPLE_FRAMES+"];\n";
        fragmentShaderSubtractProcessing += "vec3 tmp;\n";
        fragmentShaderSubtractProcessing += "float val;\n";

        fragmentShaderSubtractProcessing += "vec3 color = vec3(0);\n";

        fragmentShaderSubtractProcessing += "color = texture2D(texture[0], textureCoordinate).rgb;\n";
        fragmentShaderSubtractProcessing += "tmp.x = max(color.r, max(color.g, color.b));\n";
        fragmentShaderSubtractProcessing += "tmp.y = min(color.r, min(color.g, color.b));\n";
        fragmentShaderSubtractProcessing += "float resMax = tmp.x - tmp.y;\n";
        fragmentShaderSubtractProcessing += "int maxI = 0;\n";
        for (int i = 1; i < Config.SAMPLE_FRAMES; i++) {
            fragmentShaderSubtractProcessing += "color = texture2D(texture["+ i + "], textureCoordinate).rgb;\n";
            fragmentShaderSubtractProcessing += "tmp.x = max(color.r, max(color.g, color.b));\n";
            fragmentShaderSubtractProcessing += "tmp.y = min(color.r, min(color.g, color.b));\n";
            // Filter white
            fragmentShaderSubtractProcessing += "val = tmp.x - tmp.y;\n";
            // Fetch the brightest pixel out of multiple frames
            fragmentShaderSubtractProcessing += "if (resMax < val) {\n";
            fragmentShaderSubtractProcessing += "resMax = val;\n";
            // Remember which frame it came from
            fragmentShaderSubtractProcessing += "maxI = " + i + ";\n";
            fragmentShaderSubtractProcessing += "}\n";
        }

        // The resulting brightness.
        fragmentShaderSubtractProcessing += "float res = resMax;\n";
        fragmentShaderSubtractProcessing += "float resWithCode = 0.0;\n";

        fragmentShaderSubtractProcessing += "vec3 debugColor = vec3(0.0);\n";

        // Is the result brighter than the bound that was set?
        // If so, it is a lightsource, could be reflective.
        fragmentShaderSubtractProcessing += "if (res > bound) {\n";

        // Create a color code. See if the pixel resembles red, green or blue best.
        // A fourth option is that the pixel resembles greyscale too much (could be overlit).
        fragmentShaderSubtractProcessing += "float code = " + ((64.0 + 128.0) / 255.0) + ";\n";

        fragmentShaderSubtractProcessing += "color = texture2D(texture[maxI], textureCoordinate).rgb;\n";
        fragmentShaderSubtractProcessing += "tmp.x = max(color.r, max(color.g, color.b));\n";
        fragmentShaderSubtractProcessing += "tmp.y = min(color.r, min(color.g, color.b));\n";
        fragmentShaderSubtractProcessing += "tmp.z = color.r + color.g + color.b - tmp.x - tmp.y;\n";
        fragmentShaderSubtractProcessing += "if (tmp.z / tmp.x > " + Config.OVER_EXPOSURE_PROTECTION + ") {\n";
        // Pixel is greyscale/overlit.
        fragmentShaderSubtractProcessing += "debugColor.rgb = vec3(1.0);\n";
        fragmentShaderSubtractProcessing += "} else {\n";

        // Codes:
        // Red: 0, 0
        // Blue: 1, 64
        // Green: 2, 128
        // Unknown: 3, 128 + 64
        fragmentShaderSubtractProcessing += "if (color.r > color.b) {\n";
        fragmentShaderSubtractProcessing += "if (color.r > color.g) {\n";
        fragmentShaderSubtractProcessing += "code = "+(0.0)+";\n"; // red
        fragmentShaderSubtractProcessing += "debugColor.r = 1.0;\n";
        fragmentShaderSubtractProcessing += "} else {\n";
        fragmentShaderSubtractProcessing += "code = "+(128.0 / 255.0)+";\n"; // green
        fragmentShaderSubtractProcessing += "debugColor.g = 1.0;\n";
        fragmentShaderSubtractProcessing += "}\n";
        fragmentShaderSubtractProcessing += "} else {\n";
        fragmentShaderSubtractProcessing += "if (color.g > color.b) {\n";
        fragmentShaderSubtractProcessing += "code = "+(128.0 / 255.0)+";\n"; // green
        fragmentShaderSubtractProcessing += "debugColor.g = 1.0;\n";
        fragmentShaderSubtractProcessing += "} else {\n";
        fragmentShaderSubtractProcessing += "code = "+(64.0 / 255.0)+";\n"; // blue
        fragmentShaderSubtractProcessing += "debugColor.b = 1.0;\n";
        fragmentShaderSubtractProcessing += "}\n";

        fragmentShaderSubtractProcessing += "}\n";
        fragmentShaderSubtractProcessing += "}\n";

        // Pack the brightness and the colorcode in one 8bit float: resWithCode
        fragmentShaderSubtractProcessing += "res -= bound;\n";
        fragmentShaderSubtractProcessing += "res /= 1.0 - bound;\n";
        fragmentShaderSubtractProcessing += "resWithCode = res * " + (62.0 / 255.0) + " + code;\n";

        if (!Config.SHOW_REFLECTIVITY) {
            fragmentShaderSubtractProcessing += "res = 1.0;\n";
        }
        fragmentShaderSubtractProcessing += "debugColor *= res;\n";
        fragmentShaderSubtractProcessing += "} else {\n";
        fragmentShaderSubtractProcessing += "res = 0.0;\n";
        fragmentShaderSubtractProcessing += "}\n";

        fragmentShaderSubtractProcessing += "gl_FragColor = vec4(debugColor.r, debugColor.g, debugColor.b, resWithCode);\n";
        fragmentShaderSubtractProcessing += "}\n";

        // This shader is used when it is certain the flash is completely off or completely on.
        // Only one frame needs to be used then.
        // Practically the same as the previous shader, but there is no max operation for multiple frames.
        fragmentShaderSubtractSingleProcessing =
                "precision highp float;\n" +
                        "varying vec2 textureCoordinate;\n";

        fragmentShaderSubtractSingleProcessing += "uniform sampler2D texture;\n";

        fragmentShaderSubtractSingleProcessing += "uniform float bound;\n";
        fragmentShaderSubtractSingleProcessing += "uniform float width;\n";
        fragmentShaderSubtractSingleProcessing += "uniform float height;\n";

        fragmentShaderSubtractSingleProcessing += "void main() {\n";
        fragmentShaderSubtractSingleProcessing += "vec3 tmp;\n";

        fragmentShaderSubtractSingleProcessing += "vec3 color = vec3(0);\n";

        fragmentShaderSubtractSingleProcessing += "color = texture2D(texture, textureCoordinate).rgb;\n";
        fragmentShaderSubtractSingleProcessing += "tmp.x = max(color.r, max(color.g, color.b));\n";
        fragmentShaderSubtractSingleProcessing += "tmp.y = min(color.r, min(color.g, color.b));\n";
        fragmentShaderSubtractSingleProcessing += "float resMax = tmp.x - tmp.y;\n";

        fragmentShaderSubtractSingleProcessing += "float res = resMax;\n";
        fragmentShaderSubtractSingleProcessing += "float resWithCode = 0.0;\n";

        fragmentShaderSubtractSingleProcessing += "vec3 debugColor = vec3(0.0);\n";

        fragmentShaderSubtractSingleProcessing += "if (res > bound) {\n";

        // Decide color code
        fragmentShaderSubtractSingleProcessing += "float code = " + ((64.0 + 128.0) / 255.0) + ";\n";

        fragmentShaderSubtractSingleProcessing += "color = texture2D(texture, textureCoordinate).rgb;\n";
        fragmentShaderSubtractSingleProcessing += "tmp.x = max(color.r, max(color.g, color.b));\n";
        fragmentShaderSubtractSingleProcessing += "tmp.y = min(color.r, min(color.g, color.b));\n";
        fragmentShaderSubtractSingleProcessing += "tmp.z = color.r + color.g + color.b - tmp.x - tmp.y;\n";
        fragmentShaderSubtractSingleProcessing += "if (tmp.z / tmp.x > " + Config.OVER_EXPOSURE_PROTECTION + ") {\n";
        fragmentShaderSubtractSingleProcessing += "debugColor.rgb = vec3(1.0);\n";
        fragmentShaderSubtractSingleProcessing += "} else {\n";

        // Codes:
        // Red: 0, 0
        // Blue: 1, 64
        // Green: 2, 128
        // Unknown: 3, 128 + 64
        fragmentShaderSubtractSingleProcessing += "if (color.r > color.b) {\n";
        fragmentShaderSubtractSingleProcessing += "if (color.r > color.g) {\n";
        fragmentShaderSubtractSingleProcessing += "code = "+(0.0)+";\n"; // red
        fragmentShaderSubtractSingleProcessing += "debugColor.r = 1.0;\n";
        fragmentShaderSubtractSingleProcessing += "} else {\n";
        fragmentShaderSubtractSingleProcessing += "code = "+(128.0 / 255.0)+";\n"; // green
        fragmentShaderSubtractSingleProcessing += "debugColor.g = 1.0;\n";
        fragmentShaderSubtractSingleProcessing += "}\n";
        fragmentShaderSubtractSingleProcessing += "} else {\n";
        fragmentShaderSubtractSingleProcessing += "if (color.g > color.b) {\n";
        fragmentShaderSubtractSingleProcessing += "code = "+(128.0 / 255.0)+";\n"; // green
        fragmentShaderSubtractSingleProcessing += "debugColor.g = 1.0;\n";
        fragmentShaderSubtractSingleProcessing += "} else {\n";
        fragmentShaderSubtractSingleProcessing += "code = "+(64.0 / 255.0)+";\n"; // blue
        fragmentShaderSubtractSingleProcessing += "debugColor.b = 1.0;\n";
        fragmentShaderSubtractSingleProcessing += "}\n";

        fragmentShaderSubtractSingleProcessing += "}\n";
        fragmentShaderSubtractSingleProcessing += "}\n";

        fragmentShaderSubtractSingleProcessing += "res -= bound;\n";
        fragmentShaderSubtractSingleProcessing += "res /= 1.0 - bound;\n";
        fragmentShaderSubtractSingleProcessing += "resWithCode = res * " + (62.0 / 255.0) + " + code;\n";

        if (!Config.SHOW_REFLECTIVITY) {
            fragmentShaderSubtractSingleProcessing += "res = 1.0;\n";
        }
        fragmentShaderSubtractSingleProcessing += "debugColor *= res;\n";
        fragmentShaderSubtractSingleProcessing += "} else {\n";
        fragmentShaderSubtractSingleProcessing += "res = 0.0;\n";
        fragmentShaderSubtractSingleProcessing += "}\n";

        fragmentShaderSubtractSingleProcessing += "gl_FragColor = vec4(debugColor.r, debugColor.g, debugColor.b, resWithCode);\n";
        fragmentShaderSubtractSingleProcessing += "}\n";

        this.cameraTexture = cameraTexture;

        // Bytebuffers for the quads and such.
        ByteBuffer bb = ByteBuffer.allocateDirect(squareVertices.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(squareVertices);
        vertexBuffer.position(0);

        ByteBuffer dlb = ByteBuffer.allocateDirect(drawOrder.length * 2);
        dlb.order(ByteOrder.nativeOrder());
        drawListBuffer = dlb.asShortBuffer();
        drawListBuffer.put(drawOrder);
        drawListBuffer.position(0);

        ByteBuffer bb2 = ByteBuffer.allocateDirect(textureVertices.length * 4);
        bb2.order(ByteOrder.nativeOrder());
        textureVertexBuffer = bb2.asFloatBuffer();
        textureVertexBuffer.put(textureVertices);
        textureVertexBuffer.position(0);

        ByteBuffer bb3 = ByteBuffer.allocateDirect(flipSquareVertices.length * 4);
        bb3.order(ByteOrder.nativeOrder());
        flipVertexBuffer = bb3.asFloatBuffer();
        flipVertexBuffer.put(flipSquareVertices);
        flipVertexBuffer.position(0);

        // Next initialize shaders with uniform variables and so on.

        int vertexShader = GLRenderer.loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCodeUnity);
        //
        // Camera shader
        //
        int fragmentShader = GLRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCodeCamera);

        programCamera = GLES20.glCreateProgram();             // create empty OpenGL ES Program
        GLES20.glAttachShader(programCamera, vertexShader);   // add the vertex shader to program
        GLES20.glAttachShader(programCamera, fragmentShader); // add the fragment shader to program
        GLES20.glLinkProgram(programCamera);

        // Init vertices
        positionHandle[0] = GLES20.glGetAttribLocation(programCamera, "position");
        textureCoordHandle[0] = GLES20.glGetAttribLocation(programCamera, "inputTextureCoordinate");

        cameraHandle = GLES20.glGetUniformLocation(programCamera, "texture");

        //
        // Subtract shader
        //
        fragmentShader = GLRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSubtractProcessing);

        programSubtract = GLES20.glCreateProgram();             // create empty OpenGL ES Program
        GLES20.glAttachShader(programSubtract, vertexShader);   // add the vertex shader to program
        GLES20.glAttachShader(programSubtract, fragmentShader); // add the fragment shader to program
        GLES20.glLinkProgram(programSubtract);

        // Init vertices
        positionHandle[1] = GLES20.glGetAttribLocation(programSubtract, "position");
        textureCoordHandle[1] = GLES20.glGetAttribLocation(programSubtract, "inputTextureCoordinate");

        textureHandles = GLES20.glGetUniformLocation(programSubtract, "texture");

        boundHandle = GLES20.glGetUniformLocation(programSubtract, "bound");
        //
        // Subtract2 shader
        //
        fragmentShader = GLRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSubtractSingleProcessing);

        programSubtractSingle = GLES20.glCreateProgram();             // create empty OpenGL ES Program
        GLES20.glAttachShader(programSubtractSingle, vertexShader);   // add the vertex shader to program
        GLES20.glAttachShader(programSubtractSingle, fragmentShader); // add the fragment shader to program
        GLES20.glLinkProgram(programSubtractSingle);

        // Init vertices
        positionHandle[5] = GLES20.glGetAttribLocation(programSubtractSingle, "position");
        textureCoordHandle[5] = GLES20.glGetAttribLocation(programSubtractSingle, "inputTextureCoordinate");

        textureSingleHandle = GLES20.glGetUniformLocation(programSubtractSingle, "texture");

        boundSingleHandle = GLES20.glGetUniformLocation(programSubtractSingle, "bound");

        //
        // Unity shader
        //
        fragmentShader = GLRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCodeUnity);

        programUnity = GLES20.glCreateProgram();             // create empty OpenGL ES Program
        GLES20.glAttachShader(programUnity, vertexShader);   // add the vertex shader to program
        GLES20.glAttachShader(programUnity, fragmentShader); // add the fragment shader to program
        GLES20.glLinkProgram(programUnity);

        // Init vertices
        positionHandle[2] = GLES20.glGetAttribLocation(programUnity, "position");
        textureCoordHandle[2] = GLES20.glGetAttribLocation(programUnity, "inputTextureCoordinate");
        textureUnityHandle = GLES20.glGetUniformLocation(programUnity, "texture");

        //
        // Post shader
        //
        fragmentShader = GLRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCodePost);

        programPost = GLES20.glCreateProgram();             // create empty OpenGL ES Program
        GLES20.glAttachShader(programPost, vertexShader);   // add the vertex shader to program
        GLES20.glAttachShader(programPost, fragmentShader); // add the fragment shader to program
        GLES20.glLinkProgram(programPost);
        widthHandle = GLES20.glGetUniformLocation(programPost, "width");
        heightHandle = GLES20.glGetUniformLocation(programPost, "height");
        // Init vertices
        positionHandle[3] = GLES20.glGetAttribLocation(programPost, "position");
        textureCoordHandle[3] = GLES20.glGetAttribLocation(programPost, "inputTextureCoordinate");
        postHandle = GLES20.glGetUniformLocation(programPost, "texture");

        //
        // Final shader
        //
        fragmentShader = GLRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCodeFinal);

        programFinal = GLES20.glCreateProgram();             // create empty OpenGL ES Program
        GLES20.glAttachShader(programFinal, vertexShader);   // add the vertex shader to program
        GLES20.glAttachShader(programFinal, fragmentShader); // add the fragment shader to program
        GLES20.glLinkProgram(programFinal);

        if (Config.DRAW_POINT) {
            pointHandle = GLES20.glGetUniformLocation(programFinal, "point");
        }

        // Init vertices
        positionHandle[4] = GLES20.glGetAttribLocation(programFinal, "position");
        textureCoordHandle[4] = GLES20.glGetAttribLocation(programFinal, "inputTextureCoordinate");
        finalHandle = GLES20.glGetUniformLocation(programFinal, "texture");
        exposureHandle = GLES20.glGetUniformLocation(programFinal, "exposure");

        // Create the framebuffers
        this.cameraWidth = cameraWidth;
        this.cameraHeight = cameraHeight;

        // Scale the fbo with a config variable.
        // This means faster processing but less resolution.
        // Also means glReadPixels is faster, and blobfinder on cpu
        // less stressed.
        int scale = Config.FBO_SCALE;

        subWidth = this.cameraWidth / scale;
        subHeight = this.cameraHeight / scale;

        pixelsFar = subWidth * subHeight / 1000;

        // FramebufferGyro is a framebuffer reference with a gyroscope
        // value attached to it.
        for (int i = 0; i < framebuffers.length; i++) {
            framebuffers[i] = new FramebufferGyro(subWidth, subHeight);
        }

        // An fbo used for tmp operations.
        previousFb = new Framebuffer(subWidth, subHeight);

        // For debugging
        uploadTexture = new UploadTexture(subWidth, subHeight);

        createVertexBuffers();
    }

    // doneFramebufferPool is a sorted list with fbos that are rendered with the shaders.
    // The oldest is taken to ensure glReadPixels wont stall.
    // A pool makes sure fbos do not need to be recreated every time, which would be
    // wasteful.
    private LinkedList<FramebufferGyro> doneFramebufferPool = new LinkedList<FramebufferGyro>();
    // Number of processing threads allowed for blobfinding.
    private final static int MAX_PROCESSING_THREADS = 4;
    private ExecutorService processorService = Executors.newFixedThreadPool(MAX_PROCESSING_THREADS);
    private volatile ConcurrentLinkedQueue<BlobFinder> blobFinderPool = new ConcurrentLinkedQueue<BlobFinder>();
    private volatile ConcurrentLinkedQueue<ByteBuffer> bytePool = new ConcurrentLinkedQueue<ByteBuffer>();
    // Count how many blob finding threads are active; if too many are active, skip a frame.
    private Semaphore countRunning = new Semaphore(MAX_PROCESSING_THREADS);

    public void doProcessing() {
        synchronized (GPUProcessor.this) {
            tracker.getCorrectedHeadView(latestRotation, latestRotationRaw);
        }
        //Render result and read pixels

        // If no buffer is used, the frame is immediately read with glReadPixels.
        // Might not be optimal, but less latency for some situations.
        if (Config.READ_BUFFER_SIZE == 0) {
            if (doneFramebufferPool.size() == 0) {
                doneFramebufferPool.add(new FramebufferGyro(subWidth / 4, subHeight));
            }
            // Use the shaders to find the brightspots.
            renderResult(doneFramebufferPool.get(0));
            // Then download from GPU and do blob finding.
            getFromGPU(doneFramebufferPool.get(0));
        } else {
            // Wait READ_BUFFER_SIZE frames before glReadPixels..
            if (doneFramebufferPool.size() >= Config.READ_BUFFER_SIZE) {
                FramebufferGyro fb = doneFramebufferPool.pollFirst();
                getFromGPU(fb);
                // Reuse framebuffer for new frame
                renderResult(fb);
                doneFramebufferPool.addLast(fb);
            } else {
                // Not enough fbos in the list yet, create a new one to work with.
                FramebufferGyro fb = new FramebufferGyro(subWidth / 4, subHeight);
                renderResult(fb);
                doneFramebufferPool.addLast(fb);
            }
        }

    }

    // Create quads etc.
    private int[] textureVertexPointer = {0};
    private int[] vertexPointer = {0};
    private int[] flipVertexPointer = {0};
    private int[] drawListPointer = {0};
    public void createVertexBuffers()
    {
        GLES20.glGenBuffers(1, textureVertexPointer, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, textureVertexPointer[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, squareVertices.length * 4, textureVertexBuffer, GLES20.GL_STATIC_DRAW);

        GLES20.glGenBuffers(1, vertexPointer, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexPointer[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, squareVertices.length * 4, vertexBuffer, GLES20.GL_STATIC_DRAW);

        GLES20.glGenBuffers(1, flipVertexPointer, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, flipVertexPointer[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, flipSquareVertices.length * 4, flipVertexBuffer, GLES20.GL_STATIC_DRAW);

        GLES20.glGenBuffers(1, drawListPointer, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, drawListPointer[0]);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, drawOrder.length * 2, drawListBuffer, GLES20.GL_STATIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        Log.d(Config.TAG, "Created buffers..");
    }

    private int counter = 0;
    // A copy of the gyroscope's latest value is stored here.
    private Matrix3x3 latestRotation = new Matrix3x3();
    private Matrix3x3 latestRotationRaw = new Matrix3x3();

    // A function for debugging.
    // Currently used to set the reference orientation and the reference blob size.
    // Using a bigger marker or a smaller one will lead to the same position if set
    // from the same distance.
    public void initBase() {
        Log.d(Config.TAG, "Init base");

        tracker.setBaseRotation();
        pixelsFar = rawDistance * Config.FAR_METER;
        Log.d(Config.TAG, "Pixels far: " + pixelsFar);
    }

    public LowPassFilter blobFinderFramerate = new LowPassFilter(0.1f);

    private long prevFrameTime = 0;
    public void getFromGPU(final FramebufferGyro input) {
        // See if not too many threads active.
        if (countRunning.tryAcquire()) {
            input.bind();
            // Fetch a bytebuffer from the pool
            ByteBuffer buffer = bytePool.poll();
            if (buffer == null) {
                // Create a new bytebuffer because pool doesnt have one.
                int size = subWidth * subHeight;
                buffer = ByteBuffer.allocateDirect(size);
                buffer.order(ByteOrder.nativeOrder());
            } else if (Config.SHOW_DEBUG) {
                // Upload the buffer to the debugging texture if needed.
                buffer.rewind();
                uploadTexture.upload(buffer);
            }

            buffer.rewind();
            GLES20.glPixelStorei(GLES20.GL_PACK_ALIGNMENT, 1);
            // Read the pixels. The pixels are stored as 8bit values in a rgba texture by a shader,
            // so divide width by 4. Eases on glReadPixels (GL_ALPHA for fbo not supported).
            GLES20.glReadPixels(0, 0, subWidth / 4, subHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);

            // Pool for a blobfinder object.
            BlobFinder blobFinder = blobFinderPool.poll();
            if (blobFinder == null) {
                blobFinder = new BlobFinder(subWidth, subHeight);
            }
            final BlobFinder passBlobFinder = blobFinder;
            final ByteBuffer passBuffer = buffer;
            // The fbo will be reused, so store some values in final vars.
            final float[] matrix = new float[16];
            final boolean flashOff = input.getFlashOff();
            final boolean stableFrame = input.getStableFrame();
            final boolean compareSizes = input.getCompareSizes();
            final double movement;
            // Apply some math to account for smear of blobs, due to movement.
            // Possible to apply it to account for extra smear with non stable frames.
            // Currently unused for non stable frames.
            if (!stableFrame) {
                movement = (1.0 - input.getMovement()) * Config.SMEAR_ADJUSTER;// * 3.0;
            } else {
                movement = (1.0 - input.getMovement()) * Config.SMEAR_ADJUSTER;
            }
            matrix[3] = 0f;
            matrix[7] = 0f;
            matrix[11] = 0f;
            matrix[15] = 1f;
            // Save the matrix of the gyroscope value of the fbo
            final Matrix3x3 saveMatrix = new Matrix3x3(input.getMatrix());
            if (!processorService.isShutdown()) {
                processorService.submit(new Runnable() {
                    @Override
                    public void run() {

                        passBuffer.rewind();
                        try {
                            // Pass data to blobfinder object
                            float resData[][] = passBlobFinder.getBlobs(passBuffer.array(), flashOff, compareSizes);
                            boolean updateMatrix = false;
                            semMatrix.acquire();

                            float distTranslation[] = new float[4];
                            float inTranslation[] = new float[4];
                            // Blobs founds
                            if (resData != null) {
                                for (int k = 0; k < Config.MAX_BLOBS; k++) {
                                    // A blob is found, when resData[k][0] is not isNaN.
                                    // This is checked this way, not a typo :)
                                    if (resData[k][0] == resData[k][0]) {
                                        // The following lowpass filters are used to smooth transition between results,
                                        // especially when the blobs go in and out of the camera range.
                                        // When a blob goes out of range, the alpha of the lowpass filter is adjusted.
                                        synchronized (vecXLPFilter) {
                                            if (vecXLPFilter[k].alpha < Config.XY_LOWPASS) {
                                                vecXLPFilter[k].alpha += Config.GAIN_ADJUST_RATE * Config.XY_LOWPASS;
                                                vecXLPFilter[k].alpha = Math.min(Config.XY_LOWPASS, vecXLPFilter[k].alpha);
                                            }
                                            if (vecYLPFilter[k].alpha < Config.XY_LOWPASS) {
                                                vecYLPFilter[k].alpha += Config.GAIN_ADJUST_RATE * Config.XY_LOWPASS;
                                                vecYLPFilter[k].alpha = Math.min(Config.XY_LOWPASS, vecYLPFilter[k].alpha);
                                            }
                                            trackerLoss[k] = false;
                                        }

                                        float x = resData[k][0] * (float) screenWidth / (float) subWidth;
                                        // For outside in tracking, flip the x coordinate
                                        if (Config.FLIP_X)
                                            x = screenWidth - x;

                                        float y = resData[k][1] * (float) screenHeight / (float) subHeight;
                                        y = (float) screenHeight - 1f - y;
                                        inTranslation[0] = (2f * x) / (float) screenWidth - 1f;
                                        inTranslation[1] = (2f * y) / (float) screenHeight - 1f;

                                        // Actual Z-depth measuring. Only do this when the flash is on for the frame,
                                        // and it is a stable frame.
                                        // Could be possible to do with non stable frame, but the size would be much less
                                        // accurate with smear caused by movement.
                                        if (!flashOff && stableFrame) {
                                            float newDistance = resData[k][2];
                                            rawDistance = newDistance;

                                            // Account for distance and smear by movement
                                            distanceLPFilter[k].setExtraMultiplier(Math.max(0f, (1f - (float)movement) * Math.min(1f, newDistance / subWidth / subHeight * Config.DISTANCE_ADJUSTER)));

                                            // Divide by value that is sized at fixed distance.
                                            newDistance /= pixelsFar;

                                            newDistance = 1f - newDistance;
                                            Log.d(Config.TAG, "Distance raw: " + newDistance);
                                            newDistance = Math.max(0.001f, Math.min(1f, newDistance));

                                            // get also sets the values in the lowpass filter.
                                            // Afterwards get() retrieves the last value.
                                            distanceLPFilter[k].get(distanceMFilter[k].get(newDistance));
                                        }
                                        // Get last distance value
                                        inTranslation[2] = distanceLPFilter[k].get();
                                        inTranslation[3] = 1f;

                                        // Calculate the xyz of the blob with the inverse projection matrix.
                                        Matrix.multiplyMV(distTranslation, 0, inverseProjectionMatrix, 0, inTranslation, 0);
                                        distTranslation[0] /= distTranslation[3];
                                        distTranslation[1] /= distTranslation[3];
                                        distTranslation[2] /= distTranslation[3];

                                        saveVectors[k].set(distTranslation[0], distTranslation[1], distTranslation[2]);
                                        if (Config.DO_GYRO_CORRECTION) {
                                            // Use the gyroscope values to decrease the latency, and increase the precision.
                                            // Sensorfusion.
                                            synchronized (GPUProcessor.this) {
                                                Matrix3x3 diffGyro = new Matrix3x3(tracker.differenceHeadView(latestRotation, saveMatrix));
                                                Matrix3x3.mult(diffGyro, saveVectors[k], tmpVector2);
                                            }
                                        } else {
                                            tmpVector2.set(saveVectors[k]);
                                        }
                                        tmpVector.set(tmpVector2);

                                        if (k == 0) {
                                            // First result is for the camera matrix.
                                            matrix[12] = translations[k][0] = translationsRaw[k][0] = vecXLPFilter[k].get(tmpVector2.x);
                                            matrix[13] = translations[k][1] = translationsRaw[k][1] = vecYLPFilter[k].get(tmpVector2.y);
                                            matrix[14] = translations[k][2] = translationsRaw[k][2] = tmpVector2.z;
                                        } else {
                                            // Next results can be used for tracked objects.
                                            translationsRaw[k][0] = vecXLPFilter[k].get(tmpVector2.x);
                                            translationsRaw[k][1] = vecYLPFilter[k].get(tmpVector2.y);
                                            translationsRaw[k][2] = tmpVector2.z;
                                            inTranslation[0] = translationsRaw[k][0];
                                            inTranslation[1] = translationsRaw[k][1];
                                            inTranslation[2] = translationsRaw[k][2];
                                            inTranslation[3] = 1f;
                                            Matrix.multiplyMV(distTranslation, 0, modelMatrix, 0, inTranslation, 0);
                                            translations[k][0] = distTranslation[0];
                                            translations[k][1] = distTranslation[1];
                                            translations[k][2] = distTranslation[2];
                                        }

                                        foundFbMatrix[k].set(saveMatrix);
                                        updateMatrix = true;
                                    } else {
                                        // No blobs are found, so use the last coordinates of the blobs to
                                        // guess the new coordinates with the gyroscope.
                                        // Translation is locked, but rotation is adjusted.
                                        synchronized (vecXLPFilter) {
                                            if (vecXLPFilter[k].alpha > Config.XY_LOWPASS * Config.LOWEST) {
                                                vecXLPFilter[k].alpha -= Config.LOSS_ADJUST_RATE * Config.XY_LOWPASS;
                                                vecXLPFilter[k].alpha = Math.max(Config.XY_LOWPASS * Config.LOWEST, vecXLPFilter[k].alpha);
                                            }
                                            if (vecYLPFilter[k].alpha > Config.XY_LOWPASS * Config.LOWEST) {
                                                vecYLPFilter[k].alpha -= Config.LOSS_ADJUST_RATE * Config.XY_LOWPASS;
                                                vecYLPFilter[k].alpha = Math.max(Config.XY_LOWPASS * Config.LOWEST, vecYLPFilter[k].alpha);
                                            }
                                            trackerLoss[k] = true;
                                        }

                                        if (Config.DO_GYRO_CORRECTION) {
                                            synchronized (GPUProcessor.this) {
                                                Matrix3x3 diffFoundGyro = new Matrix3x3(tracker.differenceHeadView(latestRotation, foundFbMatrix[k]));
                                                Matrix3x3.mult(diffFoundGyro, saveVectors[k], tmpVector2);
                                            }
                                            if (k == 0) {
                                                // First result is for the camera matrix.
                                                matrix[12] = translations[k][0] = translationsRaw[k][0] = vecXLPFilter[k].get(tmpVector2.x, Config.XY_LOWPASS);
                                                matrix[13] = translations[k][1] = translationsRaw[k][1] = vecYLPFilter[k].get(tmpVector2.y, Config.XY_LOWPASS);
                                                matrix[14] = translations[k][2] = translationsRaw[k][2] = tmpVector2.z;
                                                updateMatrix = true;
                                            } else {
                                                // Next results can be used for tracked objects.
                                                translationsRaw[k][0] = vecXLPFilter[k].get(tmpVector2.x, Config.XY_LOWPASS);
                                                translationsRaw[k][1] = vecYLPFilter[k].get(tmpVector2.y, Config.XY_LOWPASS);
                                                translationsRaw[k][2] = tmpVector2.z;
                                                inTranslation[0] = translationsRaw[k][0];
                                                inTranslation[1] = translationsRaw[k][1];
                                                inTranslation[2] = translationsRaw[k][2];
                                                inTranslation[3] = 1f;
                                                Matrix.multiplyMV(distTranslation, 0, modelMatrix, 0, inTranslation, 0);
                                                translations[k][0] = distTranslation[0];
                                                translations[k][1] = distTranslation[1];
                                                translations[k][2] = distTranslation[2];
                                            }
                                        }
                                    }
                                }
                                if (updateMatrix) {
                                    synchronized (GPUProcessor.this) {
                                        for (int f = 0; f < 3; f++)
                                            for (int c = 0; c < 3; c++)
                                                matrix[c * 4 + f] = latestRotation.get(c, f);
                                    }
                                    float[] result = new float[16];

                                    // Finally set the viewmatrices for display
                                    Matrix.invertM(modelMatrix, 0, matrix, 0);

                                    Matrix.multiplyMM(resultMatrixLeft, 0, viewMatrixLeft, 0, matrix, 0);
                                    Matrix.multiplyMM(result, 0, Config.USE_CARDBOARD ? cardboardPerspective : cameraPerspective, 0, resultMatrixLeft, 0);

                                    // cubeRenderer is for testing purposes.
                                    if (Config.USE_CARDBOARD) {
                                        cubeRenderer.setMatrixLeft(result);

                                        Matrix.multiplyMM(resultMatrixRight, 0, viewMatrixRight, 0, matrix, 0);
                                        Matrix.multiplyMM(result, 0, Config.USE_CARDBOARD ? cardboardPerspective : cameraPerspective, 0, resultMatrixRight, 0);

                                        cubeRenderer.setMatrixRight(result);
                                    } else {
                                        cubeRenderer.setMatrixLeft(result);
                                    }
                                }
                            }

                            // Calculate the framerate of the blobprocessing
                            if (counter++ >= 10) {
                                Log.d(Config.TAG, "Processed framerate: " + blobFinderFramerate.get() + "(ms)");
                                counter = 0;
                            }
                            long time = System.currentTimeMillis();
                            if (prevFrameTime != 0) {
                                blobFinderFramerate.get(time - prevFrameTime);
                            }
                            prevFrameTime = time;
                        } catch (Exception ex) {
                            Log.e(Config.TAG, ex.toString());
                            Log.e(Config.TAG, ex.getStackTrace()[0].toString());
                        }

                        semMatrix.release();

                        blobFinderPool.add(passBlobFinder);
                        bytePool.add(passBuffer);
                        countRunning.release();
                    }
                });
            } else {
                Log.e(Config.TAG, "Pool service already shutdown!");
            }
            input.unbind();
        }
    }

    private Semaphore waitSem = new Semaphore(0);
    private Semaphore flashSem = new Semaphore(0);
    public void catchFlashlessFrame() {
        flashSem.release();
        try {
            waitSem.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private boolean stableFrame = true;
    public void doStableFrame(boolean state) {
        stableFrame = state;
    }

    private boolean compareSizes = true;
    public void doCompareSizes(boolean state) {
        compareSizes = state;
    }

    private void renderResult(FramebufferGyro target) {
        // Use the shaders to find the light blobs.
        // Take the variables (flash, stable) from the given fbo and take them
        // along in the new rendered fbo.
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, drawListPointer[0]);

        previousFb.bind();
        GLES20.glClearColor(0.0f, 1.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        int maxNumber = 0;
        long maxTimestamp = 0;
        for (int i = Config.SAMPLE_FRAMES - 1; i >= 0; i--) {
            long ti = framebuffers[i].getTimestamp();
            if (ti > maxTimestamp) {
                maxTimestamp = ti;
                maxNumber = i;
            }
        }

        boolean stableFrame = framebuffers[maxNumber].getStableFrame();
        boolean flashFrame = framebuffers[maxNumber].getFlashOff();

        if (!stableFrame && !flashFrame) {
            GLES20.glUseProgram(programSubtract);
            int handles[] = new int[Config.SAMPLE_FRAMES];
            for (int i = Config.SAMPLE_FRAMES - 1; i >= 0; i--) {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
                int f = maxNumber - i;
                if (f < 0)
                    f += Config.SAMPLE_FRAMES;
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, framebuffers[f].textureId.get(0));
                handles[i] = i;
            }
            GLES20.glUniform1f(boundHandle, dynamicBound.get() / 255.0f);

            GLES20.glUniform1iv(textureHandles, Config.SAMPLE_FRAMES, handles, 0);

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexPointer[0]);
            GLES20.glEnableVertexAttribArray(positionHandle[1]);
            GLES20.glVertexAttribPointer(positionHandle[1], COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, 0);

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, textureVertexPointer[0]);
            GLES20.glEnableVertexAttribArray(textureCoordHandle[1]);
            GLES20.glVertexAttribPointer(textureCoordHandle[1], COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, 0);

        } else {
            GLES20.glUseProgram(programSubtractSingle);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, framebuffers[maxNumber].textureId.get(0));
            GLES20.glUniform1i(textureSingleHandle, 0);

            GLES20.glUniform1f(boundSingleHandle, dynamicBound.get() / 255.0f);

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexPointer[0]);
            GLES20.glEnableVertexAttribArray(positionHandle[5]);
            GLES20.glVertexAttribPointer(positionHandle[5], COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, 0);

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, textureVertexPointer[0]);
            GLES20.glEnableVertexAttribArray(textureCoordHandle[5]);
            GLES20.glVertexAttribPointer(textureCoordHandle[5], COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, 0);
        }

        // Copy values to new fbo
        target.setStableFrame(stableFrame);
        target.setFlashOff(flashFrame);
        target.setCompareSizes(framebuffers[maxNumber].getCompareSizes());
        target.setMovement(framebuffers[maxNumber].getMovement());
        target.setMatrix(framebuffers[maxNumber].getMatrix());

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.length,
                GLES20.GL_UNSIGNED_SHORT, 0);

        GLES20.glUseProgram(0);
        // Pack the resulting frame in a rgba format where each value (r,g,b or a) is a seperate pixel.
        previousFb.unbind();

        target.bind();
        GLES20.glClearColor(0.0f, 0.0f, 1.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(programPost);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousFb.textureId.get(0));
        GLES20.glUniform1i(postHandle, 0);
        GLES20.glUniform1f(widthHandle, subWidth);
        GLES20.glUniform1f(heightHandle, subHeight);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, flipVertexPointer[0]);
        GLES20.glEnableVertexAttribArray(positionHandle[3]);
        GLES20.glVertexAttribPointer(positionHandle[3], COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, textureVertexPointer[0]);
        GLES20.glEnableVertexAttribArray(textureCoordHandle[3]);
        GLES20.glVertexAttribPointer(textureCoordHandle[3], COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, 0);

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.length,
                GLES20.GL_UNSIGNED_SHORT, 0);

        GLES20.glUseProgram(0);
        target.unbind();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    public void drawTexture(int textureId)
    {
        // Draw a texture
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glUseProgram(programUnity);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);

        GLES20.glUniform1i(textureUnityHandle, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, flipVertexPointer[0]);
        GLES20.glEnableVertexAttribArray(positionHandle[4]);
        GLES20.glVertexAttribPointer(positionHandle[4], COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, textureVertexPointer[0]);
        GLES20.glEnableVertexAttribArray(textureCoordHandle[4]);
        GLES20.glVertexAttribPointer(textureCoordHandle[4], COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, 0);

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, drawListPointer[0]);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.length,
                GLES20.GL_UNSIGNED_SHORT, 0);

        GLES20.glUseProgram(0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    public void draw()
    {
        // Draw to screen without SBS
        GLES20.glClearColor(0.0f, 0.0f, 1.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(programFinal);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        if (Config.SHOW_DEBUG) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, uploadTexture.textureId.get(0));
        } else if (Config.SHOW_CAMERA) {
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture);
        } else if (Config.SHOW_RGBA_RESULT && doneFramebufferPool.size() > 0) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, doneFramebufferPool.get(0).textureId.get(0));
        } else if (previousFb != null) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousFb.textureId.get(0));
        }
        GLES20.glUniform1f(exposureHandle, exposureValue.get());
        GLES20.glUniform1i(finalHandle, 0);

        if (Config.DRAW_POINT) {
            GLES20.glUniform2f(pointHandle, (float) BlobFinder.getHitPoint()[0] / (float) subWidth, (float) BlobFinder.getHitPoint()[1] / (float) subHeight);
        }

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexPointer[0]);
        GLES20.glEnableVertexAttribArray(positionHandle[4]);
        GLES20.glVertexAttribPointer(positionHandle[4], COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, textureVertexPointer[0]);
        GLES20.glEnableVertexAttribArray(textureCoordHandle[4]);
        GLES20.glVertexAttribPointer(textureCoordHandle[4], COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, 0);

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, drawListPointer[0]);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.length,
                GLES20.GL_UNSIGNED_SHORT, 0);

        GLES20.glUseProgram(0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        if (Config.DRAW_CUBES) {
            CubeRenderer.setStates(true);
            try {
                semMatrix.acquire();

                cubeRenderer.drawLeft();

                semMatrix.release();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            CubeRenderer.setStates(false);
        }
    }

    public void drawStereo()
    {
        // Draw to screen in SBS for cardboard and so on.
        GLES20.glClearColor(0, 0, 0, 0);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(programFinal);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        if (Config.SHOW_DEBUG) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, uploadTexture.textureId.get(0));
        } else if (Config.SHOW_CAMERA) {
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture);
        } else if (Config.SHOW_RGBA_RESULT && doneFramebufferPool.size() > 0) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, doneFramebufferPool.get(0).textureId.get(0));
        } else if (previousFb != null) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousFb.textureId.get(0));
        }
        GLES20.glUniform1f(exposureHandle, exposureValue.get());
        GLES20.glUniform1i(finalHandle, 0);
        GLES20.glUniform2f(pointHandle, (float) BlobFinder.getHitPoint()[0] / (float) subWidth, (float) BlobFinder.getHitPoint()[1] / (float) subHeight);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, textureVertexPointer[0]);
        GLES20.glEnableVertexAttribArray(textureCoordHandle[4]);
        GLES20.glVertexAttribPointer(textureCoordHandle[4], COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, 0);

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, drawListPointer[0]);

        float w = (float)screenWidth / 2f * hfov;
        float h = (float)screenHeight * vfov;
        float x1 = ((float)screenWidth / 2f - w) / 2f;
        float x2 = x1 + (float)screenWidth / 2f;
        float y = ((float)screenHeight - h) / 2f;
        for (int i = 0; i < 2; i++) {
            GLES20.glViewport(i == 0 ? (int)x1 : (int)x2, (int)y, (int)w, (int)h);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexPointer[0]);
            GLES20.glEnableVertexAttribArray(positionHandle[4]);
            GLES20.glVertexAttribPointer(positionHandle[4], COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, 0);

            GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.length,
                    GLES20.GL_UNSIGNED_SHORT, 0);
        }
        GLES20.glViewport(0, 0, screenWidth, screenHeight);
        GLES20.glUseProgram(0);

        if (Config.DRAW_CUBES) {
            CubeRenderer.setStates(true);
            try {
                semMatrix.acquire();

                GLES20.glViewport(0, 0, screenWidth / 2, screenHeight);
                cubeRenderer.drawLeft();

                GLES20.glViewport(screenWidth / 2, 0, screenWidth / 2, screenHeight);
                cubeRenderer.drawRight();

                semMatrix.release();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            CubeRenderer.setStates(false);
        }

        // Draw seperation line
        GLES20.glViewport(0, 0, screenWidth, screenHeight);
        GLES20.glScissor(screenWidth / 2, 0, 1, screenHeight);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(1f, 1f, 1f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }

    int countFb = 0;

    private int[] horizontalExpo = {0,0};
    private int[] verticalExpo = {0,0};
    private int horCount = 0;
    private int verCount = Config.SKIP_EXPOSURE_CHECK / 2;
    private ByteBuffer horizontalExposureBuffer = null;
    private ByteBuffer verticalExposureBuffer = null;
    private long framebufferTimestamp = 0;

    // Fetch the camera image and store it in a fbo.
    // Do other processing as well, such as finding ambient color,
    // finding the brightness of the scene, etc.
    public void cameraToFramebuffer() {
        framebufferTimestamp++;

        // Two frames back
        int prevFb = countFb - Config.EXPOSURE_BUFFER;
        if (prevFb < 0) {
            prevFb += Config.SAMPLE_FRAMES;
        }

        double movement;
        synchronized (GPUProcessor.this) {
            // Fetch gyroscope value from previous fbo, to find the movement between the old and new.
            movement = Matrix3x3.angle(latestRotation, framebuffers[countFb].getMatrix());
            // Set to 0 if isNaN.
            if (movement != movement) {
                movement = 0;
            }
        }

        // Increase the fbo counter
        countFb++;
        if (countFb >= Config.SAMPLE_FRAMES)
            countFb -= Config.SAMPLE_FRAMES;

        // Store movement and rotation
        framebuffers[countFb].setMovement(movement);
        framebuffers[countFb].setMatrix(latestRotation);

        // If a flash frame is required (flash is off), the semaphore will be set.
        boolean flashFrame;
        if (flashSem.tryAcquire()) {
            waitSem.release();
            framebuffers[countFb].setFlashOff(true);
            flashFrame = true;
        } else {
            framebuffers[countFb].setFlashOff(false);
            flashFrame = false;
        }

        // Store values..
        framebuffers[countFb].setTimestamp(framebufferTimestamp);
        framebuffers[countFb].setStableFrame(stableFrame);
        framebuffers[countFb].setCompareSizes(stableFrame || compareSizes);
        framebuffers[countFb].setFlashOff(flashFrame);
        framebuffers[countFb].bind();

        // Render to fbo
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(programCamera);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture);
        GLES20.glUniform1i(cameraHandle, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexPointer[0]);
        GLES20.glEnableVertexAttribArray(positionHandle[0]);
        GLES20.glVertexAttribPointer(positionHandle[0], COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, 0);
        GLES20.glEnableVertexAttribArray(textureCoordHandle[0]);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, textureVertexPointer[0]);
        GLES20.glVertexAttribPointer(textureCoordHandle[0], COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, 0);

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, drawListPointer[0]);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.length,
                GLES20.GL_UNSIGNED_SHORT, 0);

        GLES20.glUseProgram(0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        horCount++;
        verCount++;

        framebuffers[countFb].unbind();

        if ((Config.DO_AUTO_EXPOSURE || !Config.CAP_FIXED) && (stableFrame || flashFrame)) {
            // Find the bounds for the distinction of light blobs.
            // Also find out the exposure for after auto exposure correction, which
            // multiplies the colors with the exposure variable.
            framebuffers[prevFb].bind();

            int type = flashFrame ? 1 : 0;

            boolean updateExposure = false;
            int avg = 0;
            int ma = 0;
            int color[] = {0,0,0};
            if (horCount > Config.SKIP_EXPOSURE_CHECK) {
                int size = subWidth * 4;
                if (horizontalExposureBuffer == null) {
                    horizontalExposureBuffer = ByteBuffer.allocateDirect(size);
                    horizontalExposureBuffer.order(ByteOrder.nativeOrder());
                }

                horizontalExposureBuffer.rewind();
                GLES20.glPixelStorei(GLES20.GL_PACK_ALIGNMENT, 1);
                // One horizontal line is take, in the middle of the fbo
                GLES20.glReadPixels(0, subHeight / 2, subWidth, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, horizontalExposureBuffer);

                horizontalExposureBuffer.rewind();

                horizontalExpo[type] = 0;
                for (int i = 0; i < size; i++) {
                    int c = i % 4;
                    if (c == 3) {
                        horizontalExposureBuffer.get();
                        continue;
                    }
                    int val = horizontalExposureBuffer.get() & 0xff;
                    color[c] += val;
                    avg += val;
                    horizontalExpo[type] = Math.max(horizontalExpo[type], val);
                }
                avg /= subWidth * 3;
                ma = horizontalExpo[type] + horizontalExpo[1-type];
                ma /= 2;

                for (int i = 0; i < 3; i++)
                    color[i] /= subWidth;

                horCount = 0;

                updateExposure = true;
            }

            if (verCount > Config.SKIP_EXPOSURE_CHECK) {
                int size = subHeight * 4;
                if (verticalExposureBuffer == null) {
                    verticalExposureBuffer = ByteBuffer.allocateDirect(size);
                    verticalExposureBuffer.order(ByteOrder.nativeOrder());
                }

                verticalExposureBuffer.rewind();
                GLES20.glPixelStorei(GLES20.GL_PACK_ALIGNMENT, 1);
                // One vertical line is take, in the middle of the fbo
                GLES20.glReadPixels(subWidth / 2, 0, 1, subHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, verticalExposureBuffer);

                verticalExposureBuffer.rewind();

                verticalExpo[type] = 0;
                for (int i = 0; i < size; i++) {
                    int c = i % 4;
                    if (c == 3) {
                        verticalExposureBuffer.get();
                        continue;
                    }
                    int val = verticalExposureBuffer.get() & 0xff;
                    color[c] += val;
                    avg += val;
                    verticalExpo[type] = Math.max(verticalExpo[type], val);
                }
                avg /= subHeight * 3;
                ma = verticalExpo[type] + verticalExpo[1-type];
                ma /= 2;

                for (int i = 0; i < 3; i++)
                    color[i] /= subHeight;

                verCount = 0;

                updateExposure = true;
            }

            // Got a full result, use it.
            if (updateExposure) {
                Log.d(Config.TAG, "Max Light: " + ma);
                Log.d(Config.TAG, "Avg Light: " + avg);
                Log.d(Config.TAG, "Cap: " + Config.CAP_REF);

                if (!Config.CAP_FIXED) {
                    dynamicBound.get(Math.max(Config.CAP_MIN, Math.min(Config.CAP_MAX, Config.CAP_REF + ma)));
                    Log.d(Config.TAG, "Dynamic Bound: " + dynamicBound.get());
                }

                ambientR.get(color[0]);
                ambientG.get(color[1]);
                ambientB.get(color[2]);

                if (Config.DO_AUTO_EXPOSURE && !flashFrame) {
                    float newExposureValue = Config.EXPOSURE_BRIGHTNESS / (float)avg;

                    if (newExposureValue > Config.MAX_EXPOSURE) {
                        exposureValue.get(Config.MAX_EXPOSURE);
                    } else if (newExposureValue < Config.MIN_EXPOSURE) {
                        exposureValue.get(Config.MIN_EXPOSURE);
                    } else {
                        exposureValue.get(newExposureValue);
                    }
                }
            }

            framebuffers[prevFb].unbind();
        }
    }

    public void destroy() {
        waitSem.release(1);
    }
}