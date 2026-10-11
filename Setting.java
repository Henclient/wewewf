package com.henclient.reborn.module.setting;

import com.google.gson.JsonElement;

/** A single persisted, user-configurable option of a module. */
public abstract class Setting<T> {
    private final String key;
    private final String label;
    protected T value;

    protected Setting(String key, String label, T defaultValue) {
        this.key = key;
        this.label = label;
        this.value = defaultValue;
    }

    /** Stable identifier used in the config file. */
    public String key() {
        return key;
    }

    /** Human readable name shown in the GUI. */
    public String label() {
        return label;
    }

    public T get() {
        return value;
    }

    public abstract JsonElement toJson();

    public abstract void fromJson(JsonElement element);
}
