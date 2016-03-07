package net.kajos.holokilo.Orientation;

/**
 * Created by kajos on 9-12-15.
 */
public abstract class Tracker {
    protected Matrix3x3 initial = new Matrix3x3();

    protected Matrix3x3 flip = new Matrix3x3();

    protected Matrix3x3 tmp = new Matrix3x3();
    protected Matrix3x3 tmp2 = new Matrix3x3();
    protected Matrix3x3 tmp3 = new Matrix3x3();

    private Matrix3x3 base = new Matrix3x3();

    public abstract void init();

    public void setBaseRotation() {
        getLastHeadView(base);
    }

    public void getCorrectedHeadView(Matrix3x3 headView, Matrix3x3 headViewRaw) {
        getLastHeadView(tmp2);
        headViewRaw.set(tmp2);
        tmp2.transpose();
        Matrix3x3.mult(base, tmp2, tmp3);
        Matrix3x3.mult(tmp3, initial, tmp);
        headView.set(tmp);
    }

    public Matrix3x3 differenceHeadView(Matrix3x3 a, Matrix3x3 b) {
        a.invert(tmp);
        tmp2.set(b);
        Matrix3x3.mult(tmp, tmp2, tmp3);
        return tmp3;
    }

    public abstract void getLastHeadView(Matrix3x3 headView);

    public abstract void stopTracking() ;
    public abstract void startTracking();
}
