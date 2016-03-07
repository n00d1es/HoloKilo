package net.kajos.holokilo.Orientation.Trackers;

import net.kajos.holokilo.Orientation.Matrix3x3;
import net.kajos.holokilo.Orientation.Tracker;

/**
 * Created by Kajos on 21-1-2016.
 */
public class DummyTracker extends Tracker {
    @Override
    public void init() {

    }

    @Override
    public void getLastHeadView(Matrix3x3 headView) {
        headView.setIdentity();
    }

    @Override
    public void stopTracking() {

    }

    @Override
    public void startTracking() {

    }
}
