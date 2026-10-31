package com.gathin.tvupdater;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Bundle;
import android.widget.Toast;

public final class InstallResultActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        int status = getIntent().getIntExtra(PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirmation != null) startActivity(confirmation);
        } else {
            Toast.makeText(this,
                status == PackageInstaller.STATUS_SUCCESS ? "Apple Style TV обновлён" :
                "Не удалось установить APK: " + getIntent().getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
                Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
