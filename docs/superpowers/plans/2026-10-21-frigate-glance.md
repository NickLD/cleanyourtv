# Frigate Glance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An Android TV add-on that pops a Frigate camera card (snapshot, live video, boxes) into a corner over whatever is playing, with remote-button dismiss and snooze.

**Architecture:** One foreground service holds a hand-written, subscribe-only MQTT 3.1.1 connection to `frigate/reviews`, `frigate/events` and `frigate/available`. Pure-Java classes (config, message parsing, filtering, a card state machine) decide what to show and are unit-tested on the JVM. Android-only classes draw the card as a non-focusable overlay window, play live video with Media3, and catch two remote keys via an accessibility service that filters keys only while a card is up.

**Tech Stack:** Java 17, Android Gradle Plugin 8.5.2, Gradle 8.9 wrapper, compileSdk/targetSdk 34, minSdk 26, AndroidX Media3 ExoPlayer + RTSP, JUnit 4 and `org.json:json` (tests only).

**Spec:** `docs/superpowers/specs/2026-10-21-frigate-glance-addon-design.md`

## Global Constraints

- Folder `addons/frigate-glance/`, its own Gradle project (`settings.gradle`, `app/`, `gradlew`), never referenced from `launcher/`, never referencing `launcher/` (tests/test_addons.py enforces this).
- Package and applicationId `io.github.nickld.frigateglance`; app label and card label `Frigate Glance`.
- Java only, minSdk 26, compileSdk 34, targetSdk 34; builds with JDK 17 (CI) and JDK 21 (owner's PC).
- Module card exactly per `docs/modules.md`: `<card spec="1" label="Frigate Glance" caption="Camera pop-ups" accent="#3DDC84" />` on the activity with `LEANBACK_LAUNCHER`.
- No APKs committed, no telemetry, no update checks, no polling; the only network peers are the configured MQTT broker and Frigate host.
- README has a `## Permissions` heading listing every permission, the network access and the foreground service, says it is a community add-on not made by or affiliated with the Frigate project, and credits Peek (MIT).
- Defaults: severity alerts only, all cameras, all labels, corner `top-right`, `dismissSeconds` 8, `snoozeMinutes` 60, `liveVideo` true, `boxes` true.
- The two caught keys are never `KEYCODE_BACK` or `KEYCODE_DPAD_CENTER`/`KEYCODE_ENTER`; any other key is never consumed; no key is consumed while no card is shown.
- Test TV: TCL 65Q650G at `192.168.10.193`, via `tools/tv.py` with `MSYS_NO_PATHCONV=1` in Git Bash; real-remote checks for every key behaviour (injected keys never reach accessibility services).

## Review Focus

- MQTT broker or Frigate restarts mid-evening: the service reconnects with backoff (1, 2, 4 … 60 s) and never crashes or leaves a card stuck. Pinned in Task 3 and Task 6.
- A review's `end` message never arrives: the card still leaves, 120 s after its last update. Pinned in Task 5.
- An event for a camera with no detect size known (config fetch failed): no boxes drawn, the card still shows. Pinned in Task 4.
- Frigate's login token expires (default session 24 h): one re-login on HTTP 401, then the request is retried once. Pinned in Task 6.
- The snapshot fetch fails or is slow: the card shows with its text and an empty dark picture area rather than waiting or failing. Pinned in Task 7.

---

### Task 1: Project scaffold, module card and README skeleton

**Files:**
- Create: `addons/frigate-glance/settings.gradle`, `build.gradle`, `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*` (copy the launcher's wrapper files unchanged; Gradle 8.9 with its SHA-256)
- Create: `addons/frigate-glance/app/build.gradle`, `app/proguard-rules.pro`
- Create: `addons/frigate-glance/app/src/main/AndroidManifest.xml`
- Create: `addons/frigate-glance/app/src/main/java/io/github/nickld/frigateglance/SettingsActivity.java` (empty screen with the app name for now)
- Create: `addons/frigate-glance/app/src/main/res/xml/module_card.xml`, `res/values/strings.xml`, `res/drawable/banner.xml` (own art: a simple vector, no Frigate or Peek logo), `res/mipmap` icon
- Create: `addons/frigate-glance/README.md` with headings `## What it does`, `## Permissions`, `## Setup`, `## Config keys`, `## Tested on`, `## Credits`
- Create: `addons/frigate-glance/.gitignore` (`.gradle/`, `build/`, `app/build/`, `local.properties`)
- Modify: `addons/README.md` (replace the "None yet." row area with a row: `| [Frigate Glance](frigate-glance/) | Frigate camera pop-ups over whatever is playing | NickLD | TCL 65Q650G | overlay, accessibility (two keys), network, foreground service |`)

**Interfaces:**
- Produces: Gradle project that later tasks add to; `app/build.gradle` dependencies: `androidx.media3:media3-exoplayer:1.4.1`, `androidx.media3:media3-exoplayer-rtsp:1.4.1`, `androidx.media3:media3-ui:1.4.1`; `testImplementation 'junit:junit:4.13.2'`, `testImplementation 'org.json:json:20231013'`. Release build minified and signed with the debug key, like the launcher.

- [ ] **Step 1:** Run `python -m unittest tests.test_addons -v` from the repo root. Expected: PASS before any change (no add-ons yet).
- [ ] **Step 2:** Create the files above. Manifest: `SettingsActivity` exported with `MAIN`+`LEANBACK_LAUNCHER`, a second filter `MAIN`+`com.gathin.tvhome.category.DOCK_CARD`, and `<meta-data android:name="com.gathin.tvhome.card" android:resource="@xml/module_card" />`; `android:banner="@drawable/banner"`; `<uses-feature android:name="android.software.leanback" android:required="false"/>` and touchscreen not required.
- [ ] **Step 3:** Run `cd addons/frigate-glance && ./gradlew assembleRelease` with `JAVA_HOME` set to the JDK. Expected: `BUILD SUCCESSFUL`, APK in `app/build/outputs/apk/release/`.
- [ ] **Step 4:** Run `python -m unittest tests.test_addons -v`. Expected: PASS (own Gradle project, README with Permissions, table row, no APKs, launcher untouched).
- [ ] **Step 5:** Commit: `git add addons/ && git commit -m "addons: frigate-glance scaffold and module card"`

### Task 2: Live video spike on the TV (throwaway)

Decides how live video reaches the card; the spec says to spike RTSP on the 65Q650G before building on it.

**Files:**
- Create (throwaway, not committed): `addons/frigate-glance/app/src/debug/java/io/github/nickld/frigateglance/SpikeActivity.java` and a debug-only manifest entry
- Modify: `docs/superpowers/specs/2026-10-21-frigate-glance-addon-design.md` (record the outcome under "Left to the builder")

- [ ] **Step 1:** Write `SpikeActivity`: full-screen Media3 `PlayerView` playing `rtsp://192.168.4.58:8554/frontdoooor_1` via `RtspMediaSource.Factory().setForceUseRtpTcp(true)`, logging (tag `FrigateGlance`) the milliseconds from `prepare()` to the first `Player.STATE_READY`, and decoder name from `onVideoInputFormatChanged`.
- [ ] **Step 2:** Build `assembleDebug`, install with `tools/tv.py --push` + `pm install -r`, start with `am start -n io.github.nickld.frigateglance/.SpikeActivity`.
- [ ] **Step 3:** Pull a screenshot (`screencap -p /data/local/tmp/s.png`, `--pull` to `backup/`) and read `logcat -d -s FrigateGlance`. Pass: picture moving, ready in under 3000 ms, hardware decoder (`OMX.realtek.*` or `c2.realtek.*`). Also note `dumpsys meminfo io.github.nickld.frigateglance` TOTAL PSS while playing.
- [ ] **Step 4:** If RTSP fails (auth required, port blocked, no picture): try `rtsp://user:pass@…` if go2rtc has RTSP credentials; if it still fails, record "RTSP not usable; live video off by default, snapshots refresh every 1 s" as the decision. Either way, write the result, timings and memory into the spec, uninstall the debug build, delete the spike files.
- [ ] **Step 5:** Commit the spec change only: `git commit -m "docs: frigate-glance live video spike result" -- docs/superpowers/specs/2026-10-21-frigate-glance-addon-design.md`

### Task 3: Config model and import

**Files:**
- Create: `app/src/main/java/io/github/nickld/frigateglance/GlanceConfig.java`
- Create: `app/src/main/java/io/github/nickld/frigateglance/ConfigStore.java`
- Test: `app/src/test/java/io/github/nickld/frigateglance/GlanceConfigTest.java`

(All paths in Tasks 3 to 11 are under `addons/frigate-glance/`.)

**Interfaces:**
- Produces: `final class GlanceConfig` with public final fields `String mqttUrl, frigateUrl, frigateUser, frigatePassword, topicPrefix, rtspUser, rtspPassword, corner, severity, dismissKey, snoozeKey, certSha256; Map<String,String> cameras; Set<String> labels; int dismissSeconds, snoozeMinutes; boolean liveVideo, boxes`; `static GlanceConfig parse(String json) throws IllegalArgumentException`; `String toJson()`; `GlanceConfig withOverrides(JSONObject settings)`.
- Produces: `ConfigStore(Context)` with `GlanceConfig load()`, `boolean importPushed()` (reads `getExternalFilesDir(null)/config.json`, saves it privately, deletes the pushed file, returns true when one was imported), `void saveSetting(String key, Object value)`, `void saveCertPin(String sha256)`.
- Key names, values and defaults: `mqtt` (required, `mqtt://user:pass@host:port`), `frigateUrl` (required), `frigateUser`, `frigatePassword`, `topicPrefix` = `frigate`, `rtspUser`, `rtspPassword`, `cameras` = `{}` (name → display name; empty = all), `labels` = `[]` (empty = all), `severity` = `alerts` (or `alerts_and_detections`), `corner` = `top-right` (`top-left`, `bottom-right`, `bottom-left`), `dismissSeconds` = 8 (1–120), `snoozeMinutes` = 60 (5–720), `liveVideo` = true, `boxes` = true, `dismissKey` = `KEYCODE_DPAD_LEFT`, `snoozeKey` = `KEYCODE_DPAD_RIGHT`.

- [ ] **Step 1: Write the failing tests**

```java
@Test public void defaultsApplyWhenOnlyRequiredKeysGiven() {
    GlanceConfig c = GlanceConfig.parse("{\"mqtt\":\"mqtt://u:p@192.168.4.56:1883\",\"frigateUrl\":\"https://192.168.4.58:8971\"}");
    assertEquals("frigate", c.topicPrefix); assertEquals("alerts", c.severity);
    assertEquals("top-right", c.corner); assertEquals(8, c.dismissSeconds); assertEquals(60, c.snoozeMinutes);
    assertTrue(c.liveVideo); assertTrue(c.boxes); assertTrue(c.cameras.isEmpty()); assertTrue(c.labels.isEmpty());
    assertEquals("KEYCODE_DPAD_LEFT", c.dismissKey); assertEquals("KEYCODE_DPAD_RIGHT", c.snoozeKey);
}
@Test(expected = IllegalArgumentException.class) public void missingMqttIsRejected() { GlanceConfig.parse("{\"frigateUrl\":\"https://h:8971\"}"); }
@Test(expected = IllegalArgumentException.class) public void backAsDismissKeyIsRejected() { GlanceConfig.parse(REQUIRED_PLUS("\"dismissKey\":\"KEYCODE_BACK\"")); }
@Test(expected = IllegalArgumentException.class) public void okAsSnoozeKeyIsRejected() { GlanceConfig.parse(REQUIRED_PLUS("\"snoozeKey\":\"KEYCODE_DPAD_CENTER\"")); }
@Test(expected = IllegalArgumentException.class) public void sameKeyTwiceIsRejected() { GlanceConfig.parse(REQUIRED_PLUS("\"dismissKey\":\"KEYCODE_MENU\",\"snoozeKey\":\"KEYCODE_MENU\"")); }
@Test public void outOfRangeNumbersAreClamped() {
    GlanceConfig c = GlanceConfig.parse(REQUIRED_PLUS("\"dismissSeconds\":999,\"snoozeMinutes\":1"));
    assertEquals(120, c.dismissSeconds); assertEquals(5, c.snoozeMinutes);
}
@Test public void unknownCornerFallsBackToTopRight() { assertEquals("top-right", GlanceConfig.parse(REQUIRED_PLUS("\"corner\":\"middle\"")).corner); }
@Test public void roundTripsThroughJson() { GlanceConfig c = GlanceConfig.parse(FULL); assertEquals(c.toJson(), GlanceConfig.parse(c.toJson()).toJson()); }
@Test public void overridesFromSettingsWin() {
    GlanceConfig c = GlanceConfig.parse(FULL).withOverrides(new JSONObject("{\"liveVideo\":false,\"corner\":\"bottom-left\"}"));
    assertFalse(c.liveVideo); assertEquals("bottom-left", c.corner);
}
```

(`REQUIRED_PLUS(extra)` and `FULL` are test helpers: the two required keys plus `extra`; and a file using every key.)

- [ ] **Step 2:** Run `./gradlew testReleaseUnitTest --tests '*GlanceConfigTest'`. Expected: FAIL, `GlanceConfig` does not exist.
- [ ] **Step 3:** Implement `GlanceConfig` with `org.json`. Key names are checked against `KeyEvent.keyCodeFromString` only on the device (in `KeyService`, Task 9); here only the forbidden names `KEYCODE_BACK`, `KEYCODE_DPAD_CENTER`, `KEYCODE_ENTER` and duplicates are rejected. Implement `ConfigStore` with private `files/config.json` plus `SharedPreferences("settings")` holding overrides as one JSON string. Importing a pushed file clears the overrides (the spec: settings win until the file is pushed again).
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5:** Commit: `git commit -m "frigate-glance: config model and import"`

### Task 4: Frigate messages and filtering

**Files:**
- Create: `app/src/main/java/io/github/nickld/frigateglance/Review.java`, `Detection.java`, `ReviewFilter.java`
- Test: `app/src/test/java/io/github/nickld/frigateglance/ReviewTest.java`, `ReviewFilterTest.java`, fixtures in `app/src/test/resources/` (`review_new.json`, `review_update.json`, `review_end.json`, `event_update.json`, `event_sublabel_array.json`)

**Interfaces:**
- Produces: `final class Review { String type /* new|update|end */, id, camera, severity /* alert|detection */; List<String> objects, zones, detectionIds; static Review parse(String payload) }` (returns null for malformed payloads).
- Produces: `final class Detection { String id, camera, label, subLabel, plate; double score; float[] box /* normalised x1,y1,x2,y2 or null */; List<String> zones; static Detection parse(String payload, Map<String,int[]> detectSize) }` — `subLabel` taken from a string or from element 0 of an array; `box` divided by the camera's `{width,height}` from `detectSize`, null when the camera is missing there.
- Produces: `final class ReviewFilter { ReviewFilter(GlanceConfig c); boolean shows(Review r, long nowMs, long snoozedUntilMs) }`.

- [ ] **Step 1: Write the failing tests** (fixtures are real Frigate 0.18 payload shapes: `{"type":…,"before":{…},"after":{"id","camera","severity","data":{"detections":[…],"objects":[…],"zones":[…],"sub_labels":[…]}}}` for reviews and `{"type":…,"after":{"id","camera","label","sub_label","score","top_score","box":[x1,y1,x2,y2],"entered_zones":[…],"recognized_license_plate":…}}` for events)

```java
@Test public void parsesNewAlert() { Review r = Review.parse(fixture("review_new.json")); assertEquals("new", r.type); assertEquals("frontdoooor", r.camera); assertEquals("alert", r.severity); assertEquals(List.of("person"), r.objects); }
@Test public void malformedReviewIsNull() { assertNull(Review.parse("{\"type\":\"new\"}")); assertNull(Review.parse("not json")); }
@Test public void boxIsNormalisedByDetectSize() { Detection d = Detection.parse(fixture("event_update.json"), Map.of("driveway", new int[]{1280,720})); assertArrayEquals(new float[]{0.5f,0.25f,0.75f,1.0f}, d.box, 1e-4f); }
@Test public void unknownDetectSizeMeansNoBox() { assertNull(Detection.parse(fixture("event_update.json"), Map.of()).box); }
@Test public void subLabelArrayTakesFirstElement() { assertEquals("Nick", Detection.parse(fixture("event_sublabel_array.json"), Map.of()).subLabel); }
// ReviewFilterTest
@Test public void detectionHiddenWhenAlertsOnly() { assertFalse(filter("{}").shows(review("detection","driveway","car"), NOW, 0)); }
@Test public void detectionShownWhenAlertsAndDetections() { assertTrue(filter("{\"severity\":\"alerts_and_detections\"}").shows(review("detection","driveway","car"), NOW, 0)); }
@Test public void cameraNotListedIsHidden() { assertFalse(filter("{\"cameras\":{\"frontdoooor\":\"Front door\"}}").shows(review("alert","shop","person"), NOW, 0)); }
@Test public void labelFilterNeedsOneMatchingObject() { assertTrue(filter("{\"labels\":[\"person\"]}").shows(review("alert","driveway","car","person"), NOW, 0)); assertFalse(filter("{\"labels\":[\"person\"]}").shows(review("alert","driveway","car"), NOW, 0)); }
@Test public void snoozedHidesEverythingUntilItExpires() { assertFalse(filter("{}").shows(review("alert","driveway","person"), NOW, NOW + 1)); assertTrue(filter("{}").shows(review("alert","driveway","person"), NOW, NOW)); }
```

- [ ] **Step 2:** Run `./gradlew testReleaseUnitTest --tests '*Review*'`. Expected: FAIL, classes missing.
- [ ] **Step 3:** Implement the three classes with `org.json`.
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5:** Commit: `git commit -m "frigate-glance: Frigate review and event parsing, filter"`

### Task 5: Card state machine

**Files:**
- Create: `app/src/main/java/io/github/nickld/frigateglance/CardController.java`
- Test: `app/src/test/java/io/github/nickld/frigateglance/CardControllerTest.java`

**Interfaces:**
- Consumes: `Review`, `Detection`, `ReviewFilter` (Task 4).
- Produces: `interface CardView { void show(CardModel m); void update(CardModel m); void hide(); }`; `final class CardModel { String reviewId, camera, title /* display name */, detail /* e.g. "Person · Nick · entire_front_yard" */; List<float[]> boxes; }`.
- Produces: `final class CardController { CardController(CardView view, ReviewFilter filter, Map<String,String> displayNames, int dismissSeconds, LongSupplier clock); void onReview(Review r); void onDetection(Detection d); void onAvailable(boolean online); void dismiss(); void snooze(int minutes); void resume(); long snoozedUntil(); boolean isShowing(); void tick(); }` — `tick()` is called once a second by the service and hides the card when its deadline passes.
- Rules: `new` passing the filter shows (or replaces the current card); `update` for the shown id refreshes; `end` for the shown id sets the deadline to now + `dismissSeconds`; any shown card also gets a hard deadline of 120 s after its last `new`/`update`; detections only feed the shown review's `detectionIds`; `onAvailable(false)` hides; `dismiss()` hides; `snooze()` hides and sets `snoozedUntil`; display name = `displayNames.get(camera)` or the camera name with `_`/`-` as spaces and each word capitalised (Peek's `prettyName`).

- [ ] **Step 1: Write the failing tests** with a fake `CardView` recording calls and a mutable clock:

```java
@Test public void newAlertShowsCard() { c.onReview(rev("new","r1","frontdoooor","alert")); assertEquals(List.of("show:r1"), view.calls); }
@Test public void secondReviewReplacesFirst() { c.onReview(rev("new","r1",…)); c.onReview(rev("new","r2",…)); assertEquals("show:r2", view.last()); assertTrue(c.isShowing()); }
@Test public void endHidesAfterDismissSeconds() { c.onReview(rev("new","r1",…)); c.onReview(rev("end","r1",…)); clock.add(7_999); c.tick(); assertTrue(c.isShowing()); clock.add(2); c.tick(); assertFalse(c.isShowing()); }
@Test public void missingEndHidesAfter120s() { c.onReview(rev("new","r1",…)); clock.add(120_001); c.tick(); assertFalse(c.isShowing()); }
@Test public void detectionAddsBoxToShownCard() { c.onReview(revWithDetection("r1","ev1")); c.onDetection(det("ev1", new float[]{0,0,.5f,.5f})); assertEquals(1, view.lastModel.boxes.size()); }
@Test public void detectionForOtherReviewIgnored() { c.onReview(revWithDetection("r1","ev1")); c.onDetection(det("ev9", BOX)); assertEquals(0, view.lastModel.boxes.size()); }
@Test public void snoozeHidesAndBlocksNewCards() { c.onReview(rev("new","r1",…)); c.snooze(60); assertFalse(c.isShowing()); c.onReview(rev("new","r2",…)); assertFalse(c.isShowing()); clock.add(60*60_000); c.onReview(rev("new","r3",…)); assertTrue(c.isShowing()); }
@Test public void resumeEndsSnooze() { c.snooze(60); c.resume(); c.onReview(rev("new","r1",…)); assertTrue(c.isShowing()); }
@Test public void frigateOfflineHidesCard() { c.onReview(rev("new","r1",…)); c.onAvailable(false); assertFalse(c.isShowing()); }
@Test public void prettyNameWithoutDisplayName() { c.onReview(rev("new","r1","front_door","alert")); assertEquals("Front Door", view.lastModel.title); }
```

- [ ] **Step 2:** Run `./gradlew testReleaseUnitTest --tests '*CardControllerTest'`. Expected: FAIL.
- [ ] **Step 3:** Implement `CardController` (single-threaded; the service calls it on one handler thread).
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5:** Commit: `git commit -m "frigate-glance: card state machine"`

### Task 6: MQTT client and Frigate API

**Files:**
- Create: `app/src/main/java/io/github/nickld/frigateglance/MqttClient.java`
- Create: `app/src/main/java/io/github/nickld/frigateglance/FrigateApi.java`
- Test: `app/src/test/java/io/github/nickld/frigateglance/MqttClientTest.java`, `FrigateApiTest.java`

**Interfaces:**
- Produces: `final class MqttClient { MqttClient(String url /* mqtt://user:pass@host:port */, String clientId, List<String> topics, Listener l); interface Listener { void onConnected(); void onMessage(String topic, byte[] payload); void onDisconnected(Exception cause); } void run(SocketFactory f) /* blocks: connect, subscribe, read loop, ping every 30 s, keepalive 60 */; void stop(); static long backoffMs(int attempt) }` — MQTT 3.1.1, clean session, QoS 0 subscribe only; `backoffMs` = 1000·2^attempt capped at 60 000. Client id `frigate-glance-` + the TV's `ANDROID_ID` first 8 chars (passed in by the service).
- Produces: `final class FrigateApi { FrigateApi(String baseUrl, String user, String password, String pinnedSha256, PinListener onFirstPin); JSONObject config() throws IOException; byte[] latestJpeg(String camera, int height) throws IOException; static Map<String,int[]> detectSizes(JSONObject config); static Map<String,String> liveStreams(JSONObject config); static String tokenFromSetCookie(List<String> headers) }` — HTTPS: `POST /api/login` with `{"user","password"}`, token from `frigate_token=` cookie, sent as `Cookie: frigate_token=…`; on 401 log in once more and retry once; trust-on-first-use pin of the leaf certificate SHA-256 (hex, lower case), reported through `onFirstPin`, enforced afterwards; plain HTTP skips login. Snapshot URL `{base}/api/{camera}/latest.jpg?h={height}`. `liveStreams` maps camera → first value of `cameras.<name>.live.streams`, else the camera name. Timeouts 10 s connect and read.

- [ ] **Step 1: Write the failing tests**

```java
// MqttClientTest: drive run() against a fake socket with scripted broker bytes
@Test public void sendsConnectWithCredentialsAndSubscribes() { /* assert CONNECT has protocol "MQTT" level 4, username "u", password "p", keepalive 60; then SUBSCRIBE lists frigate/reviews, frigate/events, frigate/available */ }
@Test public void deliversPublishPayload() { /* broker sends CONNACK 0, SUBACK, PUBLISH frigate/reviews "{…}" → listener.onMessage("frigate/reviews", bytes) */ }
@Test public void refusedConnackEndsWithDisconnected() { /* CONNACK return code 5 → onDisconnected(cause) with message containing "not authorised" */ }
@Test public void decodesMultiByteRemainingLength() { /* a 300-byte PUBLISH arrives intact */ }
@Test public void backoffDoublesToSixtySeconds() { assertEquals(1000, MqttClient.backoffMs(0)); assertEquals(8000, MqttClient.backoffMs(3)); assertEquals(60_000, MqttClient.backoffMs(10)); }
// FrigateApiTest
@Test public void tokenFromSetCookie() { assertEquals("abc.def", FrigateApi.tokenFromSetCookie(List.of("other=1", "frigate_token=abc.def; Path=/; HttpOnly"))); }
@Test public void detectSizesFromConfig() { assertArrayEquals(new int[]{1280,720}, FrigateApi.detectSizes(cfg).get("driveway")); }
@Test public void liveStreamFromConfigOrCameraName() { assertEquals("driveway_1", FrigateApi.liveStreams(cfg).get("driveway")); assertEquals("shop", FrigateApi.liveStreams(cfgWithoutStreams).get("shop")); }
@Test public void reLoginOnceOn401() { /* local com.sun.net.httpserver.HttpServer over HTTP: first /api/config → 401, /api/login → cookie, second /api/config → 200; assert two logins at most and config returned */ }
```

- [ ] **Step 2:** Run `./gradlew testReleaseUnitTest --tests '*MqttClientTest' --tests '*FrigateApiTest'`. Expected: FAIL.
- [ ] **Step 3:** Implement both. `FrigateApi` uses `HttpURLConnection`; the pin is a custom `X509TrustManager` + `HostnameVerifier` that accept only the pinned digest (or any certificate on first contact, then pin it).
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5:** Commit: `git commit -m "frigate-glance: MQTT subscriber and Frigate API client"`

### Task 7: Foreground service and the overlay card (snapshot, text, boxes)

**Files:**
- Create: `app/src/main/java/io/github/nickld/frigateglance/GlanceService.java`, `OverlayCard.java`, `BoxView.java`, `BootReceiver.java`
- Create: `app/src/main/res/layout/card.xml`, `res/drawable/card_glass.xml`
- Modify: `app/src/main/AndroidManifest.xml` (permissions `INTERNET`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `RECEIVE_BOOT_COMPLETED`; service with `foregroundServiceType="dataSync"`; receiver for `BOOT_COMPLETED`)
- Modify: `SettingsActivity.java` (temporary "Show a test card" button calling `GlanceService.showTestCard(context, null)`)

**Interfaces:**
- Consumes: `ConfigStore`, `GlanceConfig` (Task 3), `Review`, `Detection` (Task 4), `CardController`, `CardView`, `CardModel` (Task 5), `MqttClient`, `FrigateApi` (Task 6).
- Produces: `GlanceService` static `start(Context)`, `showTestCard(Context, String camera)` (camera null = the first enabled camera; detail text `Test card`), `dismissCard()`, `snoozeCard()`, `static boolean isCardShowing()`, `static String statusLine()` (e.g. `MQTT connected · Frigate ok`), logging under tag `FrigateGlance` the lines `mqtt connected`, `mqtt error: <msg>`, `frigate ok`, `frigate error: <msg>` (the install script in Task 10 greps for these).
- Produces: `OverlayCard implements CardView` — window type `TYPE_APPLICATION_OVERLAY`, flags `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE | FLAG_LAYOUT_NO_LIMITS`, gravity from `corner`, 48 dp from the screen edges, 480 dp wide; picture area 16:9 (480×270 dp) dark grey until the snapshot arrives; text strip under it: title (camera), detail, footer `◀ Dismiss · ▶ Snooze 1h` built from the configured key names and `snoozeMinutes`; slide-in 250 ms from the corner's side, slide-out 200 ms; `BoxView` draws each normalised box as a 2 dp rounded rectangle in the accent colour over the picture.
- Service: one `HandlerThread` owns `CardController` and `tick()`s it every second; MQTT runs on its own thread and posts messages to that handler; reconnect with `MqttClient.backoffMs`; Frigate config fetched on start and on every MQTT reconnect; snapshot fetched off the main thread at 360 px high, decoded with `inSampleSize` to fit 480 dp, applied only if the card still shows the same review id; a snapshot failure leaves the dark picture area and logs once. The persistent notification is a low-importance channel `Frigate Glance` with text from `statusLine()`.

- [ ] **Step 1:** Implement the classes above, then build: `./gradlew assembleRelease`. Expected: `BUILD SUCCESSFUL` and all unit tests still PASS (`./gradlew testReleaseUnitTest`).
- [ ] **Step 2:** Install on the TV and grant the overlay: `tools/tv.py --push …/app-release.apk /data/local/tmp/fg.apk`, then `tools/tv.py 'pm install -r /data/local/tmp/fg.apk; rm /data/local/tmp/fg.apk; appops set io.github.nickld.frigateglance SYSTEM_ALERT_WINDOW allow; cmd appops set io.github.nickld.frigateglance APP_AUTO_START allow'`. Push a test config (the owner's real values, kept outside the repo in `backup/frigate-glance-config.json`) to `/sdcard/Android/data/io.github.nickld.frigateglance/files/config.json` and start the app with `am start -n io.github.nickld.frigateglance/.SettingsActivity`.
- [ ] **Step 3:** Verify: `logcat -d -s FrigateGlance` shows `mqtt connected` and `frigate ok`. Start YouTube (`monkey -p com.google.android.youtube.tv 1`), trigger the test card, screenshot. Pass: card in the top-right corner over YouTube with camera name, detail, footer and a snapshot. Then check of a failing snapshot: call `showTestCard(context, "nosuchcamera")` from a temporary second button. Pass: the card appears within 1 s with its text and a dark picture area, and `frigate error` is logged once.
- [ ] **Step 4:** Ask the owner to walk in front of `frontdoooor` (or wait for a real alert); screenshot while the card is up. Pass: the card shows that camera with a box around the person, and leaves about 8 s after the review ends.
- [ ] **Step 5:** Reboot the TV; after boot, without opening the app, `logcat -d -s FrigateGlance` shows `mqtt connected`. Commit: `git commit -m "frigate-glance: foreground service and overlay card"`

### Task 8: Live video in the card

**Files:**
- Create: `app/src/main/java/io/github/nickld/frigateglance/LiveVideo.java`
- Modify: `OverlayCard.java` (a `PlayerView` with `surface_type="texture_view"` under the `BoxView`, alpha 0 until ready)

**Interfaces:**
- Consumes: `GlanceConfig.liveVideo, rtspUser, rtspPassword`, `FrigateApi.liveStreams` (Task 6), the Task 2 decision.
- Produces: `final class LiveVideo { LiveVideo(Context, Listener); interface Listener { void onPlaying(); void onGaveUp(); } void start(String rtspUrl); void release(); }` — `onGaveUp` fires when `STATE_READY` has not arrived within 4000 ms or on a player error; `release()` frees the player and its surface. URL `rtsp://[user:pass@]<frigate host>:8554/<stream>`.
- Card behaviour: snapshot first; on `onPlaying` fade the video in over 300 ms; on `onGaveUp` (or when `liveVideo` is false, or the Task 2 decision was "RTSP not usable") refresh the snapshot every 1000 ms while the card shows; release the player on hide.

- [ ] **Step 1:** Implement; build; unit tests still PASS.
- [ ] **Step 2:** On the TV: test card with `liveVideo` true over YouTube; screenshot after 3 s. Pass: moving picture (two screenshots 1 s apart differ in the picture area), no stutter in YouTube audio.
- [ ] **Step 3:** Set `liveVideo` false via a pushed config; test card again. Pass: picture refreshes every second.
- [ ] **Step 4:** With the card hidden, `dumpsys meminfo io.github.nickld.frigateglance` TOTAL PSS returns to within 5 MB of the idle value measured before the card; note both numbers for the README.
- [ ] **Step 5:** Commit: `git commit -m "frigate-glance: live video with snapshot fallback"`

### Task 9: Dismiss and snooze keys

**Files:**
- Create: `app/src/main/java/io/github/nickld/frigateglance/KeyService.java`
- Create: `app/src/main/res/xml/key_service.xml` (as the launcher's `input_key_service.xml`: `flagRequestFilterKeyEvents`, `canRequestFilterKeyEvents="true"`, no event types)
- Modify: `AndroidManifest.xml` (service with `BIND_ACCESSIBILITY_SERVICE`), `OverlayCard.java` (tell `KeyService` when a card shows or hides)

**Interfaces:**
- Consumes: `GlanceService.dismissCard()`, `snoozeCard()`, `isCardShowing()` (Task 7); `GlanceConfig.dismissKey`, `snoozeKey`.
- Produces: `KeyService.setFiltering(boolean on)` — static; adds `FLAG_REQUEST_FILTER_KEY_EVENTS` to the running service's info while a card shows and removes it when the card hides (the launcher's `RemoteKeys.applyKeyFilter` does the removal half), so no key passes through this service while no card is up.
- `onKeyEvent`: when a card shows and the keycode equals `KeyEvent.keyCodeFromString(dismissKey)` or `…(snoozeKey)`, consume both `ACTION_DOWN` and `ACTION_UP`, acting on `ACTION_UP`; everything else returns false. An unknown key name (`KEYCODE_UNKNOWN`) logs once and catches nothing.

- [ ] **Step 1:** Implement; build; unit tests PASS.
- [ ] **Step 2:** Enable the service on the TV, appending to the list as the launcher's `build-and-install.sh` `enable_service` does (keep `com.gathin.tvhome/.InputKeyService:com.gathin.tvhome/.HomeKeyService`). Check `dumpsys accessibility` lists it under Bound services.
- [ ] **Step 3:** With the owner and the real remote, over YouTube: (a) test card, press Left: card leaves; (b) test card, press Right: card leaves, and a second test card within the hour does not show; Resume in settings clears it; (c) with no card up, Left and Right seek YouTube as before; (d) while a card is up, Up, Down, OK, Back, volume, Netflix, YouTube and Home all behave as normal. If Left/Right feel wrong on this remote, agree two other keys with the owner, make them the defaults in `GlanceConfig` and the README.
- [ ] **Step 4:** The launcher's Settings, Netflix, YouTube, Prime and Input buttons still work (real remote).
- [ ] **Step 5:** Commit: `git commit -m "frigate-glance: dismiss and snooze from the remote"`

### Task 10: Settings screen

**Files:**
- Modify: `SettingsActivity.java`, create `res/layout/settings.xml`, `SettingRow.java` (one focusable row: title, value, OK cycles or toggles)

**Interfaces:**
- Consumes: `ConfigStore.saveSetting`, `GlanceService.statusLine()`, `showTestCard`, `CardController.snooze/resume/snoozedUntil` through `GlanceService`, Frigate camera list from `FrigateApi.config()`.
- Rows, in order: status line (not focusable); Snooze now / Resume (shows `Snoozed until 21:40` when snoozed); Show a test card; Cameras (one toggle per camera from Frigate's config, display name per Peek's `prettyName`); Show (`Alerts only` / `Alerts and detections`); Objects (toggle per label seen in the cameras' review config); Corner (cycles four values); Card stays (`4 s`, `8 s`, `15 s`, `30 s` after the event ends); Snooze length (`15 min`, `30 min`, `1 h`, `2 h`, `4 h`); Live video (on/off); Boxes (on/off); Dismiss key and Snooze key (cycle through `KEYCODE_DPAD_LEFT`, `KEYCODE_DPAD_RIGHT`, `KEYCODE_DPAD_UP`, `KEYCODE_DPAD_DOWN`, `KEYCODE_MENU`, `KEYCODE_INFO`, `KEYCODE_PROG_RED`, `KEYCODE_PROG_GREEN`, skipping the other key's value). Each change saves at once and the service picks it up without a restart.
- D-pad only; no text fields.

- [ ] **Step 1:** Implement; build; unit tests PASS.
- [ ] **Step 2:** On the TV, open the tile from Home; screenshot. Pass: rows readable from the sofa, focus visible, every row reachable with Up/Down.
- [ ] **Step 3:** Turn `shop` off and set Corner to bottom-left; test card: bottom-left. Ask the owner to trigger `shop` (or wait for one): no card. Snooze now, then a test card: none; Resume: test card shows.
- [ ] **Step 4:** Commit: `git commit -m "frigate-glance: settings screen"`

### Task 11: Install script, README and end-to-end check

**Files:**
- Create: `addons/frigate-glance/install.sh`, `addons/frigate-glance/config.example.json` (placeholder values only)
- Create: `app/src/main/java/io/github/nickld/frigateglance/ConfigProvider.java` (write-only `openFile` for `config.json`, exported with `android:permission="android.permission.WRITE_SECURE_SETTINGS"` as the launcher's `ThemeProvider`, for firmware that locks the shell out of `Android/data`)
- Modify: `addons/frigate-glance/README.md` (complete), `addons/README.md` (row as in Task 1, final)

**Interfaces:**
- Consumes: `ConfigStore.importPushed()` (Task 3; also called when `ConfigProvider` receives a file), log lines from Task 7.
- `install.sh` usage: `TV_HOST=<ip> addons/frigate-glance/install.sh <config.json>`; steps: validate JSON with Python, `./gradlew assembleRelease`, push and `pm install -r` (via `tools/b64push.py` + `tools/tv.py` as the launcher does), stage config to `/data/local/tmp` then `cp` into `Android/data/…/files/` or `content write --uri content://io.github.nickld.frigateglance.config/config.json`, `appops set … SYSTEM_ALERT_WINDOW allow`, `cmd appops set … APP_AUTO_START allow`, append `io.github.nickld.frigateglance/.KeyService` to `enabled_accessibility_services` (launcher's `enable_service` logic), start the app, then poll `logcat -d -s FrigateGlance` up to 20 s and print `MQTT: ok|<error>` and `Frigate: ok|<error>`, exiting 1 if either failed. It never prints passwords.
- README: what it does; screenshot of the card over an app; Permissions (overlay: to draw the card; accessibility: to catch the two keys only while a card is up; internet: MQTT broker and Frigate only; foreground service: to stay connected; boot: to reconnect after a reboot); Setup with `install.sh`; Config keys table (every key from Task 3 with default and meaning); advice to use a Frigate viewer-only user and an MQTT user limited to `frigate/#`; live video needs go2rtc's RTSP port 8554 reachable; idle and playing memory from Task 8; Tested on TCL 65Q650G (Android TV 11); community add-on, not affiliated with Frigate; credit to Peek (MIT).

- [ ] **Step 1:** Write `install.sh`, `ConfigProvider`, README, example config.
- [ ] **Step 2:** Uninstall the add-on from the TV, then run `TV_HOST=192.168.10.193 addons/frigate-glance/install.sh backup/frigate-glance-config.json`. Expected: `MQTT: ok`, `Frigate: ok`, exit 0.
- [ ] **Step 3:** Run with a config whose MQTT password is wrong. Expected: `MQTT: … not authorised`, exit 1, no password printed.
- [ ] **Step 4:** Press Home: the module card prompt appears; answer Show card; screenshot the `Frigate Glance` card focused in the grid for the README; OK opens settings. Hold OK on the Settings pill → Module cards list shows it.
- [ ] **Step 5:** Full real-TV pass of the spec's "Testing on the real TV" list, including a reboot, then `python -m unittest tests.test_addons -v` and `cd addons/frigate-glance && ./gradlew testReleaseUnitTest assembleRelease`. Expected: all PASS.
- [ ] **Step 6:** Commit: `git commit -m "addons: Frigate Glance install script and README"`; push the branch to the fork (`git push -u fork addons/frigate-glance`). Opening the upstream PR waits for the owner, and its diff must not include `docs/superpowers/`.
