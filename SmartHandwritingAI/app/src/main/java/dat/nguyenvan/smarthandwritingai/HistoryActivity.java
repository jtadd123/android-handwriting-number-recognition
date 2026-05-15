package dat.nguyenvan.smarthandwritingai;

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
        try { db = FirebaseFirestore.getInstance(); } catch (Exception e) { db = null; }
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

    

    

    private void syncFirebase() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (activeNetwork == null || !activeNetwork.isConnectedOrConnecting()) {
            Toast.makeText(this, "Offline Mode: Không có kết nối mạng", Toast.LENGTH_SHORT).show();
            return;
        }

        if (db == null) { Toast.makeText(this, "Firebase chưa được cấu hình", Toast.LENGTH_SHORT).show(); return; }
        Toast.makeText(this, "Đang đồng bộ lên Firebase...", Toast.LENGTH_SHORT).show();
        executorService.execute(() -> {
            List<PredictionEntity> all = AppDatabase.getInstance(this).predictionDao().getAllPredictions();
            for (PredictionEntity p : all) {
                Map<String, Object> data = new HashMap<>();
                data.put("result", p.getResult());
                data.put("confidence", p.getConfidence());
                data.put("timestamp", p.getTimestamp());
                data.put("isFavorite", p.isFavorite);
                db.collection("history").document(String.valueOf(p.getTimestamp())).set(data);
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
