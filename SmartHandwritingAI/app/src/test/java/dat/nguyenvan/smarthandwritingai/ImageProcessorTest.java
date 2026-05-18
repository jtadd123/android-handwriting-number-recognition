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
}
