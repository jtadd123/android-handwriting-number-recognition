package dat.nguyenvan.smarthandwritingai;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.yalantis.ucrop.UCrop;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private ImageView ivPreview, ivDebug;
    private View layoutDebug;
    private TextView tvResult, tvConfidence, tvModelStatus, tvHint, tvAiFeedbackMain;
    private View cardResult, layoutHomeOptions, layoutLoading, scanLine;
    private MaterialButton btnCamera, btnGallery, btnBack;
    private BottomNavigationView bottomNavigation;


    private DigitClassifier digitClassifier;
    private Bitmap currentBitmap;
    private ExecutorService executorService;
    private TextToSpeech tts;

    private Uri photoUri;

    // ── Camera ──────────────────────────────────────────────────────────────
    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && photoUri != null) {
                    launchUCrop(photoUri); // ảnh camera → UCrop
                }
            });

    // ── Gallery ──────────────────────────────────────────────────────────────
    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    launchUCrop(uri); // ảnh gallery → UCrop
                }
            });

    // ── UCrop ─────────────────────────────────────────────────────────────────
    private final ActivityResultLauncher<Intent> cropLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri croppedUri = UCrop.getOutput(result.getData());
                    if (croppedUri != null) {
                        loadBitmapFromUri(croppedUri);
                    }
                } else if (result.getResultCode() == UCrop.RESULT_ERROR && result.getData() != null) {
                    Throwable error = UCrop.getError(result.getData());
                    Toast.makeText(this, getString(R.string.err_crop_image, (error != null ? error.getMessage() : "?")), Toast.LENGTH_SHORT).show();
                }
            });

    // ── Permission ────────────────────────────────────────────────────────────
    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) openCamera();
                else Toast.makeText(this, getString(R.string.err_camera_permission), Toast.LENGTH_SHORT).show();
            });

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        boolean isDarkMode = getSharedPreferences("AI_CONFIG", MODE_PRIVATE).getBoolean("isDarkMode", true);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                isDarkMode ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        );

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        initViews();
        initModel();
        initTTS();
        setupListeners();
        setupBottomNavigation();
    }

    private void initViews() {
        ivPreview      = findViewById(R.id.iv_preview);
        ivDebug        = findViewById(R.id.iv_debug);
        layoutDebug    = findViewById(R.id.layout_debug);
        tvResult       = findViewById(R.id.tv_result);
        tvConfidence   = findViewById(R.id.tv_confidence);
        tvModelStatus  = findViewById(R.id.tv_model_status);
        tvHint         = findViewById(R.id.tv_hint);
        tvAiFeedbackMain = findViewById(R.id.tv_ai_feedback_main);
        cardResult     = findViewById(R.id.card_result);
        btnCamera      = findViewById(R.id.btn_camera);
        btnGallery     = findViewById(R.id.btn_gallery);
        btnBack        = findViewById(R.id.btn_back_main);
        layoutHomeOptions = findViewById(R.id.layout_home_options);
        layoutLoading  = findViewById(R.id.layout_loading);
        scanLine       = findViewById(R.id.scan_line);
        bottomNavigation = findViewById(R.id.bottom_navigation);
        executorService = Executors.newSingleThreadExecutor();

        // Auto-detect math mode is integrated directly.
    }

    private void initModel() {
        tvModelStatus.setText(R.string.model_loading);
        tvModelStatus.setTextColor(getColor(R.color.warning));
        executorService.execute(() -> {
            digitClassifier = new DigitClassifier(this);
            runOnUiThread(() -> {
                if (digitClassifier.isInitialized()) {
                    tvModelStatus.setText(R.string.model_ready);
                    tvModelStatus.setTextColor(getColor(R.color.success));
                } else {
                    tvModelStatus.setText(R.string.model_error);
                    tvModelStatus.setTextColor(getColor(R.color.error));
                }
            });
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

    private void setupListeners() {
        btnCamera.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                openCamera();
            else permissionLauncher.launch(Manifest.permission.CAMERA);
        });
        btnGallery.setOnClickListener(v -> galleryLauncher.launch("image/*"));
        btnBack.setOnClickListener(v -> resetState());

        ImageButton btnSpeakResult = findViewById(R.id.btn_speak_result);
        if (btnSpeakResult != null) {
            btnSpeakResult.setOnClickListener(v -> {
                String text = tvResult.getText().toString();
                if (!text.equals("?") && tts != null) {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "btn_speak");
                }
            });
        }
    }

    private void setupBottomNavigation() {
        bottomNavigation.setSelectedItemId(R.id.nav_home);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                // Already on home — do nothing
                return true;
            } else if (id == R.id.nav_draw) {
                startActivity(new Intent(this, DrawActivity.class));
                return true;
            } else if (id == R.id.nav_history) {
                startActivity(new Intent(this, HistoryActivity.class));
                return true;
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reset rotation and flip to prevent canvas state leaks
        ImageProcessor.rotationDegrees = 0;
        ImageProcessor.isFlipped = false;

        // Reset bottom nav to Home when returning from other activities
        if (bottomNavigation != null) {
            bottomNavigation.setSelectedItemId(R.id.nav_home);
        }

        if (getIntent().hasExtra("draw_result")) {
            String drawResult = getIntent().getStringExtra("draw_result");
            float drawConfidence = getIntent().getFloatExtra("draw_confidence", 0);
            if (drawResult != null) {
                layoutHomeOptions.setVisibility(View.GONE);
                cardResult.setVisibility(View.VISIBLE);
                btnBack.setVisibility(View.VISIBLE);
                tvResult.setText(drawResult);
                tvConfidence.setText(String.format("%.1f%%", drawConfidence));
                tvHint.setVisibility(View.GONE);
            }
        }
    }

    // ── UCrop ─────────────────────────────────────────────────────────────────

    /**
     * Khởi động UCrop với ảnh nguồn. Tỉ lệ cắt tự do (freeStyle),
     * giới hạn output tối đa 1080×1080 để giảm bộ nhớ.
     */
    private void launchUCrop(Uri sourceUri) {
        Uri destUri = Uri.fromFile(new File(getCacheDir(), "ucrop_output_" + System.currentTimeMillis() + ".jpg"));

        UCrop.Options options = new UCrop.Options();
        options.setCompressionQuality(90);
        options.setFreeStyleCropEnabled(true);           // Crop tự do
        options.setShowCropGrid(true);
        options.setShowCropFrame(true);
        options.setToolbarTitle(getString(R.string.crop_toolbar_title));
        options.setToolbarColor(getColor(R.color.primary_dark));
        options.setStatusBarColor(getColor(R.color.primary_dark));
        options.setActiveControlsWidgetColor(getColor(R.color.accent));
        options.setToolbarWidgetColor(getColor(R.color.white));
        // Nút xoay & lật
        options.setHideBottomControls(false);

        Intent cropIntent = UCrop.of(sourceUri, destUri)
                .withMaxResultSize(1080, 1080)
                .withOptions(options)
                .getIntent(this);

        cropLauncher.launch(cropIntent);
    }

    /** Đọc bitmap từ URI đã crop xong và classify */
    private void loadBitmapFromUri(Uri uri) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
                currentBitmap = ImageDecoder.decodeBitmap(source).copy(Bitmap.Config.ARGB_8888, true);
            } else {
                currentBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            }
            if (currentBitmap != null) {
                ivPreview.setImageBitmap(currentBitmap);
                tvHint.setVisibility(View.GONE);
                classifyImage(currentBitmap);
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.err_read_image, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    // ── Camera ────────────────────────────────────────────────────────────────

    private void openCamera() {
        try {
            java.io.File photoFile = new java.io.File(getExternalCacheDir(), "camera_photo.jpg");
            photoUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".provider", photoFile);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            cameraLauncher.launch(intent);
        } catch (Exception e) {
            UIUtils.showErrorSnackbar(findViewById(android.R.id.content), getString(R.string.err_open_camera, e.getMessage()));
        }
    }

    // ── AI Classification ─────────────────────────────────────────────────────

    private void classifyImage(Bitmap bitmap) {
        if (digitClassifier == null || !digitClassifier.isInitialized()) {
            UIUtils.showErrorSnackbar(findViewById(android.R.id.content), getString(R.string.msg_model_not_ready));
            return;
        }

        layoutLoading.setVisibility(View.VISIBLE);
        layoutLoading.setAlpha(1f);
        scanLine.setVisibility(View.VISIBLE);
        scanLine.setTranslationY(0f);
        scanLine.animate().translationY(600f).setDuration(1000)
                .withEndAction(() -> scanLine.setVisibility(View.GONE)).start();

        executorService.execute(() -> {
            try {
                Thread.sleep(500);

                DigitClassifier.PredictionResult result;
                Bitmap debugBitmap = null;

                if (true) {
                    java.util.List<FractionParser.SegmentedSymbol> symbols = ImageProcessor.segmentImage(bitmap);
                    if (symbols.isEmpty()) {
                        result = digitClassifier.predict(bitmap);
                        debugBitmap = ImageProcessor.getLastPreprocessedBitmap();
                    } else {
                        java.util.List<DrawActivity.ExpressionToken> tokens = FractionParser.parseLayout(
                                symbols, digitClassifier, false, true, 64f
                        );

                        boolean hasMathOperator = false;
                        for (DrawActivity.ExpressionToken t : tokens) {
                            if (t.isOperator) {
                                hasMathOperator = true;
                                break;
                            }
                        }

                        java.util.List<DrawActivity.ExpressionToken> finalTokens = tokens;
                        if (hasMathOperator) {
                            finalTokens = DrawActivity.correctExpression(tokens);
                        }

                        StringBuilder exprBuilder = new StringBuilder();
                        float totalConfidence = 0;
                        int digitCount = 0;

                        for (DrawActivity.ExpressionToken t : finalTokens) {
                            if (t.isOperator) {
                                if (t.text.equals("*")) {
                                    exprBuilder.append(" x ");
                                } else if (t.text.equals("/")) {
                                    exprBuilder.append("/");
                                } else {
                                    exprBuilder.append(" ").append(t.text).append(" ");
                                }
                            } else {
                                exprBuilder.append(t.text);
                                totalConfidence += t.confidence;
                                digitCount++;
                            }
                        }

                        float avgConfidence = digitCount > 0 ? (totalConfidence / digitCount) : 100f;
                        String finalRawExpr = exprBuilder.toString().replaceAll("\\s+", " ").trim();

                        String finalLabel = finalRawExpr;
                        if (hasMathOperator) {
                            String cleanExpr = finalRawExpr.replace("x", "*").replace(":", "/").replaceAll("\\s+", "");
                            String exprToSolve = cleanExpr.split("=")[0];
                            try {
                                double solution = MathParser.eval(exprToSolve);
                                String mathResult;
                                if (solution == (long) solution) {
                                    mathResult = String.valueOf((long) solution);
                                } else {
                                    mathResult = String.format(Locale.US, "%.2f", solution);
                                }
                                if (!finalRawExpr.contains("=")) {
                                    finalLabel = finalRawExpr + " = " + mathResult;
                                } else {
                                    finalLabel = finalRawExpr.split("=")[0].trim() + " = " + mathResult;
                                }
                            } catch (Exception ex) {
                                // Silently fallback: do not append " = Err" if evaluation fails
                                finalLabel = finalRawExpr;
                            }
                        }

                        final String solvedLabel = finalLabel;
                        final float solvedConfidence = avgConfidence;

                        DigitClassifier.PredictionItem[] topK = new DigitClassifier.PredictionItem[3];
                        topK[0] = new DigitClassifier.PredictionItem(solvedLabel, solvedConfidence);
                        topK[1] = new DigitClassifier.PredictionItem("", 0f);
                        topK[2] = new DigitClassifier.PredictionItem("", 0f);
                        result = new DigitClassifier.PredictionResult(solvedLabel, solvedConfidence, topK);
                    }
                } else {
                    result = digitClassifier.predict(bitmap);
                    debugBitmap = ImageProcessor.getLastPreprocessedBitmap();
                }

                final DigitClassifier.PredictionResult finalResult = result;
                final Bitmap finalDebugBitmap = debugBitmap;

                runOnUiThread(() -> {
                    layoutLoading.setVisibility(View.GONE);
                    if (finalResult != null) {
                        displayResult(finalResult);
                        if (finalDebugBitmap != null) {
                            layoutDebug.setVisibility(View.VISIBLE);
                            ivDebug.setImageBitmap(finalDebugBitmap);
                        } else {
                            layoutDebug.setVisibility(View.GONE);
                        }
                        savePrediction(bitmap, finalResult.label, finalResult.confidence);
                        hapticFeedback();

                        boolean ttsOn = getSharedPreferences("AI_CONFIG", MODE_PRIVATE)
                                .getBoolean("tts_enabled", true);
                        if (ttsOn && tts != null) {
                            String speakText;
                            if (finalResult.label.contains("=")) {
                                speakText = "Biểu thức có kết quả là " + finalResult.label.substring(finalResult.label.indexOf("=") + 1).trim();
                            } else {
                                speakText = getString(R.string.tts_speak_format, finalResult.label, finalResult.confidence);
                            }
                            tts.speak(speakText, TextToSpeech.QUEUE_FLUSH, null, "main_result");
                        }
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    layoutLoading.setVisibility(View.GONE);
                    UIUtils.showErrorSnackbar(findViewById(android.R.id.content), getString(R.string.err_classification, e.getMessage()));
                });
            }
        });
    }

    private void displayResult(DigitClassifier.PredictionResult result) {
        tvResult.setText(result.label);
        tvConfidence.setText(String.format("%.1f%%", result.confidence));
        cardResult.setVisibility(View.VISIBLE);
        layoutHomeOptions.setVisibility(View.GONE);
        btnBack.setVisibility(View.VISIBLE);

        showAiFeedback(result.confidence);

        LinearLayout layoutTop = findViewById(R.id.layout_top_predictions);
        layoutTop.removeAllViews();

        TextView header = new TextView(this);
        header.setText(getString(R.string.main_top_predictions_title));
        header.setTextColor(getColor(R.color.text_secondary));
        header.setTextSize(14);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setPadding(0, 0, 0, 16);
        layoutTop.addView(header);

        for (DigitClassifier.PredictionItem item : result.topK) {
            if (item.label == null || item.label.isEmpty()) continue;
            View view = getLayoutInflater().inflate(R.layout.item_prediction_bar, layoutTop, false);
            ((TextView) view.findViewById(R.id.tv_label)).setText(item.label);
            ((TextView) view.findViewById(R.id.tv_confidence_value)).setText(String.format("%.1f%%", item.confidence));
            ((ProgressBar) view.findViewById(R.id.progress_confidence)).setProgress((int) item.confidence);
            layoutTop.addView(view);
        }

        if (result.confidence < 50) {
            UIUtils.showWarningSnackbar(findViewById(android.R.id.content),
                    getString(R.string.warning_low_confidence, result.confidence));
        }
    }

    private void showAiFeedback(float confidence) {
        if (tvAiFeedbackMain == null) return;
        tvAiFeedbackMain.setVisibility(View.VISIBLE);
        if (confidence >= 90) {
            tvAiFeedbackMain.setText(getString(R.string.feedback_excellent));
            tvAiFeedbackMain.setTextColor(getColor(R.color.success));
        } else if (confidence >= 70) {
            tvAiFeedbackMain.setText(getString(R.string.feedback_good));
            tvAiFeedbackMain.setTextColor(getColor(R.color.accent));
        } else if (confidence >= 50) {
            tvAiFeedbackMain.setText(getString(R.string.feedback_average));
            tvAiFeedbackMain.setTextColor(getColor(R.color.warning));
        } else {
            tvAiFeedbackMain.setText(getString(R.string.feedback_poor));
            tvAiFeedbackMain.setTextColor(getColor(R.color.error));
        }
    }

    private void hapticFeedback() {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    private void savePrediction(Bitmap bitmap, String result, float confidence) {
        executorService.execute(() -> {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Bitmap resized = Bitmap.createScaledBitmap(bitmap, 56, 56, true);
                resized.compress(Bitmap.CompressFormat.PNG, 100, baos);
                String base64 = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
                AppDatabase.getInstance(this).predictionDao()
                        .insert(new PredictionEntity(base64, result, confidence, System.currentTimeMillis()));
            } catch (Exception e) {
                Log.e(TAG, "Error saving prediction: " + e.getMessage());
            }
        });
    }

    private void resetState() {
        currentBitmap = null;
        ivPreview.setImageBitmap(null);
        tvResult.setText("?");
        tvConfidence.setText("0%");
        tvHint.setVisibility(View.VISIBLE);
        cardResult.setVisibility(View.GONE);
        btnBack.setVisibility(View.GONE);
        layoutHomeOptions.setVisibility(View.VISIBLE);
        layoutDebug.setVisibility(View.GONE);
        if (tvAiFeedbackMain != null) tvAiFeedbackMain.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (digitClassifier != null) digitClassifier.close();
        if (executorService != null) executorService.shutdown();
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }
}