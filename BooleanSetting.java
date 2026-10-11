package com.henclient.reborn.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String key, String label, boolean defaultValue) {
        super(key, label, defaultValue);
    }

    public boolean isOn() {
        return value;
    }

    public void set(boolean newValue) {
        this.value = newValue;
    }

    public void toggle() {
        this.value = !this.value;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            this.value = element.getAsBoolean();
        }
    }
}
