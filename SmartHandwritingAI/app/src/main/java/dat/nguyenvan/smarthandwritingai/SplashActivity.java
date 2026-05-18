package dat.nguyenvan.smarthandwritingai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            SharedPreferences prefs = getSharedPreferences("AI_CONFIG", MODE_PRIVATE);
            boolean isFirstRun = prefs.getBoolean("isFirstRun", true);

            if (isFirstRun) {
                // Lần đầu: Onboarding → Login
                startActivity(new Intent(this, OnboardingActivity.class));
            } else if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                // Đã đăng nhập → thẳng vào app
                startActivity(new Intent(this, MainActivity.class));
            } else {
                // Chưa đăng nhập → màn hình Login (có nút skip)
                startActivity(new Intent(this, LoginActivity.class));
            }
            finish();
        }, 2500);
    }
}
