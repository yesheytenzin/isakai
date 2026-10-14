// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.graphics.BitmapFont;
import android.graphics.Canvas;
import android.graphics.Color;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * Minimal single-line EditText: an Editable buffer with a cursor,
 * printable-key insert, DEL/BACKSPACE delete-before-cursor, DPAD move,
 * selection, clipboard shortcuts and an editable-backed input connection.
 */
public class EditText extends TextView {
    private Editable editable = Editable.Factory.getInstance().newEditable("");
    private int cursor = 0;
    private int anchor = 0;
    private int scrollX = 0;
    @Override public void setEditableFactory(Editable.Factory factory) {
        super.setEditableFactory(factory);editable=factory.newEditable(editable.toString());notifyEditableChanged();
    }
    private boolean singleLine = true;
    public void setSingleLine(boolean value) { singleLine=value; }
    public void setSingleLine() { singleLine=true; }

    public EditText() {
        super();
        setFocusable(true);
        setClickable(true);
    }

    public EditText(android.content.Context context) {
        super(context);
        setFocusable(true);
        setClickable(true);
    }

    public EditText(android.content.Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        setFocusable(true);
        setClickable(true);
    }

    public EditText(android.content.Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setFocusable(true);
        setClickable(true);
    }

    @Override
    public void setText(CharSequence text) {
        editable.replaceAll(text);
        cursor = editable.length();
        anchor = cursor;
        scrollX = 0;
        super.setText((CharSequence) editable.toString());
    }

    public void setText(String text) {
        setText((CharSequence) text);
    }

    @Override
    public Editable getText() {
        return editable;
    }

    public int getSelectionStart() { return anchor; }
    public int getSelectionEnd() { return cursor; }
    public void setSelection(int index) { setSelection(index,index); }
    public void setSelection(int start,int end) {
        anchor = boundary(start); cursor = boundary(end);
        ensureCursorVisible(); invalidate();
    }
    private int boundary(int value) {
        int index=Math.max(0,Math.min(value,editable.length()));
        if(index>0 && index<editable.length() && Character.isLowSurrogate(editable.charAt(index))
                && Character.isHighSurrogate(editable.charAt(index-1)))index--;
        return index;
    }
    public void selectAll() { setSelection(0,editable.length()); }
    public void replaceSelection(CharSequence text) {
        int start=Math.min(anchor,cursor),end=Math.max(anchor,cursor);
        String value=text==null?"":text.toString();
        editable.delete(start,end); editable.insert(start,value);
        anchor=cursor=start+value.length();refresh();
    }
    public void notifyEditableChanged() { anchor=boundary(anchor);cursor=boundary(cursor);refresh(); }
    public android.view.inputmethod.InputConnection onCreateInputConnection(android.view.inputmethod.EditorInfo info) {
        info.initialSelStart=anchor;info.initialSelEnd=cursor;
        return new android.view.inputmethod.BaseInputConnection(this,true);
    }
    public boolean onCheckIsTextEditor() { return true; }
    private android.content.ClipboardManager clipboard() {
        Object service=getContext()==null?null:getContext().getSystemService("clipboard");
        return service instanceof android.content.ClipboardManager?(android.content.ClipboardManager)service:null;
    }
    private boolean copy(boolean cut) {
        android.content.ClipboardManager manager=clipboard();
        if(manager==null || anchor==cursor)return false;
        manager.setPrimaryClip(android.content.ClipData.newPlainText("",
                editable.subSequence(Math.min(anchor,cursor),Math.max(anchor,cursor))));
        if(cut)replaceSelection("");
        return true;
    }
    private boolean paste() {
        android.content.ClipboardManager manager=clipboard();
        if(manager==null)return false;
        CharSequence value=manager.getText();
        if(value==null)return false;
        replaceSelection(value);return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN
                && containsPoint(event.getX(), event.getY())) {
            requestFocus();
            // Fall through to the standard machinery so press state and
            // the long-press arm behave like every other view; focusing
            // alone must not swallow click/long-click delivery.
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isEnabled() || !isFocused()) return false;
        if(getKeyListener()!=null && getKeyListener().onKeyDown(this,editable,keyCode,event)) { notifyEditableChanged();return true; }
        boolean ctrl=(event.getMetaState()&KeyEvent.META_CTRL_MASK)!=0;
        boolean shift=(event.getMetaState()&KeyEvent.META_SHIFT_MASK)!=0;
        if(ctrl) {
            if(keyCode==KeyEvent.KEYCODE_A){selectAll();return true;}
            if(keyCode==KeyEvent.KEYCODE_C)return copy(false);
            if(keyCode==KeyEvent.KEYCODE_X)return copy(true);
            if(keyCode==KeyEvent.KEYCODE_V)return paste();
        }
        switch(keyCode) {
            case KeyEvent.KEYCODE_DEL:
                if(anchor!=cursor)replaceSelection("");
                else if(cursor>0) { anchor=editable.toString().offsetByCodePoints(cursor,-1);replaceSelection(""); }
                return true;
            case KeyEvent.KEYCODE_FORWARD_DEL:
                if(anchor!=cursor)replaceSelection("");
                else if(cursor<editable.length()) { anchor=editable.toString().offsetByCodePoints(cursor,1);replaceSelection(""); }
                return true;
            case KeyEvent.KEYCODE_MOVE_HOME:
                cursor=0; if(!shift)anchor=cursor;ensureCursorVisible();invalidate();return true;
            case KeyEvent.KEYCODE_MOVE_END:
                cursor=editable.length(); if(!shift)anchor=cursor;ensureCursorVisible();invalidate();return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if(!shift && anchor!=cursor)cursor=Math.min(anchor,cursor);
                else if(cursor>0)cursor=editable.toString().offsetByCodePoints(cursor,-1);
                if(!shift)anchor=cursor;ensureCursorVisible();invalidate();return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if(!shift && anchor!=cursor)cursor=Math.max(anchor,cursor);
                else if(cursor<editable.length())cursor=editable.toString().offsetByCodePoints(cursor,1);
                if(!shift)anchor=cursor;ensureCursorVisible();invalidate();return true;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if(dispatchEditorAction(0,event))return true;
                if(!singleLine){replaceSelection("\n");return true;}
                clearFocus();return true;
            default:break;
        }
        int cp=event.getUnicodeChar();
        if(!ctrl && event.getAction()==KeyEvent.ACTION_DOWN && cp>=32 && cp!=127 && Character.isValidCodePoint(cp)) {
            replaceSelection(new String(Character.toChars(cp)));return true;
        }
        return false;
    }

    private void refresh() {
        ensureCursorVisible();
        super.setText(editable.toString());
    }

    private void ensureCursorVisible() {
        int cell = cellStep();
        int inner = getWidth() - 8;
        if (inner <= 0 || cell <= 0) {
            scrollX = 0;
            return;
        }
        int prefix = Canvas.measureText(editable.toString().substring(0,cursor),getTextPixelSize());
        int cursorX = prefix - scrollX;
        if (cursorX < 0) {
            scrollX = prefix;
        } else if (cursorX >= inner) {
            scrollX = prefix - inner + cell;
        }
        if (scrollX < 0) scrollX = 0;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawEditText(canvas, getLeft(), getTop());
    }

    @Override
    protected void onDrawAt(Canvas canvas, int offsetX, int offsetY) {
        drawEditText(canvas, offsetX, offsetY);
    }

    private void drawEditText(Canvas canvas, int originX, int originY) {
        canvas.drawColor(backgroundColor);
        String text=editable.toString();
        int x=originX+4-scrollX,y=originY+4;
        int lineHeight=BitmapFont.lineStep(getTextPixelSize());
        int selectionStart=Math.min(anchor,cursor),selectionEnd=Math.max(anchor,cursor),offset=0;
        for(String line:text.split("\n",-1)) {
            int start=Math.max(0,selectionStart-offset),end=Math.min(line.length(),selectionEnd-offset);
            if(end>start)canvas.fillRect(x+Canvas.measureText(line.substring(0,start),getTextPixelSize()),y,
                    x+Canvas.measureText(line.substring(0,end),getTextPixelSize()),y+lineHeight,0xffbbd7ff);
            canvas.drawText(line,x,y,getTextPixelSize(),getTextColor());
            if(isFocused() && isCursorVisible() && cursor>=offset && cursor<=offset+line.length()) {
                int cx=x+Canvas.measureText(line.substring(0,cursor-offset),getTextPixelSize());
                canvas.fillRect(cx,y,cx+1,y+lineHeight,Color.BLACK);
            }
            offset+=line.length()+1;y+=lineHeight;
        }
    }
}
