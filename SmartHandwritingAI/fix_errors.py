import os

base_dir = r"d:\MoBai\android-handwriting-number-recognition\SmartHandwritingAI"

def fix_entity():
    path = os.path.join(base_dir, r"app\src\main\java\dat\nguyenvan\smarthandwritingai\PredictionEntity.java")
    with open(path, "r", encoding="utf-8") as f: c = f.read()
    
    if "@androidx.room.ColumnInfo(name = \"is_favorite\")" not in c:
        c = c.replace("private long timestamp;", "private long timestamp;\n\n    @androidx.room.ColumnInfo(name = \"is_favorite\")\n    public boolean isFavorite = false;")
        with open(path, "w", encoding="utf-8") as f: f.write(c)

def fix_main_activity():
    path = os.path.join(base_dir, r"app\src\main\java\dat\nguyenvan\smarthandwritingai\MainActivity.java")
    with open(path, "r", encoding="utf-8") as f: c = f.read()

    # Fix imports
    c = c.replace("import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.ResultFormats;", "import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG;")
    
    # Fix intent sender request
    c = c.replace("ActivityResultLauncher<android.content.IntentSenderRequest>", "ActivityResultLauncher<androidx.activity.result.IntentSenderRequest>")
    c = c.replace("new android.content.IntentSenderRequest.Builder(intentSender).build()", "new androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()")
    
    # Fix ResultFormats to RESULT_FORMAT_JPEG
    c = c.replace("setResultFormats(ResultFormats.JPEG)", "setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)")
    c = c.replace("setScannerMode(ScannerMode.BASE)", "setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)")
    
    with open(path, "w", encoding="utf-8") as f: f.write(c)

fix_entity()
fix_main_activity()
print("Done fix errors")
