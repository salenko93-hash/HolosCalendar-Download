package ua.holoscalendar.redmi;

import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

final class ScreenInsets {
    private ScreenInsets() { }
    static void apply(View scroll) {
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(i.left, i.top, i.right, i.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        scroll.requestApplyInsets();
    }
}
