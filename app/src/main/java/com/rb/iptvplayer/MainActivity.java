package com.rb.iptvplayer;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.net.HttpURLConnection;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements ChannelAdapter.Listener {

    private static final String ALL_GROUPS = "All groups";

    private static final String[][] PRESETS = {
            {"iptv-org: Philippines", "https://iptv-org.github.io/iptv/countries/ph.m3u"},
            {"iptv-org: News", "https://iptv-org.github.io/iptv/categories/news.m3u"},
            {"iptv-org: Movies", "https://iptv-org.github.io/iptv/categories/movies.m3u"},
            {"iptv-org: Kids", "https://iptv-org.github.io/iptv/categories/kids.m3u"},
            {"iptv-org: Sports", "https://iptv-org.github.io/iptv/categories/sports.m3u"},
            {"iptv-org: Music", "https://iptv-org.github.io/iptv/categories/music.m3u"},
            {"iptv-org: All channels (large)", "https://iptv-org.github.io/iptv/index.m3u"},
            {"Free-TV: curated free channels", "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8"},
    };

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Channel> shown = new ArrayList<>();

    private ChannelStore store;
    private ChannelAdapter adapter;
    private EditText search;
    private ToggleButton favToggle;
    private Spinner groupSpinner;
    private TextView status;
    private String selectedGroup = ALL_GROUPS;
    private boolean updatingSpinner = false;

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

        search = findViewById(R.id.search);
        favToggle = findViewById(R.id.favToggle);
        groupSpinner = findViewById(R.id.groupSpinner);
        status = findViewById(R.id.status);
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
        favToggle.setOnCheckedChangeListener((b, checked) -> refresh());
        groupSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (updatingSpinner) return;
                selectedGroup = (String) parent.getItemAtPosition(pos);
                refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        rebuildGroups();
        refresh();
        if (store.all.isEmpty()) {
            // First launch: fetch the Philippines list automatically.
            loadFromUrl(PRESETS[0][1]);
        }
    }

    // ---------- list / filter ----------

    private void rebuildGroups() {
        List<String> groups = new ArrayList<>();
        groups.add(ALL_GROUPS);
        groups.addAll(store.groups());
        if (!groups.contains(selectedGroup)) selectedGroup = ALL_GROUPS;
        updatingSpinner = true;
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, groups);
        groupSpinner.setAdapter(a);
        groupSpinner.setSelection(groups.indexOf(selectedGroup), false);
        groupSpinner.post(() -> updatingSpinner = false);
    }

    private void refresh() {
        String q = search.getText().toString().trim().toLowerCase();
        boolean favOnly = favToggle.isChecked();
        shown.clear();
        for (Channel c : store.all) {
            if (favOnly && !c.favorite) continue;
            if (!selectedGroup.equals(ALL_GROUPS) && !c.group.equals(selectedGroup)) continue;
            if (!q.isEmpty() && !c.name.toLowerCase().contains(q) && !c.group.toLowerCase().contains(q)) continue;
            shown.add(c);
        }
        adapter.notifyDataSetChanged();
        if (!store.all.isEmpty()) {
            status.setText(shown.size() + " shown · " + store.all.size() + " saved");
        }
    }

    @Override
    public void onClick(int position) {
        PlayerActivity.start(this, shown, position);
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
                    refresh();
                    break;
                case 2:
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("stream", c.url));
                    toast("URL copied");
                    break;
                case 3:
                    store.remove(c);
                    rebuildGroups();
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
        if (id == R.id.menu_url) {
            askUrl();
        } else if (id == R.id.menu_file) {
            openLauncher.launch(new String[]{"*/*"});
        } else if (id == R.id.menu_presets) {
            showPresets();
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
                        rebuildGroups();
                        refresh();
                        status.setText("No channels saved yet.");
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void showPresets() {
        String[] names = new String[PRESETS.length];
        for (int i = 0; i < PRESETS.length; i++) names[i] = PRESETS[i][0];
        new AlertDialog.Builder(this)
                .setTitle("Free playlist sources")
                .setItems(names, (d, which) -> loadFromUrl(PRESETS[which][1]))
                .show();
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
                    c.group = group.getText().toString().trim();
                    List<Channel> l = new ArrayList<>();
                    l.add(c);
                    int added = store.addAll(l);
                    rebuildGroups();
                    refresh();
                    toast(added > 0 ? "Channel saved" : "Already saved");
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

    // ---------- loading / saving ----------

    private void loadFromUrl(final String urlStr) {
        status.setText("Loading playlist…");
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
                    status.setText("Couldn't load playlist: " + e.getMessage());
                    refresh();
                });
            }
        });
    }

    private void loadFromUri(final Uri uri) {
        status.setText("Reading file…");
        bg.execute(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new Exception("Can't open file");
                importStream(in);
            } catch (final Exception e) {
                ui.post(() -> {
                    status.setText("Couldn't read file: " + e.getMessage());
                    refresh();
                });
            }
        });
    }

    /** Runs on the background thread. */
    private void importStream(InputStream in) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        final List<Channel> parsed = M3uParser.parse(r);
        ui.post(() -> {
            int added = store.addAll(parsed);
            rebuildGroups();
            refresh();
            if (parsed.isEmpty()) {
                status.setText("No channels found in that playlist.");
            } else {
                toast(added + " new channels saved (" + parsed.size() + " in playlist)");
            }
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
