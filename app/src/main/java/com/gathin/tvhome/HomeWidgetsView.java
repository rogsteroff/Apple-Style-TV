package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Compact live weather widget. VPN remains an ordinary dock app, not a widget. */
public final class HomeWidgetsView extends LinearLayout {
    private static final String WEATHER_URL =
            "https://api.open-meteo.com/v1/forecast?latitude=55.0415&longitude=82.9346"
            + "&current=temperature_2m,weather_code&timezone=Asia%2FNovosibirsk";
    private static final String BACKUP_WEATHER_URL =
            "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=55.0415&lon=82.9346";
    private static final String LOG_TAG = "TvHome.Weather";
    private static final String CITY = "НОВОСИБИРСК";
    private static final long REFRESH_MS = 30L * 60 * 1000;
    private static final long RETRY_MS = 3L * 60 * 1000;
    private boolean weatherLoading;
    private int weatherGeneration;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ExecutorService io = Executors.newSingleThreadExecutor();
    private final TextView weather;
    private boolean active;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!active) return;
            loadWeather();
            main.postDelayed(this, REFRESH_MS);
        }
    };
    public HomeWidgetsView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.END);
        setClipChildren(false);
        weather = label(20);
        weather.setSingleLine(false);
        weather.setMaxLines(3);
        weather.setGravity(Gravity.CENTER_VERTICAL);
        weather.setMinWidth(dp(245));
        weather.setMinHeight(dp(86));
        weather.setText("НОВОСИБИРСК\\n☁ Загрузка погоды…");
        weather.setFocusable(true);
        weather.setClickable(true);
        weather.setContentDescription("Погода в Новосибирске. Нажмите, чтобы обновить");
        weather.setOnClickListener(v -> loadWeather());
        weather.setPadding(dp(21), dp(11), dp(21), dp(11));
        weather.setBackground(panel());
        addView(weather, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    /** Required by Android LayoutInflater for a view declared in XML. */
    public HomeWidgetsView(Context context, AttributeSet attrs) {
        this(context);
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
        d.setColor(0xB52E3655);
        d.setCornerRadius(dp(19));
        d.setStroke(dp(1), 0x88DCE6FF);
        return d;
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (io.isShutdown()) io = Executors.newSingleThreadExecutor();
        active = true;
        String last = getContext().getSharedPreferences("home_weather", Context.MODE_PRIVATE)
                .getString("last", null);
        weather.setText(last == null ? CITY + "\\n☁ Загрузка погоды…" : last + "  ·  ранее");
        main.post(refresh);
    }
    @Override protected void onDetachedFromWindow() {
        active = false;
        weatherGeneration++;
        weatherLoading = false;
        main.removeCallbacks(refresh);
        io.shutdownNow();
        super.onDetachedFromWindow();
    }
    /** Fetch on a worker; keep the previous reading while the network recovers. */
    private void loadWeather() {
        if (!active || weatherLoading) return;
        weatherLoading = true;
        final int requestGeneration = weatherGeneration;
        weather.setText(CITY + "\\n☁ Обновление…");
        io.execute(() -> {
            String reading = null;
            Exception primaryFailure = null;
            Exception backupFailure = null;
            try {
                JSONObject current = fetchJson(WEATHER_URL).getJSONObject("current");
                double temperature = current.getDouble("temperature_2m");
                int code = current.getInt("weather_code");
                reading = formatWeather(temperature, openMeteoCondition(code));
            } catch (Exception e) {
                primaryFailure = e;
                Log.w(LOG_TAG, "Open-Meteo failed; trying met.no", e);
                try {
                    JSONObject time = fetchJson(BACKUP_WEATHER_URL)
                            .getJSONObject("properties").getJSONArray("timeseries").getJSONObject(0);
                    JSONObject data = time.getJSONObject("data");
                    double temperature = data.getJSONObject("instant")
                            .getJSONObject("details").getDouble("air_temperature");
                    JSONObject next = data.optJSONObject("next_1_hours");
                    if (next == null) next = data.optJSONObject("next_6_hours");
                    JSONObject summary = next == null ? null : next.optJSONObject("summary");
                    String symbol = summary == null ? "" : summary.optString("symbol_code", "");
                    reading = formatWeather(temperature, metNoCondition(symbol));
                } catch (Exception fallbackError) {
                    backupFailure = fallbackError;
                    Log.w(LOG_TAG, "Both weather providers failed", fallbackError);
                }
            }
            final String result = reading;
            final String primaryError = primaryFailure == null ? "" : primaryFailure.getClass().getSimpleName();
            final String backupError = backupFailure == null ? "" : backupFailure.getClass().getSimpleName();
            main.post(() -> {
                if (!active || weatherGeneration != requestGeneration) return;
                weatherLoading = false;
                if (result != null) {
                    getContext().getSharedPreferences("home_weather", Context.MODE_PRIVATE)
                            .edit().putString("last", result).apply();
                    weather.setText(result);
                    main.removeCallbacks(refresh);
                    main.postDelayed(refresh, REFRESH_MS);
                    Log.i(LOG_TAG, "Weather updated");
                } else {
                    String last = getContext().getSharedPreferences("home_weather", Context.MODE_PRIVATE)
                            .getString("last", null);
                    weather.setText(last == null ? CITY + "\\nНет данных · нажми для повтора"
                            : last + "  ·  ранее");
                    Log.w(LOG_TAG, "Weather retry in 3 min; failures: "
                            + primaryError + " / " + backupError);
                    main.removeCallbacks(refresh);
                    main.postDelayed(refresh, RETRY_MS);
                }
            });
        });
    }

    private static JSONObject fetchJson(String url) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(7000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/json");
            // met.no requests must identify the calling application.
            connection.setRequestProperty("User-Agent",
                    "AppleStyleTV/3.7 (https://github.com/rogsteroff/Apple-Style-TV)");
            int status = connection.getResponseCode();
            if (status != 200) throw new java.io.IOException("HTTP " + status);
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    response.append(buffer, 0, count);
                    if (response.length() > 256 * 1024) {
                        throw new java.io.IOException("Weather response too large");
                    }
                }
            }
            return new JSONObject(response.toString());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String formatWeather(double temperature, String condition) {
        return CITY + "\\n" + Math.round(temperature) + "°C   " + condition;
    }

    private static String openMeteoCondition(int code) {
        if (code == 0) return "☀ Ясно";
        if (code <= 3) return "⛅ Облачно";
        if (code <= 48) return "☁ Туман";
        if (code <= 67) return "☂ Дождь";
        if (code <= 77) return "❄ Снег";
        if (code <= 82) return "☂ Осадки";
        if (code <= 86) return "❄ Снег";
        return "⛈ Гроза";
    }

    private static String metNoCondition(String symbol) {
        String s = symbol.toLowerCase(Locale.ROOT);
        if (s.contains("thunder")) return "⛈ Гроза";
        if (s.contains("snow") || s.contains("sleet")) return "❄ Снег";
        if (s.contains("rain") || s.contains("drizzle")) return "☂ Дождь";
        if (s.contains("fog")) return "☁ Туман";
        if (s.contains("partlycloudy") || s.contains("fair")) return "⛅ Облачно";
        if (s.contains("cloudy")) return "☁ Облачно";
        if (s.contains("clear")) return "☀ Ясно";
        return "🌡 Погода";
    }

}
