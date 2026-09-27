package com.example.togglemeaningtest;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/** Small always-on-top draggable bubble. */
public class BubbleView extends View {

    private final ToggleAccessibilityService service;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float downRawX;
    private float downRawY;
    private float startBubbleX;
    private float startBubbleY;
    private boolean moved;

    public BubbleView(ToggleAccessibilityService service) {
        super(service);
        this.service = service;
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setClickable(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) * 0.44f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(service.isToggleMode()
                ? Color.rgb(40, 190, 110)
                : Color.rgb(70, 120, 245));
        paint.setShadowLayer(9f, 0f, 4f, 0x66000000);
        canvas.drawCircle(cx, cy, r, paint);
        paint.clearShadowLayer();

        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(dp(18));
        canvas.drawText(service.isToggleMode() ? "T✓" : "T", cx, cy + dp(6), paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getRawX();
        float y = event.getRawY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = x;
                downRawY = y;
                startBubbleX = service.getBubbleX();
                startBubbleY = service.getBubbleY();
                moved = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!moved && distance(downRawX, downRawY, x, y) > service.getTouchSlop()) {
                    moved = true;
                }
                if (moved) {
                    service.moveBubble(
                            Math.round(startBubbleX + (x - downRawX)),
                            Math.round(startBubbleY + (y - downRawY))
                    );
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (!moved) {
                    service.toggleMode();
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                return true;

            default:
                return true;
        }
    }

    private float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private int dp(int v) {
        return Math.round(dp((float) v));
    }
}
