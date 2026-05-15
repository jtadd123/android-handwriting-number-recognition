package dat.nguyenvan.smarthandwritingai;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ImageProcessor {

    private static final String TAG = "ImageProcessor";
    private static final int INPUT_SIZE = 28;
    private static final int FLOAT_SIZE = 4;

    public static int rotationDegrees = 0;
    public static boolean isFlipped = false;

    private static Bitmap lastPreprocessedBitmap;

    public static Bitmap getLastPreprocessedBitmap() {
        return lastPreprocessedBitmap;
    }

    public static ByteBuffer preprocessImage(Bitmap bitmap) {
        // 1. To Grayscale
        Bitmap grayscale = toGrayscale(bitmap);
        int width = grayscale.getWidth();
        int height = grayscale.getHeight();
        int[] pixels = new int[width * height];
        grayscale.getPixels(pixels, 0, width, 0, 0, width, height);

        // 2. Otsu Binarization
        int[] intensities = new int[width * height];
        int[] histogram = new int[256];
        long sum = 0;
        for (int i = 0; i < pixels.length; i++) {
            int r = pixels[i] & 0xFF;
            intensities[i] = r;
            histogram[r]++;
            sum += r;
        }

        float sumB = 0;
        int wB = 0;
        int wF = 0;
        float varMax = 0;
        int threshold = 0;

        for (int t = 0; t < 256; t++) {
            wB += histogram[t];
            if (wB == 0) continue;
            wF = pixels.length - wB;
            if (wF == 0) break;
            sumB += (float) (t * histogram[t]);
            float mB = sumB / wB;
            float mF = (sum - sumB) / wF;
            float varBetween = (float) wB * (float) wF * (mB - mF) * (mB - mF);
            if (varBetween > varMax) {
                varMax = varBetween;
                threshold = t;
            }
        }

        int corners = (intensities[0] + intensities[width - 1] + intensities[(height - 1) * width] + intensities[width * height - 1]) / 4;
        boolean invert = corners > threshold;

        int[] inkPixels = new int[width * height];
        for (int i = 0; i < pixels.length; i++) {
            int val = intensities[i];
            if (invert) val = 255 - val;
            // Dùng ngưỡng khắt khe hơn một chút để loại bỏ bớt nhiễu ô ly
            inkPixels[i] = val > (invert ? (255 - threshold + 5) : threshold + 5) ? 255 : 0;
        }

        // 3. Grid Line Removal (Cải tiến: chỉ xóa nếu là đường kẻ rất đậm và dài)
        removeLines(inkPixels, width, height);

        // 4. Centering and Scaling
        int minX = width, minY = height, maxX = 0, maxY = 0;
        boolean hasInk = false;
        for (int i = 0; i < inkPixels.length; i++) {
            if (inkPixels[i] == 255) {
                hasInk = true;
                int x = i % width;
                int y = i / width;
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
            }
        }

        Bitmap finalBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(finalBitmap);
        canvas.drawColor(Color.BLACK);

        if (hasInk) {
            int cropW = maxX - minX + 1;
            int cropH = maxY - minY + 1;
            Bitmap inkOnly = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888);
            int[] cropPixels = new int[cropW * cropH];
            for (int y = 0; y < cropH; y++) {
                for (int x = 0; x < cropW; x++) {
                    cropPixels[y * cropW + x] = inkPixels[(minY + y) * width + (minX + x)] == 255 ? Color.WHITE : Color.BLACK;
                }
            }
            inkOnly.setPixels(cropPixels, 0, cropW, 0, 0, cropW, cropH);

            float scale = 18.0f / Math.max(cropW, cropH);
            int sw = Math.max(1, Math.round(cropW * scale));
            int sh = Math.max(1, Math.round(cropH * scale));
            Bitmap scaledInk = Bitmap.createScaledBitmap(inkOnly, sw, sh, true);
            canvas.drawBitmap(scaledInk, (INPUT_SIZE - sw) / 2f, (INPUT_SIZE - sh) / 2f, null);
        }

        // 5. Dilation
        finalBitmap = dilate(finalBitmap);
        
        // 6. EMNIST TRANSPOSE (Chỉ Rotate 90 CW, không lật)
        // Dựa trên việc "3" đã xuất hiện trong Top 3, ta cần chỉnh lại góc quay đứng
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        // Bỏ Flip Horizontal để xem số 3 có đứng thẳng không
        
        if (rotationDegrees != 0) matrix.postRotate(rotationDegrees);
        if (isFlipped) matrix.postScale(-1, 1, INPUT_SIZE / 2f, INPUT_SIZE / 2f);

        finalBitmap = Bitmap.createBitmap(finalBitmap, 0, 0, INPUT_SIZE, INPUT_SIZE, matrix, true);
        lastPreprocessedBitmap = finalBitmap;

        // 7. Convert to ByteBuffer
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * FLOAT_SIZE);
        byteBuffer.order(ByteOrder.nativeOrder());
        byteBuffer.rewind();

        int[] finalPixels = new int[INPUT_SIZE * INPUT_SIZE];
        finalBitmap.getPixels(finalPixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);
        for (int p : finalPixels) {
            float val = ((p >> 16) & 0xFF) / 255.0f;
            byteBuffer.putFloat(val);
        }

        return byteBuffer;
    }

    private static void removeLines(int[] pixels, int w, int h) {
        // Tăng ngưỡng lên 85% để tránh xóa nhầm nét của số
        for (int y = 0; y < h; y++) {
            int count = 0;
            for (int x = 0; x < w; x++) {
                if (pixels[y * w + x] == 255) count++;
            }
            if (count > w * 0.85) { 
                for (int x = 0; x < w; x++) pixels[y * w + x] = 0;
            }
        }
        for (int x = 0; x < w; x++) {
            int count = 0;
            for (int y = 0; y < h; y++) {
                if (pixels[y * w + x] == 255) count++;
            }
            if (count > h * 0.85) { 
                for (int y = 0; y < h; y++) pixels[y * w + x] = 0;
            }
        }
    }

    private static Bitmap dilate(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] pixels = new int[w * h];
        src.getPixels(pixels, 0, w, 0, 0, w, h);
        int[] outPixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int max = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                            int v = pixels[ny * w + nx] & 0xFF;
                            if (v > max) max = v;
                        }
                    }
                }
                outPixels[y * w + x] = Color.argb(255, max, max, max);
            }
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(outPixels, 0, w, 0, 0, w, h);
        return out;
    }

    public static Bitmap toGrayscale(Bitmap original) {
        Bitmap grayscale = Bitmap.createBitmap(original.getWidth(), original.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(grayscale);
        Paint paint = new Paint();
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0);
        paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
        canvas.drawBitmap(original, 0, 0, paint);
        return grayscale;
    }
}