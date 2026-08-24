package me.liaoheng.wallpaper.service;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ListenableWorker;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.github.liaoheng.common.util.L;

import java.util.Map;

import me.liaoheng.wallpaper.model.Config;
import me.liaoheng.wallpaper.model.Wallpaper;
import me.liaoheng.wallpaper.util.BingWallpaperUtils;
import me.liaoheng.wallpaper.util.AutomaticUpdateResult;
import me.liaoheng.wallpaper.util.AutomaticUpdateSource;
import me.liaoheng.wallpaper.util.LogDebugFileUtils;
import me.liaoheng.wallpaper.util.Settings;
import me.liaoheng.wallpaper.util.WorkerManager;

/**
 * @author liaoheng
 * @version 2019-03-18 10:42
 */
public class BingWallpaperWorker extends Worker {
    private final String TAG = BingWallpaperWorker.class.getSimpleName();
    private final SetWallpaperDelegate mSetWallpaperDelegate;

    public BingWallpaperWorker(@NonNull Context appContext,
            @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
        mSetWallpaperDelegate = new SetWallpaperDelegate(appContext, TAG);
    }

    @NonNull
    @Override
    public ListenableWorker.Result doWork() {
        L.alog().d(TAG, "action worker id : %s", getId());
        if (Settings.isEnableLogProvider(getApplicationContext())) {
            LogDebugFileUtils.get()
                    .i(TAG, "action worker id : %s", getId());
        }
        Map<String, Object> map = getInputData().getKeyValueMap();
        String sourceValue = getInputData().getString(WorkerManager.INPUT_AUTOMATIC_SOURCE);
        AutomaticUpdateSource source = AutomaticUpdateSource.from(sourceValue);
        String triggerDate = getInputData().getString(WorkerManager.INPUT_TRIGGER_DATE);
        boolean automatic = source != null;
        if (automatic && !WorkerManager.isAutomaticSourceCurrent(
                getApplicationContext(), source, triggerDate)) {
            return Result.success();
        }
        Config config = Config.to(map);
        if (config == null) {
            if (!automatic) {
                return Result.failure();
            }
            config = BingWallpaperUtils.checkRunningToConfig(getApplicationContext(), TAG);
            if (config == null) {
                return source == AutomaticUpdateSource.TIMER
                        && WorkerManager.isAutomaticSourceCurrent(
                        getApplicationContext(), source, triggerDate)
                        ? Result.retry()
                        : Result.success();
            }
        }
        if (!automatic && config.isBackground()) {
            return Result.success();
        }
        AutomaticUpdateResult updateResult = mSetWallpaperDelegate.setWallpaper(Wallpaper.to(map), config, true,
                automatic ? () -> WorkerManager.isAutomaticSourceCurrent(
                        getApplicationContext(), source, triggerDate) : null);
        if (source == AutomaticUpdateSource.TIMER
                && WorkerManager.isAutomaticSourceCurrent(getApplicationContext(), source, triggerDate)) {
            boolean taskUndone = updateResult == AutomaticUpdateResult.APPLIED
                    && BingWallpaperUtils.isTaskUndone(getApplicationContext());
            if (updateResult.shouldRetryTimer(taskUndone)) {
                return Result.retry();
            }
        }
        return Result.success();
    }
}
