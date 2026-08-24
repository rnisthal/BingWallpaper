package me.liaoheng.wallpaper.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.github.liaoheng.common.util.L;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;

import me.liaoheng.wallpaper.service.AutoSetWallpaperBroadcastReceiver;

/**
 * @author liaoheng
 * @version 2016-09-20 16:25
 */
public class BingWallpaperAlarmManager {

    private static final int REQUEST_CODE = 0x12;
    private static final int RETRY_MINUTES = 30;

    private static PendingIntent getPendingIntent(Context context) {
        Intent intent = new Intent(context, AutoSetWallpaperBroadcastReceiver.class);
        intent.setAction(AutoSetWallpaperBroadcastReceiver.ACTION);
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, BingWallpaperUtils.getPendingIntentFlag());
    }

    public static boolean disabled(Context context) {
        boolean disabled = true;
        try {
            Settings.setTimerAlarmTriggerAt(0).blockingAwait();
        } catch (Throwable throwable) {
            L.alog().w("BingWallpaperAlarmManager", throwable, "clear alarm state error");
            disabled = false;
        }
        try {
            PendingIntent pendingIntent = getPendingIntent(context);
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                alarmManager.cancel(pendingIntent);
            }
        } catch (Throwable throwable) {
            L.alog().w("BingWallpaperAlarmManager", throwable, "cancel alarm error");
            disabled = false;
        }
        return disabled;
    }

    public static boolean enabled(Context context, @NonNull LocalTime localTime) {
        try {
            if (!disabled(context)) {
                return false;
            }
            return add(context, localTime);
        } catch (Throwable throwable) {
            L.alog().w("BingWallpaperAlarmManager", throwable, "enable alarm error");
        }
        return false;
    }

    public static boolean scheduleNext(Context context) {
        return enabled(context, BingWallpaperUtils.getDayUpdateTime(context));
    }

    public static boolean isScheduled(Context context) {
        Intent intent = new Intent(context, AutoSetWallpaperBroadcastReceiver.class);
        intent.setAction(AutoSetWallpaperBroadcastReceiver.ACTION);
        int flags = PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE;
        return Settings.getTimerAlarmTriggerAt() > System.currentTimeMillis()
                && PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags) != null;
    }

    public static void markDelivered() {
        Settings.setTimerAlarmTriggerAt(0).blockingAwait();
    }

    public static boolean scheduleRetry(Context context) {
        try {
            return add(context, DateTime.now().plusMinutes(RETRY_MINUTES));
        } catch (Throwable throwable) {
            L.alog().w("BingWallpaperAlarmManager", throwable, "retry alarm error");
            return false;
        }
    }

    private static boolean add(Context context, DateTime time) {
        PendingIntent pendingIntent = getPendingIntent(context);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            L.alog().w("BingWallpaperAlarmManager", "AlarmManager unavailable");
            return false;
        }
        alarmManager.set(AlarmManager.RTC_WAKEUP, time.getMillis(), pendingIntent);
        try {
            Settings.setTimerAlarmTriggerAt(time.getMillis()).blockingAwait();
        } catch (Throwable throwable) {
            alarmManager.cancel(pendingIntent);
            throw new IllegalStateException("persist timer alarm state failure", throwable);
        }
        return true;
    }

    private static boolean add(Context context, @NonNull LocalTime localTime) {
        DateTime dateTime = BingWallpaperUtils.checkTime(localTime);
        return add(context, dateTime);
    }
}
