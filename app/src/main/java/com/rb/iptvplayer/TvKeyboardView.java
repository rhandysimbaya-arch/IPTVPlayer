package com.rb.iptvplayer;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

/**
 * Android-TV style on-screen keyboard: a grid of big keys that works with touch and with a
 * D-pad / remote (every key is focusable).
 */
public class TvKeyboardView extends LinearLayout {

    public interface Listener {
        void onKey(String text);

        void onBackspace();

        void onClear();

        void onMic();

        void onDone();
    }

    private Listener listener;
    private final int keyHeight;
    private final int gap;

    public TvKeyboardView(Context c) {
        this(c, null);
    }

    public TvKeyboardView(Context c, AttributeSet a) {
        super(c, a);
        setOrientation(VERTICAL);
        setBackgroundResource(R.drawable.keyboard_panel_bg);
        float d = getResources().getDisplayMetrics().density;
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        keyHeight = (int) ((landscape ? 34 : 46) * d);
        gap = (int) (4 * d);
        int pad = (int) (8 * d);
        setPadding(pad, pad, pad, pad);
        build();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    private void build() {
        String[] rows = {"1234567890", "QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM"};
        for (int r = 0; r < rows.length; r++) {
            LinearLayout row = newRow();
            if (r == 2) row.setPadding((int) (14 * getResources().getDisplayMetrics().density), 0,
                    (int) (14 * getResources().getDisplayMetrics().density), 0);
            for (char ch : rows[r].toCharArray()) {
                final String s = String.valueOf(ch);
                row.addView(textKey(s, 1f, false, v -> {
                    if (listener != null) listener.onKey(s);
                }));
            }
            if (r == 3) {
                row.addView(textKey("-", 1f, false, v -> {
                    if (listener != null) listener.onKey("-");
                }));
                row.addView(iconKey(R.drawable.ic_backspace, 1.6f, false, v -> {
                    if (listener != null) listener.onBackspace();
                }));
            }
            addView(row);
        }
        LinearLayout bottom = newRow();
        bottom.addView(iconKey(R.drawable.ic_mic, 1.4f, false, v -> {
            if (listener != null) listener.onMic();
        }));
        bottom.addView(textKey("CLEAR", 1.6f, false, v -> {
            if (listener != null) listener.onClear();
        }));
        bottom.addView(iconKey(R.drawable.ic_space_bar, 3.6f, false, v -> {
            if (listener != null) listener.onKey(" ");
        }));
        bottom.addView(textKey("SEARCH", 2.0f, true, v -> {
            if (listener != null) listener.onDone();
        }));
        addView(bottom);
    }

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, keyHeight + gap * 2);
        row.setLayoutParams(lp);
        return row;
    }

    private FrameLayout baseKey(float weight, boolean accent, OnClickListener click) {
        FrameLayout k = new FrameLayout(getContext());
        LayoutParams lp = new LayoutParams(0, keyHeight, weight);
        lp.setMargins(gap, gap, gap, gap);
        k.setLayoutParams(lp);
        k.setBackgroundResource(accent ? R.drawable.key_bg_accent : R.drawable.key_bg);
        k.setClickable(true);
        k.setFocusable(true);
        k.setOnClickListener(click);
        return k;
    }

    private View textKey(String label, float weight, boolean accent, OnClickListener click) {
        FrameLayout k = baseKey(weight, accent, click);
        TextView t = new TextView(getContext());
        t.setText(label);
        t.setTextColor(0xFFFFFFFF);
        t.setGravity(Gravity.CENTER);
        boolean word = label.length() > 1;
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, word ? 12 : 17);
        Typeface tf = ResourcesCompat.getFont(getContext(),
                word || accent ? R.font.poppins_semibold : R.font.poppins_medium);
        if (tf != null) t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        k.addView(t, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        k.setContentDescription(label);
        return k;
    }

    private View iconKey(int icon, float weight, boolean accent, OnClickListener click) {
        FrameLayout k = baseKey(weight, accent, click);
        ImageView iv = new ImageView(getContext());
        iv.setImageResource(icon);
        iv.setColorFilter(0xFFFFFFFF);
        int s = (int) (22 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(s, s, Gravity.CENTER);
        k.addView(iv, lp);
        return k;
    }
}
