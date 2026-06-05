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
    private android.widget.AutoCompleteTextView spinnerLanguage;
    private MaterialSwitch switchDarkMode;
    private ImageButton btnTtsToggle;
    private TextView tvTtsStatus;
    private android.widget.LinearLayout settingsRootLayout;

    private TextToSpeech tts;
    private boolean isTtsReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences("AI_CONFIG", MODE_PRIVATE);
        String langCode = prefs.getString("app_language", null);
        if (langCode == null) {
            boolean isEnglish = prefs.getBoolean("isEnglish", false);
            langCode = isEnglish ? "en" : "vi";
            prefs.edit().putString("app_language", langCode).apply();
        }
        setLocaleTemp(langCode);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out);
            overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_CLOSE, android.R.anim.fade_in, android.R.anim.fade_out);
        } else {
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initViews();
        loadSettings();
    }

    private void initViews() {
        settingsRootLayout = findViewById(R.id.settings_root_layout);
        seekbarThreshold = findViewById(R.id.seekbar_threshold);
        tvThresholdValue = findViewById(R.id.tv_threshold_value);
        switchFirebase = findViewById(R.id.switch_firebase);
        spinnerLanguage = findViewById(R.id.spinner_language);
        switchDarkMode = findViewById(R.id.switch_dark_mode);
        btnTtsToggle = findViewById(R.id.btn_tts_toggle);
        tvTtsStatus = findViewById(R.id.tv_tts_status);

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

        btnTtsToggle.setOnClickListener(v -> {
            boolean currentState = prefs.getBoolean("tts_enabled", true);
            boolean newState = !currentState;
            prefs.edit().putBoolean("tts_enabled", newState).apply();

            if (newState) {
                enableTts(true);
            } else {
                disableTts();
            }
            updateTtsUI(newState);
        });

        findViewById(R.id.btn_clear_data).setOnClickListener(v -> {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                AppDatabase.getInstance(this).predictionDao().deleteAll();
                runOnUiThread(() -> {
                    UIUtils.showSuccessSnackbar(findViewById(android.R.id.content), getString(R.string.msg_history_cleared));
                });
            });
            executor.shutdown();
        });

        tvAccountEmail = findViewById(R.id.tv_account_email);
        findViewById(R.id.btn_logout).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            UIUtils.showSuccessSnackbar(findViewById(android.R.id.content), getString(R.string.msg_logged_out));
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
        findViewById(R.id.btn_go_login).setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));
    }

    private void enableTts(boolean speakConfirmation) {
        if (tts != null) return;

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int langResult = tts.setLanguage(new Locale("vi", "VN"));
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {

                    tts.setLanguage(Locale.getDefault());
                }
                isTtsReady = true;

                if (speakConfirmation) {
                    tts.speak(getString(R.string.msg_tts_enabled_speak), TextToSpeech.QUEUE_FLUSH, null, "tts_test");
                }
                Log.d(TAG, "TTS initialized successfully");
            } else {
                Log.e(TAG, "TTS initialization failed with status: " + status);
                runOnUiThread(() -> UIUtils.showErrorSnackbar(
                        findViewById(android.R.id.content),
                        getString(R.string.msg_tts_init_failed)));
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
        UIUtils.showSuccessSnackbar(findViewById(android.R.id.content), getString(R.string.msg_tts_disabled));
    }

    private void updateTtsUI(boolean isEnabled) {
        if (btnTtsToggle == null) return;

        if (isEnabled) {
            btnTtsToggle.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
            btnTtsToggle.setColorFilter(getColor(R.color.accent));
            if (tvTtsStatus != null) {
                tvTtsStatus.setText(getString(R.string.setting_tts_on));
                tvTtsStatus.setTextColor(getColor(R.color.accent));
            }
        } else {
            btnTtsToggle.setImageResource(android.R.drawable.ic_lock_silent_mode);
            btnTtsToggle.setColorFilter(getColor(R.color.text_hint));
            if (tvTtsStatus != null) {
                tvTtsStatus.setText(getString(R.string.setting_tts_off));
                tvTtsStatus.setTextColor(getColor(R.color.text_hint));
            }
        }
    }

    private void loadSettings() {
        int threshold = prefs.getInt("confidence_threshold", 50);
        boolean sync = prefs.getBoolean("firebase_sync", false);
        boolean isDarkMode = prefs.getBoolean("isDarkMode", true);
        boolean ttsEnabled = prefs.getBoolean("tts_enabled", true);

        seekbarThreshold.setProgress(threshold);
        tvThresholdValue.setText(threshold + "%");

        final String[] languageCodes = {"vi", "en", "es", "fr", "de", "zh", "ja", "ko", "ru"};
        final String[] languageNames = {
            "Vietnamese (Tiếng Việt)",
            "English",
            "Spanish (Español)",
            "French (Français)",
            "German (Deutsch)",
            "Chinese (中文 (简体))",
            "Japanese (日本語)",
            "Korean (한국어)",
            "Russian (Русский)"
        };

        switchFirebase.setOnCheckedChangeListener(null);
        switchDarkMode.setOnCheckedChangeListener(null);

        switchFirebase.setChecked(sync);

        String currentLangCode = prefs.getString("app_language", "vi");
        int currentLangIdx = 0;
        for (int i = 0; i < languageCodes.length; i++) {
            if (languageCodes[i].equals(currentLangCode)) {
                currentLangIdx = i;
                break;
            }
        }
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
            this,
            android.R.layout.simple_dropdown_item_1line,
            languageNames
        );
        spinnerLanguage.setAdapter(adapter);
        spinnerLanguage.setText(languageNames[currentLangIdx], false);

        switchDarkMode.setChecked(isDarkMode);

        switchFirebase.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("firebase_sync", isChecked).apply();
            String msg = isChecked ? getString(R.string.msg_firebase_sync_on) : getString(R.string.msg_firebase_sync_off);
            UIUtils.showSuccessSnackbar(findViewById(android.R.id.content), msg);
        });

        spinnerLanguage.setOnItemClickListener((parent, view, position, id) -> {
            String targetLangCode = languageCodes[position];
            prefs.edit().putString("app_language", targetLangCode).apply();
            prefs.edit().putBoolean("isEnglish", "en".equals(targetLangCode)).apply();
            
            setLocaleTemp(targetLangCode);
            
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("isDarkMode", isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(
                isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
            );
        });

        updateTtsUI(ttsEnabled);
        if (ttsEnabled) {
            enableTts(false);
        }

        if (tvAccountEmail != null) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                tvAccountEmail.setText(getString(R.string.setting_logged_in, user.getEmail()));
            } else {
                tvAccountEmail.setText(getString(R.string.setting_account_offline));
            }
        }
    }

    @Override
    public void recreate() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_CLOSE, android.R.anim.fade_in, android.R.anim.fade_out);
            overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out);
        } else {
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
        super.recreate();
    }

    private void setLocaleTemp(String langCode) {
        Locale locale = new Locale(langCode);
        Locale.setDefault(locale);
        android.content.res.Resources resources = getResources();
        android.content.res.Configuration config = resources.getConfiguration();
        config.setLocale(locale);
        resources.updateConfiguration(config, resources.getDisplayMetrics());

        android.content.res.Resources appResources = getApplicationContext().getResources();
        android.content.res.Configuration appConfig = appResources.getConfiguration();
        appConfig.setLocale(locale);
        appResources.updateConfiguration(appConfig, appResources.getDisplayMetrics());
    }

    @Override
    public void finish() {
        String langCode = prefs.getString("app_language", "vi");
        androidx.core.os.LocaleListCompat locales = androidx.core.os.LocaleListCompat.create(
                java.util.Locale.forLanguageTag(langCode)
        );
        if (!locales.equals(AppCompatDelegate.getApplicationLocales())) {
            AppCompatDelegate.setApplicationLocales(locales);
        }
        super.finish();
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
