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
    private ImageButton btnSpeakResult;

    private DigitClassifier digitClassifier;
    private Bitmap currentBitmap;
    private ExecutorService executorService;
    private TextToSpeech tts;

    private Uri photoUri;

    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && photoUri != null) {
                    launchUCrop(photoUri);
                }
            });

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    launchUCrop(uri);
                }
            });

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

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) openCamera();
                else Toast.makeText(this, getString(R.string.err_camera_permission), Toast.LENGTH_SHORT).show();
            });

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
        btnSpeakResult = findViewById(R.id.btn_speak_result);
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
            if (status == TextToSpeech.SUCCESS) {
                android.content.SharedPreferences prefs = getSharedPreferences("AI_CONFIG", MODE_PRIVATE);
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
    }

    private void setupListeners() {
        btnCamera.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                openCamera();
            else permissionLauncher.launch(Manifest.permission.CAMERA);
        });
        btnGallery.setOnClickListener(v -> galleryLauncher.launch("image/*"));
        btnBack.setOnClickListener(v -> resetState());

        if (btnSpeakResult != null) {
            btnSpeakResult.setOnClickListener(v -> {
                boolean ttsEnabled = getSharedPreferences("AI_CONFIG", MODE_PRIVATE).getBoolean("tts_enabled", true);
                if (!ttsEnabled) {
                    UIUtils.showWarningSnackbar(findViewById(android.R.id.content), getString(R.string.msg_tts_disabled));
                    return;
                }
                String text = tvResult.getText().toString();
                if (!text.equals("?") && tts != null) {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "btn_speak");
                }
            });
        }
    }

    private void setupBottomNavigation() {
        bottomNavigation.setItemIconTintList(null);
        bottomNavigation.setSelectedItemId(R.id.nav_home);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {

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

        ImageProcessor.rotationDegrees = 0;
        ImageProcessor.isFlipped = false;

        if (bottomNavigation != null) {
            bottomNavigation.setSelectedItemId(R.id.nav_home);
        }

        updateTtsButtonState();

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

    private void updateTtsButtonState() {
        if (btnSpeakResult != null) {
            boolean ttsEnabled = getSharedPreferences("AI_CONFIG", MODE_PRIVATE).getBoolean("tts_enabled", true);
            if (ttsEnabled) {
                btnSpeakResult.setImageResource(R.drawable.ic_volume_up);
                btnSpeakResult.setColorFilter(getColor(R.color.primary));
            } else {
                btnSpeakResult.setImageResource(R.drawable.ic_volume_off);
                btnSpeakResult.setColorFilter(getColor(R.color.text_hint));
            }
        }
    }

    private void launchUCrop(Uri sourceUri) {
        Uri destUri = Uri.fromFile(new File(getCacheDir(), "ucrop_output_" + System.currentTimeMillis() + ".jpg"));

        UCrop.Options options = new UCrop.Options();
        options.setCompressionQuality(90);
        options.setFreeStyleCropEnabled(true);
        options.setShowCropGrid(true);
        options.setShowCropFrame(true);
        options.setToolbarTitle(getString(R.string.crop_toolbar_title));
        options.setToolbarColor(getColor(R.color.primary_dark));
        options.setStatusBarColor(getColor(R.color.primary_dark));
        options.setActiveControlsWidgetColor(getColor(R.color.accent));
        options.setToolbarWidgetColor(getColor(R.color.white));

        options.setHideBottomControls(false);

        Intent cropIntent = UCrop.of(sourceUri, destUri)
                .withMaxResultSize(1080, 1080)
                .withOptions(options)
                .getIntent(this);

        cropLauncher.launch(cropIntent);
    }

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

                java.util.List<FractionParser.SegmentedSymbol> symbols = ImageProcessor.segmentImage(bitmap);
                if (symbols.isEmpty()) {
                    result = digitClassifier.predict(bitmap, DigitClassifier.PredictionMode.ALL_CLASSES, false);
                    debugBitmap = ImageProcessor.getLastPreprocessedBitmap();
                } else if (shouldClassifyWholeImageAsSingleDigit(symbols)) {
                    result = digitClassifier.predict(bitmap, DigitClassifier.PredictionMode.ALL_CLASSES, false);
                    debugBitmap = ImageProcessor.getLastPreprocessedBitmap();
                } else if (symbols.size() == 1) {
                    FractionParser.SegmentedSymbol sym = symbols.get(0);
                    result = digitClassifier.predict(sym.bitmap, DigitClassifier.PredictionMode.ALL_CLASSES, true);
                    debugBitmap = sym.bitmap;
                } else {
                    StringBuilder exprBuilder = new StringBuilder();
                    float totalConfidence = 0;
                    int digitCount = 0;

                    java.util.Collections.sort(symbols, (s1, s2) -> Float.compare(s1.left, s2.left));
                    for (FractionParser.SegmentedSymbol sym : symbols) {
                        DigitClassifier.PredictionResult digitResult =
                                digitClassifier.predict(sym.bitmap, DigitClassifier.PredictionMode.ALL_CLASSES, true);
                        if (digitResult != null) {
                            exprBuilder.append(digitResult.label);
                            totalConfidence += digitResult.confidence;
                            digitCount++;
                        }
                    }

                    float avgConfidence = digitCount > 0 ? (totalConfidence / digitCount) : 100f;
                    final String solvedLabel = exprBuilder.toString();
                    final float solvedConfidence = avgConfidence;

                    DigitClassifier.PredictionItem[] topK = new DigitClassifier.PredictionItem[3];
                    topK[0] = new DigitClassifier.PredictionItem(solvedLabel, solvedConfidence);
                    topK[1] = new DigitClassifier.PredictionItem("", 0f);
                    topK[2] = new DigitClassifier.PredictionItem("", 0f);
                    result = new DigitClassifier.PredictionResult(solvedLabel, solvedConfidence, topK);
                    if (!symbols.isEmpty()) {
                        debugBitmap = symbols.get(0).bitmap;
                    }
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

    static boolean shouldClassifyWholeImageAsSingleDigit(List<FractionParser.SegmentedSymbol> symbols) {
        if (symbols == null || symbols.size() <= 1 || symbols.size() > 3) {
            return false;
        }

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -1f;
        float maxY = -1f;
        float totalWidth = 0f;
        java.util.List<FractionParser.SegmentedSymbol> sorted = new java.util.ArrayList<>(symbols);
        java.util.Collections.sort(sorted, (s1, s2) -> Float.compare(s1.left, s2.left));

        for (FractionParser.SegmentedSymbol sym : sorted) {
            minX = Math.min(minX, sym.left);
            minY = Math.min(minY, sym.top);
            maxX = Math.max(maxX, sym.right);
            maxY = Math.max(maxY, sym.bottom);
            totalWidth += sym.width();
        }

        float unionWidth = maxX - minX;
        float unionHeight = Math.max(1f, maxY - minY);
        if (unionWidth / unionHeight > 0.70f) {
            return false;
        }

        float maxAllowedGap = Math.max(8f, unionHeight * 0.22f);
        float totalGap = unionWidth - totalWidth;
        if (totalGap > unionHeight * 0.18f) {
            return false;
        }

        for (int i = 1; i < sorted.size(); i++) {
            float gap = sorted.get(i).left - sorted.get(i - 1).right;
            if (gap > maxAllowedGap) {
                return false;
            }
        }

        return true;
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
                PredictionEntity entity = new PredictionEntity(base64, result, confidence, System.currentTimeMillis());
                AppDatabase.getInstance(this).predictionDao().insert(entity);

                boolean isSyncEnabled = getSharedPreferences("AI_CONFIG", MODE_PRIVATE).getBoolean("firebase_sync", false);
                if (isSyncEnabled && com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null) {
                    FirebaseSyncHelper.syncPrediction(this, entity, new FirebaseSyncHelper.OnSyncCompleteListener() {
                        @Override
                        public void onSuccess(String imageUrl) {
                            Log.d(TAG, "Auto sync successful: " + imageUrl);
                        }

                        @Override
                        public void onFailure(Exception e) {
                            Log.e(TAG, "Auto sync failed: " + e.getMessage());
                        }
                    });
                }
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
