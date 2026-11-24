package com.rb.iptvplayer;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class M3uParser {

    private static String attr(String line, String key) {
        Matcher m = Pattern.compile(key + "=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE).matcher(line);
        return m.find() ? m.group(1).trim() : "";
    }

    public static List<Channel> parse(BufferedReader r) throws IOException {
        List<Channel> out = new ArrayList<>();
        Channel cur = null;
        String ua = "";
        String ref = "";
        String line;
        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.charAt(0) == '﻿') line = line.substring(1).trim();
            if (line.startsWith("#EXTINF")) {
                cur = new Channel();
                int q = line.lastIndexOf('"');
                int c = line.indexOf(',', Math.max(q, 0));
                String name = c >= 0 ? line.substring(c + 1).trim() : "";
                if (name.isEmpty()) name = attr(line, "tvg-name");
                cur.name = name;
                cur.group = attr(line, "group-title");
                cur.logo = attr(line, "tvg-logo");
            } else if (line.startsWith("#EXTVLCOPT:")) {
                String o = line.substring(11);
                if (o.startsWith("http-user-agent=")) ua = o.substring(16);
                else if (o.startsWith("http-referrer=")) ref = o.substring(14);
            } else if (line.startsWith("#")) {
                continue;
            } else {
                String lower = line.toLowerCase();
                if (lower.startsWith("http://") || lower.startsWith("https://")) {
                    if (cur == null) {
                        cur = new Channel();
                        cur.name = line;
                    }
                    cur.url = line;
                    cur.userAgent = ua;
                    cur.referer = ref;
                    if (cur.name.isEmpty()) cur.name = line;
                    out.add(cur);
                }
                cur = null;
                ua = "";
                ref = "";
            }
        }
        return out;
    }
}
