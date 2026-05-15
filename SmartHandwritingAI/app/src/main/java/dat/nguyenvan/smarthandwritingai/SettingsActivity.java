package dat.nguyenvan.smarthandwritingai;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private SeekBar seekbarThreshold;
    private TextView tvThresholdValue;
    private MaterialSwitch switchFirebase;
    private MaterialSwitch switchLanguage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("AI_CONFIG", MODE_PRIVATE);

        initViews();
        loadSettings();
    }

    private void initViews() {
        seekbarThreshold = findViewById(R.id.seekbar_threshold);
        tvThresholdValue = findViewById(R.id.tv_threshold_value);
        switchFirebase = findViewById(R.id.switch_firebase);
        switchLanguage = findViewById(R.id.switch_language);

        findViewById(R.id.btn_back_settings).setOnClickListener(v -> finish());

        seekbarThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvThresholdValue.setText(progress + "%");
                prefs.edit().putInt("confidence_threshold", progress).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        switchFirebase.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("firebase_sync", isChecked).apply();
            String msg = isChecked ? "Đã bật đồng bộ Firebase" : "Đã tắt đồng bộ";
            UIUtils.showSuccessSnackbar(findViewById(android.R.id.content), msg);
        });

        
        switchLanguage.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("isEnglish", isChecked).apply();
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                androidx.core.os.LocaleListCompat.create(java.util.Locale.forLanguageTag(isChecked ? "en" : "vi"))
            );
        });
        
        findViewById(R.id.btn_clear_data).setOnClickListener(v -> {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                AppDatabase.getInstance(this).predictionDao().deleteAll();
                runOnUiThread(() -> {
                    UIUtils.showSuccessSnackbar(findViewById(android.R.id.content), "Đã xóa toàn bộ lịch sử!");
                });
            });
            executor.shutdown();
        });
    }

    private void loadSettings() {
        int threshold = prefs.getInt("confidence_threshold", 50); // Default 50%
        boolean sync = prefs.getBoolean("firebase_sync", false);

        seekbarThreshold.setProgress(threshold);
        tvThresholdValue.setText(threshold + "%");
        switchFirebase.setChecked(sync);
        switchLanguage.setChecked(prefs.getBoolean("isEnglish", false));
    }
}
