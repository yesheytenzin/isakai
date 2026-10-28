// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.inputmethod;

import android.os.Bundle;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;

/** Editable-backed input connection with selection and composing-region replacement. */
public class BaseInputConnection implements InputConnection {
    protected final View targetView;
    protected final boolean fullEditor;
    private final Editable buffer = Editable.Factory.getInstance().newEditable("");
    private int start,end,composingStart=-1,composingEnd=-1;
    public BaseInputConnection(View view,boolean editor) { targetView=view;fullEditor=editor; }
    public Editable getEditable() { return targetView instanceof EditText?((EditText)targetView).getText():buffer; }
    private int start() { return targetView instanceof EditText?((EditText)targetView).getSelectionStart():start; }
    private int end() { return targetView instanceof EditText?((EditText)targetView).getSelectionEnd():end; }
    private void changed() { if(targetView instanceof EditText)((EditText)targetView).notifyEditableChanged(); }
    public CharSequence getTextBeforeCursor(int length,int flags) {
        int at=Math.min(start(),end());return getEditable().subSequence(Math.max(0,at-Math.max(0,length)),at).toString();
    }
    public CharSequence getTextAfterCursor(int length,int flags) {
        int at=Math.max(start(),end());return getEditable().subSequence(at,Math.min(getEditable().length(),at+Math.max(0,length))).toString();
    }
    public CharSequence getSelectedText(int flags) {
        return start()==end()?null:getEditable().subSequence(Math.min(start(),end()),Math.max(start(),end())).toString();
    }
    private boolean replace(CharSequence text,int cursorPosition,boolean composing) {
        Editable editable=getEditable();
        int a=composingStart>=0?composingStart:Math.min(start(),end());
        int b=composingEnd>=0?composingEnd:Math.max(start(),end());
        String value=text==null?"":text.toString();
        editable.replace(a,b,value);
        if(composing){composingStart=a;composingEnd=a+value.length();}
        else {composingStart=composingEnd=-1;}
        int cursor=cursorPosition>0?a+value.length()+cursorPosition-1:a+cursorPosition;
        changed();return setSelection(cursor,cursor);
    }
    public boolean setComposingText(CharSequence text,int cursorPosition) { return replace(text,cursorPosition,true); }
    public boolean commitText(CharSequence text,int cursorPosition) { return replace(text,cursorPosition,false); }
    public boolean finishComposingText() { composingStart=composingEnd=-1;return true; }
    public boolean setComposingRegion(int a,int b) {
        composingStart=Math.max(0,Math.min(Math.min(a,b),getEditable().length()));
        composingEnd=Math.max(0,Math.min(Math.max(a,b),getEditable().length()));return true;
    }
    public boolean deleteSurroundingText(int before,int after) {
        int a=Math.min(start(),end()),b=Math.max(start(),end());
        int from=Math.max(0,a-Math.max(0,before)),to=Math.min(getEditable().length(),b+Math.max(0,after));
        getEditable().delete(b,to);getEditable().delete(from,a);
        composingStart=composingEnd=-1;changed();return setSelection(from,from+(b-a));
    }
    public boolean deleteSurroundingTextInCodePoints(int before,int after) {
        if(before<0||after<0)return false;
        String text=getEditable().toString();int a=Math.min(start(),end()),b=Math.max(start(),end());
        int from=a,to=b;
        for(int i=0;i<before && from>0;i++)from=text.offsetByCodePoints(from,-1);
        for(int i=0;i<after && to<text.length();i++)to=text.offsetByCodePoints(to,1);
        return deleteSurroundingText(a-from,to-b);
    }
    public boolean setSelection(int a,int b) {
        int length=getEditable().length();
        start=Math.max(0,Math.min(a,length));end=Math.max(0,Math.min(b,length));
        if(targetView instanceof EditText)((EditText)targetView).setSelection(start,end);
        return true;
    }
    public boolean beginBatchEdit() { return true; }
    public boolean endBatchEdit() { return true; }
    public boolean performEditorAction(int code) { return false; }
    public boolean performPrivateCommand(String action,Bundle data) { return false; }
    public boolean commitContent(InputContentInfo info,int flags,Bundle opts) { return false; }
    public boolean sendKeyEvent(KeyEvent event) { return targetView!=null && targetView.dispatchKeyEvent(event); }
}
