package com.gigroup.linkopener.ui;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.gigroup.linkopener.R;
import com.gigroup.linkopener.util.StorageManager;

/**
 * SettingsActivity — equivalente ao popup.html da extensão Chrome.
 * Permite configurar: CPF, data de nascimento, limite diário, dias permitidos.
 */
public class SettingsActivity extends AppCompatActivity {

    private StorageManager storage;

    // Dias da semana
    private final int[] DAY_IDS = {
        R.id.cb_dom, R.id.cb_seg, R.id.cb_ter,
        R.id.cb_qua, R.id.cb_qui, R.id.cb_sex, R.id.cb_sab
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Configurações");
        }

        storage = new StorageManager(this);

        loadCurrentValues();
        setupListeners();
    }

    private void loadCurrentValues() {
        // Automação on/off
        Switch swEnabled = findViewById(R.id.sw_enabled);
        swEnabled.setChecked(storage.isEnabled());

        // CPF e data nascimento
        EditText etCpf  = findViewById(R.id.et_cpf);
        EditText etNasc = findViewById(R.id.et_nasc);
        etCpf.setText(storage.getCpf());
        etNasc.setText(storage.getDataNascimento());

        // Limite diário
        EditText etLimit = findViewById(R.id.et_daily_limit);
        etLimit.setText(String.valueOf(storage.getDailyLimit()));

        // Dias permitidos
        String raw = storage.getAllowedDaysRaw();
        for (int dow = 0; dow < 7; dow++) {
            boolean on = raw.contains(String.valueOf(dow));
            CheckBox cb = findViewById(DAY_IDS[dow]);
            if (cb != null) cb.setChecked(on);
        }

        // Stats
        updateStats();
    }

    private void setupListeners() {
        Switch swEnabled = findViewById(R.id.sw_enabled);
        swEnabled.setOnCheckedChangeListener((v, checked) -> {
            storage.setEnabled(checked);
            // Notifica o WebView principal
            if (MainActivity.webView != null) {
                String js = "if(window.__GIGROUP_dispatchEnabled) " +
                            "window.__GIGROUP_dispatchEnabled(" + checked + ");";
                MainActivity.webView.post(() ->
                    MainActivity.webView.evaluateJavascript(js, null));
            }
        });

        Button btnSave = findViewById(R.id.btn_save);
        btnSave.setOnClickListener(v -> saveSettings());

        Button btnClear = findViewById(R.id.btn_clear_history);
        btnClear.setOnClickListener(v -> {
            storage.clearHistory();
            updateStats();
            Toast.makeText(this, "Histórico zerado ✓", Toast.LENGTH_SHORT).show();
        });
    }

    private void saveSettings() {
        // Perfil
        EditText etCpf  = findViewById(R.id.et_cpf);
        EditText etNasc = findViewById(R.id.et_nasc);
        storage.saveCpf(etCpf.getText().toString().trim());
        storage.saveDataNascimento(etNasc.getText().toString().trim());

        // Limite diário
        EditText etLimit = findViewById(R.id.et_daily_limit);
        try {
            int limit = Integer.parseInt(etLimit.getText().toString().trim());
            storage.setDailyLimit(limit);
        } catch (NumberFormatException e) {
            storage.setDailyLimit(50);
        }

        // Dias permitidos
        StringBuilder days = new StringBuilder();
        for (int dow = 0; dow < 7; dow++) {
            CheckBox cb = findViewById(DAY_IDS[dow]);
            if (cb != null && cb.isChecked()) {
                if (days.length() > 0) days.append(",");
                days.append(dow);
            }
        }
        storage.setAllowedDays(days.toString());

        Toast.makeText(this, "✓ Configurações salvas!", Toast.LENGTH_SHORT).show();
    }

    private void updateStats() {
        TextView tvTotal = findViewById(R.id.tv_total);
        TextView tvToday = findViewById(R.id.tv_today);
        TextView tvLimit = findViewById(R.id.tv_limit);
        if (tvTotal != null) tvTotal.setText(String.valueOf(storage.getTotalOpened()));
        if (tvToday != null) tvToday.setText(String.valueOf(storage.getDailyCount()));
        if (tvLimit != null) tvLimit.setText(String.valueOf(storage.getDailyLimit()));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
