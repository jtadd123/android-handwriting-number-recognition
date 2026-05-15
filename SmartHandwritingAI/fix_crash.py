import os

base_dir = r"d:\MoBai\android-handwriting-number-recognition\SmartHandwritingAI"

def fix_history_activity():
    path = os.path.join(base_dir, r"app\src\main\java\dat\nguyenvan\smarthandwritingai\HistoryActivity.java")
    with open(path, "r", encoding="utf-8") as f:
        c = f.read()

    # Wrap Firebase initialization to prevent crash
    c = c.replace("db = FirebaseFirestore.getInstance();", "try { db = FirebaseFirestore.getInstance(); } catch (Exception e) { db = null; }")

    # Remove PDF export from activity
    c = c.replace("public class HistoryActivity extends AppCompatActivity implements HistoryAdapter.OnItemClickListener", "public class HistoryActivity extends AppCompatActivity implements HistoryAdapter.OnItemClickListener")
    
    # Actually, let's just wipe out the onExportClick implementation completely
    import re
    c = re.sub(r'@Override\s*public void onExportClick\(PredictionEntity entity\) \{.*?(?=\s*private void sharePdf)', '', c, flags=re.DOTALL)
    c = re.sub(r'private void sharePdf\(File file\) \{.*?(?=\s*private void syncFirebase)', '', c, flags=re.DOTALL)
    
    # Fix syncFirebase to handle null db
    c = c.replace("Toast.makeText(this, \"Đang đồng bộ lên Firebase...\", Toast.LENGTH_SHORT).show();", "if (db == null) { Toast.makeText(this, \"Firebase chưa được cấu hình\", Toast.LENGTH_SHORT).show(); return; }\n        Toast.makeText(this, \"Đang đồng bộ lên Firebase...\", Toast.LENGTH_SHORT).show();")

    with open(path, "w", encoding="utf-8") as f:
        f.write(c)

def fix_history_adapter():
    path = os.path.join(base_dir, r"app\src\main\java\dat\nguyenvan\smarthandwritingai\HistoryAdapter.java")
    with open(path, "r", encoding="utf-8") as f:
        c = f.read()
    
    # Remove onExportClick from interface
    c = c.replace("void onExportClick(PredictionEntity entity);", "")
    
    # Remove btnExport binding
    c = c.replace("holder.btnExport.setOnClickListener(v -> listener.onExportClick(prediction));", "")
    c = c.replace("ImageButton btnFavorite, btnExport;", "ImageButton btnFavorite;")
    c = c.replace("btnExport = v.findViewById(R.id.btn_export);", "")
    
    with open(path, "w", encoding="utf-8") as f:
        f.write(c)

def fix_item_history_xml():
    path = os.path.join(base_dir, r"app\src\main\res\layout\item_history.xml")
    with open(path, "r", encoding="utf-8") as f:
        c = f.read()
    
    # Remove the export button XML
    import re
    c = re.sub(r'<ImageButton\s+android:id="@+id/btn_export".*?/>', '', c, flags=re.DOTALL)
    
    with open(path, "w", encoding="utf-8") as f:
        f.write(c)

fix_history_activity()
fix_history_adapter()
fix_item_history_xml()
print("Done fix crashes and remove prompt 22")
