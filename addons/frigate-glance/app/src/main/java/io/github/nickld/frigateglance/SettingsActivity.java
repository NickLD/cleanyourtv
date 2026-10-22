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
 * The tile and module card open this: a menu of sections on the left, that section's rows on the right.
 * Moving through the menu switches the right side; Right enters the rows, Left goes back to the menu. Every
 * row changes with OK. No text fields: addresses and passwords come from the pushed config file.
 */
public class SettingsActivity extends Activity {
    private static final long REFRESH_MS = 2000;
    private static final String[] SECTIONS = {"Overview", "Cameras", "Alerts", "Card", "Remote"};
    private static final Map<String, String> CORNER_NAMES = Map.of(
            "top-right", "Top right", "top-left", "Top left", "bottom-left", "Bottom left", "bottom-right", "Bottom right");

    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout menu, rows;
    private ConfigStore store;
    private TextView status;
    private SettingRow snoozeRow;
    private int section;
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
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFF0D1117);
        page.setPadding(px(48), px(32), px(48), px(24));
        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(28);
        title.setTextColor(0xFFFFFFFF);
        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(0xFF8B949E);
        status.setPadding(0, 0, 0, px(16));
        page.addView(title);
        page.addView(status);

        LinearLayout panes = new LinearLayout(this);
        panes.setOrientation(LinearLayout.HORIZONTAL);
        menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        panes.addView(menu, new LinearLayout.LayoutParams(px(220), ViewGroup.LayoutParams.WRAP_CONTENT));
        View divider = new View(this);
        divider.setBackgroundColor(0x22FFFFFF);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(px(1), ViewGroup.LayoutParams.MATCH_PARENT);
        dp.leftMargin = px(16);
        dp.rightMargin = px(16);
        panes.addView(divider, dp);
        ScrollView scroll = new ScrollView(this);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        panes.addView(scroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        page.addView(panes, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);

        for (int i = 0; i < SECTIONS.length; i++) {
            int index = i;
            SettingRow item = new SettingRow(this, SECTIONS[i], "", () -> focusFirstRow());
            item.setId(View.generateViewId());
            item.setOnFocusChangeListener((v, has) -> {
                if (has && section != index) {
                    section = index;
                    buildRows(-1);
                }
            });
            menu.addView(item, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        GlanceService.start(this);
        buildRows(-1);
        menu.getChildAt(0).requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // A config pushed while this screen was open: take it now and reconnect with it.
        if (store.importPushed()) {
            GlanceService.reload(this);
            buildRows(-1);
        }
        main.post(refresh);
    }

    @Override
    protected void onPause() {
        main.removeCallbacks(refresh);
        super.onPause();
    }

    /** Rebuilds the right side for the current section; focusIndex keeps focus on a row after a change. */
    private void buildRows(int focusIndex) {
        rows.removeAllViews();
        snoozeRow = null;
        GlanceConfig c = store.load();
        if (c == null) {
            rows.addView(new SettingRow(this, "No config yet", "push one with install.sh", null));
            return;
        }
        JSONObject frigate = GlanceService.frigateConfig();
        waitingForFrigate = frigate == null;
        switch (SECTIONS[section]) {
            case "Overview":
                snoozeRow = row("Snooze", "", () -> {
                    if (GlanceService.snoozedUntilWallClock() > 0) GlanceService.resume();
                    else GlanceService.snoozeCard();
                    main.postDelayed(this::updateLive, 200);
                });
                row("Show a test card", "", () -> GlanceService.showTestCard(this, null));
                break;
            case "Cameras":
                List<String> cameras = SettingsModel.cameraNames(frigate);
                if (cameras.isEmpty()) rows.addView(new SettingRow(this, "Cameras", "waiting for Frigate", null));
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
                break;
            case "Alerts":
                row("Show", "alerts".equals(c.severity) ? "Alerts only" : "Alerts and detections", () -> save(cfg ->
                        store.saveSetting("severity", SettingsModel.next(SettingsModel.SEVERITIES, cfg.severity))));
                List<String> labels = SettingsModel.labelNames(frigate);
                if (labels.isEmpty()) rows.addView(new SettingRow(this, "Objects", "waiting for Frigate", null));
                for (String label : labels) {
                    row(CardController.pretty(label), onOff(SettingsModel.labelOn(c, label)),
                            () -> save(cfg -> store.saveSetting("labels", SettingsModel.toggleLabel(cfg, labels, label))));
                }
                break;
            case "Card":
                row("Corner", CORNER_NAMES.get(c.corner),
                        () -> save(cfg -> store.saveSetting("corner", SettingsModel.next(SettingsModel.CORNERS, cfg.corner))));
                row("Card stays", c.dismissSeconds + " s after the event", () -> save(cfg -> store.saveSetting(
                        "dismissSeconds", SettingsModel.next(SettingsModel.DISMISS_SECONDS, cfg.dismissSeconds))));
                row("Live video", onOff(c.liveVideo), () -> save(cfg -> store.saveSetting("liveVideo", !cfg.liveVideo)));
                row("Boxes", onOff(c.boxes), () -> save(cfg -> store.saveSetting("boxes", !cfg.boxes)));
                break;
            default:
                row("Dismiss key", CardText.key(c.dismissKey), () -> save(cfg ->
                        store.saveSetting("dismissKey", SettingsModel.nextKey(cfg.dismissKey, cfg.snoozeKey))));
                row("Snooze key", CardText.key(c.snoozeKey), () -> save(cfg ->
                        store.saveSetting("snoozeKey", SettingsModel.nextKey(cfg.snoozeKey, cfg.dismissKey))));
                row("Snooze length", CardText.duration(c.snoozeMinutes), () -> save(cfg -> store.saveSetting(
                        "snoozeMinutes", SettingsModel.next(SettingsModel.SNOOZE_MINUTES, cfg.snoozeMinutes))));
                break;
        }
        // Left from any row goes back to this section's menu item; Right from the menu enters at the first row.
        int back = menu.getChildAt(section).getId();
        int first = View.NO_ID;
        for (int i = 0; i < rows.getChildCount(); i++) {
            View r = rows.getChildAt(i);
            r.setNextFocusLeftId(back);
            if (first == View.NO_ID && r.isFocusable()) {
                r.setId(View.generateViewId());
                first = r.getId();
            }
        }
        for (int i = 0; i < menu.getChildCount(); i++) {
            menu.getChildAt(i).setActivated(i == section);
            menu.getChildAt(i).setNextFocusRightId(first);
        }
        updateLive();
        if (focusIndex >= 0 && focusIndex < rows.getChildCount()) rows.getChildAt(focusIndex).requestFocus();
    }

    private void focusFirstRow() {
        for (int i = 0; i < rows.getChildCount(); i++) {
            if (rows.getChildAt(i).isFocusable()) {
                rows.getChildAt(i).requestFocus();
                return;
            }
        }
    }

    private interface Change {
        void apply(GlanceConfig current);
    }

    private void save(Change change) {
        GlanceConfig c = store.load();
        if (c == null) return;
        View focused = getCurrentFocus();
        int index = focused == null ? -1 : rows.indexOfChild(focused);
        change.apply(c);
        GlanceService.settingsChanged(this);
        buildRows(index);
    }

    private SettingRow row(String name, String value, Runnable onClick) {
        SettingRow r = new SettingRow(this, name, value, onClick);
        rows.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private void updateLive() {
        status.setText(GlanceService.statusLine());
        if (snoozeRow != null) {
            long until = GlanceService.snoozedUntilWallClock();
            snoozeRow.setValue(until > 0
                    ? "Snoozed until " + DateFormat.getTimeFormat(this).format(new Date(until)) + " · OK resumes"
                    : "Snooze now");
        }
        // The camera and object lists appear once Frigate's config has been read.
        if (waitingForFrigate && GlanceService.frigateConfig() != null) {
            View focused = getCurrentFocus();
            buildRows(focused == null ? -1 : rows.indexOfChild(focused));
        }
    }

    private int px(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private static String onOff(boolean on) {
        return on ? "On" : "Off";
    }
}
