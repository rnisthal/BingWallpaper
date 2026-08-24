package me.liaoheng.wallpaper.service;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.github.liaoheng.common.util.L;

import java.io.File;
import java.io.IOException;
import java.util.function.BooleanSupplier;

import androidx.annotation.Nullable;

import me.liaoheng.wallpaper.data.BingWallpaperNetworkClient;
import me.liaoheng.wallpaper.model.Config;
import me.liaoheng.wallpaper.model.Wallpaper;
import me.liaoheng.wallpaper.util.IUIHelper;
import me.liaoheng.wallpaper.util.AutomaticUpdateResult;
import me.liaoheng.wallpaper.util.BingWallpaperUtils;
import me.liaoheng.wallpaper.util.Settings;
import me.liaoheng.wallpaper.util.UIHelper;
import me.liaoheng.wallpaper.util.WallpaperUtils;

/**
 * @author liaoheng
 * @date 2022-05-18 21:47
 */
public class SetWallpaperDelegate {
    private final String TAG;
    private final Context mContext;
    private final IUIHelper mUiHelper;
    private final SetWallpaperServiceHelper mServiceHelper;

    public SetWallpaperDelegate(Context context, String tag) {
        TAG = tag;
        mContext = context;
        mUiHelper = new UIHelper();
        mServiceHelper = new SetWallpaperServiceHelper(context, TAG);
    }

    public void setWallpaper(Intent intent) {
        if (intent == null) {
            return;
        }
        Wallpaper image = intent.getParcelableExtra(Config.EXTRA_SET_WALLPAPER_IMAGE);
        Config config = intent.getParcelableExtra(Config.EXTRA_SET_WALLPAPER_CONFIG);
        setWallpaper(image, config, false);
    }

    public AutomaticUpdateResult setWallpaper(Wallpaper image, Config config, boolean showNotification) {
        return setWallpaper(image, config, showNotification, null);
    }

    public AutomaticUpdateResult setWallpaper(Wallpaper image, Config config, boolean showNotification,
            @Nullable BooleanSupplier automaticGate) {
        if (config == null) {
            return AutomaticUpdateResult.FAILURE;
        }
        L.alog().d(TAG, config.toString());

        mServiceHelper.begin(config, showNotification);

        boolean automatic = config.isBackground() || automaticGate != null;
        if (automatic && !isAutomaticValid(automaticGate)) {
            mServiceHelper.unchanged();
            return AutomaticUpdateResult.SKIPPED;
        }

        if (image == null) {
            try {
                image = BingWallpaperNetworkClient.getWallpaper(getContext(), false);
            } catch (IOException e) {
                failure(config, e);
                return AutomaticUpdateResult.RETRYABLE_FAILURE;
            }
        }

        boolean completeDay = false;
        if (automatic) {
            String candidateBase = image.getBaseUrl();
            String storedBase = Settings.getLastWallpaperBaseUrl(getContext());
            if (TextUtils.isEmpty(storedBase)
                    && BingWallpaperUtils.legacyUrlMatchesBase(
                    Settings.getLastWallpaperImageUrl(getContext()), candidateBase)) {
                try {
                    Settings.setLastWallpaperBaseUrlAsync(candidateBase).blockingAwait();
                } catch (Throwable throwable) {
                    failure(config, throwable);
                    return AutomaticUpdateResult.RETRYABLE_FAILURE;
                }
                mServiceHelper.unchanged();
                return AutomaticUpdateResult.UNCHANGED;
            }
            if (!TextUtils.isEmpty(storedBase) && storedBase.equals(candidateBase)) {
                try {
                    mServiceHelper.unchanged(image);
                    return AutomaticUpdateResult.UNCHANGED;
                } catch (Throwable throwable) {
                    failure(config, throwable);
                    return throwable instanceof SetWallpaperServiceHelper.PersistenceException
                            ? AutomaticUpdateResult.RETRYABLE_FAILURE
                            : AutomaticUpdateResult.FAILURE;
                }
            }
            completeDay = !TextUtils.isEmpty(storedBase);
        }

        if (TextUtils.isEmpty(image.getImageUrl())) {
            image.setResolutionImageUrl(getContext());
        }

        try {
            File wallpaper = downloadWallpaper(image);
            if (automatic) {
                Wallpaper appliedImage = image;
                boolean shouldCompleteDay = completeDay;
                boolean applied = Settings.runIfAutomaticUpdateCurrent(
                        () -> isAutomaticValid(automaticGate), () -> {
                            applyWallpaper(appliedImage, config, wallpaper);
                            success(config, appliedImage, shouldCompleteDay);
                        });
                if (!applied) {
                    mServiceHelper.unchanged();
                    return AutomaticUpdateResult.SKIPPED;
                }
            } else {
                applyWallpaper(image, config, wallpaper);
                success(config, image, completeDay);
            }
            return AutomaticUpdateResult.APPLIED;
        } catch (Throwable e) {
            failure(config, e);
            return e instanceof IOException || e instanceof SetWallpaperServiceHelper.PersistenceException
                    ? AutomaticUpdateResult.RETRYABLE_FAILURE
                    : AutomaticUpdateResult.FAILURE;
        }
    }

    private void failure(Config config, Throwable throwable) {
        mServiceHelper.failure(config, throwable);
    }

    private void success(Config config, Wallpaper image, boolean completeDay) {
        mServiceHelper.success(config, image, completeDay);
    }

    private File downloadWallpaper(Wallpaper image) throws IOException {
        File wallpaper;
        try {
            wallpaper = WallpaperUtils.getImageFile(getContext(), image.getImageUrl());
        } catch (Exception e) {
            throw new IOException("Download wallpaper failure", e);
        }

        if (wallpaper == null || !wallpaper.exists()) {
            throw new IOException("Download wallpaper failure");
        }

        return wallpaper;
    }

    private void applyWallpaper(Wallpaper image, Config config, File wallpaper) throws Throwable {
        if (config.isBackground()) {
            WallpaperUtils.autoSaveWallpaper(getContext(), TAG, image, wallpaper);
        }
        mUiHelper.setWallpaper(getContext(), config, wallpaper, image.getImageUrl());
    }

    private Context getContext() {
        return mContext;
    }

    private boolean isAutomaticValid(@Nullable BooleanSupplier automaticGate) {
        return BingWallpaperUtils.isAutomaticUpdateEligible(getContext())
                && (automaticGate == null || automaticGate.getAsBoolean());
    }
}
