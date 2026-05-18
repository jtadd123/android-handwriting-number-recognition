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
                tts.setLanguage(new Locale("vi", "VN"));
            }
        });
    }

    private void generateNewTarget() {
        currentTarget = ALL_CHARS[random.nextInt(ALL_CHARS.length)];
        tvTargetChar.setText(currentTarget);
        // Đọc ký tự mục tiêu
        speakText("Hãy viết ký tự " + currentTarget);
    }

    private void submitAnswer() {
        if (drawingView.isEmpty()) {
            UIUtils.showErrorSnackbar(findViewById(android.R.id.content), "Hãy vẽ gì đó trước!");
            return;
        }

        Bitmap bitmap = drawingView.getBitmap();
        executorService.execute(() -> {
            DigitClassifier.PredictionResult result = digitClassifier.predict(bitmap);
            if (result == null) return;

            totalAttempts++;
            boolean isCorrect = result.label.equalsIgnoreCase(currentTarget);
            if (isCorrect) correctAttempts++;

            // Tìm vị trí ký tự mục tiêu trong top predictions
            float targetConfidence = 0;
            for (DigitClassifier.PredictionItem item : result.topK) {
                if (item.label.equalsIgnoreCase(currentTarget)) {
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

    private void showFeedback(boolean correct, String predicted, float confidence, float targetConf) {
        cardFeedback.setVisibility(View.VISIBLE);

        if (correct && confidence >= 80) {
            tvFeedbackEmoji.setText("🌟");
            tvFeedbackText.setText("Xuất sắc!");
            tvFeedbackDetail.setText(String.format("AI nhận đúng \"%s\" với %.1f%% tự tin", currentTarget, confidence));
            tvFeedbackText.setTextColor(getColor(R.color.success));
            speakText("Xuất sắc! Bạn viết rất đẹp");
        } else if (correct) {
            tvFeedbackEmoji.setText("✅");
            tvFeedbackText.setText("Đúng rồi!");
            tvFeedbackDetail.setText(String.format("AI nhận đúng \"%s\" (%.1f%%). Hãy viết rõ hơn.", currentTarget, confidence));
            tvFeedbackText.setTextColor(getColor(R.color.success));
            speakText("Đúng rồi, nhưng hãy viết rõ hơn");
        } else {
            tvFeedbackEmoji.setText("❌");
            tvFeedbackText.setText("Chưa đúng!");
            String detail = String.format("AI nhận thành \"%s\" (%.1f%%).", predicted, confidence);
            if (targetConf > 0) {
                detail += String.format(" Ký tự \"%s\" chỉ %.1f%%.", currentTarget, targetConf);
            }
            detail += " Hãy viết lại rõ hơn!";
            tvFeedbackDetail.setText(detail);
            tvFeedbackText.setTextColor(getColor(R.color.error));
            speakText("Chưa đúng. AI nhận thành " + predicted + ". Hãy thử lại.");
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
