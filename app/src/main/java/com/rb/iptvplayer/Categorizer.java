package com.rb.iptvplayer;

import java.util.Locale;

/** Maps the many category labels found in playlists onto a small, tidy set of groups. */
public class Categorizer {
    public static final String KIDS = "Kids", NEWS = "News", MOVIES = "Movies",
            ENT = "Entertainment", SPORTS = "Sports", MUSIC = "Music",
            KMUSIC = "Korean Music", PH = "Philippines", DOC = "Documentary", LIFE = "Lifestyle",
            EDU = "Education", GENERAL = "General", OTHER = "Other";

    public static final String[] ORDER = {KIDS, NEWS, MOVIES, ENT, SPORTS, MUSIC, KMUSIC, PH,
            DOC, LIFE, EDU, GENERAL, OTHER};

    public static int rank(String group) {
        for (int i = 0; i < ORDER.length; i++) if (ORDER[i].equals(group)) return i;
        return ORDER.length;
    }

    private static boolean has(String s, String... keys) {
        for (String k : keys) if (s.contains(k)) return true;
        return false;
    }

    public static String categorize(String rawGroup, String name) {
        String g = rawGroup == null ? "" : rawGroup.toLowerCase(Locale.ROOT);
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);

        // 1) the playlist's own category label
        if (has(g, "kids", "animation", "family")) return KIDS;
        if (has(g, "music")) return MUSIC;
        if (has(g, "sport")) return SPORTS;
        if (has(g, "news", "weather")) return NEWS;
        if (has(g, "movies")) return MOVIES;
        if (has(g, "documentary", "science")) return DOC;
        if (has(g, "lifestyle", "cooking", "travel", "outdoor", "auto", "shop", "culture", "relax")) return LIFE;
        if (has(g, "education")) return EDU;
        if (has(g, "entertainment", "comedy", "series", "classic")) return ENT;

        // 2) fall back to words in the channel name
        if (has(n, "kids", "cartoon", "nick", "disney", "junior", "boomerang", "baby", "toon")) return KIDS;
        if (has(n, "mtv", "music", "vevo", "hits")) return MUSIC;
        if (has(n, "sport", "espn", "nba", "nfl", "nhl", "mlb", "golf", "racing", "wrestling", "ufc", "fight")) return SPORTS;
        if (has(n, "news", "cnn", "cnbc", "bloomberg", "weather", "msnbc", "al jazeera", "france 24")) return NEWS;
        if (has(n, "movie", "cinema", "film")) return MOVIES;
        if (has(n, "documentary", "history", "discovery", "nat geo", "national geographic")) return DOC;
        if (has(n, "cook", "food", "travel", "garden")) return LIFE;

        if (has(g, "general")) return GENERAL;
        return OTHER;
    }
}
