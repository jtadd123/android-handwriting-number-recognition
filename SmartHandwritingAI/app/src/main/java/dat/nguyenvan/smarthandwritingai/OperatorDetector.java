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

            // Kiểm tra xem 2 nét có giao nhau không
            boolean intersects = RectF.intersects(r1, r2);

            // Hoặc khoảng cách tâm rất gần nhau
            float centerX1 = r1.centerX();
            float centerY1 = r1.centerY();
            float centerX2 = r2.centerX();
            float centerY2 = r2.centerY();
            float xDist = Math.abs(centerX1 - centerX2);
            float yDist = Math.abs(centerY1 - centerY2);
            boolean centersClose = (xDist < groupW * 0.5f && yDist < groupH * 0.5f);

            if (intersects || centersClose) {
                float groupRatio = groupW / Math.max(1f, groupH);
                // Khung bao chung tương đối vuông vắn (tránh chữ số dài dẹt)
                if (groupRatio > 0.4f && groupRatio < 2.5f) {
                    // Kiểm tra một nét thiên về chiều ngang và một nét thiên về chiều dọc
                    boolean vertical1 = r1.height() > r1.width() * 0.8f;
                    boolean horizontal1 = r1.width() > r1.height() * 0.8f;
                    boolean vertical2 = r2.height() > r2.width() * 0.8f;
                    boolean horizontal2 = r2.width() > r2.height() * 0.8f;

                    if ((vertical1 && horizontal2) || (horizontal1 && vertical2)) {
                        return "+";
                    }
                }
            }
        }

        return null;
    }
}
