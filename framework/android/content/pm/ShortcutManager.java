package android.content.pm;
import android.content.IntentSender;
import java.util.List;
/** Launcher shortcut publishing has no host backend yet. */
public final class ShortcutManager {
    public boolean isRequestPinShortcutSupported(){return false;}
    public boolean requestPinShortcut(ShortcutInfo shortcut,IntentSender callback){
        org.isakai.runtime.Capabilities.unavailable("ShortcutManager.requestPinShortcut");return false;
    }
    public boolean setDynamicShortcuts(List<ShortcutInfo> shortcuts){
        org.isakai.runtime.Capabilities.unavailable("ShortcutManager.setDynamicShortcuts");return false;
    }
}
