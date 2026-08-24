package me.liaoheng.wallpaper.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.github.liaoheng.common.util.L;

import me.liaoheng.wallpaper.util.BingWallpaperJobManager;
import me.liaoheng.wallpaper.util.BingWallpaperAlarmManager;
import me.liaoheng.wallpaper.util.LogDebugFileUtils;
import me.liaoheng.wallpaper.util.Settings;
import me.liaoheng.wallpaper.util.WorkerManager;
import me.liaoheng.wallpaper.widget.AppWidget_5x1;
import me.liaoheng.wallpaper.widget.AppWidget_5x2;

import org.joda.time.LocalDate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 接收定时闹钟与开机自启事件
 *
 * @author liaoheng
 * @version 2016-09-19 15:49
 */
public class AutoSetWallpaperBroadcastReceiver extends BroadcastReceiver {

    public static final String ACTION = "me.liaoheng.wallpaper.ALARM_TASK_SCHEDULE";
    private static final ExecutorService RECEIVER_EXECUTOR = Executors.newSingleThreadExecutor();
    private final String TAG = AutoSetWallpaperBroadcastReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult pendingResult = goAsync();
        Context appContext = context.getApplicationContext();
        String action = intent.getAction();
        RECEIVER_EXECUTOR.execute(() -> {
            try {
                handle(appContext, action);
            } finally {
                pendingResult.finish();
            }
        });
    }

    private void handle(Context context, String action) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                AppWidget_5x1.start(context, null);
                AppWidget_5x2.start(context, null);
            }
            try {
                Settings.runIfAutomaticUpdateCurrent(
                        () -> Settings.isAutomaticUpdateEnabled(context)
                                && Settings.getJobType(context) == Settings.TIMER, () -> {
                    if (!BingWallpaperJobManager.enableTimer(context)) {
                        BingWallpaperAlarmManager.scheduleRetry(context);
                    }
                });
            } catch (Throwable throwable) {
                L.alog().w(TAG, throwable, "Timer restoration failure");
                scheduleRetryIfCurrent(context);
            }
            return;
        }
        if (ACTION.equals(action)
                && Settings.isAutomaticUpdateEnabled(context)) {
            try {
                Settings.runIfAutomaticUpdateCurrent(
                        () -> Settings.isAutomaticUpdateEnabled(context)
                                && Settings.getJobType(context) == Settings.TIMER, () -> {
                    L.alog().d(TAG, "timer : %s", action);
                    if (Settings.isEnableLog(context)) {
                        LogDebugFileUtils.get().i(TAG, "timer : %s", action);
                    }
                    WorkerManager.enqueueTimer(context, LocalDate.now(), false).getResult().get();
                    if (!BingWallpaperAlarmManager.scheduleNext(context)) {
                        BingWallpaperAlarmManager.scheduleRetry(context);
                    }
                });
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                scheduleRetryIfCurrent(context);
            } catch (Throwable exception) {
                L.alog().w(TAG, exception, "timer enqueue failure");
                scheduleRetryIfCurrent(context);
            }
        }
    }

    private void scheduleRetryIfCurrent(Context context) {
        try {
            Settings.runIfAutomaticUpdateCurrent(
                    () -> Settings.isAutomaticUpdateEnabled(context)
                            && Settings.getJobType(context) == Settings.TIMER,
                    () -> BingWallpaperAlarmManager.scheduleRetry(context));
        } catch (Throwable throwable) {
            L.alog().w(TAG, throwable, "timer retry scheduling failure");
        }
    }
}
