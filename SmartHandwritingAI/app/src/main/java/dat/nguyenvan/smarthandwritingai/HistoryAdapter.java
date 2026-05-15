package dat.nguyenvan.smarthandwritingai;

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
        holder.tvResult.setText(String.valueOf(prediction.getResult()));
        holder.tvConfidence.setText(String.format("%.1f%%", prediction.getConfidence()));
        
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        holder.tvTimestamp.setText(sdf.format(new Date(prediction.getTimestamp())));
        
        try {
            if (prediction.getImageBase64() != null && !prediction.getImageBase64().isEmpty()) {
                byte[] bytes = Base64.decode(prediction.getImageBase64(), Base64.DEFAULT);
                holder.ivImage.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
            }
        } catch (Exception e) {}

        if (prediction.getConfidence() >= 90) holder.tvConfidence.setTextColor(0xFF4CAF50);
        else if (prediction.getConfidence() >= 70) holder.tvConfidence.setTextColor(0xFFFF9800);
        else holder.tvConfidence.setTextColor(0xFFF44336);
        
        holder.btnFavorite.setImageResource(prediction.isFavorite ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
        holder.btnFavorite.setOnClickListener(v -> listener.onFavoriteClick(prediction));
        
    }

    @Override
    public int getItemCount() { return predictions != null ? predictions.size() : 0; }
    public void updateData(List<PredictionEntity> newPredictions) {
        this.predictions = newPredictions;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivImage; TextView tvResult, tvConfidence, tvTimestamp;
        ImageButton btnFavorite;
        ViewHolder(@NonNull View v) {
            super(v);
            ivImage = v.findViewById(R.id.iv_history_image);
            tvResult = v.findViewById(R.id.tv_history_result);
            tvConfidence = v.findViewById(R.id.tv_history_confidence);
            tvTimestamp = v.findViewById(R.id.tv_history_timestamp);
            btnFavorite = v.findViewById(R.id.btn_favorite);
            
        }
    }
}
