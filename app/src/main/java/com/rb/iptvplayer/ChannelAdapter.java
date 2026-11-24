package com.rb.iptvplayer;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.VH> {

    public interface Listener {
        void onClick(int position);

        void onLongClick(View anchor, int position);

        void onFavorite(int position);
    }

    private final List<Channel> items;
    private final Listener listener;

    public ChannelAdapter(List<Channel> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    static int iconFor(String g) {
        if (Categorizer.KIDS.equals(g)) return R.drawable.ic_kids;
        if (Categorizer.NEWS.equals(g)) return R.drawable.ic_news;
        if (Categorizer.MOVIES.equals(g)) return R.drawable.ic_movie;
        if (Categorizer.ENT.equals(g)) return R.drawable.ic_play_circle;
        if (Categorizer.SPORTS.equals(g)) return R.drawable.ic_sports;
        if (Categorizer.MUSIC.equals(g)) return R.drawable.ic_music;
        if (Categorizer.KMUSIC.equals(g)) return R.drawable.ic_headphones;
        if (Categorizer.PH.equals(g)) return R.drawable.ic_place;
        if (Categorizer.DOC.equals(g)) return R.drawable.ic_videocam;
        if (Categorizer.LIFE.equals(g)) return R.drawable.ic_favorite;
        if (Categorizer.EDU.equals(g)) return R.drawable.ic_school;
        if (Categorizer.GENERAL.equals(g)) return R.drawable.ic_apps;
        return R.drawable.ic_tv;
    }

    static int colorFor(String g) {
        if (Categorizer.KIDS.equals(g)) return 0xFF29B6F6;
        if (Categorizer.NEWS.equals(g)) return 0xFF2F80FF;
        if (Categorizer.MOVIES.equals(g)) return 0xFF5C6BC0;
        if (Categorizer.ENT.equals(g)) return 0xFF7E57C2;
        if (Categorizer.SPORTS.equals(g)) return 0xFF26A69A;
        if (Categorizer.MUSIC.equals(g)) return 0xFFAB47BC;
        if (Categorizer.KMUSIC.equals(g)) return 0xFFEC407A;
        if (Categorizer.PH.equals(g)) return 0xFFFFA726;
        if (Categorizer.DOC.equals(g)) return 0xFF42A5F5;
        if (Categorizer.LIFE.equals(g)) return 0xFF26C6DA;
        if (Categorizer.EDU.equals(g)) return 0xFF66BB6A;
        if (Categorizer.GENERAL.equals(g)) return 0xFF5C7CFA;
        return 0xFF78909C;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_channel, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Channel c = items.get(position);
        h.name.setText(c.name);
        h.group.setText(c.group.isEmpty() ? "Ungrouped" : c.group);

        int st = c.status;
        int color;
        if (st == Channel.ONLINE) {
            h.status.setText("● Online");
            color = 0xFF2ECC71;
        } else if (st == Channel.OFFLINE) {
            h.status.setText("● Offline");
            color = 0xFFFF5252;
        } else {
            boolean checking = Updater.get(h.itemView.getContext()).isRunning();
            h.status.setText(checking ? "● Checking…" : "● Not checked");
            color = 0xFF8FA6D0;
        }
        h.status.setTextColor(color);
        h.status.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 38)));

        h.fav.setImageResource(c.favorite ? R.drawable.ic_star : R.drawable.ic_star_border);

        h.badge.setImageResource(iconFor(c.group));
        h.badge.setBackgroundTintList(ColorStateList.valueOf(colorFor(c.group)));

        h.itemView.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION) listener.onClick(p);
        });
        h.itemView.setOnLongClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION) listener.onLongClick(v, p);
            return true;
        });
        h.fav.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION) listener.onFavorite(p);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name, group, status;
        final ImageView badge;
        final ImageButton fav;

        VH(View v) {
            super(v);
            badge = v.findViewById(R.id.badge);
            name = v.findViewById(R.id.name);
            group = v.findViewById(R.id.group);
            status = v.findViewById(R.id.status);
            fav = v.findViewById(R.id.favBtn);
        }
    }
}
