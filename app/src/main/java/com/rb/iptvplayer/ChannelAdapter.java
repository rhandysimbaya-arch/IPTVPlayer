package com.rb.iptvplayer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.VH> {

    public interface Listener {
        void onClick(int position);

        void onLongClick(View anchor, int position);
    }

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
        h.star.setText(c.favorite ? "★" : "");
        String n = c.name.trim();
        h.badge.setText(n.isEmpty() ? "?" : n.substring(0, 1).toUpperCase());
        h.itemView.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION) listener.onClick(p);
        });
        h.itemView.setOnLongClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION) listener.onLongClick(v, p);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView badge, name, group, star;

        VH(View v) {
            super(v);
            badge = v.findViewById(R.id.badge);
            name = v.findViewById(R.id.name);
            group = v.findViewById(R.id.group);
            star = v.findViewById(R.id.star);
        }
    }
}
