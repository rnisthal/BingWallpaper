package me.liaoheng.wallpaper.util;

import android.app.Activity;
import android.app.WallpaperManager;
import android.app.WallpaperInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.widget.Toast;

import com.github.liaoheng.common.util.L;
import com.github.liaoheng.common.util.YNCallback;

import org.joda.time.LocalTime;
import org.joda.time.LocalDate;

import java.util.concurrent.TimeUnit;

import me.liaoheng.wallpaper.R;
import me.liaoheng.wallpaper.service.LiveWallpaperService;

/**
 * @author liaoheng
 * @version 2017-12-21 15:25
 */
public class BingWallpaperJobManager {
    private static final String TAG = BingWallpaperJobManager.class.getSimpleName();
    public static final int PENDING_LIVE = -2;

    public static void disabled(Context context) {
        disabled(context, false);
    }

    public static void disabled(Context context, boolean force) {
        try {
            Settings.setJobTypeAsync(Settings.NONE).blockingAwait();
        } catch (Throwable throwable) {
            L.alog().w(TAG, throwable, "disable job state failure");
        }
        WorkerManager.disabled(context);
        BingWallpaperAlarmManager.disabled(context);
        setLivePolling(context, false);
    }

    @Deprecated
    public static void clear(Context context) {
        Settings.setJobType(context, Settings.NONE);
    }

    public static int enabled(Context context) {
        int ret = enabledJob(context);
        if (ret == Settings.NONE) {
            Toast.makeText(context, R.string.enable_job_error, Toast.LENGTH_LONG).show();
        }
        return ret;
    }

    @Settings.JobType
    public static int enabledJob(Context context) {
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
        long time = TimeUnit.HOURS.toSeconds(Settings.getAutomaticUpdateInterval(context));
        int previousJobType = Settings.getJobType(context);
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
        new Thread(() -> {
            if (Settings.isEnableLog(context)) {
                LogDebugFileUtils.get().i(TAG, "Enable scheduler interval time : %s", time);
            }
        }).start();
        L.alog().d(TAG, "enable scheduler interval time : %s", time);
        return true;
    }

    public static boolean enableTimer(Context context) {
        LocalTime updateTime = BingWallpaperUtils.getDayUpdateTime(context);
        int previousJobType = Settings.getJobType(context);
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
        try {
            if (BingWallpaperUtils.isAtOrAfterEarliestTime(LocalTime.now(), updateTime)
                    && BingWallpaperUtils.isTaskUndone(context)) {
                WorkerManager.enqueueTimer(context, LocalDate.now(), true).getResult().get();
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
                Settings.setJobTypeAsync(Settings.LIVE_WALLPAPER).blockingAwait();
                WorkerManager.disabled(context);
                BingWallpaperAlarmManager.disabled(context);
                setLivePolling(context, true);
                return Settings.LIVE_WALLPAPER;
            }
            startLiveService(context);
            return PENDING_LIVE;
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
                try {
                    Settings.setJobTypeAsync(Settings.LIVE_WALLPAPER).blockingAwait();
                } catch (Throwable throwable) {
                    L.alog().w(TAG, throwable, "persist live state failure");
                    if (callback != null) {
                        callback.onDeny();
                    }
                    return;
                }
                WorkerManager.disabled(context);
                BingWallpaperAlarmManager.disabled(context);
                setLivePolling(context, true);
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
                if (Settings.getAutomaticUpdateType(context) == Settings.AUTOMATIC_UPDATE_TYPE_AUTO
                        && (enableSystem(context) || enableTimer(context))) {
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
            setLivePolling(context, true);
            return true;
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
            try {
                Settings.setJobTypeAsync(Settings.LIVE_WALLPAPER).blockingAwait();
            } catch (Throwable throwable) {
                L.alog().w(TAG, throwable, "restore live state failure");
                return false;
            }
            WorkerManager.disabled(context);
            BingWallpaperAlarmManager.disabled(context);
            setLivePolling(context, true);
            return true;
        }
        disabled(context);
        return jobType == Settings.NONE;
    }

    public static void setLivePolling(Context context, boolean enabled) {
        Intent intent = new Intent(LiveWallpaperService.ENABLE_LIVE_WALLPAPER);
        intent.putExtra(LiveWallpaperService.EXTRA_ENABLE_LIVE_WALLPAPER, enabled);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent, LiveWallpaperService.PERMISSION_UPDATE_LIVE_WALLPAPER);
    }

    private static void restoreJobType(@Settings.JobType int jobType) {
        try {
            Settings.setJobTypeAsync(jobType).blockingAwait();
        } catch (Throwable throwable) {
            L.alog().w(TAG, throwable, "restore job state failure");
        }
    }

}
