package dat.nguyenvan.smarthandwritingai;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Chuyển sang MainActivity sau 2.5 giây
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            android.content.SharedPreferences prefs = getSharedPreferences("AI_CONFIG", MODE_PRIVATE);
            boolean isFirstRun = prefs.getBoolean("isFirstRun", true);
            if (isFirstRun) {
                startActivity(new Intent(SplashActivity.this, OnboardingActivity.class));
            } else {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
            }
            finish();
        }, 2500);
    }
}
