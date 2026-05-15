package dat.nguyenvan.smarthandwritingai;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;

import android.provider.MediaStore;
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

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private ImageView ivPreview, ivDebug;
    private View layoutDebug;
    private TextView tvResult, tvConfidence, tvModelStatus, tvHint;
    private View cardResult, layoutHomeOptions, layoutLoading, scanLine;
    private MaterialButton btnCamera, btnGallery, btnDraw, btnHistory, btnAnalytics, btnBack;

    private DigitClassifier digitClassifier;
    private Bitmap currentBitmap;
    private ExecutorService executorService;

    private Uri photoUri;

    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    try {
                        if (photoUri != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), photoUri);
                                currentBitmap = ImageDecoder.decodeBitmap(source).copy(Bitmap.Config.ARGB_8888, true);
                            } else {
                                currentBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), photoUri);
                            }
                            
                            if (currentBitmap != null) {
                                currentBitmap = rotateImageIfRequired(currentBitmap, photoUri);
                                ivPreview.setImageBitmap(currentBitmap);
                                tvHint.setVisibility(View.GONE);
                                classifyImage(currentBitmap);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error reading camera image: " + e.getMessage());
                        Toast.makeText(this, "Lỗi đọc ảnh camera", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    private Bitmap rotateImageIfRequired(Bitmap img, Uri selectedImage) throws java.io.IOException {
        java.io.InputStream input = getContentResolver().openInputStream(selectedImage);
        androidx.exifinterface.media.ExifInterface ei;
        if (Build.VERSION.SDK_INT > 23)
            ei = new androidx.exifinterface.media.ExifInterface(input);
        else
            ei = new androidx.exifinterface.media.ExifInterface(selectedImage.getPath());

        int orientation = ei.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL);

        switch (orientation) {
            case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90:
                return rotateImage(img, 90);
            case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180:
                return rotateImage(img, 180);
            case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270:
                return rotateImage(img, 270);
            default:
                return img;
        }
    }

    private static Bitmap rotateImage(Bitmap img, int degree) {
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.postRotate(degree);
        Bitmap rotatedImg = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
        img.recycle();
        return rotatedImg;
    }

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
                            currentBitmap = ImageDecoder.decodeBitmap(source).copy(Bitmap.Config.ARGB_8888, true);
                        } else {
                            currentBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                        }
                        ivPreview.setImageBitmap(currentBitmap);
                        tvHint.setVisibility(View.GONE);
                        classifyImage(currentBitmap);
                    } catch (Exception e) {
                        Toast.makeText(this, "Lỗi đọc ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) openCamera();
                else Toast.makeText(this, "Cần quyền Camera để chụp ảnh", Toast.LENGTH_SHORT).show();
            });

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
        setupListeners();
    }

    private void initViews() {
        ivPreview = findViewById(R.id.iv_preview);
        ivDebug = findViewById(R.id.iv_debug);
        layoutDebug = findViewById(R.id.layout_debug);
        tvResult = findViewById(R.id.tv_result);
        tvConfidence = findViewById(R.id.tv_confidence);
        tvModelStatus = findViewById(R.id.tv_model_status);
        tvHint = findViewById(R.id.tv_hint);
        cardResult = findViewById(R.id.card_result);
        btnCamera = findViewById(R.id.btn_camera);
        btnGallery = findViewById(R.id.btn_gallery);
        btnDraw = findViewById(R.id.btn_draw);
        btnHistory = findViewById(R.id.btn_history);
        btnAnalytics = findViewById(R.id.btn_analytics);
        btnBack = findViewById(R.id.btn_back_main);
        layoutHomeOptions = findViewById(R.id.layout_home_options);
        layoutLoading = findViewById(R.id.layout_loading);
        scanLine = findViewById(R.id.scan_line);

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

    private void setupListeners() {
        btnCamera.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera();
            else permissionLauncher.launch(Manifest.permission.CAMERA);
        });

        btnGallery.setOnClickListener(v -> galleryLauncher.launch("image/*"));
        btnDraw.setOnClickListener(v -> startActivity(new Intent(this, DrawActivity.class)));
        btnHistory.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        btnAnalytics.setOnClickListener(v -> startActivity(new Intent(this, AnalyticsActivity.class)));
        findViewById(R.id.btn_settings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        btnBack.setOnClickListener(v -> resetState());
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
    }

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

    private void classifyImage(Bitmap bitmap) {
        if (digitClassifier == null || !digitClassifier.isInitialized()) {
            UIUtils.showErrorSnackbar(findViewById(android.R.id.content), "Model chưa sẵn sàng");
            return;
        }

        layoutLoading.setVisibility(View.VISIBLE);
        layoutLoading.setAlpha(1f);
        scanLine.setVisibility(View.VISIBLE);
        scanLine.setTranslationY(0f);
        scanLine.animate().translationY(600f).setDuration(1000).withEndAction(() -> scanLine.setVisibility(View.GONE)).start();

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
            TextView tvLabel = view.findViewById(R.id.tv_label);
            TextView tvValue = view.findViewById(R.id.tv_confidence_value);
            ProgressBar progress = view.findViewById(R.id.progress_confidence);

            tvLabel.setText(item.label);
            tvValue.setText(String.format("%.1f%%", item.confidence));
            progress.setProgress((int) item.confidence);
            layoutTop.addView(view);
        }

        if (result.confidence < 50) {
            UIUtils.showWarningSnackbar(findViewById(android.R.id.content),
                    String.format("Độ tự tin (%.1f%%) thấp hơn ngưỡng cài đặt (50%%)", result.confidence));
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
            } catch (Exception e) {
                Log.e(TAG, "Error saving prediction: " + e.getMessage());
            }
        });
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
    }
}