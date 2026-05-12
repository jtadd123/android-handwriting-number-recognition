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
    private static final int NUM_CLASSES = 10;

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

    
    public float[] predict(Bitmap bitmap) {
        if (!isInitialized) {
            Log.e(TAG, "Model not initialized!");
            return new float[]{-1, 0};
        }

        
        Bitmap resizedBitmap = ImageProcessor.resizeBitmap(bitmap, INPUT_SIZE, INPUT_SIZE);
        ByteBuffer inputBuffer = ImageProcessor.bitmapToByteBuffer(resizedBitmap);

        
        float[][] output = new float[1][NUM_CLASSES];

        
        interpreter.run(inputBuffer, output);

        
        int predictedDigit = 0;
        float maxConfidence = output[0][0];
        for (int i = 1; i < NUM_CLASSES; i++) {
            if (output[0][i] > maxConfidence) {
                maxConfidence = output[0][i];
                predictedDigit = i;
            }
        }

        float confidencePercent = maxConfidence * 100f;
        Log.d(TAG, "Predicted: " + predictedDigit + " | Confidence: " + confidencePercent + "%");

        return new float[]{predictedDigit, confidencePercent};
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
