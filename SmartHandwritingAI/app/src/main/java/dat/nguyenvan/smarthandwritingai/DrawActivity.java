package dat.nguyenvan.smarthandwritingai;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DrawActivity extends AppCompatActivity {

    private static final String TAG = "DrawActivity";

    private DrawingView drawingView;
    private TextView tvDrawResult;
    private TextView tvDrawConfidence;
    private CardView cardDrawResult;
    private MaterialButton btnClear;
    private MaterialButton btnPredict;

    private DigitClassifier digitClassifier;
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_draw);

        initViews();
        initModel();
        setupListeners();
    }

    private void initViews() {
        drawingView = findViewById(R.id.drawing_view);
        tvDrawResult = findViewById(R.id.tv_draw_result);
        tvDrawConfidence = findViewById(R.id.tv_draw_confidence);
        cardDrawResult = findViewById(R.id.card_draw_result);
        btnClear = findViewById(R.id.btn_clear);
        btnPredict = findViewById(R.id.btn_predict);

        executorService = Executors.newSingleThreadExecutor();
    }

    private void initModel() {
        executorService.execute(() -> {
            digitClassifier = new DigitClassifier(this);
            runOnUiThread(() -> {
                if (!digitClassifier.isInitialized()) {
                    Toast.makeText(this, R.string.model_error, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void setupListeners() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        btnClear.setOnClickListener(v -> {
            drawingView.clearCanvas();
            cardDrawResult.setVisibility(View.GONE);
        });

        btnPredict.setOnClickListener(v -> {
            if (drawingView.isEmpty()) {
                Toast.makeText(this, R.string.draw_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            predictDrawing();
        });
    }

    private void predictDrawing() {
        if (digitClassifier == null || !digitClassifier.isInitialized()) {
            Toast.makeText(this, R.string.model_error, Toast.LENGTH_SHORT).show();
            return;
        }

        Bitmap bitmap = drawingView.getBitmap();

        executorService.execute(() -> {
            float[] result = digitClassifier.predict(bitmap);
            int predictedDigit = (int) result[0];
            float confidence = result[1];

            savePrediction(bitmap, predictedDigit, confidence);

            runOnUiThread(() -> {
                cardDrawResult.setVisibility(View.VISIBLE);
                tvDrawResult.setText(String.valueOf(predictedDigit));
                tvDrawConfidence.setText(String.format("%.1f%%", confidence));

                if (confidence >= 90) {
                    tvDrawConfidence.setTextColor(getColor(R.color.success));
                } else if (confidence >= 70) {
                    tvDrawConfidence.setTextColor(getColor(R.color.warning));
                } else {
                    tvDrawConfidence.setTextColor(getColor(R.color.error));
                }
            });
        });
    }

    private void savePrediction(Bitmap bitmap, int result, float confidence) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Bitmap resized = Bitmap.createScaledBitmap(bitmap, 56, 56, true);
            resized.compress(Bitmap.CompressFormat.PNG, 100, baos);
            String base64 = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);

            PredictionEntity entity = new PredictionEntity(
                    base64, result, confidence, System.currentTimeMillis());

            AppDatabase.getInstance(this).predictionDao().insert(entity);
            Log.d(TAG, "Draw prediction saved: " + result);
        } catch (Exception e) {
            Log.e(TAG, "Error saving prediction: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (digitClassifier != null) {
            digitClassifier.close();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
