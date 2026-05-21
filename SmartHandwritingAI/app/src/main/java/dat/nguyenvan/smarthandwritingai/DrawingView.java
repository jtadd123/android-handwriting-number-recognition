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
    
    private float mX, mY;
    private static final float TOUCH_TOLERANCE = 4;

    private int paintColor = Color.WHITE;
    private static final int BACKGROUND_COLOR = Color.BLACK;
    private float strokeWidth = 64f;

    private OnDrawListener drawListener;

    public interface OnDrawListener {
        void onDrawEnd();
    }

    // Inner class to store path with its paint properties
    private static class PathData {
        Path path;
        int color;
        float strokeWidth;

        PathData(Path path, int color, float strokeWidth) {
            this.path = path;
            this.color = color;
            this.strokeWidth = strokeWidth;
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
        mX = x;
        mY = y;
    }

    private void touchMove(float x, float y) {
        float dx = Math.abs(x - mX);
        float dy = Math.abs(y - mY);
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            currentPath.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2);
            mX = x;
            mY = y;
        }
    }

    private void touchUp() {
        currentPath.lineTo(mX, mY);
        paths.add(new PathData(currentPath, paintColor, strokeWidth));
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
     * Get bitmap specifically for model prediction.
     * Always returns black background with white strokes,
     * regardless of user's brush color settings.
     * This ensures the ML model always receives consistent input.
     */
    public Bitmap getBitmapForModel() {
        Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.BLACK);

        // Draw all paths in WHITE regardless of their original color
        Paint modelPaint = new Paint();
        modelPaint.setAntiAlias(true);
        modelPaint.setStyle(Paint.Style.STROKE);
        modelPaint.setStrokeJoin(Paint.Join.ROUND);
        modelPaint.setStrokeCap(Paint.Cap.ROUND);
        modelPaint.setColor(Color.WHITE);

        for (PathData pd : paths) {
            modelPaint.setStrokeWidth(pd.strokeWidth);
            canvas.drawPath(pd.path, modelPaint);
        }
        return bitmap;
    }

    public boolean isEmpty() {
        return paths.isEmpty() && currentPath == null;
    }
}
