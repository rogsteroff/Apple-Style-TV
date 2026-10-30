package com.gathin.tvupdater;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class MainActivity extends Activity {
    private static final String REPO = "rogsteroff/Apple-Style-TV";
    private static final String TARGET = "com.gathin.tvhome.applestyle";
    private TextView status;
    private Button check;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(64, 40, 64, 40);
        layout.setBackgroundColor(Color.rgb(20, 18, 34));
        TextView title = new TextView(this);
        title.setText("Apple Style TV • Обновления");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        layout.addView(title);
        status = new TextView(this);
        status.setText("Нажмите, чтобы проверить новую версию.");
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(18);
        status.setPadding(0, 25, 0, 25);
        layout.addView(status);
        check = new Button(this);
        check.setText("Проверить и установить");
        check.setOnClickListener(v -> begin());
        layout.addView(check);
        setContentView(layout);
    }

    private void show(String message) { runOnUiThread(() -> status.setText(message)); }

    private void begin() {
        if (android.os.Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            show("Разрешите установку приложений для Apple Style TV Updater, затем нажмите кнопку снова.");
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }
        check.setEnabled(false);
        show("Проверяю GitHub Releases…");
        new Thread(() -> {
            try { downloadAndInstall(); }
            catch (Exception e) { show("Ошибка: " + e.getMessage()); }
            finally { runOnUiThread(() -> check.setEnabled(true)); }
        }).start();
    }

    private void downloadAndInstall() throws Exception {
        HttpURLConnection api = (HttpURLConnection) new URL(
            "https://api.github.com/repos/" + REPO + "/releases/latest").openConnection();
        api.setConnectTimeout(15000);
        api.setReadTimeout(15000);
        api.setRequestProperty("Accept", "application/vnd.github+json");
        if (api.getResponseCode() != 200) {
            throw new Exception("Релиз пока не опубликован (HTTP " + api.getResponseCode() + ")");
        }
        JSONObject release;
        try (InputStream stream = api.getInputStream()) {
            release = new JSONObject(new String(readAll(stream), java.nio.charset.StandardCharsets.UTF_8));
        } finally { api.disconnect(); }
        JSONArray assets = release.getJSONArray("assets");
        String apkUrl = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if ("AppleStyleTV.apk".equals(asset.optString("name"))) {
                apkUrl = asset.getString("browser_download_url");
                break;
            }
        }
        if (apkUrl == null) throw new Exception("В последнем релизе нет AppleStyleTV.apk");
        if (!apkUrl.startsWith("https://github.com/" + REPO + "/releases/download/"))
            throw new Exception("Недоверенная ссылка APK");
        show("Загружаю APK…");
        HttpURLConnection download = (HttpURLConnection) new URL(apkUrl).openConnection();
        download.setConnectTimeout(15000);
        download.setReadTimeout(60000);
        download.setInstanceFollowRedirects(true);
        if (download.getResponseCode() != 200) throw new Exception("Загрузка: HTTP " + download.getResponseCode());
        PackageInstaller installer = getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(TARGET);
        int id = installer.createSession(params);
        boolean committed = false;
        try (PackageInstaller.Session session = installer.openSession(id)) {
            try (InputStream in = download.getInputStream();
                 OutputStream out = session.openWrite("base.apk", 0, -1)) {
                byte[] buf = new byte[32768];
                int n;
                long total = 0;
                while ((n = in.read(buf)) != -1) {
                    total += n;
                    if (total > 150_000_000) throw new Exception("APK слишком большой");
                    out.write(buf, 0, n);
                }
                session.fsync(out);
            }
            show("Запрашиваю установку Android…");
            Intent callback = new Intent(this, InstallResultActivity.class);
            PendingIntent pending = PendingIntent.getActivity(this, id, callback,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            session.commit(pending.getIntentSender());
            committed = true;
        } finally {
            download.disconnect();
            if (!committed) installer.abandonSession(id);
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            if (out.size() > 1_000_000) throw new Exception("Ответ GitHub слишком большой");
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
