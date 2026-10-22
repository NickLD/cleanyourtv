package io.github.nickld.frigateglance;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One settings row: a name on the left, its value on the right; OK runs the row's action. */
final class SettingRow extends LinearLayout {
    private final TextView value;

    SettingRow(Context context, String name, String value, Runnable onClick) {
        super(context);
        float dp = context.getResources().getDisplayMetrics().density;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int ph = Math.round(16 * dp), pv = Math.round(10 * dp);
        setPadding(ph, pv, ph, pv);
        setBackgroundResource(R.drawable.row_background);
        setFocusable(onClick != null);
        setClickable(onClick != null);
        if (onClick != null) setOnClickListener(v -> onClick.run());
        TextView label = new TextView(context);
        label.setText(name);
        label.setTextSize(17);
        label.setTextColor(onClick != null ? 0xFFFFFFFF : 0xFF8B949E);
        addView(label, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        this.value = new TextView(context);
        this.value.setTextSize(17);
        this.value.setTextColor(0xFFC9D1D9);
        this.value.setText(value);
        addView(this.value, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    void setValue(String v) {
        value.setText(v);
    }
}
