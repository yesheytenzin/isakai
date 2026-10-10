// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

import android.os.Bundle;

/** Legacy fragment: lifecycle hooks with activity link. */
public class Fragment {
    private Activity activity;

    public void onAttach(Activity activity) {
        this.activity = activity;
    }

    public Activity getActivity() {
        return activity;
    }

    public void onCreate(Bundle savedInstanceState) {}
    public void onActivityCreated(Bundle savedInstanceState) {}
    public void onStart() {}
    public void onResume() {}
    public void onPause() {}
    public void onStop() {}
    public void onDestroy() {}
    public void onSaveInstanceState(Bundle outState) {}
}
