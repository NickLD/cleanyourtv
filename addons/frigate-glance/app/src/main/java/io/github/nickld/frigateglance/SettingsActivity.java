package io.github.nickld.frigateglance;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** The tile and module card open this: the add-on's settings. */
public class SettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        setContentView(title);
    }
}
