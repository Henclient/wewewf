package com.henclient.reborn.module;

public enum Category {
    HUD("HUD & Info"),
    EXPLORATION("Exploration"),
    COMBAT("Combat");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
