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
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                // Already logged in → skip login, go straight to onboarding animations
                startActivity(new Intent(this, OnboardingActivity.class));
            } else {
                // Not logged in → show login screen
                startActivity(new Intent(this, LoginActivity.class));
            }
            finish();
        }, 2500);
    }
}
