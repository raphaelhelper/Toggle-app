package com.example.togglemeaningtest;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Tiny setup screen. The actual floating bubble lives in the AccessibilityService.
 */
public class MainActivity extends Activity {

    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));

        TextView title = new TextView(this);
        title.setText("Toggle Meaning — test 1");
        title.setTextSize(23f);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, matchWrap());

        statusView = new TextView(this);
        statusView.setTextSize(16f);
        statusView.setPadding(0, dp(18), 0, dp(18));
        statusView.setMovementMethod(new ScrollingMovementMethod());
        root.addView(statusView, matchWrap());

        Button settings = new Button(this);
        settings.setText("Mở Accessibility Settings");
        settings.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        root.addView(settings, matchWrap());

        TextView info = new TextView(this);
        info.setText(
                "TEST NÀY CHỈ KIỂM TRA 1 VIỆC:\n\n" +
                "1) Bật service \"Toggle Meaning Test\" trong Accessibility.\n" +
                "2) Một bong bóng T sẽ nổi trên màn hình.\n" +
                "3) Chạm bong bóng 1 lần → ON.\n" +
                "4) Khi ON, chạm vào một word/text trên app khác.\n" +
                "5) APK sẽ cố lấy đúng word tại vị trí chạm và hiện kết quả cạnh đó.\n" +
                "6) Chạm bong bóng lần nữa → OFF.\n\n" +
                "Bản test dùng Accessibility + accessibility overlay. Không có dictionary ở bước này."
        );
        info.setTextSize(15f);
        info.setPadding(0, dp(18), 0, 0);
        info.setMovementMethod(new ScrollingMovementMethod());
        root.addView(info, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        if (isServiceEnabled()) {
            statusView.setText("✅ Accessibility service đang BẬT.\n" +
                    "Ra màn hình chính/app khác để thấy bong bóng T.");
        } else {
            statusView.setText("⚪ Accessibility service chưa BẬT.\n" +
                    "Bấm nút bên dưới và bật \"Toggle Meaning Test\".");
        }
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabled == null) return false;

        ComponentName component = new ComponentName(this, ToggleAccessibilityService.class);
        String target = component.flattenToString();
        String targetShort = component.flattenToShortString();

        String[] services = enabled.split(":");
        for (String service : services) {
            if (target.equalsIgnoreCase(service) || targetShort.equalsIgnoreCase(service)) {
                return true;
            }
        }
        return false;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
