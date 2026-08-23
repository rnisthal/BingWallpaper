package me.liaoheng.wallpaper.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import me.liaoheng.wallpaper.BaseTest;
import me.liaoheng.wallpaper.TestApplication;
import me.liaoheng.wallpaper.model.Wallpaper;

import static org.junit.Assert.assertFalse;

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
}
