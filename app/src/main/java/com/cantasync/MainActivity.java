package com.cantasync;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Environment;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.provider.Settings;
import android.provider.DocumentsContract;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.InputType;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.BaseAdapter;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService artworkExecutor = Executors.newSingleThreadExecutor();
    private EditText titleInput, artistInput;
    private TextView status, selectedInfo, lyrics;
    private ImageView heroCover;
    private TextView heroPlaceholder;
    private ScrollView rootScroll, lyricScroll;
    private LinearLayout resultCard, lyricActions;
    private View copyButton, saveButton, applyButton;
    private TrackAdapter trackAdapter;
    private MediaController activePlayer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<LyricRow> lyricRows = new ArrayList<>();
    private SpannableString lyricDisplay;
    private int highlightedRow = -2;
    private String selectedLrc = "";
    private String selectedTitle = "";
    private String selectedArtist = "";
    private final ArrayList<Track> results = new ArrayList<>();
    private Track selectedTrack, activeArtworkTrack;
    private Bitmap displayedArtwork;
    private final Map<String, Bitmap> artworkCache = new ConcurrentHashMap<>();
    private final Map<String, ArrayList<String>> recordingReleaseCache = new ConcurrentHashMap<>();
    private long lastMusicBrainzRequestMs;
    private static final int REQUEST_MUSIC_FOLDER = 42;
    private static final int MUSIC_ACTION_SAVE = 1;
    private static final int MUSIC_ACTION_APPLY = 2;
    private int pendingMusicAction;
    private static final int REQUEST_INSTALL_SOURCES = 43;
    private DownloadManager downloadManager;
    private long updateDownloadId = -1;
    private Uri pendingUpdateUri;
    private boolean updateReceiverRegistered;
    private final BroadcastReceiver updateDownloadReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            long expected = getSharedPreferences("cantasync_settings", MODE_PRIVATE).getLong("update_download_id", -1);
            if (id >= 0 && id == expected) finishUpdateDownload(id);
        }
    };
    private final int purple = 0xFF7438D5, cyan = 0xFF20CFE0;
    private int ink, muted, pageColor, border, cardColor, softSurface, themeMode;

    private static class Track {
        String title, artist, album, lrc;
        volatile String artworkState = "Buscando portada…";
        volatile Bitmap artwork;
        Track(JSONObject o) {
            title = o.optString("trackName", ""); artist = o.optString("artistName", "");
            album = o.optString("albumName", ""); lrc = o.optString("syncedLyrics", "");
        }
    }
    private static class LyricRow {
        long startMs; int start, end;
        LyricRow(long time, int from, int to) { startMs=time; start=from; end=to; }
    }
    private static class AudioCandidate {
        Uri parentUri;
        String fileName, displayPath;
        AudioCandidate(Uri parent, String name, String path) { parentUri = parent; fileName = name; displayPath = path; }
    }

    private class ActionIcon extends View {
        private final int icon;
        private final int iconColor;
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        ActionIcon(int icon, String description) {
            super(MainActivity.this); this.icon = icon; this.iconColor = icon == 1 ? cyan : purple; setContentDescription(description); setClickable(true); setFocusable(true);
            android.graphics.drawable.GradientDrawable mask = new android.graphics.drawable.GradientDrawable(); mask.setShape(android.graphics.drawable.GradientDrawable.OVAL); mask.setColor(0xFFFFFFFF);
            setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x44FFFFFF), null, mask));
        }
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas); float d=getResources().getDisplayMetrics().density, cx=getWidth()/2f, cy=getHeight()/2f;
            paint.setColor(iconColor); paint.setStyle(android.graphics.Paint.Style.STROKE); paint.setStrokeWidth(2.0f*d); paint.setStrokeCap(android.graphics.Paint.Cap.ROUND); paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
            android.graphics.RectF r = new android.graphics.RectF(cx-8*d,cy-9*d,cx+8*d,cy+10*d);
            if(icon==0) {
                canvas.drawRoundRect(r,1.5f*d,1.5f*d,paint); canvas.drawRoundRect(new android.graphics.RectF(cx-4*d,cy-11*d,cx+4*d,cy-6*d),1*d,1*d,paint);
                canvas.drawLine(cx-4*d,cy-1*d,cx+4*d,cy-1*d,paint); canvas.drawLine(cx-4*d,cy+4*d,cx+4*d,cy+4*d,paint);
            } else if(icon==1) {
                android.graphics.Path p=new android.graphics.Path(); p.moveTo(cx-10*d,cy-6*d); p.lineTo(cx-3*d,cy-6*d); p.lineTo(cx,cy-2*d); p.lineTo(cx+10*d,cy-2*d); p.lineTo(cx+8*d,cy+9*d); p.lineTo(cx-10*d,cy+9*d); p.close(); canvas.drawPath(p,paint);
                canvas.drawLine(cx,cy+2*d,cx,cy+7*d,paint); canvas.drawLine(cx-3*d,cy+4*d,cx,cy+7*d,paint); canvas.drawLine(cx+3*d,cy+4*d,cx,cy+7*d,paint);
            } else if (icon == 2) {
                // A simple check-circle reads as “apply” without the cramped music-note glyph.
                canvas.drawCircle(cx,cy,9*d,paint);
                android.graphics.Path check=new android.graphics.Path();
                check.moveTo(cx-4.5f*d,cy); check.lineTo(cx-1.3f*d,cy+3.2f*d); check.lineTo(cx+5*d,cy-3.5f*d);
                canvas.drawPath(check,paint);
            } else if (icon == 3) {
                canvas.drawCircle(cx,cy,6*d,paint); canvas.drawCircle(cx,cy,2.3f*d,paint);
                for(int i=0;i<8;i++){double a=Math.PI*i/4; canvas.drawLine(cx+(float)Math.cos(a)*8*d,cy+(float)Math.sin(a)*8*d,cx+(float)Math.cos(a)*10*d,cy+(float)Math.sin(a)*10*d,paint);}
            } else {
                canvas.drawCircle(cx,cy-7*d,1.8f*d,paint); canvas.drawCircle(cx,cy,1.8f*d,paint); canvas.drawCircle(cx,cy+7*d,1.8f*d,paint);
            }
        }
    }

    private class TrackAdapter extends BaseAdapter {
        private final ArrayList<Track> tracks;
        TrackAdapter(ArrayList<Track> items) { tracks = items; }
        @Override public int getCount() { return tracks.size(); }
        @Override public Object getItem(int position) { return tracks.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View recycled, android.view.ViewGroup parent) {
            Track track = tracks.get(position);
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(10), dp(14), dp(10));

            android.widget.FrameLayout coverFrame = new android.widget.FrameLayout(MainActivity.this);
            LinearLayout.LayoutParams coverLp = new LinearLayout.LayoutParams(dp(56), dp(56)); coverFrame.setLayoutParams(coverLp);
            GradientDrawable coverBg = new GradientDrawable(); coverBg.setColor(softSurface); coverBg.setCornerRadius(dp(6)); coverFrame.setBackground(coverBg); coverFrame.setClipToOutline(true);
            ImageView cover = new ImageView(MainActivity.this); cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            coverFrame.addView(cover, new android.widget.FrameLayout.LayoutParams(-1, -1));
            TextView noCover = text("♫", 25, muted); noCover.setGravity(Gravity.CENTER);
            coverFrame.addView(noCover, new android.widget.FrameLayout.LayoutParams(-1, -1));
            if (track.artwork != null) { cover.setImageBitmap(track.artwork); noCover.setVisibility(View.GONE); }
            row.addView(coverFrame);

            LinearLayout details = new LinearLayout(MainActivity.this);
            details.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams detailsLp = new LinearLayout.LayoutParams(0, -2, 1f);
            detailsLp.leftMargin = dp(12); row.addView(details, detailsLp);
            TextView title = text(track.title, 16, ink); title.setTypeface(null, Typeface.BOLD);
            details.addView(title);
            TextView subtitle = lightText(track.artist + (track.album.isEmpty() ? "" : " · " + track.album), 13, muted);
            details.addView(subtitle);
            TextView source = lightText(track.artworkState, 11, muted);
            LinearLayout.LayoutParams sourceLp = new LinearLayout.LayoutParams(-2, -2);
            sourceLp.topMargin = dp(3); details.addView(source, sourceLp);
            return row;
        }
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        applyThemeColors();
        getWindow().setStatusBarColor(pageColor); getWindow().setNavigationBarColor(pageColor);
        getWindow().getDecorView().setSystemUiVisibility(themeMode == 0 ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
        downloadManager = (DownloadManager)getSystemService(DOWNLOAD_SERVICE);
        IntentFilter downloadFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(updateDownloadReceiver, downloadFilter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(updateDownloadReceiver, downloadFilter);
        updateReceiverRegistered = true;
        buildUi();
    }

    private void applyThemeColors() {
        themeMode = getSharedPreferences("cantasync_settings", MODE_PRIVATE).getInt("appearance", 2);
        if (themeMode == 1) {
            pageColor=0xFF17151D; cardColor=0xFF23212A; softSurface=0xFF302D36; border=0xFF3A3543;
            ink=0xFFF6F3FA; muted=0xFFB8B2C2;
        } else if (themeMode == 2) {
            pageColor=0xFF000000; cardColor=0xFF09090C; softSurface=0xFF141318; border=0xFF29262F;
            ink=0xFFF7F5FA; muted=0xFFAAA4B4;
        } else {
            themeMode=0; pageColor=0xFFF8F7FA; cardColor=0xFFFFFFFF; softSurface=0xFFEDE9F4; border=0xFFE5DDF1;
            ink=0xFF111016; muted=0xFF746F7D;
        }
    }

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); return t;
    }
    private TextView lightText(String value, int size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL)); return t;
    }
    private void buildUi() {
        ScrollView scroll = new ScrollView(this); rootScroll=scroll; scroll.setFillViewport(true); scroll.setBackgroundColor(pageColor);
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(22), dp(24), dp(22), dp(28));
        scroll.addView(page); setContentView(scroll);

        TextView brand = text("CantaSync", 29, ink);
        Typeface brandTypeface = Typeface.create("Segoe UI", Typeface.BOLD);
        if (brandTypeface == Typeface.DEFAULT) brandTypeface = Typeface.create("sans-serif", Typeface.BOLD);
        brand.setTypeface(brandTypeface);
        LinearLayout brandRow = new LinearLayout(this); brandRow.setOrientation(LinearLayout.HORIZONTAL); brandRow.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(brandRow, new LinearLayout.LayoutParams(-1, -2));
        brandRow.addView(brand, new LinearLayout.LayoutParams(0, -2, 1f));
        View more = new ActionIcon(4, "Más opciones");
        brandRow.addView(more, new LinearLayout.LayoutParams(dp(44), dp(44))); more.setOnClickListener(v -> showMoreMenu(more));
        android.widget.FrameLayout hero = new android.widget.FrameLayout(this);
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(-1, dp(164));
        heroLp.topMargin = dp(12); heroLp.bottomMargin = dp(8); page.addView(hero, heroLp);
        heroCover = new ImageView(this); heroCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        android.widget.FrameLayout.LayoutParams heroImageLp = new android.widget.FrameLayout.LayoutParams(dp(148), dp(148), Gravity.CENTER);
        GradientDrawable coverShape = new GradientDrawable(); coverShape.setColor(softSurface); coverShape.setCornerRadius(dp(12)); heroCover.setBackground(coverShape); heroCover.setClipToOutline(true);
        hero.addView(heroCover, heroImageLp);
        heroPlaceholder = text("♫", 52, purple); heroPlaceholder.setGravity(Gravity.CENTER);
        hero.addView(heroPlaceholder, heroImageLp);
        TextView intro = lightText("Encuentra letras sincronizadas para cantar siguiendo el ritmo.", 15, muted);
        LinearLayout.LayoutParams introLp = new LinearLayout.LayoutParams(-1, -2); introLp.topMargin = dp(5); introLp.bottomMargin = dp(22); page.addView(intro, introLp);

        page.addView(label("ARTISTA")); artistInput = input("Nombre del artista"); page.addView(artistInput, fieldLp());
        page.addView(label("CANCIÓN")); titleInput = input("Título de la canción"); page.addView(titleInput, fieldLp());
        LinearLayout searchActions = new LinearLayout(this); searchActions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams searchActionsLp = new LinearLayout.LayoutParams(-1, -2); searchActionsLp.topMargin = dp(4); searchActionsLp.bottomMargin = dp(8); page.addView(searchActions, searchActionsLp);
        Button search = new Button(this); search.setText("Buscar Letra"); search.setAllCaps(false); search.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); search.setTextColor(0xFFFFFFFF); search.setBackgroundTintList(android.content.res.ColorStateList.valueOf(purple));
        LinearLayout.LayoutParams searchButtonLp = new LinearLayout.LayoutParams(0, -2, 1f); searchButtonLp.rightMargin = dp(4); searchActions.addView(search, searchButtonLp); search.setOnClickListener(v -> search());
        Button detect = new Button(this); detect.setText("Canción Actual"); detect.setAllCaps(false); detect.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); detect.setTextColor(ink); detect.setBackgroundTintList(android.content.res.ColorStateList.valueOf(cyan));
        LinearLayout.LayoutParams detectButtonLp = new LinearLayout.LayoutParams(0, -2, 1f); detectButtonLp.leftMargin = dp(4); searchActions.addView(detect, detectButtonLp);
        detect.setOnClickListener(v -> detectPlayingTrack());
        status = text("Solo mostramos resultados con marcas de tiempo.", 13, muted);
        status.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2); statusLp.topMargin = dp(10); statusLp.bottomMargin = dp(12); page.addView(status, statusLp);

        resultCard = new LinearLayout(this); resultCard.setOrientation(LinearLayout.VERTICAL);
        resultCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(cardColor); cardBackground.setCornerRadius(dp(14));
        cardBackground.setStroke(dp(1), border); resultCard.setBackground(cardBackground);
        resultCard.setVisibility(View.GONE);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.topMargin = dp(8); cardLp.bottomMargin = dp(10); page.addView(resultCard, cardLp);

        selectedInfo = text("", 16, ink); selectedInfo.setTypeface(null, Typeface.BOLD); selectedInfo.setGravity(Gravity.START);
        resultCard.addView(selectedInfo, new LinearLayout.LayoutParams(-1, -2));
        lyricActions = new LinearLayout(this); lyricActions.setOrientation(LinearLayout.HORIZONTAL); lyricActions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lyricActionsLp = new LinearLayout.LayoutParams(-1, dp(68)); lyricActionsLp.topMargin = dp(5); lyricActionsLp.bottomMargin = dp(3); resultCard.addView(lyricActions, lyricActionsLp);
        lyricScroll = new ScrollView(this); lyricScroll.setFillViewport(false);
        lyricScroll.setVerticalScrollBarEnabled(true);
        lyrics = text("", 17, ink); lyrics.setLineSpacing(dp(7), 1f); lyrics.setGravity(Gravity.START);
        lyricScroll.addView(lyrics, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams lyricScrollLp = new LinearLayout.LayoutParams(-1, dp(320));
        lyricScrollLp.topMargin = dp(5); resultCard.addView(lyricScroll, lyricScrollLp);
        copyButton = actionItem(0, "Copiar", "Copiar letra");
        saveButton = actionItem(1, "Guardar .lrc", "Guardar letra como archivo LRC");
        applyButton = actionItem(2, "Aplicar", "Aplicar letra a la canción actual");
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(0, dp(64), 1f); iconLp.setMargins(dp(3), 0, dp(3), 0); lyricActions.addView(copyButton, iconLp);
        iconLp = new LinearLayout.LayoutParams(0, dp(64), 1f); iconLp.setMargins(dp(3), 0, dp(3), 0); lyricActions.addView(saveButton, iconLp);
        iconLp = new LinearLayout.LayoutParams(0, dp(64), 1f); iconLp.setMargins(dp(3), 0, dp(3), 0); lyricActions.addView(applyButton, iconLp);
        copyButton.setOnClickListener(v -> copyLyrics());
        saveButton.setOnClickListener(v -> ensureMusicFolder(MUSIC_ACTION_SAVE));
        applyButton.setOnClickListener(v -> ensureMusicFolder(MUSIC_ACTION_APPLY));
        copyButton.setVisibility(View.GONE); lyricActions.setVisibility(View.GONE);
    }

    private View actionItem(int icon, String label, String description) {
        LinearLayout item=new LinearLayout(this); item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER); item.setContentDescription(description); item.setClickable(true); item.setFocusable(true);
        GradientDrawable mask=new GradientDrawable(); mask.setColor(0xFFFFFFFF); mask.setCornerRadius(dp(10));
        item.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x227438D5),null,mask));
        ActionIcon glyph=new ActionIcon(icon,description); glyph.setClickable(false); glyph.setFocusable(false); glyph.setBackground(null);
        item.addView(glyph,new LinearLayout.LayoutParams(dp(28),dp(29)));
        TextView caption=text(label,12,muted); caption.setGravity(Gravity.CENTER); caption.setSingleLine(true);
        item.addView(caption,new LinearLayout.LayoutParams(-1,dp(20)));
        return item;
    }

    private void showMoreMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Buscar actualizaciones");
        menu.getMenu().add("Apariencia");
        menu.setOnMenuItemClickListener(item -> {
            if ("Buscar actualizaciones".contentEquals(item.getTitle())) { checkForUpdates(); return true; }
            if ("Apariencia".contentEquals(item.getTitle())) { showAppearanceChooser(); return true; }
            return false;
        });
        menu.show();
    }

    private void checkForUpdates() {
        status.setText("Buscando actualizaciones en GitHub…");
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL endpoint = new URL("https://api.github.com/repos/Jerkinall/CantaSync/releases?per_page=30");
                connection = (HttpURLConnection)endpoint.openConnection();
                connection.setRequestMethod("GET"); connection.setConnectTimeout(12000); connection.setReadTimeout(15000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
                connection.setRequestProperty("User-Agent", "CantaSync-Android");
                if (connection.getResponseCode() != 200) throw new Exception("GitHub no respondió correctamente");
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line; while ((line = reader.readLine()) != null) response.append(line);
                }
                JSONArray releases = new JSONArray(response.toString());
                JSONObject newest = null; String apkUrl = null, apkName = null;
                for (int i = 0; i < releases.length(); i++) {
                    JSONObject release = releases.getJSONObject(i);
                    if (release.optBoolean("draft", false) || !release.optBoolean("prerelease", false)) continue;
                    String tag = release.optString("tag_name", "");
                    if (parseVersion(tag) == null) continue;
                    JSONArray assets = release.optJSONArray("assets"); if (assets == null) continue;
                    for (int j = 0; j < assets.length(); j++) {
                        JSONObject asset = assets.getJSONObject(j);
                        String name = asset.optString("name", "");
                        String url = asset.optString("browser_download_url", "");
                        if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".apk") &&
                            url.startsWith("https://github.com/Jerkinall/CantaSync/releases/download/")) {
                            if (newest == null || compareVersions(tag, newest.optString("tag_name", "")) > 0) {
                                newest = release; apkUrl = url; apkName = name;
                            }
                        }
                    }
                }
                if (connection != null) connection.disconnect();
                if (newest == null) throw new Exception("No se encontró un release debug con APK en GitHub");
                PackageInfo installed = getPackageManager().getPackageInfo(getPackageName(), 0);
                String currentVersion = installed.versionName == null ? "0.0.0-debug" : installed.versionName;
                String latestVersion = newest.optString("tag_name", "");
                JSONObject selectedRelease = newest; String selectedUrl = apkUrl, selectedName = apkName;
                if (compareVersions(latestVersion, currentVersion) <= 0) {
                    runOnUiThread(() -> { status.setText("CantaSync está actualizado (" + currentVersion + ")."); Toast.makeText(this, "Ya tienes la versión más reciente", Toast.LENGTH_SHORT).show(); });
                } else {
                    runOnUiThread(() -> showUpdateAvailable(selectedRelease, selectedName, selectedUrl));
                }
            } catch (Exception e) {
                if (connection != null) connection.disconnect();
                String reason = e.getMessage() == null ? "Comprueba tu conexión e inténtalo de nuevo." : e.getMessage();
                runOnUiThread(() -> { status.setText("No se pudo buscar actualizaciones."); Toast.makeText(this, reason, Toast.LENGTH_LONG).show(); });
            }
        });
    }

    private int[] parseVersion(String value) {
        Matcher matcher = Pattern.compile("(?i)^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-debug)?$").matcher(value.trim());
        if (!matcher.matches()) return null;
        try { return new int[] { Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)) }; }
        catch (NumberFormatException e) { return null; }
    }

    private int compareVersions(String first, String second) {
        int[] a = parseVersion(first), b = parseVersion(second);
        if (a == null) return -1; if (b == null) return 1;
        for (int i = 0; i < 3; i++) if (a[i] != b[i]) return Integer.compare(a[i], b[i]);
        return 0;
    }

    private void showUpdateAvailable(JSONObject release, String assetName, String downloadUrl) {
        String details = release.optString("body", "").trim();
        if (details.length() > 700) details = details.substring(0, 697) + "…";
        String message = "Nueva versión " + release.optString("tag_name") + " disponible.\n\n" +
            (details.isEmpty() ? "Descarga e instala el APK de GitHub." : details) +
            "\n\nAndroid mostrará una confirmación antes de instalarla.";
        dialogBuilder().setTitle("Actualización disponible").setMessage(message)
            .setPositiveButton("Descargar", (dialog, which) -> downloadUpdate(assetName, downloadUrl))
            .setNegativeButton("Ahora no", null).show();
    }

    private void downloadUpdate(String assetName, String downloadUrl) {
        try {
            long existingDownload = getSharedPreferences("cantasync_settings", MODE_PRIVATE).getLong("update_download_id", -1);
            if (existingDownload >= 0) { Toast.makeText(this, "La actualización ya se está descargando", Toast.LENGTH_SHORT).show(); return; }
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
            request.setTitle("CantaSync " + assetName); request.setDescription("Descargando actualización desde GitHub");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, assetName);
            updateDownloadId = downloadManager.enqueue(request);
            getSharedPreferences("cantasync_settings", MODE_PRIVATE).edit().putLong("update_download_id", updateDownloadId).apply();
            status.setText("Descargando actualización desde GitHub…");
            Toast.makeText(this, "La descarga de CantaSync comenzó", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo iniciar la descarga: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void finishUpdateDownload(long downloadId) {
        getSharedPreferences("cantasync_settings", MODE_PRIVATE).edit().remove("update_download_id").apply();
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor cursor = downloadManager.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) throw new Exception("No se encontró la descarga");
            int statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int reasonColumn = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
            int downloadStatus = cursor.getInt(statusColumn);
            if (downloadStatus != DownloadManager.STATUS_SUCCESSFUL) {
                int reason = cursor.getInt(reasonColumn); downloadManager.remove(downloadId);
                throw new Exception("La descarga falló (" + reason + ")");
            }
        } catch (Exception e) {
            String reason = e.getMessage() == null ? "Error de descarga" : e.getMessage();
            runOnUiThread(() -> { status.setText("No se pudo descargar la actualización."); Toast.makeText(this, reason, Toast.LENGTH_LONG).show(); }); return;
        }
        Uri downloadedUri = downloadManager.getUriForDownloadedFile(downloadId);
        if (downloadedUri == null) { Toast.makeText(this, "No se pudo abrir el APK descargado", Toast.LENGTH_LONG).show(); return; }
        status.setText("Verificando la actualización descargada…");
        executor.execute(() -> {
            File apkCopy = new File(getCacheDir(), "cantasync-update-" + downloadId + ".apk");
            try (android.os.ParcelFileDescriptor descriptor = downloadManager.openDownloadedFile(downloadId);
                 InputStream input = new FileInputStream(descriptor.getFileDescriptor());
                 OutputStream output = new FileOutputStream(apkCopy)) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "No se pudo verificar el APK descargado", Toast.LENGTH_LONG).show()); return;
            }
            try {
                int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
                PackageInfo candidate = getPackageManager().getPackageArchiveInfo(apkCopy.getAbsolutePath(), flags);
                PackageInfo installed = getPackageManager().getPackageInfo(getPackageName(), flags);
                if (candidate == null || !getPackageName().equals(candidate.packageName)) throw new Exception("El APK no corresponde a CantaSync");
                if (!signerDigests(candidate).equals(signerDigests(installed)))
                    throw new Exception("La firma de esta actualización no coincide con la app instalada. Android no permitirá instalarla sobre esta versión.");
                long newCode = Build.VERSION.SDK_INT >= 28 ? candidate.getLongVersionCode() : candidate.versionCode;
                long currentCode = Build.VERSION.SDK_INT >= 28 ? installed.getLongVersionCode() : installed.versionCode;
                if (newCode <= currentCode) throw new Exception("El APK descargado no tiene un número de versión superior al instalado");
                runOnUiThread(() -> { pendingUpdateUri = downloadedUri; status.setText("Actualización verificada. Lista para instalar."); askToInstallUpdate(); });
            } catch (Exception e) {
                String reason = e.getMessage() == null ? "No se pudo verificar el certificado del APK" : e.getMessage();
                runOnUiThread(() -> { status.setText("La actualización no se puede instalar."); new AlertDialog.Builder(this).setTitle("No se puede actualizar")
                    .setMessage(reason).setPositiveButton("Entendido", null).show(); });
            }
        });
    }

    private Set<String> signerDigests(PackageInfo info) throws Exception {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) signatures = info.signingInfo.getApkContentsSigners();
        else signatures = info.signatures;
        if (signatures == null || signatures.length == 0) throw new Exception("El APK no tiene una firma válida");
        Set<String> digests = new HashSet<>();
        for (Signature signature : signatures) {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());
            StringBuilder hex = new StringBuilder(); for (byte value : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", value));
            digests.add(hex.toString());
        }
        return digests;
    }

    private void askToInstallUpdate() {
        if (pendingUpdateUri == null) return;
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            dialogBuilder().setTitle("Permitir instalación de actualizaciones")
                .setMessage("Android requiere que autorices a CantaSync a abrir el instalador para este APK descargado desde el release oficial de GitHub.")
                .setPositiveButton("Abrir ajustes", (dialog, which) -> startActivityForResult(
                    new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())), REQUEST_INSTALL_SOURCES))
                .setNegativeButton("Ahora no", null).show(); return;
        }
        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(pendingUpdateUri, "application/vnd.android.package-archive");
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(install); }
        catch (Exception e) { Toast.makeText(this, "Android no pudo abrir el instalador del APK", Toast.LENGTH_LONG).show(); }
    }

    private void showAppearanceChooser() {
        String[] options = {"Claro", "Oscuro", "AMOLED (negro puro)"};
        dialogBuilder().setTitle("Apariencia")
            .setSingleChoiceItems(options, themeMode, (dialog, which) -> { dialog.dismiss(); setAppearance(which); })
            .setNegativeButton("Cancelar", null).show();
    }

    private AlertDialog.Builder dialogBuilder() {
        return new AlertDialog.Builder(this, themeMode == 0 ? android.R.style.Theme_Material_Light_Dialog_Alert : android.R.style.Theme_Material_Dialog_Alert);
    }

    private void setAppearance(int mode) {
        if (mode == themeMode) return;
        String title = titleInput.getText().toString(), artist = artistInput.getText().toString();
        String currentStatus = status.getText().toString(); int scrollY = rootScroll.getScrollY();
        Track selected = selectedTrack; Bitmap currentCover = displayedArtwork;
        handler.removeCallbacks(syncTick);
        getSharedPreferences("cantasync_settings", MODE_PRIVATE).edit().putInt("appearance", mode).apply();
        applyThemeColors();
        getWindow().setStatusBarColor(pageColor); getWindow().setNavigationBarColor(pageColor);
        getWindow().getDecorView().setSystemUiVisibility(themeMode == 0 ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
        buildUi(); titleInput.setText(title); artistInput.setText(artist);
        if (selected != null) select(selected); else { status.setText(currentStatus); if (currentCover != null) showHeroArtwork(currentCover); }
        rootScroll.post(() -> rootScroll.scrollTo(0, scrollY));
    }
    private TextView label(String s) { TextView t = text(s, 12, muted); t.setTypeface(null, Typeface.BOLD); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(4); p.bottomMargin = dp(5); t.setLayoutParams(p); return t; }
    private EditText input(String hint) { EditText e = new EditText(this); e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS); e.setSingleLine(true); e.setTextSize(16); e.setTextColor(ink); e.setHintTextColor(muted); e.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); e.setHint(hint); e.setPadding(dp(14), dp(9), dp(14), dp(9)); e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(purple)); return e; }
    private LinearLayout.LayoutParams fieldLp() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(12); return p; }

    private void search() {
        String title = titleInput.getText().toString().trim(), artist = artistInput.getText().toString().trim();
        if (title.isEmpty()) { titleInput.setError("Escribe el título"); return; }
        lyricDisplay=null; lyricRows.clear(); highlightedRow=-2;
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(titleInput.getWindowToken(), 0);
        status.setText("Buscando coincidencias sincronizadas…"); results.clear();
        copyButton.setVisibility(View.GONE); saveButton.setVisibility(View.GONE); applyButton.setVisibility(View.GONE); lyricActions.setVisibility(View.GONE); resultCard.setVisibility(View.GONE);
        executor.execute(() -> {
            try {
                String q = "track_name=" + enc(title) + (artist.isEmpty() ? "" : "&artist_name=" + enc(artist));
                HttpURLConnection c = (HttpURLConnection)new URL("https://lrclib.net/api/search?" + q).openConnection();
                c.setRequestMethod("GET"); c.setConnectTimeout(12000); c.setReadTimeout(15000);
                c.setRequestProperty("Accept", "application/json"); c.setRequestProperty("Lrclib-App-Id", "CantaSync/0.1 Android");
                int code = c.getResponseCode();
                if (code < 200 || code >= 300) throw new Exception("El servicio respondió " + code);
                StringBuilder body = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) { String line; while ((line = r.readLine()) != null) body.append(line); }
                c.disconnect(); JSONArray arr = new JSONArray(body.toString()); ArrayList<Track> found = new ArrayList<>();
                for (int i=0; i<arr.length(); i++) { Track t = new Track(arr.getJSONObject(i)); if (!t.lrc.trim().isEmpty()) found.add(t); }
                runOnUiThread(() -> showResults(found));
            } catch (Exception ex) { runOnUiThread(() -> status.setText("No se pudo buscar. Comprueba tu conexión e inténtalo otra vez.")); }
        });
    }
    private String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }
    private void showResults(ArrayList<Track> found) {
        results.clear(); results.addAll(found);
        if (found.isEmpty()) { status.setText("No encontramos letras sincronizadas para esa búsqueda. Prueba otro título o artista."); return; }
        status.setText(found.size() + " resultado(s) sincronizado(s). Elige la versión correcta:");
        ListView list = new ListView(this);
        list.setBackgroundColor(android.graphics.Color.TRANSPARENT); list.setDivider(new android.graphics.drawable.ColorDrawable(border)); list.setDividerHeight(dp(1));
        trackAdapter = new TrackAdapter(found); list.setAdapter(trackAdapter);
        LinearLayout content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(12),dp(10),dp(12),dp(6));
        GradientDrawable dialogShape=new GradientDrawable(); dialogShape.setColor(cardColor); dialogShape.setCornerRadius(dp(22)); dialogShape.setStroke(dp(1),border);
        content.setBackground(dialogShape); content.setClipToOutline(true);
        TextView resultHeading=text(found.size() + " resultados encontrados",20,ink); resultHeading.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        resultHeading.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); resultHeading.setPadding(dp(6),0,dp(6),dp(8));
        content.addView(resultHeading,new LinearLayout.LayoutParams(-1,dp(46)));
        content.addView(list,new LinearLayout.LayoutParams(-1,dp(300)));
        Button cancel=new Button(this); cancel.setText("Cancelar"); cancel.setAllCaps(false); cancel.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); cancel.setTextColor(purple); cancel.setBackgroundColor(android.graphics.Color.TRANSPARENT); cancel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cancelLp=new LinearLayout.LayoutParams(-1,dp(48)); cancelLp.gravity=Gravity.CENTER_HORIZONTAL; content.addView(cancel,cancelLp);
        AlertDialog dialog = dialogBuilder().create(); dialog.setView(content,0,0,0,0);
        cancel.setOnClickListener(v->dialog.dismiss());
        list.setOnItemClickListener((parent, view, which, id) -> { dialog.dismiss(); select(found.get(which)); });
        dialog.show();
        android.view.Window dialogWindow=dialog.getWindow();
        if(dialogWindow!=null){
            dialogWindow.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            int availableWidth=getResources().getDisplayMetrics().widthPixels-dp(40);
            dialogWindow.setLayout(Math.min(availableWidth,dp(480)),-2);
        }
        for (Track track : found) artworkExecutor.execute(() -> loadArtwork(track));
    }

    private void loadArtwork(Track track) {
        if (track.album.trim().isEmpty() && track.title.trim().isEmpty()) { track.artworkState="Sin datos de portada"; return; }
        String cacheKey = artworkCacheKey(track);
        Bitmap cached = artworkCache.get(cacheKey);
        if (cached == null) cached = artworkCache.get(recordingArtworkCacheKey(track));
        if (cached != null) { final Bitmap cachedArtwork=cached; track.artwork = cachedArtwork; track.artworkState="Portada disponible"; runOnUiThread(() -> { if (trackAdapter != null) trackAdapter.notifyDataSetChanged(); if (track == selectedTrack || track == activeArtworkTrack) showHeroArtwork(cachedArtwork); }); return; }
        try {
            ArrayList<String> recordingIds = lookupRecordingReleases(track);
            for (String id : recordingIds) {
                Bitmap bitmap = downloadArtwork("https://coverartarchive.org/release/" + id + "/front-250");
                if (bitmap != null) { publishArtwork(track, cacheKey, bitmap, "Cover Art Archive"); return; }
            }
            long wait = 1100 - (System.currentTimeMillis() - lastMusicBrainzRequestMs);
            if (wait > 0) Thread.sleep(wait);
            lastMusicBrainzRequestMs = System.currentTimeMillis();

            String query = "release:\"" + escapeLucenePhrase(track.album) + "\" AND artist:\"" + escapeLucenePhrase(track.artist) + "\"";
            String requestUrl = "https://musicbrainz.org/ws/2/release/?query=" + enc(query) + "&fmt=json&limit=5";
            HttpURLConnection mb = (HttpURLConnection)new URL(requestUrl).openConnection();
            mb.setRequestMethod("GET"); mb.setConnectTimeout(10000); mb.setReadTimeout(12000);
            mb.setRequestProperty("Accept", "application/json");
            mb.setRequestProperty("User-Agent", "CantaSync/0.1.0 (https://github.com/Jerkinall/CantaSync)");
            int code = mb.getResponseCode();
            if (code < 200 || code >= 300) { mb.disconnect(); return; }
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(mb.getInputStream(), StandardCharsets.UTF_8))) {
                String line; while ((line = reader.readLine()) != null) body.append(line);
            } finally { mb.disconnect(); }

            JSONArray releases = new JSONObject(body.toString()).optJSONArray("releases");
            ArrayList<String> releaseIds = new ArrayList<>();
            if (releases != null) {
                String expectedAlbum = normalize(track.album), expectedArtist = normalize(track.artist);
                for (int i = 0; i < releases.length() && releaseIds.size() < 4; i++) {
                    JSONObject release = releases.getJSONObject(i);
                    if (!expectedAlbum.equals(normalize(release.optString("title")))) continue;
                    if (!artistCreditMatches(release.optJSONArray("artist-credit"), expectedArtist)) continue;
                    String id = release.optString("id", ""); if (!id.isEmpty() && !releaseIds.contains(id)) releaseIds.add(id);
                }
            }
            for (String id : releaseIds) {
                Bitmap bitmap = downloadArtwork("https://coverartarchive.org/release/" + id + "/front-250");
                if (bitmap != null) { publishArtwork(track, cacheKey, bitmap, "Cover Art Archive"); return; }
            }
            for (String id : releaseIds) {
                if (recordingIds.contains(id)) continue;
                Bitmap bitmap = downloadArtwork("https://coverartarchive.org/release/" + id + "/front-250");
                if (bitmap != null) { publishArtwork(track, cacheKey, bitmap, "Cover Art Archive"); return; }
            }
        } catch (Exception ignored) {
            // Artwork is optional; keep the result selectable when a lookup fails.
        } finally {
            if (track.artwork == null) {
                track.artworkState = "Portada no encontrada";
                runOnUiThread(() -> { if (trackAdapter != null) trackAdapter.notifyDataSetChanged(); });
            }
        }
    }

    private Bitmap downloadArtwork(String imageUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)new URL(imageUrl).openConnection();
            connection.setConnectTimeout(10000); connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "CantaSync/0.2 Android");
            int code = connection.getResponseCode(); if (code < 200 || code >= 300) return null;
            String mime = connection.getContentType(); if (mime == null || !mime.toLowerCase(java.util.Locale.ROOT).startsWith("image/")) return null;
            try (java.io.InputStream imageStream = connection.getInputStream()) {
                Bitmap bitmap = BitmapFactory.decodeStream(imageStream);
                return bitmap != null && !looksLikePlaceholder(bitmap) ? bitmap : null;
            }
        } catch (Exception ignored) { return null; }
        finally { if (connection != null) connection.disconnect(); }
    }

    private ArrayList<String> lookupRecordingReleases(Track track) {
        ArrayList<String> ids = new ArrayList<>();
        if (track.title.trim().isEmpty() || track.artist.trim().isEmpty()) return ids;
        String cacheKey = recordingArtworkCacheKey(track);
        ArrayList<String> cached = recordingReleaseCache.get(cacheKey);
        if (cached != null) return cached;
        try {
            long wait = 1100 - (System.currentTimeMillis() - lastMusicBrainzRequestMs);
            if (wait > 0) Thread.sleep(wait);
            lastMusicBrainzRequestMs = System.currentTimeMillis();
            String query = "recording:\"" + escapeLucenePhrase(track.title) + "\" AND artist:\"" + escapeLucenePhrase(track.artist) + "\"";
            HttpURLConnection connection = (HttpURLConnection)new URL("https://musicbrainz.org/ws/2/recording/?query=" + enc(query) + "&fmt=json&limit=5").openConnection();
            connection.setConnectTimeout(10000); connection.setReadTimeout(12000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "CantaSync/0.2.0 (https://github.com/Jerkinall/CantaSync)");
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) { String line; while ((line = reader.readLine()) != null) body.append(line); }
            finally { connection.disconnect(); }
            JSONArray recordings = new JSONObject(body.toString()).optJSONArray("recordings");
            if (recordings == null) return ids;
            String expectedTitle = normalize(track.title), expectedArtist = normalize(track.artist);
            for (int i = 0; i < recordings.length(); i++) {
                JSONObject recording = recordings.getJSONObject(i);
                if (!expectedTitle.equals(normalize(recording.optString("title")))) continue;
                if (!artistCreditMatches(recording.optJSONArray("artist-credit"), expectedArtist)) continue;
                JSONArray releases = recording.optJSONArray("releases");
                if (releases != null) for (int j = 0; j < releases.length() && ids.size() < 8; j++) {
                    String id = releases.getJSONObject(j).optString("id", ""); if (!id.isEmpty() && !ids.contains(id)) ids.add(id);
                }
            }
            recordingReleaseCache.put(cacheKey, ids);
        } catch (Exception ignored) { }
        return ids;
    }

    private boolean looksLikePlaceholder(Bitmap bitmap) {
        if (bitmap.getWidth() < 32 || bitmap.getHeight() < 32) return true;
        long red=0, green=0, blue=0, redSq=0, greenSq=0, blueSq=0; int count=0;
        for (int y=0; y<8; y++) for (int x=0; x<8; x++) {
            int c=bitmap.getPixel((x*(bitmap.getWidth()-1))/7,(y*(bitmap.getHeight()-1))/7);
            int r=(c>>16)&255, g=(c>>8)&255, b=c&255; red+=r; green+=g; blue+=b; redSq+=(long)r*r; greenSq+=(long)g*g; blueSq+=(long)b*b; count++;
        }
        double mr=red/(double)count, mg=green/(double)count, mb=blue/(double)count;
        double variance=((redSq/(double)count-mr*mr)+(greenSq/(double)count-mg*mg)+(blueSq/(double)count-mb*mb))/3.0;
        return variance < 5.0;
    }

    private boolean artistMatches(String expected, String actual) { return !expected.isEmpty() && !actual.isEmpty() && (expected.equals(actual) || expected.contains(actual) || actual.contains(expected)); }

    private String artworkCacheKey(Track track) {
        return normalize(track.artist) + "|" + normalize(track.album) + (track.album.trim().isEmpty() ? "|" + normalize(track.title) : "");
    }

    private String recordingArtworkCacheKey(Track track) { return "recording|" + normalize(track.artist) + "|" + normalize(track.title); }

    private void publishArtwork(Track track, String cacheKey, Bitmap bitmap, String source) {
        artworkCache.put(cacheKey, bitmap); artworkCache.put(recordingArtworkCacheKey(track), bitmap); track.artwork = bitmap; track.artworkState="Portada: "+source;
        runOnUiThread(() -> {
            if (trackAdapter != null) trackAdapter.notifyDataSetChanged();
            if (track == selectedTrack || track == activeArtworkTrack) showHeroArtwork(bitmap);
        });
    }

    private void showHeroArtwork(Bitmap bitmap) { displayedArtwork=bitmap; heroCover.setImageBitmap(bitmap); heroPlaceholder.setVisibility(View.GONE); }
    private void showHeroPlaceholder() { displayedArtwork=null; heroCover.setImageDrawable(null); heroPlaceholder.setVisibility(View.VISIBLE); }

    private boolean artistCreditMatches(JSONArray credit, String expectedArtist) {
        if (credit == null || expectedArtist.isEmpty()) return false;
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < credit.length(); i++) {
            JSONObject entry = credit.optJSONObject(i);
            if (entry != null) names.append(entry.optString("name", "")).append(' ');
        }
        String actual = normalize(names.toString());
        return actual.equals(expectedArtist) || actual.contains(expectedArtist) || expectedArtist.contains(actual);
    }

    private String escapeLucenePhrase(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String normalize(String value) {
        String decomposed = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
    private void select(Track t) {
        selectedTrack = t; activeArtworkTrack = null;
        Bitmap playerArtwork = getMatchingPlayerArtwork(t);
        if (playerArtwork != null) { t.artwork=playerArtwork; t.artworkState="Portada del reproductor"; artworkCache.put(artworkCacheKey(t), playerArtwork); artworkCache.put(recordingArtworkCacheKey(t), playerArtwork); }
        if (t.artwork != null) showHeroArtwork(t.artwork); else showHeroPlaceholder();
        if (trackAdapter != null) trackAdapter.notifyDataSetChanged();
        selectedLrc = t.lrc.trim() + "\n"; selectedTitle = t.title; selectedArtist = t.artist;
        selectedInfo.setText(t.title + "\n" + t.artist + (t.album.isEmpty() ? "" : " · " + t.album));
        StringBuilder view = new StringBuilder(); lyricRows.clear();
        Pattern timestamp = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\]");
        for (String line : selectedLrc.split("\\n")) {
            Matcher m=timestamp.matcher(line); ArrayList<Long> times=new ArrayList<>();
            while (m.find()) { long fraction=Long.parseLong(m.group(3)); if (m.group(3).length()==2) fraction*=10; times.add((Long.parseLong(m.group(1))*60+Long.parseLong(m.group(2)))*1000+fraction); }
            String words=line.replaceAll("\\[[^\\]]+\\]", "").trim();
            if (times.isEmpty() || words.isEmpty()) continue;
            for (long time : times) {
                String stamp=String.format(java.util.Locale.US,"%02d:%02d",time/60000,(time/1000)%60);
                int from=view.length(); view.append(stamp).append("  ").append(words); int to=view.length(); view.append("\n");
                lyricRows.add(new LyricRow(time,from,to));
            }
        }
        lyricDisplay=new SpannableString(view.toString().trim()); highlightedRow=-2; lyrics.setText(lyricDisplay); lyricScroll.scrollTo(0, 0); resultCard.setVisibility(View.VISIBLE);
        copyButton.setVisibility(View.VISIBLE); saveButton.setVisibility(View.VISIBLE); applyButton.setVisibility(View.VISIBLE); lyricActions.setVisibility(View.VISIBLE);
        status.setText(activePlayer == null ? "Letra sincronizada lista. Conecta el reproductor para resaltar la línea actual." : "Letra sincronizada lista; siguiendo la reproducción.");
        handler.removeCallbacks(syncTick); handler.post(syncTick);
    }

    private Bitmap getMatchingPlayerArtwork(Track track) {
        try {
            if (activePlayer == null || activePlayer.getMetadata() == null) return null;
            android.media.MediaMetadata metadata=activePlayer.getMetadata();
            String title=metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE);
            String artist=metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST);
            if (artist == null) artist=metadata.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            if (!normalize(track.title).equals(normalize(title)) || !artistMatches(normalize(track.artist),normalize(artist))) return null;
            Bitmap art=metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null) art=metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART);
            if (art == null) art=metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            return art;
        } catch (Exception ignored) { return null; }
    }
    private final Runnable syncTick = new Runnable() { @Override public void run() { updateCurrentLine(); handler.postDelayed(this, 350); } };
    private void detectPlayingTrack() {
        if (!hasNotificationAccess()) {
            dialogBuilder().setTitle("Permite detectar el reproductor")
                .setMessage("Android necesita tu autorización para leer el título y el progreso de la canción que está sonando. CantaSync solo usa esos datos para buscar y sincronizar la letra.")
                .setPositiveButton("Abrir ajustes",(d,w)->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                .setNegativeButton("Ahora no",null).show(); return;
        }
        refreshPlayer(true);
    }
    private boolean hasNotificationAccess() {
        String enabled=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");
        return enabled!=null && enabled.contains(getPackageName()+"/");
    }
    @Override protected void onResume() {
        super.onResume();
        long queuedDownload = getSharedPreferences("cantasync_settings", MODE_PRIVATE).getLong("update_download_id", -1);
        if (queuedDownload >= 0) finishUpdateDownload(queuedDownload);
        if (hasNotificationAccess()) refreshPlayer(false);
    }

    @Override protected void onDestroy() {
        if (updateReceiverRegistered) { try { unregisterReceiver(updateDownloadReceiver); } catch (IllegalArgumentException ignored) { } updateReceiverRegistered = false; }
        super.onDestroy();
    }
    private void refreshPlayer(boolean showMessage) {
        try {
            MediaSessionManager manager=(MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE);
            ArrayList<MediaController> sessions=new ArrayList<>(manager.getActiveSessions(new ComponentName(this,PlaybackNotificationService.class)));
            activePlayer=null;
            for (MediaController c:sessions) {
                if (c.getMetadata()!=null && c.getMetadata().getString(android.media.MediaMetadata.METADATA_KEY_TITLE)!=null) { activePlayer=c; break; }
            }
            if (activePlayer==null) { if(showMessage) status.setText("No encontramos un reproductor activo. Reproduce una canción e inténtalo otra vez."); return; }
            String title=activePlayer.getMetadata().getString(android.media.MediaMetadata.METADATA_KEY_TITLE);
            String artist=activePlayer.getMetadata().getString(android.media.MediaMetadata.METADATA_KEY_ARTIST);
            if(artist==null) artist=activePlayer.getMetadata().getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            titleInput.setText(title); artistInput.setText(artist==null?"":artist);
            selectedTrack = null;
            String album = activePlayer.getMetadata().getString(android.media.MediaMetadata.METADATA_KEY_ALBUM);
            Bitmap mediaArt = activePlayer.getMetadata().getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (mediaArt == null) mediaArt = activePlayer.getMetadata().getBitmap(android.media.MediaMetadata.METADATA_KEY_ART);
            if (mediaArt == null) mediaArt = activePlayer.getMetadata().getBitmap(android.media.MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            if (mediaArt != null) { activeArtworkTrack = null; showHeroArtwork(mediaArt); }
            else if (album != null && !album.isEmpty()) {
                activeArtworkTrack = new Track(new JSONObject()); activeArtworkTrack.artist=artist==null?"":artist;
                activeArtworkTrack.album=album; activeArtworkTrack.title=title; showHeroPlaceholder(); loadArtwork(activeArtworkTrack);
            } else { activeArtworkTrack=null; showHeroPlaceholder(); }
            status.setText("Detectada: " + title + (artist==null?"":" — "+artist) + ". Toca Buscar para elegir una letra sincronizada.");
            if(lyricDisplay!=null) updateCurrentLine();
        } catch (SecurityException e) { if(showMessage) status.setText("Android todavía no habilitó el acceso al reproductor. Vuelve a intentarlo."); }
    }
    private void updateCurrentLine() {
        if(activePlayer==null || lyricDisplay==null || lyricRows.isEmpty()) return;
        PlaybackState state=activePlayer.getPlaybackState(); if(state==null || state.getPosition()<0) return;
        long position=state.getPosition();
        if(state.getState()==PlaybackState.STATE_PLAYING) position+=(long)((SystemClock.elapsedRealtime()-state.getLastPositionUpdateTime())*state.getPlaybackSpeed());
        int current=-1; for(int i=0;i<lyricRows.size();i++) { if(lyricRows.get(i).startMs<=position) current=i; else break; }
        SpannableString frame=new SpannableString(lyricDisplay);
        if(current>=0) { LyricRow row=lyricRows.get(current); frame.setSpan(new BackgroundColorSpan(0xFFD9F7FA),row.start,row.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); frame.setSpan(new ForegroundColorSpan(purple),row.start,row.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); frame.setSpan(new StyleSpan(Typeface.BOLD),row.start,row.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); }
        lyrics.setText(frame);
        if(current>=0 && current!=highlightedRow) { highlightedRow=current; LyricRow row=lyricRows.get(current); lyrics.post(()->{ if(lyrics.getLayout()!=null) { int line=lyrics.getLayout().getLineForOffset(Math.min(row.start,lyrics.length())); int y=lyrics.getLayout().getLineTop(line)-dp(100); lyricScroll.smoothScrollTo(0,Math.max(0,y)); } }); }
    }
    private void copyLyrics() {
        String plain = selectedLrc.trim();
        ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Letra", plain));
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(this, "Letra copiada", Toast.LENGTH_SHORT).show();
    }

    private void ensureMusicFolder(int action) {
        String savedTree = getSharedPreferences("cantasync_settings", MODE_PRIVATE).getString("music_tree_uri", null);
        if (savedTree != null && hasPersistedWriteAccess(Uri.parse(savedTree))) { performMusicAction(action, Uri.parse(savedTree)); return; }
        if (savedTree != null) getSharedPreferences("cantasync_settings", MODE_PRIVATE).edit().remove("music_tree_uri").apply();
        pendingMusicAction = action;
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(picker, REQUEST_MUSIC_FOLDER); }
        catch (Exception e) { Toast.makeText(this, "No se pudo abrir el selector de carpetas", Toast.LENGTH_SHORT).show(); }
    }

    private boolean hasPersistedWriteAccess(Uri treeUri) {
        try {
            for (android.content.UriPermission permission : getContentResolver().getPersistedUriPermissions()) {
                if (treeUri.equals(permission.getUri()) && permission.isReadPermission() && permission.isWritePermission()) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private void performMusicAction(int action, Uri treeUri) {
        if (action == MUSIC_ACTION_SAVE) {
            final String lrc = selectedLrc, title = selectedTitle, artist = selectedArtist;
            status.setText("Guardando letra en la carpeta seleccionada…");
            executor.execute(() -> saveLrcInFolder(treeUri, artist + " - " + title, lrc));
        } else if (action == MUSIC_ACTION_APPLY) {
            final String lrc = selectedLrc, title = selectedTitle, artist = selectedArtist;
            status.setText("Buscando la canción dentro de Music…");
            executor.execute(() -> findAudioMatches(treeUri, title, artist, lrc));
        }
    }

    private Uri treeRoot(Uri treeUri) throws Exception {
        String rootId = DocumentsContract.getTreeDocumentId(treeUri);
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId);
    }

    private Uri findChild(Uri treeUri, Uri parentUri, String name) throws Exception {
        String parentId = DocumentsContract.getDocumentId(parentUri);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId);
        String[] projection = { DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME };
        try (Cursor cursor = getContentResolver().query(childrenUri, projection, null, null, null)) {
            if (cursor == null) return null;
            int idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            while (cursor.moveToNext()) {
                if (name.equalsIgnoreCase(cursor.getString(nameColumn))) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idColumn));
                }
            }
        }
        return null;
    }

    private Uri findOrCreateDirectory(Uri treeUri, Uri parentUri, String name) throws Exception {
        Uri existing = findChild(treeUri, parentUri, name);
        if (existing != null) return existing;
        return DocumentsContract.createDocument(getContentResolver(), parentUri,
            DocumentsContract.Document.MIME_TYPE_DIR, name);
    }

    private Uri findOrCreateTextFile(Uri treeUri, Uri parentUri, String name) throws Exception {
        Uri existing = null;
        try { existing = findChild(treeUri, parentUri, name); } catch (Exception ignored) { }
        if (existing != null) return existing;
        // text/plain makes Android's external-storage provider append .txt to .lrc.
        // An unregistered LRC MIME has no automatic extension, so the requested .lrc stays intact.
        return DocumentsContract.createDocument(getContentResolver(), parentUri, "application/x-lrc", name);
    }

    private OutputStream openLrcOutput(Uri target) throws Exception {
        Exception last = null;
        for (String mode : new String[] {"wt", "rwt"}) {
            try {
                OutputStream output = getContentResolver().openOutputStream(target, mode);
                if (output != null) return output;
                last = new Exception("El proveedor devolvió un archivo sin flujo de escritura (" + mode + ")");
            } catch (Exception e) { last = e; }
        }
        throw last == null ? new Exception("Android no pudo abrir el archivo") : last;
    }

    private void writeLrc(Uri treeUri, Uri parentUri, String name, String lrc, String successMessage) {
        try {
            Uri target = findOrCreateTextFile(treeUri, parentUri, name);
            if (target == null) throw new Exception("No se pudo crear el archivo .lrc");
            try (OutputStream out = openLrcOutput(target)) {
                out.write(lrc.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            runOnUiThread(() -> { status.setText(successMessage); Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show(); });
        } catch (Exception e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            runOnUiThread(() -> { status.setText("No se pudo guardar: " + reason); Toast.makeText(this, "No se pudo guardar la letra: " + reason, Toast.LENGTH_LONG).show(); });
        }
    }

    private String safeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private String selectedFolderPath(Uri treeUri) {
        try {
            String documentId = DocumentsContract.getTreeDocumentId(treeUri);
            int separator = documentId.indexOf(':');
            return separator >= 0 ? documentId.substring(separator + 1) : documentId;
        } catch (Exception ignored) {
            return "carpeta seleccionada";
        }
    }

    private void saveLrcInFolder(Uri treeUri, String baseName, String lrc) {
        try {
            Uri root = treeRoot(treeUri);
            String fileName = safeFileName(baseName) + ".lrc";
            String folder = selectedFolderPath(treeUri);
            String path = (folder.isEmpty() ? "Music" : folder) + "/" + fileName;
            writeLrc(treeUri, root, fileName, lrc, "Guardado en " + path);
        } catch (Exception e) {
            String reason=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
            runOnUiThread(() -> { status.setText("No se pudo guardar: "+reason); Toast.makeText(this, "No se pudo guardar: "+reason, Toast.LENGTH_LONG).show(); });
        }
    }

    private void findAudioMatches(Uri treeUri, String title, String artist, String lrc) {
        try {
            ArrayList<AudioCandidate> matches = new ArrayList<>();
            collectAudioMatches(treeUri, treeRoot(treeUri), "", title, artist, matches, 0);
            if (matches.isEmpty() && !artist.trim().isEmpty()) {
                collectAudioMatches(treeUri, treeRoot(treeUri), "", title, "", matches, 0);
            }
            runOnUiThread(() -> chooseAudioMatch(treeUri, matches, lrc));
        } catch (Exception e) {
            String reason=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
            runOnUiThread(() -> { status.setText("No se pudo buscar en la carpeta: "+reason); Toast.makeText(this, "No se pudo buscar la canción: "+reason, Toast.LENGTH_LONG).show(); });
        }
    }

    private void collectAudioMatches(Uri treeUri, Uri folderUri, String relativePath, String title,
                                     String artist, ArrayList<AudioCandidate> matches, int depth) throws Exception {
        if (depth > 20) return;
        String folderId = DocumentsContract.getDocumentId(folderUri);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderId);
        String[] projection = { DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE };
        try (Cursor cursor = getContentResolver().query(childrenUri, projection, null, null, null)) {
            if (cursor == null) return;
            int idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);
            while (cursor.moveToNext()) {
                String id = cursor.getString(idColumn), name = cursor.getString(nameColumn);
                String mime = cursor.getString(mimeColumn);
                Uri childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id);
                String childPath = relativePath.isEmpty() ? name : relativePath + "/" + name;
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    collectAudioMatches(treeUri, childUri, childPath, title, artist, matches, depth + 1);
                } else if (isAudioFile(name) && audioNameMatches(name, childPath, title, artist)) {
                    matches.add(new AudioCandidate(folderUri, name, childPath));
                }
            }
        }
    }

    private boolean isAudioFile(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".mp3") || lower.endsWith(".flac") || lower.endsWith(".wav");
    }

    private boolean audioNameMatches(String fileName, String relativePath, String title, String artist) {
        int extension = fileName.lastIndexOf('.');
        String base = extension > 0 ? fileName.substring(0, extension) : fileName;
        String normalizedBase = normalize(base), normalizedPath = normalize(relativePath);
        String normalizedTitle = normalize(title), normalizedArtist = normalize(artist);
        if (normalizedTitle.isEmpty() || !normalizedBase.contains(normalizedTitle)) return false;
        return normalizedArtist.isEmpty() || normalizedPath.contains(normalizedArtist);
    }

    private void chooseAudioMatch(Uri treeUri, ArrayList<AudioCandidate> matches, String lrc) {
        if (matches.isEmpty()) {
            status.setText("No encontramos un archivo de audio con ese título y artista en Music.");
            Toast.makeText(this, "No se encontró el archivo de la canción", Toast.LENGTH_SHORT).show(); return;
        }
        if (matches.size() == 1) { applyLrcToAudio(treeUri, matches.get(0), lrc); return; }
        String[] choices = new String[matches.size()];
        for (int i = 0; i < matches.size(); i++) choices[i] = matches.get(i).displayPath;
        dialogBuilder().setTitle("Elige el archivo de la canción")
            .setItems(choices, (dialog, which) -> applyLrcToAudio(treeUri, matches.get(which), lrc))
            .setNegativeButton("Cancelar", null).show();
    }

    private void applyLrcToAudio(Uri treeUri, AudioCandidate audio, String lrc) {
        int extension = audio.fileName.lastIndexOf('.');
        String base = extension > 0 ? audio.fileName.substring(0, extension) : audio.fileName;
        status.setText("Aplicando letra junto a la canción…");
        String lrcName = base + ".lrc";
        String path = audio.displayPath.contains("/")
            ? audio.displayPath.substring(0, audio.displayPath.lastIndexOf('/') + 1) + lrcName
            : lrcName;
        executor.execute(() -> writeLrc(treeUri, audio.parentUri, lrcName,
            lrc, "Letra aplicada a la canción actual: " + path));
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQUEST_INSTALL_SOURCES) {
            if (Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls()) askToInstallUpdate();
            else Toast.makeText(this, "Autoriza a CantaSync a instalar el APK para continuar", Toast.LENGTH_LONG).show();
        } else if (request == REQUEST_MUSIC_FOLDER && result == RESULT_OK && data != null && data.getData() != null) {
            Uri treeUri = data.getData();
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (flags == 0) flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            boolean persisted = false;
            try { getContentResolver().takePersistableUriPermission(treeUri, flags); persisted = hasPersistedWriteAccess(treeUri); } catch (SecurityException ignored) { }
            android.content.SharedPreferences.Editor preferences = getSharedPreferences("cantasync_settings", MODE_PRIVATE).edit();
            if (persisted) preferences.putString("music_tree_uri", treeUri.toString()); else preferences.remove("music_tree_uri");
            preferences.apply();
            performMusicAction(pendingMusicAction, treeUri);
        } else if (request == REQUEST_MUSIC_FOLDER && result != RESULT_OK) {
            Toast.makeText(this, "Selecciona la carpeta Music para guardar la letra", Toast.LENGTH_SHORT).show();
        }
    }
}

