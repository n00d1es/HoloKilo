package net.kajos.holokilo.Util;

/**
 * Created by kajos on 15-12-15.
 */
public class LowPassFilter {
    private float prev = 0f;
    public float alpha;
    private boolean empty = true;

    public LowPassFilter(float alpha) {
        setAlpha(alpha);
        empty();
    }

    public void set(float value) {
        prev = value;
    }

    public boolean isEmpty() {
        return empty;
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    public float get(float value) {
        if (value != value)
            return get();

        if (empty) {
            empty = false;
            prev = value;
            return value;
        } else {
            prev = get(prev, value);
            return prev;
        }
    }

    public float get() {
        return prev;
    }

    public void empty() {
        empty = true;
    }

    private float get(float prev, float value) {
        return prev + alpha * (value - prev);
    }
}
