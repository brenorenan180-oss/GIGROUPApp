package com.gigroup.linkopener.ui;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.gigroup.linkopener.R;
import com.gigroup.linkopener.util.StorageManager;

public class SettingsActivity extends AppCompatActivity {

    private StorageManager storage;
    private final int[] DAY_IDS = {
        R.id.cb_dom, R.id.cb_seg, R.id.cb_ter,
        R.id.cb_qua, R.id.cb_qui, R.id.cb_sex, R.id.cb_sab
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Configurações");
        }

        storage = new StorageManager(this);
        load();
        setupListeners();
    }

    private void load() {
        ((Switch) findViewById(R.id.sw_enabled)).setChecked(storage.isEnabled());
        ((EditText) findViewById(R.id.et_cpf)).setText(storage.getCpf());
        ((EditText) findViewById(R.id.et_nasc)).setText(storage.getDataNascimento());
        ((EditText) findViewById(R.id.et_daily_limit)).setText(String.valueOf(storage.getDailyLimit()));

        String raw = storage.getAllowedDaysRaw();
        for (int i = 0; i < 7; i++) {
            CheckBox cb = findViewById(DAY_IDS[i]);
            if (cb != null) cb.setChecked(raw.contains(String.valueOf(i)));
        }
        updateStats();
    }

    private void setupListeners() {
        ((Switch) findViewById(R.id.sw_enabled)).setOnCheckedChangeListener((v, checked) -> {
            storage.setEnabled(checked);
            if (MainActivity.webView != null) {
                String js = "if(window.__GIGROUP_dispatchEnabled) window.__GIGROUP_dispatchEnabled(" + checked + ");";
                MainActivity.webView.post(() -> MainActivity.webView.evaluateJavascript(js, null));
            }
        });

        findViewById(R.id.btn_save).setOnClickListener(v -> save());
        findViewById(R.id.btn_clear_history).setOnClickListener(v -> {
            storage.clearHistory();
            updateStats();
            Toast.makeText(this, "Histórico zerado ✓", Toast.LENGTH_SHORT).show();
        });
    }

    private void save() {
        storage.saveCpf(((EditText) findViewById(R.id.et_cpf)).getText().toString().trim());
        storage.saveDataNascimento(((EditText) findViewById(R.id.et_nasc)).getText().toString().trim());

        try {
            storage.setDailyLimit(Integer.parseInt(
                ((EditText) findViewById(R.id.et_daily_limit)).getText().toString().trim()));
        } catch (Exception e) { storage.setDailyLimit(50); }

        StringBuilder days = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            CheckBox cb = findViewById(DAY_IDS[i]);
            if (cb != null && cb.isChecked()) {
                if (days.length() > 0) days.append(",");
                days.append(i);
            }
        }
        storage.setAllowedDays(days.toString());
        Toast.makeText(this, "✓ Salvo!", Toast.LENGTH_SHORT).show();
    }

    private void updateStats() {
        TextView t = findViewById(R.id.tv_total);
        TextView d = findViewById(R.id.tv_today);
        TextView l = findViewById(R.id.tv_limit);
        if (t != null) t.setText(String.valueOf(storage.getTotalOpened()));
        if (d != null) d.setText(String.valueOf(storage.getDailyCount()));
        if (l != null) l.setText(String.valueOf(storage.getDailyLimit()));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
