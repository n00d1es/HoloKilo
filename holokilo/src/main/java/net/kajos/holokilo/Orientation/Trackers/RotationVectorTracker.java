package net.kajos.holokilo.Orientation.Trackers;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import net.kajos.holokilo.Config;
import net.kajos.holokilo.Orientation.Matrix3x3;
import net.kajos.holokilo.Orientation.Tracker;
import net.kajos.holokilo.Orientation.Vector3;

/**
 * Created by kajos on 5-12-15.
 */
public class RotationVectorTracker extends Tracker implements SensorEventListener {
    // Tracker that uses Android's RotationVector to track
    protected Matrix3x3 initial = new Matrix3x3();

    protected Matrix3x3 flip = new Matrix3x3();

    protected Matrix3x3 tmp = new Matrix3x3();
    protected Matrix3x3 tmp2 = new Matrix3x3();
    protected Matrix3x3 tmp3 = new Matrix3x3();

    private Matrix3x3 base = new Matrix3x3();

    protected SensorManager mSensorManager = null;
    protected final float[] currentOrientationRotationMatrix = new float[16];
    protected final Object syncToken = new Object();

    public void init() {
        tmp.setToRotation(new Vector3(1, 0, 0), -90);
        flip = new Matrix3x3(0,-1,0, -1,0,0, 0,0,1);
        Matrix3x3.mult(flip, tmp, initial);
    }

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
        Log.d(Config.TAG, " Roll:" + headView.roll() + " Yaw:" + headView.yaw() + " Pitch:" + headView.pitch());
    }

    public Matrix3x3 differenceHeadView(Matrix3x3 a, Matrix3x3 b) {
        tmp.set(a);
        tmp.transpose();
        tmp2.set(b);
        Matrix3x3.mult(tmp, tmp2, tmp3);
        tmp3.transpose();
        return tmp3;
    }

    public void getLastHeadView(Matrix3x3 headView) {
        for (int c = 0; c < 3; c++)
            for (int r = 0; r < 3; r++) {
                headView.set(r,c, currentOrientationRotationMatrix[c*4+r]);
            }
    }

    public RotationVectorTracker(Context context) {
        init();

        // get sensorManager and initialise sensor listeners
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        initListeners();
    }

    public static RotationVectorTracker createFromContext(Context context) {
        return new RotationVectorTracker(context);
    }

    public void stopTracking() {
        // unregister sensor listeners to prevent the activity from draining the device's battery.
        mSensorManager.unregisterListener(this);
    }

    public void startTracking() {
        // restore the sensor listeners when user resumes the application.
        initListeners();
    }

    // This function registers sensor listeners for the accelerometer, magnetometer and gyroscope.
    public void initListeners(){
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                SensorManager.SENSOR_DELAY_FASTEST);

    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        // that we received the proper event
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            // convert the rotation-vector to a 4x4 matrix. the matrix
            // is interpreted by Open GL as the inverse of the
            // rotation-vector, which is what we want.
            SensorManager.getRotationMatrixFromVector(currentOrientationRotationMatrix, event.values);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
