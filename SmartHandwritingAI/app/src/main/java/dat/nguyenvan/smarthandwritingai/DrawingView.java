package dat.nguyenvan.smarthandwritingai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;

public class DrawingView extends View {

    private Paint drawPaint;
    private ArrayList<PathData> paths = new ArrayList<>();
    private ArrayList<PathData> undonePaths = new ArrayList<>();
    private Path currentPath;
    private ArrayList<android.graphics.PointF> currentPoints = new ArrayList<>();
    
    private float mX, mY;
    private static final float TOUCH_TOLERANCE = 4;

    private int paintColor = Color.WHITE;
    private static final int BACKGROUND_COLOR = Color.BLACK;
    private float strokeWidth = 64f;

    private OnDrawListener drawListener;

    public interface OnDrawListener {
        void onDrawEnd();
    }

    // Inner class to store path with its paint properties and points
    public static class PathData {
        public Path path;
        public int color;
        public float strokeWidth;
        public ArrayList<android.graphics.PointF> points;

        public PathData(Path path, int color, float strokeWidth, ArrayList<android.graphics.PointF> points) {
            this.path = path;
            this.color = color;
            this.strokeWidth = strokeWidth;
            this.points = points;
        }
    }

    public DrawingView(Context context) {
        super(context);
        init();
    }

    public DrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public void setOnDrawListener(OnDrawListener listener) {
        this.drawListener = listener;
    }

    private void init() {
        drawPaint = new Paint();
        drawPaint.setColor(paintColor);
        drawPaint.setAntiAlias(true);
        drawPaint.setStrokeWidth(strokeWidth);
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setBrushSize(float size) {
        this.strokeWidth = size;
        drawPaint.setStrokeWidth(size);
    }

    public float getBrushSize() {
        return strokeWidth;
    }

    public void setBrushColor(int color) {
        this.paintColor = color;
        drawPaint.setColor(color);
    }

    public int getBrushColor() {
        return paintColor;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(BACKGROUND_COLOR);

        // Draw subtle technical grid lines (opacity ~5%)
        Paint gridPaint = new Paint();
        gridPaint.setColor(Color.WHITE);
        gridPaint.setAlpha(12);
        gridPaint.setStrokeWidth(2f);
        float spacing = 80f;
        for (float x = spacing; x < getWidth(); x += spacing) {
            canvas.drawLine(x, 0, x, getHeight(), gridPaint);
        }
        for (float y = spacing; y < getHeight(); y += spacing) {
            canvas.drawLine(0, y, getWidth(), y, gridPaint);
        }

        // Draw all saved paths with their own paint properties
        Paint tempPaint = new Paint(drawPaint);
        for (PathData pd : paths) {
            tempPaint.setColor(pd.color);
            tempPaint.setStrokeWidth(pd.strokeWidth);
            canvas.drawPath(pd.path, tempPaint);
        }
        // Draw current path with current settings
        if (currentPath != null) {
            canvas.drawPath(currentPath, drawPaint);
        }
    }

    private void touchStart(float x, float y) {
        undonePaths.clear();
        currentPath = new Path();
        currentPath.moveTo(x, y);
        currentPoints = new ArrayList<>();
        currentPoints.add(new android.graphics.PointF(x, y));
        mX = x;
        mY = y;
    }

    private void addInterpolatedPoints(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len == 0) return;
        int steps = Math.max(1, (int) (len / 6.0f));
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            currentPoints.add(new android.graphics.PointF(x1 + dx * t, y1 + dy * t));
        }
    }

    private void touchMove(float x, float y) {
        float dx = Math.abs(x - mX);
        float dy = Math.abs(y - mY);
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            currentPath.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2);
            addInterpolatedPoints(mX, mY, x, y);
            mX = x;
            mY = y;
        }
    }

    private void touchUp() {
        currentPath.lineTo(mX, mY);
        currentPoints.add(new android.graphics.PointF(mX, mY));
        paths.add(new PathData(currentPath, paintColor, strokeWidth, currentPoints));
        currentPath = null;
        if (drawListener != null) {
            drawListener.onDrawEnd();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStart(x, y);
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                touchMove(x, y);
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                touchUp();
                invalidate();
                break;
        }
        return true;
    }

    public void onClickUndo() {
        if (paths.size() > 0) {
            undonePaths.add(paths.remove(paths.size() - 1));
            invalidate();
            if (drawListener != null) drawListener.onDrawEnd();
        }
    }

    public void onClickRedo() {
        if (undonePaths.size() > 0) {
            paths.add(undonePaths.remove(undonePaths.size() - 1));
            invalidate();
            if (drawListener != null) drawListener.onDrawEnd();
        }
    }

    public void clearCanvas() {
        paths.clear();
        undonePaths.clear();
        invalidate();
    }

    public Bitmap getBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        draw(canvas);
        return bitmap;
    }

    /**
     * Helper to create a tight-bound SQUARE bitmap around the specified paths.
     * This avoids downscaling a giant canvas and preserves aspect ratio perfectly.
     */
    private Bitmap getBitmapForPaths(ArrayList<PathData> pathList, float left, float top, float right, float bottom) {
        float widthVal = right - left;
        float heightVal = bottom - top;

        // Determine padding based on maximum stroke width
        float maxStrokeWidth = strokeWidth;
        for (PathData pd : pathList) {
            if (pd.strokeWidth > maxStrokeWidth) {
                maxStrokeWidth = pd.strokeWidth;
            }
        }
        float padding = maxStrokeWidth * 0.8f;

        // Make the bitmap SQUARE to prevent vertical/horizontal aspect ratio distortion
        float maxDim = Math.max(widthVal, heightVal);
        float dx = (maxDim - widthVal) / 2f;
        float dy = (maxDim - heightVal) / 2f;

        int size = Math.max(1, (int) Math.ceil(maxDim + 2 * padding));

        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.BLACK);

        Paint modelPaint = new Paint();
        modelPaint.setAntiAlias(true);
        modelPaint.setStyle(Paint.Style.STROKE);
        modelPaint.setStrokeJoin(Paint.Join.ROUND);
        modelPaint.setStrokeCap(Paint.Cap.ROUND);
        modelPaint.setColor(Color.WHITE);

        canvas.save();
        // Centered translation inside the square bitmap with padding
        canvas.translate(-left + padding + dx, -top + padding + dy);

        for (PathData pd : pathList) {
            modelPaint.setStrokeWidth(pd.strokeWidth);
            canvas.drawPath(pd.path, modelPaint);
        }
        canvas.restore();

        return bitmap;
    }

    /**
     * Get bitmap specifically for model prediction.
     * Always returns black background with white strokes, cropped tightly to the drawn area.
     */
    public Bitmap getBitmapForModel() {
        if (paths.isEmpty()) {
            Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.BLACK);
            return bitmap;
        }

        // Compute overall bounds of all paths
        android.graphics.RectF bounds = new android.graphics.RectF();
        paths.get(0).path.computeBounds(bounds, true);
        android.graphics.RectF temp = new android.graphics.RectF();
        for (int i = 1; i < paths.size(); i++) {
            paths.get(i).path.computeBounds(temp, true);
            bounds.union(temp);
        }

        return getBitmapForPaths(paths, bounds.left, bounds.top, bounds.right, bounds.bottom);
    }

    public ArrayList<PathData> getPaths() {
        return paths;
    }

    public static class StrokeCluster {
        public ArrayList<PathData> paths = new ArrayList<>();
        public float left = Float.MAX_VALUE;
        public float right = -Float.MAX_VALUE;
        public float top = Float.MAX_VALUE;
        public float bottom = -Float.MAX_VALUE;

        public void addPath(PathData pd, android.graphics.RectF bounds) {
            paths.add(pd);
            if (bounds.left < left) left = bounds.left;
            if (bounds.right > right) right = bounds.right;
            if (bounds.top < top) top = bounds.top;
            if (bounds.bottom > bottom) bottom = bounds.bottom;
        }

        public boolean isOverlappingOrClose(PathData newPath, float threshold) {
            if (newPath.points == null || newPath.points.isEmpty()) {
                return isOverlappingOrCloseBoundingBox(newPath);
            }

            // 1. Kiểm tra khoảng cách điểm-đối-điểm thực tế (đối với nét giao nhau/chạm nhau như '+', 'x')
            float minDistanceSq = Float.MAX_VALUE;
            for (PathData clusterPath : this.paths) {
                if (clusterPath.points == null || clusterPath.points.isEmpty()) continue;
                for (android.graphics.PointF p1 : newPath.points) {
                    for (android.graphics.PointF p2 : clusterPath.points) {
                        float dx = p1.x - p2.x;
                        float dy = p1.y - p2.y;
                        float distSq = dx * dx + dy * dy;
                        if (distSq < minDistanceSq) {
                            minDistanceSq = distSq;
                        }
                    }
                }
            }

            if (minDistanceSq != Float.MAX_VALUE) {
                // Sử dụng ngưỡng khoảng cách từ tham số (threshold = strokeWidth * 0.75f) để gộp các nét vẽ
                float distanceThreshold = Math.max(threshold, newPath.strokeWidth * 0.8f);
                if (minDistanceSq <= (distanceThreshold * distanceThreshold)) {
                    return true;
                }
            }

            // 2. Kiểm tra phần chồng lấn nét song song xếp dọc (đặc biệt cho dấu bằng '=')
            android.graphics.RectF r1 = new android.graphics.RectF();
            newPath.path.computeBounds(r1, true);
            for (PathData clusterPath : this.paths) {
                android.graphics.RectF r2 = new android.graphics.RectF();
                clusterPath.path.computeBounds(r2, true);

                float overlapLeft = Math.max(r1.left, r2.left);
                float overlapRight = Math.min(r1.right, r2.right);
                float overlapW = overlapRight - overlapLeft;
                float minW = Math.min(r1.width(), r2.width());

                if (overlapW > minW * 0.5f) {
                    float verticalDist = 0;
                    if (r1.bottom < r2.top) {
                        verticalDist = r2.top - r1.bottom;
                    } else if (r2.bottom < r1.top) {
                        verticalDist = r1.top - r2.bottom;
                    }
                    if (verticalDist < newPath.strokeWidth * 0.8f) {
                        return true;
                    }
                }
            }

            return false;
        }

        private boolean isOverlappingOrCloseBoundingBox(PathData newPath) {
            android.graphics.RectF bounds = new android.graphics.RectF();
            newPath.path.computeBounds(bounds, true);

            float dx = 0;
            if (bounds.right < this.left) {
                dx = this.left - bounds.right;
            } else if (bounds.left > this.right) {
                dx = bounds.left - this.right;
            }

            float dy = 0;
            if (bounds.bottom < this.top) {
                dy = this.top - bounds.bottom;
            } else if (bounds.top > this.bottom) {
                dy = bounds.top - this.bottom;
            }

            double dist = Math.sqrt(dx * dx + dy * dy);
            float limitX = Math.max(8f, newPath.strokeWidth * 0.15f);
            float limitDist = Math.max(96f, newPath.strokeWidth * 1.5f);
            if (dx > limitX) {
                return false;
            }
            return dist <= limitDist;
        }

        public void merge(StrokeCluster other) {
            this.paths.addAll(other.paths);
            if (other.left < this.left) this.left = other.left;
            if (other.right > this.right) this.right = other.right;
            if (other.top < this.top) this.top = other.top;
            if (other.bottom > this.bottom) this.bottom = other.bottom;
        }
    }

    public ArrayList<StrokeCluster> getSegmentedClusters() {
        ArrayList<StrokeCluster> clusters = new ArrayList<>();
        android.graphics.RectF bounds = new android.graphics.RectF();
        float threshold = strokeWidth * 0.75f; // Ngưỡng khoảng cách tối đa để gộp nét vẽ gần nhau

        for (PathData pd : paths) {
            pd.path.computeBounds(bounds, true);

            ArrayList<StrokeCluster> overlapping = new ArrayList<>();
            for (StrokeCluster c : clusters) {
                if (c.isOverlappingOrClose(pd, threshold)) {
                    overlapping.add(c);
                }
            }

            if (overlapping.isEmpty()) {
                StrokeCluster newCluster = new StrokeCluster();
                newCluster.addPath(pd, bounds);
                clusters.add(newCluster);
            } else {
                StrokeCluster first = overlapping.get(0);
                first.addPath(pd, bounds);
                for (int i = 1; i < overlapping.size(); i++) {
                    StrokeCluster other = overlapping.get(i);
                    first.merge(other);
                    clusters.remove(other);
                }
            }
        }

        // Sắp xếp các cụm từ trái sang phải theo trục X
        java.util.Collections.sort(clusters, (c1, c2) -> Float.compare(c1.left, c2.left));
        return clusters;
    }

    public Bitmap getBitmapForCluster(StrokeCluster cluster) {
        return getBitmapForPaths(cluster.paths, cluster.left, cluster.top, cluster.right, cluster.bottom);
    }

    public boolean isEmpty() {
        return paths.isEmpty() && currentPath == null;
    }
}
