// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

/** Async filter flow: performFiltering then publishResults via Delayer. */
public abstract class Filter {
    public static final class FilterResults {
        public Object values;
        public int count;
    }
    public interface Delayer {
        long getPostingDelay(CharSequence constraint);
    }
    protected abstract FilterResults performFiltering(CharSequence constraint);
    protected abstract void publishResults(CharSequence constraint, FilterResults results);
    public final void filter(CharSequence constraint) { publishResults(constraint, performFiltering(constraint)); }
    public final void filter(CharSequence constraint, FilterListener listener) {
        FilterResults results = performFiltering(constraint);
        publishResults(constraint, results);
        if (listener != null) listener.onFilterComplete(results.count);
    }
    public CharSequence convertResultToString(Object resultValue) { return resultValue == null ? "" : resultValue.toString(); }
    public interface FilterListener {
        void onFilterComplete(int count);
    }
}
