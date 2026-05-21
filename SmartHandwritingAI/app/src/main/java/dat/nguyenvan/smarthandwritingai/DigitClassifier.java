package dat.nguyenvan.smarthandwritingai;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class DigitClassifier {

    private static final String TAG = "DigitClassifier";
    private static final String MODEL_PATH = "model.tflite";
    private static final int NUM_CLASSES = 36;

    private static final String[] LABELS = {
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"
    };

    private Interpreter interpreter;
    private boolean isInitialized = false;

    public DigitClassifier(Context context) {
        try {
            initializeInterpreter(context);
            isInitialized = true;
        } catch (Throwable e) {
            Log.e(TAG, "Lỗi khi load mô hình: " + e.getMessage());
        }
    }

    private void initializeInterpreter(Context context) throws IOException {
        MappedByteBuffer model = loadModelFile(context);
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);
        interpreter = new Interpreter(model, options);
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_PATH);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public PredictionResult predict(Bitmap bitmap) {
        if (!isInitialized) return null;

        ByteBuffer inputBuffer = ImageProcessor.preprocessImage(bitmap);
        float[][] output = new float[1][NUM_CLASSES];

        interpreter.run(inputBuffer, output);

        // Debug: Log tensor info and top predictions
        Log.d(TAG, "[DEBUG] Input buffer size: " + inputBuffer.capacity() + " bytes");
        float maxConf = 0;
        int maxIdx = 0;
        for (int i = 0; i < output[0].length; i++) {
            if (output[0][i] > maxConf) {
                maxConf = output[0][i];
                maxIdx = i;
            }
        }
        Log.d(TAG, "[DEBUG] Top prediction: index=" + maxIdx 
            + " label=" + (maxIdx < LABELS.length ? LABELS[maxIdx] : "?") 
            + " confidence=" + String.format("%.2f%%", maxConf * 100));

        return getBestPrediction(output[0]);
    }

    private PredictionResult getBestPrediction(float[] probabilities) {
        java.util.List<PredictionItem> allPredictions = new java.util.ArrayList<>();
        for (int i = 0; i < probabilities.length; i++) {
            allPredictions.add(new PredictionItem(LABELS[i], probabilities[i] * 100));
        }
        java.util.Collections.sort(allPredictions);

        PredictionItem top1 = allPredictions.get(0);
        PredictionItem[] topK = new PredictionItem[3];
        for (int i = 0; i < 3; i++) {
            topK[i] = allPredictions.get(i);
        }

        return new PredictionResult(top1.label, top1.confidence, topK);
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
        }
    }

    public static class PredictionResult {
        public final String label;
        public final float confidence;
        public final PredictionItem[] topK;

        public PredictionResult(String label, float confidence, PredictionItem[] topK) {
            this.label = label;
            this.confidence = confidence;
            this.topK = topK;
        }
    }

    public static class PredictionItem implements Comparable<PredictionItem> {
        public final String label;
        public final float confidence;

        public PredictionItem(String label, float confidence) {
            this.label = label;
            this.confidence = confidence;
        }

        @Override
        public int compareTo(PredictionItem o) {
            return Float.compare(o.confidence, this.confidence);
        }
    }
}
