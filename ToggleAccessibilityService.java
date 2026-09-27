package com.example.togglemeaningtest;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.View;
import android.graphics.PixelFormat;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Test AccessibilityService for Toggle Meaning.
 *
 * Core test flow:
 *   bubble -> ON -> capture screen tap -> find accessibility node under point
 *   -> locate character/word -> show it -> replay original tap -> bubble OFF.
 */
public class ToggleAccessibilityService extends AccessibilityService {

    private static final String PREFS = "toggle_meaning_test";
    private static final String KEY_BUBBLE_X = "bubble_x";
    private static final String KEY_BUBBLE_Y = "bubble_y";

    private WindowManager windowManager;
    private BubbleView bubbleView;
    private WindowManager.LayoutParams bubbleParams;
    private TouchOverlayView touchOverlay;
    private WindowManager.LayoutParams touchParams;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean toggleMode;
    private boolean destroyed;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        destroyed = false;

        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 40;
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.flags |= AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        setServiceInfo(info);

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createBubble();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // The test intentionally does not auto-read the whole screen.
        // We only inspect the node at the user's explicit tap coordinate.
    }

    @Override
    public void onInterrupt() {
        // Nothing to interrupt.
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        toggleMode = false;
        removeTouchOverlay();
        removeBubble();
        super.onDestroy();
    }

    public boolean isToggleMode() {
        return toggleMode;
    }

    public int getTouchSlop() {
        return ViewConfiguration.get(this).getScaledTouchSlop();
    }

    public int getBubbleX() {
        return bubbleParams == null ? 0 : bubbleParams.x;
    }

    public int getBubbleY() {
        return bubbleParams == null ? 0 : bubbleParams.y;
    }

    public void toggleMode() {
        if (destroyed || windowManager == null) return;

        toggleMode = !toggleMode;
        if (toggleMode) {
            showTouchOverlay();
        } else {
            removeTouchOverlay();
        }

        if (bubbleView != null) {
            bubbleView.invalidate();
        }
    }

    public boolean isInsideBubble(float rawX, float rawY) {
        if (bubbleParams == null || bubbleView == null) return false;
        int size = bubbleView.getWidth() > 0 ? bubbleView.getWidth() : bubbleParams.width;
        return rawX >= bubbleParams.x && rawX < bubbleParams.x + size
                && rawY >= bubbleParams.y && rawY < bubbleParams.y + size;
    }

    public void moveBubble(int x, int y) {
        if (bubbleParams == null || bubbleView == null || windowManager == null) return;

        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        int size = bubbleParams.width;

        int maxX = Math.max(0, width - size);
        int maxY = Math.max(0, height - size);

        bubbleParams.x = clamp(x, 0, maxX);
        bubbleParams.y = clamp(y, 0, maxY);

        try {
            windowManager.updateViewLayout(bubbleView, bubbleParams);
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_BUBBLE_X, bubbleParams.x)
                    .putInt(KEY_BUBBLE_Y, bubbleParams.y)
                    .apply();
        } catch (Exception ignored) {
        }
    }

    /** Called by the transparent overlay after the user taps the screen. */
    public void handleWordTap(float rawX, float rawY) {
        if (touchOverlay == null) return;

        WordResult result = findWordAt((int) rawX, (int) rawY);
        if (result == null) {
            touchOverlay.showResult("✕ no accessible text", rawX, rawY);
            return;
        }

        String shown = result.exact ? "✓ " + result.word : "~ " + result.word;
        touchOverlay.showResult(shown, rawX, rawY);
    }

    /** Replays the original tap so the underlying app still receives it. */
    public void replayTap(float rawX, float rawY) {
        Path path = new Path();
        path.moveTo(rawX, rawY);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 1);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();

        temporarilyDisableTouchCapture(90L);
        dispatchGesture(gesture, null, handler);
    }

    /** Replays a simple straight-line swipe for basic scrolling in the test. */
    public void replaySwipe(float x1, float y1, float x2, float y2) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);

        float distance = (float) Math.hypot(x2 - x1, y2 - y1);
        long duration = Math.max(120L, Math.min(450L, (long) (distance * 0.7f)));

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, duration);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();

        temporarilyDisableTouchCapture(duration + 40L);
        dispatchGesture(gesture, null, handler);
    }

    private void temporarilyDisableTouchCapture(long restoreAfterMs) {
        if (touchOverlay == null || touchParams == null || windowManager == null) return;

        touchParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        try {
            windowManager.updateViewLayout(touchOverlay, touchParams);
        } catch (Exception ignored) {
        }

        handler.postDelayed(() -> {
            if (destroyed || touchOverlay == null || touchParams == null || windowManager == null) return;
            if (!toggleMode) return;

            touchParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            try {
                windowManager.updateViewLayout(touchOverlay, touchParams);
            } catch (Exception ignored) {
            }
        }, restoreAfterMs);
    }

    private void createBubble() {
        if (bubbleView != null || windowManager == null) return;

        int size = dp(58);
        int savedX = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(KEY_BUBBLE_X, dp(12));
        int savedY = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(KEY_BUBBLE_Y, dp(160));

        bubbleView = new BubbleView(this);
        bubbleParams = new WindowManager.LayoutParams(
                size,
                size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = savedX;
        bubbleParams.y = savedY;
        bubbleParams.alpha = 1f;

        try {
            windowManager.addView(bubbleView, bubbleParams);
        } catch (Exception e) {
            bubbleView = null;
            bubbleParams = null;
        }
    }

    private void showTouchOverlay() {
        if (touchOverlay != null || windowManager == null) return;

        touchOverlay = new TouchOverlayView(this);
        touchParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        touchParams.gravity = Gravity.TOP | Gravity.START;
        touchParams.alpha = 1f;

        try {
            // Add after the bubble so it can catch the entire screen. It explicitly
            // checks the bubble rectangle and routes bubble taps to toggle/drag logic.
            windowManager.addView(touchOverlay, touchParams);
        } catch (Exception e) {
            touchOverlay = null;
            touchParams = null;
            toggleMode = false;
            if (bubbleView != null) bubbleView.invalidate();
        }
    }

    private void removeTouchOverlay() {
        if (touchOverlay == null || windowManager == null) return;
        try {
            windowManager.removeView(touchOverlay);
        } catch (Exception ignored) {
        }
        touchOverlay = null;
        touchParams = null;
    }

    private void removeBubble() {
        if (bubbleView == null || windowManager == null) return;
        try {
            windowManager.removeView(bubbleView);
        } catch (Exception ignored) {
        }
        bubbleView = null;
        bubbleParams = null;
    }

    private WordResult findWordAt(int screenX, int screenY) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;

        AccessibilityNodeInfo node = findBestTextNode(root, screenX, screenY, new Counter());
        if (node == null) return null;

        CharLocationResult exact = getWordFromCharacterLocations(node, screenX, screenY);
        if (exact != null) {
            return new WordResult(exact.word, true);
        }

        CharLocationResult approximate = approximateWord(node, screenX, screenY);
        if (approximate != null) {
            return new WordResult(approximate.word, false);
        }

        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            return new WordResult(text.toString().trim(), false);
        }
        return null;
    }

    private AccessibilityNodeInfo findBestTextNode(
            AccessibilityNodeInfo node,
            int x,
            int y,
            Counter counter
    ) {
        if (node == null || counter.value++ > 3500) return null;

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (!bounds.contains(x, y)) return null;

        AccessibilityNodeInfo best = hasText(node) ? node : null;
        int bestArea = best == null ? Integer.MAX_VALUE : area(bounds);

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = null;
            try {
                child = node.getChild(i);
            } catch (Exception ignored) {
            }

            if (child == null) continue;
            AccessibilityNodeInfo candidate = findBestTextNode(child, x, y, counter);
            if (candidate == null) continue;

            Rect candidateBounds = new Rect();
            candidate.getBoundsInScreen(candidateBounds);
            int candidateArea = area(candidateBounds);
            if (candidateArea <= bestArea) {
                best = candidate;
                bestArea = candidateArea;
            }
        }

        return best;
    }

    private CharLocationResult getWordFromCharacterLocations(
            AccessibilityNodeInfo node,
            int x,
            int y
    ) {
        if (Build.VERSION.SDK_INT < 26) return null;

        CharSequence cs = node.getText();
        if (cs == null || cs.length() == 0) return null;
        String text = cs.toString();

        try {
            String key = AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY;
            Bundle args = new Bundle();
            args.putInt(
                    AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX,
                    0
            );
            args.putInt(
                    AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH,
                    text.length()
            );

            node.refreshWithExtraData(key, args);
            Bundle extras = node.getExtras();
            extras.setClassLoader(RectF.class.getClassLoader());

            ArrayList<RectF> locations;
            if (Build.VERSION.SDK_INT >= 33) {
                locations = extras.getParcelableArrayList(key, RectF.class);
            } else {
                locations = extras.getParcelableArrayList(key);
            }

            if (locations == null || locations.isEmpty()) return null;

            int charIndex = -1;
            float bestDistance = Float.MAX_VALUE;

            for (int i = 0; i < locations.size() && i < text.length(); i++) {
                RectF r = locations.get(i);
                if (r == null || r.isEmpty()) continue;

                if (r.contains(x, y)) {
                    charIndex = i;
                    break;
                }

                float cx = r.centerX();
                float cy = r.centerY();
                float dx = cx - x;
                float dy = cy - y;
                float d = dx * dx + dy * dy;
                if (d < bestDistance) {
                    bestDistance = d;
                    charIndex = i;
                }
            }

            if (charIndex < 0 || charIndex >= text.length()) return null;
            String word = extractWord(text, charIndex);
            if (word.isEmpty()) return null;
            return new CharLocationResult(word);

        } catch (Exception ignored) {
            return null;
        }
    }

    /** Fallback for apps that expose text but not per-character rectangles. */
    private CharLocationResult approximateWord(
            AccessibilityNodeInfo node,
            int x,
            int y
    ) {
        CharSequence cs = node.getText();
        if (cs == null || cs.length() == 0) return null;

        String text = cs.toString();
        Rect b = new Rect();
        node.getBoundsInScreen(b);
        if (!b.contains(x, y)) return null;

        if (!containsWordSeparator(text)) {
            return new CharLocationResult(text.trim());
        }

        String[] lines = text.split("\\n", -1);
        int lineIndex;
        if (lines.length <= 1 || b.height() <= 0) {
            lineIndex = 0;
        } else {
            float row = (y - b.top) / (float) b.height();
            lineIndex = clamp((int) (row * lines.length), 0, lines.length - 1);
        }

        String line = lines[lineIndex];
        if (line.isEmpty()) return null;

        float col = b.width() <= 0 ? 0f : (x - b.left) / (float) b.width();
        int charIndex = clamp(Math.round(col * (line.length() - 1)), 0, line.length() - 1);
        String word = extractWord(line, charIndex);
        if (word.isEmpty()) return null;
        return new CharLocationResult(word);
    }

    private String extractWord(String text, int charIndex) {
        if (text == null || text.isEmpty()) return "";
        charIndex = clamp(charIndex, 0, text.length() - 1);

        if (!isWordChar(text.charAt(charIndex))) {
            int left = charIndex - 1;
            int right = charIndex + 1;
            while (left >= 0 || right < text.length()) {
                if (left >= 0 && isWordChar(text.charAt(left))) {
                    charIndex = left;
                    break;
                }
                if (right < text.length() && isWordChar(text.charAt(right))) {
                    charIndex = right;
                    break;
                }
                left--;
                right++;
            }
        }

        if (!isWordChar(text.charAt(charIndex))) return "";

        int start = charIndex;
        int end = charIndex;
        while (start > 0 && isWordChar(text.charAt(start - 1))) start--;
        while (end + 1 < text.length() && isWordChar(text.charAt(end + 1))) end++;
        return text.substring(start, end + 1);
    }

    private boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '\'' || c == '-';
    }

    private boolean containsWordSeparator(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i)) || text.charAt(i) == ','
                    || text.charAt(i) == '.' || text.charAt(i) == ';'
                    || text.charAt(i) == ':' || text.charAt(i) == '!' 
                    || text.charAt(i) == '?') {
                return true;
            }
        }
        return false;
    }

    private boolean hasText(AccessibilityNodeInfo node) {
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        return (text != null && text.length() > 0)
                || (desc != null && desc.length() > 0);
    }

    private int area(Rect r) {
        long a = (long) r.width() * (long) r.height();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, a));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class Counter {
        int value;
    }

    private static final class CharLocationResult {
        final String word;
        CharLocationResult(String word) {
            this.word = word;
        }
    }

    private static final class WordResult {
        final String word;
        final boolean exact;
        WordResult(String word, boolean exact) {
            this.word = word;
            this.exact = exact;
        }
    }
}
