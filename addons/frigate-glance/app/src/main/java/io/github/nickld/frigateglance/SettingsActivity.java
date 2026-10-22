package io.github.nickld.frigateglance;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The tile and module card open this: the add-on's settings. For now: status and test cards. */
public class SettingsActivity extends Activity {
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(48 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(28);
        status = new TextView(this);
        status.setTextSize(18);
        Button test = new Button(this);
        test.setText("Show a test card");
        test.setOnClickListener(v -> GlanceService.showTestCard(this, null));
        Button missing = new Button(this);
        missing.setText("Test card for a missing camera");
        missing.setOnClickListener(v -> GlanceService.showTestCard(this, "nosuchcamera"));
        root.addView(title);
        root.addView(status);
        root.addView(test);
        root.addView(missing);
        setContentView(root);
        GlanceService.reload(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        status.setText(GlanceService.statusLine());
    }
}
