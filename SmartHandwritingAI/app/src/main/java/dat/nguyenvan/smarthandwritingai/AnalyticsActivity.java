package dat.nguyenvan.smarthandwritingai;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AnalyticsActivity extends AppCompatActivity {

    private TextView tvTotalPredictions, tvAvgConfidence, tvTopCharacter;
    private PieChart pieChart;
    private BarChart barChart;
    private ExecutorService executorService;
    private BottomNavigationView bottomNavigation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analytics);

        initViews();
        setupBottomNavigation();
        loadAnalyticsData();
    }

    private void initViews() {
        tvTotalPredictions = findViewById(R.id.tv_total_predictions);
        tvAvgConfidence = findViewById(R.id.tv_avg_confidence);
        tvTopCharacter = findViewById(R.id.tv_top_character);
        pieChart = findViewById(R.id.pie_chart);
        barChart = findViewById(R.id.bar_chart);
        bottomNavigation = findViewById(R.id.bottom_navigation);
        
        findViewById(R.id.btn_back_analytics).setOnClickListener(v -> finish());
        
        executorService = Executors.newSingleThreadExecutor();
        
        setupPieChart();
        setupBarChart();
    }

    private void setupBottomNavigation() {
        bottomNavigation.setSelectedItemId(R.id.nav_settings);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_settings) {
                return true; // Already here
            } else if (id == R.id.nav_home) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return true;
            } else if (id == R.id.nav_draw) {
                startActivity(new Intent(this, DrawActivity.class));
                return true;
            } else if (id == R.id.nav_history) {
                startActivity(new Intent(this, HistoryActivity.class));
                finish();
                return true;
            }
            return false;
        });
    }

    private void setupPieChart() {
        pieChart.setUsePercentValues(true);
        pieChart.getDescription().setEnabled(false);
        pieChart.setExtraOffsets(5, 10, 5, 5);
        pieChart.setDragDecelerationFrictionCoef(0.95f);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.TRANSPARENT);
        pieChart.setTransparentCircleColor(Color.WHITE);
        pieChart.setTransparentCircleAlpha(110);
        pieChart.setHoleRadius(58f);
        pieChart.setTransparentCircleRadius(61f);
        pieChart.setDrawCenterText(true);
        pieChart.setRotationAngle(0);
        pieChart.setRotationEnabled(true);
        pieChart.getLegend().setTextColor(Color.WHITE);
    }

    private void setupBarChart() {
        barChart.getDescription().setEnabled(false);
        barChart.setDrawGridBackground(false);
        barChart.getAxisRight().setEnabled(false);
        barChart.getAxisLeft().setTextColor(Color.WHITE);
        barChart.getXAxis().setTextColor(Color.WHITE);
        barChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        barChart.getXAxis().setDrawGridLines(false);
        barChart.getLegend().setTextColor(Color.WHITE);
    }

    private void loadAnalyticsData() {
        executorService.execute(() -> {
            List<PredictionEntity> predictions = AppDatabase.getInstance(this).predictionDao().getAllPredictions();
            
            if (predictions == null || predictions.isEmpty()) return;

            int total = predictions.size();
            float totalConfidence = 0;
            Map<String, Integer> characterCounts = new HashMap<>();

            for (PredictionEntity entity : predictions) {
                totalConfidence += entity.getConfidence();
                String label = entity.getResult();
                characterCounts.put(label, characterCounts.getOrDefault(label, 0) + 1);
            }

            float avgConfidence = totalConfidence / total;
            
            // Find top character
            String topChar = "-";
            int maxCount = 0;
            for (Map.Entry<String, Integer> entry : characterCounts.entrySet()) {
                if (entry.getValue() > maxCount) {
                    maxCount = entry.getValue();
                    topChar = entry.getKey();
                }
            }

            // Update UI with calculated stats
            final String finalTopChar = topChar;
            runOnUiThread(() -> {
                tvTotalPredictions.setText(String.valueOf(total));
                tvAvgConfidence.setText(String.format("%.1f%%", avgConfidence));
                tvTopCharacter.setText(finalTopChar);
                
                updateCharts(characterCounts, total);
            });
        });
    }

    private void updateCharts(Map<String, Integer> characterCounts, int total) {
        ArrayList<PieEntry> pieEntries = new ArrayList<>();
        ArrayList<BarEntry> barEntries = new ArrayList<>();
        ArrayList<String> xLabels = new ArrayList<>();

        int[] colors = new int[] {
            Color.parseColor("#FF58A6FF"), // accent
            Color.parseColor("#FF3FB950"), // success
            Color.parseColor("#FFD29922"), // warning
            Color.parseColor("#FF6C8EEF"), // primary
            Color.parseColor("#FFBC8CFF")  // purple
        };
        ArrayList<Integer> chartColors = new ArrayList<>();

        int index = 0;
        for (Map.Entry<String, Integer> entry : characterCounts.entrySet()) {
            pieEntries.add(new PieEntry((float) entry.getValue() / total, entry.getKey()));
            barEntries.add(new BarEntry(index, entry.getValue()));
            xLabels.add(entry.getKey());
            chartColors.add(colors[index % colors.length]);
            index++;
        }

        // --- Pie Chart ---
        PieDataSet pieDataSet = new PieDataSet(pieEntries, getString(R.string.chart_label_character));
        pieDataSet.setColors(chartColors);
        pieDataSet.setSliceSpace(3f);
        pieDataSet.setSelectionShift(5f);

        PieData pieData = new PieData(pieDataSet);
        pieData.setValueFormatter(new PercentFormatter(pieChart));
        pieData.setValueTextSize(11f);
        pieData.setValueTextColor(Color.WHITE);
        
        pieChart.setData(pieData);
        pieChart.invalidate();

        // --- Bar Chart ---
        BarDataSet barDataSet = new BarDataSet(barEntries, getString(R.string.chart_label_recognition_count));
        barDataSet.setColors(chartColors);
        barDataSet.setValueTextColor(Color.WHITE);
        barDataSet.setValueTextSize(10f);

        BarData barData = new BarData(barDataSet);
        
        barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(xLabels));
        barChart.getXAxis().setGranularity(1f);
        
        barChart.setData(barData);
        barChart.invalidate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
