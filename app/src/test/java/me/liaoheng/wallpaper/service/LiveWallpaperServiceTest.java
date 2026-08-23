package me.liaoheng.wallpaper.service;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import me.liaoheng.wallpaper.BaseTest;
import me.liaoheng.wallpaper.TestApplication;
import me.liaoheng.wallpaper.model.Wallpaper;
import me.liaoheng.wallpaper.util.BingWallpaperUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(application = TestApplication.class)
public class LiveWallpaperServiceTest extends BaseTest {

    @Test
    public void downloadBitmapEqualityUsesBothImages() {
        Wallpaper first = new Wallpaper("", "", "first", "", "", "", "");
        Wallpaper second = new Wallpaper("", "", "second", "", "", "", "");
        first.setImageUrl("image");
        second.setImageUrl("image");
        me.liaoheng.wallpaper.model.Config config =
                new me.liaoheng.wallpaper.model.Config.Builder().build();
        LiveWallpaperService.DownloadBitmap a = new LiveWallpaperService.DownloadBitmap(first, config);
        LiveWallpaperService.DownloadBitmap b = new LiveWallpaperService.DownloadBitmap(second, config);
        a.width = b.width = 1080;
        a.height = b.height = 1920;

        assertFalse(a.eq(b));
    }

    @Test
    public void portraitDetectionFallsBackWhenSurfaceHasNoSize() {
        Context context = RuntimeEnvironment.getApplication();

        assertTrue(LiveWallpaperService.DownloadBitmap.isPortrait(context, 1080, 1920));
        assertFalse(LiveWallpaperService.DownloadBitmap.isPortrait(context, 1920, 1080));
        assertEquals(BingWallpaperUtils.isPortrait(context),
                LiveWallpaperService.DownloadBitmap.isPortrait(context, 0, 0));
    }
}
