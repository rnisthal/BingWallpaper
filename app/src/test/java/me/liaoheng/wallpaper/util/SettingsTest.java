package me.liaoheng.wallpaper.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import me.liaoheng.wallpaper.BaseTest;
import me.liaoheng.wallpaper.TestApplication;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(application = TestApplication.class)
public class SettingsTest extends BaseTest {
    @Test
    public void completionRecoveryRequiresTheSameWallpaperAndDate() {
        assertTrue(Settings.wasWallpaperApplied("base", "base", "2026-08-23", "2026-08-23"));
        assertFalse(Settings.wasWallpaperApplied("base", "base", "2026-08-24", "2026-08-23"));
        assertFalse(Settings.wasWallpaperApplied("other", "base", "2026-08-23", "2026-08-23"));
        assertFalse(Settings.wasWallpaperApplied("base", "base", "2026-08-23", ""));
    }
}
