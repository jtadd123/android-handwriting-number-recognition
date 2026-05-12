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


public class DrawingView extends View {

    private Paint drawPaint;
    private Paint canvasPaint;
    private Path drawPath;
    private Canvas drawCanvas;
    private Bitmap canvasBitmap;

    private static final int PAINT_COLOR = Color.WHITE;
    private static final int BACKGROUND_COLOR = Color.BLACK;
    private static final float STROKE_WIDTH = 64f;

    public DrawingView(Context context) {
        super(context);
        init();
    }

    public DrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DrawingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        drawPath = new Path();

        
        drawPaint = new Paint();
        drawPaint.setColor(PAINT_COLOR);
        drawPaint.setAntiAlias(true);
        drawPaint.setStrokeWidth(STROKE_WIDTH);
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);

        
        canvasPaint = new Paint(Paint.DITHER_FLAG);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        drawCanvas = new Canvas(canvasBitmap);
        drawCanvas.drawColor(BACKGROUND_COLOR);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawBitmap(canvasBitmap, 0, 0, canvasPaint);
        canvas.drawPath(drawPath, drawPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float touchX = event.getX();
        float touchY = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                drawPath.moveTo(touchX, touchY);
                break;
            case MotionEvent.ACTION_MOVE:
                drawPath.lineTo(touchX, touchY);
                break;
            case MotionEvent.ACTION_UP:
                drawCanvas.drawPath(drawPath, drawPaint);
                drawPath.reset();
                break;
            default:
                return false;
        }

        invalidate();
        return true;
    }

    
    public void clearCanvas() {
        drawCanvas.drawColor(BACKGROUND_COLOR);
        drawPath.reset();
        invalidate();
    }

    
    public Bitmap getBitmap() {
        
        Bitmap resultBitmap = Bitmap.createBitmap(
                canvasBitmap.getWidth(), canvasBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas tempCanvas = new Canvas(resultBitmap);
        tempCanvas.drawBitmap(canvasBitmap, 0, 0, canvasPaint);
        tempCanvas.drawPath(drawPath, drawPaint);
        return resultBitmap;
    }

    
    public boolean isEmpty() {
        int[] pixels = new int[canvasBitmap.getWidth() * canvasBitmap.getHeight()];
        canvasBitmap.getPixels(pixels, 0, canvasBitmap.getWidth(),
                0, 0, canvasBitmap.getWidth(), canvasBitmap.getHeight());
        for (int pixel : pixels) {
            if (pixel != Color.BLACK && pixel != 0) {
                return false;
            }
        }
        return true;
    }
}
