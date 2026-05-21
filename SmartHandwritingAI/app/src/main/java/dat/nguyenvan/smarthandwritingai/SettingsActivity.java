package dat.nguyenvan.smarthandwritingai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    private SharedPreferences prefs;
    private SeekBar seekbarThreshold;
    private TextView tvThresholdValue;
    private TextView tvAccountEmail;
    private MaterialSwitch switchFirebase;
    private MaterialSwitch switchLanguage;
    private MaterialSwitch switchDarkMode;
    private ImageButton btnTtsToggle;
    private TextView tvTtsStatus;

    private TextToSpeech tts;
    private boolean isTtsReady = false;

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
        btnTtsToggle = findViewById(R.id.btn_tts_toggle);
        tvTtsStatus = findViewById(R.id.tv_tts_status);

        findViewById(R.id.btn_back_settings).setOnClickListener(v -> finish());

        // ── Confidence Threshold ──────────────────────────────────────────
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

        // ── Firebase Sync ──────────────────────────────────────────────────
        switchFirebase.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("firebase_sync", isChecked).apply();
            String msg = isChecked ? "Đã bật đồng bộ Firebase" : "Đã tắt đồng bộ";
            UIUtils.showSuccessSnackbar(findViewById(android.R.id.content), msg);
        });

        // ── Language ────────────────────────────────────────────────────────
        switchLanguage.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("isEnglish", isChecked).apply();
            AppCompatDelegate.setApplicationLocales(
                androidx.core.os.LocaleListCompat.create(java.util.Locale.forLanguageTag(isChecked ? "en" : "vi"))
            );
        });

        // ── Dark/Light Theme Toggle ─────────────────────────────────────────
        // We set the initial state and listener in loadSettings to avoid early trigger

        // ── TTS Toggle Button (Speaker Icon) ─────────────────────────────────
        btnTtsToggle.setOnClickListener(v -> {
            boolean currentState = prefs.getBoolean("tts_enabled", true);
            boolean newState = !currentState;
            prefs.edit().putBoolean("tts_enabled", newState).apply();

            if (newState) {
                enableTts();
            } else {
                disableTts();
            }
            updateTtsUI(newState);
        });

        // ── Clear Data ────────────────────────────────────────────────────────
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

        // ── Account Section ───────────────────────────────────────────────────
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

    // ══════════════════════════════════════════════════════════════════════════
    // TTS Engine Management
    // ══════════════════════════════════════════════════════════════════════════

    private void enableTts() {
        if (tts != null) return; // Already initialized

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int langResult = tts.setLanguage(new Locale("vi", "VN"));
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fallback to default locale
                    tts.setLanguage(Locale.getDefault());
                }
                isTtsReady = true;
                // Speak confirmation
                tts.speak("Đã bật giọng nói", TextToSpeech.QUEUE_FLUSH, null, "tts_test");
                Log.d(TAG, "TTS initialized successfully");
            } else {
                Log.e(TAG, "TTS initialization failed with status: " + status);
                runOnUiThread(() -> UIUtils.showErrorSnackbar(
                        findViewById(android.R.id.content),
                        "Không thể khởi tạo TTS. Kiểm tra engine giọng nói trên thiết bị."));
            }
        });
    }

    private void disableTts() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
            isTtsReady = false;
        }
        UIUtils.showSuccessSnackbar(findViewById(android.R.id.content), "Đã tắt giọng nói AI");
    }

    private void updateTtsUI(boolean isEnabled) {
        if (btnTtsToggle == null) return;

        if (isEnabled) {
            btnTtsToggle.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
            btnTtsToggle.setColorFilter(getColor(R.color.accent));
            if (tvTtsStatus != null) {
                tvTtsStatus.setText("Đang bật");
                tvTtsStatus.setTextColor(getColor(R.color.accent));
            }
        } else {
            btnTtsToggle.setImageResource(android.R.drawable.ic_lock_silent_mode);
            btnTtsToggle.setColorFilter(getColor(R.color.text_hint));
            if (tvTtsStatus != null) {
                tvTtsStatus.setText("Đã tắt");
                tvTtsStatus.setTextColor(getColor(R.color.text_hint));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Load Settings
    // ══════════════════════════════════════════════════════════════════════════

    private void loadSettings() {
        int threshold = prefs.getInt("confidence_threshold", 50);
        boolean sync = prefs.getBoolean("firebase_sync", false);
        boolean isDarkMode = prefs.getBoolean("isDarkMode", true); // Default dark
        boolean ttsEnabled = prefs.getBoolean("tts_enabled", true);

        seekbarThreshold.setProgress(threshold);
        tvThresholdValue.setText(threshold + "%");
        switchFirebase.setChecked(sync);
        switchLanguage.setChecked(prefs.getBoolean("isEnglish", false));
        
        switchDarkMode.setOnCheckedChangeListener(null);
        switchDarkMode.setChecked(isDarkMode);
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("isDarkMode", isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(
                isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
            );
        });

        // Initialize TTS UI state
        updateTtsUI(ttsEnabled);
        if (ttsEnabled) {
            enableTts();
        }

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }
}
