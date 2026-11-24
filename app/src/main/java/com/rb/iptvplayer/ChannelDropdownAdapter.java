package com.rb.iptvplayer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Dropdown list of channels that also filters while you type (matches anywhere in the name or group). */
public class ChannelDropdownAdapter extends BaseAdapter implements Filterable {

    private final List<Channel> all;
    private List<Channel> shown;

    public ChannelDropdownAdapter(List<Channel> all) {
        this.all = all;
        this.shown = all;
    }

    public void showAll() {
        shown = all;
        notifyDataSetChanged();
    }

    public Channel first() {
        return shown.isEmpty() ? null : shown.get(0);
    }

    @Override
    public int getCount() {
        return shown.size();
    }

    @Override
    public Channel getItem(int i) {
        return shown.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View convert, ViewGroup parent) {
        if (convert == null) {
            convert = LayoutInflater.from(parent.getContext()).inflate(R.layout.dropdown_item, parent, false);
        }
        Channel c = shown.get(i);
        ((TextView) convert.findViewById(R.id.ddName)).setText(c.name);
        ((TextView) convert.findViewById(R.id.ddGroup)).setText(c.group);
        return convert;
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence cs) {
                FilterResults r = new FilterResults();
                String q = cs == null ? "" : cs.toString().trim().toLowerCase(Locale.ROOT);
                List<Channel> out;
                if (q.isEmpty()) {
                    out = all;
                } else {
                    out = new ArrayList<>();
                    for (Channel c : all) {
                        if (c.name.toLowerCase(Locale.ROOT).contains(q)
                                || c.group.toLowerCase(Locale.ROOT).contains(q)) out.add(c);
                    }
                }
                r.values = out;
                r.count = out.size();
                return r;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence cs, FilterResults r) {
                shown = (List<Channel>) r.values;
                if (shown.isEmpty()) notifyDataSetInvalidated();
                else notifyDataSetChanged();
            }

            @Override
            public CharSequence convertResultToString(Object o) {
                return ((Channel) o).name;
            }
        };
    }
}
