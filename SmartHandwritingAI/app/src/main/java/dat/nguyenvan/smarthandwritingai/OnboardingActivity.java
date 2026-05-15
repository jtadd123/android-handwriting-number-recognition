package dat.nguyenvan.smarthandwritingai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {

    private OnboardingAdapter onboardingAdapter;
    private LinearLayout layoutIndicators;
    private MaterialButton btnNext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        layoutIndicators = findViewById(R.id.layout_indicators);
        btnNext = findViewById(R.id.btn_next);

        setupOnboardingItems();

        ViewPager2 viewPager = findViewById(R.id.viewPager);
        viewPager.setAdapter(onboardingAdapter);

        setupIndicators();
        setCurrentIndicator(0);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                setCurrentIndicator(position);
                if (position == onboardingAdapter.getItemCount() - 1) {
                    btnNext.setText("BẮT ĐẦU");
                } else {
                    btnNext.setText("TIẾP TỤC");
                }
            }
        });

        btnNext.setOnClickListener(v -> {
            if (viewPager.getCurrentItem() + 1 < onboardingAdapter.getItemCount()) {
                viewPager.setCurrentItem(viewPager.getCurrentItem() + 1);
            } else {
                startMainActivity();
            }
        });

        findViewById(R.id.btn_skip).setOnClickListener(v -> startMainActivity());
    }

    private void setupOnboardingItems() {
        List<OnboardingAdapter.OnboardingItem> items = new ArrayList<>();
        items.add(new OnboardingAdapter.OnboardingItem(
                "AI Recognition",
                "Nhận diện chữ viết tay chính xác bằng mô hình TensorFlow Lite hiện đại.",
                R.raw.ai_splash // Reusing existing animation
        ));
        items.add(new OnboardingAdapter.OnboardingItem(
                "Draw Canvas",
                "Hỗ trợ vẽ tay trực tiếp trên màn hình cực kỳ mượt mà.",
                R.raw.ai_splash
        ));
        items.add(new OnboardingAdapter.OnboardingItem(
                "Smart Camera & Firebase",
                "Quét ảnh thông minh và đồng bộ dữ liệu đám mây qua Firebase.",
                R.raw.ai_splash
        ));
        onboardingAdapter = new OnboardingAdapter(items);
    }

    private void setupIndicators() {
        ImageView[] indicators = new ImageView[onboardingAdapter.getItemCount()];
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        layoutParams.setMargins(8, 0, 8, 0);
        for (int i = 0; i < indicators.length; i++) {
            indicators[i] = new ImageView(getApplicationContext());
            indicators[i].setImageDrawable(ContextCompat.getDrawable(
                    getApplicationContext(), R.drawable.indicator_inactive
            ));
            indicators[i].setLayoutParams(layoutParams);
            layoutIndicators.addView(indicators[i]);
        }
    }

    private void setCurrentIndicator(int index) {
        int childCount = layoutIndicators.getChildCount();
        for (int i = 0; i < childCount; i++) {
            ImageView imageView = (ImageView) layoutIndicators.getChildAt(i);
            if (i == index) {
                imageView.setImageDrawable(ContextCompat.getDrawable(
                        getApplicationContext(), R.drawable.indicator_active
                ));
            } else {
                imageView.setImageDrawable(ContextCompat.getDrawable(
                        getApplicationContext(), R.drawable.indicator_inactive
                ));
            }
        }
    }

    private void startMainActivity() {
        SharedPreferences prefs = getSharedPreferences("AI_CONFIG", MODE_PRIVATE);
        prefs.edit().putBoolean("isFirstRun", false).apply();
        startActivity(new Intent(getApplicationContext(), MainActivity.class));
        finish();
    }
}
