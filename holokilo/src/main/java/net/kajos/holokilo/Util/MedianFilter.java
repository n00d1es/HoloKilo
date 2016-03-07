package net.kajos.holokilo.Util;

import java.util.Arrays;

/**
 * Created by kajos on 15-12-15.
 */
public class MedianFilter {
    public float[] values;
    public float[] sortedValues;
    private boolean empty = false;

    public MedianFilter(int size) {
        values = new float[size];
        sortedValues = new float[size];
        empty();
    }

    public void add(float value) {
        if (value != value)
            return;

        if (empty) {
            empty = false;
            for (int i = 0; i < values.length; i++) {
                values[i] = value;
                sortedValues[i] = value;
            }
        } else {
            for (int i = values.length - 1; i > 0; i--) {
                values[i] = values[i - 1];
            }

            values[0] = value;

            System.arraycopy(values, 0, sortedValues, 0, values.length);
            Arrays.sort(sortedValues);
        }
    }

    public float get() {
        return sortedValues[sortedValues.length / 2];
    }

    public void empty() {
        empty = true;
    }

    public float get(float value) {
        add(value);
        return sortedValues[sortedValues.length / 2];
    }
}
