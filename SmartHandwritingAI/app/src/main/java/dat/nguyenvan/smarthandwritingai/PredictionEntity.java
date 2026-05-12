package dat.nguyenvan.smarthandwritingai;

import androidx.room.Entity;
import androidx.room.PrimaryKey;


@Entity(tableName = "prediction_history")
public class PredictionEntity {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String imageBase64;    
    private int result;            
    private float confidence;      
    private long timestamp;        

    public PredictionEntity(String imageBase64, int result, float confidence, long timestamp) {
        this.imageBase64 = imageBase64;
        this.result = result;
        this.confidence = confidence;
        this.timestamp = timestamp;
    }

    
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getImageBase64() { return imageBase64; }
    public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }

    public int getResult() { return result; }
    public void setResult(int result) { this.result = result; }

    public float getConfidence() { return confidence; }
    public void setConfidence(float confidence) { this.confidence = confidence; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
