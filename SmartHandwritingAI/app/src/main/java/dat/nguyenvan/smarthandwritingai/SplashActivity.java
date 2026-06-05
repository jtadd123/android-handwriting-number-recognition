package dat.nguyenvan.smarthandwritingai;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        android.content.SharedPreferences prefs = getSharedPreferences("AI_CONFIG", MODE_PRIVATE);
        String langCode = prefs.getString("app_language", null);
        if (langCode == null) {
            boolean isEnglish = prefs.getBoolean("isEnglish", false);
            langCode = isEnglish ? "en" : "vi";
            prefs.edit().putString("app_language", langCode).apply();
        }

        androidx.core.os.LocaleListCompat currentLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales();
        java.util.Locale targetLocale = java.util.Locale.forLanguageTag(langCode);
        if (currentLocales.isEmpty() || !targetLocale.getLanguage().equals(currentLocales.get(0).getLanguage())) {
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                androidx.core.os.LocaleListCompat.create(targetLocale)
            );
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {

                startActivity(new Intent(this, OnboardingActivity.class));
            } else {

                startActivity(new Intent(this, LoginActivity.class));
            }
            finish();
        }, 2500);
    }
}
