package dat.nguyenvan.smarthandwritingai;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DrawActivity extends AppCompatActivity {

    private DrawingView drawingView;
    private DigitClassifier digitClassifier;
    private TextView tvDrawResult, tvDrawConfidence, tvCurrentSetting, tvAiFeedback;
    private CardView cardDrawResult;
    private Button btnRotate, btnFlip;
    private View btnUndo, btnRedo;
    private AppDatabase db;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private TextToSpeech tts;

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
        tvAiFeedback = findViewById(R.id.tv_ai_feedback);
        cardDrawResult = findViewById(R.id.card_draw_result);
        btnRotate = findViewById(R.id.btn_rotate);
        btnFlip = findViewById(R.id.btn_flip);
        btnUndo = findViewById(R.id.btn_undo);
        btnRedo = findViewById(R.id.btn_redo);

        prefs = getSharedPreferences("AI_CONFIG", MODE_PRIVATE);
        ImageProcessor.rotationDegrees = prefs.getInt("rotation", 0);
        ImageProcessor.isFlipped = prefs.getBoolean("flip", false);
        updateDirectionUI();

        // Init TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("vi", "VN"));
            }
        });

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        View btnSpeakResult = findViewById(R.id.btn_speak_draw_result);
        if (btnSpeakResult != null) {
            btnSpeakResult.setOnClickListener(v -> {
                String text = tvDrawResult.getText().toString();
                if (!text.equals("?") && tts != null) {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "btn_speak");
                }
            });
        }

        findViewById(R.id.btn_clear).setOnClickListener(v -> {
            drawingView.clearCanvas();
            cardDrawResult.setVisibility(View.GONE);
        });

        btnUndo.setOnClickListener(v -> drawingView.onClickUndo());
        btnRedo.setOnClickListener(v -> drawingView.onClickRedo());

        // Brush settings button
        findViewById(R.id.btn_brush_settings).setOnClickListener(v -> showBrushSettingsDialog());

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

    private void showBrushSettingsDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, com.google.android.material.R.style.Theme_Material3_DayNight_BottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.dialog_brush_settings, null);
        dialog.setContentView(view);

        SeekBar seekBar = view.findViewById(R.id.seekbar_brush_size);
        TextView tvSize = view.findViewById(R.id.tv_brush_size);
        View preview = view.findViewById(R.id.brush_preview);

        seekBar.setProgress((int) drawingView.getBrushSize());
        tvSize.setText(String.valueOf((int) drawingView.getBrushSize()));

        // Update preview size
        updateBrushPreview(preview, (int) drawingView.getBrushSize());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int size = Math.max(8, progress);
                tvSize.setText(String.valueOf(size));
                updateBrushPreview(preview, size);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        // Handle color clicks
        View.OnClickListener colorClickListener = v -> {
            String tag = (String) v.getTag();
            if (tag != null) {
                try {
                    int color = Color.parseColor(tag);
                    drawingView.setBrushColor(color);
                    preview.getBackground().setTint(color);
                } catch (Exception e) { /* ignore */ }
            }
        };
        // Set click listeners for all color buttons
        for (int i = 0; i < ((android.view.ViewGroup) view.findViewById(R.id.seekbar_brush_size).getParent().getParent()).getChildCount(); i++) {
            // We'll use a different approach - find all ImageButtons with tags
        }
        // Find color buttons by iterating
        android.view.ViewGroup root = (android.view.ViewGroup) view;
        setColorListeners(root, colorClickListener);

        view.findViewById(R.id.btn_brush_cancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btn_brush_apply).setOnClickListener(v -> {
            int size = Math.max(8, seekBar.getProgress());
            drawingView.setBrushSize(size);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void setColorListeners(android.view.ViewGroup parent, View.OnClickListener listener) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof android.widget.ImageButton && child.getTag() != null) {
                child.setOnClickListener(listener);
            } else if (child instanceof android.view.ViewGroup) {
                setColorListeners((android.view.ViewGroup) child, listener);
            }
        }
    }

    private void updateBrushPreview(View preview, int size) {
        int previewSize = Math.max(16, Math.min(size, 96));
        android.view.ViewGroup.LayoutParams params = preview.getLayoutParams();
        params.width = previewSize * 2;
        params.height = previewSize * 2;
        preview.setLayoutParams(params);
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
        // Use getBitmapForModel() for AI: always black bg + white strokes
        Bitmap modelBitmap = drawingView.getBitmapForModel();
        // Use getBitmap() for history: preserves user's actual drawing
        Bitmap displayBitmap = saveToHistory ? drawingView.getBitmap() : null;
        
        Log.d("DrawActivity", "[DEBUG] Model bitmap: " + modelBitmap.getWidth() + "x" + modelBitmap.getHeight());
        
        executorService.execute(() -> {
            try {
                DigitClassifier.PredictionResult result = digitClassifier.predict(modelBitmap);
                
                if (result == null) {
                    Log.e("DrawActivity", "[DEBUG] Prediction returned null - model not initialized?");
                    runOnUiThread(() -> UIUtils.showErrorSnackbar(
                        findViewById(android.R.id.content), "Model chưa sẵn sàng"));
                    return;
                }
                
                Log.d("DrawActivity", "[DEBUG] Prediction: label=" + result.label 
                    + " confidence=" + String.format("%.1f%%", result.confidence));
                
                SharedPreferences prefs = getSharedPreferences("AI_CONFIG", MODE_PRIVATE);
                int threshold = prefs.getInt("confidence_threshold", 50);

                if (result.confidence < threshold) {
                    runOnUiThread(() -> {
                        tvDrawResult.setText("?");
                        showAiFeedback(result.confidence, false);
                    });
                    return; // Không đủ tự tin thì không hiện kết quả
                }
                
                if (saveToHistory && displayBitmap != null) {
                    // Chuyển bitmap sang Base64 để lưu (display bitmap, not model bitmap)
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    displayBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
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
                    showAiFeedback(result.confidence, true);
                    hapticFeedback();

                    // TTS đọc kết quả
                    if (saveToHistory && tts != null) {
                        String speak = "Kết quả là " + result.label + ", độ tự tin " + String.format("%.0f", result.confidence) + " phần trăm";
                        tts.speak(speak, TextToSpeech.QUEUE_FLUSH, null, "draw_result");
                    }
                });
            } catch (Exception e) {
                Log.e("DrawActivity", "Error during prediction", e);
                runOnUiThread(() -> UIUtils.showErrorSnackbar(findViewById(android.R.id.content), "Lỗi nhận dạng: " + e.getMessage()));
            }
        });
    }

    private void showAiFeedback(float confidence, boolean recognized) {
        if (tvAiFeedback == null) return;
        tvAiFeedback.setVisibility(View.VISIBLE);

        if (!recognized) {
            tvAiFeedback.setText("🤔 Chữ khó đọc. Hãy viết rõ hơn!");
            tvAiFeedback.setTextColor(getColor(R.color.warning));
        } else if (confidence >= 90) {
            tvAiFeedback.setText("🌟 Chữ viết rất rõ ràng và đẹp!");
            tvAiFeedback.setTextColor(getColor(R.color.success));
        } else if (confidence >= 70) {
            tvAiFeedback.setText("👍 Chữ viết tốt, có thể viết đậm hơn.");
            tvAiFeedback.setTextColor(getColor(R.color.accent));
        } else if (confidence >= 50) {
            tvAiFeedback.setText("✏️ Hãy viết nét đậm và rõ ràng hơn.");
            tvAiFeedback.setTextColor(getColor(R.color.warning));
        } else {
            tvAiFeedback.setText("🔄 Chữ khó phân biệt. Hãy viết lại.");
            tvAiFeedback.setTextColor(getColor(R.color.error));
        }
    }

    private void hapticFeedback() {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    @Override
    protected void onDestroy() {
        digitClassifier.close();
        executorService.shutdown();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
