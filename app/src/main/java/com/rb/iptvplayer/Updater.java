package com.rb.iptvplayer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Downloads the American and Western channel lists (plus Korean music channels), sorts them
 * into groups, tests every stream and removes the ones that do not work.
 */
public class Updater {

    public interface Listener {
        void onProgress(String phase, int done, int total, int online);

        void onDone(String summary);
    }

    private static final String BASE = "https://iptv-org.github.io/iptv/countries/";
    /** American and Western countries: US, Canada, UK, Ireland, Australia, NZ, Germany, France, Spain, Italy, Netherlands. */
    private static final String[] COUNTRIES = {"us", "ca", "gb", "ie", "au", "nz", "de", "fr", "es", "it", "nl"};
    private static final String PH_URL =
            "https://raw.githubusercontent.com/Harleythetech/IPHTV/refs/heads/main/ph.m3u";
    private static final Pattern KOREAN_MUSIC =
            Pattern.compile("mtv|mnet|k-?pop|music|뮤직|엠넷", Pattern.CASE_INSENSITIVE);

    private static Updater instance;

    public static synchronized Updater get(Context c) {
        if (instance == null) instance = new Updater(c.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private volatile Listener listener;
    private volatile String phase = "";
    private volatile int done, total, online;

    private Updater(Context c) {
        app = c;
    }

    public boolean isRunning() {
        return running;
    }

    public void setListener(Listener l) {
        listener = l;
        if (l != null && running) l.onProgress(phase, done, total, online);
    }

    /**
     * @param fetch      download fresh channel lists first
     * @param removeDead remove channels that fail the check (favorites and your own channels are kept)
     * @param subset     only check these channels, or null for all saved channels
     */
    public boolean start(final boolean fetch, final boolean removeDead, final List<Channel> subset) {
        synchronized (this) {
            if (running) return false;
            running = true;
        }
        phase = "Starting…";
        done = total = online = 0;
        new Thread(() -> run(fetch, removeDead, subset), "channel-updater").start();
        return true;
    }

    private <T> T onMain(Callable<T> c) throws Exception {
        FutureTask<T> t = new FutureTask<>(c);
        ui.post(t);
        return t.get();
    }

    private void progress(String p, int d, int t, int o) {
        phase = p;
        done = d;
        total = t;
        online = o;
        final Listener l = listener;
        if (l != null) ui.post(() -> l.onProgress(p, d, t, o));
    }

    private void finish(final String summary) {
        running = false;
        final Listener l = listener;
        if (l != null) ui.post(() -> l.onDone(summary));
    }

    private void run(boolean fetch, boolean removeDead, List<Channel> subset) {
        try {
            final ChannelStore store = ChannelStore.get(app);
            List<Channel> work;
            if (fetch) {
                progress("Downloading channel lists…", 0, COUNTRIES.length + 2, 0);
                List<Channel> existing = onMain(() -> new ArrayList<>(store.all));
                Map<String, Channel> byUrl = new LinkedHashMap<>();
                for (Channel c : existing) if (c.favorite || c.custom) byUrl.put(c.url, c);

                ExecutorService ex = Executors.newFixedThreadPool(4);
                List<Future<List<Channel>>> fs = new ArrayList<>();
                for (final String cc : COUNTRIES) fs.add(ex.submit(() -> download(BASE + cc + ".m3u", 0)));
                fs.add(ex.submit(() -> download(BASE + "kr.m3u", 1)));
                fs.add(ex.submit(() -> download(PH_URL, 2)));
                int ok = 0;
                for (int i = 0; i < fs.size(); i++) {
                    try {
                        for (Channel ch : fs.get(i).get()) if (!byUrl.containsKey(ch.url)) byUrl.put(ch.url, ch);
                        ok++;
                    } catch (Exception ignored) {
                    }
                    progress("Downloading channel lists…", i + 1, fs.size(), 0);
                }
                ex.shutdown();
                if (ok == 0) {
                    finish("Couldn't download the channel lists. Check your internet connection.");
                    return;
                }
                work = new ArrayList<>(byUrl.values());
                sort(work);
                final List<Channel> toStore = work;
                onMain(() -> {
                    store.replaceAll(toStore);
                    return null;
                });
            } else if (subset != null) {
                work = subset;
            } else {
                work = onMain(() -> new ArrayList<>(store.all));
            }

            check(work);

            int removed = 0;
            if (removeDead) {
                final List<Channel> checked = work;
                removed = onMain(() -> {
                    int r = 0;
                    Iterator<Channel> it = store.all.iterator();
                    while (it.hasNext()) {
                        Channel c = it.next();
                        if (c.status == Channel.OFFLINE && !c.favorite && !c.custom) {
                            it.remove();
                            r++;
                        }
                    }
                    store.save();
                    return r;
                });
            } else {
                onMain(() -> {
                    store.save();
                    return null;
                });
            }
            int working = 0;
            for (Channel c : work) if (c.status == Channel.ONLINE) working++;
            if (fetch) {
                SharedPreferences sp = app.getSharedPreferences("prefs", Context.MODE_PRIVATE);
                sp.edit().putLong("last_update", System.currentTimeMillis()).apply();
            }
            finish(working + " working channels" + (removed > 0 ? ", " + removed + " dead removed" : ""));
        } catch (Throwable t) {
            finish("Update failed: " + t.getMessage());
        }
    }

    private void check(List<Channel> work) throws InterruptedException {
        final int n = work.size();
        final AtomicInteger doneCount = new AtomicInteger();
        final AtomicInteger onlineCount = new AtomicInteger();
        ExecutorService ex = Executors.newFixedThreadPool(48);
        for (final Channel c : work) {
            ex.execute(() -> {
                boolean ok = Checker.isOnline(c);
                c.status = ok ? Channel.ONLINE : Channel.OFFLINE;
                if (ok) onlineCount.incrementAndGet();
                doneCount.incrementAndGet();
            });
        }
        ex.shutdown();
        progress("Checking channels…", 0, n, 0);
        while (!ex.awaitTermination(600, TimeUnit.MILLISECONDS)) {
            progress("Checking channels…", doneCount.get(), n, onlineCount.get());
        }
        progress("Checking channels…", n, n, onlineCount.get());
    }

    private static void sort(List<Channel> list) {
        Collections.sort(list, new Comparator<Channel>() {
            @Override
            public int compare(Channel a, Channel b) {
                int ra = Categorizer.rank(a.group), rb = Categorizer.rank(b.group);
                if (ra != rb) return ra - rb;
                return a.name.compareToIgnoreCase(b.name);
            }
        });
    }

    private List<Channel> download(String urlStr, int mode) throws Exception {
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
            if (code != 200) throw new Exception("HTTP " + code);
            break;
        }
        List<Channel> parsed;
        try (InputStream in = conn.getInputStream()) {
            parsed = M3uParser.parse(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } finally {
            conn.disconnect();
        }
        List<Channel> out = new ArrayList<>();
        for (Channel c : parsed) {
            String raw = c.group == null ? "" : c.group;
            if (raw.toLowerCase(Locale.ROOT).contains("xxx")) continue;
            if (mode == 1) {
                if (!KOREAN_MUSIC.matcher(c.name).find()) continue;
                c.group = Categorizer.KMUSIC;
            } else if (mode == 2) {
                c.group = Categorizer.PH;
            } else {
                c.group = Categorizer.categorize(raw, c.name);
            }
            out.add(c);
        }
        return out;
    }
}
