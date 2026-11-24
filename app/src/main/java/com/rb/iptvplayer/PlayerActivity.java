package com.rb.iptvplayer;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerActivity extends AppCompatActivity {

    private static List<Channel> queue = new ArrayList<>();
    private static int startIndex = 0;

    public static void start(Context c, List<Channel> list, int position) {
        queue = new ArrayList<>(list);
        startIndex = position;
        c.startActivity(new Intent(c, PlayerActivity.class));
    }

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView title, error;
    private View topBar;
    private int index;
    private boolean triedHls;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        playerView = findViewById(R.id.playerView);
        title = findViewById(R.id.title);
        error = findViewById(R.id.error);
        topBar = findViewById(R.id.topBar);
        index = savedInstanceState != null ? savedInstanceState.getInt("index") : startIndex;

        findViewById(R.id.prev).setOnClickListener(v -> play(index - 1, false));
        findViewById(R.id.next).setOnClickListener(v -> play(index + 1, false));
        playerView.setControllerVisibilityListener(
                (PlayerView.ControllerVisibilityListener) visibility ->
                        topBar.setVisibility(visibility == View.VISIBLE ? View.VISIBLE : View.GONE));

        WindowInsetsControllerCompat ic =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        ic.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        ic.hide(WindowInsetsCompat.Type.systemBars());
    }

    @Override
    protected void onStart() {
        super.onStart();
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) error.setVisibility(View.GONE);
            }

            @Override
            public void onPlayerError(PlaybackException e) {
                boolean formatProblem =
                        e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                                || e.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED
                                || e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED;
                if (!triedHls && formatProblem) {
                    triedHls = true;
                    play(index, true);
                    return;
                }
                error.setText("Can't play this channel (" + e.getErrorCodeName()
                        + ").\nIt may be offline or blocked in your region. Try Next.");
                error.setVisibility(View.VISIBLE);
            }
        });
        play(index, false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("index", index);
    }

    private void play(int i, boolean forceHls) {
        if (queue.isEmpty()) {
            finish();
            return;
        }
        if (!forceHls) triedHls = false;
        index = ((i % queue.size()) + queue.size()) % queue.size();
        Channel c = queue.get(index);
        title.setText(c.name);
        error.setVisibility(View.GONE);

        DefaultHttpDataSource.Factory f = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
                .setUserAgent(c.userAgent.isEmpty() ? "Mozilla/5.0 (Linux; Android 13) RBIPTV" : c.userAgent);
        if (!c.referer.isEmpty()) {
            Map<String, String> h = new HashMap<>();
            h.put("Referer", c.referer);
            f.setDefaultRequestProperties(h);
        }
        MediaItem.Builder mi = new MediaItem.Builder().setUri(c.url);
        if (forceHls) mi.setMimeType(MimeTypes.APPLICATION_M3U8);
        player.setMediaSource(new DefaultMediaSourceFactory(f).createMediaSource(mi.build()));
        player.prepare();
        player.setPlayWhenReady(true);
    }
}
