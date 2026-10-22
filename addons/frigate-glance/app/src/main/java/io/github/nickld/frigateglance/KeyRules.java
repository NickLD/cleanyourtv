package io.github.nickld.frigateglance;

/**
 * Which remote keys the card catches: only its two keys, only while it shows. A key's up goes wherever
 * its down went, so an app never sees half a press.
 */
final class KeyRules {
    static final int PASS = 0, SWALLOW = 1, DISMISS = 2, SNOOZE = 3;
    private static final int ACTION_DOWN = 0;

    private final int dismissCode, snoozeCode;
    private int held;

    /** Key codes from KeyEvent.keyCodeFromString; 0 (KEYCODE_UNKNOWN) catches nothing. */
    KeyRules(int dismissCode, int snoozeCode) {
        this.dismissCode = dismissCode;
        this.snoozeCode = snoozeCode;
    }

    int decide(int keyCode, int action, boolean cardShowing) {
        if (keyCode == 0 || (keyCode != dismissCode && keyCode != snoozeCode)) return PASS;
        if (action == ACTION_DOWN) {
            if (!cardShowing) return PASS;
            held = keyCode;
            return SWALLOW;
        }
        if (held != keyCode) return PASS;
        held = 0;
        return keyCode == dismissCode ? DISMISS : SNOOZE;
    }

    /** True while a caught key is down, so filtering must stay on for its up. */
    boolean holding() {
        return held != 0;
    }
}
