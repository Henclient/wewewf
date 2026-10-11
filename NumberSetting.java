package com.henclient.reborn.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A bounded numeric setting. Values are clamped to [min, max] and snapped to the step size. */
public final class NumberSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;

    public NumberSetting(String key, String label, double defaultValue, double min, double max, double step) {
        super(key, label, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
        set(defaultValue);
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double step() {
        return step;
    }

    public double getDouble() {
        return value;
    }

    public float getFloat() {
        return value.floatValue();
    }

    public int getInt() {
        return (int) Math.round(value);
    }

    public void set(double newValue) {
        double v = newValue;
        if (step > 0) {
            v = Math.round((v - min) / step) * step + min;
        }
        this.value = Math.max(min, Math.min(max, v));
    }

    /** Position of the current value within the range, from 0 to 1. */
    public double ratio() {
        return max <= min ? 0 : (value - min) / (max - min);
    }

    public void setRatio(double ratio) {
        set(min + Math.max(0, Math.min(1, ratio)) * (max - min));
    }

    /** Value formatted for display (no trailing zeros for whole steps). */
    public String display() {
        if (step >= 1 || step == Math.floor(step)) {
            return Long.toString(Math.round(value));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            set(element.getAsDouble());
        }
    }
}
