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
import java.util.ArrayList;
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

    // View và biến trạng thái cho chế độ Giải Toán AI
    private com.google.android.material.button.MaterialButtonToggleGroup toggleGroupMode;
    private TextView tvDrawHint;
    private boolean isMathMode = false;
    private android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable predictRunnable = () -> {
        if (!drawingView.isEmpty()) {
            predictDrawing(false); // Realtime predict
        }
    };

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

        // Khởi tạo các view chế độ Giải Toán AI
        toggleGroupMode = findViewById(R.id.toggle_group_mode);
        tvDrawHint = findViewById(R.id.tv_draw_hint);

        if (toggleGroupMode != null) {
            toggleGroupMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (isChecked) {
                    isMathMode = (checkedId == R.id.btn_mode_math);
                    if (isMathMode) {
                        if (tvDrawHint != null) tvDrawHint.setText("Vẽ biểu thức số học liên tiếp (Ví dụ: 5 + 3 x 2 =)");
                        ((Button) findViewById(R.id.btn_predict)).setText("Giải Toán");
                    } else {
                        if (tvDrawHint != null) tvDrawHint.setText(R.string.draw_guide_hint);
                        ((Button) findViewById(R.id.btn_predict)).setText(R.string.btn_save_result);
                    }
                    drawingView.clearCanvas();
                    cardDrawResult.setVisibility(View.GONE);
                    handler.removeCallbacks(predictRunnable);
                }
            });
        }

        prefs = getSharedPreferences("AI_CONFIG", MODE_PRIVATE);
        ImageProcessor.rotationDegrees = prefs.getInt("rotation", 0);
        ImageProcessor.isFlipped = prefs.getBoolean("flip", false);
        updateDirectionUI();

        // Init TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                boolean isEnglish = prefs.getBoolean("isEnglish", false);
                tts.setLanguage(isEnglish ? Locale.US : new Locale("vi", "VN"));
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

        // Listener cho Realtime Prediction (Debounced/Delay)
        drawingView.setOnDrawListener(() -> {
            if (!drawingView.isEmpty()) {
                handler.removeCallbacks(predictRunnable);
                if (isMathMode) {
                    // Chờ lâu hơn (1.2 giây) để người dùng vẽ tiếp nét khác
                    handler.postDelayed(predictRunnable, 1200);
                } else {
                    // Nhận dạng nhanh cho ký tự đơn (500ms)
                    handler.postDelayed(predictRunnable, 500);
                }
            } else {
                handler.removeCallbacks(predictRunnable);
                cardDrawResult.setVisibility(View.GONE);
            }
        });

        findViewById(R.id.btn_predict).setOnClickListener(v -> {
            if (drawingView.isEmpty()) {
                UIUtils.showErrorSnackbar(findViewById(android.R.id.content), getString(R.string.draw_empty));
                return;
            }
            handler.removeCallbacks(predictRunnable);
            predictDrawing(true); // Lưu vào DB
            UIUtils.showSuccessSnackbar(findViewById(android.R.id.content), getString(R.string.msg_saved_history));
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
        String flippedSuffix = ImageProcessor.isFlipped ? getString(R.string.draw_flipped_suffix) : "";
        String info = getString(R.string.draw_direction_format, ImageProcessor.rotationDegrees, flippedSuffix);
        if (tvCurrentSetting != null) {
            tvCurrentSetting.setText(info);
        }
    }

    private void predictDrawing(boolean saveToHistory) {
        if (!isMathMode) {
            // ── CHẾ ĐỘ 1: KÝ TỰ ĐƠN (HỖ TRỢ CẢ KÝ TỰ ĐƠN VÀ CHUỖI KÝ TỰ) ────────
            ArrayList<DrawingView.StrokeCluster> clusters = drawingView.getSegmentedClusters();
            if (clusters.isEmpty()) return;

            Bitmap displayBitmap = saveToHistory ? drawingView.getBitmap() : null;

            executorService.execute(() -> {
                try {
                    StringBuilder resultBuilder = new StringBuilder();
                    float totalConfidence = 0;
                    int count = 0;

                    for (DrawingView.StrokeCluster cluster : clusters) {
                        Bitmap clusterBitmap = drawingView.getBitmapForCluster(cluster);
                        DigitClassifier.PredictionResult pred = digitClassifier.predict(clusterBitmap);
                        if (pred != null) {
                            resultBuilder.append(pred.label);
                            totalConfidence += pred.confidence;
                            count++;
                        }
                    }

                    String finalLabel = resultBuilder.toString().trim();
                    if (finalLabel.isEmpty()) return;

                    float finalConfidence = count > 0 ? (totalConfidence / count) : 0f;

                    SharedPreferences configPrefs = getSharedPreferences("AI_CONFIG", MODE_PRIVATE);
                    int threshold = configPrefs.getInt("confidence_threshold", 50);

                    if (finalConfidence < threshold) {
                        runOnUiThread(() -> {
                            tvDrawResult.setText("?");
                            showAiFeedback(finalConfidence, false);
                        });
                        return;
                    }

                    if (saveToHistory && displayBitmap != null) {
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        displayBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                        String base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT);

                        PredictionEntity entity = new PredictionEntity(
                                base64Image,
                                finalLabel,
                                finalConfidence,
                                System.currentTimeMillis()
                        );
                        db.predictionDao().insert(entity);
                    }

                    runOnUiThread(() -> {
                        tvDrawResult.setText(finalLabel);
                        tvDrawConfidence.setText(String.format(Locale.US, "%.1f%%", finalConfidence));
                        cardDrawResult.setVisibility(View.VISIBLE);
                        showAiFeedback(finalConfidence, true);
                        hapticFeedback();

                        if (saveToHistory && tts != null) {
                            String speak = getString(R.string.draw_tts_speak_format, finalLabel, finalConfidence);
                            tts.speak(speak, TextToSpeech.QUEUE_FLUSH, null, "draw_result");
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> UIUtils.showErrorSnackbar(findViewById(android.R.id.content), getString(R.string.err_classification, e.getMessage())));
                }
            });
        } else {
            // ── CHẾ ĐỘ 2: GIẢI TOÁN AI (MULTI-DIGIT & EQUATION SOLVER) ────────
            Bitmap displayBitmap = saveToHistory ? drawingView.getBitmap() : null;

            executorService.execute(() -> {
                try {
                    // Bước 1: Phân tách nét vẽ thành các cụm
                    ArrayList<DrawingView.StrokeCluster> clusters = drawingView.getSegmentedClusters();
                    if (clusters.isEmpty()) return;

                    StringBuilder expressionBuilder = new StringBuilder();
                    float totalConfidence = 0;
                    int aiCount = 0;
                    boolean hasEquals = false;

                    // Bước 2: Duyệt qua từng cụm nét vẽ để nhận diện toán tử hoặc chữ số
                    for (DrawingView.StrokeCluster cluster : clusters) {
                        DrawingView.PathData[] pathArray = cluster.paths.toArray(new DrawingView.PathData[0]);
                        
                        // Kiểm tra xem cụm nét vẽ có phải là toán tử heuristic (+, -, =) không
                        String detectedOp = OperatorDetector.detectOperator(pathArray, drawingView.getBrushSize());

                        if (detectedOp != null) {
                            if (detectedOp.equals("=")) {
                                hasEquals = true;
                            }
                            expressionBuilder.append(" ").append(detectedOp).append(" ");
                        } else {
                            // Nếu không phải toán tử, vẽ cụm này lên bitmap đưa qua AI
                            Bitmap clusterBitmap = drawingView.getBitmapForCluster(cluster);
                            DigitClassifier.PredictionResult pred = digitClassifier.predict(clusterBitmap, true);

                            if (pred != null) {
                                String label = pred.label;
                                // Ánh xạ các nhãn tương đương
                                if (label.equalsIgnoreCase("X")) {
                                    expressionBuilder.append(" * "); // phép nhân
                                } else if (label.equalsIgnoreCase("D")) {
                                    expressionBuilder.append(" / "); // phép chia
                                } else {
                                    expressionBuilder.append(label);
                                }
                                totalConfidence += pred.confidence;
                                aiCount++;
                            }
                        }
                    }

                    String finalExpression = expressionBuilder.toString().trim();
                    if (finalExpression.isEmpty()) return;

                    // Bước 3: Tính toán biểu thức số học
                    String cleanExpr = finalExpression.replaceAll("\\s+", "");
                    String mathResult = "";
                    boolean solveSuccess = false;

                    boolean shouldSolve = cleanExpr.contains("=") || saveToHistory;
                    
                    if (shouldSolve) {
                        String exprToSolve = cleanExpr.split("=")[0];
                        try {
                            double solution = MathParser.eval(exprToSolve);
                            solveSuccess = true;
                            if (solution == (long) solution) {
                                mathResult = String.valueOf((long) solution);
                            } else {
                                mathResult = String.format(Locale.US, "%.2f", solution);
                            }
                        } catch (Exception ex) {
                            mathResult = "Err";
                        }
                    }

                    final boolean finalSolveSuccess = solveSuccess;
                    final String finalMathResult = mathResult;

                    final String displayResultText;
                    if (finalSolveSuccess) {
                        // Định dạng hiển thị đẹp: "5 + 3 x 2 = 11"
                        String visualExpr = finalExpression.replace("*", "x").replace("/", ":");
                        if (!visualExpr.contains("=")) {
                            displayResultText = visualExpr + " = " + finalMathResult;
                        } else {
                            String beforeEquals = finalExpression.split("=")[0].trim().replace("*", "x").replace("/", ":");
                            displayResultText = beforeEquals + " = " + finalMathResult;
                        }
                    } else {
                        displayResultText = finalExpression.replace("*", "x").replace("/", ":");
                    }

                    float finalAvgConfidence = aiCount > 0 ? (totalConfidence / aiCount) : 100f;

                    // Lưu vào DB lịch sử nếu bấm nút Giải Toán (saveToHistory = true)
                    if (saveToHistory && displayBitmap != null) {
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        displayBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                        String base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT);

                        PredictionEntity entity = new PredictionEntity(
                                base64Image,
                                displayResultText, // Lưu toàn bộ phép toán và đáp án
                                finalAvgConfidence,
                                System.currentTimeMillis()
                        );
                        db.predictionDao().insert(entity);
                    }

                    runOnUiThread(() -> {
                        tvDrawResult.setText(displayResultText);
                        tvDrawConfidence.setText(String.format(Locale.US, "%.1f%%", finalAvgConfidence));
                        cardDrawResult.setVisibility(View.VISIBLE);
                        
                        showAiFeedback(finalAvgConfidence, true);
                        hapticFeedback();

                        // Đọc to kết quả qua TTS
                        if (saveToHistory && tts != null) {
                            String speakText;
                            if (finalSolveSuccess) {
                                speakText = "Biểu thức có kết quả là " + finalMathResult;
                            } else {
                                speakText = "Biểu thức nhận dạng được là " + displayResultText;
                            }
                            tts.speak(speakText, TextToSpeech.QUEUE_FLUSH, null, "draw_result_math");
                        }
                    });

                } catch (Exception e) {
                    Log.e("DrawActivity", "Math solver prediction error", e);
                    runOnUiThread(() -> UIUtils.showErrorSnackbar(findViewById(android.R.id.content), "Lỗi giải toán: " + e.getMessage()));
                }
            });
        }
    }

    private void showAiFeedback(float confidence, boolean recognized) {
        if (tvAiFeedback == null) return;
        tvAiFeedback.setVisibility(View.VISIBLE);

        if (!recognized) {
            tvAiFeedback.setText(getString(R.string.draw_feedback_hard_to_read));
            tvAiFeedback.setTextColor(getColor(R.color.warning));
        } else if (confidence >= 90) {
            tvAiFeedback.setText(getString(R.string.draw_feedback_excellent));
            tvAiFeedback.setTextColor(getColor(R.color.success));
        } else if (confidence >= 70) {
            tvAiFeedback.setText(getString(R.string.draw_feedback_good));
            tvAiFeedback.setTextColor(getColor(R.color.accent));
        } else if (confidence >= 50) {
            tvAiFeedback.setText(getString(R.string.draw_feedback_average));
            tvAiFeedback.setTextColor(getColor(R.color.warning));
        } else {
            tvAiFeedback.setText(getString(R.string.draw_feedback_poor));
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
