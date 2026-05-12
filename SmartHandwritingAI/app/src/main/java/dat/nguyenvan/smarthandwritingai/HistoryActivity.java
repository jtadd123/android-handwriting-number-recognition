package dat.nguyenvan.smarthandwritingai;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class HistoryActivity extends AppCompatActivity {

    private RecyclerView rvHistory;
    private LinearLayout layoutEmpty;
    private MaterialButton btnClearAll;

    private HistoryAdapter adapter;
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        initViews();
        setupListeners();
        loadHistory();
    }

    private void initViews() {
        rvHistory = findViewById(R.id.rv_history);
        layoutEmpty = findViewById(R.id.layout_empty);
        btnClearAll = findViewById(R.id.btn_clear_all);

        
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(new ArrayList<>());
        rvHistory.setAdapter(adapter);

        executorService = Executors.newSingleThreadExecutor();
    }

    private void setupListeners() {
        
        findViewById(R.id.btn_back_history).setOnClickListener(v -> finish());

        
        btnClearAll.setOnClickListener(v -> showClearConfirmDialog());
    }

    
    private void loadHistory() {
        executorService.execute(() -> {
            List<PredictionEntity> predictions =
                    AppDatabase.getInstance(this).predictionDao().getAllPredictions();

            runOnUiThread(() -> {
                if (predictions != null && !predictions.isEmpty()) {
                    adapter.updateData(predictions);
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
        new AlertDialog.Builder(this)
                .setMessage(R.string.confirm_clear)
                .setPositiveButton(R.string.yes, (dialog, which) -> clearAllHistory())
                .setNegativeButton(R.string.no, null)
                .show();
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
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
