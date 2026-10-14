package android.content;
import android.net.Uri;
/** Result of a provider insert or row-count operation. */
public final class ContentProviderResult {
    public final Uri uri;
    public final Integer count;
    public ContentProviderResult(Uri uri) { this.uri=uri;count=null; }
    public ContentProviderResult(int count) { uri=null;this.count=count; }
}
