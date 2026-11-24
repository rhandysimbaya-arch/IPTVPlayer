package com.rb.iptvplayer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Decides whether a stream URL is really serving video right now. */
public class Checker {
    private static final String UA = "Mozilla/5.0 (Linux; Android 13) RBIPTV";

    public static boolean isOnline(Channel c) {
        try {
            return probe(c.url, c.userAgent, c.referer, 0);
        } catch (Throwable t) {
            return false;
        }
    }

    private static HttpURLConnection open(String url, String ua, String ref, boolean range) throws IOException {
        String u = url;
        for (int i = 0; i < 5; i++) {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(8000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", ua == null || ua.isEmpty() ? UA : ua);
            if (ref != null && !ref.isEmpty()) c.setRequestProperty("Referer", ref);
            if (range) c.setRequestProperty("Range", "bytes=0-2047");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) return null;
                u = new URL(new URL(u), loc).toString();
                continue;
            }
            if (code == 200 || code == 206) return c;
            c.disconnect();
            return null;
        }
        return null;
    }

    private static byte[] read(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int total = 0;
        while (total < max) {
            int n = in.read(buf, 0, Math.min(buf.length, max - total));
            if (n < 0) break;
            out.write(buf, 0, n);
            total += n;
        }
        return out.toByteArray();
    }

    private static boolean probe(String url, String ua, String ref, int depth) throws IOException {
        if (depth > 2) return false;
        HttpURLConnection c = open(url, ua, ref, false);
        if (c == null) return false;
        try {
            String ct = c.getContentType();
            ct = ct == null ? "" : ct.toLowerCase();
            InputStream in = c.getInputStream();
            byte[] head = read(in, 2048);
            if (head.length == 0) return false;
            String start = new String(head, 0, Math.min(head.length, 16), StandardCharsets.ISO_8859_1).trim();
            if (start.startsWith("﻿")) start = start.substring(1);
            if (start.startsWith("#EXTM3U")) {
                // HLS playlist: make sure its first stream/segment actually responds
                ByteArrayOutputStream all = new ByteArrayOutputStream();
                all.write(head);
                all.write(read(in, 65536));
                String text = new String(all.toByteArray(), StandardCharsets.UTF_8);
                String next = null;
                for (String line : text.split("\n")) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        next = line;
                        break;
                    }
                }
                if (next == null) return false;
                String resolved = new URL(c.getURL(), next).toString();
                if (text.contains("#EXT-X-STREAM-INF")) return probe(resolved, ua, ref, depth + 1);
                return segmentWorks(resolved, ua, ref);
            }
            if (start.startsWith("<")) {
                // DASH manifest (.mpd) counts as working; any other markup is an error page
                String text = new String(head, StandardCharsets.UTF_8);
                return ct.contains("dash") || text.contains("<MPD");
            }
            if (ct.contains("text/html") || ct.contains("json")) return false;
            return true;
        } finally {
            c.disconnect();
        }
    }

    private static boolean segmentWorks(String url, String ua, String ref) throws IOException {
        HttpURLConnection c = open(url, ua, ref, true);
        if (c == null) return false;
        try {
            String ct = c.getContentType();
            if (ct != null && ct.toLowerCase().contains("text/html")) return false;
            return read(c.getInputStream(), 1024).length > 0;
        } finally {
            c.disconnect();
        }
    }
}
