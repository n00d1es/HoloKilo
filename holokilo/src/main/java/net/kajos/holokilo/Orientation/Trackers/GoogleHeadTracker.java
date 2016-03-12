/*
 * Copyright 2014 Google Inc. All Rights Reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.kajos.holokilo.Orientation.Trackers;

import android.content.Context;
import android.hardware.SensorManager;
import android.view.Display;
import android.view.WindowManager;
import com.google.vrtoolkit.cardboard.sensors.*;
import net.kajos.holokilo.Config;
import net.kajos.holokilo.Orientation.Matrix3x3;
import net.kajos.holokilo.Orientation.Tracker;
import net.kajos.holokilo.Orientation.Vector3;
import net.kajos.holokilo.Util.LowPassFilter;

/**
 * Provides head tracking information from the device IMU.
 */
public class GoogleHeadTracker extends Tracker
{
    // Tracker that wraps around Google Cardboard's headtracker.

    private HeadTracker headTracker;
    private LowPassFilter[] filters = new LowPassFilter[16];

    public GoogleHeadTracker(SensorEventProvider sensorEventProvider, Clock clock, Display display) {
        headTracker = new HeadTracker(sensorEventProvider, clock, display);
        for (int i = 0; i < 16; i++) {
            filters[i] = new LowPassFilter(Config.LP_FOR_GYRO);
        }
    }

    public static GoogleHeadTracker createFromContext(Context context) {
        SensorManager sensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
        Display display = ((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        GoogleHeadTracker result = new GoogleHeadTracker(new DeviceSensorLooper(sensorManager), new SystemClock(), display);
        result.init();
        return result;
    }

    public void init() {
        tmp.setToRotation(new Vector3(1, 0, 0), -90);
        flip = new Matrix3x3(1,0,0, 0,-1,0, 0,0,1);
        Matrix3x3.mult(tmp, flip, initial);
    }

    float matrix[] = new float[16];
    public void getLastHeadView(Matrix3x3 headView) {
        headTracker.getLastHeadView(matrix, 0);
        for (int i = 0; i < 16; i++) {
            matrix[i] = filters[i].get(matrix[i]);
        }
        for (int c = 0; c < 3; c++)
            for (int r = 0; r < 3; r++) {
                headView.set(r, c, matrix[c * 4 + r]);
            }
    }

    @Override
    public void stopTracking() {
        headTracker.stopTracking();
    }

    @Override
    public void startTracking() {
        headTracker.startTracking();
    }
}