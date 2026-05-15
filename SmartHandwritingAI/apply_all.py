import os
import re

base_dir = r"d:\MoBai\android-handwriting-number-recognition\SmartHandwritingAI"

def fix_gradle():
    path = os.path.join(base_dir, "app", "build.gradle.kts")
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    
    # Remove the bad line
    content = re.sub(r'}\s*implementation\("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1"\)\s*', '}\n', content)
    
    # Add inside block
    if "play-services-mlkit-document-scanner" not in content:
        content = content.replace("    androidTestImplementation(libs.ext.junit)\n}", "    androidTestImplementation(libs.ext.junit)\n    implementation(\"com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1\")\n}")
    
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)

def add_is_favorite():
    path = os.path.join(base_dir, r"app\src\main\java\dat\nguyenvan\smarthandwritingai\PredictionEntity.java")
    with open(path, "r", encoding="utf-8") as f:
        c = f.read()
    
    if "isFavorite" not in c:
        c = c.replace("public long timestamp;", "public long timestamp;\n\n    @androidx.room.ColumnInfo(name = \"is_favorite\")\n    public boolean isFavorite = false;")
        c = c.replace("long timestamp) {", "long timestamp) {\n        this.isFavorite = false;")
        with open(path, "w", encoding="utf-8") as f:
            f.write(c)

def bump_db_version():
    path = os.path.join(base_dir, r"app\src\main\java\dat\nguyenvan\smarthandwritingai\AppDatabase.java")
    with open(path, "r", encoding="utf-8") as f:
        c = f.read()
    if "version = 1" in c:
        c = c.replace("version = 1", "version = 2")
        c = c.replace(".build()", ".fallbackToDestructiveMigration().build()")
        with open(path, "w", encoding="utf-8") as f:
            f.write(c)

def modify_main_activity():
    path = os.path.join(base_dir, r"app\src\main\java\dat\nguyenvan\smarthandwritingai\MainActivity.java")
    with open(path, "r", encoding="utf-8") as f:
        c = f.read()
    
    # Imports
    imports = """import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.ResultFormats;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.ScannerMode;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;
"""
    if "GmsDocumentScannerOptions" not in c:
        c = c.replace("import android.os.Bundle;", "import android.os.Bundle;\n" + imports)

    # Scanner Launcher
    scanner_launcher = """
    private final ActivityResultLauncher<android.content.IntentSenderRequest> scannerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    GmsDocumentScanningResult scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.getData());
                    if (scanResult != null && scanResult.getPages() != null && !scanResult.getPages().isEmpty()) {
                        Uri imageUri = scanResult.getPages().get(0).getImageUri();
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), imageUri);
                                currentBitmap = ImageDecoder.decodeBitmap(source).copy(Bitmap.Config.ARGB_8888, true);
                            } else {
                                currentBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                            }
                            ivPreview.setImageBitmap(currentBitmap);
                            tvHint.setVisibility(View.GONE);
                            classifyImage(currentBitmap);
                        } catch (Exception e) {
                            Toast.makeText(this, "Lỗi đọc ảnh scan: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });
"""
    if "scannerLauncher =" not in c:
        c = c.replace("private final ActivityResultLauncher<String> galleryLauncher =", scanner_launcher + "\n    private final ActivityResultLauncher<String> galleryLauncher =")

    # Change openCamera logic
    new_open_camera = """
    private void openCamera() {
        GmsDocumentScannerOptions options = new GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(false)
                .setPageLimit(1)
                .setResultFormats(ResultFormats.JPEG)
                .setScannerMode(ScannerMode.BASE)
                .build();
        GmsDocumentScanning.getClient(options).getStartScanIntent(this)
                .addOnSuccessListener(intentSender -> {
                    scannerLauncher.launch(new android.content.IntentSenderRequest.Builder(intentSender).build());
                })
                .addOnFailureListener(e -> {
                    // Fallback to normal camera if ML Kit fails
                    try {
                        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        cameraLauncher.launch(takePictureIntent);
                    } catch (Exception ex) {
                        UIUtils.showErrorSnackbar(findViewById(android.R.id.content), "Không thể mở Camera: " + ex.getMessage());
                    }
                });
    }
"""
    if "GmsDocumentScannerOptions" not in c:
        c = re.sub(r'private void openCamera\(\) \{.*?(?=\n\s*private void openGallery)', new_open_camera.strip(), c, flags=re.DOTALL)

    with open(path, "w", encoding="utf-8") as f:
        f.write(c)

def modify_manifest():
    path = os.path.join(base_dir, r"app\src\main\AndroidManifest.xml")
    with open(path, "r", encoding="utf-8") as f:
        c = f.read()
    
    if "hardwareAccelerated=\"true\"" not in c:
        c = c.replace("<application", "<application\n        android:hardwareAccelerated=\"true\"\n        android:largeHeap=\"true\"")
    
    with open(path, "w", encoding="utf-8") as f:
        f.write(c)

fix_gradle()
add_is_favorite()
bump_db_version()
modify_main_activity()
modify_manifest()
print("Done Script 1")
