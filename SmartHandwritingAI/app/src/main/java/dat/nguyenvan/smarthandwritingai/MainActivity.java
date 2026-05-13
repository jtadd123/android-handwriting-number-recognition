package dat.nguyenvan.smarthandwritingai;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private ImageView ivPreview;
    private TextView tvResult;
    private TextView tvConfidence;
    private TextView tvModelStatus;
    private TextView tvHint;
    private CardView cardResult;
    private MaterialButton btnCamera;
    private MaterialButton btnGallery;
    private MaterialButton btnDraw;
    private MaterialButton btnHistory;
    private MaterialButton btnBack;
    private View layoutHomeOptions;
    private View layoutRecognition;

    private DigitClassifier digitClassifier;
    private Bitmap currentBitmap;
    private ExecutorService executorService;

    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Bundle extras = result.getData().getExtras();
                    if (extras != null) {
                        currentBitmap = (Bitmap) extras.get("data");
                        if (currentBitmap != null) {
                            ivPreview.setImageBitmap(currentBitmap);
                            tvHint.setVisibility(View.GONE);
                            classifyImage(currentBitmap);
                        }
                    }
                } else if (result.getResultCode() == RESULT_CANCELED) {
                    Toast.makeText(this, "Đã hủy chụp ảnh", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ImageDecoder.Source source = ImageDecoder.createSource(
                                    getContentResolver(), uri);
                            currentBitmap = ImageDecoder.decodeBitmap(source)
                                    .copy(Bitmap.Config.ARGB_8888, true);
                        } else {
                            currentBitmap = MediaStore.Images.Media.getBitmap(
                                    getContentResolver(), uri);
                        }
                        ivPreview.setImageBitmap(currentBitmap);
                        tvHint.setVisibility(View.GONE);
                        classifyImage(currentBitmap);
                    } catch (Exception e) {
                        Toast.makeText(this, "Lỗi đọc ảnh: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    openCamera();
                } else {
                    Toast.makeText(this, "Cần quyền Camera để chụp ảnh", Toast.LENGTH_SHORT).show();
                }
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
        tvResult = findViewById(R.id.tv_result);
        tvConfidence = findViewById(R.id.tv_confidence);
        tvModelStatus = findViewById(R.id.tv_model_status);
        tvHint = findViewById(R.id.tv_hint);
        cardResult = findViewById(R.id.card_result);
        btnCamera = findViewById(R.id.btn_camera);
        btnGallery = findViewById(R.id.btn_gallery);
        btnDraw = findViewById(R.id.btn_draw);
        btnHistory = findViewById(R.id.btn_history);
        btnBack = findViewById(R.id.btn_back_main);
        layoutHomeOptions = findViewById(R.id.layout_home_options);
        layoutRecognition = findViewById(R.id.layout_recognition);

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
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                permissionLauncher.launch(Manifest.permission.CAMERA);
            }
        });

        btnGallery.setOnClickListener(v -> openGallery());

        btnDraw.setOnClickListener(v -> {
            Intent intent = new Intent(this, DrawActivity.class);
            startActivity(intent);
        });

        btnHistory.setOnClickListener(v -> {
            Intent intent = new Intent(this, HistoryActivity.class);
            startActivity(intent);
        });

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
        layoutRecognition.setVisibility(View.GONE);
        layoutHomeOptions.setVisibility(View.VISIBLE);
    }

    private void openCamera() {
        try {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraLauncher.launch(takePictureIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Không thể mở Camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        try {
            galleryLauncher.launch("image/*");
        } catch (Exception e) {
            Toast.makeText(this, "Không thể mở Thư viện: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void classifyImage(Bitmap bitmap) {
        if (digitClassifier == null || !digitClassifier.isInitialized()) {
            Toast.makeText(this, "Model chưa sẵn sàng", Toast.LENGTH_SHORT).show();
            return;
        }

        executorService.execute(() -> {
            try {
                DigitClassifier.PredictionResult result = digitClassifier.predict(bitmap);
                String predictedLabel = result.label;
                float confidence = result.confidence;

                savePrediction(bitmap, predictedLabel, confidence);

                runOnUiThread(() -> {
                    layoutHomeOptions.setVisibility(View.GONE);
                    layoutRecognition.setVisibility(View.VISIBLE);
                    cardResult.setVisibility(View.VISIBLE);
                    btnBack.setVisibility(View.VISIBLE);
                    tvResult.setText(predictedLabel);
                    tvConfidence.setText(String.format("%.1f%%", confidence));

                    if (confidence >= 90) {
                        tvConfidence.setTextColor(getColor(R.color.success));
                    } else if (confidence >= 70) {
                        tvConfidence.setTextColor(getColor(R.color.warning));
                    } else {
                        tvConfidence.setTextColor(getColor(R.color.error));
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Lỗi nhận dạng ảnh: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Lỗi xử lý hình ảnh", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void savePrediction(Bitmap bitmap, String result, float confidence) {
        executorService.execute(() -> {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Bitmap resized = Bitmap.createScaledBitmap(bitmap, 56, 56, true);
                resized.compress(Bitmap.CompressFormat.PNG, 100, baos);
                String base64 = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);

                PredictionEntity entity = new PredictionEntity(
                        base64, result, confidence, System.currentTimeMillis());

                AppDatabase.getInstance(this).predictionDao().insert(entity);
                Log.d(TAG, "Prediction saved: " + result + " (" + confidence + "%)");
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
                layoutRecognition.setVisibility(View.VISIBLE);
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
        if (digitClassifier != null) {
            digitClassifier.close();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}