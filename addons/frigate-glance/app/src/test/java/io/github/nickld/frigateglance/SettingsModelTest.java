package io.github.nickld.frigateglance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class SettingsModelTest {
    private static final String FRIGATE = "{\"cameras\":{"
            + "\"driveway\":{\"review\":{\"alerts\":{\"labels\":[\"person\",\"car\"]}}},"
            + "\"shop\":{\"review\":{\"alerts\":{\"labels\":[\"person\"]}},\"objects\":{\"track\":[\"person\",\"dog\"]}},"
            + "\"frontdoooor\":{}}}";

    private static GlanceConfig config(String extra) {
        return GlanceConfig.parse("{\"mqtt\":\"mqtt://h\",\"frigateUrl\":\"https://h:8971\"" + extra + "}");
    }

    @Test
    public void camerasComeSortedFromFrigate() throws Exception {
        assertEquals(List.of("driveway", "frontdoooor", "shop"), SettingsModel.cameraNames(new JSONObject(FRIGATE)));
    }

    @Test
    public void labelsAreAlertLabelsAndTrackedObjects() throws Exception {
        assertEquals(List.of("car", "dog", "person"), SettingsModel.labelNames(new JSONObject(FRIGATE)));
    }

    @Test
    public void everyCameraIsOnWhenNoneAreListed() {
        assertTrue(SettingsModel.cameraOn(config(""), "shop"));
    }

    @Test
    public void turningOneCameraOffListsTheOthers() throws Exception {
        JSONObject cams = SettingsModel.toggleCamera(config(""), List.of("driveway", "frontdoooor", "shop"), "shop");
        assertEquals(2, cams.length());
        assertTrue(cams.has("driveway"));
        assertFalse(cams.has("shop"));
        assertEquals("Frontdoooor", cams.getString("frontdoooor"));
    }

    @Test
    public void turningACameraBackOnKeepsDisplayNames() throws Exception {
        GlanceConfig c = config(",\"cameras\":{\"driveway\":\"Drive\"}");
        JSONObject cams = SettingsModel.toggleCamera(c, List.of("driveway", "shop"), "shop");
        assertEquals("Drive", cams.getString("driveway"));
        assertEquals("Shop", cams.getString("shop"));
    }

    @Test
    public void allCamerasOnAgainStayListedWithTheirNames() throws Exception {
        GlanceConfig c = config(",\"cameras\":{\"driveway\":\"Drive\"}");
        JSONObject cams = SettingsModel.toggleCamera(c, List.of("driveway", "shop"), "shop");
        assertEquals(2, cams.length());
        assertEquals("Drive", cams.getString("driveway"));
    }

    @Test
    public void labelToggleWorksTheSameWay() throws Exception {
        JSONArray l = SettingsModel.toggleLabel(config(""), List.of("car", "person"), "car");
        assertEquals("[\"person\"]", l.toString());
        GlanceConfig c = config(",\"labels\":[\"person\"]");
        assertEquals(0, SettingsModel.toggleLabel(c, List.of("car", "person"), "car").length());
    }

    @Test
    public void lastCameraCannotBeTurnedOff() throws Exception {
        GlanceConfig c = config(",\"cameras\":{\"shop\":\"Shop\"}");
        JSONObject cams = SettingsModel.toggleCamera(c, List.of("driveway", "shop"), "shop");
        assertTrue(cams.has("shop"));
    }

    @Test
    public void cycleWrapsAround() {
        assertEquals("top-left", SettingsModel.next(List.of("top-right", "top-left"), "top-right"));
        assertEquals("top-right", SettingsModel.next(List.of("top-right", "top-left"), "top-left"));
        assertEquals("top-right", SettingsModel.next(List.of("top-right", "top-left"), "unknown"));
    }

    @Test
    public void keyCycleSkipsTheOtherKey() {
        assertEquals("KEYCODE_DPAD_UP", SettingsModel.nextKey("KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_RIGHT"));
        assertEquals("KEYCODE_DPAD_RIGHT", SettingsModel.nextKey("KEYCODE_PROG_GREEN", "KEYCODE_DPAD_LEFT"));
    }

    @Test
    public void durationsCycle() {
        assertEquals(Integer.valueOf(15), SettingsModel.next(SettingsModel.DISMISS_SECONDS, 8));
        assertEquals(Integer.valueOf(4), SettingsModel.next(SettingsModel.DISMISS_SECONDS, 30));
        assertEquals(Integer.valueOf(120), SettingsModel.next(SettingsModel.SNOOZE_MINUTES, 60));
        assertEquals(Integer.valueOf(15), SettingsModel.next(SettingsModel.SNOOZE_MINUTES, 240));
    }
}
