// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;

/** Message envelope: what/arg/obj plus target handler. */
public class Message {
    public int what;
    public int arg1;
    public int arg2;
    public Object obj;
    public Handler target;
    public long when;

    public static Message obtain() {
        return new Message();
    }

    public static Message obtain(Handler h) {
        Message m = new Message();
        m.target = h;
        return m;
    }

    public static Message obtain(Handler h, int what) {
        Message m = obtain(h);
        m.what = what;
        return m;
    }

    public static Message obtain(Handler h, int what, Object obj) {
        Message m = obtain(h, what);
        m.obj = obj;
        return m;
    }

    public void sendToTarget() {
        if (target != null) target.sendMessage(this);
    }

    public void recycle() {}
}
