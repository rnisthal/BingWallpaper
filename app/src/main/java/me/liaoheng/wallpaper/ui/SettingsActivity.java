package me.liaoheng.wallpaper.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.github.liaoheng.common.util.AppUtils;
import com.github.liaoheng.common.util.Callback;
import com.github.liaoheng.common.util.LanguageContextWrapper;
import com.github.liaoheng.common.util.ROM;
import com.github.liaoheng.common.util.ShellUtils;
import com.github.liaoheng.common.util.UIUtils;
import com.github.liaoheng.common.util.Utils;
import com.github.liaoheng.common.util.YNCallback;

import java.util.Locale;
import java.util.Objects;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import me.liaoheng.wallpaper.R;
import me.liaoheng.wallpaper.util.BingWallpaperJobManager;
import me.liaoheng.wallpaper.util.BingWallpaperUtils;
import me.liaoheng.wallpaper.util.Constants;
import me.liaoheng.wallpaper.util.CrashReportHandle;
import me.liaoheng.wallpaper.util.LogDebugFileUtils;
import me.liaoheng.wallpaper.util.SettingTrayPreferences;
import me.liaoheng.wallpaper.util.Settings;
import me.liaoheng.wallpaper.util.WallpaperUtils;
import me.liaoheng.wallpaper.widget.SeekBarDialogPreference;
import me.liaoheng.wallpaper.widget.SeekBarPreferenceDialogFragmentCompat;
import me.liaoheng.wallpaper.widget.TimePreference;
import me.liaoheng.wallpaper.widget.TimePreferenceDialogFragmentCompat;

/**
 * @author liaoheng
 * @version 2016-09-20 13:59
 */
public class SettingsActivity extends BaseActivity {
    private static boolean isChangeLanguage;

    @Override
    protected void attachBaseContext(Context context) {
        super.attachBaseContext(LanguageContextWrapper.wrap(context, BingWallpaperUtils.getLanguage(context)));
    }

    private Fragment mSettingPreferenceFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.menu_main_setting);
        setContentView(R.layout.activity_settings);
        if (savedInstanceState != null) {
            isChangeLanguage = savedInstanceState.getBoolean("isChangeLanguage");
            mSettingPreferenceFragment = getSupportFragmentManager().getFragment(savedInstanceState, "Settings");
        } else {
            mSettingPreferenceFragment = new SettingsPreferenceFragment();
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.settings_layout, mSettingPreferenceFragment, "SettingsFragment")
                .commitAllowingStateLoss();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("isChangeLanguage", isChangeLanguage);
        getSupportFragmentManager().putFragment(outState, "Settings", mSettingPreferenceFragment);
    }

    public static final String CLOSE_FULLY_AUTOMATIC_UPDATE = "close_fully_automatic_update";

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Context appContext = getApplicationContext();
        Schedulers.io().scheduleDirect(() -> BingWallpaperJobManager.onActivityResult(
                appContext, requestCode, resultCode, new YNCallback() {
            final Intent intent = new Intent(CLOSE_FULLY_AUTOMATIC_UPDATE);

            @Override
            public void onAllow() {
                publishLiveChooserResult(appContext, intent, true);
            }

            @Override
            public void onDeny() {
                publishLiveChooserResult(appContext, intent, false);
            }
        }));
    }

    private void publishLiveChooserResult(Context context, Intent intent, boolean enabled) {
        boolean durable = true;
        try {
            Settings.setLiveChooserResult(enabled ? 1 : 2).blockingAwait();
        } catch (Throwable throwable) {
            durable = false;
            try {
                Settings.setLiveChooserResult(2).blockingAwait();
            } catch (Throwable ignored) {
            }
        }
        intent.putExtra("enable", durable && enabled);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    @Override
    public void onBackPressed() {
        if (isChangeLanguage) {
            setResult(RESULT_OK);
        }
        isChangeLanguage = false;
        super.onBackPressed();
    }

    public static final String PREF_SET_WALLPAPER_DAILY_UPDATE = "pref_set_wallpaper_day_fully_automatic_update";
    public static final String PREF_SET_WALLPAPER_DAILY_UPDATE_MODE = "pref_set_wallpaper_day_fully_automatic_update_type";
    public static final String PREF_SET_WALLPAPER_DAILY_UPDATE_INTERVAL = "pref_set_wallpaper_day_fully_automatic_update_interval";
    public static final String PREF_SET_WALLPAPER_DAILY_UPDATE_TIME = "pref_set_wallpaper_day_auto_update_time";
    public static final String PREF_SET_WALLPAPER_DAILY_UPDATE_SUCCESS_NOTIFICATION = "pref_set_wallpaper_day_fully_automatic_update_notification";
    public static final String PREF_COUNTRY = "pref_country";
    public static final String PREF_LANGUAGE = "pref_language";
    public static final String PREF_SET_WALLPAPER_RESOLUTION = "pref_set_wallpaper_resolution";
    public static final String PREF_SET_WALLPAPER_MATCH_SCREEN_ORIENTATION = "pref_set_wallpaper_match_screen_orientation";
    public static final String PREF_SAVE_WALLPAPER_RESOLUTION = "pref_save_wallpaper_resolution";
    public static final String PREF_SET_WALLPAPER_AUTO_MODE = "pref_set_wallpaper_auto_mode";
    public static final String PREF_SET_WALLPAPER_DAY_AUTO_UPDATE_ONLY_WIFI = "pref_set_wallpaper_day_auto_update_only_wifi";
    public static final String PREF_SET_WALLPAPER_LOG = "pref_set_wallpaper_debug_log";
    public static final String PREF_SET_MIUI_LOCK_SCREEN_WALLPAPER = "pref_set_miui_lock_screen_wallpaper";
    public static final String PREF_CRASH_REPORT = "pref_crash_report";
    public static final String PREF_DOH = "pref_doh";
    public static final String PREF_STACK_BLUR = "pref_stack_blur";
    public static final String PREF_STACK_BLUR_MODE = "pref_stack_blur_mode";
    public static final String PREF_BRIGHTNESS = "pref_brightness";
    public static final String PREF_BRIGHTNESS_MODE = "pref_brightness_mode";
    public static final String PREF_AUTO_SAVE_WALLPAPER_FILE = "pref_auto_save_wallpaper_file";

    public final static class SettingsPreferenceFragment extends PreferenceFragmentCompat
            implements Preference.OnPreferenceChangeListener {

        private SwitchPreferenceCompat mDailyUpdatePreference;
        private ListPreference mDailyUpdateIntervalPreference;
        private TimePreference mDailyUpdateTimePreference;
        private Preference mDailyUpdateModePreference;
        private SwitchPreferenceCompat mOnlyWifiPreference;
        private SwitchPreferenceCompat mAutoSaveWallpaperPreference;
        private boolean mAutomaticTransition;
        private Preference mPendingPreference;
        private Object mPendingOldValue;
        private Object mPendingNewValue;
        private int mPendingPreviousJobType = Settings.NONE;
        private final CompositeDisposable mAutomaticDisposables = new CompositeDisposable();
        private boolean mWaitingForLiveResult;
        private static final String STATE_PENDING_KEY = "automatic_pending_key";
        private static final String STATE_PENDING_OLD = "automatic_pending_old";
        private static final String STATE_PENDING_NEW = "automatic_pending_new";
        private static final String STATE_PENDING_JOB = "automatic_pending_job";
        private static final String STATE_PENDING_PHASE = "automatic_pending_phase";
        private static final int PHASE_APPLY = 0;
        private static final int PHASE_LIVE = 1;
        private static final int PHASE_ROLLBACK = 2;
        private static final int PHASE_DISABLE = 3;
        private static final int PHASE_FALLBACK = 4;
        private int mAutomaticPhase = PHASE_APPLY;
        private Context mAutomaticContext;

        private static final class AutomaticChangeResult {
            final boolean success;
            final int jobType;

            AutomaticChangeResult(boolean success, int jobType) {
                this.success = success;
                this.jobType = jobType;
            }
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            getPreferenceManager().setPreferenceDataStore(SettingTrayPreferences.get());
            setPreferencesFromResource(R.xml.preferences, rootKey);
        }

        @Override
        public void onDisplayPreferenceDialog(@NonNull Preference preference) {
            if (preference instanceof TimePreference) {
                FragmentManager fragmentManager = getParentFragmentManager();
                DialogFragment dialogFragment = TimePreferenceDialogFragmentCompat.newInstance(preference.getKey());
                dialogFragment.setTargetFragment(this, 0);
                dialogFragment.show(fragmentManager, "TimePreference");
            } else if (preference instanceof SeekBarDialogPreference) {
                FragmentManager fragmentManager = getParentFragmentManager();
                int max = 100;
                int min = 0;
                if (Objects.equals(preference.getKey(), PREF_BRIGHTNESS)) {
                    min = -100;
                }
                DialogFragment dialogFragment = SeekBarPreferenceDialogFragmentCompat.newInstance(preference.getKey(),
                        max, min);
                dialogFragment.setTargetFragment(this, 1);
                dialogFragment.show(fragmentManager, "SeekBarDialogPreference");
            } else {
                super.onDisplayPreferenceDialog(preference);
            }
        }

        final class SettingBroadcastReceiver extends BroadcastReceiver {

            @Override
            public void onReceive(Context context, Intent intent) {
                if (CLOSE_FULLY_AUTOMATIC_UPDATE.equals(intent.getAction())) {
                    if (mDailyUpdatePreference == null) {
                        return;
                    }
                    boolean enable = intent.getBooleanExtra("enable", false);
                    completePendingLiveChange(enable);
                }
            }
        }

        private SettingBroadcastReceiver mReceiver;
        private ActivityResultLauncher<String[]> mAutoSavePermissions;

        @SuppressWarnings("ConstantConditions")
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mReceiver = new SettingBroadcastReceiver();
            LocalBroadcastManager.getInstance(requireContext())
                    .registerReceiver(mReceiver, new IntentFilter(CLOSE_FULLY_AUTOMATIC_UPDATE));
            mAutoSavePermissions = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    map -> onAutoSaveWallpaperRequestPermissionsResult(
                            BingWallpaperUtils.checkStoragePermissions(map)));
            Preference version = findPreference("pref_version");
            version.setSummary(AppUtils.getVersionInfo(requireContext()).versionName);

            findPreference("pref_github").setOnPreferenceClickListener(preference -> {
                BingWallpaperUtils.openBrowser(requireContext(), "https://github.com/liaoheng/BingWallpaper");
                return true;
            });

            findPreference("pref_intro").setOnPreferenceClickListener(preference -> {
                UIUtils.startActivity(requireContext(), IntroActivity.class);
                return true;
            });

            findPreference("pref_license").setOnPreferenceClickListener(preference -> {
                UIUtils.startActivity(requireContext(), LicenseActivity.class);
                return true;
            });

            findPreference("pref_clear_cache").setOnPreferenceClickListener(preference -> {
                UIUtils.showYNAlertDialog(requireContext(), getString(R.string.pref_clear_cache) + "?",
                        new YNCallback() {
                            @Override
                            public void onAllow() {
                                Utils.addSubscribe(BingWallpaperUtils.clearCache(getActivity()),
                                        new Callback.EmptyCallback<Object>() {
                                            @Override
                                            public void onSuccess(Object o) {
                                                UIUtils.showToast(requireContext(), R.string.pref_clear_cache_success);
                                            }
                                        });
                            }

                            @Override
                            public void onDeny() {

                            }
                        });
                return true;
            });

            findPreference("pref_translation").setOnPreferenceClickListener(preference -> {
                UIUtils.startActivity(requireContext(), TranslatorActivity.class);
                return true;
            });

            mDailyUpdatePreference = findPreference(PREF_SET_WALLPAPER_DAILY_UPDATE);
            mDailyUpdatePreference.setOnPreferenceChangeListener(this);
            mDailyUpdateModePreference = findPreference(PREF_SET_WALLPAPER_DAILY_UPDATE_MODE);
            mDailyUpdateModePreference.setOnPreferenceChangeListener(this);
            mDailyUpdateIntervalPreference = findPreference(PREF_SET_WALLPAPER_DAILY_UPDATE_INTERVAL);
            mDailyUpdateIntervalPreference.setOnPreferenceChangeListener(this);
            mDailyUpdateIntervalPreference.setSummaryProvider(new Preference.SummaryProvider<ListPreference>() {
                @Nullable
                @Override
                public CharSequence provideSummary(@NonNull ListPreference preference) {
                    return requireContext().getString(R.string.pref_auto_update_check_time,
                            Integer.parseInt(preference.getEntry().toString()));
                }
            });
            mDailyUpdateTimePreference = findPreference(PREF_SET_WALLPAPER_DAILY_UPDATE_TIME);
            mDailyUpdateTimePreference.setOnPreferenceChangeListener(this);
            mOnlyWifiPreference = findPreference(PREF_SET_WALLPAPER_DAY_AUTO_UPDATE_ONLY_WIFI);
            mOnlyWifiPreference.setOnPreferenceChangeListener(this);
            Preference mCountryListPreference = findPreference(PREF_COUNTRY);
            mCountryListPreference.setOnPreferenceChangeListener(this);
            Preference mLanguageListPreference = findPreference(PREF_LANGUAGE);
            mLanguageListPreference.setOnPreferenceChangeListener(this);
            Preference mModeTypeListPreference = findPreference(PREF_SET_WALLPAPER_AUTO_MODE);
            mModeTypeListPreference.setOnPreferenceChangeListener(this);
            Preference mMIuiLockScreenPreference = findPreference(PREF_SET_MIUI_LOCK_SCREEN_WALLPAPER);
            mMIuiLockScreenPreference.setOnPreferenceChangeListener(this);
            Preference mLogPreference = findPreference(PREF_SET_WALLPAPER_LOG);
            mLogPreference.setOnPreferenceChangeListener(this);
            Preference mCrashPreference = findPreference(PREF_CRASH_REPORT);
            mCrashPreference.setOnPreferenceChangeListener(this);
            mAutoSaveWallpaperPreference = findPreference(PREF_AUTO_SAVE_WALLPAPER_FILE);
            mAutoSaveWallpaperPreference.setOnPreferenceChangeListener(this);

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R || !ROM.getROM().isMiui()) {
                ((PreferenceCategory) findPreference("pref_wallpaper_group")).removePreference(
                        mMIuiLockScreenPreference);
            }
            mDailyUpdateTimePreference.setDefaultValue(Constants.DEF_TIMER_PERIODIC);
            mDailyUpdateTimePreference.setSummaryProvider(new Preference.SummaryProvider<TimePreference>() {
                @Nullable
                @Override
                public CharSequence provideSummary(@NonNull TimePreference preference) {
                    return preference.getLocalTime().toString("HH:mm");
                }
            });
            findPreference(PREF_STACK_BLUR).setSummaryProvider(new Preference.SummaryProvider<SeekBarDialogPreference>() {
                @Nullable
                @Override
                public CharSequence provideSummary(@NonNull SeekBarDialogPreference preference) {
                    return String.valueOf(preference.getProgress());
                }
            });
            findPreference(PREF_BRIGHTNESS).setSummaryProvider(new Preference.SummaryProvider<SeekBarDialogPreference>() {
                @Nullable
                @Override
                public CharSequence provideSummary(@NonNull SeekBarDialogPreference preference) {
                    return String.valueOf(preference.getProgress());
                }
            });

            mDailyUpdatePreference.setSummary(Settings.getJobTypeString(requireContext()));

            switch (Settings.getAutomaticUpdateType(requireContext())) {
                case Settings.AUTOMATIC_UPDATE_TYPE_AUTO:
                    int jobType = Settings.getJobType(requireContext());
                    if (jobType == Settings.WORKER) {
                        initWorkerView();
                    } else if (jobType == Settings.LIVE_WALLPAPER) {
                        initLiveView();
                    } else if (jobType == Settings.TIMER) {
                        initTimerView();
                    }
                    break;
                case Settings.AUTOMATIC_UPDATE_TYPE_SYSTEM:
                    initWorkerView();
                    break;
                case Settings.AUTOMATIC_UPDATE_TYPE_SERVICE:
                    initLiveView();
                    break;
                case Settings.AUTOMATIC_UPDATE_TYPE_TIMER:
                    initTimerView();
                    break;
            }
            if (WallpaperUtils.isNotSupportedWallpaper(requireContext())) {
                mDailyUpdatePreference.setEnabled(false);
            }
            restorePendingAutomaticChange(savedInstanceState);
        }

        private void initWorkerView() {
            mDailyUpdateIntervalPreference.setEnabled(true);
            mDailyUpdateTimePreference.setEnabled(true);
        }

        private void initLiveView() {
            mDailyUpdateIntervalPreference.setEnabled(false);
            mDailyUpdateTimePreference.setEnabled(true);
        }

        private void initTimerView() {
            mDailyUpdateTimePreference.setEnabled(true);
            mDailyUpdateIntervalPreference.setEnabled(false);
        }

        @Override
        public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
            if (isAutomaticPreference(preference.getKey())) {
                return beginAutomaticChange(preference, newValue);
            }
            switch (Objects.requireNonNull(preference.getKey())) {
                case PREF_COUNTRY:
                    BingWallpaperUtils.clearNetCache().subscribe();
                    break;
                case PREF_LANGUAGE:
                    Locale currentLocale = LanguageContextWrapper.getCurrentLocale(requireContext());
                    Locale newLocale = BingWallpaperUtils.getLanguage(Integer.parseInt(String.valueOf(newValue)),
                            LanguageContextWrapper.getOriginalLocale());
                    if (currentLocale.equals(newLocale)) {
                        break;
                    }
                    LanguageContextWrapper.wrap(requireContext(), newLocale);
                    isChangeLanguage = true;
                    requireActivity().recreate();
                    break;
                case PREF_SET_WALLPAPER_AUTO_MODE:
                    if (Integer.parseInt(String.valueOf(newValue))
                            == Constants.EXTRA_SET_WALLPAPER_MODE_LOCK) {
                        if (Settings.getJobType(requireContext()) == Settings.LIVE_WALLPAPER) {
                            return false;
                        }
                    }
                    break;
                case PREF_SET_WALLPAPER_LOG:
                    if (Boolean.parseBoolean(String.valueOf(newValue))) {
                        LogDebugFileUtils.create(requireContext());
                        Intent intent = new Intent(Constants.ACTION_DEBUG_LOG);
                        intent.setPackage(requireContext().getPackageName());
                        requireContext().sendBroadcast(intent);
                    } else {
                        LogDebugFileUtils.destroy();
                    }
                    break;
                case PREF_CRASH_REPORT:
                    if (Boolean.parseBoolean(String.valueOf(newValue))) {
                        CrashReportHandle.enable(getActivity());
                    } else {
                        CrashReportHandle.disable(getActivity());
                    }
                    break;
                case PREF_AUTO_SAVE_WALLPAPER_FILE:
                    if (Boolean.parseBoolean(String.valueOf(newValue))) {
                        return BingWallpaperUtils.requestStoragePermissions(requireActivity(), mAutoSavePermissions);
                    }
                    break;
                case PREF_SET_MIUI_LOCK_SCREEN_WALLPAPER:
                    if (Boolean.parseBoolean(String.valueOf(newValue))) {
                        if (!ShellUtils.hasRootPermission()) {
                            UIUtils.showToast(requireContext(), R.string.unable_root_permission);
                            return false;
                        }
                    }
                    break;
            }
            return true;
        }

        private boolean isAutomaticPreference(String key) {
            return PREF_SET_WALLPAPER_DAILY_UPDATE.equals(key)
                    || PREF_SET_WALLPAPER_DAILY_UPDATE_MODE.equals(key)
                    || PREF_SET_WALLPAPER_DAILY_UPDATE_INTERVAL.equals(key)
                    || PREF_SET_WALLPAPER_DAILY_UPDATE_TIME.equals(key)
                    || PREF_SET_WALLPAPER_DAY_AUTO_UPDATE_ONLY_WIFI.equals(key);
        }

        private boolean beginAutomaticChange(Preference preference, Object newValue) {
            if (mAutomaticTransition) {
                return false;
            }
            mAutomaticTransition = true;
            mPendingPreference = preference;
            mPendingOldValue = getPreferenceValue(preference);
            mPendingNewValue = newValue;
            mPendingPreviousJobType = Settings.getJobType(requireContext());
            mWaitingForLiveResult = false;
            mAutomaticPhase = PHASE_APPLY;
            mAutomaticContext = requireContext().getApplicationContext();
            updateAutomaticControls();

            persistPendingAutomaticValue();
            return false;
        }

        private void persistPendingAutomaticValue() {
            mAutomaticDisposables.add(persistAutomaticValue(mPendingPreference.getKey(), mPendingNewValue)
                    .andThen(Single.fromCallable(() -> applyAutomaticChange(mAutomaticContext)))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::handleAutomaticChange,
                            throwable -> handleAutomaticChange(new AutomaticChangeResult(false, Settings.NONE))));
        }

        private Completable persistAutomaticValue(String key, Object value) {
            if (PREF_SET_WALLPAPER_DAILY_UPDATE.equals(key)) {
                return Settings.setAutomaticUpdateEnabled(Boolean.parseBoolean(String.valueOf(value)));
            }
            if (PREF_SET_WALLPAPER_DAILY_UPDATE_MODE.equals(key)) {
                return Settings.setAutomaticUpdateType(Integer.parseInt(String.valueOf(value)));
            }
            if (PREF_SET_WALLPAPER_DAILY_UPDATE_INTERVAL.equals(key)) {
                return Settings.setAutomaticUpdateInterval(Integer.parseInt(String.valueOf(value)));
            }
            if (PREF_SET_WALLPAPER_DAILY_UPDATE_TIME.equals(key)) {
                return Settings.setAutomaticUpdateTime(String.valueOf(value));
            }
            return Settings.setOnlyWifi(Boolean.parseBoolean(String.valueOf(value)));
        }

        private AutomaticChangeResult applyAutomaticChange(Context context) {
            String key = mPendingPreference.getKey();
            boolean success = true;
            int result = Settings.NONE;
            if (PREF_SET_WALLPAPER_DAILY_UPDATE.equals(key)) {
                if (Boolean.parseBoolean(String.valueOf(mPendingNewValue))) {
                    result = BingWallpaperJobManager.enabledJob(context);
                    success = result != Settings.NONE;
                } else {
                    success = BingWallpaperJobManager.disabled(context);
                }
            } else if (PREF_SET_WALLPAPER_DAILY_UPDATE_MODE.equals(key)
                    && Settings.isAutomaticUpdateEnabled(context)) {
                result = BingWallpaperJobManager.enabledJob(context);
                success = result != Settings.NONE;
            } else if (Settings.isAutomaticUpdateEnabled(context)) {
                success = BingWallpaperJobManager.reconfigure(context);
            }
            if (result == BingWallpaperJobManager.PENDING_LIVE) {
                Settings.setLiveChooserResult(0).blockingAwait();
            }
            return new AutomaticChangeResult(success, result);
        }

        private void handleAutomaticChange(AutomaticChangeResult change) {
            if (!isAdded()) {
                if (!change.success || change.jobType == BingWallpaperJobManager.PENDING_LIVE) {
                    rollbackAutomaticChangeDetached();
                }
                return;
            }
            if (change.jobType == BingWallpaperJobManager.PENDING_LIVE) {
                mWaitingForLiveResult = true;
                mAutomaticPhase = PHASE_LIVE;
                try {
                    BingWallpaperJobManager.startLiveService(requireActivity());
                } catch (Throwable throwable) {
                    applyLiveFallback();
                }
                return;
            }
            if (change.success) {
                finishAutomaticChange(true);
            } else {
                rollbackAutomaticChange();
            }
        }

        private void completePendingLiveChange(boolean success) {
            if (!mAutomaticTransition || mPendingPreference == null) {
                mDailyUpdatePreference.setChecked(Settings.isAutomaticUpdateEnabled(requireContext()));
                updateAutomaticControls();
                return;
            }
            mWaitingForLiveResult = false;
            mAutomaticDisposables.add(Settings.setLiveChooserResult(0)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(() -> {
                        if (!isAdded()) {
                            if (!success) {
                                rollbackAutomaticChangeDetached();
                            }
                        } else if (success) {
                            finishAutomaticChange(true);
                        } else {
                            rollbackAutomaticChange();
                        }
                    }, throwable -> {
                        if (isAdded()) {
                            rollbackAutomaticChange();
                        } else {
                            rollbackAutomaticChangeDetached();
                        }
                    }));
        }

        private void readPendingLiveResult() {
            mAutomaticDisposables.add(Single.fromCallable(Settings::getLiveChooserResult)
                    .retry(2)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(result -> {
                        if (result != 0 && isAdded()) {
                            completePendingLiveChange(result == 1);
                        }
                    }, throwable -> {
                        if (isAdded()) {
                            failClosedAutomaticChange();
                        } else {
                            failClosedAutomaticChangeDetached();
                        }
                    }));
        }

        private void applyLiveFallback() {
            mAutomaticPhase = PHASE_FALLBACK;
            mAutomaticDisposables.add(Single.fromCallable(
                            () -> BingWallpaperJobManager.enableAutomaticFallback(mAutomaticContext))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(success -> {
                        if (!isAdded()) {
                            if (!success) {
                                rollbackAutomaticChangeDetached();
                            }
                        } else if (success) {
                            finishAutomaticChange(true);
                        } else {
                            rollbackAutomaticChange();
                        }
                    }, throwable -> {
                        if (isAdded()) {
                            rollbackAutomaticChange();
                        } else {
                            rollbackAutomaticChangeDetached();
                        }
                    }));
        }

        private void rollbackAutomaticChange() {
            mAutomaticPhase = PHASE_ROLLBACK;
            Preference preference = mPendingPreference;
            Object oldValue = mPendingOldValue;
            int previousJobType = mPendingPreviousJobType;
            mAutomaticDisposables.add(persistAutomaticValue(preference.getKey(), oldValue)
                    .andThen(Completable.fromAction(() -> {
                        boolean restored;
                        if (Settings.isAutomaticUpdateEnabled(mAutomaticContext)) {
                            restored = BingWallpaperJobManager.restore(mAutomaticContext, previousJobType);
                        } else {
                            restored = BingWallpaperJobManager.disabled(mAutomaticContext);
                        }
                        if (!restored) {
                            throw new IllegalStateException("automatic scheduler restore failed");
                        }
                    }))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(() -> {
                        if (!isAdded()) {
                            return;
                        }
                        setPreferenceValue(preference, oldValue);
                        UIUtils.showToast(requireContext(), R.string.enable_job_error);
                        finishAutomaticChange(false);
                    }, throwable -> {
                        if (isAdded()) {
                            failClosedAutomaticChange();
                        } else {
                            failClosedAutomaticChangeDetached();
                        }
                    }));
        }

        private void rollbackAutomaticChangeDetached() {
            String key = mPendingPreference.getKey();
            Object oldValue = mPendingOldValue;
            int previousJobType = mPendingPreviousJobType;
            mAutomaticDisposables.add(persistAutomaticValue(key, oldValue)
                    .andThen(Completable.fromAction(() -> {
                        if (Settings.isAutomaticUpdateEnabled(mAutomaticContext)) {
                            if (!BingWallpaperJobManager.restore(mAutomaticContext, previousJobType)) {
                                throw new IllegalStateException("automatic scheduler restore failed");
                            }
                        } else {
                            if (!BingWallpaperJobManager.disabled(mAutomaticContext)) {
                                throw new IllegalStateException("disable automatic scheduler failed");
                            }
                        }
                    }))
                    .subscribeOn(Schedulers.io())
                    .subscribe(() -> { }, throwable -> failClosedAutomaticChangeDetached()));
        }

        private void failClosedAutomaticChange() {
            mAutomaticPhase = PHASE_DISABLE;
            Context context = mAutomaticContext;
            mAutomaticDisposables.add(Completable.fromAction(() -> disableAutomaticOrThrow(context))
                    .andThen(Settings.setAutomaticUpdateEnabled(false))
                    .retry(2)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(() -> {
                        if (isAdded()) {
                            finishFailedClosedChange(false);
                        }
                    }, throwable -> {
                        if (isAdded()) {
                            readFailedClosedState();
                        }
                    }));
        }

        private void failClosedAutomaticChangeDetached() {
            mAutomaticDisposables.add(Completable.fromAction(
                            () -> disableAutomaticOrThrow(mAutomaticContext))
                    .andThen(Settings.setAutomaticUpdateEnabled(false))
                    .retry(2)
                    .subscribeOn(Schedulers.io())
                    .subscribe(() -> { }, throwable -> { }));
        }

        private void disableAutomaticOrThrow(Context context) {
            if (!BingWallpaperJobManager.disabled(context)) {
                throw new IllegalStateException("disable automatic scheduler failed");
            }
        }

        private void readFailedClosedState() {
            mAutomaticDisposables.add(Single.fromCallable(
                            () -> Settings.isAutomaticUpdateEnabled(mAutomaticContext))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(checked -> {
                        if (isAdded()) {
                            finishFailedClosedChange(checked);
                        }
                    }, throwable -> {
                        if (isAdded()) {
                            finishFailedClosedChange(false);
                        }
                    }));
        }

        private void finishFailedClosedChange(boolean checked) {
            mDailyUpdatePreference.setChecked(checked);
            UIUtils.showToast(requireContext(), R.string.enable_job_error);
            finishAutomaticChange(false);
        }

        private void finishAutomaticChange(boolean useNewValue) {
            if (useNewValue) {
                setPreferenceValue(mPendingPreference, mPendingNewValue);
            }
            mAutomaticTransition = false;
            mPendingPreference = null;
            mPendingOldValue = null;
            mPendingNewValue = null;
            mPendingPreviousJobType = Settings.NONE;
            mWaitingForLiveResult = false;
            mAutomaticPhase = PHASE_APPLY;
            mAutomaticContext = null;
            mDailyUpdatePreference.setSummary(Settings.getJobTypeString(requireContext()));
            updateAutomaticControls();
        }

        private Object getPreferenceValue(Preference preference) {
            if (preference instanceof SwitchPreferenceCompat) {
                return ((SwitchPreferenceCompat) preference).isChecked();
            }
            if (preference instanceof ListPreference) {
                return ((ListPreference) preference).getValue();
            }
            return ((TimePreference) preference).getLocalTime();
        }

        private void setPreferenceValue(Preference preference, Object value) {
            if (preference instanceof SwitchPreferenceCompat) {
                ((SwitchPreferenceCompat) preference).setChecked(Boolean.parseBoolean(String.valueOf(value)));
            } else if (preference instanceof ListPreference) {
                ((ListPreference) preference).setValue(String.valueOf(value));
            } else if (preference instanceof TimePreference) {
                ((TimePreference) preference).setTime((org.joda.time.LocalTime) value);
            }
        }

        private void updateAutomaticControls() {
            boolean controlsEnabled = !mAutomaticTransition;
            if (!WallpaperUtils.isNotSupportedWallpaper(requireContext())) {
                mDailyUpdatePreference.setEnabled(controlsEnabled);
            }
            mDailyUpdateModePreference.setEnabled(controlsEnabled);
            mDailyUpdateTimePreference.setEnabled(controlsEnabled);
            mOnlyWifiPreference.setEnabled(controlsEnabled);
            int mode = Settings.getAutomaticUpdateType(requireContext());
            mDailyUpdateIntervalPreference.setEnabled(controlsEnabled
                    && (mode == Settings.AUTOMATIC_UPDATE_TYPE_AUTO
                    || mode == Settings.AUTOMATIC_UPDATE_TYPE_SYSTEM));
        }

        private void restorePendingAutomaticChange(Bundle state) {
            if (state == null || !state.containsKey(STATE_PENDING_KEY)) {
                return;
            }
            String key = state.getString(STATE_PENDING_KEY);
            mPendingPreference = findPreference(key);
            if (mPendingPreference == null) {
                return;
            }
            mPendingOldValue = parseAutomaticValue(key, state.getString(STATE_PENDING_OLD));
            mPendingNewValue = parseAutomaticValue(key, state.getString(STATE_PENDING_NEW));
            mPendingPreviousJobType = state.getInt(STATE_PENDING_JOB, Settings.NONE);
            mAutomaticPhase = state.getInt(STATE_PENDING_PHASE, PHASE_APPLY);
            mAutomaticContext = requireContext().getApplicationContext();
            mAutomaticTransition = true;
            mWaitingForLiveResult = mAutomaticPhase == PHASE_LIVE;
            updateAutomaticControls();
            if (mAutomaticPhase == PHASE_APPLY) {
                persistPendingAutomaticValue();
            } else if (mAutomaticPhase == PHASE_ROLLBACK) {
                rollbackAutomaticChange();
            } else if (mAutomaticPhase == PHASE_DISABLE) {
                failClosedAutomaticChange();
            } else if (mAutomaticPhase == PHASE_LIVE) {
                readPendingLiveResult();
            } else if (mAutomaticPhase == PHASE_FALLBACK) {
                applyLiveFallback();
            }
        }

        private Object parseAutomaticValue(String key, String value) {
            if (PREF_SET_WALLPAPER_DAILY_UPDATE.equals(key)
                    || PREF_SET_WALLPAPER_DAY_AUTO_UPDATE_ONLY_WIFI.equals(key)) {
                return Boolean.parseBoolean(value);
            }
            if (PREF_SET_WALLPAPER_DAILY_UPDATE_TIME.equals(key)) {
                return org.joda.time.LocalTime.parse(value);
            }
            return value;
        }

        @Override
        public void onSaveInstanceState(@NonNull Bundle outState) {
            if (mAutomaticTransition && mPendingPreference != null) {
                outState.putString(STATE_PENDING_KEY, mPendingPreference.getKey());
                outState.putString(STATE_PENDING_OLD, String.valueOf(mPendingOldValue));
                outState.putString(STATE_PENDING_NEW, String.valueOf(mPendingNewValue));
                outState.putInt(STATE_PENDING_JOB, mPendingPreviousJobType);
                outState.putInt(STATE_PENDING_PHASE, mAutomaticPhase);
            }
            super.onSaveInstanceState(outState);
        }

        public void onAutoSaveWallpaperRequestPermissionsResult(boolean granted) {
            mAutoSaveWallpaperPreference.setChecked(granted);
        }

        @Override
        public void onDestroy() {
            if (mReceiver != null) {
                LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mReceiver);
            }
            if (!mAutomaticTransition || getActivity() != null && requireActivity().isChangingConfigurations()) {
                mAutomaticDisposables.clear();
            }
            super.onDestroy();
        }
    }
}
