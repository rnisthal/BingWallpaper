package me.liaoheng.wallpaper.util;

import android.app.Activity;
import android.app.WallpaperManager;
import android.app.WallpaperInfo;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.os.Handler;
import android.widget.Toast;

import com.github.liaoheng.common.util.L;
import com.github.liaoheng.common.util.YNCallback;

import org.joda.time.LocalTime;
import org.joda.time.LocalDate;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import me.liaoheng.wallpaper.R;
import me.liaoheng.wallpaper.service.LiveWallpaperService;

/**
 * @author liaoheng
 * @version 2017-12-21 15:25
 */
public class BingWallpaperJobManager {
    private static final String TAG = BingWallpaperJobManager.class.getSimpleName();
    public static final int PENDING_LIVE = -2;

    public static boolean disabled(Context context) {
        return disabled(context, false);
    }

    public static boolean disabled(Context context, boolean force) {
        for (int attempt = 0; attempt < 3; attempt++) {
            if (disableOnce(context)) {
                return true;
            }
        }
        return false;
    }

    private static boolean disableOnce(Context context) {
        try {
            Settings.runAutomaticUpdateTransition(() -> {
                if (!setLivePollingAndAwait(context, false)) {
                    throw new IllegalStateException("disable Live polling timed out");
                }
                Throwable stateFailure = null;
                try {
                    Settings.setJobTypeAsync(Settings.NONE).blockingAwait();
                } catch (Throwable throwable) {
                    stateFailure = throwable;
                }
                boolean workDisabled = WorkerManager.disabledAndAwait(context);
                BingWallpaperAlarmManager.disabled(context);
                try {
                    Settings.clearSchedulerFingerprint().blockingAwait();
                } catch (Throwable throwable) {
                    if (stateFailure == null) {
                        stateFailure = throwable;
                    }
                }
                if (stateFailure != null) {
                    throw stateFailure;
                }
                if (!workDisabled) {
                    throw new IllegalStateException("disable automatic work failed");
                }
            });
            return true;
        } catch (Throwable throwable) {
            L.alog().w(TAG, throwable, "disable job state failure");
            return false;
        }
    }

    @Deprecated
    public static void clear(Context context) {
        Settings.setJobType(context, Settings.NONE);
    }

    public static int enabled(Context context) {
        int ret = enabledJob(context);
        if (ret == PENDING_LIVE) {
            try {
                startLiveService(context);
            } catch (Throwable throwable) {
                L.alog().w(TAG, throwable, "start live wallpaper chooser failure");
                ret = Settings.NONE;
            }
        }
        if (ret == Settings.NONE) {
            Toast.makeText(context, R.string.enable_job_error, Toast.LENGTH_LONG).show();
        }
        return ret;
    }

    @Settings.JobType
    public static int enabledJob(Context context) {
        AtomicInteger result = new AtomicInteger(Settings.NONE);
        try {
            Settings.runIfAutomaticUpdateCurrent(
                    () -> Settings.isAutomaticUpdateEnabled(context),
                    () -> result.set(enabledJobLocked(context)));
        } catch (Throwable throwable) {
            L.alog().w(TAG, throwable, "enable job transition failure");
        }
        return result.get();
    }

    private static int enabledJobLocked(Context context) {
        try {
            int type = Settings.getAutomaticUpdateType(context);
            if (type == Settings.AUTOMATIC_UPDATE_TYPE_AUTO) {
                if (BingWallpaperUtils.isROMSystem()) {
                    int live = enableLiveService(context);
                    if (live != Settings.NONE) {
                        return live;
                    }
                    if (enableSystem(context)) {
                        return Settings.WORKER;
                    }
                } else {
                    if (enableSystem(context)) {
                        return Settings.WORKER;
                    }
                    int live = enableLiveService(context);
                    if (live != Settings.NONE) {
                        return live;
                    }
                }
                if (enableTimer(context)) {
                    return Settings.TIMER;
                }
            } else if (type == Settings.AUTOMATIC_UPDATE_TYPE_SYSTEM) {
                if (enableSystem(context)) {
                    return Settings.WORKER;
                }
            } else if (type == Settings.AUTOMATIC_UPDATE_TYPE_SERVICE) {
                return enableLiveService(context);
            } else if (type == Settings.AUTOMATIC_UPDATE_TYPE_TIMER) {
                if (enableTimer(context)) {
                    return Settings.TIMER;
                }
            }
        } catch (Throwable throwable) {
            L.alog().w(TAG, throwable, "enable job failure");
        }
        return Settings.NONE;
    }

    public static boolean enableSystem(Context context) {
        AtomicBoolean enabled = new AtomicBoolean(false);
        try {
            boolean current = Settings.runIfAutomaticUpdateCurrent(
                    () -> Settings.isAutomaticUpdateEnabled(context),
                    () -> enabled.set(enableSystemLocked(context)));
            return current && enabled.get();
        } catch (Throwable throwable) {
            L.alog().w(TAG, throwable, "enable worker transition failure");
            return false;
        }
    }

    private static boolean enableSystemLocked(Context context) {
        long time = TimeUnit.HOURS.toSeconds(Settings.getAutomaticUpdateInterval(context));
        int previousJobType = Settings.getJobType(context);
        if (previousJobType == Settings.LIVE_WALLPAPER && !setLivePollingAndAwait(context, false)) {
            return false;
        }
        try {
            Settings.setJobTypeAsync(Settings.WORKER).blockingAwait();
        } catch (Throwable throwable) {
            L.alog().w(TAG, throwable, "persist worker state failure");
            return false;
        }
        boolean enabled = WorkerManager.enabled(context, time);
        if (!enabled) {
            restoreJobType(previousJobType);
            return false;
        }
        WorkerManager.cancelTimer(context);
        BingWallpaperAlarmManager.disabled(context);
        setLivePolling(context, false);
        try {
            Settings.setSchedulerFingerprint(context, Settings.WORKER).blockingAwait();
        } catch (Throwable throwable) {
            L.alog().w(TAG, throwable, "persist worker scheduler fingerprint failure");
            WorkerManager.cancelPeriodic(context);
            restoreJobType(previousJobType);
            return false;
        }
        new Thread(() -> {
            if (Settings.isEnableLog(context)) {
                LogDebugFileUtils.get().i(TAG, "Enable scheduler interval time : %s", time);
            }
        }).start();
        L.alog().d(TAG, "enable scheduler interval time : %s", time);
        return true;
    }

    public static boolean enableTimer(Context context) {
        AtomicBoolean enabled = new AtomicBoolean(false);
        try {
            boolean current = Settings.runIfAutomaticUpdateCurrent(
                    () -> Settings.isAutomaticUpdateEnabled(context),
                    () -> enabled.set(enableTimerLocked(context)));
            return current && enabled.get();
        } catch (Throwable throwable) {
            L.alog().w(TAG, throwable, "enable timer transition failure");
            return false;
        }
    }

    private static boolean enableTimerLocked(Context context) {
        LocalTime updateTime = BingWallpaperUtils.getDayUpdateTime(context);
        int previousJobType = Settings.getJobType(context);
        if (previousJobType == Settings.LIVE_WALLPAPER && !setLivePollingAndAwait(context, false)) {
            return false;
        }
        try {
            Settings.setJobTypeAsync(Settings.TIMER).blockingAwait();
        } catch (Throwable throwable) {
            L.alog().w(TAG, throwable, "persist timer state failure");
            return false;
        }
        boolean enabled = BingWallpaperAlarmManager.enabled(context, updateTime);
        if (!enabled) {
            restoreJobType(previousJobType);
            return false;
        }
        LocalDate today = LocalDate.now();
        if (!WorkerManager.cancelTimerAndAwait(context, today)) {
            BingWallpaperAlarmManager.disabled(context);
            restoreJobType(previousJobType);
            return false;
        }
        try {
            if (BingWallpaperUtils.isAtOrAfterEarliestTime(LocalTime.now(), updateTime)
                    && BingWallpaperUtils.isTaskUndone(context)) {
                WorkerManager.enqueueTimer(context, today, true).getResult().get();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            BingWallpaperAlarmManager.disabled(context);
            restoreJobType(previousJobType);
            return false;
        } catch (Throwable throwable) {
            L.alog().w(TAG, throwable, "enqueue timer failure");
            BingWallpaperAlarmManager.disabled(context);
            restoreJobType(previousJobType);
            return false;
        }
        WorkerManager.cancelPeriodic(context);
        setLivePolling(context, false);
        try {
            Settings.setSchedulerFingerprint(context, Settings.TIMER).blockingAwait();
        } catch (Throwable throwable) {
            L.alog().w(TAG, throwable, "persist Timer scheduler fingerprint failure");
            BingWallpaperAlarmManager.disabled(context);
            WorkerManager.cancelTimerAndAwait(context, today);
            restoreJobType(previousJobType);
            return false;
        }
        new Thread(() -> {
            if (Settings.isEnableLog(context)) {
                LogDebugFileUtils.get().i(TAG, "Enable timer time : %s", updateTime.toString("HH:mm"));
            }
        }).start();
        L.alog().d(TAG, "enable timer time : %s", updateTime.toString("HH:mm"));
        return true;
    }

    public static int enableLiveService(Context context) {
        if (Settings.getAutoModeValue(context) == Constants.EXTRA_SET_WALLPAPER_MODE_LOCK) {
            return Settings.NONE;
        }
        try {
            if (isLiveWallpaperActive(context)) {
                AtomicBoolean enabled = new AtomicBoolean(false);
                boolean current = Settings.runIfAutomaticUpdateCurrent(
                        () -> Settings.isAutomaticUpdateEnabled(context), () -> {
                            Settings.setJobTypeAsync(Settings.LIVE_WALLPAPER).blockingAwait();
                            WorkerManager.disabled(context);
                            BingWallpaperAlarmManager.disabled(context);
                            setLivePolling(context, true);
                            Settings.setSchedulerFingerprint(context, Settings.LIVE_WALLPAPER).blockingAwait();
                            enabled.set(true);
                        });
                return current && enabled.get() ? Settings.LIVE_WALLPAPER : Settings.NONE;
            }
            AtomicBoolean pending = new AtomicBoolean(false);
            Settings.runIfAutomaticUpdateCurrent(
                    () -> Settings.isAutomaticUpdateEnabled(context), () -> pending.set(true));
            return pending.get() ? PENDING_LIVE : Settings.NONE;
        } catch (Throwable ignored) {
        }
        return Settings.NONE;
    }

    public static int LIVE_WALLPAPER_REQUEST_CODE = 0x99;

    public static void startLiveService(Context context) {
        Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(context, LiveWallpaperService.class));
        if (intent.resolveActivity(context.getPackageManager()) == null) {
            throw new android.content.ActivityNotFoundException("LiveWallpaperService");
        }
        if (!(context instanceof Activity) || Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("Live wallpaper chooser requires a foreground activity");
        }
        ((Activity) context).startActivityForResult(intent, LIVE_WALLPAPER_REQUEST_CODE);
    }

    public static void onActivityResult(Context context, int requestCode, int resultCode, YNCallback callback) {
        if (requestCode == LIVE_WALLPAPER_REQUEST_CODE) {
            if (Activity.RESULT_OK == resultCode && isLiveWallpaperActive(context)) {
                if (enableLiveService(context) != Settings.LIVE_WALLPAPER) {
                    if (callback != null) {
                        callback.onDeny();
                    }
                    return;
                }
                new Thread(() -> {
                    if (Settings.isEnableLog(context)) {
                        LogDebugFileUtils.get().i(TAG, "Enable live wallpaper");
                    }
                }).start();
                L.alog().d(TAG, "enable live wallpaper");
                if (callback != null) {
                    callback.onAllow();
                }
            } else {
                if (enableAutomaticFallback(context)) {
                    if (callback != null) {
                        callback.onAllow();
                    }
                } else if (callback != null) {
                    callback.onDeny();
                }
            }
        }
    }

    public static String check(Context context) {
        int jobType = Settings.getJobType(context);
        if (jobType == Settings.NONE) {
            return "none";
        } else if (jobType == Settings.LIVE_WALLPAPER) {
            if (checkLiveWallpaperService()) {
                return "live_wallpaper";
            }
            return "live_wallpaper_error";
        } else if (jobType == Settings.WORKER) {
            if (WorkerManager.isScheduled(context)) {
                return "worker";
            } else {
                return "worker_error";
            }
        } else if (jobType == Settings.TIMER) {
            return "timer(" + BingWallpaperUtils.getDayUpdateTime(context) + ")";
        }
        return String.valueOf(jobType);
    }

    public static boolean checkLiveWallpaperService() {
        long heartbeat = Settings.getLiveWallpaperHeartbeat();
        return heartbeat > 0 && (System.currentTimeMillis() - heartbeat <= Constants.DEF_LIVE_WALLPAPER_CHECK_PERIODIC);
    }

    public static boolean isLiveWallpaperActive(Context context) {
        WallpaperInfo info = WallpaperManager.getInstance(context).getWallpaperInfo();
        return info != null && new ComponentName(context, LiveWallpaperService.class).equals(info.getComponent());
    }

    public static boolean reconfigure(Context context) {
        if (!Settings.isAutomaticUpdateEnabled(context)) {
            return true;
        }
        int jobType = Settings.getJobType(context);
        if (jobType == Settings.WORKER) {
            return enableSystem(context);
        }
        if (jobType == Settings.TIMER) {
            return enableTimer(context);
        }
        if (jobType == Settings.LIVE_WALLPAPER) {
            return enableLiveService(context) == Settings.LIVE_WALLPAPER;
        }
        return false;
    }

    public static boolean restore(Context context, @Settings.JobType int jobType) {
        if (jobType == Settings.WORKER) {
            return enableSystem(context);
        }
        if (jobType == Settings.TIMER) {
            return enableTimer(context);
        }
        if (jobType == Settings.LIVE_WALLPAPER && isLiveWallpaperActive(context)) {
            return enableLiveService(context) == Settings.LIVE_WALLPAPER;
        }
        return jobType == Settings.NONE && disabled(context);
    }

    public static void reconcile(Context context) {
        try {
            Settings.runAutomaticUpdateTransition(() -> reconcileLocked(context));
        } catch (Throwable throwable) {
            L.alog().w(TAG, throwable, "automatic scheduler reconciliation failure");
        }
    }

    private static void reconcileLocked(Context context) {
        if (!Settings.isAutomaticUpdateEnabled(context)) {
            if (!disabled(context)) {
                throw new IllegalStateException("disabled scheduler reconciliation failed");
            }
            return;
        }
        int jobType = Settings.getJobType(context);
        boolean restored;
        if (jobType == Settings.WORKER) {
            boolean legacyCanceled = WorkerManager.cancelLegacyPeriodicAndAwait(context);
            restored = legacyCanceled && (Settings.isSchedulerFingerprintCurrent(context, jobType)
                    && WorkerManager.isScheduled(context) || enableSystem(context));
        } else if (jobType == Settings.TIMER) {
            restored = Settings.isSchedulerFingerprintCurrent(context, jobType)
                    ? reconcileTimer(context) : enableTimer(context);
        } else if (jobType == Settings.LIVE_WALLPAPER) {
            restored = restore(context, jobType);
        } else {
            restored = reconcileMissingEngine(context);
        }
        if (!restored) {
            L.alog().w(TAG, "automatic scheduler reconciliation failed for job type : %s", jobType);
            try {
                Settings.setAutomaticUpdateEnabled(false).blockingAwait();
            } catch (Throwable throwable) {
                L.alog().w(TAG, throwable, "persist reconciliation failure state");
            }
            if (!disabled(context)) {
                throw new IllegalStateException("fail-closed scheduler cleanup failed");
            }
        }
    }

    public static boolean enableAutomaticFallback(Context context) {
        return Settings.getAutomaticUpdateType(context) == Settings.AUTOMATIC_UPDATE_TYPE_AUTO
                && (enableSystem(context) || enableTimer(context));
    }

    private static boolean reconcileTimer(Context context) {
        try {
            boolean alarmScheduled = BingWallpaperAlarmManager.isScheduled(context)
                    || BingWallpaperAlarmManager.scheduleNext(context);
            LocalDate today = LocalDate.now();
            LocalTime updateTime = BingWallpaperUtils.getDayUpdateTime(context);
            if (BingWallpaperUtils.isAtOrAfterEarliestTime(LocalTime.now(), updateTime)
                    && BingWallpaperUtils.isTaskUndone(context)
                    && !WorkerManager.isTimerScheduled(context, today)) {
                WorkerManager.enqueueTimer(context, today, false).getResult().get();
            }
            WorkerManager.cancelPeriodic(context);
            boolean liveStopped = setLivePollingAndAwait(context, false);
            return liveStopped && (alarmScheduled || BingWallpaperAlarmManager.scheduleRetry(context));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Throwable throwable) {
            L.alog().w(TAG, throwable, "reconcile timer failure");
        }
        BingWallpaperAlarmManager.scheduleRetry(context);
        return false;
    }

    private static boolean reconcileMissingEngine(Context context) {
        int type = Settings.getAutomaticUpdateType(context);
        if (type == Settings.AUTOMATIC_UPDATE_TYPE_SYSTEM) {
            return enableSystem(context);
        }
        if (type == Settings.AUTOMATIC_UPDATE_TYPE_TIMER) {
            return enableTimer(context);
        }
        if (type == Settings.AUTOMATIC_UPDATE_TYPE_SERVICE) {
            return isLiveWallpaperActive(context)
                    && enableLiveService(context) == Settings.LIVE_WALLPAPER;
        }
        if (isLiveWallpaperActive(context)
                && enableLiveService(context) == Settings.LIVE_WALLPAPER) {
            return true;
        }
        return enableSystem(context) || enableTimer(context);
    }

    public static void setLivePolling(Context context, boolean enabled) {
        Intent intent = new Intent(LiveWallpaperService.ENABLE_LIVE_WALLPAPER);
        intent.putExtra(LiveWallpaperService.EXTRA_ENABLE_LIVE_WALLPAPER, enabled);
        intent.putExtra(LiveWallpaperService.EXTRA_CONFIRMED_LIVE_STATE, enabled);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent, LiveWallpaperService.PERMISSION_UPDATE_LIVE_WALLPAPER);
    }

    private static boolean setLivePollingAndAwait(Context context, boolean enabled) {
        Intent intent = new Intent(LiveWallpaperService.ENABLE_LIVE_WALLPAPER);
        intent.putExtra(LiveWallpaperService.EXTRA_ENABLE_LIVE_WALLPAPER, enabled);
        intent.putExtra(LiveWallpaperService.EXTRA_CONFIRMED_LIVE_STATE, enabled);
        intent.setPackage(context.getPackageName());
        CountDownLatch completed = new CountDownLatch(1);
        BroadcastReceiver resultReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ignored, Intent resultIntent) {
                completed.countDown();
            }
        };
        context.sendOrderedBroadcast(intent, LiveWallpaperService.PERMISSION_UPDATE_LIVE_WALLPAPER,
                resultReceiver, new Handler(Looper.getMainLooper()), Activity.RESULT_CANCELED,
                null, null);
        try {
            return completed.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void restoreJobType(@Settings.JobType int jobType) {
        try {
            Settings.setJobTypeAsync(jobType).blockingAwait();
        } catch (Throwable throwable) {
            L.alog().w(TAG, throwable, "restore job state failure");
        }
    }

}
