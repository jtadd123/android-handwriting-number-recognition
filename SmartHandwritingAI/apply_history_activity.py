import os

base_dir = r"d:\MoBai\android-handwriting-number-recognition\SmartHandwritingAI"

def write_xml():
    path = os.path.join(base_dir, r"app\src\main\res\layout\activity_history.xml")
    c = """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent" android:layout_height="match_parent"
    android:background="@color/surface_dark" android:orientation="vertical">
    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
        android:gravity="center_vertical" android:orientation="horizontal"
        android:paddingHorizontal="20dp" android:paddingTop="20dp" android:paddingBottom="12dp">
        <ImageButton android:id="@+id/btn_back_history" android:layout_width="40dp"
            android:layout_height="40dp" android:background="?attr/selectableItemBackgroundBorderless"
            android:src="@android:drawable/ic_menu_revert" app:tint="@color/text_primary" />
        <TextView android:layout_width="0dp" android:layout_height="wrap_content"
            android:layout_marginStart="12dp" android:layout_weight="1" android:text="@string/title_history"
            android:textColor="@color/text_primary" android:textSize="22sp" android:textStyle="bold" />
        <ImageButton android:id="@+id/btn_sync" android:layout_width="40dp" android:layout_height="40dp"
            android:background="?attr/selectableItemBackgroundBorderless" android:src="@android:drawable/stat_notify_sync" app:tint="@color/accent" />
        <com.google.android.material.button.MaterialButton android:id="@+id/btn_clear_all"
            style="@style/Widget.Material3.Button.TextButton" android:layout_width="wrap_content"
            android:layout_height="wrap_content" android:text="@string/btn_clear_history" android:textColor="@color/error" />
    </LinearLayout>
    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
        android:orientation="vertical" android:paddingHorizontal="20dp" android:paddingBottom="8dp">
        <androidx.appcompat.widget.SearchView android:id="@+id/search_view" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:background="@drawable/bg_rounded_pill"
            android:backgroundTint="@color/surface_elevated" app:queryHint="Tìm kiếm lịch sử" app:iconifiedByDefault="false" />
        <com.google.android.material.chip.ChipGroup android:id="@+id/chip_group_filter" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:layout_marginTop="8dp" app:singleSelection="true">
            <com.google.android.material.chip.Chip android:id="@+id/chip_all" android:layout_width="wrap_content"
                android:layout_height="wrap_content" android:text="Tất cả" android:checked="true" style="@style/Widget.Material3.Chip.Filter" />
            <com.google.android.material.chip.Chip android:id="@+id/chip_favorites" android:layout_width="wrap_content"
                android:layout_height="wrap_content" android:text="Yêu thích" style="@style/Widget.Material3.Chip.Filter" />
        </com.google.android.material.chip.ChipGroup>
    </LinearLayout>
    <LinearLayout android:id="@+id/layout_empty" android:layout_width="match_parent"
        android:layout_height="match_parent" android:gravity="center" android:orientation="vertical">
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="📭" android:textSize="64sp" />
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_marginTop="16dp"
            android:text="@string/no_history" android:textColor="@color/text_hint" android:textSize="16sp" />
    </LinearLayout>
    <androidx.recyclerview.widget.RecyclerView android:id="@+id/rv_history" android:layout_width="match_parent"
        android:layout_height="match_parent" android:clipToPadding="false" android:padding="16dp" android:visibility="gone" />
</LinearLayout>
"""
    with open(path, "w", encoding="utf-8") as f: f.write(c)

def write_java():
    path = os.path.join(base_dir, r"app\src\main\java\dat\nguyenvan\smarthandwritingai\HistoryActivity.java")
    c = """package dat.nguyenvan.smarthandwritingai;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.firestore.FirebaseFirestore;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HistoryActivity extends AppCompatActivity implements HistoryAdapter.OnItemClickListener {
    private RecyclerView rvHistory;
    private LinearLayout layoutEmpty;
    private HistoryAdapter adapter;
    private ExecutorService executorService;
    private SearchView searchView;
    private ChipGroup chipGroupFilter;
    private String currentQuery = "";
    private boolean showFavoritesOnly = false;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        db = FirebaseFirestore.getInstance();
        initViews();
        setupListeners();
        loadHistory();
    }

    private void initViews() {
        rvHistory = findViewById(R.id.rv_history);
        layoutEmpty = findViewById(R.id.layout_empty);
        searchView = findViewById(R.id.search_view);
        chipGroupFilter = findViewById(R.id.chip_group_filter);
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(new ArrayList<>(), this);
        rvHistory.setAdapter(adapter);
        executorService = Executors.newSingleThreadExecutor();
    }

    private void setupListeners() {
        findViewById(R.id.btn_back_history).setOnClickListener(v -> finish());
        findViewById(R.id.btn_clear_all).setOnClickListener(v -> showClearConfirmDialog());
        findViewById(R.id.btn_sync).setOnClickListener(v -> syncFirebase());
        
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { return false; }
            @Override public boolean onQueryTextChange(String newText) {
                currentQuery = newText; loadHistory(); return true;
            }
        });
        
        chipGroupFilter.setOnCheckedChangeListener((group, checkedId) -> {
            showFavoritesOnly = (checkedId == R.id.chip_favorites);
            loadHistory();
        });
    }

    private void loadHistory() {
        executorService.execute(() -> {
            PredictionDao dao = AppDatabase.getInstance(this).predictionDao();
            List<PredictionEntity> list;
            if (showFavoritesOnly) list = dao.getFavorites();
            else if (!currentQuery.isEmpty()) list = dao.searchPredictions(currentQuery);
            else list = dao.getAllPredictions();

            runOnUiThread(() -> {
                if (list != null && !list.isEmpty()) {
                    adapter.updateData(list);
                    rvHistory.setVisibility(View.VISIBLE);
                    layoutEmpty.setVisibility(View.GONE);
                } else {
                    rvHistory.setVisibility(View.GONE);
                    layoutEmpty.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private void showClearConfirmDialog() {
        new AlertDialog.Builder(this).setMessage(R.string.confirm_clear)
                .setPositiveButton(R.string.yes, (dialog, which) -> clearAllHistory())
                .setNegativeButton(R.string.no, null).show();
    }

    private void clearAllHistory() {
        executorService.execute(() -> {
            AppDatabase.getInstance(this).predictionDao().deleteAll();
            runOnUiThread(() -> {
                adapter.updateData(new ArrayList<>());
                rvHistory.setVisibility(View.GONE);
                layoutEmpty.setVisibility(View.VISIBLE);
                Toast.makeText(this, R.string.history_cleared, Toast.LENGTH_SHORT).show();
            });
        });
    }

    @Override
    public void onFavoriteClick(PredictionEntity entity) {
        executorService.execute(() -> {
            entity.isFavorite = !entity.isFavorite;
            AppDatabase.getInstance(this).predictionDao().update(entity);
            runOnUiThread(this::loadHistory);
        });
    }

    @Override
    public void onExportClick(PredictionEntity entity) {
        executorService.execute(() -> {
            try {
                PdfDocument document = new PdfDocument();
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(400, 600, 1).create();
                PdfDocument.Page page = document.startPage(pageInfo);
                Canvas canvas = page.getCanvas();
                Paint paint = new Paint();
                
                canvas.drawText("AI Handwriting Report", 10, 30, paint);
                canvas.drawText("Result: " + entity.result, 10, 60, paint);
                canvas.drawText(String.format("Confidence: %.1f%%", entity.confidence), 10, 80, paint);
                
                if (entity.imageBase64 != null) {
                    byte[] bytes = Base64.decode(entity.imageBase64, Base64.DEFAULT);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bitmap != null) {
                        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 200, 200, true);
                        canvas.drawBitmap(scaled, 10, 100, paint);
                    }
                }
                
                document.finishPage(page);
                File file = new File(getCacheDir(), "report_" + entity.timestamp + ".pdf");
                document.writeTo(new FileOutputStream(file));
                document.close();
                
                runOnUiThread(() -> sharePdf(file));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Lỗi tạo PDF", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void sharePdf(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share PDF"));
        } catch (Exception e) {
            Toast.makeText(this, "Không thể chia sẻ", Toast.LENGTH_SHORT).show();
        }
    }

    private void syncFirebase() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (activeNetwork == null || !activeNetwork.isConnectedOrConnecting()) {
            Toast.makeText(this, "Offline Mode: Không có kết nối mạng", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Đang đồng bộ lên Firebase...", Toast.LENGTH_SHORT).show();
        executorService.execute(() -> {
            List<PredictionEntity> all = AppDatabase.getInstance(this).predictionDao().getAllPredictions();
            for (PredictionEntity p : all) {
                Map<String, Object> data = new HashMap<>();
                data.put("result", p.result);
                data.put("confidence", p.confidence);
                data.put("timestamp", p.timestamp);
                data.put("isFavorite", p.isFavorite);
                db.collection("history").document(String.valueOf(p.timestamp)).set(data);
            }
            runOnUiThread(() -> Toast.makeText(this, "Đồng bộ thành công!", Toast.LENGTH_SHORT).show());
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) executorService.shutdown();
    }
}
"""
    with open(path, "w", encoding="utf-8") as f: f.write(c)

write_xml()
write_java()
print("Done Script 3")
