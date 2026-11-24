package com.rb.iptvplayer;

public class Channel {
    public static final int UNKNOWN = 0, ONLINE = 1, OFFLINE = 2;

    public String name = "";
    public String url = "";
    public String group = "";
    public String logo = "";
    public String userAgent = "";
    public String referer = "";
    public boolean favorite = false;
    /** Added or imported by the user; never removed by the automatic cleanup. */
    public boolean custom = false;
    public volatile int status = UNKNOWN;
}
