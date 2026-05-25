package dat.nguyenvan.smarthandwritingai;

import android.graphics.RectF;
import java.util.ArrayList;

public class OperatorDetector {

    /**
     * Nhận dạng nhanh các toán tử +, -, = bằng phân tích hình học nét vẽ (Heuristics).
     * Trả về "+", "-", "=" nếu khớp, hoặc null nếu cần đưa qua AI để phân loại (số hoặc chữ).
     */
    public static String detectOperator(DrawingView.PathData[] pathDatas, float strokeWidth) {
        if (pathDatas == null || pathDatas.length == 0) return null;

        // 0. Lọc bỏ các nét vẽ siêu nhỏ (noise/chấm lỗi)
        ArrayList<DrawingView.PathData> validPaths = new ArrayList<>();
        for (DrawingView.PathData pd : pathDatas) {
            RectF r = new RectF();
            pd.path.computeBounds(r, true);
            // Một nét vẽ được coi là hợp lệ nếu chiều rộng hoặc chiều cao lớn hơn một phần nhỏ nét bút
            if (r.width() > strokeWidth * 0.15f || r.height() > strokeWidth * 0.15f) {
                validPaths.add(pd);
            }
        }

        if (validPaths.isEmpty()) return null;

        // Tính bounding box chung của các nét vẽ hợp lệ
        RectF groupBounds = new RectF();
        validPaths.get(0).path.computeBounds(groupBounds, true);

        ArrayList<RectF> individualBounds = new ArrayList<>();
        RectF temp = new RectF();
        validPaths.get(0).path.computeBounds(temp, true);
        individualBounds.add(new RectF(temp));

        for (int i = 1; i < validPaths.size(); i++) {
            validPaths.get(i).path.computeBounds(temp, true);
            individualBounds.add(new RectF(temp));
            groupBounds.union(temp);
        }

        float groupW = groupBounds.width();
        float groupH = groupBounds.height();

        // 1. Nhận dạng dấu trừ '-'
        // Điều kiện: Chỉ có 1 nét vẽ, dẹt ngang (width / height > 1.2), chiều cao tương đối nhỏ so với nét bút
        if (validPaths.size() == 1) {
            float ratio = groupW / Math.max(1f, groupH);
            if (ratio > 1.2f && groupH < strokeWidth * 2.5f) {
                return "-";
            }
        }

        // 2. Nhận dạng dấu bằng '='
        // Điều kiện: Có đúng 2 nét vẽ nằm ngang song song chồng lên nhau
        if (validPaths.size() == 2) {
            RectF r1 = individualBounds.get(0);
            RectF r2 = individualBounds.get(1);

            float r1Ratio = r1.width() / Math.max(1f, r1.height());
            float r2Ratio = r2.width() / Math.max(1f, r2.height());

            // Cả hai nét đều dẹt ngang
            if (r1Ratio > 1.1f && r2Ratio > 1.1f) {
                // Kiểm tra sự trùng lặp (overlap) theo chiều ngang trục X
                float overlapLeft = Math.max(r1.left, r2.left);
                float overlapRight = Math.min(r1.right, r2.right);
                float overlapW = overlapRight - overlapLeft;
                float minW = Math.min(r1.width(), r2.width());

                if (overlapW > minW * 0.4f) {
                    // Kiểm tra khoảng cách Y hợp lý giữa 2 nét
                    float yDist = Math.abs(r1.centerY() - r2.centerY());
                    if (yDist > strokeWidth * 0.2f && yDist < groupH * 1.1f) {
                        return "=";
                    }
                }
            }
        }

        // 3. Nhận dạng dấu cộng '+'
        // Điều kiện: Có đúng 2 nét vẽ cắt nhau hoặc tâm rất gần nhau, tỉ lệ khung bao vuông vắn và không phải song song ngang (dấu bằng)
        if (validPaths.size() == 2) {
            RectF r1 = individualBounds.get(0);
            RectF r2 = individualBounds.get(1);

            boolean intersects = RectF.intersects(r1, r2);

            float centerX1 = r1.centerX();
            float centerY1 = r1.centerY();
            float centerX2 = r2.centerX();
            float centerY2 = r2.centerY();
            float xDist = Math.abs(centerX1 - centerX2);
            float yDist = Math.abs(centerY1 - centerY2);
            // Ngưỡng khoảng cách tâm rất gần nhau để đảm bảo giao nhau ở giữa
            boolean centersVeryClose = (xDist < Math.max(r1.width(), r2.width()) * 0.35f 
                                     && yDist < Math.max(r1.height(), r2.height()) * 0.35f);

            if (intersects || centersVeryClose) {
                float groupRatio = groupW / Math.max(1f, groupH);
                if (groupRatio > 0.4f && groupRatio < 2.5f) {
                    boolean vertical1 = r1.height() > r1.width() * 0.6f;
                    boolean horizontal1 = r1.width() > r1.height() * 0.6f;
                    boolean vertical2 = r2.height() > r2.width() * 0.6f;
                    boolean horizontal2 = r2.width() > r2.height() * 0.6f;

                    if ((vertical1 && horizontal2) || (horizontal1 && vertical2)) {
                        // Kiểm tra giao nhau gần tâm của cả hai nét (tránh nhận diện nhầm số 4, 7, 1 có móc)
                        float intersectX = centerX1;
                        float intersectY = centerY1;
                        if (intersects) {
                            RectF intersectRect = new RectF();
                            intersectRect.left = Math.max(r1.left, r2.left);
                            intersectRect.right = Math.min(r1.right, r2.right);
                            intersectRect.top = Math.max(r1.top, r2.top);
                            intersectRect.bottom = Math.min(r1.bottom, r2.bottom);
                            intersectX = intersectRect.centerX();
                            intersectY = intersectRect.centerY();
                        }

                        boolean nearCenter1 = Math.abs(intersectX - centerX1) < r1.width() * 0.38f
                                && Math.abs(intersectY - centerY1) < r1.height() * 0.38f;
                        boolean nearCenter2 = Math.abs(intersectX - centerX2) < r2.width() * 0.38f
                                && Math.abs(intersectY - centerY2) < r2.height() * 0.38f;

                        if (nearCenter1 && nearCenter2) {
                            return "+";
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Nhận dạng nhanh các toán tử +, -, = từ bitmap 28x28 (cho chế độ ảnh)
     * originalRatio là tỷ lệ aspect ratio (width / height) ban đầu của kí tự.
     */
    public static String detectOperatorFromBitmap(android.graphics.Bitmap bitmap, float originalRatio) {
        if (bitmap == null) return null;

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w < 28 || h < 28) return null; // Safe guard for dimensions

        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);

        // Corner pixel checking: count white pixels in the 4 corners of the 20x20 bounding box
        int cornerWhite = 0;
        // Top-Left corner: [4..8, 4..8]
        for (int y = 4; y <= 8; y++) {
            for (int x = 4; x <= 8; x++) {
                if (((pixels[y * w + x] >> 16) & 0xFF) > 128) cornerWhite++;
            }
        }
        // Top-Right corner: [19..23, 4..8]
        for (int y = 4; y <= 8; y++) {
            for (int x = 19; x <= 23; x++) {
                if (((pixels[y * w + x] >> 16) & 0xFF) > 128) cornerWhite++;
            }
        }
        // Bottom-Left corner: [4..8, 19..23]
        for (int y = 19; y <= 23; y++) {
            for (int x = 4; x <= 8; x++) {
                if (((pixels[y * w + x] >> 16) & 0xFF) > 128) cornerWhite++;
            }
        }
        // Bottom-Right corner: [19..23, 19..23]
        for (int y = 19; y <= 23; y++) {
            for (int x = 19; x <= 23; x++) {
                if (((pixels[y * w + x] >> 16) & 0xFF) > 128) cornerWhite++;
            }
        }

        if (cornerWhite > 3) {
            return null; // Bypasses operator heuristics, routes to TFLite AI model
        }

        // Count row and column white pixels
        int[] rowCounts = new int[h];
        int[] colCounts = new int[w];
        int totalWhite = 0;
        int maxRow = 0;
        int maxCol = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int val = (pixels[y * w + x] >> 16) & 0xFF;
                if (val > 128) {
                    rowCounts[y]++;
                    colCounts[x]++;
                    totalWhite++;
                }
            }
        }

        for (int y = 0; y < h; y++) {
            if (rowCounts[y] > maxRow) maxRow = rowCounts[y];
        }
        for (int x = 0; x < w; x++) {
            if (colCounts[x] > maxCol) maxCol = colCounts[x];
        }

        if (totalWhite < 12) return null;

        // 1. Nhận dạng dấu trừ '-'
        if (originalRatio > 2.0f && maxRow >= 8 && maxCol < 6) {
            return "-";
        }

        // 2. Nhận dạng dấu bằng '='
        if (originalRatio >= 0.7f && maxCol < 7) {
            int topMaxIdx = -1;
            int topMaxVal = 0;
            int yStartTop = Math.max(0, (int)(h * 0.07));
            int yEndTop = Math.min(h - 1, (int)(h * 0.45));
            for (int y = yStartTop; y <= yEndTop; y++) {
                if (rowCounts[y] > topMaxVal) {
                    topMaxVal = rowCounts[y];
                    topMaxIdx = y;
                }
            }

            int bottomMaxIdx = -1;
            int bottomMaxVal = 0;
            int yStartBottom = Math.max(0, (int)(h * 0.55));
            int yEndBottom = Math.min(h - 1, (int)(h * 0.93));
            for (int y = yStartBottom; y <= yEndBottom; y++) {
                if (rowCounts[y] > bottomMaxVal) {
                    bottomMaxVal = rowCounts[y];
                    bottomMaxIdx = y;
                }
            }

            if (topMaxIdx != -1 && bottomMaxIdx != -1 && topMaxVal > 6 && bottomMaxVal > 6) {
                int minVal = Integer.MAX_VALUE;
                for (int y = topMaxIdx + 1; y < bottomMaxIdx; y++) {
                    if (rowCounts[y] < minVal) {
                        minVal = rowCounts[y];
                    }
                }
                if (minVal <= 1) {
                    return "=";
                }
            }
        }

        // 3. Nhận dạng dấu cộng '+'
        if (originalRatio > 0.6f && originalRatio < 1.6f) {
            // A plus sign must have strong vertical and horizontal strokes intersecting near the center
            if (maxRow >= 8 && maxCol >= 8) {
                // Find peak row and column (center of the cross strokes)
                int peakRow = -1;
                int maxRowVal = 0;
                for (int y = 0; y < h; y++) {
                    if (rowCounts[y] > maxRowVal) {
                        maxRowVal = rowCounts[y];
                        peakRow = y;
                    }
                }
                int peakCol = -1;
                int maxColVal = 0;
                for (int x = 0; x < w; x++) {
                    if (colCounts[x] > maxColVal) {
                        maxColVal = colCounts[x];
                        peakCol = x;
                    }
                }

                // Verify peak position is near the center [8..20]
                if (peakRow >= 8 && peakRow <= 20 && peakCol >= 8 && peakCol <= 20) {
                    // Count white pixels in quadrants (outside the central cross of width 5)
                    int quadrantWhite = 0;
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            int val = (pixels[y * w + x] >> 16) & 0xFF;
                            if (val > 128) {
                                if (Math.abs(x - peakCol) > 2 && Math.abs(y - peakRow) > 2) {
                                    quadrantWhite++;
                                }
                            }
                        }
                    }

                    // For '+' sign, quadrant pixels must be very low (typically < 18% of total white pixels)
                    // and total white pixels must not be too high (indicates solid blobs rather than thin lines)
                    if (quadrantWhite < totalWhite * 0.18f && totalWhite < 150) {
                        return "+";
                    }
                }
            }
        }

        return null;
    }
}
