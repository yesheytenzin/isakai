// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

/** List-backed adapter rendering items as text rows. */
public class ArrayAdapter<T> extends BaseAdapter {
    private final List<T> items = new ArrayList<>();
    private final Context context;
    private int dropDownResource;

    public ArrayAdapter() {
        this.context = null;
    }

    public ArrayAdapter(Context context, int resource) {
        this.context = context;
    }

    public ArrayAdapter(Context context, int resource, T[] objects) {
        this.context = context;
        if (objects != null) for (T o : objects) items.add(o);
    }

    public ArrayAdapter(Context context, int resource, int textViewResourceId, T[] objects) {
        this(context, resource, objects);
    }

    public ArrayAdapter(Context context, int resource, List<T> objects) {
        this.context = context;
        if (objects != null) items.addAll(objects);
    }

    public void add(T object) {
        items.add(object);
        notifyDataSetChanged();
    }

    public void addAll(T... items) {
        if (items != null) for (T o : items) this.items.add(o);
        notifyDataSetChanged();
    }

    public void clear() {
        items.clear();
        notifyDataSetChanged();
    }

    public void remove(T object) {
        items.remove(object);
        notifyDataSetChanged();
    }

    public void setDropDownViewResource(int resource) {
        this.dropDownResource = resource;
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public T getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public android.view.View getView(int position, android.view.View convertView,
            android.view.ViewGroup parent) {
        TextView tv = convertView instanceof TextView ? (TextView) convertView : new TextView();
        T item = getItem(position);
        tv.setText(item == null ? "" : item.toString());
        return tv;
    }
}
