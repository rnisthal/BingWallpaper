package me.liaoheng.wallpaper.util;

import androidx.annotation.Nullable;

public enum AutomaticUpdateSource {
    PERIODIC("periodic"),
    TIMER("timer");

    private final String value;

    AutomaticUpdateSource(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    @Nullable
    public static AutomaticUpdateSource from(String value) {
        for (AutomaticUpdateSource source : values()) {
            if (source.value.equals(value)) {
                return source;
            }
        }
        return null;
    }
}
