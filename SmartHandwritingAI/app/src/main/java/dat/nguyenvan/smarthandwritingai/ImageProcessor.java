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
        // We downscale until max dimension is <= 150
        Bitmap workingBitmap = bitmap;
        while (workingBitmap.getWidth() > 150 || workingBitmap.getHeight() > 150) {
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
            if (cumSum >= totalPixels * 0.02) { minVal = i; break; }
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

        int threshold;
        if (invert) {
            // White background, black pen. Keep only the darkest 40% of the range as ink.
            threshold = minVal + (int)((maxVal - minVal) * 0.40);
        } else {
            // Black background, white pen. Keep only the brightest 40% of the range as ink.
            threshold = maxVal - (int)((maxVal - minVal) * 0.40);
        }

        int[] inkPixels = new int[width * height];
        for (int i = 0; i < intensities.length; i++) {
            if (invert) {
                inkPixels[i] = intensities[i] < threshold ? 255 : 0;
            } else {
                inkPixels[i] = intensities[i] > threshold ? 255 : 0;
            }
        }

        // 7. Morphological noise removal
        removeSmallNoise(inkPixels, width, height);

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
            int cropW = maxX - minX + 1;
            int cropH = maxY - minY + 1;
            Bitmap cropBitmap = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888);
            int[] cropPixels = new int[cropW * cropH];
            for (int y = 0; y < cropH; y++) {
                for (int x = 0; x < cropW; x++) {
                    cropPixels[y * cropW + x] = inkPixels[(minY + y) * width + (minX + x)] == 255 ? Color.WHITE : Color.BLACK;
                }
            }
            cropBitmap.setPixels(cropPixels, 0, cropW, 0, 0, cropW, cropH);

            float scale = 20.0f / Math.max(cropW, cropH);
            int sw = Math.max(1, Math.round(cropW * scale));
            int sh = Math.max(1, Math.round(cropH * scale));
            Bitmap scaledInk = Bitmap.createScaledBitmap(cropBitmap, sw, sh, true);

            float offsetX = (INPUT_SIZE - sw) / 2f;
            float offsetY = (INPUT_SIZE - sh) / 2f;
            canvas.drawBitmap(scaledInk, offsetX, offsetY, null);
        }

        // 10. Dilation to thicken strokes
        finalBitmap = dilate(finalBitmap);

        // 11. Transformation
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

        // 12. Convert to ByteBuffer
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
        while (workingBitmap.getWidth() > 150 || workingBitmap.getHeight() > 150) {
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
            if (cumSum >= totalPixels * 0.02) { minVal = i; break; }
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

        int threshold;
        if (invert) {
            threshold = minVal + (int)((maxVal - minVal) * 0.40);
        } else {
            threshold = maxVal - (int)((maxVal - minVal) * 0.40);
        }

        int[] inkPixels = new int[width * height];
        for (int i = 0; i < intensities.length; i++) {
            if (invert) {
                inkPixels[i] = intensities[i] < threshold ? 255 : 0;
            } else {
                inkPixels[i] = intensities[i] > threshold ? 255 : 0;
            }
        }

        // 4. Remove small noise
        removeSmallNoise(inkPixels, width, height);

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

        // 6. Gộp các component gần nhau thành cụm ký tự (Symbol Clusters)
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

        // 7. Tạo preprocessed bitmap 28x28 cho từng cụm và lưu thành SegmentedSymbol
        for (SymbolCluster cluster : clusters) {
            int cropW = cluster.width();
            int cropH = cluster.height();

            int padding = Math.max(2, (int) (Math.max(cropW, cropH) * 0.15f));
            int maxDim = Math.max(cropW, cropH);
            int size = maxDim + 2 * padding;

            Bitmap clusterBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(clusterBitmap);
            canvas.drawColor(Color.BLACK);

            int dx = padding + (maxDim - cropW) / 2;
            int dy = padding + (maxDim - cropH) / 2;

            for (Component comp : cluster.components) {
                for (android.graphics.Point pt : comp.points) {
                    int px = pt.x - cluster.minX + dx;
                    int py = pt.y - cluster.minY + dy;
                    if (px >= 0 && px < size && py >= 0 && py < size) {
                        clusterBitmap.setPixel(px, py, Color.WHITE);
                    }
                }
            }

            Bitmap finalBitmap = Bitmap.createBitmap(28, 28, Bitmap.Config.ARGB_8888);
            Canvas finalCanvas = new Canvas(finalBitmap);
            finalCanvas.drawColor(Color.BLACK);

            Bitmap scaled = Bitmap.createScaledBitmap(clusterBitmap, 20, 20, true);
            finalCanvas.drawBitmap(scaled, 4, 4, null);

            finalBitmap = dilate(finalBitmap);

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
}