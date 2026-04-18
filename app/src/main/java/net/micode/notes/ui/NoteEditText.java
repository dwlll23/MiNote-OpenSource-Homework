/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.ui;

import android.content.Context;
import android.graphics.Rect;
import android.text.Layout;
import android.text.Selection;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.widget.EditText;

import net.micode.notes.R;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义 EditText，用于便签编辑界面（特别是 checklist 模式）。
 *
 * 主要功能：
 *     支持点击触摸时精确定位光标位置（重写 {@link #onTouchEvent}）
 *     在 checklist 模式下，按回车键（{@link KeyEvent#KEYCODE_ENTER}）时，
 *         将光标后的内容移到新行，并通知外部添加新的编辑框
 *     在 checklist 模式下，按删除键（{@link KeyEvent#KEYCODE_DEL}）且当前行无内容时，
 *         删除当前编辑框并通知外部
 *     支持识别文本中的链接（电话、网址、邮箱），长按时弹出上下文菜单并提供跳转功能
 *     通过 {@link OnTextViewChangeListener} 回调与外部 {@link NoteEditActivity} 交互
 *
 */
public class NoteEditText extends EditText {
    private static final String TAG = "NoteEditText";
    private int mIndex;                      // 当前编辑框在 checklist 列表中的索引
    private int mSelectionStartBeforeDelete; // 删除前光标位置，用于判断是否为空行删除

    // 支持的链接协议
    private static final String SCHEME_TEL = "tel:";
    private static final String SCHEME_HTTP = "http:";
    private static final String SCHEME_EMAIL = "mailto:";

    // 协议对应的上下文菜单资源 ID
    private static final Map<String, Integer> sSchemaActionResMap = new HashMap<String, Integer>();
    static {
        sSchemaActionResMap.put(SCHEME_TEL, R.string.note_link_tel);
        sSchemaActionResMap.put(SCHEME_HTTP, R.string.note_link_web);
        sSchemaActionResMap.put(SCHEME_EMAIL, R.string.note_link_email);
    }

    /**
     * 文本变化监听器接口，用于与 {@link NoteEditActivity} 交互。
     */
    public interface OnTextViewChangeListener {
        /**
         * 删除当前编辑框（当按删除键且文本为空时调用）。
         *
         * @param index 当前编辑框索引
         * @param text  当前编辑框的文本内容（删除前）
         */
        void onEditTextDelete(int index, String text);

        /**
         * 在当前编辑框后添加一个新的编辑框（当按回车键时调用）。
         *
         * @param index 新编辑框的索引（当前索引 + 1）
         * @param text  当前编辑框中光标后的文本（将移到新编辑框）
         */
        void onEditTextEnter(int index, String text);

        /**
         * 当文本内容从无到有或从有到无时调用，用于显示/隐藏复选框。
         *
         * @param index   当前编辑框索引
         * @param hasText 是否有文本内容
         */
        void onTextChange(int index, boolean hasText);
    }

    private OnTextViewChangeListener mOnTextViewChangeListener;

    public NoteEditText(Context context) {
        super(context, null);
        mIndex = 0;
    }

    public void setIndex(int index) {
        mIndex = index;
    }

    public void setOnTextViewChangeListener(OnTextViewChangeListener listener) {
        mOnTextViewChangeListener = listener;
    }

    public NoteEditText(Context context, AttributeSet attrs) {
        super(context, attrs, android.R.attr.editTextStyle);
    }

    public NoteEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * 处理触摸事件，实现点击定位光标到触摸位置（默认行为可能不精确）。
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 计算触摸点相对于文本布局的坐标，并设置光标位置
                int x = (int) event.getX();
                int y = (int) event.getY();
                x -= getTotalPaddingLeft();
                y -= getTotalPaddingTop();
                x += getScrollX();
                y += getScrollY();

                Layout layout = getLayout();
                int line = layout.getLineForVertical(y);
                int off = layout.getOffsetForHorizontal(line, x);
                Selection.setSelection(getText(), off);
                break;
        }
        return super.onTouchEvent(event);
    }

    /**
     * 按键按下时，记录删除前的光标位置，并拦截回车键（让 onKeyUp 处理）。
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                // 如果有监听器，返回 false 表示不消费此事件，让 onKeyUp 处理
                if (mOnTextViewChangeListener != null) {
                    return false;
                }
                break;
            case KeyEvent.KEYCODE_DEL:
                mSelectionStartBeforeDelete = getSelectionStart();
                break;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 按键弹起时，处理回车（新增行）和删除（空行时删除当前编辑框）。
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DEL:
                if (mOnTextViewChangeListener != null) {
                    // 如果光标在起始位置且不是第一个编辑框，则删除当前编辑框
                    if (0 == mSelectionStartBeforeDelete && mIndex != 0) {
                        mOnTextViewChangeListener.onEditTextDelete(mIndex, getText().toString());
                        return true;
                    }
                } else {
                    Log.d(TAG, "OnTextViewChangeListener was not seted");
                }
                break;
            case KeyEvent.KEYCODE_ENTER:
                if (mOnTextViewChangeListener != null) {
                    int selectionStart = getSelectionStart();
                    // 将光标后的文本取出，并清空当前编辑框光标后的内容
                    String text = getText().subSequence(selectionStart, length()).toString();
                    setText(getText().subSequence(0, selectionStart));
                    mOnTextViewChangeListener.onEditTextEnter(mIndex + 1, text);
                } else {
                    Log.d(TAG, "OnTextViewChangeListener was not seted");
                }
                break;
            default:
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * 焦点变化时通知外部，用于控制复选框的显示/隐藏。
     */
    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        if (mOnTextViewChangeListener != null) {
            if (!focused && TextUtils.isEmpty(getText())) {
                mOnTextViewChangeListener.onTextChange(mIndex, false);
            } else {
                mOnTextViewChangeListener.onTextChange(mIndex, true);
            }
        }
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    /**
     * 创建上下文菜单：当长按选中一个链接（URLSpan）时，显示对应的操作菜单（拨号、打开网页、发邮件等）。
     */
    @Override
    protected void onCreateContextMenu(ContextMenu menu) {
        if (getText() instanceof Spanned) {
            int selStart = getSelectionStart();
            int selEnd = getSelectionEnd();

            int min = Math.min(selStart, selEnd);
            int max = Math.max(selStart, selEnd);

            final URLSpan[] urls = ((Spanned) getText()).getSpans(min, max, URLSpan.class);
            if (urls.length == 1) {
                int defaultResId = 0;
                // 根据 URL 的协议匹配对应的菜单文案
                for (String schema : sSchemaActionResMap.keySet()) {
                    if (urls[0].getURL().indexOf(schema) >= 0) {
                        defaultResId = sSchemaActionResMap.get(schema);
                        break;
                    }
                }

                if (defaultResId == 0) {
                    defaultResId = R.string.note_link_other;
                }

                menu.add(0, 0, 0, defaultResId).setOnMenuItemClickListener(
                        new OnMenuItemClickListener() {
                            public boolean onMenuItemClick(MenuItem item) {
                                // 执行链接的默认行为（打开浏览器、拨号等）
                                urls[0].onClick(NoteEditText.this);
                                return true;
                            }
                        });
            }
        }
        super.onCreateContextMenu(menu);
    }
}