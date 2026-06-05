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
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import android.transition.TransitionManager;
import android.view.ViewGroup;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;

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
    private ImageButton btnSpeakDrawResult;
    private AppDatabase db;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private TextToSpeech tts;

    private com.google.android.material.button.MaterialButtonToggleGroup toggleGroupMode;
    private TextView tvDrawHint;
    private boolean isMathMode = false;
    private boolean isFractionMode = true;
    private android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

    private CardView cardAiCorrecting;
    private CardView cardAiSuggestion;
    private TextView tvAiSuggestionText;
    private Button btnApplySuggestion;
    private TextView tvAiCorrectingText;
    private ObjectAnimator correctingPulseAnim;
    private int correctingDotCount = 0;
    private android.os.Handler dotAnimHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable dotAnimRunnable;
    private String lastCorrectedExpression = "";
    private String lastCorrectedMathResult = "";
    private float lastCorrectedAvgConfidence = 100f;
    private Runnable predictRunnable = () -> {
        if (!drawingView.isEmpty()) {
            predictDrawing(false);
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

        cardAiCorrecting = findViewById(R.id.card_ai_correcting);
        cardAiSuggestion = findViewById(R.id.card_ai_suggestion);
        tvAiSuggestionText = findViewById(R.id.tv_ai_suggestion_text);
        btnApplySuggestion = findViewById(R.id.btn_apply_suggestion);
        tvAiCorrectingText = findViewById(R.id.tv_ai_correcting);

        if (btnApplySuggestion != null) {
            btnApplySuggestion.setOnClickListener(v -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && cardDrawResult != null) {
                    TransitionManager.beginDelayedTransition((ViewGroup) cardDrawResult.getParent());
                }
                tvDrawResult.setText(lastCorrectedExpression);
                tvDrawConfidence.setText(String.format(Locale.US, "%.1f%%", lastCorrectedAvgConfidence));
                if (tvAiFeedback != null) {
                    tvAiFeedback.setText("💡 Đã áp dụng gợi ý công thức đúng của AI.");
                    tvAiFeedback.setTextColor(getColor(R.color.success));
                    tvAiFeedback.setVisibility(View.VISIBLE);
                }
                if (cardAiSuggestion != null) {
                    cardAiSuggestion.setVisibility(View.GONE);
                }
                hapticFeedback();
                boolean ttsEnabled = prefs.getBoolean("tts_enabled", true);
                if (ttsEnabled && tts != null) {
                    String[] parts = lastCorrectedExpression.split("=", -1);
                    String cleanSpeak = (parts.length > 0 ? parts[0] : "").trim().replace("x", "nhân").replace(":", "chia");
                    tts.speak("Đã áp dụng biểu thức " + cleanSpeak, TextToSpeech.QUEUE_FLUSH, null, "apply_suggestion");
                }
            });
        }
        btnRotate = findViewById(R.id.btn_rotate);
        btnFlip = findViewById(R.id.btn_flip);
        btnUndo = findViewById(R.id.btn_undo);
        btnRedo = findViewById(R.id.btn_redo);

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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && cardDrawResult != null) {
                        TransitionManager.beginDelayedTransition((ViewGroup) cardDrawResult.getParent());
                    }
                    cardDrawResult.setVisibility(View.GONE);
                    if (cardAiCorrecting != null) cardAiCorrecting.setVisibility(View.GONE);
                    if (cardAiSuggestion != null) cardAiSuggestion.setVisibility(View.GONE);
                    stopAiCorrectingAnimations();
                    handler.removeCallbacks(predictRunnable);
                }
            });
        }

        prefs = getSharedPreferences("AI_CONFIG", MODE_PRIVATE);
        ImageProcessor.rotationDegrees = prefs.getInt("rotation", 0);
        ImageProcessor.isFlipped = prefs.getBoolean("flip", false);
        updateDirectionUI();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                String langCode = prefs.getString("app_language", "vi");
                java.util.Locale locale;
                switch (langCode) {
                    case "en":
                        locale = java.util.Locale.US;
                        break;
                    case "es":
                        locale = new java.util.Locale("es", "ES");
                        break;
                    case "fr":
                        locale = java.util.Locale.FRANCE;
                        break;
                    case "de":
                        locale = java.util.Locale.GERMANY;
                        break;
                    case "zh":
                        locale = java.util.Locale.SIMPLIFIED_CHINESE;
                        break;
                    case "ja":
                        locale = java.util.Locale.JAPAN;
                        break;
                    case "ko":
                        locale = java.util.Locale.KOREA;
                        break;
                    case "ru":
                        locale = new java.util.Locale("ru", "RU");
                        break;
                    default:
                        locale = new java.util.Locale("vi", "VN");
                        break;
                }
                tts.setLanguage(locale);
            }
        });

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        btnSpeakDrawResult = findViewById(R.id.btn_speak_draw_result);
        if (btnSpeakDrawResult != null) {
            btnSpeakDrawResult.setOnClickListener(v -> {
                boolean ttsEnabled = prefs.getBoolean("tts_enabled", true);
                if (!ttsEnabled) {
                    UIUtils.showWarningSnackbar(findViewById(android.R.id.content), getString(R.string.msg_tts_disabled));
                    return;
                }
                String text = tvDrawResult.getText().toString();
                if (!text.equals("?") && tts != null) {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "btn_speak");
                }
            });
        }

        findViewById(R.id.btn_clear).setOnClickListener(v -> {
            drawingView.clearCanvas();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && cardDrawResult != null) {
                TransitionManager.beginDelayedTransition((ViewGroup) cardDrawResult.getParent());
            }
            cardDrawResult.setVisibility(View.GONE);
            if (cardAiCorrecting != null) cardAiCorrecting.setVisibility(View.GONE);
            if (cardAiSuggestion != null) cardAiSuggestion.setVisibility(View.GONE);
            stopAiCorrectingAnimations();
        });

        btnUndo.setOnClickListener(v -> drawingView.onClickUndo());
        btnRedo.setOnClickListener(v -> drawingView.onClickRedo());

        findViewById(R.id.btn_brush_settings).setOnClickListener(v -> showBrushSettingsDialog());

        drawingView.setOnDrawListener(() -> {
            if (!drawingView.isEmpty()) {
                handler.removeCallbacks(predictRunnable);
                if (isMathMode) {

                    handler.postDelayed(predictRunnable, 1200);
                } else {

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
            predictDrawing(true);
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

        View.OnClickListener colorClickListener = v -> {
            String tag = (String) v.getTag();
            if (tag != null) {
                try {
                    int color = Color.parseColor(tag);
                    drawingView.setBrushColor(color);
                    preview.getBackground().setTint(color);
                } catch (Exception e) {  }
            }
        };

        for (int i = 0; i < ((android.view.ViewGroup) view.findViewById(R.id.seekbar_brush_size).getParent().getParent()).getChildCount(); i++) {

        }

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

            ArrayList<DrawingView.StrokeCluster> clusters = drawingView.getSegmentedClusters();
            if (clusters.isEmpty()) return;

            Bitmap displayBitmap = saveToHistory ? drawingView.getBitmap() : null;

            executorService.execute(() -> {
                try {
                    java.util.List<FractionParser.SegmentedSymbol> symbols = new java.util.ArrayList<>();
                    for (DrawingView.StrokeCluster cluster : clusters) {
                        Bitmap clusterBitmap = drawingView.getBitmapForCluster(cluster);
                        DrawingView.PathData[] paths = cluster.paths.toArray(new DrawingView.PathData[0]);
                        symbols.add(new FractionParser.SegmentedSymbol(
                                cluster.left, cluster.top, cluster.right, cluster.bottom, clusterBitmap, paths
                        ));
                    }

                    java.util.List<ExpressionToken> tokens = FractionParser.parseLayout(
                            symbols, digitClassifier, isMathMode, isFractionMode, drawingView.getBrushSize()
                    );

                    StringBuilder resultBuilder = new StringBuilder();
                    float totalConfidence = 0;
                    int count = 0;

                    for (ExpressionToken token : tokens) {
                        if (token.isOperator) {
                            if (token.text.equals("*")) {
                                resultBuilder.append("x");
                            } else if (token.text.equals("/")) {
                                resultBuilder.append("/");
                            } else {
                                resultBuilder.append(token.text);
                            }
                        } else {
                            resultBuilder.append(token.text);
                            totalConfidence += token.confidence;
                            count++;
                        }
                    }

                    String finalLabel = resultBuilder.toString().trim();
                    if (finalLabel.isEmpty()) return;

                    float finalConfidence = count > 0 ? (totalConfidence / count) : 100f;

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

                        boolean isSyncEnabled = getSharedPreferences("AI_CONFIG", MODE_PRIVATE).getBoolean("firebase_sync", false);
                        if (isSyncEnabled && com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null) {
                            FirebaseSyncHelper.syncPrediction(DrawActivity.this, entity, new FirebaseSyncHelper.OnSyncCompleteListener() {
                                @Override
                                public void onSuccess(String imageUrl) {
                                    Log.d("DrawActivity", "Auto sync successful: " + imageUrl);
                                }

                                @Override
                                public void onFailure(Exception e) {
                                    Log.e("DrawActivity", "Auto sync failed: " + e.getMessage());
                                }
                            });
                        }
                    }

                    runOnUiThread(() -> {
                        tvDrawResult.setText(finalLabel);
                        tvDrawConfidence.setText(String.format(Locale.US, "%.1f%%", finalConfidence));
                        cardDrawResult.setVisibility(View.VISIBLE);
                        showAiFeedback(finalConfidence, true);
                        hapticFeedback();

                        boolean ttsEnabled = prefs.getBoolean("tts_enabled", true);
                        if (saveToHistory && ttsEnabled && tts != null) {
                            String speak = getString(R.string.draw_tts_speak_format, finalLabel, finalConfidence);
                            tts.speak(speak, TextToSpeech.QUEUE_FLUSH, null, "draw_result");
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> UIUtils.showErrorSnackbar(findViewById(android.R.id.content), getString(R.string.err_classification, e.getMessage())));
                }
            });
        } else {

            Bitmap displayBitmap = saveToHistory ? drawingView.getBitmap() : null;

            executorService.execute(() -> {
                try {

                    runOnUiThread(() -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && cardDrawResult != null) {
                            TransitionManager.beginDelayedTransition((ViewGroup) cardDrawResult.getParent());
                        }
                        if (cardAiCorrecting != null) cardAiCorrecting.setVisibility(View.VISIBLE);
                        if (cardDrawResult != null) cardDrawResult.setVisibility(View.GONE);
                        if (cardAiSuggestion != null) cardAiSuggestion.setVisibility(View.GONE);
                        startAiCorrectingAnimations();
                    });

                    long startTime = System.currentTimeMillis();

                    ArrayList<DrawingView.StrokeCluster> clusters = drawingView.getSegmentedClusters();
                    if (clusters.isEmpty()) {
                        runOnUiThread(() -> {
                            stopAiCorrectingAnimations();
                            if (cardAiCorrecting != null) cardAiCorrecting.setVisibility(View.GONE);
                        });
                        return;
                    }

                    java.util.List<FractionParser.SegmentedSymbol> symbols = new java.util.ArrayList<>();
                    for (DrawingView.StrokeCluster cluster : clusters) {
                        Bitmap clusterBitmap = drawingView.getBitmapForCluster(cluster);
                        DrawingView.PathData[] paths = cluster.paths.toArray(new DrawingView.PathData[0]);
                        symbols.add(new FractionParser.SegmentedSymbol(
                                cluster.left, cluster.top, cluster.right, cluster.bottom, clusterBitmap, paths
                        ));
                    }

                    java.util.List<ExpressionToken> rawTokens = FractionParser.parseLayout(
                            symbols, digitClassifier, isMathMode, isFractionMode, drawingView.getBrushSize()
                    );

                    boolean hasEquals = false;
                    for (ExpressionToken token : rawTokens) {
                        if (token.text.equals("=")) {
                            hasEquals = true;
                            break;
                        }
                    }

                    if (rawTokens.isEmpty()) {
                        runOnUiThread(() -> {
                            stopAiCorrectingAnimations();
                            if (cardAiCorrecting != null) cardAiCorrecting.setVisibility(View.GONE);
                        });
                        return;
                    }

                    float UNSURE_THRESHOLD = 80.0f;
                    boolean hasUnsure = false;
                    for (ExpressionToken token : rawTokens) {
                        if (!token.isOperator && token.confidence < UNSURE_THRESHOLD) {
                            token.isUnsure = true;
                            hasUnsure = true;
                        }
                    }

                    java.util.List<ExpressionToken> correctedTokens = correctExpression(rawTokens);
                    boolean wasCorrected = false;
                    if (correctedTokens.size() < rawTokens.size()) {
                        wasCorrected = true;
                    } else {
                        for (ExpressionToken t : correctedTokens) {
                            if (t.isCorrected) {
                                wasCorrected = true;
                                break;
                            }
                        }
                    }

                    SpannableStringBuilder rawSpannable = new SpannableStringBuilder();
                    float rawTotalConfidence = 0;
                    int rawAiCount = 0;

                    for (ExpressionToken token : rawTokens) {
                        String displayVal;
                        if (token.text.equals("*")) {
                            displayVal = "x";
                        } else if (token.text.equals("/")) {
                            displayVal = "/";
                        } else {
                            displayVal = token.text;
                        }

                        if (token.isOperator) {
                            rawSpannable.append(" ");
                            int start = rawSpannable.length();
                            rawSpannable.append(displayVal);
                            int end = rawSpannable.length();
                            rawSpannable.append(" ");

                            if (token.isUnsure) {
                                rawSpannable.setSpan(new ForegroundColorSpan(Color.parseColor("#F59E0B")), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                rawSpannable.setSpan(new BackgroundColorSpan(Color.parseColor("#33F59E0B")), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
                        } else {
                            int start = rawSpannable.length();
                            rawSpannable.append(displayVal);
                            int end = rawSpannable.length();

                            if (token.isUnsure) {
                                rawSpannable.setSpan(new ForegroundColorSpan(Color.parseColor("#F59E0B")), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                rawSpannable.setSpan(new BackgroundColorSpan(Color.parseColor("#33F59E0B")), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }

                            rawTotalConfidence += token.confidence;
                            rawAiCount++;
                        }
                    }

                    float rawAvgConfidence = rawAiCount > 0 ? (rawTotalConfidence / rawAiCount) : 100f;

                    StringBuilder rawExprBuilder = new StringBuilder();
                    for (ExpressionToken token : rawTokens) {
                        rawExprBuilder.append(token.text);
                    }
                    String rawCleanExpr = rawExprBuilder.toString().replaceAll("\\s+", "");
                    String rawMathResult = "";
                    boolean rawSolveSuccess = false;
                    boolean shouldSolveRaw = rawCleanExpr.contains("=") || saveToHistory;

                    if (shouldSolveRaw) {
                        String[] parts = rawCleanExpr.split("=", -1);
                        String exprToSolve = parts.length > 0 ? parts[0] : "";
                        try {
                            double solution = MathParser.eval(exprToSolve);
                            rawSolveSuccess = true;
                            if (solution == (long) solution) {
                                rawMathResult = String.valueOf((long) solution);
                            } else {
                                rawMathResult = String.format(Locale.US, "%.2f", solution);
                            }
                        } catch (Exception ex) {
                            rawMathResult = "Err";
                        }
                    }

                    final boolean finalRawSolveSuccess = rawSolveSuccess;
                    final String finalRawMathResult = rawMathResult;

                    SpannableStringBuilder displayResultSpannable = new SpannableStringBuilder(rawSpannable);
                    if (finalRawSolveSuccess) {
                        String visualExpr = rawSpannable.toString();
                        if (!visualExpr.contains("=")) {
                            displayResultSpannable.append(" = ").append(finalRawMathResult);
                        } else {
                            int equalsIdx = visualExpr.indexOf("=");
                            if (equalsIdx != -1) {
                                displayResultSpannable.clear();
                                displayResultSpannable.append(rawSpannable.subSequence(0, equalsIdx)).append(" = ").append(finalRawMathResult);
                            }
                        }
                    }

                    StringBuilder correctedExprBuilder = new StringBuilder();
                    for (ExpressionToken token : correctedTokens) {
                        correctedExprBuilder.append(token.text);
                    }
                    String correctedExprStr = correctedExprBuilder.toString();
                    String correctedCleanExpr = correctedExprStr.replaceAll("\\s+", "");

                    String correctedMathResult = "";
                    boolean correctedSolveSuccess = false;
                    String[] correctedParts = correctedCleanExpr.split("=", -1);
                    String exprToSolveCorrected = correctedParts.length > 0 ? correctedParts[0] : "";
                    try {
                        double solution = MathParser.eval(exprToSolveCorrected);
                        correctedSolveSuccess = true;
                        if (solution == (long) solution) {
                            correctedMathResult = String.valueOf((long) solution);
                        } else {
                            correctedMathResult = String.format(Locale.US, "%.2f", solution);
                        }
                    } catch (Exception ex) {
                        correctedMathResult = "Err";
                    }

                    float correctedTotalConfidence = 0;
                    int correctedAiCount = 0;
                    for (ExpressionToken token : correctedTokens) {
                        if (!token.isOperator) {
                            correctedTotalConfidence += token.confidence;
                            correctedAiCount++;
                        }
                    }
                    float correctedAvgConfidence = correctedAiCount > 0 ? (correctedTotalConfidence / correctedAiCount) : 100f;

                    String visualCorrected = correctedExprStr.replace("*", " x ").replace("/", "/").replace("+", " + ").replace("-", " - ").replaceAll("\\s+", " ").trim();
                    if (!visualCorrected.contains("=")) {
                        visualCorrected = visualCorrected + " = " + correctedMathResult;
                    } else {
                        String[] visualParts = visualCorrected.split("=", -1);
                        String beforeEquals = (visualParts.length > 0 ? visualParts[0] : "").trim();
                        visualCorrected = beforeEquals + " = " + correctedMathResult;
                    }

                    lastCorrectedExpression = visualCorrected;
                    lastCorrectedMathResult = correctedMathResult;
                    lastCorrectedAvgConfidence = correctedAvgConfidence;

                    if (saveToHistory && displayBitmap != null) {
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        displayBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                        String base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT);

                        String dbSaveExpr = wasCorrected ? visualCorrected : displayResultSpannable.toString();
                        float dbSaveConf = wasCorrected ? correctedAvgConfidence : rawAvgConfidence;

                        PredictionEntity entity = new PredictionEntity(
                                base64Image,
                                dbSaveExpr,
                                dbSaveConf,
                                System.currentTimeMillis()
                        );
                        db.predictionDao().insert(entity);

                        boolean isSyncEnabled = getSharedPreferences("AI_CONFIG", MODE_PRIVATE).getBoolean("firebase_sync", false);
                        if (isSyncEnabled && com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null) {
                            FirebaseSyncHelper.syncPrediction(DrawActivity.this, entity, new FirebaseSyncHelper.OnSyncCompleteListener() {
                                @Override
                                public void onSuccess(String imageUrl) {
                                    Log.d("DrawActivity", "Auto sync successful: " + imageUrl);
                                }

                                @Override
                                public void onFailure(Exception e) {
                                    Log.e("DrawActivity", "Auto sync failed: " + e.getMessage());
                                }
                            });
                        }
                    }

                    long elapsed = System.currentTimeMillis() - startTime;
                    if (elapsed < 900) {
                        try {
                            Thread.sleep(900 - elapsed);
                        } catch (InterruptedException ignored) {}
                    }

                    final boolean finalWasCorrected = wasCorrected;
                    final boolean finalHasUnsure = hasUnsure;
                    final float finalAvgConfidenceToDisplay = rawAvgConfidence;
                    final SpannableStringBuilder finalDisplayResultSpannable = displayResultSpannable;

                    runOnUiThread(() -> {
                        stopAiCorrectingAnimations();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && cardDrawResult != null) {
                            TransitionManager.beginDelayedTransition((ViewGroup) cardDrawResult.getParent());
                        }

                        if (cardAiCorrecting != null) cardAiCorrecting.setVisibility(View.GONE);
                        if (tvDrawResult != null) tvDrawResult.setText(finalDisplayResultSpannable);
                        if (tvDrawConfidence != null) tvDrawConfidence.setText(String.format(Locale.US, "%.1f%%", finalAvgConfidenceToDisplay));
                        if (cardDrawResult != null) cardDrawResult.setVisibility(View.VISIBLE);

                        if (finalWasCorrected && cardAiSuggestion != null) {
                            if (tvAiSuggestionText != null) {
                                tvAiSuggestionText.setText(lastCorrectedExpression);
                            }
                            cardAiSuggestion.setVisibility(View.VISIBLE);
                        }

                        if (tvAiFeedback != null) {
                            tvAiFeedback.setVisibility(View.VISIBLE);
                            if (finalHasUnsure) {
                                tvAiFeedback.setText("✏️ Ký tự màu cam có độ tin cậy thấp. Hãy vẽ rõ ràng hơn hoặc Áp dụng gợi ý.");
                                tvAiFeedback.setTextColor(getColor(R.color.warning));
                            } else {
                                showAiFeedback(finalAvgConfidenceToDisplay, true);
                            }
                        }

                        hapticFeedback();

                        boolean ttsEnabled = prefs.getBoolean("tts_enabled", true);
                        if (saveToHistory && ttsEnabled && tts != null) {
                            String speakText;
                            if (finalRawSolveSuccess) {
                                speakText = "Biểu thức có kết quả là " + finalRawMathResult;
                            } else {
                                speakText = "Biểu thức nhận dạng được là " + finalDisplayResultSpannable.toString().replace("x", "nhân").replace(":", "chia");
                            }
                            tts.speak(speakText, TextToSpeech.QUEUE_FLUSH, null, "draw_result_math");
                        }
                    });

                } catch (Exception e) {
                    Log.e("DrawActivity", "Math solver prediction error", e);
                    runOnUiThread(() -> {
                        stopAiCorrectingAnimations();
                        if (cardAiCorrecting != null) cardAiCorrecting.setVisibility(View.GONE);
                        UIUtils.showErrorSnackbar(findViewById(android.R.id.content), "Lỗi giải toán: " + e.getMessage());
                    });
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

    private void startAiCorrectingAnimations() {
        if (cardAiCorrecting == null) return;
        correctingPulseAnim = ObjectAnimator.ofFloat(cardAiCorrecting, "alpha", 0.6f, 1.0f);
        correctingPulseAnim.setDuration(450);
        correctingPulseAnim.setRepeatMode(ValueAnimator.REVERSE);
        correctingPulseAnim.setRepeatCount(ValueAnimator.INFINITE);
        correctingPulseAnim.start();

        correctingDotCount = 0;
        if (dotAnimRunnable != null) {
            dotAnimHandler.removeCallbacks(dotAnimRunnable);
        }
        dotAnimRunnable = new Runnable() {
            @Override
            public void run() {
                if (cardAiCorrecting != null && cardAiCorrecting.getVisibility() == View.VISIBLE) {
                    StringBuilder text = new StringBuilder("AI correcting");
                    for (int i = 0; i < correctingDotCount; i++) {
                        text.append(".");
                    }
                    if (tvAiCorrectingText != null) {
                        tvAiCorrectingText.setText(text.toString());
                    }
                    correctingDotCount = (correctingDotCount + 1) % 4;
                    dotAnimHandler.postDelayed(this, 300);
                }
            }
        };
        dotAnimHandler.post(dotAnimRunnable);
    }

    private void stopAiCorrectingAnimations() {
        if (correctingPulseAnim != null) {
            correctingPulseAnim.cancel();
        }
        if (cardAiCorrecting != null) {
            cardAiCorrecting.setAlpha(1.0f);
        }
        if (dotAnimRunnable != null) {
            dotAnimHandler.removeCallbacks(dotAnimRunnable);
        }
    }

    public static class ExpressionToken {
        public String text;
        public boolean isOperator;
        public float confidence;
        public boolean isCorrected;
        public boolean isUnsure;

        public ExpressionToken(String text, boolean isOperator, float confidence) {
            this.text = text;
            this.isOperator = isOperator;
            this.confidence = confidence;
            this.isCorrected = false;
            this.isUnsure = false;
        }
    }

    public static java.util.List<ExpressionToken> correctExpression(java.util.List<ExpressionToken> tokens) {
        java.util.List<ExpressionToken> result = new java.util.ArrayList<>();
        int i = 0;
        while (i < tokens.size()) {
            ExpressionToken current = tokens.get(i);
            if (current.isOperator && !current.text.equals("=")) {
                java.util.List<ExpressionToken> opSeq = new java.util.ArrayList<>();
                opSeq.add(current);
                int j = i + 1;
                while (j < tokens.size() && tokens.get(j).isOperator && !tokens.get(j).text.equals("=")) {
                    opSeq.add(tokens.get(j));
                    j++;
                }

                if (opSeq.size() > 1) {
                    ExpressionToken lastToken = opSeq.get(opSeq.size() - 1);
                    if (lastToken.text.equals("-")) {
                        if (opSeq.size() == 2) {
                            result.addAll(opSeq);
                        } else {
                            ExpressionToken correctedPrefix = opSeq.get(opSeq.size() - 2);
                            correctedPrefix.isCorrected = true;
                            result.add(correctedPrefix);
                            result.add(lastToken);
                        }
                    } else {
                        lastToken.isCorrected = true;
                        result.add(lastToken);
                    }
                } else {
                    result.add(current);
                }
                i = j;
            } else {
                result.add(current);
                i++;
            }
        }
        return result;
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateTtsButtonState();
    }

    private void updateTtsButtonState() {
        if (btnSpeakDrawResult != null) {
            boolean ttsEnabled = prefs.getBoolean("tts_enabled", true);
            if (ttsEnabled) {
                btnSpeakDrawResult.setImageResource(R.drawable.ic_volume_up);
                btnSpeakDrawResult.setColorFilter(getColor(R.color.primary));
            } else {
                btnSpeakDrawResult.setImageResource(R.drawable.ic_volume_off);
                btnSpeakDrawResult.setColorFilter(getColor(R.color.text_hint));
            }
        }
    }

    @Override
    protected void onDestroy() {
        stopAiCorrectingAnimations();
        digitClassifier.close();
        executorService.shutdown();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
