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
    // Xiaomi firmware can launch branded apps before accessibility receives a key.
    // Retain the working window fallback, now for all three requested shortcuts.
    private static final String LOG_TAG = "TvHome.InputKey";
    private static final long REDIRECT_COOLDOWN_MS = 3500L;
    private final Handler redirectHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Long> lastRedirects = new java.util.HashMap<>();
    private Runnable pendingRedirect;

    private Intent shortcutIntent(String target) {
        if ("hub_plus".equals(target)) {
            return AppleQuickAccessView.findByLabel(getPackageManager(), 2);
        }
        Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(target);
        if (intent == null) intent = getPackageManager().getLaunchIntentForPackage(target);
        return intent;
    }

    private void openShortcut(String target) {
        Intent intent = shortcutIntent(target);
        if (intent == null) {
            Log.w(LOG_TAG, "Shortcut not installed or not launchable: " + target);
            return;
        }
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Log.i(LOG_TAG, "Opened remote shortcut: " + target);
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.e(LOG_TAG, "Could not open remote shortcut: " + target, e);
        }
    }

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
        // These build-specific mappings take precedence over old external app_keys.
        String shortcut = AppleRemoteShortcuts.forKey(event.getScanCode(), event.getKeyCode());
        if (shortcut != null && shortcutIntent(shortcut) != null) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                openShortcut(shortcut);
            }
            return true;
        }
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
        redirectHandler.removeCallbacksAndMessages(null);
        RemoteKeys.release(this);
        if (repairRecord) ServiceRepair.disconnected(this, getClass());
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        redirectHandler.removeCallbacksAndMessages(null);
        RemoteKeys.release(this);
        if (gamepads != null) gamepads.stop();
        if (consumeVolumeKeys) VolumeKeys.stop();
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getPackageName() == null) return;
        String source = event.getPackageName().toString();
        String target = AppleRemoteShortcuts.forWindow(source);
        if (target == null) return;
        String setting = "com.netflix.ninja".equals(source)
                ? "apple_style_tv_netflix_redirect" : "apple_style_tv_remote_redirect";
        if (Settings.Global.getInt(getContentResolver(), setting, 1) == 0) return;
        if (shortcutIntent(target) == null) {
            Log.w(LOG_TAG, "Window fallback target unavailable: " + target);
            return;
        }
        long now = SystemClock.uptimeMillis();
        Long last = lastRedirects.get(source);
        if (last != null && now - last < REDIRECT_COOLDOWN_MS) return;
        lastRedirects.put(source, now);
        if (pendingRedirect != null) redirectHandler.removeCallbacks(pendingRedirect);
        pendingRedirect = () -> openShortcut(target);
        // Let the firmware finish opening its activity before switching to the requested app.
        redirectHandler.postDelayed(pendingRedirect, 350);
    }

    @Override
    public void onInterrupt() {
    }
}
