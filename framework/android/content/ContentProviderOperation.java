package android.content;
import android.net.Uri;

/** Immutable CRUD operation; provider execution is explicit and reports failure. */
public final class ContentProviderOperation {
    private final int type;
    private final Uri uri;
    private final ContentValues values;
    private final String selection;
    private final String[] selectionArgs;
    private final Integer expectedCount;
    private ContentProviderOperation(Builder b) {
        type=b.type;uri=b.uri;values=new ContentValues(b.values);selection=b.selection;
        selectionArgs=b.selectionArgs==null?null:b.selectionArgs.clone();expectedCount=b.expectedCount;
    }
    public static Builder newInsert(Uri uri) { return new Builder(1,uri); }
    public static Builder newUpdate(Uri uri) { return new Builder(2,uri); }
    public static Builder newDelete(Uri uri) { return new Builder(3,uri); }
    public Uri getUri() { return uri; }
    public boolean isInsert() { return type==1; }
    public boolean isUpdate() { return type==2; }
    public boolean isDelete() { return type==3; }
    public ContentProviderResult apply(ContentProvider provider,ContentProviderResult[] backRefs,int numBackRefs)
            throws OperationApplicationException {
        if (isInsert()) {
            Uri inserted=provider.insert(uri,new ContentValues(values));
            if (inserted==null) throw new OperationApplicationException("Provider insert returned null");
            return new ContentProviderResult(inserted);
        }
        int count=isUpdate()?provider.update(uri,new ContentValues(values),selection,selectionArgs):
                provider.delete(uri,selection,selectionArgs);
        if(expectedCount!=null && count!=expectedCount)
            throw new OperationApplicationException("Unexpected provider row count: "+count);
        return new ContentProviderResult(count);
    }
    public static final class Builder {
        private final int type;
        private final Uri uri;
        private final ContentValues values=new ContentValues();
        private String selection;
        private String[] selectionArgs;
        private Integer expectedCount;
        private Builder(int type,Uri uri) {
            if(uri==null)throw new NullPointerException("uri");this.type=type;this.uri=uri;
        }
        public Builder withValue(String key,Object value) {
            if(type==3)throw new IllegalArgumentException("Delete has no values");
            if(value==null)values.putNull(key);
            else if(value instanceof String)values.put(key,(String)value);
            else if(value instanceof Integer)values.put(key,(Integer)value);
            else if(value instanceof Long)values.put(key,(Long)value);
            else if(value instanceof Float)values.put(key,(Float)value);
            else if(value instanceof Double)values.put(key,(Double)value);
            else if(value instanceof Boolean)values.put(key,(Boolean)value);
            else if(value instanceof byte[])values.put(key,(byte[])value);
            else if(value instanceof Short || value instanceof Byte)values.put(key,((Number)value).intValue());
            else throw new IllegalArgumentException("Unsupported provider value type");
            return this;
        }
        public Builder withValues(ContentValues source) {
            for(java.util.Map.Entry<String,Object> entry:source.valueSet())withValue(entry.getKey(),entry.getValue());
            return this;
        }
        public Builder withSelection(String selection,String[] args) {
            if(type==1)throw new IllegalArgumentException("Insert has no selection");
            this.selection=selection;selectionArgs=args==null?null:args.clone();return this;
        }
        public Builder withExpectedCount(int count) {
            if(type==1)throw new IllegalArgumentException("Insert has no row count");
            expectedCount=count;return this;
        }
        public ContentProviderOperation build() { return new ContentProviderOperation(this); }
    }
}
