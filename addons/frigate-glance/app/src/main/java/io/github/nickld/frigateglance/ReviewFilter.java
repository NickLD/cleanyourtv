package io.github.nickld.frigateglance;

/** Decides whether a review may open a card: severity, camera, objects and snooze. */
public final class ReviewFilter {
    private final GlanceConfig config;

    public ReviewFilter(GlanceConfig config) {
        this.config = config;
    }

    static final String REPLAY_PREFIX = "_replay_";

    /** Frigate's debug replay runs a recording as camera "_replay_<name>"; it counts as <name>. */
    static String baseCamera(String camera) {
        return camera.startsWith(REPLAY_PREFIX) ? camera.substring(REPLAY_PREFIX.length()) : camera;
    }

    public boolean shows(Review r, long nowMs, long snoozedUntilMs) {
        if (nowMs < snoozedUntilMs) return false;
        if (!"alert".equals(r.severity)
                && !("detection".equals(r.severity) && "alerts_and_detections".equals(config.severity))) {
            return false;
        }
        if (!config.cameras.isEmpty() && !config.cameras.containsKey(baseCamera(r.camera))) return false;
        if (config.labels.isEmpty()) return true;
        for (String o : r.objects) if (config.labels.contains(o)) return true;
        return false;
    }
}
