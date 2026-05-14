package dat.nguyenvan.smarthandwritingai;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ImageProcessor {

    private static final int INPUT_SIZE = 28;
    private static final int FLOAT_SIZE = 4;

    public static int rotationDegrees = 0;
    public static boolean isFlipped = false;

    public static ByteBuffer preprocessImage(Bitmap bitmap) {
        Bitmap grayscale = toGrayscale(bitmap);
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(grayscale, INPUT_SIZE, INPUT_SIZE, true);

        if (rotationDegrees != 0 || isFlipped) {
            Matrix matrix = new Matrix();
            if (rotationDegrees != 0) matrix.postRotate(rotationDegrees);
            if (isFlipped) matrix.postScale(-1, 1, INPUT_SIZE / 2f, INPUT_SIZE / 2f);
            
            scaledBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0, 
                    INPUT_SIZE, INPUT_SIZE, matrix, true);
            scaledBitmap = Bitmap.createScaledBitmap(scaledBitmap, INPUT_SIZE, INPUT_SIZE, true);
        }

        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 1 * FLOAT_SIZE);
        byteBuffer.order(ByteOrder.nativeOrder());
        byteBuffer.rewind();

        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        scaledBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        for (int pixel : pixels) {
            int r = (pixel >> 16) & 0xFF;
            float normalizedPixel = r / 255.0f;
            byteBuffer.putFloat(normalizedPixel);
        }

        return byteBuffer;
    }

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
}
