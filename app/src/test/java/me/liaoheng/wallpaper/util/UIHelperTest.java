package me.liaoheng.wallpaper.util;

import android.graphics.Point;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

import me.liaoheng.wallpaper.BaseTest;
import me.liaoheng.wallpaper.TestApplication;

@RunWith(RobolectricTestRunner.class)
@Config(application = TestApplication.class)
public class UIHelperTest extends BaseTest {

    @Test
    public void cropSizeMatchesTargetAspectRatioWithoutUpscaling() {
        assertEquals(new Point(864, 1920), UIHelper.getCropSize(1080, 1920, 1080, 2400));
        assertEquals(new Point(1920, 864), UIHelper.getCropSize(1920, 1080, 2400, 1080));
        assertEquals(new Point(800, 450), UIHelper.getCropSize(800, 480, 1920, 1080));
        assertEquals(new Point(1920, 1080), UIHelper.getCropSize(1920, 1080, 1280, 720));
    }
}
