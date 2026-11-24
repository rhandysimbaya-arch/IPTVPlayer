package com.rb.iptvplayer;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
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
    private static Channel startChannel;

    /** Opens the player. The dropdown lists every channel that is not known to be offline. */
    public static void start(Context c, List<Channel> all, Channel current) {
        List<Channel> q = new ArrayList<>();
        for (Channel ch : all) if (ch.status != Channel.OFFLINE) q.add(ch);
        if (!q.contains(current)) q.add(0, current);
        queue = q;
        startChannel = current;
        c.startActivity(new Intent(c, PlayerActivity.class));
    }

    private ExoPlayer player;
    private PlayerView playerView;
    private AutoCompleteTextView searchBox;
    private ChannelDropdownAdapter dropdown;
    private TextView error;
    private View topBar;
    private Channel current;
    private boolean triedHls;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (queue.isEmpty() || startChannel == null) {
            finish();
            return;
        }
        setContentView(R.layout.activity_player);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        playerView = findViewById(R.id.playerView);
        searchBox = findViewById(R.id.searchBox);
        error = findViewById(R.id.error);
        topBar = findViewById(R.id.topBar);
        current = startChannel;

        findViewById(R.id.back).setOnClickListener(v -> finish());

        dropdown = new ChannelDropdownAdapter(queue);
        searchBox.setAdapter(dropdown);
        searchBox.setDropDownBackgroundResource(R.drawable.dropdown_bg);
        searchBox.setDropDownHeight(getResources().getDisplayMetrics().heightPixels / 2);
        searchBox.setOnItemClickListener((parent, view, pos, id) -> {
            Channel c = (Channel) parent.getItemAtPosition(pos);
            choose(c);
        });
        searchBox.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                Channel first = dropdown.first();
                if (first != null) choose(first);
                return true;
            }
            return false;
        });
        searchBox.setOnClickListener(v -> {
            if (!searchBox.isPopupShowing()) {
                dropdown.showAll();
                searchBox.showDropDown();
            }
        });
        findViewById(R.id.dropBtn).setOnClickListener(v -> {
            if (searchBox.isPopupShowing()) {
                searchBox.dismissDropDown();
            } else {
                searchBox.requestFocus();
                dropdown.showAll();
                searchBox.showDropDown();
            }
        });

        playerView.setControllerVisibilityListener(
                (PlayerView.ControllerVisibilityListener) visibility -> {
                    boolean keep = searchBox.hasFocus() || searchBox.isPopupShowing();
                    topBar.setVisibility(visibility == View.VISIBLE || keep ? View.VISIBLE : View.GONE);
                });

        WindowInsetsControllerCompat ic =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        ic.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        ic.hide(WindowInsetsCompat.Type.systemBars());
    }

    private void choose(Channel c) {
        searchBox.dismissDropDown();
        searchBox.clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchBox.getWindowToken(), 0);
        play(c, false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (current == null) return;
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
                    play(current, true);
                    return;
                }
                error.setText("Can't play this channel right now (" + e.getErrorCodeName()
                        + ").\nPick another one from the list at the top.");
                error.setVisibility(View.VISIBLE);
            }
        });
        play(current, false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            player.release();
            player = null;
        }
    }

    private void play(Channel c, boolean forceHls) {
        if (player == null) return;
        if (!forceHls) triedHls = false;
        current = c;
        searchBox.setText(c.name, false);
        searchBox.setSelection(0);
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
