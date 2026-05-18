package dat.nguyenvan.smarthandwritingai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private SeekBar seekbarThreshold;
    private TextView tvThresholdValue;
    private TextView tvAccountEmail;
    private MaterialSwitch switchFirebase;
    private MaterialSwitch switchLanguage;
    private MaterialSwitch switchDarkMode;
    private MaterialSwitch switchTTS;

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
        switchDarkMode = findViewById(R.id.switch_dark_mode);
        switchTTS = findViewById(R.id.switch_tts);

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
            AppCompatDelegate.setApplicationLocales(
                androidx.core.os.LocaleListCompat.create(java.util.Locale.forLanguageTag(isChecked ? "en" : "vi"))
            );
        });

        // Dark/Light theme toggle
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("isDarkMode", isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(
                isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
            );
        });

        // TTS toggle
        switchTTS.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("tts_enabled", isChecked).apply();
            String msg = isChecked ? "Đã bật đọc kết quả bằng giọng nói" : "Đã tắt giọng nói AI";
            UIUtils.showSuccessSnackbar(findViewById(android.R.id.content), msg);
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

        // Account section
        tvAccountEmail = findViewById(R.id.tv_account_email);
        findViewById(R.id.btn_logout).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            UIUtils.showSuccessSnackbar(findViewById(android.R.id.content), "Đã đăng xuất");
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
        findViewById(R.id.btn_go_login).setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));
    }

    private void loadSettings() {
        int threshold = prefs.getInt("confidence_threshold", 50);
        boolean sync = prefs.getBoolean("firebase_sync", false);
        boolean isDarkMode = prefs.getBoolean("isDarkMode", true); // Default dark
        boolean ttsEnabled = prefs.getBoolean("tts_enabled", true);

        seekbarThreshold.setProgress(threshold);
        tvThresholdValue.setText(threshold + "%");
        switchFirebase.setChecked(sync);
        switchLanguage.setChecked(prefs.getBoolean("isEnglish", false));
        switchDarkMode.setChecked(isDarkMode);
        switchTTS.setChecked(ttsEnabled);

        // Show current user info
        if (tvAccountEmail != null) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                tvAccountEmail.setText("✅ Đã đăng nhập: " + user.getEmail());
            } else {
                tvAccountEmail.setText("⬜ Chưa đăng nhập (Offline)");
            }
        }
    }
}
