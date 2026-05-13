package dat.nguyenvan.smarthandwritingai;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ImageProcessor {

    private static final int INPUT_SIZE = 28;
    private static final int FLOAT_SIZE = 4; 
    
    private static final int DIGIT_SIZE = 24;
    private static final int PADDING = 2;

    // Cấu hình động
    public static int rotationDegrees = 0;
    public static boolean isFlipped = false;

    public static Bitmap toGrayscale(Bitmap original) {
        int width = original.getWidth();
        int height = original.getHeight();
        Bitmap grayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(grayscale);
        Paint paint = new Paint();
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0); 
        paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
        canvas.drawBitmap(original, 0, 0, paint);
        return grayscale;
    }

    public static Rect findBoundingBox(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        int minX = width, minY = height, maxX = -1, maxY = -1;
        boolean found = false;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];
                int r = (pixel >> 16) & 0xFF;
                if (r > 30) { 
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                    found = true;
                }
            }
        }

        if (!found) return new Rect(0, 0, width, height);
        return new Rect(minX, minY, maxX, maxY);
    }

    public static Bitmap centerAndResize(Bitmap bitmap) {
        Bitmap grayscale = toGrayscale(bitmap);
        Rect bbox = findBoundingBox(grayscale);

        int cropWidth = bbox.width();
        int cropHeight = bbox.height();

        if (cropWidth <= 0 || cropHeight <= 0) {
            return resizeBitmap(grayscale, INPUT_SIZE, INPUT_SIZE);
        }

        Bitmap cropped = Bitmap.createBitmap(grayscale,
                bbox.left, bbox.top, cropWidth, cropHeight);

        float scale = (float) DIGIT_SIZE / Math.max(cropWidth, cropHeight);
        int scaledWidth = (int) (cropWidth * scale);
        int scaledHeight = (int) (cropHeight * scale);

        Bitmap scaled = Bitmap.createScaledBitmap(cropped, scaledWidth, scaledHeight, true);

        Bitmap output = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.BLACK);

        int offsetX = (INPUT_SIZE - scaledWidth) / 2;
        int offsetY = (INPUT_SIZE - scaledHeight) / 2;
        canvas.drawBitmap(scaled, offsetX, offsetY, null);

        return output;
    }

    public static Bitmap resizeBitmap(Bitmap bitmap, int width, int height) {
        return Bitmap.createScaledBitmap(bitmap, width, height, true);
    }

    public static ByteBuffer bitmapToByteBuffer(Bitmap bitmap) {
        Bitmap processed = centerAndResize(bitmap);

        // ÁP DỤNG CẤU HÌNH XOAY/LẬT ĐỘNG
        Matrix matrix = new Matrix();
        if (rotationDegrees != 0) {
            matrix.postRotate(rotationDegrees);
        }
        if (isFlipped) {
            matrix.postScale(-1, 1, INPUT_SIZE / 2f, INPUT_SIZE / 2f);
        }
        
        Bitmap finalBitmap = Bitmap.createBitmap(processed, 0, 0, 
                INPUT_SIZE, INPUT_SIZE, matrix, true);
        finalBitmap = Bitmap.createScaledBitmap(finalBitmap, INPUT_SIZE, INPUT_SIZE, true);

        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(
                1 * INPUT_SIZE * INPUT_SIZE * 1 * FLOAT_SIZE);
        byteBuffer.order(ByteOrder.nativeOrder());
        byteBuffer.rewind();

        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        finalBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        for (int pixel : pixels) {
            int r = (pixel >> 16) & 0xFF;
            float normalizedPixel = r / 255.0f;
            byteBuffer.putFloat(normalizedPixel);
        }

        return byteBuffer;
    }
}
