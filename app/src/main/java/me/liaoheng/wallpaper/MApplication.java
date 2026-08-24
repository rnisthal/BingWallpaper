package me.liaoheng.wallpaper;

import android.app.Application;
import android.app.ActivityManager;
import android.os.Build;
import android.os.Process;

import java.io.BufferedReader;
import java.io.FileReader;

import androidx.annotation.NonNull;
import androidx.startup.AppInitializer;
import androidx.work.Configuration;

import com.github.liaoheng.common.Common;
import com.github.liaoheng.common.util.L;
import com.github.liaoheng.common.util.LanguageContextWrapper;

import net.danlew.android.joda.JodaTimeInitializer;

import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import me.liaoheng.wallpaper.data.db.DBHelper;
import me.liaoheng.wallpaper.util.CacheUtils;
import me.liaoheng.wallpaper.util.BingWallpaperJobManager;
import me.liaoheng.wallpaper.util.Constants;
import me.liaoheng.wallpaper.util.CrashReportHandle;
import me.liaoheng.wallpaper.util.LogDebugFileUtils;
import me.liaoheng.wallpaper.util.NetUtils;
import me.liaoheng.wallpaper.util.NotificationUtils;
import me.liaoheng.wallpaper.util.SettingTrayPreferences;
import me.liaoheng.wallpaper.util.TasksUtils;
import me.liaoheng.wallpaper.util.WorkerManager;

/**
 * @author liaoheng
 * @version 2016-09-19 11:34
 */
public class MApplication extends Application implements Configuration.Provider {

    @Override
    public void onCreate() {
        super.onCreate();
        LanguageContextWrapper.init(this);
        Common.init(this, Constants.PROJECT_NAME, BuildConfig.DEBUG);
        AppInitializer.getInstance(this).initializeComponent(JodaTimeInitializer.class);
        SettingTrayPreferences.init(getApplicationContext());
        LogDebugFileUtils.init(getApplicationContext());
        TasksUtils.init(getApplicationContext());
        boolean mainProcess = isMainProcess();
        new Thread(() -> {
            boolean settingsReady = true;
            if (mainProcess) {
                try {
                    DBHelper.toChangeDataStore(getApplicationContext());
                } catch (Throwable throwable) {
                    settingsReady = false;
                    L.alog().w("MApplication", throwable, "settings migration failure");
                }
            }
            NetUtils.get().init(getApplicationContext());
            if (mainProcess && settingsReady) {
                BingWallpaperJobManager.reconcile(getApplicationContext());
            }
            CacheUtils.init(getApplicationContext());
            CrashReportHandle.init(getApplicationContext());
        }).start();
        RxJavaPlugins.setErrorHandler(throwable -> L.alog().w("RxJavaPlugins", throwable));
        Constants.Config.isPhone = getString(R.string.screen_type).equals("phone");

        NotificationUtils.createNotificationChannels(this);
    }

    private boolean isMainProcess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return getPackageName().equals(Application.getProcessName());
        }
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (manager != null) {
            java.util.List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
            if (processes != null) {
                for (ActivityManager.RunningAppProcessInfo process : processes) {
                    if (process.pid == Process.myPid()) {
                        return getPackageName().equals(process.processName);
                    }
                }
            }
        }
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/cmdline"))) {
            String processName = reader.readLine();
            if (processName != null) {
                int terminator = processName.indexOf('\0');
                return getPackageName().equals(
                        terminator >= 0 ? processName.substring(0, terminator) : processName);
            }
        } catch (Throwable ignored) {
        }
        return getPackageName().equals(getApplicationInfo().processName);
    }

    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        return WorkerManager.getConfig(BuildConfig.DEBUG);
    }
}
