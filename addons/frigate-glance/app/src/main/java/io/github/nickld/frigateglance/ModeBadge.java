package io.github.nickld.frigateglance;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

/** The corner badge on the picture: "● LIVE" while live video plays, "FRAME" while it shows snapshots. */
final class ModeBadge extends TextView {
    private final GradientDrawable background = new GradientDrawable();

    ModeBadge(Context context, float textSp) {
        super(context);
        float dp = context.getResources().getDisplayMetrics().density;
        background.setCornerRadius(4 * dp * textSp / 10f);
        setBackground(background);
        setTextSize(textSp);
        setTextColor(0xFFFFFFFF);
        setLetterSpacing(0.08f);
        int h = Math.round(5 * dp * textSp / 10f), v = Math.round(2 * dp * textSp / 10f);
        setPadding(h, v, h, v);
        setLive(false);
    }

    void setLive(boolean live) {
        setText(live ? "● LIVE" : "FRAME");
        background.setColor(live ? 0xD9DC3C3C : 0x99000000);
    }
}
