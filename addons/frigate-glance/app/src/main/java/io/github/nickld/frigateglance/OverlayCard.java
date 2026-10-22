package io.github.nickld.frigateglance;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The card on screen: a window over every app that never takes focus or touches, so the remote keeps
 * controlling what is playing. Called from the service's thread; does its view work on the main thread.
 */
final class OverlayCard implements CardView {
    /** Where the picture comes from; the service answers from Frigate. */
    interface Source {
        byte[] snapshot(String camera) throws IOException;

        /** Width over height of the camera's frame, so boxes line up; 16/9 when unknown. */
        float aspect(String camera);

        /** RTSP address for live video, or null when live video is off. */
        String liveUrl(String camera);
    }

    /** Told when a card appears and leaves, so the key service filters keys only meanwhile. */
    interface ShowingListener {
        void onShowing(boolean showing);
    }

    static final int WIDTH_DP = 480;
    static final int MAX_HEIGHT_DP = 270;
    private static final int MARGIN_DP = 48;
    static final long SNAPSHOT_REFRESH_MS = 1000;

    private final Context context;
    private final WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Source source;
    private final ShowingListener showingListener;
    private final float dp;

    private GlanceConfig config;
    private LinearLayout root;
    private FrameLayout picture;
    private ImageView image;
    private TextureView video;
    private LiveVideo live;
    private boolean refreshing;
    private BoxView boxes;
    private TextView title, detail, footer;
    private volatile String reviewId;
    private boolean snapshotLogged;

    OverlayCard(Context context, GlanceConfig config, Source source, ShowingListener showingListener) {
        this.context = context;
        this.wm = context.getSystemService(WindowManager.class);
        this.config = config;
        this.source = source;
        this.showingListener = showingListener;
        this.dp = context.getResources().getDisplayMetrics().density;
    }

    void setConfig(GlanceConfig c) {
        config = c;
    }

    @Override
    public void show(CardModel m) {
        reviewId = m.reviewId;
        main.post(() -> {
            boolean fresh = root == null;
            if (fresh) build();
            fill(m, true);
            if (fresh) {
                try {
                    wm.addView(root, params());
                } catch (RuntimeException e) {
                    Log.w(ConfigStore.TAG, "card not shown (display over other apps not allowed?): " + e.getMessage());
                    root = null;
                    return;
                }
                slideIn();
                showingListener.onShowing(true);
            }
        });
        loadSnapshot(m.reviewId, m.camera);
        main.post(() -> startPicture(m.reviewId, m.camera));
    }

    /** Live video when it is on and starts; otherwise a snapshot refreshed every second. */
    private void startPicture(String id, String camera) {
        if (root == null || !id.equals(reviewId)) return;
        stopPicture();
        String url = source.liveUrl(camera);
        if (url == null) {
            refresh(id, camera);
            return;
        }
        live = new LiveVideo(context, new LiveVideo.Listener() {
            @Override
            public void onPlaying() {
                if (video != null) video.animate().alpha(1f).setDuration(300).start();
            }

            @Override
            public void onGaveUp() {
                if (video != null) video.setAlpha(0f);
                refresh(id, camera);
            }
        });
        live.start(url, video);
    }

    private void refresh(String id, String camera) {
        refreshing = true;
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!refreshing || root == null || !id.equals(reviewId)) return;
                loadSnapshot(id, camera);
                main.postDelayed(this, SNAPSHOT_REFRESH_MS);
            }
        }, SNAPSHOT_REFRESH_MS);
    }

    private void stopPicture() {
        refreshing = false;
        if (live != null) {
            live.release();
            live = null;
        }
        if (video != null) video.setAlpha(0f);
    }

    @Override
    public void update(CardModel m) {
        main.post(() -> {
            if (root != null && m.reviewId.equals(reviewId)) fill(m, false);
        });
    }

    @Override
    public void hide() {
        reviewId = null;
        main.post(() -> {
            if (root == null) return;
            View gone = root;
            stopPicture();
            root = null;
            video = null;
            showingListener.onShowing(false);
            gone.animate().translationX(slideDistance()).alpha(0f).setDuration(200)
                    .withEndAction(() -> removeQuietly(gone)).start();
        });
    }

    private void build() {
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.card_glass);
        int pad = px(12);
        root.setPadding(pad, pad, pad, pad);

        picture = new FrameLayout(context);
        picture.setBackgroundResource(R.drawable.picture_frame);
        picture.setClipToOutline(true);
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.FIT_XY);
        video = new TextureView(context);
        video.setAlpha(0f);
        boxes = new BoxView(context);
        picture.addView(image, new FrameLayout.LayoutParams(-1, -1));
        picture.addView(video, new FrameLayout.LayoutParams(-1, -1));
        picture.addView(boxes, new FrameLayout.LayoutParams(-1, -1));
        root.addView(picture, new LinearLayout.LayoutParams(px(WIDTH_DP), px(WIDTH_DP * 9 / 16)));

        title = text(22, 0xFFFFFFFF, Typeface.DEFAULT_BOLD);
        detail = text(17, 0xFFC9D1D9, Typeface.DEFAULT);
        footer = text(14, 0xFF8B949E, Typeface.DEFAULT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = px(10);
        root.addView(title, lp);
        root.addView(detail, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
        fp.topMargin = px(6);
        root.addView(footer, fp);
    }

    private void fill(CardModel m, boolean newCard) {
        if (newCard) {
            // At most 480 dp wide and 270 dp tall (a 16:9 frame fills both), in the camera's own shape so boxes line up.
            float aspect = source.aspect(m.camera);
            int h = Math.min(px(MAX_HEIGHT_DP), Math.round(px(WIDTH_DP) / aspect));
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) picture.getLayoutParams();
            lp.height = h;
            lp.width = Math.round(h * aspect);
            picture.setLayoutParams(lp);
            image.setImageDrawable(null);
        }
        title.setText(m.title);
        detail.setText(m.detail);
        detail.setVisibility(m.detail.isEmpty() ? View.GONE : View.VISIBLE);
        footer.setText(CardText.footer(config.dismissKey, config.snoozeKey, config.snoozeMinutes));
        boxes.setBoxes(config.boxes ? m.boxes : null);
    }

    void loadSnapshot(String id, String camera) {
        loader.execute(() -> {
            try {
                byte[] jpeg = source.snapshot(camera);
                Bitmap bmp = decode(jpeg);
                main.post(() -> {
                    if (root != null && id.equals(reviewId) && bmp != null) image.setImageBitmap(bmp);
                });
            } catch (IOException | RuntimeException e) {
                if (!snapshotLogged) Log.w(ConfigStore.TAG, "frigate error: snapshot " + camera + ": " + e.getMessage());
                snapshotLogged = true;
            }
        });
    }

    /** Current review id on screen, or null. */
    String reviewId() {
        return reviewId;
    }

    private Bitmap decode(byte[] jpeg) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= px(WIDTH_DP)) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        d.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, d);
    }

    private WindowManager.LayoutParams params() {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        p.gravity = gravity(config.corner);
        p.x = px(MARGIN_DP);
        p.y = px(MARGIN_DP);
        p.setTitle("Frigate Glance");
        return p;
    }

    static int gravity(String corner) {
        switch (corner) {
            case "top-left": return Gravity.TOP | Gravity.LEFT;
            case "bottom-left": return Gravity.BOTTOM | Gravity.LEFT;
            case "bottom-right": return Gravity.BOTTOM | Gravity.RIGHT;
            default: return Gravity.TOP | Gravity.RIGHT;
        }
    }

    private void slideIn() {
        root.setTranslationX(slideDistance());
        root.setAlpha(0f);
        root.animate().translationX(0f).alpha(1f).setDuration(250).start();
    }

    private float slideDistance() {
        float d = px(WIDTH_DP + MARGIN_DP);
        return config.corner.endsWith("left") ? -d : d;
    }

    private TextView text(int sp, int color, Typeface face) {
        TextView t = new TextView(context);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(face);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    private int px(int dps) {
        return Math.round(dps * dp);
    }

    private void removeQuietly(View v) {
        try {
            wm.removeView(v);
        } catch (RuntimeException ignored) {
            // already gone
        }
    }
}
