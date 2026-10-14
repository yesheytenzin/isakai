package android.content.res;
import java.nio.charset.StandardCharsets;
import android.util.AttributeSet;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;

public final class XmlParserTest {
    public static void main(String[] args) throws Exception {
        Resources systemOnly=new Resources((Resources.Table)null);
        if(systemOnly.getColor(0x0106001e)!=0xfffcfcff)throw new AssertionError("AOSP neutral1_10 missing");
        if(systemOnly.getColorStateList(0x0106001e).getDefaultColor()!=0xfffcfcff)throw new AssertionError("system color state list");
        String layerXml="<layer-list xmlns:android=\"http://schemas.android.com/apk/res/android\"><item android:id=\"@0x7f010001\" ><shape><solid android:color=\"#ffffffff\"/></shape></item><item android:id=\"@0x7f010002\" android:drawable=\"#ff00ff00\"/></layer-list>";
        try (XmlResourceParser parser=Resources.decodeBinaryXml(layerXml.getBytes(StandardCharsets.UTF_8),"layers.xml")) {
            android.graphics.drawable.LayerDrawable layers=(android.graphics.drawable.LayerDrawable)
                    android.graphics.drawable.Drawable.createFromXml(systemOnly,parser);
            if(layers==null || layers.getNumberOfLayers()!=2 || layers.findDrawableByLayerId(0x7f010001)==null
                    || layers.findDrawableByLayerId(0x7f010002)==null)throw new AssertionError("layer item ID/reference lost during parser advance");
            android.graphics.drawable.Drawable first=layers.findDrawableByLayerId(0x7f010001);
            first.setBounds(1,1,3,3);first.setTint(0xff123456);
            android.graphics.Canvas canvas=new android.graphics.Canvas(4,4);first.draw(canvas);
            if(canvas.getPixel(1,1)!=0xff123456 || canvas.getPixel(0,0)!=0)throw new AssertionError("solid tint or drawable bounds ignored");
            android.graphics.drawable.ColorDrawable transparent=new android.graphics.drawable.ColorDrawable(0);
            transparent.setBounds(0,0,4,4);transparent.draw(canvas);
            if(canvas.getPixel(1,1)!=0xff123456)throw new AssertionError("transparent drawable erased destination");
        }
        String rippleXml="<ripple xmlns:android=\"http://schemas.android.com/apk/res/android\" android:color=\"#ff888888\"><item android:drawable=\"#ff00ff00\"/><item android:id=\"@android:id/mask\" android:drawable=\"#ffff0000\"/></ripple>";
        try(XmlResourceParser parser=Resources.decodeBinaryXml(rippleXml.getBytes(StandardCharsets.UTF_8),"ripple.xml")) {
            android.graphics.drawable.RippleDrawable ripple=(android.graphics.drawable.RippleDrawable)
                    android.graphics.drawable.Drawable.createFromXml(systemOnly,parser);
            if(ripple==null || ripple.findDrawableByLayerId(0x0102002e)==null)throw new AssertionError("ripple mask identity lost");
            ripple.setBounds(0,0,2,2);android.graphics.Canvas canvas=new android.graphics.Canvas(2,2);ripple.draw(canvas);
            if(canvas.getPixel(0,0)!=0xff00ff00)throw new AssertionError("ripple mask must not render as content");
        }
        String xml="<menu><item id=\"7\" enabled=\"true\"/></menu>";
        try(XmlResourceParser parser=Resources.decodeBinaryXml(xml.getBytes(StandardCharsets.UTF_8),"menu.xml")) {
            if(parser.next()!=XmlPullParser.START_TAG || !"menu".equals(parser.getName()))throw new AssertionError("menu start");
            if(Xml.asAttributeSet(parser)!=parser)throw new AssertionError("resource attribute identity");
            if(parser.next()!=XmlPullParser.START_TAG || !"item".equals(parser.getName()))throw new AssertionError("item start");
            if(parser.next()!=XmlPullParser.END_TAG || !"item".equals(parser.getName()))throw new AssertionError("item end name missing");
            if(parser.next()!=XmlPullParser.END_TAG || !"menu".equals(parser.getName()))throw new AssertionError("menu end name missing");
            if(parser.next()!=XmlPullParser.END_DOCUMENT || parser.getName()!=null)throw new AssertionError("end document name");
        }
        XmlPullParser plain=Xml.newPullParser();plain.setInput(new java.io.StringReader(xml));
        plain.nextTag();plain.nextTag();AttributeSet attrs=Xml.asAttributeSet(plain);
        if(attrs.getAttributeIntValue(null,"id",0)!=7 || !attrs.getAttributeBooleanValue(null,"enabled",false))throw new AssertionError("plain attributes lost");
        Resources includedResources=new Resources((Resources.Table)null) {
            public XmlResourceParser getLayout(int id) {
                String body=id==1?"<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"><include layout=\"@2\" android:id=\"@0x7b\"/></LinearLayout>":
                        "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" android:id=\"@0x63\"/>";
                return Resources.decodeBinaryXml(body.getBytes(StandardCharsets.UTF_8),"include.xml");
            }
        };
        android.app.Application application=new android.app.Application() {
            public Resources getResources(){return includedResources;}
        };
        android.view.View inflated=android.view.LayoutInflater.from(application).inflate(1,null,false);
        if(inflated.findViewById(123)==null || inflated.findViewById(99)!=null)throw new AssertionError("include ID override lost");
        Resources enumResources=new Resources((Resources.Table)null) {
            public XmlResourceParser getLayout(int id) {
                String body="<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" android:orientation=\"0\">"
                        +"<View android:visibility=\"2\"/><View android:visibility=\"1\"/><View android:visibility=\"0\"/></LinearLayout>";
                return Resources.decodeBinaryXml(body.getBytes(StandardCharsets.UTF_8),"compiled-enums.xml");
            }
        };
        android.app.Application enumApp=new android.app.Application() {
            public Resources getResources(){return enumResources;}
        };
        android.widget.LinearLayout enumLayout=(android.widget.LinearLayout)android.view.LayoutInflater.from(enumApp).inflate(1,null,false);
        if(enumLayout.getOrientation()!=android.widget.LinearLayout.HORIZONTAL
                || enumLayout.getChildAt(0).getVisibility()!=android.view.View.GONE
                || enumLayout.getChildAt(1).getVisibility()!=android.view.View.INVISIBLE
                || enumLayout.getChildAt(2).getVisibility()!=android.view.View.VISIBLE)
            throw new AssertionError("compiled visibility/orientation enum values ignored");
        System.out.println("PASS: XML end-tag names, resource attribute identity and plain attribute adapter");
    }
}
