package dat.nguyenvan.smarthandwritingai;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;


public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private List<PredictionEntity> predictions;

    public HistoryAdapter(List<PredictionEntity> predictions) {
        this.predictions = predictions;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PredictionEntity prediction = predictions.get(position);

        
        holder.tvResult.setText(String.valueOf(prediction.getResult()));
        holder.tvConfidence.setText(String.format("%.1f%%", prediction.getConfidence()));

        
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        String dateStr = sdf.format(new Date(prediction.getTimestamp()));
        holder.tvTimestamp.setText(dateStr);

        
        try {
            String base64 = prediction.getImageBase64();
            if (base64 != null && !base64.isEmpty()) {
                byte[] imageBytes = Base64.decode(base64, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                holder.ivImage.setImageBitmap(bitmap);
            }
        } catch (Exception e) {
            holder.ivImage.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        
        float confidence = prediction.getConfidence();
        if (confidence >= 90) {
            holder.tvConfidence.setTextColor(0xFF4CAF50); 
        } else if (confidence >= 70) {
            holder.tvConfidence.setTextColor(0xFFFF9800); 
        } else {
            holder.tvConfidence.setTextColor(0xFFF44336); 
        }
    }

    @Override
    public int getItemCount() {
        return predictions != null ? predictions.size() : 0;
    }

    public void updateData(List<PredictionEntity> newPredictions) {
        this.predictions = newPredictions;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivImage;
        TextView tvResult;
        TextView tvConfidence;
        TextView tvTimestamp;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.iv_history_image);
            tvResult = itemView.findViewById(R.id.tv_history_result);
            tvConfidence = itemView.findViewById(R.id.tv_history_confidence);
            tvTimestamp = itemView.findViewById(R.id.tv_history_timestamp);
        }
    }
}
