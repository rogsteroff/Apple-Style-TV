package com.gathin.tvhome;

/** Build-specific Xiaomi remote routes, independent of Android for regression testing. */
final class AppleRemoteShortcuts {
    private AppleRemoteShortcuts() { }

    static String forKey(int scanCode, int keyCode) {
        if (scanCode == 393) return "com.google.android.youtube.tv";
        if (scanCode == 400) return "top.rootu.lampa";
        // Linux KEY_VOICECOMMAND and Android KEYCODE_VOICE_ASSIST / KEYCODE_ASSIST.
        if (scanCode == 582 || keyCode == 231 || keyCode == 219) return "hub_plus";
        return null;
    }

    static String forWindow(String packageName) {
        if ("com.netflix.ninja".equals(packageName)) return "com.google.android.youtube.tv";
        if ("com.amazon.amazonvideo.livingroom".equals(packageName)) return "top.rootu.lampa";
        if ("com.google.android.katniss".equals(packageName)
                || "com.google.android.googlequicksearchbox".equals(packageName)) return "hub_plus";
        return null;
    }
}
