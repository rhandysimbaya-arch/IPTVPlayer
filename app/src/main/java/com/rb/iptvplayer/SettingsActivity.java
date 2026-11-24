package com.rb.iptvplayer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        final SharedPreferences sp = getSharedPreferences("prefs", Context.MODE_PRIVATE);

        MaterialToolbar tb = findViewById(R.id.toolbar);
        tb.setNavigationOnClickListener(v -> finish());

        MaterialSwitch sw = findViewById(R.id.autoSwitch);
        sw.setChecked(sp.getBoolean("auto_update", true));
        sw.setOnCheckedChangeListener((b, checked) -> sp.edit().putBoolean("auto_update", checked).apply());

        TextView last = findViewById(R.id.lastUpdate);
        long t = sp.getLong("last_update", 0);
        last.setText(t == 0 ? "Last updated: never"
                : "Last updated: " + DateUtils.getRelativeTimeSpanString(t, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS));

        findViewById(R.id.updateNow).setOnClickListener(v -> {
            if (Updater.get(this).start(true, true, null)) {
                Toast.makeText(this, "Updating channels…", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "An update is already running", Toast.LENGTH_SHORT).show();
            }
            finish();
        });
    }
}
