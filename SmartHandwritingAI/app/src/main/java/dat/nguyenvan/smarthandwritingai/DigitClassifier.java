package dat.nguyenvan.smarthandwritingai;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;


public class DigitClassifier {

    private static final String TAG = "DigitClassifier";
    private static final String MODEL_FILENAME = "model.tflite";
    private static final int INPUT_SIZE = 28;
    private static final int NUM_CLASSES = 36;

    // Mapping cho 36 classes: 0-9 và A-Z
    private static final String[] LABELS = {
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"
    };

    private Interpreter interpreter;
    private boolean isInitialized = false;

    public DigitClassifier(Context context) {
        try {
            File localModel = new File(context.getFilesDir(), MODEL_FILENAME);
            if (localModel.exists()) {
                Log.d(TAG, "Loading model from local storage: " + localModel.getAbsolutePath());
                FileInputStream fis = new FileInputStream(localModel);
                FileChannel fileChannel = fis.getChannel();
                MappedByteBuffer buffer = fileChannel.map(
                        FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
                interpreter = new Interpreter(buffer);
                fis.close();
            } else {
                Log.d(TAG, "Loading model from assets");
                MappedByteBuffer modelBuffer = loadModelFromAssets(context);
                interpreter = new Interpreter(modelBuffer);
            }

            // In thông số model để debug
            int[] inputShape = interpreter.getInputTensor(0).shape();
            int[] outputShape = interpreter.getOutputTensor(0).shape();
            Log.i(TAG, "Model Loaded! Input Shape: " + java.util.Arrays.toString(inputShape));
            Log.i(TAG, "Model Loaded! Output Shape: " + java.util.Arrays.toString(outputShape));

            isInitialized = true;
            Log.d(TAG, "Model loaded successfully!");
        } catch (IOException e) {
            Log.e(TAG, "Error loading model: " + e.getMessage());
            isInitialized = false;
        }
    }

    private MappedByteBuffer loadModelFromAssets(Context context) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_FILENAME);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        MappedByteBuffer buffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        inputStream.close();
        fileDescriptor.close();
        return buffer;
    }

    public PredictionResult predict(Bitmap bitmap) {
        if (!isInitialized) {
            Log.e(TAG, "Model not initialized!");
            return new PredictionResult("?", 0);
        }

        try {
            // Preprocess image
            Bitmap resizedBitmap = ImageProcessor.resizeBitmap(bitmap, INPUT_SIZE, INPUT_SIZE);
            ByteBuffer inputBuffer = ImageProcessor.bitmapToByteBuffer(resizedBitmap);

            // DEBUG: In ma trận ảnh ra Logcat để kiểm tra hướng
            StringBuilder sb = new StringBuilder("\n--- AI VIEW ---\n");
            inputBuffer.rewind();
            for (int i = 0; i < INPUT_SIZE; i++) {
                for (int j = 0; j < INPUT_SIZE; j++) {
                    float val = inputBuffer.getFloat();
                    sb.append(val > 0.5 ? "#" : ".");
                }
                sb.append("\n");
            }
            Log.d(TAG, sb.toString());
            inputBuffer.rewind(); // Reset để run model
            
            float[][] output = new float[1][NUM_CLASSES];
            interpreter.run(inputBuffer, output);

            // Tìm Top 3 để debug
            for (int k = 0; k < 3; k++) {
                float max = -1;
                int idx = -1;
                for (int i = 0; i < NUM_CLASSES; i++) {
                    if (output[0][i] > max) {
                        max = output[0][i];
                        idx = i;
                    }
                }
                if (idx != -1) {
                    Log.i(TAG, "Top " + (k+1) + ": " + LABELS[idx] + " (" + (max*100) + "%)");
                    output[0][idx] = -1; // Đánh dấu để tìm cái tiếp theo
                }
            }

            // Lấy lại kết quả cao nhất
            interpreter.run(inputBuffer, output); // Reset output và chạy lại
            int maxIndex = 0;
            float maxConfidence = -1;
            for (int i = 0; i < NUM_CLASSES; i++) {
                if (output[0][i] > maxConfidence) {
                    maxConfidence = output[0][i];
                    maxIndex = i;
                }
            }

            String label = LABELS[maxIndex];
            return new PredictionResult(label, maxConfidence * 100f);
        } catch (Exception e) {
            Log.e(TAG, "Lỗi thực thi Model: " + e.getMessage(), e);
            throw e;
        }
    }

    public static class PredictionResult {
        public final String label;
        public final float confidence;

        public PredictionResult(String label, float confidence) {
            this.label = label;
            this.confidence = confidence;
        }
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
            isInitialized = false;
        }
    }
}
