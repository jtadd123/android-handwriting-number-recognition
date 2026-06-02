package dat.nguyenvan.smarthandwritingai;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.firestore.FirebaseFirestore;

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
    private BottomNavigationView bottomNavigation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        try { db = FirebaseFirestore.getInstance(); } catch (Exception e) { db = null; }
        initViews();
        setupListeners();
        setupSwipeToDelete();
        setupBottomNavigation();
        loadHistory();
    }

    private void initViews() {
        rvHistory = findViewById(R.id.rv_history);
        layoutEmpty = findViewById(R.id.layout_empty);
        searchView = findViewById(R.id.search_view);
        chipGroupFilter = findViewById(R.id.chip_group_filter);
        bottomNavigation = findViewById(R.id.bottom_navigation);
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

    private void setupBottomNavigation() {
        bottomNavigation.setItemIconTintList(null);
        bottomNavigation.setSelectedItemId(R.id.nav_history);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_history) {
                return true;
            } else if (id == R.id.nav_home) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return true;
            } else if (id == R.id.nav_draw) {
                startActivity(new Intent(this, DrawActivity.class));
                return true;
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });
    }

    private void setupSwipeToDelete() {
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(RecyclerView rv, RecyclerView.ViewHolder vh, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                PredictionEntity entity = adapter.getItemAt(position);
                if (entity != null) {
                    deleteItem(entity, position);
                }
            }

            @Override
            public void onChildDraw(android.graphics.Canvas c, RecyclerView recyclerView,
                                    RecyclerView.ViewHolder viewHolder, float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {

                View itemView = viewHolder.itemView;
                android.graphics.Paint paint = new android.graphics.Paint();
                paint.setColor(getColor(R.color.error));

                if (dX < 0) {
                    c.drawRect(itemView.getRight() + dX, itemView.getTop(),
                            itemView.getRight(), itemView.getBottom(), paint);
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(rvHistory);
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
    public void onDeleteClick(PredictionEntity entity, int position) {
        new AlertDialog.Builder(this)
                .setMessage(R.string.confirm_delete_item)
                .setPositiveButton(R.string.yes, (dialog, which) -> deleteItem(entity, position))
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void deleteItem(PredictionEntity entity, int position) {
        executorService.execute(() -> {
            AppDatabase.getInstance(this).predictionDao().delete(entity);
            runOnUiThread(() -> {
                adapter.removeItem(position);
                if (adapter.getItemCount() == 0) {
                    rvHistory.setVisibility(View.GONE);
                    layoutEmpty.setVisibility(View.VISIBLE);
                }
                UIUtils.showSuccessSnackbar(findViewById(android.R.id.content), getString(R.string.item_deleted));
            });
        });
    }

    private void syncFirebase() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (activeNetwork == null || !activeNetwork.isConnectedOrConnecting()) {
            Toast.makeText(this, R.string.msg_offline_mode, Toast.LENGTH_SHORT).show();
            return;
        }

        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Vui lòng đăng nhập để đồng bộ lịch sử.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, R.string.msg_syncing_firebase, Toast.LENGTH_SHORT).show();
        FirebaseSyncHelper.syncAll(this, new FirebaseSyncHelper.OnSyncAllListener() {
            @Override
            public void onProgress(int current, int total) {
            }

            @Override
            public void onComplete(int successCount, int totalCount) {
                runOnUiThread(() -> {
                    if (successCount > 0) {
                        Toast.makeText(HistoryActivity.this, getString(R.string.msg_sync_success) + " (" + successCount + "/" + totalCount + ")", Toast.LENGTH_SHORT).show();
                        loadHistory();
                    } else {
                        Toast.makeText(HistoryActivity.this, "Đồng bộ hoàn tất (0 mục mới).", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) executorService.shutdown();
    }
}
