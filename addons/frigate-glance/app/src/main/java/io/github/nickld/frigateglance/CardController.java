package io.github.nickld.frigateglance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * When a card shows, changes and leaves. One card at a time: a new matching review replaces it. It leaves
 * dismissSeconds after its review ends, and at the latest 120 s after its last news, so a lost "end"
 * message can never leave a card on screen. Not thread-safe: the service calls it from one thread.
 */
public final class CardController {
    static final long HARD_LIMIT_MS = 120_000;
    static final String TEST_ID = "test";
    private static final long TEST_EXTRA_MS = 10_000;

    private final CardView view;
    private final ReviewFilter filter;
    private final Map<String, String> displayNames;
    private final int dismissSeconds;
    private final LongSupplier clock;

    private Review shown;
    private final Map<String, Detection> detections = new LinkedHashMap<>();
    private long deadline;
    /** True once the shown card is counting down to leaving (its review ended, or a test card). */
    private boolean ending;
    private long snoozedUntil;

    public CardController(CardView view, ReviewFilter filter, Map<String, String> displayNames, int dismissSeconds,
            LongSupplier clock) {
        this.view = view;
        this.filter = filter;
        this.displayNames = displayNames;
        this.dismissSeconds = dismissSeconds;
        this.clock = clock;
    }

    public void onReview(Review r) {
        long now = clock.getAsLong();
        boolean isShown = shown != null && shown.id.equals(r.id);
        if ("new".equals(r.type) || ("update".equals(r.type) && !isShown)) {
            if (!"new".equals(r.type) || !filter.shows(r, now, snoozedUntil)) return;
            shown = r;
            detections.clear();
            deadline = now + HARD_LIMIT_MS;
            ending = false;
            view.show(model());
        } else if (isShown && "update".equals(r.type)) {
            shown = r;
            deadline = now + HARD_LIMIT_MS;
            ending = false;
            view.update(model());
        } else if (isShown && "end".equals(r.type)) {
            shown = r;
            deadline = now + dismissSeconds * 1000L;
            ending = true;
            view.update(model());
        }
    }

    public void onDetection(Detection d) {
        if (shown == null || !shown.detectionIds.contains(d.id)) return;
        detections.put(d.id, d);
        view.update(model());
    }

    public void onAvailable(boolean online) {
        if (!online) hideNow();
    }

    /** A card that does not come from Frigate, to check placement and the buttons. */
    public void showTest(String camera) {
        String after = "{\"id\":\"" + TEST_ID + "\",\"camera\":\"" + camera + "\",\"severity\":\"alert\",\"data\":{}}";
        shown = Review.parse("{\"type\":\"new\",\"after\":" + after + "}");
        detections.clear();
        deadline = clock.getAsLong() + dismissSeconds * 1000L + TEST_EXTRA_MS;
        ending = true;
        view.show(model());
    }

    public void dismiss() {
        hideNow();
    }

    public void snooze(int minutes) {
        snoozedUntil = clock.getAsLong() + minutes * 60_000L;
        hideNow();
    }

    public void resume() {
        snoozedUntil = 0;
    }

    public long snoozedUntil() {
        return snoozedUntil > clock.getAsLong() ? snoozedUntil : 0;
    }

    public boolean isShowing() {
        return shown != null;
    }

    /** Called once a second. */
    public void tick() {
        if (shown != null && clock.getAsLong() >= deadline) hideNow();
    }

    private void hideNow() {
        if (shown == null) return;
        shown = null;
        detections.clear();
        view.hide();
    }

    private CardModel model() {
        List<float[]> boxes = new ArrayList<>();
        for (Detection d : detections.values()) if (d.box != null) boxes.add(d.box);
        long countdown = ending ? Math.max(0, deadline - clock.getAsLong()) : 0;
        return new CardModel(shown.id, shown.camera, displayName(shown.camera), detail(), boxes, countdown);
    }

    private String detail() {
        if (TEST_ID.equals(shown.id)) return "Test card";
        Set<String> parts = new LinkedHashSet<>();
        for (String o : shown.objects) parts.add(pretty(o));
        for (Detection d : detections.values()) {
            if (d.subLabel != null) parts.add(d.subLabel);
            if (d.plate != null) parts.add(d.plate);
        }
        for (String z : shown.zones) parts.add(pretty(z));
        return String.join(" · ", parts);
    }

    private String displayName(String camera) {
        String n = displayNames.get(camera);
        return n != null && !n.isEmpty() ? n : pretty(camera);
    }

    /** Peek's prettyName: separators become spaces, each word capitalised. */
    static String pretty(String name) {
        StringBuilder out = new StringBuilder();
        for (String w : name.split("[_\\-\\s]+")) {
            if (w.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1));
        }
        return out.toString();
    }
}
