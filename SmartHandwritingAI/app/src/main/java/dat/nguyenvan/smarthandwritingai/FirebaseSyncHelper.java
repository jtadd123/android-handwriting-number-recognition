package dat.nguyenvan.smarthandwritingai;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class FirebaseSyncHelper {

    private static final String TAG = "FirebaseSyncHelper";

    public interface OnSyncCompleteListener {
        void onSuccess(String imageUrl);
        void onFailure(Exception e);
    }

    public interface OnSyncAllListener {
        void onProgress(int current, int total);
        void onComplete(int successCount, int totalCount);
    }

    /**
     * Đồng bộ một kết quả nhận diện lên Firebase (Storage + Firestore)
     */
    public static void syncPrediction(Context context, PredictionEntity entity, OnSyncCompleteListener listener) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (listener != null) listener.onFailure(new Exception("Chưa đăng nhập tài khoản Firebase."));
            return;
        }

        String email = user.getEmail();
        String username = "guest";
        if (email != null && email.contains("@")) {
            username = email.substring(0, email.indexOf("@"));
        } else {
            username = user.getUid();
        }

        final String finalUsername = username;
        final String documentId = String.valueOf(entity.getTimestamp());

        // Nếu đã có imageUrl (đã upload Storage trước đó), chỉ lưu Firestore
        if (entity.getImageUrl() != null && !entity.getImageUrl().isEmpty()) {
            saveToFirestore(context, entity, entity.getImageUrl(), finalUsername, documentId, listener);
            return;
        }

        if (entity.getImageBase64() == null || entity.getImageBase64().isEmpty()) {
            if (listener != null) listener.onFailure(new Exception("Dữ liệu ảnh trống."));
            return;
        }

        try {
            byte[] decodedString = Base64.decode(entity.getImageBase64(), Base64.DEFAULT);
            FirebaseStorage storage = FirebaseStorage.getInstance();
            StorageReference storageRef = storage.getReference();
            StorageReference imageRef = storageRef.child("history_images/" + finalUsername + "/" + documentId + ".png");

            UploadTask uploadTask = imageRef.putBytes(decodedString);
            uploadTask.addOnSuccessListener(taskSnapshot -> {
                imageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    String imageUrl = uri.toString();
                    saveToFirestore(context, entity, imageUrl, finalUsername, documentId, listener);
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "Lỗi lấy download URL (chuyển sang lưu Firestore không ảnh): " + e.getMessage());
                    saveToFirestore(context, entity, "", finalUsername, documentId, listener);
                });
            }).addOnFailureListener(e -> {
                Log.e(TAG, "Lỗi upload ảnh lên Storage (chuyển sang lưu Firestore không ảnh): " + e.getMessage());
                saveToFirestore(context, entity, "", finalUsername, documentId, listener);
            });
        } catch (Exception e) {
            Log.e(TAG, "Lỗi xử lý upload (chuyển sang lưu Firestore không ảnh): " + e.getMessage());
            saveToFirestore(context, entity, "", finalUsername, documentId, listener);
        }
    }

    private static void saveToFirestore(Context context, PredictionEntity entity, String imageUrl,
                                        String username, String documentId, OnSyncCompleteListener listener) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> data = new HashMap<>();
        data.put("result", entity.getResult());
        data.put("confidence", entity.getConfidence());
        data.put("timestamp", entity.getTimestamp());
        data.put("isFavorite", entity.isFavorite);
        data.put("imageUrl", imageUrl != null ? imageUrl : "");

        db.collection("users").document(username)
                .collection("history").document(documentId)
                .set(data)
                .addOnSuccessListener(aVoid -> {
                    entity.setImageUrl(imageUrl);
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    executor.execute(() -> {
                        AppDatabase.getInstance(context).predictionDao().update(entity);
                        executor.shutdown();
                        if (listener != null) {
                            listener.onSuccess(imageUrl);
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Lỗi lưu document Firestore: " + e.getMessage());
                    if (listener != null) listener.onFailure(e);
                });
    }

    /**
     * Đồng bộ toàn bộ dữ liệu lịch sử từ SQLite lên Firebase
     */
    public static void syncAll(Context context, OnSyncAllListener listener) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (listener != null) listener.onComplete(0, 0);
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<PredictionEntity> all = AppDatabase.getInstance(context).predictionDao().getAllPredictions();
            executor.shutdown();

            if (all == null || all.isEmpty()) {
                if (listener != null) listener.onComplete(0, 0);
                return;
            }

            int total = all.size();
            AtomicInteger current = new AtomicInteger(0);
            AtomicInteger successCount = new AtomicInteger(0);

            for (PredictionEntity p : all) {
                syncPrediction(context, p, new OnSyncCompleteListener() {
                    @Override
                    public void onSuccess(String imageUrl) {
                        successCount.incrementAndGet();
                        int done = current.incrementAndGet();
                        if (listener != null) {
                            listener.onProgress(done, total);
                        }
                        if (done == total) {
                            listener.onComplete(successCount.get(), total);
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        int done = current.incrementAndGet();
                        if (listener != null) {
                            listener.onProgress(done, total);
                        }
                        if (done == total) {
                            listener.onComplete(successCount.get(), total);
                        }
                    }
                });
            }
        });
    }
}
