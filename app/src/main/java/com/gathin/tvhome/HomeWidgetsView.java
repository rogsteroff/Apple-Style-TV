package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small tvOS-style live weather and VPN controls. No location permission or API key needed. */
public final class HomeWidgetsView extends LinearLayout {
    private static final String WEATHER_URL =
            "https://api.open-meteo.com/v1/forecast?latitude=55.0415&longitude=82.9346"
            + "&current=temperature_2m,weather_code&timezone=Asia%2FNovosibirsk";
    private static final long REFRESH_MS = 30L * 60 * 1000;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final TextView weather;
    private final TextView vpn;
    private boolean active;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!active) return;
            loadWeather();
            updateVpn();
            main.postDelayed(this, REFRESH_MS);
        }
    };
    private final Runnable vpnPoll = new Runnable() {
        @Override public void run() {
            if (!active) return;
            updateVpn();
            main.postDelayed(this, 5000);
        }
    };

    public HomeWidgetsView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.END);
        setClipChildren(false);
        int gap = dp(9);
        weather = label(16);
        weather.setText("НОВОСИБИРСК  ·  Погода…");
        weather.setFocusable(false);
        weather.setPadding(dp(18), dp(13), dp(18), dp(13));
        weather.setBackground(panel());
        addView(weather, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        vpn = label(15);
        vpn.setText("VPN  ·  Проверка…");
        vpn.setGravity(Gravity.CENTER);
        vpn.setPadding(dp(18), dp(11), dp(18), dp(11));
        vpn.setBackground(panel());
        vpn.setFocusable(true);
        vpn.setClickable(true);
        vpn.setOnClickListener(v -> openVpn());
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = gap;
        addView(vpn, lp);
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView label(int size) {
        TextView t = new TextView(getContext());
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        t.setSingleLine(true);
        return t;
    }
    private GradientDrawable panel() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xB523293B);
        d.setCornerRadius(dp(19));
        d.setStroke(dp(1), 0x44FFFFFF);
        return d;
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        active = true;
        main.post(refresh);
        main.post(vpnPoll);
    }
    @Override protected void onDetachedFromWindow() {
        active = false;
        main.removeCallbacks(refresh);
        main.removeCallbacks(vpnPoll);
        io.shutdownNow();
        super.onDetachedFromWindow();
    }
    private void loadWeather() {
        io.execute(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(WEATHER_URL).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("Accept", "application/json");
                if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
                StringBuilder b = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) b.append(line);
                }
                JSONObject current = new JSONObject(b.toString()).getJSONObject("current");
                double temp = current.getDouble("temperature_2m");
                int code = current.getInt("weather_code");
                String condition = code == 0 ? "☀ Ясно" : code <= 3 ? "⛅ Облачно"
                        : code <= 48 ? "☁ Туман" : code <= 67 ? "☂ Дождь"
                        : code <= 77 ? "❄ Снег" : code <= 82 ? "☂ Осадки"
                        : code <= 86 ? "❄ Снег" : "⛈ Гроза";
                String text = "НОВОСИБИРСК  ·  " + Math.round(temp) + "°  " + condition;
                getContext().getSharedPreferences("home_weather", Context.MODE_PRIVATE)
                        .edit().putString("last", text).apply();
                main.post(() -> { if (active) weather.setText(text); });
            } catch (Exception e) {
                String saved = getContext().getSharedPreferences("home_weather", Context.MODE_PRIVATE)
                        .getString("last", null);
                main.post(() -> { if (active) weather.setText(saved == null
                        ? "НОВОСИБИРСК  ·  Нет данных" : saved + "  ·  ранее"); });
            } finally {
                if (c != null) c.disconnect();
            }
        });
    }
    private boolean vpnConnected() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }
    private void updateVpn() {
        boolean on = vpnConnected();
        vpn.setText(on ? "🔒 VPN  ·  Включён" : "🔓 VPN  ·  Выключен");
        vpn.setContentDescription(on ? "VPN включён. Открыть управление" : "VPN выключен. Открыть управление");
    }
    private void openVpn() {
        String pkg = getResources().getString(R.string.vpn_app);
        Intent intent = null;
        if (pkg != null && !pkg.trim().isEmpty()) {
            PackageManager pm = getContext().getPackageManager();
            intent = pm.getLeanbackLaunchIntentForPackage(pkg.trim());
            if (intent == null) intent = pm.getLaunchIntentForPackage(pkg.trim());
        }
        if (intent == null) intent = new Intent(Settings.ACTION_VPN_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { getContext().startActivity(intent); }
        catch (Exception ignored) {
            try { getContext().startActivity(new Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (Exception ignoredAgain) { }
        }
    }
}
