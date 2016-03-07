package net.kajos.holokilo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import android.opengl.GLES20;
import android.util.Log;

public class CubeRenderer {
    int iProgId;
    int iPosition;
    int iVPMatrix;
    int iTexId;
    int iTexLoc;
    int iTexCoords;

    float[] MVPMatrixLeft = new float[16];
    float[] MVPMatrixRight = new float[16];

    float[] cube = {
            2,2,0, -2,2,0,   -2,-2,0,  2,-2,0, //-2-1-2-3 front
            2,2,0,  2,-2,0,   2,-2,-4,  2,2,-4,//-2-3--4-5 right
            2,-2,-4, -2,-2,-4, -2,2,-4, 2,2,-4,//-4-7-6-5 back
            -2,2,0, -2,2,-4, -2,-2,-4, -2,-2,0,//1-6-7-2 left
            2,2,0, 2,2,-4,   -2,2,-4,  -2,2,0, //top
            2,-2,0, -2,-2,0,  -2,-2,-4, 2,-2,-4,//bottom

//            2,2,2, 0,2,2, 0,0,2, 2,0,2, //0-10-3 front
//            2,2,2, 2,0,2,  2,0,0, 2,2,0,//0-3-4-5 right
//            2,0,0, 0,0,0, 0,2,0, 2,2,0,//4-7-6-5 back
//            0,2,2, 0,2,0, 0,0,0, 0,0,2,//1-6-70 left
//            2,2,2, 2,2,0, 0,2,0, 0,2,2, //top
//            2,0,2, 0,0,2, 0,0,0, 2,0,0,//bottom
    };

    short[] indeces = {
            0,1,2, 0,2,3,
            4,5,6, 4,6,7,
            8,9,10, 8,10,11,
            12,13,14, 12,14,15,
            16,17,18, 16,18,19,
            20,21,22, 20,22,23,
    };

    float[] tex = {
            1,1,1, -1,1,1, -1,-1,1, 1,-1,1, //0-1-2-3 front
            1,1,1, 1,-1,1,  1,-1,-1, 1,1,-1,//0-3-4-5 right
            1,-1,-1, -1,-1,-1, -1,1,-1, 1,1,-1,//4-7-6-5 back
            -1,1,1, -1,1,-1, -1,-1,-1, -1,-1,1,//1-6-7-2 left
            1,1,1, 1,1,-1, -1,1,-1, -1,1,1, //top
            1,-1,1, -1,-1,1, -1,-1,-1, 1,-1,-1,//bottom
    };

    final String strVShader =
            "attribute vec4 a_position;" +
                    "attribute vec4 a_color;" +
                    "attribute vec3 a_normal;" +
                    "uniform mat4 u_VPMatrix;" +
                    "uniform vec3 u_LightPos;" +
                    "varying vec3 v_texCoords;" +
                    "attribute vec3 a_texCoords;" +
                    "void main()" +
                    "{" +
                    "v_texCoords = a_texCoords;" +
                    "gl_Position = u_VPMatrix * a_position;" +
                    "}";

    final String strFShader =
            "precision mediump float;" +
                    "uniform samplerCube u_texId;" +
                    "varying vec3 v_texCoords;" +
                    "void main()" +
                    "{" +
                    "gl_FragColor = textureCube(u_texId, v_texCoords);" +
                    "}";

    FloatBuffer cubeBuffer = null;
    ShortBuffer indexBuffer = null;
    FloatBuffer texBuffer = null;
    public CubeRenderer()
    {
        cubeBuffer = ByteBuffer.allocateDirect(cube.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        cubeBuffer.put(cube).position(0);

        indexBuffer = ByteBuffer.allocateDirect(indeces.length * 4).order(ByteOrder.nativeOrder()).asShortBuffer();
        indexBuffer.put(indeces).position(0);

        texBuffer = ByteBuffer.allocateDirect(tex.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        texBuffer.put(tex).position(0);

        iProgId = loadProgram(strVShader, strFShader);
        iPosition = GLES20.glGetAttribLocation(iProgId, "a_position");
        iVPMatrix = GLES20.glGetUniformLocation(iProgId, "u_VPMatrix");
        iTexLoc = GLES20.glGetUniformLocation(iProgId, "u_texId");
        iTexCoords = GLES20.glGetAttribLocation(iProgId, "a_texCoords");
        iTexId = createCubeTexture();

        createVertexBuffers();
    }

    private int[] texturePointer = {0};
    private int[] cubePointer = {0};
    private int[] indexPointer = {0};
    private void createVertexBuffers() {
        cubeBuffer.position(0);
        texBuffer.position(0);
        indexBuffer.position(0);

        GLES20.glGenBuffers(1, texturePointer, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, texturePointer[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, tex.length * 4, texBuffer, GLES20.GL_STATIC_DRAW);

        GLES20.glGenBuffers(1, cubePointer, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, cubePointer[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, cube.length * 4, cubeBuffer, GLES20.GL_STATIC_DRAW);

        GLES20.glGenBuffers(1, indexPointer, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexPointer[0]);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, indeces.length * 4, indexBuffer, GLES20.GL_STATIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    public void setMatrixLeft(float[] matrix) {
        for (int i = 0; i < 16; i++) {
            MVPMatrixLeft[i] = matrix[i];
        }
    }

    public void setMatrixRight(float[] matrix) {
        for (int i = 0; i < 16; i++) {
            MVPMatrixRight[i] = matrix[i];
        }
    }

    public void drawLeft() {
        GLES20.glUseProgram(iProgId);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_CUBE_MAP, iTexId);
        GLES20.glUniform1i(iTexLoc, 0);

        GLES20.glUniformMatrix4fv(iVPMatrix, 1, false, MVPMatrixLeft, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, cubePointer[0]);
        GLES20.glVertexAttribPointer(iPosition, 3, GLES20.GL_FLOAT, false, 0, 0);
        GLES20.glEnableVertexAttribArray(iPosition);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, texturePointer[0]);
        GLES20.glVertexAttribPointer(iTexCoords, 3, GLES20.GL_FLOAT, false, 0, 0);
        GLES20.glEnableVertexAttribArray(iTexCoords);

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexPointer[0]);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 36, GLES20.GL_UNSIGNED_SHORT, 0);
    }

    public void drawRight() {
        GLES20.glUseProgram(iProgId);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_CUBE_MAP, iTexId);
        GLES20.glUniform1i(iTexLoc, 0);

        GLES20.glUniformMatrix4fv(iVPMatrix, 1, false, MVPMatrixRight, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, cubePointer[0]);
        GLES20.glVertexAttribPointer(iPosition, 3, GLES20.GL_FLOAT, false, 0, 0);
        GLES20.glEnableVertexAttribArray(iPosition);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, texturePointer[0]);
        GLES20.glVertexAttribPointer(iTexCoords, 3, GLES20.GL_FLOAT, false, 0, 0);
        GLES20.glEnableVertexAttribArray(iTexCoords);

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexPointer[0]);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 36, GLES20.GL_UNSIGNED_SHORT, 0);

    }

    public static void setStates(boolean state) {
        if (state) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glDepthFunc(GLES20.GL_LESS);
            GLES20.glFrontFace(GLES20.GL_CCW);
            GLES20.glEnable(GLES20.GL_CULL_FACE);
            GLES20.glCullFace(GLES20.GL_FRONT);
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);
        } else {
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
            GLES20.glUseProgram(0);
        }
    }

    private int createCubeTexture() {
        int[] textureId = new int[1];

        // Face 0 - Red
        byte[] cubePixels0 = { 127, 0, 0 };
        // Face 1 - Green
        byte[] cubePixels1 = { 0, 127, 0 };
        // Face 2 - Blue
        byte[] cubePixels2 = { 0, 0, 127 };
        // Face 3 - Yellow
        byte[] cubePixels3 = { 127, 127, 0 };
        // Face 4 - Purple
        byte[] cubePixels4 = { 127, 0, 127 };
        // Face 5 - White
        byte[] cubePixels5 = { 127, 127, 127 };

        ByteBuffer cubePixels = ByteBuffer.allocateDirect(3);

        // Generate a texture object
        GLES20.glGenTextures ( 1, textureId, 0 );

        // Bind the texture object
        GLES20.glBindTexture ( GLES20.GL_TEXTURE_CUBE_MAP, textureId[0] );

        // Load the cube face - Positive X
        cubePixels.put(cubePixels0).position(0);
        GLES20.glTexImage2D ( GLES20.GL_TEXTURE_CUBE_MAP_POSITIVE_X, 0, GLES20.GL_RGB, 1, 1, 0,
                GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, cubePixels );

        // Load the cube face - Negative X
        cubePixels.put(cubePixels1).position(0);
        GLES20.glTexImage2D ( GLES20.GL_TEXTURE_CUBE_MAP_NEGATIVE_X, 0, GLES20.GL_RGB, 1, 1, 0,
                GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, cubePixels );

        // Load the cube face - Positive Y
        cubePixels.put(cubePixels2).position(0);
        GLES20.glTexImage2D ( GLES20.GL_TEXTURE_CUBE_MAP_POSITIVE_Y, 0, GLES20.GL_RGB, 1, 1, 0,
                GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, cubePixels );

        // Load the cube face - Negative Y
        cubePixels.put(cubePixels3).position(0);
        GLES20.glTexImage2D ( GLES20.GL_TEXTURE_CUBE_MAP_NEGATIVE_Y, 0, GLES20.GL_RGB, 1, 1, 0,
                GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, cubePixels );

        // Load the cube face - Positive Z
        cubePixels.put(cubePixels4).position(0);
        GLES20.glTexImage2D ( GLES20.GL_TEXTURE_CUBE_MAP_POSITIVE_Z, 0, GLES20.GL_RGB, 1, 1, 0,
                GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, cubePixels );

        // Load the cube face - Negative Z
        cubePixels.put(cubePixels5).position(0);
        GLES20.glTexImage2D ( GLES20.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z, 0, GLES20.GL_RGB, 1, 1, 0,
                GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, cubePixels );

        // Set the filtering mode
        GLES20.glTexParameteri ( GLES20.GL_TEXTURE_CUBE_MAP, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST );
        GLES20.glTexParameteri ( GLES20.GL_TEXTURE_CUBE_MAP, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST );

        return textureId[0];
    }


    public static int loadShader(String strSource, int iType)
    {
        int[] compiled = new int[1];
        int iShader = GLES20.glCreateShader(iType);
        GLES20.glShaderSource(iShader, strSource);
        GLES20.glCompileShader(iShader);
        GLES20.glGetShaderiv(iShader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.d("Load Shader Failed", "Compilation\n"+GLES20.glGetShaderInfoLog(iShader));
            return 0;
        }
        return iShader;
    }

    public static int loadProgram(String strVSource, String strFSource)
    {
        int iVShader;
        int iFShader;
        int iProgId;
        int[] link = new int[1];
        iVShader = loadShader(strVSource, GLES20.GL_VERTEX_SHADER);
        if (iVShader == 0)
        {
            Log.d("Load Program", "Vertex Shader Failed");
            return 0;
        }
        iFShader = loadShader(strFSource, GLES20.GL_FRAGMENT_SHADER);
        if(iFShader == 0)
        {
            Log.d("Load Program", "Fragment Shader Failed");
            return 0;
        }

        iProgId = GLES20.glCreateProgram();

        GLES20.glAttachShader(iProgId, iVShader);
        GLES20.glAttachShader(iProgId, iFShader);

        GLES20.glLinkProgram(iProgId);

        GLES20.glGetProgramiv(iProgId, GLES20.GL_LINK_STATUS, link, 0);
        if (link[0] <= 0) {
            Log.d("Load Program", "Linking Failed");
            return 0;
        }
        GLES20.glDeleteShader(iVShader);
        GLES20.glDeleteShader(iFShader);
        return iProgId;
    }

}