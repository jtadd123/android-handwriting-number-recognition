package dat.nguyenvan.smarthandwritingai;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class ImageProcessor {

    private static final String TAG = "ImageProcessor";
    private static final int INPUT_SIZE = 28;
    private static final int FLOAT_SIZE = 4; 
    
    private static final int DIGIT_SIZE = 20;
    private static final int PADDING = 4;

    
    public static Bitmap toGrayscale(Bitmap original) {
        int width = original.getWidth();
        int height = original.getHeight();

        Bitmap grayscaleBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(grayscaleBitmap);

        Paint paint = new Paint();
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0); 
        paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));

        canvas.drawBitmap(original, 0, 0, paint);
        return grayscaleBitmap;
    }

    
    public static Bitmap resizeBitmap(Bitmap original, int targetWidth, int targetHeight) {
        return Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true);
    }

    
    private static Rect findBoundingBox(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        int minX = width, minY = height, maxX = 0, maxY = 0;
        
        int threshold = 30;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                
                if (r > threshold || g > threshold || b > threshold) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        
        if (minX > maxX || minY > maxY) {
            return new Rect(0, 0, width, height);
        }

        
        int margin = Math.max(width, height) / 20; 
        minX = Math.max(0, minX - margin);
        minY = Math.max(0, minY - margin);
        maxX = Math.min(width - 1, maxX + margin);
        maxY = Math.min(height - 1, maxY + margin);

        return new Rect(minX, minY, maxX + 1, maxY + 1);
    }

    
    public static Bitmap centerAndResize(Bitmap bitmap) {
        
        Bitmap grayscale = toGrayscale(bitmap);

        
        Rect bbox = findBoundingBox(grayscale);
        Log.d(TAG, "Bounding box: " + bbox.toString());

        
        int cropWidth = bbox.width();
        int cropHeight = bbox.height();

        if (cropWidth <= 0 || cropHeight <= 0) {
            return resizeBitmap(grayscale, INPUT_SIZE, INPUT_SIZE);
        }

        Bitmap cropped = Bitmap.createBitmap(grayscale,
                bbox.left, bbox.top, cropWidth, cropHeight);

        
        float scale;
        if (cropWidth > cropHeight) {
            scale = (float) DIGIT_SIZE / cropWidth;
        } else {
            scale = (float) DIGIT_SIZE / cropHeight;
        }

        int scaledWidth = Math.round(cropWidth * scale);
        int scaledHeight = Math.round(cropHeight * scale);

        
        scaledWidth = Math.max(1, Math.min(scaledWidth, DIGIT_SIZE));
        scaledHeight = Math.max(1, Math.min(scaledHeight, DIGIT_SIZE));

        Bitmap scaled = Bitmap.createScaledBitmap(cropped, scaledWidth, scaledHeight, true);

        
        Bitmap result = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.drawColor(Color.BLACK);

        int offsetX = (INPUT_SIZE - scaledWidth) / 2;
        int offsetY = (INPUT_SIZE - scaledHeight) / 2;

        canvas.drawBitmap(scaled, offsetX, offsetY, null);

        Log.d(TAG, "Processed: crop=" + cropWidth + "x" + cropHeight
                + " -> scaled=" + scaledWidth + "x" + scaledHeight
                + " -> centered at (" + offsetX + "," + offsetY + ")");

        return result;
    }

    
    public static ByteBuffer bitmapToByteBuffer(Bitmap bitmap) {
        
        Bitmap processed = centerAndResize(bitmap);

        
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(
                1 * INPUT_SIZE * INPUT_SIZE * 1 * FLOAT_SIZE);
        byteBuffer.order(ByteOrder.nativeOrder());
        byteBuffer.rewind();

        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        processed.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        for (int pixel : pixels) {
            
            int r = (pixel >> 16) & 0xFF;
            
            float normalizedPixel = r / 255.0f;
            byteBuffer.putFloat(normalizedPixel);
        }

        return byteBuffer;
    }

    
    public static float[][][][] bitmapToFloatArray(Bitmap bitmap) {
        Bitmap processed = centerAndResize(bitmap);

        float[][][][] input = new float[1][INPUT_SIZE][INPUT_SIZE][1];

        for (int y = 0; y < INPUT_SIZE; y++) {
            for (int x = 0; x < INPUT_SIZE; x++) {
                int pixel = processed.getPixel(x, y);
                int r = (pixel >> 16) & 0xFF;
                input[0][y][x][0] = r / 255.0f;
            }
        }

        return input;
    }
}
