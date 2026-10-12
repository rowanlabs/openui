package com.rowan.android.uiclasses;

import android.animation.ValueAnimator;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;


import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * MoreAppsView
 * A self-contained "More apps" list. Pure Java, no third-party libraries.
 * <p>
 * - Fetches the app list from an API (JSON).
 * - First successful response is saved in SharedPreferences. On every later launch the
 * cached list is shown INSTANTLY while the API is called again in the background;
 * when fresh data differs, the list updates itself.
 * - No cache yet? A pulsing skeleton is shown while loading.
 * - Icons are loaded with a built-in memory + disk cache.
 * - Tapping a row opens the app's store link (or Play Store via its package name).
 * <p>
 * Needs:  <uses-permission android:name="android.permission.INTERNET" />
 * <p>
 * XML:
 * <com.rowan.android.uiclasses.MoreAppsView
 * android:id="@+id/moreAppsView"
 * android:layout_width="match_parent"
 * android:layout_height="wrap_content"
 * app:rowan_apiUrl="https://dummyjson.com/c/0516-00cc-42cc-989c"
 * app:rowan_listCount="3"
 * app:rowan_hideWhenEmpty="true"
 * app:rowan_excludeOwnApp="true"
 * app:rowan_itemBackgroundColor="#3C3C3C"
 * app:rowan_itemBorderColor="#ffffff"
 * app:rowan_itemBorderWidth="2dp"
 * app:rowan_itemCornerRadius="5dp"
 * app:rowan_itemSpacing="10dp"
 * app:rowan_itemPadding="5dp"
 * app:rowan_logoSize="50dp"
 * app:rowan_logoCornerRadius="5dp"
 * app:rowan_skeletonColor="#505050"
 * app:rowan_appTitleColor="#ffffff"
 * app:rowan_appTitleSize="18sp"
 * app:rowan_appDescriptionColor="#ffffff"
 * app:rowan_appDescriptionSize="14sp"
 * app:rowan_appDescriptionMaxLines="3"
 * app:layout_constraintEnd_toEndOf="parent"
 * app:layout_constraintStart_toStartOf="parent"
 * app:layout_constraintTop_toBottomOf="@id/moreappsTv" />
 * <p>
 * Expected JSON (either a bare array, or an object holding the array under
 * "apps" / "data" / "items" / "results"):
 * [
 * {
 * "name": "Wave pure",                        // also: title, app_name
 * "description": "Clean Speaker in one tap",  // also: desc, subtitle, summary
 * "icon": "https://.../icon.png",             // also: logo, image, icon_url, logo_url
 * "package": "com.rowan.android.wavepure",    // also: packageName, package_name
 * "url": "https://play.google.com/..."        // optional, also: link, store_url
 * }
 * ]
 */
public class MoreAppsView extends LinearLayout {

    // ------------------------------------------------------------------
    // Public types
    // ------------------------------------------------------------------

    public static class AppItem {
        public final String name;
        public final String description;
        public final String iconUrl;
        public final String packageName;
        public final String link;

        public AppItem(String name, String description, String iconUrl, String packageName, String link) {
            this.name = name;
            this.description = description;
            this.iconUrl = iconUrl;
            this.packageName = packageName;
            this.link = link;
        }
    }

    public interface OnAppClickListener {
        /**
         * Return true if you handled the click; false lets the view open the store link.
         */
        boolean onAppClick(AppItem item);
    }

    public interface OnLoadListener {
        void onLoaded(List<AppItem> items, boolean fromCache);

        void onError(Exception e);
    }

    // ------------------------------------------------------------------
    // Shared infrastructure (static, shared by all instances)
    // ------------------------------------------------------------------

    private static final String PREFS_NAME = "more_apps_view_cache";
    private static final int MAX_JSON_BYTES = 2 * 1024 * 1024;
    private static final int MAX_DISK_ICONS = 80;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final ThreadFactory DAEMON_FACTORY = r -> {
        Thread t = new Thread(r, "MoreAppsView");
        t.setDaemon(true);
        return t;
    };
    private static final ExecutorService API_EXEC = Executors.newSingleThreadExecutor(DAEMON_FACTORY);
    private static final ExecutorService IMG_EXEC = Executors.newFixedThreadPool(3, DAEMON_FACTORY);

    private static final LruCache<String, Bitmap> MEM_CACHE = new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 1024 / 16)) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount() / 1024;
        }
    };

    // ------------------------------------------------------------------
    // Config
    // ------------------------------------------------------------------

    private String apiUrl = "https://rowanvex.onrender.com/api/v1/rvb/builds/moreapps";
    private int listCount = 0; // 0 = all
    private boolean hideWhenEmpty = true;
    private boolean excludeOwnApp = true;

    private int itemBackgroundColor = Color.WHITE;
    private int itemBorderColor = Color.parseColor("#E6E8EB");
    private int itemBorderWidth;
    private int itemCornerRadius;
    private int itemSpacing;
    private int itemPadding;
    private int logoSize;
    private int logoCornerRadius;
    private int skeletonColor = Color.parseColor("#E3E6EA");

    private int titleColor = Color.parseColor("#1B1F23");
    private float titleSize;
    private int descriptionColor = Color.parseColor("#6B7280");
    private float descriptionSize;
    private int descriptionMaxLines = 2;

    private final Map<String, String> headers = new HashMap<>();
    private OnAppClickListener clickListener;
    private OnLoadListener loadListener;

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    private int generation = 0;            // invalidates in-flight callbacks
    private List<AppItem> lastItems = null; // currently displayed data (null = nothing yet)
    private String renderedRaw = null;      // raw JSON that produced lastItems
    private final List<View> skeletonRows = new ArrayList<>();
    private ValueAnimator skeletonAnimator;

    public MoreAppsView(Context c) {
        this(c, null);
    }

    public MoreAppsView(Context c, AttributeSet a) {
        this(c, a, 0);
    }

    public MoreAppsView(Context c, AttributeSet a, int defStyle) {
        super(c, a, defStyle);
        setOrientation(VERTICAL);

        itemBorderWidth = dp(1);
        itemCornerRadius = dp(14);
        itemSpacing = dp(10);
        itemPadding = dp(12);
        logoSize = dp(48);
        logoCornerRadius = dp(12);
        titleSize = sp(15);
        descriptionSize = sp(13);

        if (a != null) {
            TypedArray t = c.obtainStyledAttributes(a, R.styleable.rowan_MoreAppsView, defStyle, 0);
            try {
                String url = t.getString(R.styleable.rowan_MoreAppsView_rowan_apiUrl);
                if (url != null) apiUrl = url.trim();
                listCount = Math.max(0, t.getInt(R.styleable.rowan_MoreAppsView_rowan_listCount, listCount));
                hideWhenEmpty = t.getBoolean(R.styleable.rowan_MoreAppsView_rowan_hideWhenEmpty, hideWhenEmpty);
                excludeOwnApp = t.getBoolean(R.styleable.rowan_MoreAppsView_rowan_excludeOwnApp, excludeOwnApp);

                itemBackgroundColor = t.getColor(R.styleable.rowan_MoreAppsView_rowan_itemBackgroundColor, itemBackgroundColor);
                itemBorderColor = t.getColor(R.styleable.rowan_MoreAppsView_rowan_itemBorderColor, itemBorderColor);
                itemBorderWidth = (int) t.getDimension(R.styleable.rowan_MoreAppsView_rowan_itemBorderWidth, itemBorderWidth);
                itemCornerRadius = (int) t.getDimension(R.styleable.rowan_MoreAppsView_rowan_itemCornerRadius, itemCornerRadius);
                itemSpacing = (int) t.getDimension(R.styleable.rowan_MoreAppsView_rowan_itemSpacing, itemSpacing);
                itemPadding = (int) t.getDimension(R.styleable.rowan_MoreAppsView_rowan_itemPadding, itemPadding);
                logoSize = (int) t.getDimension(R.styleable.rowan_MoreAppsView_rowan_logoSize, logoSize);
                logoCornerRadius = (int) t.getDimension(R.styleable.rowan_MoreAppsView_rowan_logoCornerRadius, logoCornerRadius);
                skeletonColor = t.getColor(R.styleable.rowan_MoreAppsView_rowan_skeletonColor, skeletonColor);

                titleColor = t.getColor(R.styleable.rowan_MoreAppsView_rowan_appTitleColor, titleColor);
                titleSize = t.getDimension(R.styleable.rowan_MoreAppsView_rowan_appTitleSize, titleSize);
                descriptionColor = t.getColor(R.styleable.rowan_MoreAppsView_rowan_appDescriptionColor, descriptionColor);
                descriptionSize = t.getDimension(R.styleable.rowan_MoreAppsView_rowan_appDescriptionSize, descriptionSize);
                descriptionMaxLines = Math.max(1, t.getInt(R.styleable.rowan_MoreAppsView_rowan_appDescriptionMaxLines, descriptionMaxLines));
            } finally {
                t.recycle();
            }
        }
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Change the API url at runtime (also reloads).
     */
    public void setApiUrl(String url) {
        String clean = url == null ? "" : url.trim();
        if (clean.equals(apiUrl)) return;
        apiUrl = clean;
        lastItems = null;
        renderedRaw = null;
        if (isAttachedToWindow()) start();
    }

    /**
     * Max rows to show (0 = all). Re-renders instantly with the data we already have.
     */
    public void setListCount(int count) {
        listCount = Math.max(0, count);
        if (lastItems != null) render(lastItems, renderedRaw);
    }

    public void setHideWhenEmpty(boolean hide) {
        hideWhenEmpty = hide;
    }

    /**
     * Optional request header, e.g. setHeader("Authorization", "Bearer ...").
     */
    public void setHeader(String key, String value) {
        if (key == null) return;
        if (value == null) headers.remove(key);
        else headers.put(key, value);
    }

    public void setOnAppClickListener(OnAppClickListener l) {
        clickListener = l;
    }

    public void setOnLoadListener(OnLoadListener l) {
        loadListener = l;
    }

    /**
     * Force a new API call (cached data stays visible meanwhile).
     */
    public void refresh() {
        if (isAttachedToWindow()) start();
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        generation++; // drop any in-flight callback
        if (skeletonAnimator != null) skeletonAnimator.cancel();
    }

    // ------------------------------------------------------------------
    // Load flow: cache first -> API -> update
    // ------------------------------------------------------------------

    private void start() {
        if (TextUtils.isEmpty(apiUrl)) {
            applyEmptyState();
            return;
        }
        final int gen = ++generation;
        final String url = apiUrl;
        final Map<String, String> hdr = new HashMap<>(headers);

        boolean hasData = lastItems != null || showCached(url);
        if (!hasData) showSkeleton();

        API_EXEC.execute(() -> {
            String raw = null;
            List<AppItem> parsed = null;
            Exception error = null;
            try {
                raw = httpGet(url, hdr);
                parsed = parse(raw);
            } catch (Exception e) {
                error = e;
            }
            final String fRaw = raw;
            final List<AppItem> fParsed = parsed;
            final Exception fError = error;
            MAIN.post(() -> onFetchDone(gen, url, fRaw, fParsed, fError));
        });
    }

    private void onFetchDone(int gen, String url, String raw, List<AppItem> parsed, Exception error) {
        if (gen != generation) return; // detached or superseded

        if (error != null || parsed == null || raw == null) {
            if (lastItems == null) { // nothing to show (skeleton was showing)
                stopSkeleton();
                removeAllViews();
                applyEmptyState();
            }
            if (loadListener != null) {
                loadListener.onError(error != null ? error : new IOException("Invalid response"));
            }
            return;
        }

        saveCache(url, raw);
        if (!raw.equals(renderedRaw)) render(parsed, raw);
        RowanUi.d("MoreApps", "fetch ok: " + parsed.size() + " items, changed=" + !raw.equals(renderedRaw));

        if (loadListener != null) loadListener.onLoaded(parsed, false);
    }

    private boolean showCached(String url) {
        SharedPreferences sp = prefs();
        String key = cacheKey(url);
        String raw = sp.getString(key, null);
        if (raw == null) return false;
        try {
            List<AppItem> items = parse(raw);
            render(items, raw);
            RowanUi.d("MoreApps", "cache hit: " + items.size() + " items");
            if (loadListener != null) loadListener.onLoaded(items, true);
            return true;
        } catch (Exception e) {
            sp.edit().remove(key).apply(); // corrupted cache, drop it
            RowanUi.e("MoreApps", "fetch failed", e);
            return false;
        }
    }

    private void saveCache(String url, String raw) {
        prefs().edit().putString(cacheKey(url), raw).apply();
    }

    private SharedPreferences prefs() {
        return getContext().getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String cacheKey(String url) {
        return "cache_" + sha1(url);
    }

    // ------------------------------------------------------------------
    // Networking + parsing
    // ------------------------------------------------------------------

    private static String httpGet(String urlStr, Map<String, String> hdr) throws IOException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            c.setRequestProperty("Accept", "application/json");
            for (Map.Entry<String, String> e : hdr.entrySet())
                c.setRequestProperty(e.getKey(), e.getValue());

            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code);

            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n, total = 0;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > MAX_JSON_BYTES) throw new IOException("Response too large");
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static List<AppItem> parse(String raw) throws Exception {
        String s = raw.trim();
        JSONArray arr;
        if (s.startsWith("[")) {
            arr = new JSONArray(s);
        } else {
            JSONObject obj = new JSONObject(s);
            arr = null;
            for (String k : Arrays.asList("apps", "data", "items", "results", "more_apps")) {
                arr = obj.optJSONArray(k);
                if (arr != null) break;
            }
            if (arr == null) { // fall back: first array found anywhere at top level
                Iterator<String> it = obj.keys();
                while (it.hasNext() && arr == null) arr = obj.optJSONArray(it.next());
            }
            if (arr == null) throw new IllegalArgumentException("No array found in JSON");
        }

        List<AppItem> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String name = firstString(o, "name", "title", "app_name", "appName");
            if (TextUtils.isEmpty(name)) continue;
            out.add(new AppItem(name, firstString(o, "description", "desc", "subtitle", "short_description", "summary"), firstString(o, "icon", "logo", "image", "icon_url", "iconUrl", "logo_url", "image_url", "thumbnail"), firstString(o, "package", "packageName", "package_name", "applicationId"), firstString(o, "url", "link", "store_url", "play_url")));
        }
        return out;
    }

    private static String firstString(JSONObject o, String... keys) {
        for (String k : keys) {
            if (o.has(k) && !o.isNull(k)) {
                String v = o.optString(k, "").trim();
                if (!v.isEmpty()) return v;
            }
        }
        return "";
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private void render(List<AppItem> items, String raw) {
        stopSkeleton();
        removeAllViews();
        lastItems = items;
        renderedRaw = raw;

        String ownPkg = getContext().getPackageName();
        int shown = 0;
        for (AppItem item : items) {
            if (excludeOwnApp && ownPkg.equals(item.packageName)) continue;
            if (listCount > 0 && shown >= listCount) break;

            View row = createRow(item);
            LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (shown > 0) lp.topMargin = itemSpacing;
            addView(row, lp);
            shown++;
        }

        if (shown == 0) applyEmptyState();
        else setVisibility(VISIBLE);
    }

    private void applyEmptyState() {
        if (hideWhenEmpty) setVisibility(GONE);
    }

    private View createRow(final AppItem item) {
        LinearLayout row = newRowContainer(true);

        ImageView logo = new ImageView(getContext());
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        roundOutline(logo, logoCornerRadius);
        logo.setContentDescription(item.name);
        row.addView(logo, new LayoutParams(logoSize, logoSize));
        loadIcon(logo, item.iconUrl);

        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(VERTICAL);
        LayoutParams colLp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        colLp.leftMargin = dp(12);
        row.addView(col, colLp);

        TextView title = new TextView(getContext());
        title.setText(item.name);
        title.setTextColor(titleColor);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, titleSize);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(title, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!TextUtils.isEmpty(item.description)) {
            TextView desc = new TextView(getContext());
            desc.setText(item.description);
            desc.setTextColor(descriptionColor);
            desc.setTextSize(TypedValue.COMPLEX_UNIT_PX, descriptionSize);
            desc.setMaxLines(descriptionMaxLines);
            desc.setEllipsize(TextUtils.TruncateAt.END);
            LayoutParams dLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dLp.topMargin = dp(2);
            col.addView(desc, dLp);
        }

        row.setOnClickListener(v -> openApp(item));
        return row;
    }

    private LinearLayout newRowContainer(boolean clickable) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(itemPadding, itemPadding, itemPadding, itemPadding);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(itemBackgroundColor);
        bg.setCornerRadius(itemCornerRadius);
        if (itemBorderWidth > 0) bg.setStroke(itemBorderWidth, itemBorderColor);

        Drawable finalBg = bg;
        if (clickable && Build.VERSION.SDK_INT >= 21) {
            GradientDrawable mask = new GradientDrawable();
            mask.setColor(Color.BLACK);
            mask.setCornerRadius(itemCornerRadius);
            finalBg = new RippleDrawable(ColorStateList.valueOf(0x22000000), bg, mask);
        }
        row.setBackground(finalBg);
        row.setClickable(clickable);
        return row;
    }

    // ---- skeleton (shown only while there is no cache and we are loading) ----

    private void showSkeleton() {
        stopSkeleton();
        removeAllViews();
        setVisibility(VISIBLE);

        int count = listCount > 0 ? listCount : 3;
        for (int i = 0; i < count; i++) {
            LinearLayout row = newRowContainer(false);

            View logo = new View(getContext());
            logo.setBackground(roundedRect(skeletonColor, logoCornerRadius));
            row.addView(logo, new LayoutParams(logoSize, logoSize));

            LinearLayout col = new LinearLayout(getContext());
            col.setOrientation(VERTICAL);
            LayoutParams colLp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            colLp.leftMargin = dp(12);
            row.addView(col, colLp);

            View bar1 = new View(getContext());
            bar1.setBackground(roundedRect(skeletonColor, dp(4)));
            col.addView(bar1, new LayoutParams(dp(120), dp(12)));

            View bar2 = new View(getContext());
            bar2.setBackground(roundedRect(skeletonColor, dp(4)));
            LayoutParams b2 = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10));
            b2.topMargin = dp(8);
            col.addView(bar2, b2);

            LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.topMargin = itemSpacing;
            addView(row, lp);
            skeletonRows.add(row);
        }

        skeletonAnimator = ValueAnimator.ofFloat(0.45f, 1f);
        skeletonAnimator.setDuration(800);
        skeletonAnimator.setRepeatCount(ValueAnimator.INFINITE);
        skeletonAnimator.setRepeatMode(ValueAnimator.REVERSE);
        skeletonAnimator.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            for (View r : skeletonRows) r.setAlpha(v);
        });
        skeletonAnimator.start();
    }

    private void stopSkeleton() {
        if (skeletonAnimator != null) {
            skeletonAnimator.cancel();
            skeletonAnimator = null;
        }
        skeletonRows.clear();
    }

    // ------------------------------------------------------------------
    // Click handling
    // ------------------------------------------------------------------

    private void openApp(AppItem item) {
        if (clickListener != null && clickListener.onAppClick(item)) return;

        Uri uri = null;
        if (!TextUtils.isEmpty(item.link)) {
            Uri u = Uri.parse(item.link);
            String scheme = u.getScheme();
            if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme) || "market".equalsIgnoreCase(scheme)) {
                uri = u; // only allow safe schemes coming from the API
            }
        }
        if (uri == null && !TextUtils.isEmpty(item.packageName)) {
            uri = Uri.parse("market://details?id=" + item.packageName);
        }
        if (uri == null) return;

        try {
            startView(uri);
        } catch (ActivityNotFoundException e) {
            if (!TextUtils.isEmpty(item.packageName)) {
                try {
                    startView(Uri.parse("https://play.google.com/store/apps/details?id=" + item.packageName));
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void startView(Uri uri) {
        Intent i = new Intent(Intent.ACTION_VIEW, uri);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
    }

    // ------------------------------------------------------------------
    // Image loading (memory + disk cache)
    // ------------------------------------------------------------------

    private void loadIcon(final ImageView iv, final String url) {
        iv.setTag(url);
        iv.setImageDrawable(new ColorDrawable(skeletonColor));
        if (TextUtils.isEmpty(url) || !(url.startsWith("https://") || url.startsWith("http://")))
            return;

        Bitmap cached = MEM_CACHE.get(url);
        if (cached != null) {
            iv.setImageBitmap(cached);
            return;
        }

        final File dir = new File(getContext().getCacheDir(), "more_apps_icons");
        final int target = Math.max(logoSize * 2, 64);

        IMG_EXEC.execute(() -> {
            Bitmap bmp = null;
            try {
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, sha1(url));
                if (!f.exists()) {
                    downloadTo(url, f);
                    trimDisk(dir);
                }
                bmp = decodeSampled(f, target);
                if (bmp == null) f.delete(); // corrupt file
            } catch (Exception ignored) {
            }

            final Bitmap result = bmp;
            if (result != null) MEM_CACHE.put(url, result);
            MAIN.post(() -> {
                if (result != null && url.equals(iv.getTag())) iv.setImageBitmap(result);
            });
        });
    }

    private static void downloadTo(String urlStr, File dest) throws IOException {
        HttpURLConnection c = null;
        File tmp = new File(dest.getPath() + ".tmp");
        try {
            c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code);

            InputStream in = c.getInputStream();
            FileOutputStream out = new FileOutputStream(tmp);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            } finally {
                out.close();
            }
            if (!tmp.renameTo(dest)) throw new IOException("rename failed");
        } finally {
            if (c != null) c.disconnect();
            if (tmp.exists()) tmp.delete();
        }
    }

    private static Bitmap decodeSampled(File f, int target) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;

        int sample = 1;
        while (o.outWidth / (sample * 2) >= target && o.outHeight / (sample * 2) >= target)
            sample *= 2;

        o.inJustDecodeBounds = false;
        o.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    private static void trimDisk(File dir) {
        File[] files = dir.listFiles();
        if (files == null || files.length <= MAX_DISK_ICONS) return;
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i = 0; i < files.length - MAX_DISK_ICONS; i++) files[i].delete();
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    private static void roundOutline(View v, final int radius) {
        if (Build.VERSION.SDK_INT < 21) return;
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        v.setClipToOutline(true);
    }

    private static GradientDrawable roundedRect(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private static String sha1(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }
}
