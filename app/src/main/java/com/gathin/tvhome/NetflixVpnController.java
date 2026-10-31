package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

/**
 * Owns only the VPN connection started for a Netflix-remote-key YouTube session.
 * A VPN already active before the key press is never toggled on exit.
 */
final class NetflixVpnController {
    private static final String TAG = "TvHome.NetflixVPN";
    private static final String YOUTUBE = "com.google.android.youtube.tv";
    private static final String HAPP = "com.happproxy";
    private static final long CONNECT_TIMEOUT_MS = 7000;
    private static final long POLL_MS = 350;
    private static final long EXIT_GRACE_MS = 1400;
    private final Context context;
    private final Handler handler;
    private final Runnable openYoutube;
    private boolean running;
    private boolean youtubeSeen;
    private boolean switchRequested;
    private boolean owned;
    private long connectDeadline;
    private Runnable connectPoll;
    private Runnable pendingExit;

    NetflixVpnController(Context context, Handler handler, Runnable openYoutube) {
        this.context = context;
        this.handler = handler;
        this.openYoutube = openYoutube;
    }

    /** null means the VPN state cannot be read, so we must not toggle it. */
    private Boolean isVpnUp() {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return null;
            Network[] nets = cm.getAllNetworks();
            if (nets == null) return null;
            for (Network net : nets) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
            }
            return false;
        } catch (RuntimeException e) {
            Log.w(TAG, "VPN state unavailable", e);
            return null;
        }
    }

    private boolean toggleHapp() {
        Intent click = new Intent("com.happproxy.action.widget.click");
        click.setComponent(new ComponentName(HAPP, HAPP + ".receiver.WidgetProvider"));
        try {
            context.sendBroadcast(click);
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Happ Proxy rejected widget broadcast from launcher", e);
            return false;
        }
    }

    private boolean happInstalled() {
        try {
            context.getPackageManager().getPackageInfo(HAPP, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** Both the physical key and Netflix firmware-window fallback may fire. Start only once. */
    void start() {
        if (running) return;
        running = true;
        youtubeSeen = false;
        switchRequested = false;
        owned = false;
        cancelExit();

        Boolean previous = isVpnUp();
        if (!Boolean.FALSE.equals(previous) || !happInstalled() || !toggleHapp()) {
            // Pre-existing VPN or unknown state: do not change it.
            openYoutube.run();
            return;
        }
        switchRequested = true;
        connectDeadline = SystemClock.uptimeMillis() + CONNECT_TIMEOUT_MS;
        connectPoll = new Runnable() {
            @Override public void run() {
                if (!running) return;
                if (Boolean.TRUE.equals(isVpnUp())) {
                    owned = true;
                    connectPoll = null;
                    openYoutube.run();
                } else if (SystemClock.uptimeMillis() >= connectDeadline) {
                    // The VPN may still connect later. On exit we'll re-check before toggling.
                    Log.w(TAG, "Happ Proxy not connected after timeout; launching YouTube");
                    connectPoll = null;
                    openYoutube.run();
                } else {
                    handler.postDelayed(this, POLL_MS);
                }
            }
        };
        handler.postDelayed(connectPoll, POLL_MS);
    }

    /** Track YouTube and its departure through AccessibilityService window-state events. */
    void onWindow(String packageName) {
        if (!running) return;
        if (YOUTUBE.equals(packageName)) {
            youtubeSeen = true;
            if (switchRequested && Boolean.TRUE.equals(isVpnUp())) owned = true;
            cancelExit();
            return;
        }
        if (!youtubeSeen || "com.android.systemui".equals(packageName)
                || "android".equals(packageName) || packageName.contains("inputmethod")) return;
        cancelExit();
        pendingExit = () -> {
            pendingExit = null;
            finish();
        };
        handler.postDelayed(pendingExit, EXIT_GRACE_MS);
    }

    private void cancelExit() {
        if (pendingExit != null) handler.removeCallbacks(pendingExit);
        pendingExit = null;
    }

    private void cancelPoll() {
        if (connectPoll != null) handler.removeCallbacks(connectPoll);
        connectPoll = null;
    }

    /** Close a session when YouTube was left, not when a system overlay briefly appeared. */
    void finish() {
        if (!running) return;
        running = false;
        cancelPoll();
        cancelExit();
        // Only this session requested a connection while Android confirmed that VPN was OFF.
        // Do not blindly toggle if disconnected or network status is unknown.
        if ((owned || switchRequested) && Boolean.TRUE.equals(isVpnUp())) {
            toggleHapp();
        }
        youtubeSeen = false;
        switchRequested = false;
        owned = false;
    }

    /** Service teardown must not send a surprise toggle while another app is foreground. */
    void abandon() {
        running = false;
        cancelPoll();
        cancelExit();
        youtubeSeen = false;
        switchRequested = false;
        owned = false;
    }

    boolean isRunning() { return running; }
}
