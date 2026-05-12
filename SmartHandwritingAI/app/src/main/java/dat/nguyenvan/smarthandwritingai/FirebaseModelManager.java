package dat.nguyenvan.smarthandwritingai;

import android.content.Context;
import android.util.Log;

import java.io.File;


public class FirebaseModelManager {

    private static final String TAG = "FirebaseModelManager";
    private static final String MODEL_FILENAME = "model.tflite";
    
    private static final String FIREBASE_MODEL_PATH = "models/model.tflite";

    
    public interface OnModelDownloadListener {
        void onSuccess(File modelFile);
        void onFailure(String error);
    }

    
    public static void downloadModel(Context context, OnModelDownloadListener listener) {
        File localFile = new File(context.getFilesDir(), MODEL_FILENAME);

        

        
        Log.w(TAG, "Firebase not configured. Using model from assets.");
        if (listener != null) {
            listener.onFailure("Firebase not configured. Using model from assets.");
        }
    }

    
    public static boolean hasLocalModel(Context context) {
        File localFile = new File(context.getFilesDir(), MODEL_FILENAME);
        return localFile.exists() && localFile.length() > 0;
    }

    
    public static boolean deleteLocalModel(Context context) {
        File localFile = new File(context.getFilesDir(), MODEL_FILENAME);
        if (localFile.exists()) {
            return localFile.delete();
        }
        return false;
    }
}
