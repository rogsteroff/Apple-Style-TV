package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Live translucent-glass weather card inspired by the TV's reference mockup.
 * The city stays explicit (no location permissions or GPS prompts).
 */
public final class HomeWidgetsView extends LinearLayout {
    private static final String WEATHER_URL =
            "https://api.open-meteo.com/v1/forecast?latitude=55.0415&longitude=82.9346"
            + "&current=temperature_2m,weather_code&timezone=Asia%2FNovosibirsk";
    private static final String BACKUP_WEATHER_URL =
            "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=55.0415&lon=82.9346";
    private static final String TAG = "TvHome.Weather";
    private static final long REFRESH_MS = 30L * 60 * 1000L;
    private static final long RETRY_MS = 3L * 60 * 1000L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private ExecutorService io = Executors.newSingleThreadExecutor();
    private final TextView city, glyph, degrees, condition;
    private boolean active, loading;
    private int generation;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (active) loadWeather();
        }
    };

    public HomeWidgetsView(Context c) {
        super(c);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(17), dp(12), dp(17), dp(12));
        setMinimumWidth(dp(235));
        setMinimumHeight(dp(113));
        setFocusable(true);
        setClickable(true);
        setClipToPadding(false);
        setBackground(glass());
        setOnFocusChangeListener((v, focused) -> {
            setBackground(focused ? focusGlass() : glass());
            animate().scaleX(focused ? 1.035f : 1f)
                    .scaleY(focused ? 1.035f : 1f).setDuration(140).start();
        });
        setOnClickListener(v -> loadWeather());
        setContentDescription("Погода в Новосибирске. Нажмите для обновления");

        city = label(14, 0xFFF6F5FC);
        city.setText("Новосибирск  ◈");
        city.setSingleLine(true);
        addView(city, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout mainReading = new LinearLayout(c);
        mainReading.setOrientation(HORIZONTAL);
        mainReading.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams readingLp = new LayoutParams(LayoutParams.WRAP_CONTENT, dp(56));
        readingLp.topMargin = dp(1);
        addView(mainReading, readingLp);

        glyph = label(32, Color.WHITE);
        glyph.setGravity(Gravity.CENTER);
        glyph.setText("☁");
        mainReading.addView(glyph, new LayoutParams(dp(62), LayoutParams.MATCH_PARENT));

        degrees = label(40, Color.WHITE);
        degrees.setIncludeFontPadding(false);
        degrees.setText("—°");
        mainReading.addView(degrees, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        condition = label(13, 0xFFD1D7E4);
        condition.setText("Загрузка погоды…");
        LayoutParams conditionLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        conditionLp.leftMargin = dp(62);
        addView(condition, conditionLp);
    }

    public HomeWidgetsView(Context c, AttributeSet a) { this(c); }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private TextView label(int size, int color) {
        TextView v = new TextView(getContext());
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setSingleLine(true);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return v;
    }

    private GradientDrawable glass() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xA35B5669, 0xA52B3D54});
        d.setCornerRadius(dp(22));
        d.setStroke(dp(1), 0x6CDDE2EF);
        return d;
    }

    private GradientDrawable focusGlass() {
        GradientDrawable d = glass();
        d.setStroke(dp(2), 0xFFFFF3DF);
        return d;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (io.isShutdown()) io = Executors.newSingleThreadExecutor();
        active = true;
        SharedPreferences prefs = getContext().getSharedPreferences("home_weather", Context.MODE_PRIVATE);
        if (prefs.contains("last_temp")) {
            show(prefs.getInt("last_temp", 0), prefs.getString("last_condition", "Погода"),
                    prefs.getString("last_glyph", "☁"));
        }
        main.post(refresh);
    }

    @Override protected void onDetachedFromWindow() {
        active = false;
        generation++;
        loading = false;
        main.removeCallbacks(refresh);
        io.shutdownNow();
        super.onDetachedFromWindow();
    }

    private void show(int temperature, String description, String symbol) {
        degrees.setText(temperature + "°");
        glyph.setText(symbol);
        condition.setText(description);
        setContentDescription("Новосибирск, " + temperature + " градусов, " + description
                + ". Нажмите для обновления");
    }

    private void loadWeather() {
        if (!active || loading) return;
        loading = true;
        final int requestGeneration = generation;
        io.execute(() -> {
            Reading reading = null;
            try {
                JSONObject current = fetchJson(WEATHER_URL).getJSONObject("current");
                int code = current.getInt("weather_code");
                reading = new Reading((int) Math.round(current.getDouble("temperature_2m")),
                        openMeteoCondition(code), openMeteoIcon(code));
            } catch (Exception primary) {
                Log.w(TAG, "Open-Meteo failed, falling back to met.no", primary);
                try {
                    JSONObject data = fetchJson(BACKUP_WEATHER_URL).getJSONObject("properties")
                            .getJSONArray("timeseries").getJSONObject(0).getJSONObject("data");
                    int temp = (int) Math.round(data.getJSONObject("instant").getJSONObject("details")
                            .getDouble("air_temperature"));
                    JSONObject next = data.optJSONObject("next_1_hours");
                    if (next == null) next = data.optJSONObject("next_6_hours");
                    JSONObject summary = next == null ? null : next.optJSONObject("summary");
                    String symbol = summary == null ? "" : summary.optString("symbol_code", "");
                    reading = new Reading(temp, metNoCondition(symbol), metNoIcon(symbol));
                } catch (Exception backup) {
                    Log.w(TAG, "Both weather feeds failed", backup);
                }
            }
            final Reading result = reading;
            main.post(() -> {
                if (!active || requestGeneration != generation) return;
                loading = false;
                if (result != null) {
                    show(result.temperature, result.description, result.icon);
                    getContext().getSharedPreferences("home_weather", Context.MODE_PRIVATE).edit()
                            .putInt("last_temp", result.temperature)
                            .putString("last_condition", result.description)
                            .putString("last_glyph", result.icon).apply();
                    main.removeCallbacks(refresh);
                    main.postDelayed(refresh, REFRESH_MS);
                } else {
                    if (!getContext().getSharedPreferences("home_weather", Context.MODE_PRIVATE)
                            .contains("last_temp")) condition.setText("Нет данных · нажми ещё раз");
                    main.removeCallbacks(refresh);
                    main.postDelayed(refresh, RETRY_MS);
                }
            });
        });
    }

    private static final class Reading {
        final int temperature;
        final String description, icon;
        Reading(int temp, String description, String icon) {
            this.temperature = temp;
            this.description = description;
            this.icon = icon;
        }
    }

    private static JSONObject fetchJson(String url) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(7000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent",
                    "AppleStyleTV/3.14 (https://github.com/rogsteroff/Apple-Style-TV)");
            if (connection.getResponseCode() != 200) {
                throw new java.io.IOException("Weather HTTP " + connection.getResponseCode());
            }
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    response.append(buffer, 0, count);
                    if (response.length() > 256 * 1024) {
                        throw new java.io.IOException("Oversized weather response");
                    }
                }
            }
            return new JSONObject(response.toString());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String openMeteoCondition(int code) {
        if (code == 0) return "Ясно";
        if (code <= 3) return "Переменная облачность";
        if (code <= 48) return "Туман";
        if (code <= 67) return "Дождь";
        if (code <= 77) return "Снег";
        if (code <= 82) return "Осадки";
        if (code <= 86) return "Снег";
        return "Гроза";
    }

    private static String openMeteoIcon(int code) {
        if (code == 0) return "☀";
        if (code <= 3) return "☁";
        if (code <= 48) return "☁";
        if (code <= 67) return "☂";
        if (code <= 77) return "❄";
        if (code <= 82) return "☂";
        if (code <= 86) return "❄";
        return "⚡";
    }

    private static String metNoCondition(String symbol) {
        String s = symbol.toLowerCase(Locale.ROOT);
        if (s.contains("thunder")) return "Гроза";
        if (s.contains("snow") || s.contains("sleet")) return "Снег";
        if (s.contains("rain") || s.contains("drizzle")) return "Дождь";
        if (s.contains("fog")) return "Туман";
        if (s.contains("partlycloudy") || s.contains("fair")) return "Переменная облачность";
        if (s.contains("cloudy")) return "Облачно";
        if (s.contains("clear")) return "Ясно";
        return "Погода";
    }

    private static String metNoIcon(String symbol) {
        String s = symbol.toLowerCase(Locale.ROOT);
        if (s.contains("thunder")) return "⚡";
        if (s.contains("snow") || s.contains("sleet")) return "❄";
        if (s.contains("rain") || s.contains("drizzle")) return "☂";
        if (s.contains("clear")) return "☀";
        return "☁";
    }
}
