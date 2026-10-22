package io.github.nickld.frigateglance;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * The tile and module card open this: status, snooze, a test card, and every choice as a row the remote can
 * change with OK. No text fields: addresses and passwords come from the pushed config file.
 */
public class SettingsActivity extends Activity {
    private static final long REFRESH_MS = 2000;
    private static final Map<String, String> CORNER_NAMES = Map.of(
            "top-right", "Top right", "top-left", "Top left", "bottom-left", "Bottom left", "bottom-right", "Bottom right");

    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout rows;
    private ConfigStore store;
    private TextView status;
    private SettingRow snoozeRow;
    private boolean waitingForFrigate;
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            updateLive();
            main.postDelayed(this, REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ConfigStore(this);
        float dp = getResources().getDisplayMetrics().density;
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF0D1117);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(48 * dp);
        page.setPadding(pad, Math.round(32 * dp), pad, pad);
        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(28);
        title.setTextColor(0xFFFFFFFF);
        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(0xFF8B949E);
        status.setPadding(0, 0, 0, Math.round(12 * dp));
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        page.addView(title);
        page.addView(status);
        page.addView(rows);
        scroll.addView(page);
        setContentView(scroll);
        GlanceService.start(this);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        main.post(refresh);
    }

    @Override
    protected void onPause() {
        main.removeCallbacks(refresh);
        super.onPause();
    }

    private void build() {
        View focused = getCurrentFocus();
        int focusIndex = focused == null ? -1 : rows.indexOfChild(focused);
        rows.removeAllViews();
        GlanceConfig c = store.load();
        if (c == null) {
            rows.addView(new SettingRow(this, "No config yet", "push one with install.sh", null));
            return;
        }
        snoozeRow = row("Snooze", "", () -> {
            if (GlanceService.snoozedUntilWallClock() > 0) GlanceService.resume();
            else GlanceService.snoozeCard();
            main.postDelayed(this::updateLive, 200);
        });
        row("Show a test card", "", () -> GlanceService.showTestCard(this, null));

        JSONObject frigate = GlanceService.frigateConfig();
        waitingForFrigate = frigate == null;
        List<String> cameras = SettingsModel.cameraNames(frigate);
        heading("Cameras");
        if (cameras.isEmpty()) {
            rows.addView(new SettingRow(this, "Cameras", "waiting for Frigate", null));
        }
        for (String cam : cameras) {
            String name = c.cameras.containsKey(cam) ? c.cameras.get(cam) : CardController.pretty(cam);
            row(name, onOff(SettingsModel.cameraOn(c, cam)), () -> save(cfg -> {
                try {
                    store.saveSetting("cameras", SettingsModel.toggleCamera(cfg, cameras, cam));
                } catch (JSONException ignored) {
                    // unchanged
                }
            }));
        }
        heading("What opens a card");
        row("Show", "alerts".equals(c.severity) ? "Alerts only" : "Alerts and detections",
                () -> save(cfg -> store.saveSetting("severity", SettingsModel.next(SettingsModel.SEVERITIES, cfg.severity))));
        List<String> labels = SettingsModel.labelNames(frigate);
        for (String label : labels) {
            row(CardController.pretty(label), onOff(SettingsModel.labelOn(c, label)),
                    () -> save(cfg -> store.saveSetting("labels", SettingsModel.toggleLabel(cfg, labels, label))));
        }
        heading("Card");
        row("Corner", CORNER_NAMES.get(c.corner),
                () -> save(cfg -> store.saveSetting("corner", SettingsModel.next(SettingsModel.CORNERS, cfg.corner))));
        row("Card stays", c.dismissSeconds + " s after the event", () -> save(cfg ->
                store.saveSetting("dismissSeconds", SettingsModel.next(SettingsModel.DISMISS_SECONDS, cfg.dismissSeconds))));
        row("Snooze length", CardText.duration(c.snoozeMinutes), () -> save(cfg ->
                store.saveSetting("snoozeMinutes", SettingsModel.next(SettingsModel.SNOOZE_MINUTES, cfg.snoozeMinutes))));
        row("Live video", onOff(c.liveVideo), () -> save(cfg -> store.saveSetting("liveVideo", !cfg.liveVideo)));
        row("Boxes", onOff(c.boxes), () -> save(cfg -> store.saveSetting("boxes", !cfg.boxes)));
        heading("Remote");
        row("Dismiss key", CardText.key(c.dismissKey),
                () -> save(cfg -> store.saveSetting("dismissKey", SettingsModel.nextKey(cfg.dismissKey, cfg.snoozeKey))));
        row("Snooze key", CardText.key(c.snoozeKey),
                () -> save(cfg -> store.saveSetting("snoozeKey", SettingsModel.nextKey(cfg.snoozeKey, cfg.dismissKey))));

        updateLive();
        int target = focusIndex >= 0 && focusIndex < rows.getChildCount() ? focusIndex : 0;
        for (int i = target; i < rows.getChildCount(); i++) {
            if (rows.getChildAt(i).isFocusable()) {
                rows.getChildAt(i).requestFocus();
                break;
            }
        }
    }

    private interface Change {
        void apply(GlanceConfig current);
    }

    private void save(Change change) {
        GlanceConfig c = store.load();
        if (c == null) return;
        change.apply(c);
        GlanceService.settingsChanged(this);
        build();
    }

    private SettingRow row(String name, String value, Runnable onClick) {
        SettingRow r = new SettingRow(this, name, value, onClick);
        rows.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private void updateLive() {
        status.setText(GlanceService.statusLine());
        if (snoozeRow == null) return;
        long until = GlanceService.snoozedUntilWallClock();
        snoozeRow.setValue(until > 0
                ? "Snoozed until " + DateFormat.getTimeFormat(this).format(new Date(until)) + " · OK resumes"
                : "Snooze now");
        // The camera and object lists appear once Frigate's config has been read.
        if (waitingForFrigate && GlanceService.frigateConfig() != null) build();
    }

    private void heading(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(0xFF3DDC84);
        float dp = getResources().getDisplayMetrics().density;
        t.setPadding(Math.round(16 * dp), Math.round(18 * dp), 0, Math.round(4 * dp));
        rows.addView(t);
    }

    private static String onOff(boolean on) {
        return on ? "On" : "Off";
    }
}
