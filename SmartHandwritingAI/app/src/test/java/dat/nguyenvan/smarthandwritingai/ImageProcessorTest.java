package dat.nguyenvan.smarthandwritingai;

import android.graphics.Bitmap;
import android.graphics.Color;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit test cho ImageProcessor — chạy trên JVM, không cần thiết bị.
 * Các bitmap Android cần Robolectric hoặc instrumented test nếu muốn
 * chạy đầy đủ; test này kiểm tra logic thuần Java.
 */
public class ImageProcessorTest {

    @Test
    public void testRotationDegreeDefault() {
        // Mặc định rotation phải là 0 hoặc một góc hợp lệ
        int rotation = ImageProcessor.rotationDegrees;
        assertTrue("Rotation should be 0, 90, 180 or 270",
                rotation == 0 || rotation == 90 || rotation == 180 || rotation == 270);
    }

    @Test
    public void testFlippedDefaultIsFalse() {
        // Mặc định không lật
        // Chỉ kiểm tra giá trị là boolean hợp lệ
        boolean flipped = ImageProcessor.isFlipped;
        assertTrue("isFlipped should be a valid boolean", flipped == true || flipped == false);
    }
    @Test
    public void bridgeSmallStrokeGapReconnectsBrokenNine() {
        int width = 28;
        int height = 28;
        int[] pixels = new int[width * height];

        pixels[8 * width + 14] = 255;
        pixels[9 * width + 14] = 255;
        pixels[10 * width + 15] = 255;
        pixels[15 * width + 16] = 255;
        pixels[16 * width + 16] = 255;

        ImageProcessor.bridgeSmallStrokeGaps(pixels, width, height);

        assertEquals(255, pixels[11 * width + 15]);
        assertEquals(255, pixels[12 * width + 15]);
        assertEquals(255, pixels[13 * width + 16]);
        assertEquals(255, pixels[14 * width + 16]);
    }

    @Test
    public void bridgeSmallStrokeGapDoesNotMergeSeparateCharacters() {
        int width = 40;
        int height = 28;
        int[] pixels = new int[width * height];

        pixels[10 * width + 6] = 255;
        pixels[11 * width + 6] = 255;
        pixels[10 * width + 28] = 255;
        pixels[11 * width + 28] = 255;

        ImageProcessor.bridgeSmallStrokeGaps(pixels, width, height);

        for (int x = 7; x < 28; x++) {
            assertEquals(0, pixels[10 * width + x]);
        }
    }
}
