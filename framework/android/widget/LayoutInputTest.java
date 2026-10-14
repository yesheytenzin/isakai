package android.widget;

import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.BaseInputConnection;

public final class LayoutInputTest {
    private static void check(boolean value,String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        ViewGroup.MarginLayoutParams margins = new ViewGroup.MarginLayoutParams(10,20);
        margins.setMargins(3,4,5,6);
        margins.resolveLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        check(margins.leftMargin==3 && margins.rightMargin==5,"physical margins lost");
        margins.setMarginStart(9); margins.setMarginEnd(11);
        margins.resolveLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        check(margins.leftMargin==11 && margins.rightMargin==9,"RTL margins reversed");
        ViewGroup.MarginLayoutParams copy = new ViewGroup.MarginLayoutParams(margins);
        copy.resolveLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        check(copy.leftMargin==9 && copy.rightMargin==11,"relative margins lost on copy");

        android.widget.FrameLayout container=new android.widget.FrameLayout(new android.app.Application());
        check(container.getChildAt(-1)==null && container.getChildAt(0)==null,"out-of-range child must return null");
        View child=new View();container.addView(child);container.layout(0,0,100,100);
        check(!container.isLayoutRequested(),"layout flag did not clear");
        child.requestLayout();check(container.isLayoutRequested(),"child layout request did not reach parent");
        TextView label = new TextView(); label.setText("བཀྲ་ཤིས་ 中文 😀"); label.setMinWidth(210);
        int atMost = View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.AT_MOST);
        label.measure(atMost,atMost);
        check(label.getMeasuredWidth()>=210,"minimum text width ignored");
        label.measure(View.MeasureSpec.makeMeasureSpec(100,View.MeasureSpec.EXACTLY),atMost);
        check(label.getMeasuredWidth()==100,"minimum overrides parent exact bound");
        Canvas canvas = new Canvas(300,80);
        canvas.drawText("བཀྲ་ཤིས་ 中文 😀",4,4,24,0xff000000);
        check(Canvas.measureText("中文",24)>0,"Unicode advance missing");

        android.text.Editable filtered=android.text.Editable.Factory.getInstance().newEditable("a");
        filtered.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2)});
        filtered.append("😀");
        check(filtered.toString().equals("a"),"length filter split a surrogate pair");
        filtered.replace(0,1,"😀");
        check(filtered.toString().equals("😀"),"replacement filter ignored freed capacity");
        EditText customEdit=new EditText();customEdit.setText("中文");
        final int[] factoryCalls={0};
        customEdit.setEditableFactory(new android.text.Editable.Factory() {
            public android.text.Editable newEditable(CharSequence text) {
                factoryCalls[0]++;return new android.text.SpannableStringBuilder(text);
            }
        });
        check(factoryCalls[0]==1 && customEdit.getText().toString().equals("中文"),"editable factory lost existing text");
        EditText edit = new EditText(); edit.setText("a😀b"); edit.setSelection(3);
        BaseInputConnection input = new BaseInputConnection(edit,true);
        check(input.deleteSurroundingTextInCodePoints(1,0),"codepoint delete failed");
        check(edit.getText().toString().equals("ab"),"split supplementary codepoint");
        input.setSelection(1,1); input.setComposingText("中",1); input.setComposingText("中文",1);
        check(edit.getText().toString().equals("a中文b"),"composition appended instead of replacing");
        input.commitText("ཀ",1);
        check(edit.getText().toString().equals("aཀb"),"commit failed to replace composition");
        input.setSelection(0,2);
        check(input.getSelectedText(0).toString().equals("aཀ"),"selection mismatch");
        input.commitText("x",1);
        check(edit.getText().toString().equals("xb"),"selection not replaced");
        System.out.println("PASS: margin direction/copy, text bounds, Unicode and composing input");
    }
}
