package com.rb.iptvplayer;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity
        implements ChannelAdapter.Listener, Updater.Listener {

    private static final String KEY_ALL = "@all";
    private static final String KEY_FAV = "@fav";

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Channel> shown = new ArrayList<>();

    private ChannelStore store;
    private ChannelAdapter adapter;
    private EditText search;
    private ChipGroup chipGroup;
    private View progressBox;
    private LinearProgressIndicator progress;
    private TextView progressText, empty;
    private String selected = KEY_ALL;
    private boolean rebuildingChips = false;
    private boolean indeterminate = false;
    private String lastPhase = "";

    private final ActivityResultLauncher<String[]> openLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) loadFromUri(uri);
            });

    private final ActivityResultLauncher<String> exportLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("audio/x-mpegurl"), uri -> {
                if (uri != null) exportTo(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        store = ChannelStore.get(this);

        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        search = findViewById(R.id.search);
        chipGroup = findViewById(R.id.chipGroup);
        progressBox = findViewById(R.id.progressBox);
        progress = findViewById(R.id.progress);
        progressText = findViewById(R.id.progressText);
        empty = findViewById(R.id.empty);

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChannelAdapter(shown, this);
        list.setAdapter(adapter);

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                refresh();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        chipGroup.setOnCheckedStateChangeListener((group, ids) -> {
            if (rebuildingChips || ids.isEmpty()) return;
            Chip c = group.findViewById(ids.get(0));
            if (c != null) {
                selected = (String) c.getTag();
                refresh();
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Exit RB IPTV Player?")
                        .setMessage("Do you want to close the app?")
                        .setPositiveButton("Exit", (d, w) -> finishAffinity())
                        .setNegativeButton("Stay", null)
                        .show();
            }
        });

        rebuildChips();
        refresh();

        if (savedInstanceState == null) {
            boolean auto = getSharedPreferences("prefs", MODE_PRIVATE).getBoolean("auto_update", true);
            if (auto || !store.all.isEmpty()) Updater.get(this).start(auto, auto, null);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Updater u = Updater.get(this);
        u.setListener(this);
        if (!u.isRunning()) {
            progressBox.setVisibility(View.GONE);
            rebuildChips();
            refresh();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Updater.get(this).setListener(null);
    }

    // ---------- update progress ----------

    @Override
    public void onProgress(String phase, int done, int total, int online) {
        progressBox.setVisibility(View.VISIBLE);
        boolean checking = phase.startsWith("Checking");
        if (!phase.equals(lastPhase)) {
            lastPhase = phase;
            rebuildChips();
            refresh();
        }
        if (checking && total > 0) {
            if (indeterminate) {
                progress.setIndeterminate(false);
                indeterminate = false;
            }
            progress.setMax(total);
            progress.setProgressCompat(done, true);
            progressText.setText("Checking channels " + done + "/" + total + " · " + online + " working");
        } else {
            if (!indeterminate) {
                progress.setIndeterminate(true);
                indeterminate = true;
            }
            progressText.setText(phase);
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onDone(String summary) {
        lastPhase = "";
        progressBox.setVisibility(View.GONE);
        rebuildChips();
        refresh();
        toast(summary);
    }

    private void showBusy(String text) {
        progressBox.setVisibility(View.VISIBLE);
        if (!indeterminate) {
            progress.setIndeterminate(true);
            indeterminate = true;
        }
        progressText.setText(text);
    }

    private void hideBusy() {
        if (!Updater.get(this).isRunning()) progressBox.setVisibility(View.GONE);
    }

    // ---------- list / filter ----------

    private void rebuildChips() {
        Map<String, Integer> counts = new HashMap<>();
        int fav = 0;
        for (Channel c : store.all) {
            Integer n = counts.get(c.group);
            counts.put(c.group, n == null ? 1 : n + 1);
            if (c.favorite) fav++;
        }
        if (!selected.equals(KEY_ALL) && !selected.equals(KEY_FAV) && !counts.containsKey(selected)) {
            selected = KEY_ALL;
        }
        rebuildingChips = true;
        chipGroup.removeAllViews();
        addChip("All (" + store.all.size() + ")", KEY_ALL);
        addChip("★ Favorites (" + fav + ")", KEY_FAV);
        for (String g : store.groups()) addChip(g + " (" + counts.get(g) + ")", g);
        rebuildingChips = false;
    }

    private void addChip(String label, String key) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.chip_filter, chipGroup, false);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setTag(key);
        chipGroup.addView(chip);
        chip.setChecked(key.equals(selected));
    }

    private void refresh() {
        String q = search.getText().toString().trim().toLowerCase();
        shown.clear();
        for (Channel c : store.all) {
            if (selected.equals(KEY_FAV)) {
                if (!c.favorite) continue;
            } else if (!selected.equals(KEY_ALL) && !c.group.equals(selected)) {
                continue;
            }
            if (!q.isEmpty() && !c.name.toLowerCase().contains(q) && !c.group.toLowerCase().contains(q)) continue;
            shown.add(c);
        }
        adapter.notifyDataSetChanged();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(shown.size() + " channels");
        }
        if (shown.isEmpty()) {
            if (Updater.get(this).isRunning()) {
                empty.setText("Downloading and testing channels…\nThis can take a few minutes.");
            } else if (store.all.isEmpty()) {
                empty.setText("No channels yet.\nTap the refresh icon to download the latest channels.");
            } else {
                empty.setText("No channels match.");
            }
            empty.setVisibility(View.VISIBLE);
        } else {
            empty.setVisibility(View.GONE);
        }
    }

    @Override
    public void onClick(int position) {
        PlayerActivity.start(this, store.all, shown.get(position));
    }

    @Override
    public void onFavorite(int position) {
        store.toggleFavorite(shown.get(position));
        rebuildChips();
        refresh();
    }

    @Override
    public void onLongClick(View anchor, int position) {
        final Channel c = shown.get(position);
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, c.favorite ? "Remove from favorites" : "Add to favorites");
        pm.getMenu().add(0, 2, 1, "Copy stream URL");
        pm.getMenu().add(0, 3, 2, "Delete");
        pm.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    store.toggleFavorite(c);
                    rebuildChips();
                    refresh();
                    break;
                case 2:
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("stream", c.url));
                    toast("URL copied");
                    break;
                case 3:
                    store.remove(c);
                    rebuildChips();
                    refresh();
                    break;
            }
            return true;
        });
        pm.show();
    }

    // ---------- menu ----------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_update) {
            if (Updater.get(this).start(true, true, null)) {
                toast("Updating channels…");
                refresh();
            } else {
                toast("An update is already running");
            }
        } else if (id == R.id.menu_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (id == R.id.menu_url) {
            askUrl();
        } else if (id == R.id.menu_file) {
            openLauncher.launch(new String[]{"*/*"});
        } else if (id == R.id.menu_add) {
            addOne();
        } else if (id == R.id.menu_export) {
            if (store.all.isEmpty()) toast("Nothing to export");
            else exportLauncher.launch("my_channels.m3u");
        } else if (id == R.id.menu_clear) {
            new AlertDialog.Builder(this)
                    .setTitle("Delete all channels?")
                    .setMessage("This removes all " + store.all.size() + " saved channels, including favorites.")
                    .setPositiveButton("Delete", (d, w) -> {
                        store.clear();
                        rebuildChips();
                        refresh();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void askUrl() {
        final SharedPreferences sp = getSharedPreferences("prefs", MODE_PRIVATE);
        final EditText et = new EditText(this);
        et.setHint("https://example.com/playlist.m3u");
        et.setText(sp.getString("last_url", ""));
        et.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("Load M3U from URL")
                .setView(pad(et))
                .setPositiveButton("Load", (d, w) -> {
                    String u = et.getText().toString().trim();
                    if (u.isEmpty()) return;
                    sp.edit().putString("last_url", u).apply();
                    loadFromUrl(u);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addOne() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final EditText name = new EditText(this);
        name.setHint("Channel name");
        final EditText url = new EditText(this);
        url.setHint("Stream URL (http/https)");
        final EditText group = new EditText(this);
        group.setHint("Group (optional)");
        box.addView(name);
        box.addView(url);
        box.addView(group);
        new AlertDialog.Builder(this)
                .setTitle("Add channel")
                .setView(pad(box))
                .setPositiveButton("Save", (d, w) -> {
                    String u = url.getText().toString().trim();
                    String n = name.getText().toString().trim();
                    if (!u.toLowerCase().startsWith("http")) {
                        toast("URL must start with http:// or https://");
                        return;
                    }
                    Channel c = new Channel();
                    c.url = u;
                    c.name = n.isEmpty() ? u : n;
                    String g = group.getText().toString().trim();
                    c.group = g.isEmpty() ? Categorizer.categorize("", c.name) : g;
                    c.custom = true;
                    List<Channel> l = new ArrayList<>();
                    l.add(c);
                    addAndCheck(l);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private View pad(View v) {
        LinearLayout wrap = new LinearLayout(this);
        int p = (int) (20 * getResources().getDisplayMetrics().density);
        wrap.setPadding(p, p / 2, p, 0);
        wrap.addView(v, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    private void addAndCheck(List<Channel> incoming) {
        List<Channel> added = store.addNew(incoming);
        rebuildChips();
        refresh();
        if (added.isEmpty()) {
            toast("Already saved");
        } else {
            toast(added.size() + " channels saved");
            Updater.get(this).start(false, false, added);
        }
    }

    // ---------- loading / saving ----------

    private void loadFromUrl(final String urlStr) {
        showBusy("Loading playlist…");
        bg.execute(() -> {
            try {
                String u = urlStr;
                HttpURLConnection conn = null;
                for (int i = 0; i < 6; i++) {
                    conn = (HttpURLConnection) new URL(u).openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(60000);
                    conn.setInstanceFollowRedirects(false);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) RBIPTV");
                    int code = conn.getResponseCode();
                    if (code >= 300 && code < 400) {
                        String loc = conn.getHeaderField("Location");
                        conn.disconnect();
                        if (loc == null) throw new Exception("Bad redirect");
                        u = new URL(new URL(u), loc).toString();
                        continue;
                    }
                    if (code != 200) throw new Exception("Server returned HTTP " + code);
                    break;
                }
                try (InputStream in = conn.getInputStream()) {
                    importStream(in);
                } finally {
                    conn.disconnect();
                }
            } catch (final Exception e) {
                ui.post(() -> {
                    hideBusy();
                    toast("Couldn't load playlist: " + e.getMessage());
                });
            }
        });
    }

    private void loadFromUri(final Uri uri) {
        showBusy("Reading file…");
        bg.execute(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new Exception("Can't open file");
                importStream(in);
            } catch (final Exception e) {
                ui.post(() -> {
                    hideBusy();
                    toast("Couldn't read file: " + e.getMessage());
                });
            }
        });
    }

    /** Runs on the background thread. */
    private void importStream(InputStream in) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        final List<Channel> parsed = M3uParser.parse(r);
        ui.post(() -> {
            hideBusy();
            if (parsed.isEmpty()) {
                toast("No channels found in that playlist.");
                return;
            }
            for (Channel c : parsed) {
                c.custom = true;
                c.group = Categorizer.categorize(c.group, c.name);
            }
            addAndCheck(parsed);
        });
    }

    private void exportTo(final Uri uri) {
        final String data = store.toM3u();
        bg.execute(() -> {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new Exception("Can't write file");
                out.write(data.getBytes(StandardCharsets.UTF_8));
                ui.post(() -> toast("Exported " + store.all.size() + " channels"));
            } catch (final Exception e) {
                ui.post(() -> toast("Export failed: " + e.getMessage()));
            }
        });
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
