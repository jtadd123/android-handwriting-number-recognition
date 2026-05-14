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
    private ArrayList<Path> paths = new ArrayList<>();
    private ArrayList<Path> undonePaths = new ArrayList<>();
    private Path currentPath;
    
    private float mX, mY;
    private static final float TOUCH_TOLERANCE = 4;

    private static final int PAINT_COLOR = Color.WHITE;
    private static final int BACKGROUND_COLOR = Color.BLACK;
    private static final float STROKE_WIDTH = 64f;

    private OnDrawListener drawListener;

    public interface OnDrawListener {
        void onDrawEnd();
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
        drawPaint.setColor(PAINT_COLOR);
        drawPaint.setAntiAlias(true);
        drawPaint.setStrokeWidth(STROKE_WIDTH);
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(BACKGROUND_COLOR);

        for (Path p : paths) {
            canvas.drawPath(p, drawPaint);
        }
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
        paths.add(currentPath);
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

    public boolean isEmpty() {
        return paths.isEmpty() && currentPath == null;
    }
}
