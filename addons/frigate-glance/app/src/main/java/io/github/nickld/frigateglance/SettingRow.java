package io.github.nickld.frigateglance;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One settings row: a name with a line saying what it does on the left, its value on the right; OK runs it. */
final class SettingRow extends LinearLayout {
    private final TextView value;

    SettingRow(Context context, String name, String value, Runnable onClick) {
        this(context, name, null, value, onClick);
    }

    SettingRow(Context context, String name, String description, String value, Runnable onClick) {
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

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(VERTICAL);
        TextView label = new TextView(context);
        label.setText(name);
        label.setTextSize(17);
        label.setTextColor(onClick != null ? 0xFFFFFFFF : 0xFF8B949E);
        text.addView(label);
        if (description != null && !description.isEmpty()) {
            TextView desc = new TextView(context);
            desc.setText(description);
            desc.setTextSize(12);
            desc.setTextColor(0xFF8B949E);
            desc.setPadding(0, Math.round(2 * dp), Math.round(16 * dp), 0);
            text.addView(desc);
        }
        addView(text, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

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
