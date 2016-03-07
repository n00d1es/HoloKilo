package net.kajos.holokilo.Orientation.Trackers;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import net.kajos.holokilo.Config;
import net.kajos.holokilo.Orientation.Matrix3x3;
import net.kajos.holokilo.Orientation.Tracker;
import net.kajos.holokilo.Orientation.Vector3;
import net.kajos.holokilo.Util.LowPassFilter;

/**
 * Created by kajos on 7-12-15.
 */
public class AccCompassTracker extends Tracker implements SensorEventListener {

    /**
     * Compass values
     */
    private float[] magnitudeValues = new float[3];

    /**
     * Accelerometer values
     */
    private float[] accelerometerValues = new float[3];

    private SensorManager mSensorManager = null;
    private final float[] currentOrientationRotationMatrix = new float[16];

    private LowPassFilter filters[] = new LowPassFilter[6];

    public AccCompassTracker(Context context) {
        for (int i = 0; i < filters.length; i++)
            filters[i] = new LowPassFilter(Config.SOFTEN_NON_GYRO_ROTATION);

        init();

        // get sensorManager and initialise sensor listeners
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        initListeners();
    }

    public static Tracker createFromContext(Context context) {
        return new AccCompassTracker(context);
    }

    @Override
    public void init() {
        tmp.setToRotation(new Vector3(1, 0, 0), -90);
//        flip = new Matrix3x3(0,1,0, 1,0,0, 0,0,-1);
        flip = new Matrix3x3(0,-1,0, -1,0,0, 0,0,1);
        Matrix3x3.mult(tmp, flip, initial);
    }

    @Override
    public void getLastHeadView(Matrix3x3 headView) {
        for (int c = 0; c < 3; c++)
            for (int r = 0; r < 3; r++) {
                headView.set(r,c, currentOrientationRotationMatrix[c*4+r]);
            }
    }

    @Override
    public void stopTracking() {
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void startTracking() {
        initListeners();
    }

    public void initListeners(){
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_FASTEST);

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // we received a sensor event. it is a good practice to check
        // that we received the proper event
        float inv = 1f - Config.SOFTEN_NON_GYRO_ROTATION;
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            magnitudeValues[0] = filters[0].get(event.values[0]);
            magnitudeValues[1] = filters[1].get(event.values[0]);
            magnitudeValues[2] = filters[2].get(event.values[0]);
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelerometerValues[0] = filters[3].get(event.values[0]);
            accelerometerValues[1] = filters[4].get(event.values[1]);
            accelerometerValues[2] = filters[5].get(event.values[2]);
        }

        if (magnitudeValues != null && accelerometerValues != null) {
            float[] i = new float[16];

            // Fuse accelerometer with compass
            SensorManager.getRotationMatrix(currentOrientationRotationMatrix, i, accelerometerValues,
                    magnitudeValues);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
