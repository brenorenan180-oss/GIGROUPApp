package com.gigroup.linkopener.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import com.gigroup.linkopener.R;
import com.gigroup.linkopener.util.StorageManager;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * SettingsActivity v3 — Material Design 3
 * Chips para dias, Slider para limite, ProgressIndicator, BottomSheet para logs.
 */
public class SettingsActivity extends AppCompatActivity {

    private StorageManager storage;

    // UI refs
    private SwitchMaterial swEnabled;
    private TextInputEditText etCpf, etNasc;
    private ChipGroup chipDays;
    private Slider sliderLimit;
    private TextView tvSliderValue, tvProgress, tvTotal, tvToday, tvLimit;
    private LinearProgressIndicator progressIndicator;

    // IDs dos chips de dias (0=Dom…6=Sáb)
    private final int[] CHIP_IDS = {
        R.id.chip_dom, R.id.chip_seg, R.id.chip_ter,
        R.id.chip_qua, R.id.chip_qui, R.id.chip_sex, R.id.chip_sab
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
            getSupportActionBar().setBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(
                    getResources().getColor(R.color.bg_toolbar, getTheme())));
        }

        storage = new StorageManager(this);
        bindViews();
        loadValues();
        setupListeners();
    }

    private void bindViews() {
        swEnabled       = findViewById(R.id.sw_enabled);
        etCpf           = findViewById(R.id.et_cpf);
        etNasc          = findViewById(R.id.et_nasc);
        chipDays        = findViewById(R.id.chip_days);
        sliderLimit     = findViewById(R.id.slider_limit);
        tvSliderValue   = findViewById(R.id.tv_slider_value);
        tvProgress      = findViewById(R.id.tv_progress);
        tvTotal         = findViewById(R.id.tv_total);
        tvToday         = findViewById(R.id.tv_today);
        tvLimit         = findViewById(R.id.tv_limit);
        progressIndicator = findViewById(R.id.progress_indicator);
    }

    private void loadValues() {
        // Toggle
        swEnabled.setChecked(storage.isEnabled());

        // Perfil
        etCpf.setText(storage.getCpf());
        etNasc.setText(storage.getDataNascimento());

        // Limite (slider)
        int limit = storage.getDailyLimit();
        sliderLimit.setValue(Math.min(limit, 100));
        tvSliderValue.setText(String.valueOf(limit));

        // Chips de dias
        String raw = storage.getAllowedDaysRaw();
        for (int i = 0; i < 7; i++) {
            Chip chip = findViewById(CHIP_IDS[i]);
            if (chip != null) chip.setChecked(raw.contains(String.valueOf(i)));
        }

        // Stats e progresso
        updateStats();
    }

    private void setupListeners() {
        // Toggle automação
        swEnabled.setOnCheckedChangeListener((v, checked) -> {
            storage.setEnabled(checked);
            if (MainActivity.webView != null) {
                String js = "if(window.__GIGROUP_dispatchEnabled) window.__GIGROUP_dispatchEnabled(" + checked + ");";
                MainActivity.webView.post(() -> MainActivity.webView.evaluateJavascript(js, null));
            }
        });

        // Slider → atualiza label em tempo real
        sliderLimit.addOnChangeListener((slider, value, fromUser) -> {
            tvSliderValue.setText(String.valueOf((int) value));
        });

        // Botão salvar
        MaterialButton btnSave = findViewById(R.id.btn_save);
        btnSave.setOnClickListener(v -> save());

        // Botão zerar histórico
        MaterialButton btnClear = findViewById(R.id.btn_clear);
        btnClear.setOnClickListener(v -> {
            storage.clearHistory();
            updateStats();
            Toast.makeText(this, "Histórico zerado ✓", Toast.LENGTH_SHORT).show();
        });

        // Botão ver logs (BottomSheet)
        MaterialButton btnLogs = findViewById(R.id.btn_logs);
        btnLogs.setOnClickListener(v -> showLogsBottomSheet());

        // Botão ignorar otimizações de bateria
        MaterialButton btnBattery = findViewById(R.id.btn_battery);
        btnBattery.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }
        });

        // Botão ativar NotificationListener
        MaterialButton btnNotif = findViewById(R.id.btn_notif_access);
        btnNotif.setOnClickListener(v ->
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
    }

    private void save() {
        // Perfil
        String cpf  = etCpf.getText() != null ? etCpf.getText().toString().trim() : "";
        String nasc = etNasc.getText() != null ? etNasc.getText().toString().trim() : "";
        storage.saveCpf(cpf);
        storage.saveDataNascimento(nasc);

        // Limite do slider
        storage.setDailyLimit((int) sliderLimit.getValue());

        // Dias (chips)
        StringBuilder days = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            Chip chip = findViewById(CHIP_IDS[i]);
            if (chip != null && chip.isChecked()) {
                if (days.length() > 0) days.append(",");
                days.append(i);
            }
        }
        storage.setAllowedDays(days.toString());

        updateStats();
        Toast.makeText(this, "✓ Configurações salvas!", Toast.LENGTH_SHORT).show();
    }

    private void updateStats() {
        int total = storage.getTotalOpened();
        int today = storage.getDailyCount();
        int limit = storage.getDailyLimit();

        if (tvTotal != null) tvTotal.setText(String.valueOf(total));
        if (tvToday != null) tvToday.setText(String.valueOf(today));
        if (tvLimit != null) tvLimit.setText(String.valueOf(limit));

        // Progress indicator
        int pct = limit > 0 ? Math.min(100, (today * 100) / limit) : 0;
        if (progressIndicator != null) progressIndicator.setProgressCompat(pct, true);
        if (tvProgress != null) tvProgress.setText(today + " / " + limit);
    }

    // ── BottomSheet de logs ───────────────────────────────────────────────────
    private void showLogsBottomSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this, R.style.BottomSheetStyle);
        sheet.setContentView(R.layout.bottom_sheet_logs);

        TextView tvLogs = sheet.findViewById(R.id.tv_logs_content);
        MaterialButton btnClose = sheet.findViewById(R.id.btn_close_logs);

        if (tvLogs != null) {
            try {
                JSONArray logs = new JSONArray(storage.getActionLogJson());
                if (logs.length() == 0) {
                    tvLogs.setText("Nenhuma ação registrada ainda.");
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < Math.min(logs.length(), 20); i++) {
                        JSONObject e = logs.getJSONObject(i);
                        sb.append("• ").append(e.optString("ts", "")).append("\n");
                        String snippet = e.optString("snippet", "");
                        if (!snippet.isEmpty()) sb.append("  \"").append(snippet, 0, Math.min(snippet.length(), 50)).append("\"\n");
                        sb.append("  ").append(e.optString("url", "")).append("\n\n");
                    }
                    tvLogs.setText(sb.toString());
                }
            } catch (Exception e) {
                tvLogs.setText("Erro ao carregar logs.");
            }
        }

        if (btnClose != null) btnClose.setOnClickListener(v -> sheet.dismiss());
        sheet.show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
