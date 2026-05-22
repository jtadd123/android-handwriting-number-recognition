package dat.nguyenvan.smarthandwritingai;

import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PracticeModeActivity extends AppCompatActivity {

    private static final String[] ALL_CHARS = {
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J",
            "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T",
            "U", "V", "W", "X", "Y", "Z"
    };

    private DrawingView drawingView;
    private TextView tvTargetChar, tvScore, tvFeedbackEmoji, tvFeedbackText, tvFeedbackDetail;
    private CardView cardFeedback;
    private DigitClassifier digitClassifier;
    private ExecutorService executorService;
    private TextToSpeech tts;
    private Random random = new Random();

    private String currentTarget = "";
    private int totalAttempts = 0;
    private int correctAttempts = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_practice);

        // Reset rotation and flip to prevent canvas state leaks
        ImageProcessor.rotationDegrees = 0;
        ImageProcessor.isFlipped = false;

        initViews();
        initTTS();
        digitClassifier = new DigitClassifier(this);
        executorService = Executors.newSingleThreadExecutor();
        generateNewTarget();
    }

    private void initViews() {
        drawingView = findViewById(R.id.practice_drawing_view);
        tvTargetChar = findViewById(R.id.tv_target_char);
        tvScore = findViewById(R.id.tv_score);
        tvFeedbackEmoji = findViewById(R.id.tv_feedback_emoji);
        tvFeedbackText = findViewById(R.id.tv_feedback_text);
        tvFeedbackDetail = findViewById(R.id.tv_feedback_detail);
        cardFeedback = findViewById(R.id.card_feedback);

        findViewById(R.id.btn_back_practice).setOnClickListener(v -> finish());
        findViewById(R.id.btn_practice_clear).setOnClickListener(v -> {
            drawingView.clearCanvas();
            cardFeedback.setVisibility(View.GONE);
        });
        findViewById(R.id.btn_practice_submit).setOnClickListener(v -> submitAnswer());
        findViewById(R.id.btn_practice_next).setOnClickListener(v -> {
            drawingView.clearCanvas();
            cardFeedback.setVisibility(View.GONE);
            generateNewTarget();
        });
    }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                boolean isEnglish = getSharedPreferences("AI_CONFIG", MODE_PRIVATE).getBoolean("isEnglish", false);
                tts.setLanguage(isEnglish ? Locale.US : new Locale("vi", "VN"));
            }
        });
    }

    private void generateNewTarget() {
        currentTarget = ALL_CHARS[random.nextInt(ALL_CHARS.length)];
        tvTargetChar.setText(currentTarget);
        // Đọc ký tự mục tiêu
        speakText(getString(R.string.practice_speak_prompt, currentTarget));
    }

    private void submitAnswer() {
        if (drawingView.isEmpty()) {
            UIUtils.showErrorSnackbar(findViewById(android.R.id.content), getString(R.string.draw_empty));
            return;
        }

        Bitmap bitmap = drawingView.getBitmapForModel();
        executorService.execute(() -> {
            DigitClassifier.PredictionResult result = digitClassifier.predict(bitmap);
            if (result == null) return;

            totalAttempts++;
            boolean isCorrect = isVisualEquivalent(result.label, currentTarget);
            if (isCorrect) correctAttempts++;

            // Tìm vị trí ký tự mục tiêu trong top predictions
            float targetConfidence = 0;
            for (DigitClassifier.PredictionItem item : result.topK) {
                if (isVisualEquivalent(item.label, currentTarget)) {
                    targetConfidence = item.confidence;
                    break;
                }
            }

            final boolean correct = isCorrect;
            final float conf = result.confidence;
            final float targetConf = targetConfidence;
            final String predicted = result.label;

            runOnUiThread(() -> {
                tvScore.setText(correctAttempts + "/" + totalAttempts);
                showFeedback(correct, predicted, conf, targetConf);
                hapticFeedback(correct);
            });
        });
    }

    private boolean isVisualEquivalent(String val1, String val2) {
        if (val1.equalsIgnoreCase(val2)) return true;
        String[][] groups = {
                {"0", "O"},
                {"1", "I", "L"},
                {"2", "Z"},
                {"5", "S"},
                {"8", "B"}
        };
        for (String[] group : groups) {
            boolean hasVal1 = false;
            boolean hasVal2 = false;
            for (String s : group) {
                if (s.equalsIgnoreCase(val1)) hasVal1 = true;
                if (s.equalsIgnoreCase(val2)) hasVal2 = true;
            }
            if (hasVal1 && hasVal2) return true;
        }
        return false;
    }

    private void showFeedback(boolean correct, String predicted, float confidence, float targetConf) {
        cardFeedback.setVisibility(View.VISIBLE);

        if (correct && confidence >= 80) {
            tvFeedbackEmoji.setText("🌟");
            tvFeedbackText.setText(getString(R.string.practice_feedback_excellent));
            tvFeedbackDetail.setText(getString(R.string.practice_feedback_excellent_detail, currentTarget, confidence));
            tvFeedbackText.setTextColor(getColor(R.color.success));
            speakText(getString(R.string.practice_speak_excellent));
        } else if (correct) {
            tvFeedbackEmoji.setText("✅");
            tvFeedbackText.setText(getString(R.string.practice_feedback_correct));
            tvFeedbackDetail.setText(getString(R.string.practice_feedback_correct_detail, currentTarget, confidence));
            tvFeedbackText.setTextColor(getColor(R.color.success));
            speakText(getString(R.string.practice_speak_correct_clearer));
        } else {
            tvFeedbackEmoji.setText("❌");
            tvFeedbackText.setText(getString(R.string.practice_feedback_incorrect));
            StringBuilder detail = new StringBuilder(getString(R.string.practice_feedback_incorrect_pred_format, predicted, confidence));
            if (targetConf > 0) {
                detail.append(getString(R.string.practice_feedback_incorrect_target_format, currentTarget, targetConf));
            }
            detail.append(getString(R.string.practice_feedback_incorrect_rewrite));
            tvFeedbackDetail.setText(detail.toString());
            tvFeedbackText.setTextColor(getColor(R.color.error));
            speakText(getString(R.string.practice_speak_incorrect_format, predicted));
        }
    }

    private void speakText(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "practice_feedback");
        }
    }

    private void hapticFeedback(boolean success) {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (success) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 50, 50, 50}, -1));
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (digitClassifier != null) digitClassifier.close();
        if (executorService != null) executorService.shutdown();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
