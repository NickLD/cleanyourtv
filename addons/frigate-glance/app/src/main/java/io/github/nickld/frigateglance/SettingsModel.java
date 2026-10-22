package io.github.nickld.frigateglance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** The settings screen's choices, as values the config understands. No Android here, so it is unit-tested. */
final class SettingsModel {
    static final List<String> CORNERS = List.of("top-right", "top-left", "bottom-left", "bottom-right");
    static final List<String> SEVERITIES = List.of("alerts", "alerts_and_detections");
    static final List<Integer> DISMISS_SECONDS = List.of(4, 8, 15, 30);
    static final List<Integer> SNOOZE_MINUTES = List.of(15, 30, 60, 120, 240);
    static final List<String> KEYS = List.of("KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_RIGHT", "KEYCODE_DPAD_UP",
            "KEYCODE_DPAD_DOWN", "KEYCODE_MENU", "KEYCODE_INFO", "KEYCODE_PROG_RED", "KEYCODE_PROG_GREEN");

    private SettingsModel() {}

    static List<String> cameraNames(JSONObject frigate) {
        List<String> out = new ArrayList<>();
        JSONObject cams = frigate == null ? null : frigate.optJSONObject("cameras");
        if (cams != null) for (Iterator<String> it = cams.keys(); it.hasNext(); ) out.add(it.next());
        Collections.sort(out);
        return out;
    }

    /** Every label a camera alerts on or tracks. */
    static List<String> labelNames(JSONObject frigate) {
        Set<String> out = new TreeSet<>();
        JSONObject cams = frigate == null ? null : frigate.optJSONObject("cameras");
        if (cams == null) return new ArrayList<>(out);
        for (Iterator<String> it = cams.keys(); it.hasNext(); ) {
            JSONObject cam = cams.optJSONObject(it.next());
            if (cam == null) continue;
            JSONObject review = cam.optJSONObject("review");
            JSONObject alerts = review == null ? null : review.optJSONObject("alerts");
            out.addAll(Json.strings(alerts == null ? null : alerts.optJSONArray("labels")));
            JSONObject objects = cam.optJSONObject("objects");
            out.addAll(Json.strings(objects == null ? null : objects.optJSONArray("track")));
        }
        return new ArrayList<>(out);
    }

    static boolean cameraOn(GlanceConfig c, String camera) {
        return c.cameras.isEmpty() || c.cameras.containsKey(camera);
    }

    static boolean labelOn(GlanceConfig c, String label) {
        return c.labels.isEmpty() || c.labels.contains(label);
    }

    /** The "cameras" value after switching one camera; the last camera on cannot be switched off. */
    static JSONObject toggleCamera(GlanceConfig c, List<String> all, String camera) throws JSONException {
        JSONObject out = new JSONObject();
        for (String name : all) {
            boolean on = cameraOn(c, name);
            if (name.equals(camera)) on = !on;
            if (on) out.put(name, displayName(c, name));
        }
        if (out.length() == 0) out.put(camera, displayName(c, camera));
        return out;
    }

    /** The "labels" value after switching one label; empty means every label. */
    static JSONArray toggleLabel(GlanceConfig c, List<String> all, String label) {
        List<String> on = new ArrayList<>();
        for (String name : all) {
            boolean isOn = labelOn(c, name);
            if (name.equals(label)) isOn = !isOn;
            if (isOn) on.add(name);
        }
        return new JSONArray(on.isEmpty() || on.size() == all.size() ? Collections.emptyList() : on);
    }

    static <T> T next(List<T> values, T current) {
        int i = values.indexOf(current);
        return values.get((i + 1) % values.size());
    }

    static String nextKey(String current, String other) {
        String k = next(KEYS, current);
        return k.equals(other) ? next(KEYS, k) : k;
    }

    private static String displayName(GlanceConfig c, String camera) {
        String n = c.cameras.get(camera);
        return n != null && !n.isEmpty() ? n : CardController.pretty(camera);
    }
}
