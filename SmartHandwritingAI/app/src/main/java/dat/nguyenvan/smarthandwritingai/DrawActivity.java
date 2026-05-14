package dat.nguyenvan.smarthandwritingai;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DrawActivity extends AppCompatActivity {

    private DrawingView drawingView;
    private DigitClassifier digitClassifier;
    private TextView tvDrawResult, tvDrawConfidence, tvCurrentSetting;
    private CardView cardDrawResult;
    private Button btnRotate, btnFlip;
    private View btnUndo, btnRedo;
    private AppDatabase db;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_draw);

        db = AppDatabase.getInstance(this);
        digitClassifier = new DigitClassifier(this);
        
        drawingView = findViewById(R.id.drawing_view);
        tvDrawResult = findViewById(R.id.tv_draw_result);
        tvDrawConfidence = findViewById(R.id.tv_draw_confidence);
        tvCurrentSetting = findViewById(R.id.tv_current_setting);
        cardDrawResult = findViewById(R.id.card_draw_result);
        btnRotate = findViewById(R.id.btn_rotate);
        btnFlip = findViewById(R.id.btn_flip);
        btnUndo = findViewById(R.id.btn_undo);
        btnRedo = findViewById(R.id.btn_redo);

        prefs = getSharedPreferences("AI_CONFIG", MODE_PRIVATE);
        ImageProcessor.rotationDegrees = prefs.getInt("rotation", 0);
        ImageProcessor.isFlipped = prefs.getBoolean("flip", false);
        updateDirectionUI();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        findViewById(R.id.btn_clear).setOnClickListener(v -> {
            drawingView.clearCanvas();
            cardDrawResult.setVisibility(View.GONE);
        });

        btnUndo.setOnClickListener(v -> drawingView.onClickUndo());
        btnRedo.setOnClickListener(v -> drawingView.onClickRedo());

        // Listener cho Realtime Prediction
        drawingView.setOnDrawListener(() -> {
            if (!drawingView.isEmpty()) {
                predictDrawing(false); // Không lưu vào lịch sử nếu chỉ là realtime predict
            } else {
                cardDrawResult.setVisibility(View.GONE);
            }
        });

        findViewById(R.id.btn_predict).setOnClickListener(v -> {
            if (drawingView.isEmpty()) {
                UIUtils.showErrorSnackbar(findViewById(android.R.id.content), "Hãy vẽ gì đó trước!");
                return;
            }
            predictDrawing(true); // Lưu vào DB
            UIUtils.showSuccessSnackbar(findViewById(android.R.id.content), "Đã lưu vào lịch sử");
        });

        btnRotate.setOnClickListener(v -> {
            ImageProcessor.rotationDegrees = (ImageProcessor.rotationDegrees + 90) % 360;
            saveAndReclassify();
        });

        btnFlip.setOnClickListener(v -> {
            ImageProcessor.isFlipped = !ImageProcessor.isFlipped;
            saveAndReclassify();
        });
    }

    private void saveAndReclassify() {
        prefs.edit()
                .putInt("rotation", ImageProcessor.rotationDegrees)
                .putBoolean("flip", ImageProcessor.isFlipped)
                .apply();
        updateDirectionUI();
        if (!drawingView.isEmpty()) {
            predictDrawing(false);
        }
    }

    private void updateDirectionUI() {
        String info = "Hướng: " + ImageProcessor.rotationDegrees + "°" + (ImageProcessor.isFlipped ? " (Lật)" : "");
        if (tvCurrentSetting != null) {
            tvCurrentSetting.setText(info);
        }
    }

    private void predictDrawing(boolean saveToHistory) {
        Bitmap bitmap = drawingView.getBitmap();
        executorService.execute(() -> {
            try {
                DigitClassifier.PredictionResult result = digitClassifier.predict(bitmap);
                
                SharedPreferences prefs = getSharedPreferences("AI_CONFIG", MODE_PRIVATE);
                int threshold = prefs.getInt("confidence_threshold", 50);

                if (result.confidence < threshold) {
                    runOnUiThread(() -> tvDrawResult.setText("?"));
                    return; // Không đủ tự tin thì không hiện kết quả
                }
                
                if (saveToHistory) {
                    // Chuyển bitmap sang Base64 để lưu
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                    String base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT);

                    // Lưu vào database
                    PredictionEntity entity = new PredictionEntity(
                            base64Image, 
                            result.label, 
                            result.confidence, 
                            System.currentTimeMillis()
                    );
                    db.predictionDao().insert(entity);
                }

                runOnUiThread(() -> {
                    tvDrawResult.setText(result.label);
                    tvDrawConfidence.setText(String.format("%.1f%%", result.confidence));
                    cardDrawResult.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                Log.e("DrawActivity", "Error during prediction", e);
                runOnUiThread(() -> UIUtils.showErrorSnackbar(findViewById(android.R.id.content), "Lỗi nhận dạng: " + e.getMessage()));
            }
        });
    }

    @Override
    protected void onDestroy() {
        digitClassifier.close();
        executorService.shutdown();
        super.onDestroy();
    }
}
