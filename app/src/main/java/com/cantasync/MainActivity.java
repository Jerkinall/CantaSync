package com.cantasync;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private EditText titleInput, artistInput;
    private TextView status, selectedInfo, lyrics;
    private ScrollView rootScroll;
    private Button saveButton;
    private MediaController activePlayer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<LyricRow> lyricRows = new ArrayList<>();
    private SpannableString lyricDisplay;
    private int highlightedRow = -2;
    private String selectedLrc = "";
    private String selectedTitle = "";
    private String selectedArtist = "";
    private final ArrayList<Track> results = new ArrayList<>();
    private final int ink = 0xFF17312C, green = 0xFF176B5B, muted = 0xFF61716C;

    private static class Track {
        String title, artist, album, lrc;
        Track(JSONObject o) {
            title = o.optString("trackName", ""); artist = o.optString("artistName", "");
            album = o.optString("albumName", ""); lrc = o.optString("syncedLyrics", "");
        }
    }
    private static class LyricRow {
        long startMs; int start, end;
        LyricRow(long time, int from, int to) { startMs=time; start=from; end=to; }
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(0xFFF5F7F5); getWindow().setNavigationBarColor(0xFFF5F7F5);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        buildUi();
    }

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t;
    }
    private void buildUi() {
        ScrollView scroll = new ScrollView(this); rootScroll=scroll; scroll.setFillViewport(true); scroll.setBackgroundColor(0xFFF5F7F5);
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(22), dp(24), dp(22), dp(28));
        scroll.addView(page); setContentView(scroll);

        TextView brand = text("CantaSync", 29, ink); brand.setTypeface(null, Typeface.BOLD); page.addView(brand);
        TextView intro = text("Encuentra letras sincronizadas para cantar siguiendo el ritmo.", 15, muted);
        LinearLayout.LayoutParams introLp = new LinearLayout.LayoutParams(-1, -2); introLp.topMargin = dp(5); introLp.bottomMargin = dp(22); page.addView(intro, introLp);

        page.addView(label("CANCIÓN")); titleInput = input("Título de la canción"); page.addView(titleInput, fieldLp());
        page.addView(label("ARTISTA")); artistInput = input("Nombre del artista"); page.addView(artistInput, fieldLp());
        Button search = new Button(this); search.setText("Buscar letras sincronizadas"); search.setAllCaps(false); search.setTextColor(0xFFFFFFFF); search.setBackgroundTintList(android.content.res.ColorStateList.valueOf(green));
        page.addView(search, fieldLp()); search.setOnClickListener(v -> search());
        Button detect = new Button(this); detect.setText("Usar canción que está sonando"); detect.setAllCaps(false); page.addView(detect, fieldLp());
        detect.setOnClickListener(v -> detectPlayingTrack());
        status = text("Solo mostramos resultados con marcas de tiempo.", 13, muted);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2); statusLp.topMargin = dp(10); statusLp.bottomMargin = dp(12); page.addView(status, statusLp);

        selectedInfo = text("", 16, ink); selectedInfo.setTypeface(null, Typeface.BOLD); selectedInfo.setVisibility(View.GONE);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(-1, -2); infoLp.topMargin = dp(12); infoLp.bottomMargin = dp(8); page.addView(selectedInfo, infoLp);
        lyrics = text("", 17, ink); lyrics.setLineSpacing(dp(7), 1f); lyrics.setGravity(Gravity.CENTER_HORIZONTAL); lyrics.setVisibility(View.GONE);
        LinearLayout.LayoutParams lyricLp = new LinearLayout.LayoutParams(-1, -2); lyricLp.topMargin = dp(8); page.addView(lyrics, lyricLp);
        saveButton = new Button(this); saveButton.setText("Guardar archivo .lrc"); saveButton.setAllCaps(false); saveButton.setVisibility(View.GONE);
        page.addView(saveButton, fieldLp()); saveButton.setOnClickListener(v -> createLrc());
    }
    private TextView label(String s) { TextView t = text(s, 12, muted); t.setTypeface(null, Typeface.BOLD); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(4); p.bottomMargin = dp(5); t.setLayoutParams(p); return t; }
    private EditText input(String hint) { EditText e = new EditText(this); e.setSingleLine(true); e.setTextSize(16); e.setHint(hint); e.setPadding(dp(14), dp(9), dp(14), dp(9)); e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFB9C8C2)); return e; }
    private LinearLayout.LayoutParams fieldLp() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(12); return p; }

    private void search() {
        String title = titleInput.getText().toString().trim(), artist = artistInput.getText().toString().trim();
        if (title.isEmpty()) { titleInput.setError("Escribe el título"); return; }
        lyricDisplay=null; lyricRows.clear(); highlightedRow=-2;
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(titleInput.getWindowToken(), 0);
        status.setText("Buscando coincidencias sincronizadas…"); results.clear(); saveButton.setVisibility(View.GONE); lyrics.setVisibility(View.GONE); selectedInfo.setVisibility(View.GONE);
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
        String[] choices = new String[found.size()];
        for (int i=0; i<found.size(); i++) { Track t=found.get(i); choices[i] = t.title + "\n" + t.artist + (t.album.isEmpty() ? "" : " · " + t.album); }
        new AlertDialog.Builder(this).setTitle("Elige una versión").setItems(choices, (d, which) -> select(found.get(which))).setNegativeButton("Cancelar", null).show();
    }
    private void select(Track t) {
        selectedLrc = t.lrc.trim() + "\n"; selectedTitle = t.title; selectedArtist = t.artist;
        selectedInfo.setText(t.title + "\n" + t.artist + (t.album.isEmpty() ? "" : " · " + t.album)); selectedInfo.setVisibility(View.VISIBLE);
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
        lyricDisplay=new SpannableString(view.toString().trim()); highlightedRow=-2; lyrics.setText(lyricDisplay); lyrics.setVisibility(View.VISIBLE); saveButton.setVisibility(View.VISIBLE);
        status.setText(activePlayer == null ? "Letra sincronizada lista. Conecta el reproductor para resaltar la línea actual." : "Letra sincronizada lista; siguiendo la reproducción.");
        handler.removeCallbacks(syncTick); handler.post(syncTick);
    }
    private final Runnable syncTick = new Runnable() { @Override public void run() { updateCurrentLine(); handler.postDelayed(this, 350); } };
    private void detectPlayingTrack() {
        if (!hasNotificationAccess()) {
            new AlertDialog.Builder(this).setTitle("Permite detectar el reproductor")
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
    @Override protected void onResume() { super.onResume(); if (hasNotificationAccess()) refreshPlayer(false); }
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
        if(current>=0) { LyricRow row=lyricRows.get(current); frame.setSpan(new BackgroundColorSpan(0xFFDCEFE8),row.start,row.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); frame.setSpan(new ForegroundColorSpan(green),row.start,row.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); frame.setSpan(new StyleSpan(Typeface.BOLD),row.start,row.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); }
        lyrics.setText(frame);
        if(current>=0 && current!=highlightedRow) { highlightedRow=current; LyricRow row=lyricRows.get(current); lyrics.post(()->{ if(lyrics.getLayout()!=null) { int line=lyrics.getLayout().getLineForOffset(Math.min(row.start,lyrics.length())); int y=lyrics.getTop()+lyrics.getLayout().getLineTop(line)-dp(120); rootScroll.smoothScrollTo(0,Math.max(0,y)); } }); }
    }
    private void createLrc() {
        String base = (selectedArtist + " - " + selectedTitle).replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TITLE, base + ".lrc"); startActivityForResult(i, 41);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 41 && result == RESULT_OK && data != null && data.getData() != null) {
            Uri uri=data.getData(); try (OutputStream out=getContentResolver().openOutputStream(uri)) { if (out == null) throw new Exception("No se pudo abrir el archivo"); out.write(selectedLrc.getBytes(StandardCharsets.UTF_8)); Toast.makeText(this, "Archivo .lrc guardado", Toast.LENGTH_LONG).show(); }
            catch (Exception e) { Toast.makeText(this, "No se pudo guardar el archivo", Toast.LENGTH_LONG).show(); }
        }
    }
}
