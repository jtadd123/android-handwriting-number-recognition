package dat.nguyenvan.smarthandwritingai;

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
