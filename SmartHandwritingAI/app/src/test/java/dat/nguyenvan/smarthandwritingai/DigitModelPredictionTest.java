package dat.nguyenvan.smarthandwritingai;

import org.junit.Test;
import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.Assert.assertTrue;

public class DigitModelPredictionTest {

    private static final String[] LABELS = {
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"
    };

    private Interpreter loadModel() {
        File modelFile = new File("src/main/assets/model.tflite");
        if (!modelFile.exists()) {
            modelFile = new File("app/src/main/assets/model.tflite");
        }
        System.out.println("Model file path: " + modelFile.getAbsolutePath() + " (exists: " + modelFile.exists() + ")");
        return new Interpreter(modelFile);
    }

    private void printTopK(float[] output, String labelPrefix) {
        System.out.println("--- Predictions for " + labelPrefix + " ---");
        // Sort
        java.util.List<Prediction> list = new java.util.ArrayList<>();
        for (int i = 0; i < output.length; i++) {
            list.add(new Prediction(LABELS[i], output[i]));
        }
        java.util.Collections.sort(list, (p1, p2) -> Float.compare(p2.confidence, p1.confidence));
        for (int i = 0; i < 5; i++) {
            Prediction p = list.get(i);
            System.out.println(String.format("  %d. %s: %.2f%%", i + 1, p.label, p.confidence * 100));
        }
    }

    private static class Prediction {
        String label;
        float confidence;
        Prediction(String l, float c) {
            this.label = l;
            this.confidence = c;
        }
    }

    private ByteBuffer toByteBuffer(float[][] grid) {
        ByteBuffer buf = ByteBuffer.allocateDirect(28 * 28 * 4);
        buf.order(ByteOrder.nativeOrder());
        buf.rewind();
        for (int r = 0; r < 28; r++) {
            for (int c = 0; c < 28; c++) {
                buf.putFloat(grid[r][c]);
            }
        }
        return buf;
    }

    @Test
    public void testOrientations() {
        Interpreter interpreter = loadModel();
        
        // 1. Synthesize a clean vertical line (which should be "1")
        float[][] normalOne = new float[28][28];
        for (int r = 4; r < 24; r++) {
            normalOne[r][14] = 1.0f;
            normalOne[r][13] = 0.8f;
            normalOne[r][15] = 0.8f;
        }

        // 2. Transposed vertical line (horizontal line)
        float[][] transposedOne = new float[28][28];
        for (int r = 0; r < 28; r++) {
            for (int c = 0; c < 28; c++) {
                transposedOne[r][c] = normalOne[c][r];
            }
        }

        // Run prediction
        float[][] output = new float[1][36];
        
        // Predict normal one
        ByteBuffer bufNormal = toByteBuffer(normalOne);
        interpreter.run(bufNormal, output);
        printTopK(output[0], "Normal Vertical Line ('1')");

        // Predict transposed one
        ByteBuffer bufTransposed = toByteBuffer(transposedOne);
        interpreter.run(bufTransposed, output);
        printTopK(output[0], "Transposed Line");

        // 3. Let's synthesize a crude '2' shape
        // Curve top: (4,10) to (4,18), (5,18) to (8,18)
        // Diagonal: (8,18) down to (20,10)
        // Base: (20,10) to (20,18)
        float[][] normalTwo = new float[28][28];
        // Top bar
        for (int c = 10; c <= 18; c++) normalTwo[5][c] = 1.0f;
        // Right top side
        for (int r = 6; r <= 10; r++) normalTwo[r][18] = 1.0f;
        // Diagonal
        for (int i = 0; i <= 10; i++) {
            normalTwo[10 + i][18 - i] = 1.0f;
        }
        // Base
        for (int c = 8; c <= 20; c++) normalTwo[20][c] = 1.0f;

        // Predict normal two
        ByteBuffer bufNormalTwo = toByteBuffer(normalTwo);
        interpreter.run(bufNormalTwo, output);
        printTopK(output[0], "Normal Upright '2'");

        // Predict transposed two
        float[][] transposedTwo = new float[28][28];
        for (int r = 0; r < 28; r++) {
            for (int c = 0; c < 28; c++) {
                transposedTwo[r][c] = normalTwo[c][r];
            }
        }
        ByteBuffer bufTransposedTwo = toByteBuffer(transposedTwo);
        interpreter.run(bufTransposedTwo, output);
        printTopK(output[0], "Transposed '2'");

        interpreter.close();
    }
}
