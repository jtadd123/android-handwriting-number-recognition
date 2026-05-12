package dat.nguyenvan.smarthandwritingai;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;


@Dao
public interface PredictionDao {

    @Insert
    void insert(PredictionEntity prediction);

    @Query("SELECT * FROM prediction_history ORDER BY timestamp DESC")
    List<PredictionEntity> getAllPredictions();

    @Query("DELETE FROM prediction_history")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM prediction_history")
    int getCount();
}
