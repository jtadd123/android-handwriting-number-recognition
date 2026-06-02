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

        boolean isEnglish = getSharedPreferences("AI_CONFIG", MODE_PRIVATE).getBoolean("isEnglish", false);
        androidx.core.os.LocaleListCompat currentLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales();
        if (currentLocales.isEmpty() || !java.util.Locale.forLanguageTag(isEnglish ? "en" : "vi").getLanguage().equals(currentLocales.get(0).getLanguage())) {
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                androidx.core.os.LocaleListCompat.create(java.util.Locale.forLanguageTag(isEnglish ? "en" : "vi"))
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
