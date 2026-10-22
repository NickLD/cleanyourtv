package io.github.nickld.frigateglance;

import java.util.List;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

/** One message from frigate/reviews: a review item starting (new), changing (update) or ending (end). */
public final class Review {
    private static final Set<String> TYPES = Set.of("new", "update", "end");

    public final String type, id, camera, severity;
    public final List<String> objects, zones, detectionIds;

    private Review(String type, JSONObject after) {
        this.type = type;
        id = after.optString("id", "");
        camera = after.optString("camera", "");
        severity = after.optString("severity", "");
        JSONObject data = after.optJSONObject("data");
        objects = Json.strings(data == null ? null : data.optJSONArray("objects"));
        zones = Json.strings(data == null ? null : data.optJSONArray("zones"));
        detectionIds = Json.strings(data == null ? null : data.optJSONArray("detections"));
    }

    /** The parsed message, or null when it is not a review message this add-on understands. */
    public static Review parse(String payload) {
        try {
            JSONObject o = new JSONObject(payload);
            String type = o.optString("type", "");
            JSONObject after = o.optJSONObject("after");
            if (!TYPES.contains(type) || after == null) return null;
            Review r = new Review(type, after);
            return r.id.isEmpty() || r.camera.isEmpty() || r.severity.isEmpty() ? null : r;
        } catch (JSONException e) {
            return null;
        }
    }
}
