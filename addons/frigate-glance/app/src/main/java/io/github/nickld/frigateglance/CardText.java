package io.github.nickld.frigateglance;

import java.util.Locale;
import java.util.Map;

/** The card's footer: which button does what, in words a remote's owner recognises. */
final class CardText {
    private static final Map<String, String> SHORT = Map.of(
            "KEYCODE_DPAD_LEFT", "◀",
            "KEYCODE_DPAD_RIGHT", "▶",
            "KEYCODE_DPAD_UP", "▲",
            "KEYCODE_DPAD_DOWN", "▼",
            "KEYCODE_PROG_RED", "Red",
            "KEYCODE_PROG_GREEN", "Green",
            "KEYCODE_PROG_YELLOW", "Yellow",
            "KEYCODE_PROG_BLUE", "Blue");

    private CardText() {}

    static String footer(String dismissKey, String snoozeKey, int snoozeMinutes) {
        return footer(dismissKey, snoozeKey, snoozeMinutes, null);
    }

    /** fullscreenKey null while the full screen feature is off. */
    static String footer(String dismissKey, String snoozeKey, int snoozeMinutes, String fullscreenKey) {
        return footer(dismissKey, snoozeKey, snoozeMinutes, fullscreenKey, false, false, false);
    }

    /** "Hold" before a key that acts only when held. */
    static String footer(String dismissKey, String snoozeKey, int snoozeMinutes, String fullscreenKey,
            boolean dismissHold, boolean snoozeHold, boolean fullscreenHold) {
        String s = hold(dismissHold) + key(dismissKey) + " Dismiss · " + hold(snoozeHold) + key(snoozeKey) + " Snooze "
                + duration(snoozeMinutes);
        return fullscreenKey == null ? s : s + " · " + hold(fullscreenHold) + key(fullscreenKey) + " Full screen";
    }

    /** Start and end of one action's part of the footer ("Hold ▶ Snooze 1h"), for highlighting it; null if absent. */
    static int[] segment(String footer, int action) {
        String word = action == KeyRules.DISMISS ? " Dismiss" : action == KeyRules.SNOOZE ? " Snooze " : " Full screen";
        int start = 0;
        while (start <= footer.length()) {
            int end = footer.indexOf(" · ", start);
            if (end < 0) end = footer.length();
            if ((footer.substring(start, end) + " ").contains(word)) {
                return new int[] {start, end};
            }
            start = end + 3;
        }
        return null;
    }

    private static String hold(boolean on) {
        return on ? "Hold " : "";
    }

    static String duration(int minutes) {
        return minutes % 60 == 0 ? (minutes / 60) + "h" : minutes + " min";
    }

    static String key(String name) {
        String s = SHORT.get(name);
        if (s != null) return s;
        String[] words = name.replaceFirst("^KEYCODE_", "").toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return out.toString();
    }
}
