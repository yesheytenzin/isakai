// SPDX-License-Identifier: GPL-3.0-or-later
package android.net.wifi.p2p;

/** Wi-Fi Direct stub: P2P unsupported. */
public class WifiP2pManager {
    public interface ActionListener {
        void onSuccess();
        void onFailure(int reason);
    }

    public interface ChannelListener {
        void onChannelDisconnected();
    }

    public static class Channel {
    }

    public WifiP2pManager() {
    }

    public Channel initialize(android.content.Context context,
            android.os.Looper looper, ChannelListener listener) {
        return new Channel();
    }

    public void discoverPeers(Channel c, ActionListener listener) {
        if (listener != null) listener.onFailure(0);
    }

    public void stopPeerDiscovery(Channel c, ActionListener listener) {
        if (listener != null) listener.onFailure(0);
    }

    public void cancelConnect(Channel c, ActionListener listener) {
        if (listener != null) listener.onFailure(0);
    }
}
