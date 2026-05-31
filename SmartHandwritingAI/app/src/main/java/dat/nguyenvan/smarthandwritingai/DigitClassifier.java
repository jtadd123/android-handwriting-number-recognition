package dat.nguyenvan.smarthandwritingai;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import com.google.firebase.ml.modeldownloader.CustomModel;
import com.google.firebase.ml.modeldownloader.CustomModelDownloadConditions;
import com.google.firebase.ml.modeldownloader.DownloadType;
import com.google.firebase.ml.modeldownloader.FirebaseModelDownloader;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class DigitClassifier {

    private static final String TAG = "DigitClassifier";
    private static final String MODEL_PATH = "model.tflite";
    static final int NUM_CLASSES = 36;

    static final String[] LABELS = {
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"
    };

    enum PredictionMode {
        ALL_CLASSES,
        MATH
    }

    private static final String[][] EQUIVALENCE_GROUPS = {
            {"0", "O"},
            {"1", "I", "L"},
            {"2", "Z", "Q"},
            {"5", "S"},
            {"8", "B"},
            {"9", "G"}
    };

    private Interpreter interpreter;
    private boolean isInitialized = false;

    public DigitClassifier(Context context) {
        try {
            // Bước 1: Khởi tạo mô hình mặc định từ assets để chạy offline (Fallback)
            initializeInterpreter(context);
            isInitialized = true;
            Log.d(TAG, "Đã khởi tạo mô hình fallback thành công từ assets.");
            
            // Bước 2: Bất đồng bộ kiểm tra và tải mô hình mới từ Firebase ML Cloud
            checkForModelUpdate(context);
        } catch (Throwable e) {
            Log.e(TAG, "Lỗi khi load mô hình fallback: " + e.getMessage());
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

    private void checkForModelUpdate(Context context) {
        CustomModelDownloadConditions conditions = new CustomModelDownloadConditions.Builder()
                .build(); // Tải qua bất kỳ mạng nào để kiểm tra nhanh

        Log.d(TAG, "Đang kiểm tra cập nhật mô hình AI từ Firebase ML Cloud...");
        FirebaseModelDownloader.getInstance()
                .getModel("HandwritingModel", DownloadType.LATEST_MODEL, conditions)
                .addOnSuccessListener(customModel -> {
                    java.io.File modelFile = customModel.getFile();
                    if (modelFile != null) {
                        Log.d(TAG, "Đã tải thành công mô hình từ Firebase ML: " + modelFile.getAbsolutePath());
                        try {
                            Interpreter.Options options = new Interpreter.Options();
                            options.setNumThreads(4);
                            Interpreter newInterpreter = new Interpreter(modelFile, options);

                            // Đồng bộ hóa để thay thế interpreter cũ
                            synchronized (this) {
                                if (interpreter != null) {
                                    interpreter.close();
                                }
                                interpreter = newInterpreter;
                                isInitialized = true;
                            }
                            Log.d(TAG, "Đã cập nhật và nạp thành công mô hình AI mới từ Firebase Cloud!");
                        } catch (Exception e) {
                            Log.e(TAG, "Lỗi nạp mô hình đã tải từ Firebase: " + e.getMessage());
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Không thể tải mô hình từ Firebase ML: " + e.getMessage() + ". Tiếp tục sử dụng mô hình fallback.");
                });
    }

    public PredictionResult predict(Bitmap bitmap) {
        return predict(bitmap, false, false);
    }

    public PredictionResult predict(Bitmap bitmap, boolean isMathModeOnly) {
        return predict(bitmap, isMathModeOnly, false);
    }

    public PredictionResult predict(Bitmap bitmap, boolean isMathModeOnly, boolean isPreprocessed) {
        return predict(bitmap, isMathModeOnly ? PredictionMode.MATH : PredictionMode.ALL_CLASSES, isPreprocessed);
    }

    public PredictionResult predict(Bitmap bitmap, PredictionMode mode, boolean isPreprocessed) {
        if (!isInitialized) return null;

        ByteBuffer inputBuffer;
        if (isPreprocessed) {
            inputBuffer = ImageProcessor.convertPreprocessedBitmapToByteBuffer(bitmap);
        } else {
            inputBuffer = ImageProcessor.preprocessImage(bitmap);
        }
        float[] inputPixels = readInputPixels(inputBuffer);
        float[][] output = new float[1][NUM_CLASSES];

        synchronized (this) {
            if (interpreter != null) {
                interpreter.run(inputBuffer, output);
            } else {
                return null;
            }
        }

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

        return getBestPrediction(output[0], mode, inputPixels);
    }

    PredictionResult getBestPrediction(float[] probabilities, boolean isMathModeOnly) {
        return getBestPrediction(probabilities, isMathModeOnly ? PredictionMode.MATH : PredictionMode.ALL_CLASSES);
    }

    static PredictionResult getBestPrediction(float[] probabilities, PredictionMode mode) {
        return getBestPrediction(probabilities, mode, null);
    }

    static PredictionResult getBestPrediction(float[] probabilities, PredictionMode mode, float[] inputPixels) {
        float[] mergedProbabilities = probabilities.clone();
        if (mode == PredictionMode.MATH) {
            for (String[] group : EQUIVALENCE_GROUPS) {
                mergeGroup(mergedProbabilities, group);
            }
        }

        if (mode == PredictionMode.MATH) {
            // Filter: Zero out non-math classes.
            // Allowed classes: "0"-"9", "X", "D".
            for (int i = 0; i < LABELS.length; i++) {
                String label = LABELS[i];
                boolean isDigit = label.length() == 1 && Character.isDigit(label.charAt(0));
                boolean isValidMathChar = isDigit || label.equals("X") || label.equals("D");
                if (!isValidMathChar) {
                    mergedProbabilities[i] = 0f;
                }
            }
        }

        java.util.List<PredictionItem> allPredictions = new java.util.ArrayList<>();
        for (int i = 0; i < mergedProbabilities.length; i++) {
            allPredictions.add(new PredictionItem(LABELS[i], mergedProbabilities[i] * 100));
        }
        java.util.Collections.sort(allPredictions);

        if (inputPixels != null) {
            applyShapeDisambiguation(allPredictions, inputPixels);
            java.util.Collections.sort(allPredictions);
        }

        PredictionItem top1 = allPredictions.get(0);
        PredictionItem[] topK = new PredictionItem[3];
        for (int i = 0; i < 3; i++) {
            topK[i] = allPredictions.get(i);
        }

        return new PredictionResult(top1.label, top1.confidence, topK);
    }

    private static float[] readInputPixels(ByteBuffer inputBuffer) {
        ByteBuffer copy = inputBuffer.duplicate();
        copy.order(inputBuffer.order());
        copy.rewind();
        float[] pixels = new float[INPUT_PIXEL_COUNT];
        for (int i = 0; i < pixels.length && copy.remaining() >= 4; i++) {
            pixels[i] = copy.getFloat();
        }
        inputBuffer.rewind();
        return pixels;
    }

    private static final int INPUT_PIXEL_COUNT = 28 * 28;

    private static void applyShapeDisambiguation(java.util.List<PredictionItem> predictions, float[] pixels) {
        PredictionItem top = predictions.get(0);
        if ((top.label.equals("3") || top.label.equals("E")) && isNineLike(pixels)) {
            boostLabel(predictions, "9", top.confidence + 0.01f);
        } else if ((top.label.equals("G") || top.label.equals("Q") || top.label.equals("Z")) && isEightLike(pixels)) {
            boostLabel(predictions, "8", top.confidence + 0.01f);
        } else if (top.label.equals("E") && isThreeLike(pixels)) {
            boostLabel(predictions, "3", top.confidence + 0.01f);
        } else if (top.label.equals("F") && isTwoLike(pixels)) {
            boostLabel(predictions, "2", top.confidence + 0.01f);
        } else if (top.label.equals("D") && isSixLike(pixels)) {
            boostLabel(predictions, "6", top.confidence + 0.01f);
        } else if (top.label.equals("P") && isFourLike(pixels)) {
            boostLabel(predictions, "4", top.confidence + 0.01f);
        } else if (top.label.equals("J") && isFiveLike(pixels)) {
            boostLabel(predictions, "5", top.confidence + 0.01f);
        } else if (top.label.equals("J") && isSevenLike(pixels)) {
            boostLabel(predictions, "7", top.confidence + 0.01f);
        }
    }

    private static void boostLabel(java.util.List<PredictionItem> predictions, String label, float confidence) {
        for (int i = 0; i < predictions.size(); i++) {
            PredictionItem item = predictions.get(i);
            if (item.label.equals(label)) {
                predictions.set(i, new PredictionItem(label, confidence));
                return;
            }
        }
    }

    private static boolean isThreeLike(float[] pixels) {
        float total = inkInRect(pixels, 0, 27, 0, 27);
        if (total < 12f) return false;

        float leftThird = inkInRect(pixels, 0, 8, 3, 24);
        float rightHalf = inkInRect(pixels, 14, 27, 3, 24);
        float topBand = inkInRect(pixels, 8, 22, 3, 7);
        float midBand = inkInRect(pixels, 8, 22, 12, 16);
        float bottomBand = inkInRect(pixels, 8, 22, 20, 24);

        boolean weakLeftStem = leftThird < total * 0.18f;
        boolean strongRightSide = rightHalf > total * 0.38f;
        boolean hasThreeBands = topBand > total * 0.08f && midBand > total * 0.08f && bottomBand > total * 0.08f;
        return weakLeftStem && strongRightSide && hasThreeBands;
    }

    private static boolean isEightLike(float[] pixels) {
        float total = inkInRect(pixels, 0, 27, 0, 27);
        if (total < 16f) return false;

        float leftSide = inkInRect(pixels, 7, 12, 5, 23);
        float rightSide = inkInRect(pixels, 17, 22, 5, 23);
        float topBand = inkInRect(pixels, 9, 20, 3, 8);
        float middleBand = inkInRect(pixels, 9, 20, 12, 16);
        float bottomBand = inkInRect(pixels, 9, 20, 19, 24);
        float upperLoop = inkInRect(pixels, 8, 21, 4, 13);
        float lowerLoop = inkInRect(pixels, 8, 21, 14, 24);

        boolean hasBothSides = leftSide > total * 0.16f && rightSide > total * 0.16f;
        boolean hasThreeBands = topBand > total * 0.08f && middleBand > total * 0.08f && bottomBand > total * 0.08f;
        boolean balancedLoops = upperLoop > total * 0.22f && lowerLoop > total * 0.22f;
        return hasBothSides && hasThreeBands && balancedLoops;
    }

    private static boolean isNineLike(float[] pixels) {
        float total = inkInRect(pixels, 0, 27, 0, 27);
        if (total < 14f) return false;

        float upperLeft = inkInRect(pixels, 7, 12, 4, 14);
        float upperRight = inkInRect(pixels, 17, 22, 4, 14);
        float lowerLeft = inkInRect(pixels, 6, 12, 15, 24);
        float lowerRight = inkInRect(pixels, 17, 23, 13, 24);
        float topBand = inkInRect(pixels, 8, 21, 3, 8);
        float middleBand = inkInRect(pixels, 8, 21, 12, 16);

        boolean hasUpperLoop = upperLeft > total * 0.10f && upperRight > total * 0.10f;
        boolean hasTopAndMiddle = topBand > total * 0.10f && middleBand > total * 0.10f;
        boolean rightTailDominates = lowerRight > total * 0.12f && lowerLeft < lowerRight * 0.75f;
        return hasUpperLoop && hasTopAndMiddle && rightTailDominates;
    }

    private static boolean isTwoLike(float[] pixels) {
        float total = inkInRect(pixels, 0, 27, 0, 27);
        if (total < 12f) return false;

        float topBand = inkInRect(pixels, 7, 22, 3, 7);
        float upperRight = inkInRect(pixels, 17, 23, 6, 12);
        float diagonal = diagonalInk(pixels, 20, 10, 9, 21);
        float bottomBand = inkInRect(pixels, 7, 22, 20, 24);
        float leftStem = inkInRect(pixels, 5, 10, 5, 17);

        boolean hasTopAndBottom = topBand > total * 0.14f && bottomBand > total * 0.14f;
        boolean hasTurnAndDiagonal = upperRight > total * 0.08f && diagonal > total * 0.18f;
        boolean notFStem = leftStem < total * 0.20f;
        return hasTopAndBottom && hasTurnAndDiagonal && notFStem;
    }

    private static boolean isSixLike(float[] pixels) {
        float total = inkInRect(pixels, 0, 27, 0, 27);
        if (total < 12f) return false;

        float leftSide = inkInRect(pixels, 6, 12, 5, 23);
        float upperRight = inkInRect(pixels, 17, 24, 5, 12);
        float lowerRight = inkInRect(pixels, 17, 24, 14, 23);
        float middleBand = inkInRect(pixels, 8, 21, 12, 16);
        float bottomBand = inkInRect(pixels, 8, 21, 20, 24);

        boolean hasLeftSpine = leftSide > total * 0.20f;
        boolean lowerLoopDominates = lowerRight > total * 0.10f && upperRight < lowerRight * 0.80f;
        boolean hasLoopBands = middleBand > total * 0.10f && bottomBand > total * 0.10f;
        return hasLeftSpine && lowerLoopDominates && hasLoopBands;
    }

    private static boolean isFourLike(float[] pixels) {
        float total = inkInRect(pixels, 0, 27, 0, 27);
        if (total < 12f) return false;

        float middleBand = inkInRect(pixels, 7, 22, 12, 16);
        float rightLower = inkInRect(pixels, 16, 22, 15, 24);
        float upperRightBowl = inkInRect(pixels, 16, 23, 5, 12);
        float diagonal = diagonalInk(pixels, 8, 5, 17, 14);

        boolean hasCrossbar = middleBand > total * 0.16f;
        boolean hasRightLeg = rightLower > total * 0.15f;
        boolean hasDiagonal = diagonal > total * 0.12f;
        boolean notUpperBowlOnly = rightLower > upperRightBowl * 0.55f;
        return hasCrossbar && hasRightLeg && hasDiagonal && notUpperBowlOnly;
    }

    private static boolean isFiveLike(float[] pixels) {
        float total = inkInRect(pixels, 0, 27, 0, 27);
        if (total < 12f) return false;

        float topBand = inkInRect(pixels, 7, 22, 3, 7);
        float middleBand = inkInRect(pixels, 7, 22, 12, 16);
        float bottomBand = inkInRect(pixels, 7, 22, 20, 24);
        float leftUpper = inkInRect(pixels, 6, 11, 5, 14);
        float rightLower = inkInRect(pixels, 18, 23, 14, 23);

        boolean hasThreeBands = topBand > total * 0.13f && middleBand > total * 0.13f && bottomBand > total * 0.13f;
        boolean hasFiveSides = leftUpper > total * 0.10f && rightLower > total * 0.10f;
        return hasThreeBands && hasFiveSides;
    }

    private static boolean isSevenLike(float[] pixels) {
        float total = inkInRect(pixels, 0, 27, 0, 27);
        if (total < 12f) return false;

        float topBand = inkInRect(pixels, 7, 22, 3, 7);
        float diagonal = diagonalInk(pixels, 21, 6, 13, 23);
        float bottomLeft = inkInRect(pixels, 5, 13, 19, 24);

        boolean hasTopBar = topBand > total * 0.20f;
        boolean hasDownLeftDiagonal = diagonal > total * 0.22f;
        boolean lacksJHook = bottomLeft < total * 0.20f;
        return hasTopBar && hasDownLeftDiagonal && lacksJHook;
    }

    private static float diagonalInk(float[] pixels, int x1, int y1, int x2, int y2) {
        float sum = 0f;
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            float t = steps == 0 ? 0f : (float) i / steps;
            int x = Math.round(x1 + (x2 - x1) * t);
            int y = Math.round(y1 + (y2 - y1) * t);
            sum += inkInRect(pixels, x - 1, x + 1, y - 1, y + 1);
        }
        return sum;
    }

    private static float inkInRect(float[] pixels, int left, int right, int top, int bottom) {
        float sum = 0f;
        for (int y = Math.max(0, top); y <= Math.min(27, bottom); y++) {
            for (int x = Math.max(0, left); x <= Math.min(27, right); x++) {
                sum += pixels[y * 28 + x];
            }
        }
        return sum;
    }

    static void mergeGroup(float[] mergedProbabilities, String[] group) {
        float sum = 0f;
        int digitIdx = -1;
        float maxLetterProb = -1f;
        int maxLetterIdx = -1;

        for (String s : group) {
            int idx = -1;
            for (int i = 0; i < LABELS.length; i++) {
                if (LABELS[i].equals(s)) {
                    idx = i;
                    break;
                }
            }
            if (idx != -1) {
                float prob = mergedProbabilities[idx];
                sum += prob;
                if (Character.isDigit(s.charAt(0))) {
                    digitIdx = idx;
                } else {
                    if (prob > maxLetterProb) {
                        maxLetterProb = prob;
                        maxLetterIdx = idx;
                    }
                }
            }
        }

        int representativeIdx = -1;
        if (digitIdx != -1) {
            representativeIdx = digitIdx;
        } else {
            representativeIdx = maxLetterIdx;
        }

        // Set all to 0, then assign sum to representative
        for (String s : group) {
            for (int i = 0; i < LABELS.length; i++) {
                if (LABELS[i].equals(s)) {
                    mergedProbabilities[i] = 0f;
                    break;
                }
            }
        }
        if (representativeIdx != -1) {
            mergedProbabilities[representativeIdx] = sum;
        }
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public void close() {
        synchronized (this) {
            if (interpreter != null) {
                interpreter.close();
                interpreter = null;
            }
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
