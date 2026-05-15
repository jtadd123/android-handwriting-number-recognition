import os

base_dir = r"d:\MoBai\android-handwriting-number-recognition\SmartHandwritingAI"

def write_dao():
    path = os.path.join(base_dir, r"app\src\main\java\dat\nguyenvan\smarthandwritingai\PredictionDao.java")
    c = """package dat.nguyenvan.smarthandwritingai;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface PredictionDao {
    @Insert
    void insert(PredictionEntity prediction);
    @Update
    void update(PredictionEntity prediction);
    @Query("SELECT * FROM prediction_history ORDER BY timestamp DESC")
    List<PredictionEntity> getAllPredictions();
    @Query("SELECT * FROM prediction_history WHERE result LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    List<PredictionEntity> searchPredictions(String query);
    @Query("SELECT * FROM prediction_history WHERE is_favorite = 1 ORDER BY timestamp DESC")
    List<PredictionEntity> getFavorites();
    @Query("DELETE FROM prediction_history")
    void deleteAll();
    @Query("SELECT COUNT(*) FROM prediction_history")
    int getCount();
}
"""
    with open(path, "w", encoding="utf-8") as f: f.write(c)

def write_adapter():
    path = os.path.join(base_dir, r"app\src\main\java\dat\nguyenvan\smarthandwritingai\HistoryAdapter.java")
    c = """package dat.nguyenvan.smarthandwritingai;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
    private List<PredictionEntity> predictions;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onFavoriteClick(PredictionEntity entity);
        void onExportClick(PredictionEntity entity);
    }

    public HistoryAdapter(List<PredictionEntity> predictions, OnItemClickListener listener) {
        this.predictions = predictions;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PredictionEntity prediction = predictions.get(position);
        holder.tvResult.setText(String.valueOf(prediction.result));
        holder.tvConfidence.setText(String.format("%.1f%%", prediction.confidence));
        
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        holder.tvTimestamp.setText(sdf.format(new Date(prediction.timestamp)));
        
        try {
            if (prediction.imageBase64 != null && !prediction.imageBase64.isEmpty()) {
                byte[] bytes = Base64.decode(prediction.imageBase64, Base64.DEFAULT);
                holder.ivImage.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
            }
        } catch (Exception e) {}

        if (prediction.confidence >= 90) holder.tvConfidence.setTextColor(0xFF4CAF50);
        else if (prediction.confidence >= 70) holder.tvConfidence.setTextColor(0xFFFF9800);
        else holder.tvConfidence.setTextColor(0xFFF44336);
        
        holder.btnFavorite.setImageResource(prediction.isFavorite ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
        holder.btnFavorite.setOnClickListener(v -> listener.onFavoriteClick(prediction));
        holder.btnExport.setOnClickListener(v -> listener.onExportClick(prediction));
    }

    @Override
    public int getItemCount() { return predictions != null ? predictions.size() : 0; }
    public void updateData(List<PredictionEntity> newPredictions) {
        this.predictions = newPredictions;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivImage; TextView tvResult, tvConfidence, tvTimestamp;
        ImageButton btnFavorite, btnExport;
        ViewHolder(@NonNull View v) {
            super(v);
            ivImage = v.findViewById(R.id.iv_history_image);
            tvResult = v.findViewById(R.id.tv_history_result);
            tvConfidence = v.findViewById(R.id.tv_history_confidence);
            tvTimestamp = v.findViewById(R.id.tv_history_timestamp);
            btnFavorite = v.findViewById(R.id.btn_favorite);
            btnExport = v.findViewById(R.id.btn_export);
        }
    }
}
"""
    with open(path, "w", encoding="utf-8") as f: f.write(c)

def write_item_history():
    path = os.path.join(base_dir, r"app\src\main\res\layout\item_history.xml")
    c = """<?xml version="1.0" encoding="utf-8"?>
<androidx.cardview.widget.CardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent" android:layout_height="wrap_content"
    android:layout_marginBottom="12dp" app:cardBackgroundColor="@color/surface_card"
    app:cardCornerRadius="16dp" app:cardElevation="4dp">
    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
        android:gravity="center_vertical" android:orientation="horizontal" android:padding="16dp">
        <androidx.cardview.widget.CardView android:layout_width="60dp" android:layout_height="60dp"
            app:cardBackgroundColor="@color/black" app:cardCornerRadius="12dp">
            <ImageView android:id="@+id/iv_history_image" android:layout_width="match_parent"
                android:layout_height="match_parent" android:padding="4dp" />
        </androidx.cardview.widget.CardView>
        <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content"
            android:layout_marginStart="16dp" android:layout_weight="1" android:orientation="vertical">
            <TextView android:id="@+id/tv_history_result" android:layout_width="wrap_content"
                android:layout_height="wrap_content" android:textColor="@color/accent"
                android:textSize="24sp" android:textStyle="bold" />
            <TextView android:id="@+id/tv_history_timestamp" android:layout_width="wrap_content"
                android:layout_height="wrap_content" android:textColor="@color/text_hint" android:textSize="11sp" />
        </LinearLayout>
        <TextView android:id="@+id/tv_history_confidence" android:layout_width="wrap_content"
            android:layout_height="wrap_content" android:textSize="16sp" android:textStyle="bold" android:layout_marginEnd="8dp"/>
        <ImageButton android:id="@+id/btn_export" android:layout_width="36dp" android:layout_height="36dp"
            android:background="?attr/selectableItemBackgroundBorderless" android:src="@android:drawable/ic_menu_share" app:tint="@color/text_secondary" />
        <ImageButton android:id="@+id/btn_favorite" android:layout_width="36dp" android:layout_height="36dp"
            android:background="?attr/selectableItemBackgroundBorderless" android:src="@android:drawable/btn_star_big_off" />
    </LinearLayout>
</androidx.cardview.widget.CardView>
"""
    with open(path, "w", encoding="utf-8") as f: f.write(c)

write_dao()
write_adapter()
write_item_history()
print("Done Script 2")
