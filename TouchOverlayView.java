package com.example.togglemeaningtest;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * Full-screen transparent touch catcher used only while toggle mode is ON.
 * It captures a tap, asks the AccessibilityService which text is under the
 * coordinate, then replays the tap with dispatchGesture so the original app
 * still receives it.
 */
public class TouchOverlayView extends View {

    private final ToggleAccessibilityService service;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float downX;
    private float downY;
    private boolean downOnBubble;
    private boolean moved;
    private float bubbleOffsetX;
    private float bubbleOffsetY;

    private String resultText = "";
    private long resultUntil = 0L;
    private float resultX;
    private float resultY;

    public TouchOverlayView(ToggleAccessibilityService service) {
        super(service);
        this.service = service;
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setFocusable(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (resultText.isEmpty() || System.currentTimeMillis() > resultUntil) {
            return;
        }

        paint.setTypeface(PaintHolder.BOLD);
        paint.setTextSize(dp(18));
        paint.setTextAlign(Paint.Align.LEFT);

        String line = resultText;
        float padX = dp(14);
        float padY = dp(10);
        float boxW = paint.measureText(line) + padX * 2f;
        float boxH = dp(44);

        float left = resultX + dp(14);
        float top = resultY - boxH - dp(14);

        if (left + boxW > getWidth() - dp(6)) {
            left = getWidth() - boxW - dp(6);
        }
        if (left < dp(6)) left = dp(6);
        if (top < dp(6)) top = resultY + dp(14);
        if (top + boxH > getHeight() - dp(6)) {
            top = getHeight() - boxH - dp(6);
        }

        paint.setColor(0xEE111111);
        paint.setStyle(Paint.Style.FILL);
        paint.setShadowLayer(10f, 0f, 4f, 0x77000000);
        canvas.drawRoundRect(new RectF(left, top, left + boxW, top + boxH), dp(12), dp(12), paint);
        paint.clearShadowLayer();

        paint.setColor(Color.WHITE);
        canvas.drawText(line, left + padX, top + boxH - padY, paint);

        postInvalidateDelayed(120);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getRawX();
        float y = event.getRawY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                moved = false;
                downOnBubble = service.isInsideBubble(x, y);

                if (downOnBubble) {
                    bubbleOffsetX = x - service.getBubbleX();
                    bubbleOffsetY = y - service.getBubbleY();
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (distance(downX, downY, x, y) > service.getTouchSlop()) {
                    moved = true;
                }

                if (downOnBubble && moved) {
                    service.moveBubble(
                            Math.round(x - bubbleOffsetX),
                            Math.round(y - bubbleOffsetY)
                    );
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (downOnBubble) {
                    if (!moved) {
                        service.toggleMode();
                    }
                    return true;
                }

                if (moved) {
                    replaySwipe(downX, downY, x, y);
                } else {
                    service.handleWordTap(x, y);
                    replayTap(x, y);
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                return true;

            default:
                return true;
        }
    }

    public void showResult(String text, float x, float y) {
        resultText = text;
        resultX = x;
        resultY = y;
        resultUntil = System.currentTimeMillis() + 2600L;
        invalidate();
    }

    public void clearResult() {
        resultText = "";
        invalidate();
    }

    private void replayTap(float x, float y) {
        service.replayTap(x, y);
    }

    private void replaySwipe(float x1, float y1, float x2, float y2) {
        service.replaySwipe(x1, y1, x2, y2);
    }

    private float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private static final class PaintHolder {
        static final android.graphics.Typeface BOLD =
                android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,
                        android.graphics.Typeface.BOLD);
    }
}
