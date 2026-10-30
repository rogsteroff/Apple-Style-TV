package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import java.util.Collections;
import java.util.Map;

/**
 * Maps the remote's Input button to the system input switcher, from any app. Remotes such as the Chromecast voice
 * remote send KEYCODE_TV for it, others (KTC and Kalley Realtek Google TV remotes) KEYCODE_TV_INPUT; on Google TV
 * the stock launcher handles both, so with the stock launcher disabled the button does nothing. Buttons listed in
 * input_key_keycodes (config.xml or a theme) open the input switcher the same way, and app_keys start an app (a
 * YouTube button that only the stock launcher answered). Every other key passes through untouched.
 * Opt-in: INPUT_KEY_SERVICE=1 build-and-install.sh (or -InputKeyService on Windows) enables it.
 */
public class InputKeyService extends AccessibilityService {

    private static final String ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS";
    // Xiaomi Android TV 9 handles Netflix keycode 1001 inside WindowManager before apps see it.
    // Watch Netflix window state as a fallback, then switch to Lampa.
    // To disable: adb shell settings put global apple_style_tv_netflix_redirect 0
    private static final String NETFLIX_PACKAGE = "com.netflix.ninja";
    private static final String LAMPA_PACKAGE = "top.rootu.lampa";
    private static final String NETFLIX_REDIRECT_SETTING = "apple_style_tv_netflix_redirect";
    private static final String LOG_TAG = "TvHome.InputKey";
    private static final long REDIRECT_COOLDOWN_MS = 3500L;
    private final Handler redirectHandler = new Handler(Looper.getMainLooper());
    private long lastRedirectAt;
    private final Runnable redirectNetflix = () -> {
        Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(LAMPA_PACKAGE);
        if (intent == null) intent = getPackageManager().getLaunchIntentForPackage(LAMPA_PACKAGE);
        if (intent == null) {
            Log.w(LOG_TAG, "Netflix redirect skipped: Lampa not installed or not launchable");
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
            Log.i(LOG_TAG, "Redirected firmware Netflix launch to Lampa");
        } catch (Exception e) {
            Log.e(LOG_TAG, "Could not redirect Netflix to Lampa", e);
        }
    };

    private int[] inputKeycodes = new int[0];
    private Map<Integer, String> appKeys = Collections.emptyMap();
    private Map<Integer, String> appScancodes = Collections.emptyMap();
    private Map<Integer, String> holdAppKeys = Collections.emptyMap();
    private Map<Integer, String> holdAppScancodes = Collections.emptyMap();
    /** restore_key_services was on when the config was read: this helper is noted for ServiceRepair. */
    private boolean repairRecord;
    /** consume_volume_keys: Volume Up/Down go to VolumeKeys instead of on to the firmware. */
    private boolean consumeVolumeKeys;
    /** Stops key filtering while a game controller is connected; null unless gamepad_passthrough and key_filter. */
    private GamepadPassthrough gamepads;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        // From config.xml or a theme's config.json; theme.sh rebinds this service after a push.
        Config config = Config.load(this);
        // restore_key_services: note that this helper runs, so Home can put it back after a force stop.
        repairRecord = config.restoreKeyServices;
        if (repairRecord) ServiceRepair.connected(this, getClass());
        inputKeycodes = config.inputKeycodes;
        consumeVolumeKeys = config.consumeVolumeKeys;
        appKeys = RemoteKeys.parseAppKeys(config.appKeys);
        appScancodes = RemoteKeys.parseAppScancodes(config.appKeys);
        RemoteKeys.applyKeyFilter(this, config.keyFilter);
        holdAppKeys = RemoteKeys.parseHoldAppKeys(config.appKeys);
        holdAppScancodes = RemoteKeys.parseHoldAppScancodes(config.appKeys);
        // gamepad_passthrough, off by default. Only with key_filter on: off, the service never filters keys.
        if (config.gamepadPassthrough && config.keyFilter && gamepads == null) {
            gamepads = new GamepadPassthrough(this);
            gamepads.start();
        }
    }

    private boolean isInputKey(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_TV || keyCode == KeyEvent.KEYCODE_TV_INPUT) return true;
        for (int code : inputKeycodes) if (keyCode == code) return true;
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (consumeVolumeKeys && VolumeKeys.handle(this, event)) return true;
        // A scancode entry is more specific than any keycode, so it goes first.
        if (RemoteKeys.handle(this, event, appScancodes.get(event.getScanCode()),
                holdAppScancodes.get(event.getScanCode()))) return true;
        if (RemoteKeys.handle(this, event, appKeys.get(event.getKeyCode()),
                holdAppKeys.get(event.getKeyCode()))) return true;
        if (!isInputKey(event.getKeyCode())) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            try {
                startActivity(new Intent(ACTION_VIEW_INPUTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException ignored) {
            }
        }
        return true;
    }

    /** Unbound by Android: the user turned the helper off, so Home doesn't put it back (ServiceRepair). */
    @Override
    public boolean onUnbind(Intent intent) {
        RemoteKeys.release(this);
        if (repairRecord) ServiceRepair.disconnected(this, getClass());
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        redirectHandler.removeCallbacks(redirectNetflix);
        RemoteKeys.release(this);
        if (gamepads != null) gamepads.stop();
        if (consumeVolumeKeys) VolumeKeys.stop();
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getPackageName() == null
                || !NETFLIX_PACKAGE.contentEquals(event.getPackageName())) return;
        if (Settings.Global.getInt(getContentResolver(), NETFLIX_REDIRECT_SETTING, 1) == 0) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastRedirectAt < REDIRECT_COOLDOWN_MS) return;
        lastRedirectAt = now;
        redirectHandler.removeCallbacks(redirectNetflix);
        // Let Xiaomi finish its direct Netflix launch before switching activities.
        redirectHandler.postDelayed(redirectNetflix, 350);
    }

    @Override
    public void onInterrupt() {
    }
}
