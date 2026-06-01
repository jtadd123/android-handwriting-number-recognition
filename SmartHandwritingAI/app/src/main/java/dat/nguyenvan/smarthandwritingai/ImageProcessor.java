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
        // 1. Iterative Downscale to preserve strokes before binarization
        // We downscale until max dimension is <= 300
        Bitmap workingBitmap = bitmap;
        while (workingBitmap.getWidth() > 300 || workingBitmap.getHeight() > 300) {
            int nw = workingBitmap.getWidth() / 2;
            int nh = workingBitmap.getHeight() / 2;
            workingBitmap = Bitmap.createScaledBitmap(workingBitmap, nw, nh, true);
        }

        // 2. To Grayscale
        workingBitmap = toGrayscale(workingBitmap);
        int width = workingBitmap.getWidth();
        int height = workingBitmap.getHeight();
        int[] pixels = new int[width * height];
        workingBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        int[] intensities = new int[width * height];
        for (int i = 0; i < pixels.length; i++) {
            intensities[i] = pixels[i] & 0xFF;
        }

        // 3. Robust Thresholding instead of Otsu and Stretching
        int[] histogram = new int[256];
        for (int v : intensities) histogram[v]++;

        int totalPixels = width * height;
        int minVal = 0, maxVal = 255;
        int cumSum = 0;
        for (int i = 0; i < 256; i++) {
            cumSum += histogram[i];
            if (cumSum >= totalPixels * 0.005) { minVal = i; break; }
        }
        cumSum = 0;
        for (int i = 255; i >= 0; i--) {
            cumSum += histogram[i];
            if (cumSum >= totalPixels * 0.05) { maxVal = i; break; }
        }

        // Determine inversion based on border mean
        long borderSum = 0;
        int borderCount = 0;
        for (int x = 0; x < width; x++) {
            borderSum += intensities[x];
            borderSum += intensities[(height - 1) * width + x];
            borderCount += 2;
        }
        for (int y = 1; y < height - 1; y++) {
            borderSum += intensities[y * width];
            borderSum += intensities[y * width + (width - 1)];
            borderCount += 2;
        }
        float borderMean = (float) borderSum / borderCount;
        boolean invert = borderMean > (minVal + maxVal) / 2.0f;

        int[] inkPixels = adaptiveThreshold(intensities, width, height, invert, minVal, maxVal);

        // 7. Morphological noise removal
        removeSmallNoise(inkPixels, width, height);
        bridgeSmallStrokeGaps(inkPixels, width, height);

        // 8. Find Bounding Box
        int minX = width, minY = height, maxX = -1, maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (inkPixels[y * width + x] == 255) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        // 9. Crop and Scale to 20x20
        Bitmap finalBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(finalBitmap);
        canvas.drawColor(Color.BLACK);

        if (maxX >= minX && maxY >= minY) {
            // Expand bounding box by 3px to capture ink edge gradients
            minX = Math.max(0, minX - 3);
            minY = Math.max(0, minY - 3);
            maxX = Math.min(width - 1, maxX + 3);
            maxY = Math.min(height - 1, maxY + 3);

            int cropW = maxX - minX + 1;
            int cropH = maxY - minY + 1;

            // Create dilated mask (2x) to include ink edge pixels
            int[] edgeMask = dilateBinary(inkPixels, width, height);
            edgeMask = dilateBinary(edgeMask, width, height);

            // Create crop bitmap using GRAYSCALE values (not binary) for natural stroke gradients
            Bitmap cropBitmap = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888);
            int[] cropPixels = new int[cropW * cropH];
            for (int y = 0; y < cropH; y++) {
                for (int x = 0; x < cropW; x++) {
                    int srcIdx = (minY + y) * width + (minX + x);
                    if (edgeMask[srcIdx] == 255) {
                        int grayVal = invert ? (255 - intensities[srcIdx]) : intensities[srcIdx];
                        grayVal = Math.max(0, grayVal);
                        cropPixels[y * cropW + x] = grayVal > 10 ? Color.argb(255, grayVal, grayVal, grayVal) : Color.BLACK;
                    } else {
                        cropPixels[y * cropW + x] = Color.BLACK;
                    }
                }
            }
            cropBitmap.setPixels(cropPixels, 0, cropW, 0, 0, cropW, cropH);

            // Scale to 20x20 — bilinear on grayscale produces smooth EMNIST-like strokes
            float scale = 20.0f / Math.max(cropW, cropH);
            int sw = Math.max(1, Math.round(cropW * scale));
            int sh = Math.max(1, Math.round(cropH * scale));
            Bitmap scaledInk = Bitmap.createScaledBitmap(cropBitmap, sw, sh, true);

            float offsetX = (INPUT_SIZE - sw) / 2f;
            float offsetY = (INPUT_SIZE - sh) / 2f;
            canvas.drawBitmap(scaledInk, offsetX, offsetY, null);
        }

        // 10. Normalize contrast: scale so max pixel intensity reaches 255
        normalizeContrast(finalBitmap);

        // 11. Transformation (dilation removed — EMNIST data has natural anti-aliased strokes without extra dilation)
        Matrix matrix = new Matrix();
        if (rotationDegrees != 0) matrix.postRotate(rotationDegrees);
        if (isFlipped) matrix.postScale(-1, 1, INPUT_SIZE / 2f, INPUT_SIZE / 2f);

        finalBitmap = Bitmap.createBitmap(finalBitmap, 0, 0, INPUT_SIZE, INPUT_SIZE, matrix, true);
        lastPreprocessedBitmap = finalBitmap;

        // Debug logging: bitmap stats for diagnosis
        int[] debugPixels = new int[INPUT_SIZE * INPUT_SIZE];
        finalBitmap.getPixels(debugPixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);
        int debugMin = 255, debugMax = 0;
        float debugSum = 0;
        int whiteCount = 0;
        for (int dp : debugPixels) {
            int dv = (dp >> 16) & 0xFF;
            if (dv < debugMin) debugMin = dv;
            if (dv > debugMax) debugMax = dv;
            debugSum += dv;
            if (dv > 128) whiteCount++;
        }
        android.util.Log.d(TAG, "[DEBUG] Final bitmap stats - Min: " + debugMin 
            + ", Max: " + debugMax 
            + ", Avg: " + String.format("%.1f", debugSum / debugPixels.length)
            + ", WhitePixels: " + whiteCount + "/" + debugPixels.length
            + ", Inverted: " + invert
            + ", BorderMean: " + String.format("%.1f", borderMean));

        // 11. Convert to ByteBuffer (divide by 255.0 to match EMNIST training normalization)
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



    private static void removeSmallNoise(int[] pixels, int w, int h) {
        int[] cleaned = pixels.clone();
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                if (pixels[y * w + x] == 255) {
                    int neighbors = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dy == 0 && dx == 0) continue;
                            if (pixels[(y + dy) * w + (x + dx)] == 255) neighbors++;
                        }
                    }
                    if (neighbors <= 1) {
                        cleaned[y * w + x] = 0;
                    }
                }
            }
        }
        System.arraycopy(cleaned, 0, pixels, 0, pixels.length);
    }

    static void bridgeSmallStrokeGaps(int[] pixels, int w, int h) {
        int maxGap = Math.max(6, Math.min(12, Math.min(w, h) / 8));
        int maxDrift = Math.max(1, maxGap / 2);
        int[] bridged = pixels.clone();

        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                if (pixels[y * w + x] != 255) continue;

                for (int dy = 2; dy <= maxGap; dy++) {
                    int targetY = y + dy;
                    if (targetY >= h - 1) break;

                    for (int dx = -maxDrift; dx <= maxDrift; dx++) {
                        int targetX = x + dx;
                        if (targetX <= 0 || targetX >= w - 1) continue;
                        if (pixels[targetY * w + targetX] == 255) {
                            drawBridge(bridged, w, h, x, y, targetX, targetY);
                        }
                    }
                }
            }
        }

        System.arraycopy(bridged, 0, pixels, 0, pixels.length);
    }

    private static void drawBridge(int[] pixels, int w, int h, int x1, int y1, int x2, int y2) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            float t = steps == 0 ? 0f : (float) i / steps;
            int x = Math.round(x1 + (x2 - x1) * t);
            int y = Math.round(y1 + (y2 - y1) * t);
            for (int oy = -1; oy <= 1; oy++) {
                for (int ox = -1; ox <= 1; ox++) {
                    int px = x + ox;
                    int py = y + oy;
                    if (px >= 0 && px < w && py >= 0 && py < h) {
                        pixels[py * w + px] = 255;
                    }
                }
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

    private static int[] dilateBinary(int[] pixels, int w, int h) {
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (pixels[y * w + x] == 255) {
                    out[y * w + x] = 255;
                    continue;
                }
                int max = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                            if (pixels[ny * w + nx] == 255) {
                                max = 255;
                                break;
                            }
                        }
                    }
                    if (max == 255) break;
                }
                out[y * w + x] = max;
            }
        }
        return out;
    }

    /**
     * Normalizes the contrast of a bitmap so the brightest pixel becomes 255.
     * This compensates for intensity loss during bilinear downscaling of binary images,
     * while preserving the anti-aliased edge gradients and original stroke shapes.
     */
    private static void normalizeContrast(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);

        int maxVal = 0;
        for (int p : pixels) {
            int v = (p >> 16) & 0xFF;
            if (v > maxVal) maxVal = v;
        }

        if (maxVal > 0 && maxVal < 255) {
            for (int i = 0; i < pixels.length; i++) {
                int v = (pixels[i] >> 16) & 0xFF;
                int nv = Math.min(255, v * 255 / maxVal);
                pixels[i] = Color.argb(255, nv, nv, nv);
            }
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
        }
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

    public static int[] adaptiveThreshold(int[] intensities, int width, int height, boolean invert, int minVal, int maxVal) {
        int[] binary = new int[width * height];
        int windowSize = Math.max(15, Math.min(35, Math.min(width, height) / 6));
        if (windowSize % 2 == 0) windowSize++; // Ensure odd window size
        int radius = windowSize / 2;
        
        // Compute integral image for fast local sum
        long[] integral = new long[width * height];
        for (int y = 0; y < height; y++) {
            long rowSum = 0;
            for (int x = 0; x < width; x++) {
                int val = intensities[y * width + x];
                rowSum += val;
                if (y == 0) {
                    integral[y * width + x] = rowSum;
                } else {
                    integral[y * width + x] = integral[(y - 1) * width + x] + rowSum;
                }
            }
        }

        // Local thresholding
        for (int y = 0; y < height; y++) {
            int y1 = Math.max(0, y - radius);
            int y2 = Math.min(height - 1, y + radius);
            for (int x = 0; x < width; x++) {
                int x1 = Math.max(0, x - radius);
                int x2 = Math.min(width - 1, x + radius);
                
                // Get sum using integral image
                long sum = integral[y2 * width + x2];
                if (x1 > 0) sum -= integral[y2 * width + (x1 - 1)];
                if (y1 > 0) sum -= integral[(y1 - 1) * width + x2];
                if (x1 > 0 && y1 > 0) sum += integral[(y1 - 1) * width + (x1 - 1)];
                
                int count = (x2 - x1 + 1) * (y2 - y1 + 1);
                float mean = (float) sum / count;
                
                int val = intensities[y * width + x];
                if (invert) {
                    int globalCutoff = minVal + (int) ((maxVal - minVal) * 0.80f);
                    if (val > globalCutoff) {
                        binary[y * width + x] = 0;
                    } else {
                        binary[y * width + x] = (val < mean * 0.93f && (mean - val) > 10) ? 255 : 0;
                    }
                } else {
                    int globalCutoff = minVal + (int) ((maxVal - minVal) * 0.20f);
                    if (val < globalCutoff) {
                        binary[y * width + x] = 0;
                    } else {
                        binary[y * width + x] = (val > mean * 1.07f && (val - mean) > 10) ? 255 : 0;
                    }
                }
            }
        }
        return binary;
    }

    public static class Component {
        public int minX = Integer.MAX_VALUE;
        public int maxX = -1;
        public int minY = Integer.MAX_VALUE;
        public int maxY = -1;
        public java.util.List<android.graphics.Point> points = new java.util.ArrayList<>();
    }

    public static class SymbolCluster {
        public java.util.List<Component> components = new java.util.ArrayList<>();
        public int minX = Integer.MAX_VALUE;
        public int maxX = -1;
        public int minY = Integer.MAX_VALUE;
        public int maxY = -1;

        public int totalPoints() {
            int sum = 0;
            for (Component c : components) {
                sum += c.points.size();
            }
            return sum;
        }

        public void addComponent(Component c) {
            components.add(c);
            if (c.minX < minX) minX = c.minX;
            if (c.maxX > maxX) maxX = c.maxX;
            if (c.minY < minY) minY = c.minY;
            if (c.maxY > maxY) maxY = c.maxY;
        }

        public int width() { return maxX - minX + 1; }
        public int height() { return maxY - minY + 1; }
        public int centerX() { return minX + width() / 2; }
        public int centerY() { return minY + height() / 2; }

        public void merge(SymbolCluster other) {
            components.addAll(other.components);
            if (other.minX < minX) minX = other.minX;
            if (other.maxX > maxX) maxX = other.maxX;
            if (other.minY < minY) minY = other.minY;
            if (other.maxY > maxY) maxY = other.maxY;
        }

        public boolean isClose(SymbolCluster other, float maxDist) {
            int dx = 0;
            if (other.maxX < this.minX) {
                dx = this.minX - other.maxX;
            } else if (other.minX > this.maxX) {
                dx = other.minX - this.maxX;
            }

            int dy = 0;
            if (other.maxY < this.minY) {
                dy = this.minY - other.maxY;
            } else if (other.minY > this.maxY) {
                dy = other.minY - this.maxY;
            }

            int overlapLeft = Math.max(this.minX, other.minX);
            int MathMin = Math.min(this.maxX, other.maxX);
            int overlapW = MathMin - overlapLeft;
            int minW = Math.min(this.width(), other.width());

            if (overlapW > minW * 0.4f && dy < maxDist * 1.5f) {
                return true;
            }

            double dist = Math.sqrt(dx * dx + dy * dy);
            return dx < maxDist * 0.5f && dy < maxDist;
        }
    }

    public static java.util.List<FractionParser.SegmentedSymbol> segmentImage(Bitmap bitmap) {
        java.util.List<FractionParser.SegmentedSymbol> resultList = new java.util.ArrayList<>();

        // 1. Iterative Downscale to preserve strokes before binarization
        Bitmap workingBitmap = bitmap;
        while (workingBitmap.getWidth() > 300 || workingBitmap.getHeight() > 300) {
            int nw = workingBitmap.getWidth() / 2;
            int nh = workingBitmap.getHeight() / 2;
            workingBitmap = Bitmap.createScaledBitmap(workingBitmap, nw, nh, true);
        }

        // 2. To Grayscale
        workingBitmap = toGrayscale(workingBitmap);
        int width = workingBitmap.getWidth();
        int height = workingBitmap.getHeight();
        int[] pixels = new int[width * height];
        workingBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        int[] intensities = new int[width * height];
        for (int i = 0; i < pixels.length; i++) {
            intensities[i] = pixels[i] & 0xFF;
        }

        // 3. Robust Thresholding
        int[] histogram = new int[256];
        for (int v : intensities) histogram[v]++;

        int totalPixels = width * height;
        int minVal = 0, maxVal = 255;
        int cumSum = 0;
        for (int i = 0; i < 256; i++) {
            cumSum += histogram[i];
            if (cumSum >= totalPixels * 0.005) { minVal = i; break; }
        }
        cumSum = 0;
        for (int i = 255; i >= 0; i--) {
            cumSum += histogram[i];
            if (cumSum >= totalPixels * 0.05) { maxVal = i; break; }
        }

        // Determine inversion based on border mean
        long borderSum = 0;
        int borderCount = 0;
        for (int x = 0; x < width; x++) {
            borderSum += intensities[x];
            borderSum += intensities[(height - 1) * width + x];
            borderCount += 2;
        }
        for (int y = 1; y < height - 1; y++) {
            borderSum += intensities[y * width];
            borderSum += intensities[y * width + (width - 1)];
            borderCount += 2;
        }
        float borderMean = (float) borderSum / borderCount;
        boolean invert = true; // Always invert for camera/gallery photos (dark ink on light paper)

        int[] inkPixels = adaptiveThreshold(intensities, width, height, invert, minVal, maxVal);

        // 4. Remove small noise
        removeSmallNoise(inkPixels, width, height);
        bridgeSmallStrokeGaps(inkPixels, width, height);

        // Save a clone of the binarized pixels before calling dilateBinary to avoid double dilation
        int[] originalInkPixels = inkPixels.clone();

        // Dilate binary image to close tiny gaps in handwritten strokes before grouping components
        inkPixels = dilateBinary(inkPixels, width, height);

        // 5. CCA - Find Connected Components
        boolean[] visited = new boolean[width * height];
        java.util.List<Component> components = new java.util.ArrayList<>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                if (inkPixels[idx] == 255 && !visited[idx]) {
                    Component comp = new Component();
                    java.util.Queue<Integer> queue = new java.util.LinkedList<>();
                    queue.add(idx);
                    visited[idx] = true;

                    while (!queue.isEmpty()) {
                        int curr = queue.poll();
                        int cx = curr % width;
                        int cy = curr / width;

                        comp.points.add(new android.graphics.Point(cx, cy));
                        if (cx < comp.minX) comp.minX = cx;
                        if (cx > comp.maxX) comp.maxX = cx;
                        if (cy < comp.minY) comp.minY = cy;
                        if (cy > comp.maxY) comp.maxY = cy;

                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dx = -1; dx <= 1; dx++) {
                                if (dx == 0 && dy == 0) continue;
                                int nx = cx + dx;
                                int ny = cy + dy;
                                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                                    int nidx = ny * width + nx;
                                    if (inkPixels[nidx] == 255 && !visited[nidx]) {
                                        visited[nidx] = true;
                                        queue.add(nidx);
                                    }
                                }
                            }
                        }
                    }

                    if (comp.points.size() >= 4) {
                        components.add(comp);
                    }
                }
            }
        }

        if (components.isEmpty()) {
            return resultList;
        }

        // 6. Gộp các component gần nhau thành cụm ký tự (Symbol Clusters) trước khi lọc nhiễu đường biên
        java.util.List<SymbolCluster> clusters = new java.util.ArrayList<>();
        float maxDist = Math.max(12f, Math.max(width, height) * 0.10f);

        for (Component c : components) {
            SymbolCluster newCluster = new SymbolCluster();
            newCluster.addComponent(c);

            java.util.List<SymbolCluster> overlapping = new java.util.ArrayList<>();
            for (SymbolCluster existing : clusters) {
                if (existing.isClose(newCluster, maxDist)) {
                    overlapping.add(existing);
                }
            }

            if (overlapping.isEmpty()) {
                clusters.add(newCluster);
            } else {
                SymbolCluster first = overlapping.get(0);
                first.addComponent(c);
                for (int i = 1; i < overlapping.size(); i++) {
                    SymbolCluster other = overlapping.get(i);
                    first.merge(other);
                    clusters.remove(other);
                }
            }
        }

        if (clusters.isEmpty()) {
            return resultList;
        }

        // Find the maximum cluster size (total points count)
        int maxClusterPoints = 0;
        for (SymbolCluster sc : clusters) {
            int pts = sc.totalPoints();
            if (pts > maxClusterPoints) {
                maxClusterPoints = pts;
            }
        }

        // Lọc bỏ cụm nhiễu nhỏ hoặc cụm bóng đổ ở biên (Border shadows/noise)
        java.util.List<SymbolCluster> filteredClusters = new java.util.ArrayList<>();
        for (SymbolCluster sc : clusters) {
            int pts = sc.totalPoints();
            
            // Lọc nhiễu kích thước siêu nhỏ (nhỏ hơn 8% cụm lớn nhất)
            if (pts < Math.max(8, (int) (maxClusterPoints * 0.08f))) {
                continue;
            }

            // Lọc nhiễu bám biên/bóng đổ góc ảnh
            boolean touchesBorder = (sc.minX <= 2 || sc.minY <= 2 || sc.maxX >= width - 3 || sc.maxY >= height - 3);
            float centerX = sc.minX + (sc.maxX - sc.minX) / 2f;
            float centerY = sc.minY + (sc.maxY - sc.minY) / 2f;
            boolean overlapsCenter = (centerX >= width * 0.25f && centerX <= width * 0.75f) && 
                                     (centerY >= height * 0.25f && centerY <= height * 0.75f);
            
            if (touchesBorder && !overlapsCenter) {
                // Nếu cụm chạm biên, lệch tâm và nhỏ hơn 35% cụm lớn nhất thì coi là nhiễu biên/bóng đổ
                if (pts < maxClusterPoints * 0.35f) {
                    continue;
                }
            }
            filteredClusters.add(sc);
        }
        clusters = filteredClusters;

        if (clusters.isEmpty()) {
            return resultList;
        }

        // 7. Tạo preprocessed bitmap 28x28 cho từng cụm và lưu thành SegmentedSymbol
        for (SymbolCluster cluster : clusters) {
            int cropW = cluster.width();
            int cropH = cluster.height();

            // Create a tight bitmap around the cluster components (no padding)
            Bitmap clusterBitmap = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(clusterBitmap);
            canvas.drawColor(Color.BLACK);

            for (Component comp : cluster.components) {
                for (android.graphics.Point pt : comp.points) {
                    int px = pt.x - cluster.minX;
                    int py = pt.y - cluster.minY;
                    if (px >= 0 && px < cropW && py >= 0 && py < cropH) {
                        // Use inverted grayscale for natural stroke gradients
                        int grayVal = 255 - intensities[pt.y * width + pt.x];
                        grayVal = Math.max(0, grayVal);
                        if (grayVal > 10) {
                            clusterBitmap.setPixel(px, py, Color.argb(255, grayVal, grayVal, grayVal));
                        }
                    }
                }
            }

            Bitmap finalBitmap = Bitmap.createBitmap(28, 28, Bitmap.Config.ARGB_8888);
            Canvas finalCanvas = new Canvas(finalBitmap);
            finalCanvas.drawColor(Color.BLACK);

            // Scale tightly to 20x20 — bilinear on grayscale produces smooth EMNIST-like strokes
            float scale = 20.0f / Math.max(cropW, cropH);
            int sw = Math.max(1, Math.round(cropW * scale));
            int sh = Math.max(1, Math.round(cropH * scale));
            Bitmap scaled = Bitmap.createScaledBitmap(clusterBitmap, sw, sh, true);

            // Center the scaled bitmap in the 28x28 canvas
            float offsetX = (28 - sw) / 2f;
            float offsetY = (28 - sh) / 2f;
            finalCanvas.drawBitmap(scaled, offsetX, offsetY, null);

            // Normalize contrast: scale so max pixel intensity reaches 255
            normalizeContrast(finalBitmap);

            float scaleX = (float) bitmap.getWidth() / width;
            float scaleY = (float) bitmap.getHeight() / height;

            float origLeft = cluster.minX * scaleX;
            float origTop = cluster.minY * scaleY;
            float origRight = (cluster.maxX + 1) * scaleX;
            float origBottom = (cluster.maxY + 1) * scaleY;

            resultList.add(new FractionParser.SegmentedSymbol(
                    origLeft, origTop, origRight, origBottom, finalBitmap, null
            ));
        }

        java.util.Collections.sort(resultList, (s1, s2) -> Float.compare(s1.left, s2.left));

        return resultList;
    }

    public static ByteBuffer convertPreprocessedBitmapToByteBuffer(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(w * h * FLOAT_SIZE);
        byteBuffer.order(java.nio.ByteOrder.nativeOrder());
        byteBuffer.rewind();

        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        for (int p : pixels) {
            float val = ((p >> 16) & 0xFF) / 255.0f;
            byteBuffer.putFloat(val);
        }
        return byteBuffer;
    }
}
