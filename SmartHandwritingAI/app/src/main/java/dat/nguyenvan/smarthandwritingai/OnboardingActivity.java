package dat.nguyenvan.smarthandwritingai;

import android.content.Intent;
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
                    btnNext.setText(getString(R.string.onboarding_start));
                } else {
                    btnNext.setText(getString(R.string.onboarding_btn_next));
                }
            }
        });

        btnNext.setOnClickListener(v -> {
            if (viewPager.getCurrentItem() + 1 < onboardingAdapter.getItemCount()) {
                viewPager.setCurrentItem(viewPager.getCurrentItem() + 1);
            } else {
                // Xem hết onboarding → đánh dấu đã xem, lần sau không hiện nữa
                finishOnboarding(true);
            }
        });

        // BỎ QUA: KHÔNG đánh dấu isFirstRun=false → lần sau vẫn hiện onboarding
        findViewById(R.id.btn_skip).setOnClickListener(v -> finishOnboarding(false));
    }

    private void setupOnboardingItems() {
        List<OnboardingAdapter.OnboardingItem> items = new ArrayList<>();
        items.add(new OnboardingAdapter.OnboardingItem(
                getString(R.string.onboarding_item1_title),
                getString(R.string.onboarding_item1_desc),
                R.raw.ai_splash // AI brain animation
        ));
        items.add(new OnboardingAdapter.OnboardingItem(
                getString(R.string.onboarding_item2_title),
                getString(R.string.onboarding_item2_desc),
                R.raw.onboarding_draw // Drawing pencil animation
        ));
        items.add(new OnboardingAdapter.OnboardingItem(
                getString(R.string.onboarding_item3_title),
                getString(R.string.onboarding_item3_desc),
                R.raw.onboarding_camera // Camera scan animation
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

    private void finishOnboarding(boolean markComplete) {
        // Onboarding always shows → go to MainActivity
        startActivity(new Intent(getApplicationContext(), MainActivity.class));
        finish();
    }
}
