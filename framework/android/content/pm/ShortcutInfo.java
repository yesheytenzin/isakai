package android.content.pm;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
/** Shortcut value record; desktop pinning is exposed as unavailable by ShortcutManager. */
public final class ShortcutInfo {
    private final String id;
    private final CharSequence shortLabel,longLabel;
    private final Intent intent;
    private final Icon icon;
    private ShortcutInfo(Builder b) { id=b.id;shortLabel=b.shortLabel;longLabel=b.longLabel;intent=b.intent;icon=b.icon; }
    public String getId(){return id;}
    public CharSequence getShortLabel(){return shortLabel;}
    public CharSequence getLongLabel(){return longLabel;}
    public Intent getIntent(){return intent;}
    public Icon getIcon(){return icon;}
    public static final class Builder {
        private final String id;
        private CharSequence shortLabel,longLabel;
        private Intent intent;
        private Icon icon;
        public Builder(Context context,String id) {
            if(context==null || id==null || id.isEmpty())throw new IllegalArgumentException("Shortcut requires context and id");
            this.id=id;
        }
        public Builder setShortLabel(CharSequence label){shortLabel=label;return this;}
        public Builder setLongLabel(CharSequence label){longLabel=label;return this;}
        public Builder setIntent(Intent value){intent=value;return this;}
        public Builder setIcon(Icon value){icon=value;return this;}
        public ShortcutInfo build(){
            if(shortLabel==null || shortLabel.length()==0 || intent==null)throw new IllegalArgumentException("Shortcut requires label and intent");
            return new ShortcutInfo(this);
        }
    }
}
