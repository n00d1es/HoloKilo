package net.kajos.holokilo.Util;

/**
 * Created by kajos on 15-12-15.
 */
public class LowPassFilter {
    private float prev = 0f;
    public float alpha;
    public float extraMultiplier = 1f;
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

    // Additional multiplier which is used on alpha.
    public void setExtraMultiplier(float alpha) {
        this.extraMultiplier = alpha;
    }

    public float get(float value) {
        if (value != value)
            return get();

        if (empty) {
            empty = false;
            prev = value;
            return value;
        } else {
            prev = get(prev, value, alpha);
            return prev;
        }
    }

    public float get() {
        return prev;
    }

    public void empty() {
        empty = true;
    }

    private float get(float prev, float value, float overrideAlpha) {
        return prev + overrideAlpha * extraMultiplier * (value - prev);
    }

    public float get(float value, float overrideAlpha) {
        if (value != value)
            return get();

        if (empty) {
            empty = false;
            prev = value;
            return value;
        } else {
            prev = get(prev, value, overrideAlpha);
            return prev;
        }
    }
}
