package com.rb.iptvplayer;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Saved channels, kept in a JSON file in the app's private storage. Use from the main thread. */
public class ChannelStore {
    private static ChannelStore instance;

    public static synchronized ChannelStore get(Context c) {
        if (instance == null) instance = new ChannelStore(c.getApplicationContext());
        return instance;
    }

    private final File file;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    public final List<Channel> all = new ArrayList<>();

    private ChannelStore(Context c) {
        file = new File(c.getFilesDir(), "channels.json");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        try (FileInputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            JSONArray arr = new JSONArray(new String(bo.toByteArray(), StandardCharsets.UTF_8));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Channel ch = new Channel();
                ch.name = o.optString("name");
                ch.url = o.optString("url");
                ch.group = o.optString("group");
                ch.logo = o.optString("logo");
                ch.userAgent = o.optString("ua");
                ch.referer = o.optString("ref");
                ch.favorite = o.optBoolean("fav");
                if (!ch.url.isEmpty()) all.add(ch);
            }
        } catch (Exception ignored) {
        }
    }

    public void save() {
        final List<Channel> snapshot = new ArrayList<>(all);
        io.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                for (Channel ch : snapshot) {
                    JSONObject o = new JSONObject();
                    o.put("name", ch.name);
                    o.put("url", ch.url);
                    o.put("group", ch.group);
                    o.put("logo", ch.logo);
                    o.put("ua", ch.userAgent);
                    o.put("ref", ch.referer);
                    o.put("fav", ch.favorite);
                    arr.put(o);
                }
                File tmp = new File(file.getPath() + ".tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
                }
                if (!tmp.renameTo(file)) {
                    file.delete();
                    tmp.renameTo(file);
                }
            } catch (Exception ignored) {
            }
        });
    }

    /** Adds channels whose URL is not saved yet. Returns how many were new. */
    public int addAll(List<Channel> incoming) {
        Set<String> have = new HashSet<>();
        for (Channel c : all) have.add(c.url);
        int added = 0;
        for (Channel c : incoming) {
            if (have.add(c.url)) {
                all.add(c);
                added++;
            }
        }
        if (added > 0) save();
        return added;
    }

    public void remove(Channel c) {
        all.remove(c);
        save();
    }

    public void clear() {
        all.clear();
        save();
    }

    public void toggleFavorite(Channel c) {
        c.favorite = !c.favorite;
        save();
    }

    public List<String> groups() {
        Set<String> set = new java.util.TreeSet<>();
        for (Channel c : all) if (!c.group.isEmpty()) set.add(c.group);
        return new ArrayList<>(set);
    }

    public String toM3u() {
        StringBuilder sb = new StringBuilder("#EXTM3U\n");
        for (Channel c : all) {
            sb.append("#EXTINF:-1");
            if (!c.logo.isEmpty()) sb.append(" tvg-logo=\"").append(c.logo).append('"');
            if (!c.group.isEmpty()) sb.append(" group-title=\"").append(c.group).append('"');
            sb.append(',').append(c.name).append('\n');
            if (!c.userAgent.isEmpty()) sb.append("#EXTVLCOPT:http-user-agent=").append(c.userAgent).append('\n');
            if (!c.referer.isEmpty()) sb.append("#EXTVLCOPT:http-referrer=").append(c.referer).append('\n');
            sb.append(c.url).append('\n');
        }
        return sb.toString();
    }
}
