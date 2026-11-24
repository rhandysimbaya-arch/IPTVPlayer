package com.rb.iptvplayer;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.VH> {

    public interface Listener {
        void onClick(int position);

        void onLongClick(View anchor, int position);

        void onFavorite(int position);
    }

    private static final int[] PALETTE = {
            0xFFE53935, 0xFF8E24AA, 0xFF3949AB, 0xFF1E88E5,
            0xFF00897B, 0xFF43A047, 0xFFF4511E, 0xFF6D4C41};

    private final List<Channel> items;
    private final Listener listener;

    public ChannelAdapter(List<Channel> items, Listener listener) {
        this.items = items;
        this.listener = listener;
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
        if (st == Channel.ONLINE) {
            h.status.setText("● Online");
            h.status.setTextColor(0xFF43A047);
        } else if (st == Channel.OFFLINE) {
            h.status.setText("● Offline");
            h.status.setTextColor(0xFFE53935);
        } else {
            boolean checking = Updater.get(h.itemView.getContext()).isRunning();
            h.status.setText(checking ? "● Checking…" : "● Not checked");
            h.status.setTextColor(0xFF8A8F98);
        }

        h.fav.setImageResource(c.favorite ? R.drawable.ic_star : R.drawable.ic_star_border);

        String n = c.name.trim();
        h.badge.setText(n.isEmpty() ? "?" : n.substring(0, 1).toUpperCase());
        int color = PALETTE[Math.abs(c.group.hashCode()) % PALETTE.length];
        h.badge.setBackgroundTintList(ColorStateList.valueOf(color));
        h.badge.setTextColor(Color.WHITE);

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
        final TextView badge, name, group, status;
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
