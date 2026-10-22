package io.github.nickld.frigateglance;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class KeyRulesTest {
    private static final int LEFT = 21, RIGHT = 22, UP = 19, BACK = 4, DOWN = 0, UPA = 1;
    private final KeyRules rules = new KeyRules(LEFT, RIGHT);

    @Test
    public void nothingCaughtWithoutACard() {
        assertEquals(KeyRules.PASS, rules.decide(LEFT, DOWN, false));
        assertEquals(KeyRules.PASS, rules.decide(LEFT, UPA, false));
    }

    @Test
    public void dismissKeyIsSwallowedOnDownAndActsOnUp() {
        assertEquals(KeyRules.SWALLOW, rules.decide(LEFT, DOWN, true));
        assertEquals(KeyRules.DISMISS, rules.decide(LEFT, UPA, true));
    }

    @Test
    public void snoozeKeyActsOnUp() {
        assertEquals(KeyRules.SWALLOW, rules.decide(RIGHT, DOWN, true));
        assertEquals(KeyRules.SNOOZE, rules.decide(RIGHT, UPA, true));
    }

    @Test
    public void otherKeysPassWhileACardShows() {
        assertEquals(KeyRules.PASS, rules.decide(UP, DOWN, true));
        assertEquals(KeyRules.PASS, rules.decide(BACK, UPA, true));
    }

    @Test
    public void upWithoutItsDownStillPassesAfterTheCardWent() {
        // Down arrived while no card was up, so it went to the app; its up must follow it there.
        assertEquals(KeyRules.PASS, rules.decide(LEFT, DOWN, false));
        assertEquals(KeyRules.PASS, rules.decide(LEFT, UPA, true));
    }

    @Test
    public void upIsStillSwallowedIfTheCardLeftAfterDown() {
        assertEquals(KeyRules.SWALLOW, rules.decide(LEFT, DOWN, true));
        assertEquals(KeyRules.DISMISS, rules.decide(LEFT, UPA, false));
    }

    @Test
    public void unknownKeyCodesCatchNothing() {
        KeyRules none = new KeyRules(0, 0);
        assertEquals(KeyRules.PASS, none.decide(0, DOWN, true));
        assertEquals(KeyRules.PASS, none.decide(LEFT, DOWN, true));
    }

    @Test
    public void fullscreenKeyActsOnUpWhenSet() {
        KeyRules r = new KeyRules(LEFT, RIGHT, UP);
        assertEquals(KeyRules.SWALLOW, r.decide(UP, DOWN, true));
        assertEquals(KeyRules.FULLSCREEN, r.decide(UP, UPA, true));
    }

    @Test
    public void upPassesWhenFullscreenIsOff() {
        assertEquals(KeyRules.PASS, rules.decide(UP, DOWN, true));
    }
}
