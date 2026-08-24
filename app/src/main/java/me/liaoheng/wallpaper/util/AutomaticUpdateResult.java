package me.liaoheng.wallpaper.util;

public enum AutomaticUpdateResult {
    APPLIED,
    UNCHANGED,
    SKIPPED,
    RETRYABLE_FAILURE,
    FAILURE;

    public boolean shouldRetryTimer(boolean taskUndone) {
        return this == UNCHANGED || this == RETRYABLE_FAILURE
                || this == APPLIED && taskUndone;
    }
}
