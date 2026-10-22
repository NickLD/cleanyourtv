package io.github.nickld.frigateglance;

import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * One tracked object from frigate/events: what it is, who or which plate, and where. The box is scaled to
 * 0..1 by the camera's detect size, as Peek's normalizeBox does, so it can be drawn over any picture size.
 */
public final class Detection {
    public final String id, camera, label, subLabel, plate;
    public final double score;
    /** x1, y1, x2, y2 as fractions of the frame, or null when the camera's detect size is unknown. */
    public final float[] box;
    public final List<String> zones;

    private Detection(JSONObject a, Map<String, int[]> detectSize) {
        id = a.optString("id", "");
        camera = a.optString("camera", "");
        label = a.optString("label", "");
        subLabel = subLabel(a.opt("sub_label"));
        String p = a.optString("recognized_license_plate", "");
        plate = p.isEmpty() || "null".equals(p) ? null : p;
        score = a.optDouble("score", a.optDouble("top_score", 0));
        box = box(a.optJSONArray("box"), detectSize.get(camera));
        zones = Json.strings(a.optJSONArray("entered_zones"));
    }

    /** The parsed message, or null when it is not an event message. */
    public static Detection parse(String payload, Map<String, int[]> detectSize) {
        try {
            JSONObject after = new JSONObject(payload).optJSONObject("after");
            if (after == null) return null;
            Detection d = new Detection(after, detectSize);
            return d.id.isEmpty() || d.camera.isEmpty() ? null : d;
        } catch (JSONException e) {
            return null;
        }
    }

    private static String subLabel(Object v) {
        if (v instanceof JSONArray) v = ((JSONArray) v).opt(0);
        if (v == null || v == JSONObject.NULL) return null;
        String s = v.toString();
        return s.isEmpty() ? null : s;
    }

    private static float[] box(JSONArray b, int[] size) {
        if (b == null || b.length() < 4 || size == null || size[0] <= 0 || size[1] <= 0) return null;
        return new float[] {
            (float) (b.optDouble(0) / size[0]), (float) (b.optDouble(1) / size[1]),
            (float) (b.optDouble(2) / size[0]), (float) (b.optDouble(3) / size[1]),
        };
    }
}
