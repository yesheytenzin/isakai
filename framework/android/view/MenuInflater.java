// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Menu inflater: builds Menu structure from binary-XML menu resources
 * ({@code <menu>}, {@code <item>}, {@code <group>}).
 *
 * <p>Group defaults (visible/enabled/checkable) apply to items without
 * explicit values. Item titles resolve {@code @string} refs via resources
 * with a literal fallback; unknown ids fail closed via {@link
 * android.util.AndroidRuntimeException}.
 */
public class MenuInflater {
    private final android.content.Context mContext;

    public MenuInflater(android.content.Context context) {
        if (context == null) throw new IllegalArgumentException("context is null");
        mContext = context;
    }

    public void inflate(int menuRes, Menu menu) {
        if (menu == null) throw new IllegalArgumentException("menu is null");
        android.content.res.XmlResourceParser parser = null;
        try {
            parser = mContext.getResources().getXml(menuRes);
        } catch (RuntimeException e) {
            throw new android.util.AndroidRuntimeException(
                    "menu inflate: bad menu id 0x" + Integer.toHexString(menuRes), e);
        }
        try {
            inflate(parser, menu);
        } finally {
            if (parser != null) {
                try {
                    parser.close();
                } catch (Exception e) {
                }
            }
        }
    }

    /** @hide test seam: inflate a menu from an open parser. */
    public void inflate(XmlPullParser parser, Menu menu) {
        if (parser == null) {
            throw new android.util.AndroidRuntimeException("menu inflate: parser is null");
        }
        if (menu == null) throw new IllegalArgumentException("menu is null");
        try {
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG
                    && type != XmlPullParser.END_DOCUMENT) {
            }
            if (type != XmlPullParser.START_TAG || !"menu".equals(parser.getName())) {
                throw new android.util.AndroidRuntimeException(
                        "menu inflate: expected <menu>");
            }
            parseMenu(parser, menu, new GroupState());
        } catch (XmlPullParserException | IOException e) {
            throw new android.util.AndroidRuntimeException(
                    "menu inflate: " + e.getMessage(), e);
        }
    }

    private static final class GroupState {
        int id;
        boolean visible = true;
        boolean enabled = true;
        boolean checkable;
    }

    private void parseMenu(XmlPullParser parser, Menu menu, GroupState group)
            throws XmlPullParserException, IOException {
        int depth = parser.getDepth();
        int type;
        while (((type = parser.next()) != XmlPullParser.END_TAG
                || parser.getDepth() > depth)
                && type != XmlPullParser.END_DOCUMENT) {
            if (type != XmlPullParser.START_TAG) continue;
            String name = parser.getName();
            if ("group".equals(name)) {
                GroupState next = new GroupState();
                next.id = resOf(parser, "id", group.id);
                next.visible = boolOf(parser, "visible", true);
                next.enabled = boolOf(parser, "enabled", true);
                next.checkable = boolOf(parser, "checkable", false);
                parseMenu(parser, menu, next);
            } else if ("item".equals(name)) {
                parseItem(parser, menu, group);
            } else if ("menu".equals(name)) {
                parseMenu(parser, menu, group);
            } else {
                skip(parser);
            }
        }
    }

    private void parseItem(XmlPullParser parser, Menu menu, GroupState group)
            throws XmlPullParserException, IOException {
        int groupId = resOf(parser, "id", group.id);
        int itemId = attrId(parser);
        int order = intOf(parser, "orderInCategory", 0);
        CharSequence title = textOf(parser, "title");
        int depth = parser.getDepth();
        MenuItem item = menu.add(groupId, itemId, order, title);
        item.setVisible(boolOf(parser, "visible", group.visible));
        item.setEnabled(boolOf(parser, "enabled", group.enabled));
        item.setCheckable(boolOf(parser, "checkable", group.checkable));
        item.setChecked(boolOf(parser, "checked", false));
        int showAsAction = intOf(parser, "showAsAction", MenuItem.SHOW_AS_ACTION_NEVER);
        if (showAsAction != MenuItem.SHOW_AS_ACTION_NEVER) {
            item.setShowAsAction(showAsAction);
        }
        String iconRaw = attr(parser, "icon");
        int iconRes = resOf(parser, "icon", 0);
        if (iconRes != 0) {
            try {
                item.setIcon(iconRes);
            } catch (RuntimeException e) {
                android.util.Log.w("MenuInflater", "bad icon " + iconRaw);
            }
        }
        int type;
        Menu sub = null;
        while (((type = parser.next()) != XmlPullParser.END_TAG
                || parser.getDepth() > depth)
                && type != XmlPullParser.END_DOCUMENT) {
            if (type != XmlPullParser.START_TAG) continue;
            if ("menu".equals(parser.getName())) {
                SubMenu subMenu = menu.addSubMenu(groupId, itemId, order, title);
                parseMenu(parser, subMenu, new GroupState());
                sub = subMenu;
            } else {
                skip(parser);
            }
        }
        if (sub == null) {
            String click = attr(parser, "onClick");
            if (click != null && !click.isEmpty()) {
                final String method = click;
                item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem it) {
                        android.util.Log.w("MenuInflater",
                                "menu onClick " + method + " unsupported headless");
                        return false;
                    }
                });
            }
        }
    }

    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        int depth = parser.getDepth();
        int type;
        while (((type = parser.next()) != XmlPullParser.END_TAG
                || parser.getDepth() > depth)
                && type != XmlPullParser.END_DOCUMENT) {
        }
    }

    private CharSequence textOf(XmlPullParser parser, String name) {
        int res = resOf(parser, name, 0);
        if (res != 0) {
            try {
                return mContext.getResources().getText(res);
            } catch (RuntimeException e) {
            }
        }
        String raw = attr(parser, name);
        return raw != null ? raw : "";
    }

    private int attrId(XmlPullParser parser) {
        int res = resOf(parser, "id", 0);
        if (res != 0) return res;
        String raw = attr(parser, "id");
        if (raw == null) return 0;
        raw = raw.trim();
        if (raw.startsWith("0x") || raw.startsWith("0X")) {
            try {
                return (int) Long.parseLong(raw.substring(2), 16);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String attr(XmlPullParser parser, String name) {
        if (!(parser instanceof android.util.AttributeSet)) {
            try {
                return parser.getAttributeValue(null, name);
            } catch (RuntimeException e) {
                return null;
            }
        }
        android.util.AttributeSet attrs = (android.util.AttributeSet) parser;
        try {
            String v = attrs.getAttributeValue(null, name);
            if (v != null) return v;
        } catch (RuntimeException e) {
        }
        try {
            String v = attrs.getAttributeValue(
                    "http://schemas.android.com/apk/res/android", name);
            if (v != null) return v;
        } catch (RuntimeException e) {
        }
        try {
            int n = attrs.getAttributeCount();
            for (int i = 0; i < n; i++) {
                String an;
                try {
                    an = attrs.getAttributeName(i);
                } catch (RuntimeException e) {
                    continue;
                }
                if (an == null) continue;
                if (an.equals(name) || an.endsWith(":" + name)) {
                    try {
                        return attrs.getAttributeValue(i);
                    } catch (RuntimeException e) {
                        return null;
                    }
                }
            }
        } catch (RuntimeException e) {
        }
        return null;
    }

    private static int resOf(XmlPullParser parser, String name, int def) {
        if (parser instanceof android.util.AttributeSet) {
            android.util.AttributeSet attrs = (android.util.AttributeSet) parser;
            try {
                int v = attrs.getAttributeResourceValue(null, name, def);
                if (v != def) return v;
            } catch (RuntimeException e) {
            }
            try {
                return attrs.getAttributeResourceValue(
                        "http://schemas.android.com/apk/res/android", name, def);
            } catch (RuntimeException e) {
                return def;
            }
        }
        return def;
    }

    private static boolean boolOf(XmlPullParser parser, String name, boolean def) {
        String raw = attr(parser, name);
        if (raw == null) return def;
        if ("true".equalsIgnoreCase(raw) || "1".equals(raw)) return true;
        if ("false".equalsIgnoreCase(raw) || "0".equals(raw)) return false;
        return def;
    }

    private static int intOf(XmlPullParser parser, String name, int def) {
        String raw = attr(parser, name);
        if (raw == null) return def;
        if ("never".equalsIgnoreCase(raw)) return MenuItem.SHOW_AS_ACTION_NEVER;
        if ("ifRoom".equalsIgnoreCase(raw)) return MenuItem.SHOW_AS_ACTION_IF_ROOM;
        if ("always".equalsIgnoreCase(raw)) return MenuItem.SHOW_AS_ACTION_ALWAYS;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
