package me.liaoheng.wallpaper.util;

import androidx.work.WorkInfo;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.joda.time.LocalDate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import me.liaoheng.wallpaper.BaseTest;
import me.liaoheng.wallpaper.TestApplication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author liaoheng
 * @version 2018-01-21 17:29
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = TestApplication.class)
public class BingWallpaperUtilsTest extends BaseTest {

    @Test
    public void checkTimeTest() {
        DateTime dateTime = DateTime.now().minusHours(1);
        DateTime dateTime1 = BingWallpaperUtils.checkTime(dateTime.toLocalTime());
        assertEquals(dateTime1.getDayOfMonth(), dateTime.plusDays(1).getDayOfMonth());
    }

    @Test
    public void resolutionMatchesScreenOrientationTest() {
        for (int portrait = 0; portrait < 10; portrait += 2) {
            assertEquals(portrait, BingWallpaperUtils.getResolutionValue(portrait + 1, true));
            assertEquals(portrait + 1, BingWallpaperUtils.getResolutionValue(portrait, false));
        }
        assertEquals(0, BingWallpaperUtils.getResolutionValue(10, true));
        assertEquals(0, BingWallpaperUtils.getResolutionValue(11, true));
        assertEquals(10, BingWallpaperUtils.getResolutionValue(10, false));
        assertEquals(11, BingWallpaperUtils.getResolutionValue(11, false));
    }

    @Test
    public void earliestTimeAndLegacyIdentityAreContentBased() {
        assertFalse(BingWallpaperUtils.isAtOrAfterEarliestTime(
                new LocalTime(2, 29), new LocalTime(2, 30)));
        assertTrue(BingWallpaperUtils.isAtOrAfterEarliestTime(
                new LocalTime(2, 30), new LocalTime(2, 30)));
        assertTrue(BingWallpaperUtils.legacyUrlMatchesBase(
                "https://www.bing.com/th?id=OHR.Example_EN-US_1920x1080.jpg",
                "/th?id=OHR.Example_EN-US"));
        assertFalse(BingWallpaperUtils.legacyUrlMatchesBase(
                "https://www.bing.com/th?id=OHR.Other_EN-US_1920x1080.jpg",
                "/th?id=OHR.Example_EN-US"));
    }

    @Test
    public void dayCompletesOnlyAfterAStoredIdentityChanges() {
        assertFalse(BingWallpaperUtils.shouldCompleteDay("", "/th?id=OHR.Today"));
        assertFalse(BingWallpaperUtils.shouldCompleteDay(
                "/th?id=OHR.Today", "/th?id=OHR.Today"));
        assertFalse(BingWallpaperUtils.shouldCompleteDay("/th?id=OHR.Yesterday", ""));
        assertTrue(BingWallpaperUtils.shouldCompleteDay(
                "/th?id=OHR.Yesterday", "/th?id=OHR.Today"));
    }

    @Test
    public void activeWorkStatesAreRunningOrEnqueuedOnly() {
        assertTrue(WorkerManager.isActiveWorkState(WorkInfo.State.ENQUEUED));
        assertTrue(WorkerManager.isActiveWorkState(WorkInfo.State.RUNNING));
        assertFalse(WorkerManager.isActiveWorkState(WorkInfo.State.CANCELLED));
    }

    @Test
    public void timerWorkIsScopedToItsTriggerDate() {
        LocalDate today = new LocalDate(2026, 8, 23);
        assertFalse(WorkerManager.timerWorkName(today)
                .equals(WorkerManager.timerWorkName(today.plusDays(1))));
        assertEquals(AutomaticUpdateSource.TIMER, AutomaticUpdateSource.from("timer"));
    }
}
