package io.github.nickld.frigateglance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class GlanceConfigTest {
    private static final String REQUIRED =
            "\"mqtt\":\"mqtt://u:p@192.168.4.56:1883\",\"frigateUrl\":\"https://192.168.4.58:8971\"";
    private static final String FULL = "{" + REQUIRED
            + ",\"frigateUser\":\"viewer\",\"frigatePassword\":\"pw\",\"topicPrefix\":\"frigate\""
            + ",\"rtspUser\":\"r\",\"rtspPassword\":\"rp\",\"cameras\":{\"frontdoooor\":\"Front door\"}"
            + ",\"labels\":[\"person\"],\"severity\":\"alerts_and_detections\",\"corner\":\"top-left\""
            + ",\"dismissSeconds\":15,\"snoozeMinutes\":30,\"liveVideo\":false,\"boxes\":false"
            + ",\"dismissKey\":\"KEYCODE_MENU\",\"snoozeKey\":\"KEYCODE_INFO\"}";

    private static String requiredPlus(String extra) {
        return "{" + REQUIRED + "," + extra + "}";
    }

    @Test
    public void defaultsApplyWhenOnlyRequiredKeysGiven() {
        GlanceConfig c = GlanceConfig.parse("{" + REQUIRED + "}");
        assertEquals("frigate", c.topicPrefix);
        assertEquals("alerts", c.severity);
        assertEquals("top-right", c.corner);
        assertEquals(8, c.dismissSeconds);
        assertEquals(60, c.snoozeMinutes);
        assertTrue(c.liveVideo);
        assertTrue(c.boxes);
        assertTrue(c.cameras.isEmpty());
        assertTrue(c.labels.isEmpty());
        assertEquals("KEYCODE_DPAD_LEFT", c.dismissKey);
        assertEquals("KEYCODE_DPAD_RIGHT", c.snoozeKey);
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingMqttIsRejected() {
        GlanceConfig.parse("{\"frigateUrl\":\"https://h:8971\"}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingFrigateUrlIsRejected() {
        GlanceConfig.parse("{\"mqtt\":\"mqtt://h:1883\"}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidJsonIsRejected() {
        GlanceConfig.parse("not json");
    }

    @Test(expected = IllegalArgumentException.class)
    public void backAsDismissKeyIsRejected() {
        GlanceConfig.parse(requiredPlus("\"dismissKey\":\"KEYCODE_BACK\""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void okAsSnoozeKeyIsRejected() {
        GlanceConfig.parse(requiredPlus("\"snoozeKey\":\"KEYCODE_DPAD_CENTER\""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void enterAsSnoozeKeyIsRejected() {
        GlanceConfig.parse(requiredPlus("\"snoozeKey\":\"KEYCODE_ENTER\""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void sameKeyTwiceIsRejected() {
        GlanceConfig.parse(requiredPlus("\"dismissKey\":\"KEYCODE_MENU\",\"snoozeKey\":\"KEYCODE_MENU\""));
    }

    @Test
    public void outOfRangeNumbersAreClamped() {
        GlanceConfig c = GlanceConfig.parse(requiredPlus("\"dismissSeconds\":999,\"snoozeMinutes\":1"));
        assertEquals(120, c.dismissSeconds);
        assertEquals(5, c.snoozeMinutes);
    }

    @Test
    public void unknownCornerFallsBackToTopRight() {
        assertEquals("top-right", GlanceConfig.parse(requiredPlus("\"corner\":\"middle\"")).corner);
    }

    @Test
    public void unknownSeverityFallsBackToAlerts() {
        assertEquals("alerts", GlanceConfig.parse(requiredPlus("\"severity\":\"everything\"")).severity);
    }

    @Test
    public void readsEveryKey() {
        GlanceConfig c = GlanceConfig.parse(FULL);
        assertEquals("viewer", c.frigateUser);
        assertEquals("Front door", c.cameras.get("frontdoooor"));
        assertTrue(c.labels.contains("person"));
        assertEquals("alerts_and_detections", c.severity);
        assertEquals("top-left", c.corner);
        assertEquals(15, c.dismissSeconds);
        assertEquals(30, c.snoozeMinutes);
        assertFalse(c.liveVideo);
        assertFalse(c.boxes);
        assertEquals("KEYCODE_MENU", c.dismissKey);
        assertEquals("rp", c.rtspPassword);
    }

    @Test
    public void roundTripsThroughJson() {
        GlanceConfig c = GlanceConfig.parse(FULL);
        assertEquals(c.toJson(), GlanceConfig.parse(c.toJson()).toJson());
    }

    @Test
    public void overridesFromSettingsWin() throws Exception {
        GlanceConfig c = GlanceConfig.parse(FULL)
                .withOverrides(new JSONObject("{\"liveVideo\":false,\"corner\":\"bottom-left\"}"));
        assertFalse(c.liveVideo);
        assertEquals("bottom-left", c.corner);
        assertEquals("viewer", c.frigateUser);
    }

    @Test(expected = IllegalArgumentException.class)
    public void overridesAreValidatedToo() throws Exception {
        GlanceConfig.parse(FULL).withOverrides(new JSONObject("{\"dismissKey\":\"KEYCODE_BACK\"}"));
    }
}
