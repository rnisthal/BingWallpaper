package me.liaoheng.wallpaper.util;

import android.content.Context;
import android.util.Log;

import androidx.work.Configuration;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.BackoffPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Operation;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.github.liaoheng.common.util.L;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import me.liaoheng.wallpaper.model.Config;
import me.liaoheng.wallpaper.model.Wallpaper;
import me.liaoheng.wallpaper.service.BingWallpaperWorker;

/**
 * @author liaoheng
 * @version 2019-03-18 10:42
 */
public class WorkerManager {
    private static final String PERIODIC_WORK_NAME = "bing_wallpaper_periodic_" + 0x484;
    private static final String LEGACY_PERIODIC_WORK_NAME = "bing_wallpaper_worker_" + 0x484;
    private static final String PERIODIC_WORK_TAG = PERIODIC_WORK_NAME + "_tag";
    private static final String TIMER_WORK_PREFIX = "bing_wallpaper_timer_";
    private static final String TIMER_WORK_TAG = "bing_wallpaper_timer_tag";
    private static final String MANUAL_WORK_TAG = "bing_wallpaper_manual";
    public static final String INPUT_AUTOMATIC_SOURCE = "automatic_source";
    public static final String INPUT_TRIGGER_DATE = "trigger_date";

    public static void disabled(Context context) {
        cancelPeriodic(context);
        cancelTimer(context);
    }

    public static boolean disabledAndAwait(Context context) {
        try {
            WorkManager manager = WorkManager.getInstance(context);
            manager.cancelUniqueWork(PERIODIC_WORK_NAME).getResult().get();
            manager.cancelUniqueWork(LEGACY_PERIODIC_WORK_NAME).getResult().get();
            manager.cancelAllWorkByTag(TIMER_WORK_TAG).getResult().get();
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            L.alog().w("WorkerManager", exception, "disable work interrupted");
        } catch (Throwable throwable) {
            L.alog().w("WorkerManager", throwable, "disable work error");
        }
        return false;
    }

    public static void cancelPeriodic(Context context) {
        WorkManager manager = WorkManager.getInstance(context);
        manager.cancelUniqueWork(PERIODIC_WORK_NAME);
        manager.cancelUniqueWork(LEGACY_PERIODIC_WORK_NAME);
    }

    public static void cancelTimer(Context context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TIMER_WORK_TAG);
    }

    public static boolean cancelTimerAndAwait(Context context, LocalDate triggerDate) {
        return awaitCancellation(WorkManager.getInstance(context)
                .cancelUniqueWork(timerWorkName(triggerDate)), "cancel Timer work");
    }

    public static boolean cancelLegacyPeriodicAndAwait(Context context) {
        return awaitCancellation(WorkManager.getInstance(context)
                .cancelUniqueWork(LEGACY_PERIODIC_WORK_NAME), "cancel legacy periodic work");
    }

    private static boolean awaitCancellation(Operation operation, String action) {
        try {
            operation.getResult().get();
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            L.alog().w("WorkerManager", exception, "%s interrupted", action);
        } catch (Throwable throwable) {
            L.alog().w("WorkerManager", throwable, "%s failure", action);
        }
        return false;
    }

    /**
     * @param time seconds
     */
    public static boolean enabled(Context context, long time) {
        try {
            PeriodicWorkRequest.Builder builder = new PeriodicWorkRequest.Builder(BingWallpaperWorker.class, time,
                    TimeUnit.SECONDS)
                    .addTag(PERIODIC_WORK_TAG)
                    .setConstraints(automaticConstraints(context))
                    .setInputData(new Data.Builder()
                            .putString(INPUT_AUTOMATIC_SOURCE, AutomaticUpdateSource.PERIODIC.value())
                            .build());
            long initialDelay = periodicInitialDelayMillis(BingWallpaperUtils.getDayUpdateTime(context));
            if (initialDelay > 0) {
                builder.setInitialDelay(initialDelay, TimeUnit.MILLISECONDS);
            }

            WorkManager manager = WorkManager.getInstance(context);
            if (!cancelLegacyPeriodicAndAwait(context)) {
                return false;
            }
            manager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME,
                            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, builder.build())
                    .getResult().get();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            L.alog().w("WorkerManager", e, "enable work interrupted");
        } catch (Throwable e) {
            L.alog().w("WorkerManager", e, "enable work error");
        }
        return false;
    }

    public static void start(Context context, Wallpaper wallpaper, Config config) {
        OneTimeWorkRequest.Builder builder = new OneTimeWorkRequest.Builder(BingWallpaperWorker.class)
                .addTag(MANUAL_WORK_TAG);
        Data.Builder dataBuilder = new Data.Builder();
        if (config != null) {
            dataBuilder.putAll(config.getMap());
        }
        if (wallpaper != null) {
            dataBuilder.putAll(wallpaper.getMap());
        }
        builder.setInputData(dataBuilder.build());
        WorkManager.getInstance(context).enqueue(builder.build());
    }

    public static Operation enqueueTimer(Context context, LocalDate triggerDate, boolean replace) {
        Data data = new Data.Builder()
                .putString(INPUT_AUTOMATIC_SOURCE, AutomaticUpdateSource.TIMER.value())
                .putString(INPUT_TRIGGER_DATE, triggerDate.toString())
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BingWallpaperWorker.class)
                .addTag(TIMER_WORK_TAG)
                .setInputData(data)
                .setConstraints(automaticConstraints(context))
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
                .build();
        return WorkManager.getInstance(context).enqueueUniqueWork(timerWorkName(triggerDate),
                replace ? ExistingWorkPolicy.REPLACE : ExistingWorkPolicy.KEEP, request);
    }

    public static boolean isAutomaticSourceCurrent(Context context, AutomaticUpdateSource source,
            String triggerDate) {
        if (source == null || !BingWallpaperUtils.isAutomaticUpdateEligible(context)) {
            return false;
        }
        if (source == AutomaticUpdateSource.PERIODIC) {
            return Settings.getJobType(context) == Settings.WORKER;
        }
        return Settings.getJobType(context) == Settings.TIMER
                && LocalDate.now().toString().equals(triggerDate);
    }

    static long periodicInitialDelayMillis(LocalTime earliest) {
        DateTime now = DateTime.now();
        DateTime today = earliest.toDateTimeToday();
        return today.isAfter(now) ? today.getMillis() - now.getMillis() : 0;
    }

    static String timerWorkName(LocalDate triggerDate) {
        return TIMER_WORK_PREFIX + triggerDate;
    }

    private static Constraints automaticConstraints(Context context) {
        return new Constraints.Builder()
                .setRequiredNetworkType(Settings.getOnlyWifi(context)
                        ? NetworkType.UNMETERED
                        : NetworkType.CONNECTED)
                .build();
    }

    public static Configuration getConfig(boolean debug) {
        return new Configuration.Builder().setMinimumLoggingLevel(debug ? Log.DEBUG : Log.ERROR)
                .setExecutor(Executors.newSingleThreadExecutor())
                .build();
    }

    public static boolean isScheduled(Context context) {
        return isScheduled(context, PERIODIC_WORK_NAME);
    }

    public static boolean isTimerScheduled(Context context, LocalDate triggerDate) {
        return isScheduled(context, timerWorkName(triggerDate));
    }

    private static boolean isScheduled(Context context, String workName) {
        ListenableFuture<List<WorkInfo>> statuses = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(workName);
        try {
            List<WorkInfo> workInfoList = statuses.get();
            for (WorkInfo workInfo : workInfoList) {
                if (isActiveWorkState(workInfo.getState())) {
                    return true;
                }
            }
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException exception) {
            return false;
        }
    }

    static boolean isActiveWorkState(WorkInfo.State state) {
        return !state.isFinished();
    }
}
