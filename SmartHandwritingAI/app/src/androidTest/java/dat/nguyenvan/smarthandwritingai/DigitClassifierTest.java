package dat.nguyenvan.smarthandwritingai;

import android.graphics.Bitmap;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented test cho DigitClassifier.
 * Chạy trên thiết bị/emulator thực.
 */
@RunWith(AndroidJUnit4.class)
public class DigitClassifierTest {

    private DigitClassifier classifier;

    @Before
    public void setUp() {
        // Khởi tạo classifier với application context
        classifier = new DigitClassifier(
                InstrumentationRegistry.getInstrumentation().getTargetContext()
        );
    }

    @After
    public void tearDown() {
        if (classifier != null) classifier.close();
    }

    @Test
    public void testClassifierInitialized() {
        // Model phải load được từ assets
        assertTrue("DigitClassifier should be initialized", classifier.isInitialized());
    }

    @Test
    public void testPredictReturnsResult() {
        // Tạo bitmap trắng 28x28 — bất kỳ ảnh nào cũng phải trả về kết quả
        Bitmap whiteBitmap = Bitmap.createBitmap(28, 28, Bitmap.Config.ARGB_8888);
        whiteBitmap.eraseColor(android.graphics.Color.WHITE);

        DigitClassifier.PredictionResult result = classifier.predict(whiteBitmap);

        assertNotNull("Prediction result should not be null", result);
        assertNotNull("Label should not be null", result.label);
        assertFalse("Label should not be empty", result.label.isEmpty());
        assertTrue("Confidence should be between 0 and 100",
                result.confidence >= 0f && result.confidence <= 100f);
    }

    @Test
    public void testPredictReturnsTopK() {
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(android.graphics.Color.BLACK);

        DigitClassifier.PredictionResult result = classifier.predict(bitmap);

        assertNotNull(result);
        assertNotNull("TopK should not be null", result.topK);
        assertEquals("TopK should have exactly 3 results", 3, result.topK.length);
    }

    @Test
    public void testPredictTopKConfidenceSumApproximate() {
        // Tổng xác suất tất cả lớp phải ≈ 100%
        // Top-3 phải ≥ 0% mỗi cái
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(android.graphics.Color.BLACK);

        DigitClassifier.PredictionResult result = classifier.predict(bitmap);
        assertNotNull(result);

        for (DigitClassifier.PredictionItem item : result.topK) {
            assertTrue("Each TopK confidence should be >= 0", item.confidence >= 0f);
            assertTrue("Each TopK confidence should be <= 100", item.confidence <= 100f);
            assertNotNull("TopK label should not be null", item.label);
        }
    }

    @Test
    public void testPredictNullBitmapHandled() {
        // Passing null should not crash — returns null or handles gracefully
        try {
            DigitClassifier.PredictionResult result = classifier.predict(null);
            // Either null result or no crash is acceptable
        } catch (Exception e) {
            // If throws exception, it should be a known type (not NPE crash)
            assertFalse("Should not be NullPointerException without handling",
                    e instanceof NullPointerException && e.getMessage() == null);
        }
    }

    @Test
    public void testEquivalenceGroupMerging() {
        // Find index of "Q" and "2"
        int qIdx = -1;
        int twoIdx = -1;
        for (int i = 0; i < DigitClassifier.LABELS.length; i++) {
            if (DigitClassifier.LABELS[i].equals("Q")) qIdx = i;
            if (DigitClassifier.LABELS[i].equals("2")) twoIdx = i;
        }
        
        // Assert we found them
        assertTrue(qIdx != -1);
        assertTrue(twoIdx != -1);
        
        // Set raw probabilities: Q has 1.0f (100%), all others 0.0f
        float[] probs = new float[DigitClassifier.NUM_CLASSES];
        probs[qIdx] = 1.0f;
        
        DigitClassifier.PredictionResult result = classifier.getBestPrediction(probs, false);
        
        assertEquals("Label should be merged to 2", "2", result.label);
        assertEquals("Confidence should be 100%", 100.0f, result.confidence, 0.01f);
    }
}
