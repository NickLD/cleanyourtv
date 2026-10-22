# Peek TV: a Frigate camera pop-up add-on for the launcher

Date: 2026-10-21. Status: design, not yet approved.

Calls marked **(my call)** were made by the designer, not by the owner; the owner can overturn any of
them. Everything else was asked for by the owner.

## Intent

[Peek](https://github.com/casi-3/peek) (MIT, Electron, macOS and Windows; the owner has contributed to it)
pops a live Frigate camera feed into a corner of a desktop the moment Frigate detects something, like
the camera notifications Apple TV shows. This brings the same thing to an Android TV: while you watch
anything (YouTube TV, Netflix, an HDMI input), a small card slides into a corner with the camera that
saw something, what it saw, and the picture, then goes away on its own.

It ships as an add-on in `addons/peek-tv/` of this repo, following `addons/README.md`, so it is
likely the first add-on listed there.

The pop-up over whatever is playing is the point of version 1. The app's own tile is mostly the way into
its settings.

## Goals (version 1)

1. A corner pop-up over any app or input, shown when Frigate reports a matching event, gone a set
   number of seconds after the event ends.
2. Little noise: by default only Frigate **alerts**, filtered further by camera and object in the app.
   Pop-ups for every passing car during a movie are what would make the owner switch it off.
3. Dismiss and snooze from the remote while the card is on screen, with the buttons named on the card.
4. Live video in the card when the TV can handle it, a snapshot otherwise, switchable in the app.
5. Bounding boxes over the detected object, as Peek draws them, switchable.
6. Set up without typing on the TV: the person (or their AI assistant) doing the debloat pushes a
   config file over ADB, as `tools/theme.sh` does for the launcher.

## Non-goals

- Browsing recordings, the review timeline, or clips. Frigate's own UI does that.
- Talking to cameras: two-way audio, PTZ, doorbell chimes.
- Depending on Home Assistant. The add-on talks to MQTT and Frigate directly, like Peek.
- Frigate audio detections (Peek's `frigate/<camera>/audio/<type>`): left out of version 1, an easy
  follow-up **(my call)**.
- Being part of the launcher. The add-on never touches `launcher/`, and the launcher never knows about
  it beyond the module card.

## Anti-goals (it fails even if it works)

- It interrupts movies for things that don't matter.
- It takes the remote away from what you're watching: any key other than the two configured ones,
  or any key at all while no card is shown, reaching the add-on instead of the app.
- It slows the TV down or makes Home sluggish on a 1 to 2 GB set while idle.
- It phones home, polls, or shows ads. No telemetry, no update checks.

## Constraints

- **Add-on rules** (`addons/README.md`, `docs/modules.md`, `docs/specs/card.md`, `tests/test_addons.py`):
  own Gradle project in `addons/peek-tv/` with `settings.gradle`, `app/` and `gradlew`; own package
  name; a README with a `Permissions` heading listing every permission, the network access and the
  background service, and why; a row in the `addons/README.md` table; no APKs in the repo; tested on
  a real TV; own art (no Frigate or Peek logos it has no right to); MIT credit to Peek for anything ported.
- **Build**: Java, like the launcher. minSdk 26, compileSdk and targetSdk 34, built by CI with JDK 17
  and `platforms;android-34 build-tools;34.0.0` (`.github/workflows/addons.yml`). Must also build
  locally with the JDK 21 the owner has.
- **Module card**: declared on the settings activity exactly as `docs/modules.md` shows. Label
  `Peek`, caption showing state (see Settings), an accent colour of its own **(my call)**. The label
  must not clash with another installed app's label.
- **Hardware**: test TV is a TCL 65Q650G (Android TV 11, SDK 30, Realtek RTD2851, armv7 32-bit
  userspace, 2 GB RAM, BT remote `BT_RC813_M1`). The repo targets TVs with as little as 1 GB.
- **TCL firmware**: TclAppBoot refuses to start an app's services at boot unless the app holds the
  `APP_AUTO_START` app-op (seen on this TV with the launcher's accessibility services). "Display over
  other apps" is not reachable in TCL's Settings and has to be granted over ADB.
- **Frigate**: needs review items, Frigate 0.14 or newer. The owner runs 0.18.2.
- **The owner's network** (verify, don't assume): Frigate at `192.168.4.58`, authenticated port
  `8971` over HTTPS (self-signed certificate likely, as Peek pins it); Mosquitto at
  `192.168.4.56:1883` with a username. The TV is on `192.168.10.x`, a different network. Checked
  2026-10-21: the TV pings `192.168.4.58`; from a PC on the TV's network, `1883`, `8971`, `8554`
  (go2rtc RTSP) and `1984` (go2rtc API) are open and `5000` is closed. The TV has no TCP client to test
  ports itself, so the setup script's own check is the first proof from the TV. Cameras: `driveway`, `frontcorner`, `frontdoooor`, `shop`; go2rtc streams
  `driveway_1`, `frontcorner_1`, `frontdoooor_1`, `shop_1` (stream names differ from camera names, so
  take the mapping from Frigate's config, `cameras.<name>.live.streams`). Alerts on all four are
  `person` and `car`; `frontcorner` and `frontdoooor` require a zone, `driveway` and `shop` do not.

## How it behaves

### Triggering

- Subscribe to `<prefix>/reviews` (default prefix `frigate`). A review message with `type` `new`
  whose `after.severity`, `after.camera` and `after.data.objects` pass the filters opens a card.
  `update` refreshes it (objects, zones), `end` starts the dismiss timer.
- Filters, all set in the app: cameras (default all), severity (default **alerts only**; option:
  alerts and detections), objects (default all that the camera's review config alerts on).
- Also subscribe to `<prefix>/events` for the card's detail and boxes: for the object ids listed in the
  review's `after.data.detections`, take `after.box`, `after.label`, `after.score`, `after.sub_label`
  (recognised face), `after.recognized_license_plate` and `after.entered_zones`. Peek's
  `normalizeBox` (box divided by the camera's detect width and height from Frigate's config) is the
  reference.
- Watch `<prefix>/available`; while Frigate is offline, show nothing and don't error on screen.
- One card at a time. A new matching review while a card is up replaces it **(my call)**.

### The card

- Slides into the configured corner (default top right), over everything, never taking focus and never
  touchable.
- Shows: camera display name, the object (and face or plate when Frigate has one), the zone if any,
  the picture, and a footer naming the buttons, for example "◀ Dismiss · ▶ Snooze 1h".
- Picture: a snapshot at once (Frigate's latest frame or the event snapshot) so there is never a black
  box, then, with live video on, the live stream fading in over it when it is actually playing. If
  live video has not started within a few seconds, keep refreshing the snapshot instead **(my call)**.
- Bounding boxes drawn over the picture from the `events` data, on by default, switchable. They trail
  live video slightly, as in Peek; that is accepted.
- Size and look: about Peek's proportions (Peek's default is 380×300 px on a desktop), scaled for a TV
  seen from the sofa, with the launcher's frosted-glass style **(my call)**. Exact size is the
  builder's.
- Leaves `dismiss_seconds` (default 8, as Peek) after the review ends. Dismiss closes it at once.

### Remote buttons

- Nothing changes on the remote until a card is on screen. Then exactly two configured keys are
  caught: **Dismiss** (close this card) and **Snooze** (no cards for the snooze length). Every other key
  goes to whatever is playing as usual, and the two keys go back to normal the moment the card leaves.
- Both keys are chosen beforehand in the app or the config file, by Android keycode name, as the
  launcher's `app_keys` takes them. The default pair must exist on common TV remotes. Avoid Back and OK,
  which people press out of habit to control playback **(my call)**. The final defaults are picked by
  testing on the 65Q650G remote with the owner.
- Snooze silences all cameras **(my call)**; the length is set in the app (default 1 hour, my call).
  The app also offers "Snooze now" and "Resume". The owner rates that pre-snooze as nice-to-have.
- Catching keys needs the add-on's own accessibility service with key filtering. The launcher's
  `InputKeyService` and `HomeKeyService` (both enabled on the test TV) do the same thing and are the
  reference for what TCL needs to keep such a service bound.

### Settings (the tile)

The activity behind the module card and the app's normal tile. Everything here can be done with the
D-pad: lists and toggles only, no text fields.

- Status: connected or not (MQTT and Frigate separately), snoozed until when.
- Cameras on or off (list read from Frigate), severity, objects, corner, how long the card stays,
  snooze length, live video on or off, boxes on or off, dismiss and snooze keys.
- "Show a test card" to check placement and buttons without waiting for someone to walk past
  **(my call)**.
- The card caption reflects state, for example `4 cameras` or `Snoozed`. Captions are static XML in
  card spec 1, so a live caption is not possible. Keep it static.

### Setup and config file

- A JSON file pushed over ADB and imported by the app on its next start, then kept in the app's
  private storage. Keys mirror Peek's `config.json` where they mean the same thing (`mqtt`,
  `frigateUrl`, `frigateUser`, `frigatePassword`, `topicPrefix`, `cameras`, `labels`, `corner`,
  `dismissSeconds`), plus the TV-only keys (`severity`, `liveVideo`, `boxes`, `dismissKey`,
  `snoozeKey`, `snoozeMinutes`). The README documents every key in a table, so an assistant can write
  the file without guessing.
- A script in `addons/peek-tv/` (not in `launcher/`) installs the APK and pushes the config the way
  `tools/theme.sh` does, including its fallback for firmware that locks the shell out of
  `Android/data`. It also grants "display over other apps" and `APP_AUTO_START`, enables the
  accessibility service, starts the app, and checks the app can reach MQTT and Frigate, saying
  clearly which one failed. It uses `tools/tv.py` like the rest of the repo.
- Changes made in the app's settings win over the file until the file is pushed again.
- The README recommends a Frigate user with viewer rights only and an MQTT user that can only read
  `frigate/#`.

### Running in the background

- One foreground service holds the MQTT connection (keepalive only, no polling) and starts at boot.
  It costs a few MB idle; the README says so.
- The live player and decoded pictures exist only while a card is up, and are released when it goes.
- Frigate login: as Peek does for port 8971, log in for a token, and pin the certificate on first
  contact. Over plain HTTP on port 5000 no login is needed.

## Left to the builder

- MQTT client library (or a small hand-written subscribe-only MQTT 3.1.1 client), weighed by size.
- How live video gets to the TV. Media3 ExoPlayer playing go2rtc's RTSP (`:8554`) is the obvious first
  try and should be **spiked on the 65Q650G first**: it bypasses Frigate's login and needs that port to
  be reachable. Frigate's authenticated MSE or WebRTC endpoints through `8971` are the alternative.
  Whichever is chosen, the README states what port and credentials it needs.
- Snapshot endpoint, card dimensions and animation, the card's art and accent colour, package name
  (something owned by the author, not `com.gathin`).
- Where reasonable, unit tests for message parsing and filtering, which can run without a TV.

## Testing on the real TV

On the 65Q650G with the real remote (injected keys don't reach accessibility services, see
`docs/TROUBLESHOOTING.md`):

- Card appears over YouTube TV, Netflix and an HDMI input; nothing appears for a filtered-out camera,
  object or severity; a car passing the street on `frontcorner` does not alert.
- Dismiss and snooze work from the real remote; all other keys keep controlling playback while the
  card is up; both keys behave normally when no card is up.
- Live video starts, and with it off, snapshots refresh; boxes line up with the object.
- After a reboot, the service reconnects without opening the app.
- Idle memory measured with `dumpsys meminfo` and noted in the README.
- Module card prompt appears on Home, the card shows after "Show card", and the app opens from it.
- The launcher's Home, its key services and Live TV still behave as before.

## Delivering it

On branch `addons/peek-tv` of the owner's fork: `addons/peek-tv/` with its README (Permissions,
config keys, setup, the TV it was tested on, a screenshot of the card), a row in the `addons/README.md`
table, and credit to Peek. The PR to upstream carries only those; this design document stays on the
fork **(my call)**.
