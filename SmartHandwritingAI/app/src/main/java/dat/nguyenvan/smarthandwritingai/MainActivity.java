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
    private MaterialButton btnCamera, btnGallery, btnDraw, btnHistory, btnAnalytics, btnBack;

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
                    Toast.makeText(this, "Lỗi crop ảnh: " + (error != null ? error.getMessage() : "?"), Toast.LENGTH_SHORT).show();
                }
            });

    // ── Permission ────────────────────────────────────────────────────────────
    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) openCamera();
                else Toast.makeText(this, "Cần quyền Camera để chụp ảnh", Toast.LENGTH_SHORT).show();
            });

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        initModel();
        initTTS();
        setupListeners();
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
        btnDraw        = findViewById(R.id.btn_draw);
        btnHistory     = findViewById(R.id.btn_history);
        btnAnalytics   = findViewById(R.id.btn_analytics);
        btnBack        = findViewById(R.id.btn_back_main);
        layoutHomeOptions = findViewById(R.id.layout_home_options);
        layoutLoading  = findViewById(R.id.layout_loading);
        scanLine       = findViewById(R.id.scan_line);
        executorService = Executors.newSingleThreadExecutor();
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
            if (status == TextToSpeech.SUCCESS) tts.setLanguage(new Locale("vi", "VN"));
        });
    }

    private void setupListeners() {
        btnCamera.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                openCamera();
            else permissionLauncher.launch(Manifest.permission.CAMERA);
        });
        btnGallery.setOnClickListener(v -> galleryLauncher.launch("image/*"));
        btnDraw.setOnClickListener(v -> startActivity(new Intent(this, DrawActivity.class)));
        btnHistory.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        btnAnalytics.setOnClickListener(v -> startActivity(new Intent(this, AnalyticsActivity.class)));
        findViewById(R.id.btn_settings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btn_practice).setOnClickListener(v -> startActivity(new Intent(this, PracticeModeActivity.class)));
        btnBack.setOnClickListener(v -> resetState());
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
        options.setToolbarTitle("Cắt & Xoay Ảnh");
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
            Toast.makeText(this, "Lỗi đọc ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
            UIUtils.showErrorSnackbar(findViewById(android.R.id.content), "Không thể mở Camera: " + e.getMessage());
        }
    }

    // ── AI Classification ─────────────────────────────────────────────────────

    private void classifyImage(Bitmap bitmap) {
        if (digitClassifier == null || !digitClassifier.isInitialized()) {
            UIUtils.showErrorSnackbar(findViewById(android.R.id.content), "Model chưa sẵn sàng");
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
                DigitClassifier.PredictionResult result = digitClassifier.predict(bitmap);
                Bitmap debugBitmap = ImageProcessor.getLastPreprocessedBitmap();

                runOnUiThread(() -> {
                    layoutLoading.setVisibility(View.GONE);
                    if (result != null) {
                        displayResult(result);
                        if (debugBitmap != null) {
                            layoutDebug.setVisibility(View.VISIBLE);
                            ivDebug.setImageBitmap(debugBitmap);
                        }
                        savePrediction(bitmap, result.label, result.confidence);
                        hapticFeedback();

                        // Đọc kết quả bằng TTS nếu bật
                        boolean ttsOn = getSharedPreferences("AI_CONFIG", MODE_PRIVATE)
                                .getBoolean("tts_enabled", true);
                        if (ttsOn && tts != null) {
                            String speak = "Kết quả nhận dạng là " + result.label
                                    + ", độ tự tin " + String.format("%.0f", result.confidence) + " phần trăm";
                            tts.speak(speak, TextToSpeech.QUEUE_FLUSH, null, "main_result");
                        }
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    layoutLoading.setVisibility(View.GONE);
                    UIUtils.showErrorSnackbar(findViewById(android.R.id.content), "Lỗi nhận dạng: " + e.getMessage());
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
        header.setText("Top 3 Dự Đoán (AI Confidence):");
        header.setTextColor(getColor(R.color.text_secondary));
        header.setTextSize(14);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setPadding(0, 0, 0, 16);
        layoutTop.addView(header);

        for (DigitClassifier.PredictionItem item : result.topK) {
            View view = getLayoutInflater().inflate(R.layout.item_prediction_bar, layoutTop, false);
            ((TextView) view.findViewById(R.id.tv_label)).setText(item.label);
            ((TextView) view.findViewById(R.id.tv_confidence_value)).setText(String.format("%.1f%%", item.confidence));
            ((ProgressBar) view.findViewById(R.id.progress_confidence)).setProgress((int) item.confidence);
            layoutTop.addView(view);
        }

        if (result.confidence < 50) {
            UIUtils.showWarningSnackbar(findViewById(android.R.id.content),
                    String.format("Độ tự tin (%.1f%%) thấp hơn ngưỡng cài đặt (50%%)", result.confidence));
        }
    }

    private void showAiFeedback(float confidence) {
        if (tvAiFeedbackMain == null) return;
        tvAiFeedbackMain.setVisibility(View.VISIBLE);
        if (confidence >= 90) {
            tvAiFeedbackMain.setText("🌟 Ảnh rất rõ ràng! AI nhận dạng chính xác cao.");
            tvAiFeedbackMain.setTextColor(getColor(R.color.success));
        } else if (confidence >= 70) {
            tvAiFeedbackMain.setText("👍 Chất lượng ảnh tốt. Kết quả đáng tin cậy.");
            tvAiFeedbackMain.setTextColor(getColor(R.color.accent));
        } else if (confidence >= 50) {
            tvAiFeedbackMain.setText("✏️ Chất lượng trung bình. Thử crop gần ký tự hơn.");
            tvAiFeedbackMain.setTextColor(getColor(R.color.warning));
        } else {
            tvAiFeedbackMain.setText("🔄 Ảnh khó đọc. Hãy crop lại hoặc chụp rõ hơn.");
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
    protected void onResume() {
        super.onResume();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (digitClassifier != null) digitClassifier.close();
        if (executorService != null) executorService.shutdown();
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }
}