package net.kajos.holokilo.Processors;

import net.kajos.holokilo.Orientation.Matrix3x3;

/**
 * Created by kajos on 2-12-15.
 */
public class FramebufferGyro extends Framebuffer {
    // Framebuffer with extra information
    private Matrix3x3 matrix = new Matrix3x3();
    private double movement = 0;
    private long timestamp = -1;
    private boolean flashOff = false;
    private boolean stableFrame = false;
    private boolean compareSizes = false;

    public FramebufferGyro(int width, int height) {
        super(width, height);
    }

    public void setMatrix(Matrix3x3 matrix) {
        this.matrix.set(matrix);
    }

    public void setMovement(double diff) {
        movement = diff;
    }

    public void setFlashOff(boolean state){
        this.flashOff = state;
    }

    public boolean getFlashOff() {
        return flashOff;
    }

    public void setStableFrame(boolean state) {
        stableFrame = state;
    }

    public boolean getStableFrame() {
        return stableFrame;
    }

    public void setCompareSizes(boolean state) {
        compareSizes = state;
    }

    public boolean getCompareSizes() {
        return compareSizes;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Matrix3x3 getMatrix() {
        return matrix;
    }

    public double getMovement() {
        return movement;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
