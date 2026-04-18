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

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.model.WorkingNote.NoteSettingChangedListener;
import net.micode.notes.tool.DataUtils;
import android.database.Cursor;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.tool.ResourceParser.TextAppearanceResources;
import net.micode.notes.ui.DateTimePickerDialog.OnDateTimeSetListener;
import net.micode.notes.ui.NoteEditText.OnTextViewChangeListener;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 便签编辑界面（核心 Activity）。
 *
 * 主要功能：
 *     创建新便签 / 编辑已有便签
 *     支持普通文本模式和 checklist 任务列表模式
 *     设置背景颜色、字体大小、字体颜色
 *     设置提醒闹钟
 *     分享、删除、发送到桌面快捷方式
 *     通过系统搜索框（ACTION_SEARCH）打开匹配的便签，并支持模糊搜索高亮
 *
 * 数据模型：通过 {@link WorkingNote} 封装便签的数据库操作和状态变更。
 * 与 {@link NotesListActivity} 的关系：列表页点击便签时通过 Intent 启动本 Activity；
 * 保存后返回 RESULT_OK，通知列表页刷新。
 *
 */
public class NoteEditActivity extends Activity implements OnClickListener,
        NoteSettingChangedListener, OnTextViewChangeListener {

    // 用于缓存标题栏控件的 ViewHolder
    private class HeadViewHolder {
        public TextView tvModified;      // 最后修改时间
        public ImageView ivAlertIcon;    // 提醒图标
        public TextView tvAlertDate;     // 提醒时间文字
        public ImageView ibSetBgColor;   // 设置背景颜色按钮
    }

    // 背景颜色选择按钮映射（控件 ID -> 颜色常量）
    private static final Map<Integer, Integer> sBgSelectorBtnsMap = new HashMap<Integer, Integer>();
    static {
        sBgSelectorBtnsMap.put(R.id.iv_bg_yellow, ResourceParser.YELLOW);
        sBgSelectorBtnsMap.put(R.id.iv_bg_red, ResourceParser.RED);
        sBgSelectorBtnsMap.put(R.id.iv_bg_blue, ResourceParser.BLUE);
        sBgSelectorBtnsMap.put(R.id.iv_bg_green, ResourceParser.GREEN);
        sBgSelectorBtnsMap.put(R.id.iv_bg_white, ResourceParser.WHITE);
    }

    // 背景颜色选中指示器映射（颜色常量 -> 选中图标 ID）
    private static final Map<Integer, Integer> sBgSelectorSelectionMap = new HashMap<Integer, Integer>();
    static {
        sBgSelectorSelectionMap.put(ResourceParser.YELLOW, R.id.iv_bg_yellow_select);
        sBgSelectorSelectionMap.put(ResourceParser.RED, R.id.iv_bg_red_select);
        sBgSelectorSelectionMap.put(ResourceParser.BLUE, R.id.iv_bg_blue_select);
        sBgSelectorSelectionMap.put(ResourceParser.GREEN, R.id.iv_bg_green_select);
        sBgSelectorSelectionMap.put(ResourceParser.WHITE, R.id.iv_bg_white_select);
    }

    // 字体大小选择按钮映射
    private static final Map<Integer, Integer> sFontSizeBtnsMap = new HashMap<Integer, Integer>();
    static {
        sFontSizeBtnsMap.put(R.id.ll_font_large, ResourceParser.TEXT_LARGE);
        sFontSizeBtnsMap.put(R.id.ll_font_small, ResourceParser.TEXT_SMALL);
        sFontSizeBtnsMap.put(R.id.ll_font_normal, ResourceParser.TEXT_MEDIUM);
        sFontSizeBtnsMap.put(R.id.ll_font_super, ResourceParser.TEXT_SUPER);
    }

    // 字体大小选中指示器映射
    private static final Map<Integer, Integer> sFontSelectorSelectionMap = new HashMap<Integer, Integer>();
    static {
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_LARGE, R.id.iv_large_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SMALL, R.id.iv_small_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_MEDIUM, R.id.iv_medium_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SUPER, R.id.iv_super_select);
    }

    // 字体颜色选择按钮映射（控件 ID -> 颜色 ID）
    private static final Map<Integer, Integer> sFontColorBtnsMap = new HashMap<Integer, Integer>();
    static {
        sFontColorBtnsMap.put(R.id.iv_font_black, 0);
        sFontColorBtnsMap.put(R.id.iv_font_gray, 1);
        sFontColorBtnsMap.put(R.id.iv_font_blue, 2);
        sFontColorBtnsMap.put(R.id.iv_font_green, 3);
        sFontColorBtnsMap.put(R.id.iv_font_red, 4);
    }

    // 字体颜色选中指示器映射
    private static final Map<Integer, Integer> sFontColorSelectionMap = new HashMap<Integer, Integer>();
    static {
        sFontColorSelectionMap.put(0, R.id.iv_font_black_select);
        sFontColorSelectionMap.put(1, R.id.iv_font_gray_select);
        sFontColorSelectionMap.put(2, R.id.iv_font_blue_select);
        sFontColorSelectionMap.put(3, R.id.iv_font_green_select);
        sFontColorSelectionMap.put(4, R.id.iv_font_red_select);
    }

    private static final String TAG = "NoteEditActivity";

    private HeadViewHolder mNoteHeaderHolder;
    private View mHeadViewPanel;
    private View mNoteBgColorSelector;      // 背景颜色选择器面板
    private View mFontColorSelector;        // 字体颜色选择器面板
    private View mFontSizeSelector;         // 字体大小选择器面板
    private EditText mNoteEditor;           // 普通文本编辑器
    private View mNoteEditorPanel;          // 编辑器面板（用于设置背景）
    private WorkingNote mWorkingNote;       // 当前便签的数据模型
    private SharedPreferences mSharedPrefs; // 偏好设置（存储字体大小等）
    private int mFontSizeId;                // 当前字体大小 ID

    private static final String PREFERENCE_FONT_SIZE = "pref_font_size";
    private static final int SHORTCUT_ICON_TITLE_MAX_LEN = 10;

    public static final String TAG_CHECKED = String.valueOf('\u221A');   // 复选框选中标记 "√"
    public static final String TAG_UNCHECKED = String.valueOf('\u25A1'); // 复选框未选中标记 "□"

    private LinearLayout mEditTextList;     // checklist 模式下的编辑框列表容器

    private String mUserQuery;              // 从搜索跳转时传入的查询词（用于高亮）
    private Pattern mPattern;               // 用于高亮匹配的正则模式
    private long[] mSearchResultIds;        // 搜索结果便签 ID 列表
    private String[] mSearchResultSnippets; // 搜索结果便签摘要列表

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.note_edit);

        if (savedInstanceState == null && !initActivityState(getIntent())) {
            finish();
            return;
        }
        initResources();
    }

    /**
     * 当 Activity 因内存不足被系统杀死后，恢复状态时调用。
     * 从 savedInstanceState 中取出 noteId，重新加载便签。
     */
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null && savedInstanceState.containsKey(Intent.EXTRA_UID)) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.putExtra(Intent.EXTRA_UID, savedInstanceState.getLong(Intent.EXTRA_UID));
            if (!initActivityState(intent)) {
                finish();
                return;
            }
            Log.d(TAG, "Restoring from killed activity");
        }
    }

    /**
     * 根据 Intent 初始化 Activity 状态（加载已有便签、创建新便签或处理搜索）。
     * 支持的动作：ACTION_VIEW（打开指定便签）、ACTION_INSERT_OR_EDIT（新建）、ACTION_SEARCH（搜索）。
     *
     * @param intent 启动本 Activity 的 Intent
     * @return true 初始化成功，false 失败（会关闭 Activity）
     */
    private boolean initActivityState(Intent intent) {
        mWorkingNote = null;
        if (TextUtils.equals(Intent.ACTION_VIEW, intent.getAction())) {
            long noteId = intent.getLongExtra(Intent.EXTRA_UID, 0);
            mUserQuery = "";

            // 从搜索结果跳转过来的处理
            if (intent.hasExtra(SearchManager.EXTRA_DATA_KEY)) {
                noteId = Long.parseLong(intent.getStringExtra(SearchManager.EXTRA_DATA_KEY));
                mUserQuery = intent.getStringExtra(SearchManager.USER_QUERY);
            }

            if (!DataUtils.visibleInNoteDatabase(getContentResolver(), noteId, Notes.TYPE_NOTE)) {
                Intent jump = new Intent(this, NotesListActivity.class);
                startActivity(jump);
                showToast(R.string.error_note_not_exist);
                finish();
                return false;
            } else {
                mWorkingNote = WorkingNote.load(this, noteId);
                if (mWorkingNote == null) {
                    Log.e(TAG, "load note failed with note id" + noteId);
                    finish();
                    return false;
                }
            }
            getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        } else if (TextUtils.equals(Intent.ACTION_INSERT_OR_EDIT, intent.getAction())) {
            // 新建便签模式
            long folderId = intent.getLongExtra(Notes.INTENT_EXTRA_FOLDER_ID, 0);
            int widgetId = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            int widgetType = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_TYPE,
                    Notes.TYPE_WIDGET_INVALIDE);
            int bgResId = intent.getIntExtra(Notes.INTENT_EXTRA_BACKGROUND_ID,
                    ResourceParser.getDefaultBgId(this));

            // 处理来自通话记录的便签
            String phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            long callDate = intent.getLongExtra(Notes.INTENT_EXTRA_CALL_DATE, 0);
            if (callDate != 0 && phoneNumber != null) {
                if (TextUtils.isEmpty(phoneNumber)) {
                    Log.w(TAG, "The call record number is null");
                }
                long noteId;
                if ((noteId = DataUtils.getNoteIdByPhoneNumberAndCallDate(getContentResolver(),
                        phoneNumber, callDate)) > 0) {
                    mWorkingNote = WorkingNote.load(this, noteId);
                    if (mWorkingNote == null) {
                        Log.e(TAG, "load call note failed with note id" + noteId);
                        finish();
                        return false;
                    }
                } else {
                    mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId,
                            widgetType, bgResId);
                    mWorkingNote.convertToCallNote(phoneNumber, callDate);
                }
            } else {
                mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId, widgetType,
                        bgResId);
            }

            getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                            | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        } else if (TextUtils.equals(Intent.ACTION_SEARCH, intent.getAction())) {
            // 处理系统搜索框的 Intent：查询匹配的便签，弹出选择对话框
            String query = intent.getStringExtra(SearchManager.QUERY);
            mUserQuery = query;
            if (!TextUtils.isEmpty(query)) {
                Cursor c = null;
                try {
                    // 在 data 表中查找包含查询词的便签（LIKE 匹配）
                    c = getContentResolver().query(Notes.CONTENT_DATA_URI,
                            new String[] { net.micode.notes.data.Notes.DataColumns.NOTE_ID },
                            net.micode.notes.data.Notes.DataColumns.MIME_TYPE + "=? AND "
                                    + net.micode.notes.data.Notes.DataColumns.CONTENT + " LIKE ?",
                            new String[] { Notes.TextNote.CONTENT_ITEM_TYPE, "%" + query + "%" },
                            null);
                    if (c != null) {
                        java.util.ArrayList<Long> ids = new java.util.ArrayList<Long>();
                        while (c.moveToNext()) {
                            try {
                                ids.add(c.getLong(0));
                            } catch (IndexOutOfBoundsException e) {
                                Log.w(TAG, "failed to read note id from cursor");
                            }
                        }
                        if (!ids.isEmpty()) {
                            mSearchResultIds = new long[ids.size()];
                            mSearchResultSnippets = new String[ids.size()];
                            for (int i = 0; i < ids.size(); i++) {
                                long nid = ids.get(i);
                                mSearchResultIds[i] = nid;
                                try {
                                    mSearchResultSnippets[i] = DataUtils.getSnippetById(getContentResolver(), nid);
                                } catch (IllegalArgumentException ex) {
                                    mSearchResultSnippets[i] = "";
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "query for search suggestions failed", e);
                    mSearchResultIds = null;
                    mSearchResultSnippets = null;
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }
            }
            if (mSearchResultIds == null || mSearchResultIds.length == 0) {
                // 无匹配结果，创建一个空便签
                mWorkingNote = WorkingNote.createEmptyNote(this, Notes.ID_ROOT_FOLDER,
                        AppWidgetManager.INVALID_APPWIDGET_ID, Notes.TYPE_WIDGET_INVALIDE,
                        ResourceParser.getDefaultBgId(this));
            }
            // 如果有多个匹配结果，将在 onResume 中显示选择对话框
        } else {
            Log.e(TAG, "Intent not specified action, should not support");
            finish();
            return false;
        }
        mWorkingNote.setOnSettingStatusChangedListener(this);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mSearchResultIds != null) {
            try {
                showSearchResultsDialog();
            } catch (Exception e) {
                Log.e(TAG, "showSearchResultsDialog failed", e);
                mSearchResultIds = null;
                mSearchResultSnippets = null;
                initNoteScreen();
            }
        } else {
            initNoteScreen();
        }
    }

    /**
     * 显示搜索结果选择对话框，让用户从多个匹配的便签中选择一个打开。
     */
    private void showSearchResultsDialog() {
        if (mSearchResultIds == null || mSearchResultIds.length == 0) {
            mWorkingNote = WorkingNote.createEmptyNote(this, Notes.ID_ROOT_FOLDER,
                    AppWidgetManager.INVALID_APPWIDGET_ID, Notes.TYPE_WIDGET_INVALIDE,
                    ResourceParser.getDefaultBgId(this));
            initNoteScreen();
            mSearchResultIds = null;
            mSearchResultSnippets = null;
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final String[] items = mSearchResultSnippets;
        int count = items == null ? 0 : items.length;
        String title = getResources().getQuantityString(R.plurals.search_results_title, count,
                String.valueOf(count), mUserQuery == null ? "" : mUserQuery);
        builder.setTitle(title);
        builder.setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                long id = mSearchResultIds[which];
                mWorkingNote = WorkingNote.load(NoteEditActivity.this, id);
                if (mWorkingNote == null) {
                    mWorkingNote = WorkingNote.createEmptyNote(NoteEditActivity.this, Notes.ID_ROOT_FOLDER,
                            AppWidgetManager.INVALID_APPWIDGET_ID, Notes.TYPE_WIDGET_INVALIDE,
                            ResourceParser.getDefaultBgId(NoteEditActivity.this));
                }
                mSearchResultIds = null;
                mSearchResultSnippets = null;
                initNoteScreen();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                mWorkingNote = WorkingNote.createEmptyNote(NoteEditActivity.this, Notes.ID_ROOT_FOLDER,
                        AppWidgetManager.INVALID_APPWIDGET_ID, Notes.TYPE_WIDGET_INVALIDE,
                        ResourceParser.getDefaultBgId(NoteEditActivity.this));
                mSearchResultIds = null;
                mSearchResultSnippets = null;
                initNoteScreen();
            }
        });
        builder.setCancelable(false);
        builder.show();
    }

    /**
     * 初始化界面显示：设置字体、内容、背景色、修改时间、提醒头等。
     */
    private void initNoteScreen() {
        mNoteEditor.setTextAppearance(this, TextAppearanceResources
                .getTexAppearanceResource(mFontSizeId));
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            switchToListMode(mWorkingNote.getContent());
        } else {
            mNoteEditor.setText(getHighlightQueryResult(mWorkingNote.getContent(), mUserQuery));
            mNoteEditor.setSelection(mNoteEditor.getText().length());
        }
        // 隐藏所有颜色选择器的选中指示
        for (Integer id : sBgSelectorSelectionMap.keySet()) {
            findViewById(sBgSelectorSelectionMap.get(id)).setVisibility(View.GONE);
        }
        for (Integer id : sFontColorSelectionMap.keySet()) {
            findViewById(sFontColorSelectionMap.get(id)).setVisibility(View.GONE);
        }
        mHeadViewPanel.setBackgroundResource(mWorkingNote.getTitleBgResId());
        mNoteEditorPanel.setBackgroundResource(mWorkingNote.getBgColorResId());

        mNoteHeaderHolder.tvModified.setText(DateUtils.formatDateTime(this,
                mWorkingNote.getModifiedDate(), DateUtils.FORMAT_SHOW_DATE
                        | DateUtils.FORMAT_NUMERIC_DATE | DateUtils.FORMAT_SHOW_TIME
                        | DateUtils.FORMAT_SHOW_YEAR));

        showAlertHeader();
        onFontColorChanged();
    }

    /**
     * 显示提醒头部（提醒图标和相对时间），如果提醒已过期则显示“提醒已过期”。
     */
    private void showAlertHeader() {
        if (mWorkingNote.hasClockAlert()) {
            long time = System.currentTimeMillis();
            if (time > mWorkingNote.getAlertDate()) {
                mNoteHeaderHolder.tvAlertDate.setText(R.string.note_alert_expired);
            } else {
                mNoteHeaderHolder.tvAlertDate.setText(DateUtils.getRelativeTimeSpanString(
                        mWorkingNote.getAlertDate(), time, DateUtils.MINUTE_IN_MILLIS));
            }
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.VISIBLE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.VISIBLE);
        } else {
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.GONE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        initActivityState(intent);
    }

    /**
     * 保存当前便签状态（用于因内存不足而重建时恢复）。
     * 注意：对于新建的未保存便签，会先调用 saveNote() 生成 ID。
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }
        outState.putLong(Intent.EXTRA_UID, mWorkingNote.getNoteId());
        Log.d(TAG, "Save working note id: " + mWorkingNote.getNoteId() + " onSaveInstanceState");
    }

    /**
     * 拦截触摸事件：当颜色/字体选择器显示时，点击外部区域自动关闭。
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE
                && !inRangeOfView(mNoteBgColorSelector, ev)) {
            mNoteBgColorSelector.setVisibility(View.GONE);
            return true;
        }

        if (mFontColorSelector != null && mFontColorSelector.getVisibility() == View.VISIBLE
                && !inRangeOfView(mFontColorSelector, ev)) {
            mFontColorSelector.setVisibility(View.GONE);
            return true;
        }

        if (mFontSizeSelector.getVisibility() == View.VISIBLE
                && !inRangeOfView(mFontSizeSelector, ev)) {
            mFontSizeSelector.setVisibility(View.GONE);
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean inRangeOfView(View view, MotionEvent ev) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        int x = location[0];
        int y = location[1];
        if (ev.getX() < x
                || ev.getX() > (x + view.getWidth())
                || ev.getY() < y
                || ev.getY() > (y + view.getHeight())) {
            return false;
        }
        return true;
    }

    /**
     * 初始化所有 View 资源和监听器。
     */
    private void initResources() {
        mHeadViewPanel = findViewById(R.id.note_title);
        mNoteHeaderHolder = new HeadViewHolder();
        mNoteHeaderHolder.tvModified = (TextView) findViewById(R.id.tv_modified_date);
        mNoteHeaderHolder.ivAlertIcon = (ImageView) findViewById(R.id.iv_alert_icon);
        mNoteHeaderHolder.tvAlertDate = (TextView) findViewById(R.id.tv_alert_date);
        mNoteHeaderHolder.ibSetBgColor = (ImageView) findViewById(R.id.btn_set_bg_color);
        mNoteHeaderHolder.ibSetBgColor.setOnClickListener(this);
        mNoteEditor = (EditText) findViewById(R.id.note_edit_view);
        mNoteEditorPanel = findViewById(R.id.sv_note_edit);
        mNoteBgColorSelector = findViewById(R.id.note_bg_color_selector);
        for (int id : sBgSelectorBtnsMap.keySet()) {
            ImageView iv = (ImageView) findViewById(id);
            iv.setOnClickListener(this);
        }

        mFontColorSelector = findViewById(R.id.font_color_selector);
        for (int id : sFontColorBtnsMap.keySet()) {
            ImageView iv = (ImageView) findViewById(id);
            iv.setOnClickListener(this);
        }

        mFontSizeSelector = findViewById(R.id.font_size_selector);
        for (int id : sFontSizeBtnsMap.keySet()) {
            View view = findViewById(id);
            view.setOnClickListener(this);
        }
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mFontSizeId = mSharedPrefs.getInt(PREFERENCE_FONT_SIZE, ResourceParser.BG_DEFAULT_FONT_SIZE);
        // 防止存储的字体大小 ID 越界
        if (mFontSizeId >= TextAppearanceResources.getResourcesSize()) {
            mFontSizeId = ResourceParser.BG_DEFAULT_FONT_SIZE;
        }
        mEditTextList = (LinearLayout) findViewById(R.id.note_edit_list);

        findViewById(R.id.btn_checklist).setOnClickListener(this);
        findViewById(R.id.btn_reminder).setOnClickListener(this);
        findViewById(R.id.btn_set_font_color).setOnClickListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (saveNote()) {
            Log.d(TAG, "Note data was saved with length:" + mWorkingNote.getContent().length());
        }
        clearSettingState();
    }

    /**
     * 更新桌面小部件（如果有）。
     */
    private void updateWidget() {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x.class);
        } else if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x.class);
        } else {
            Log.e(TAG, "Unspported widget type");
            return;
        }

        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{
                mWorkingNote.getWidgetId()
        });

        sendBroadcast(intent);
        setResult(RESULT_OK, intent);
    }

    /**
     * 处理点击事件：背景色、字体色、字体大小、checklist 模式、提醒按钮等。
     */
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_set_bg_color) {
            mNoteBgColorSelector.setVisibility(View.VISIBLE);
            findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId()))
                    .setVisibility(View.VISIBLE);
        } else if (id == R.id.btn_set_font_color) {
            mFontColorSelector.setVisibility(View.VISIBLE);
            findViewById(sFontColorSelectionMap.get(mWorkingNote.getFontColorId()))
                    .setVisibility(View.VISIBLE);
        } else if (sBgSelectorBtnsMap.containsKey(id)) {
            findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId()))
                    .setVisibility(View.GONE);
            mWorkingNote.setBgColorId(sBgSelectorBtnsMap.get(id));
            mNoteBgColorSelector.setVisibility(View.GONE);
        } else if (sFontColorBtnsMap.containsKey(id)) {
            findViewById(sFontColorSelectionMap.get(mWorkingNote.getFontColorId()))
                    .setVisibility(View.GONE);
            mWorkingNote.setFontColorId(sFontColorBtnsMap.get(id));
            mFontColorSelector.setVisibility(View.GONE);
        } else if (sFontSizeBtnsMap.containsKey(id)) {
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.GONE);
            mFontSizeId = sFontSizeBtnsMap.get(id);
            mSharedPrefs.edit().putInt(PREFERENCE_FONT_SIZE, mFontSizeId).commit();
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.VISIBLE);
            if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
                getWorkingText();
                switchToListMode(mWorkingNote.getContent());
            } else {
                mNoteEditor.setTextAppearance(this,
                        TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
            }
            mFontSizeSelector.setVisibility(View.GONE);
        } else if (id == R.id.btn_checklist) {
            mWorkingNote.setCheckListMode(
                    mWorkingNote.getCheckListMode() == 0 ? TextNote.MODE_CHECK_LIST : 0);
        } else if (id == R.id.btn_reminder) {
            setReminder();
        }
    }

    @Override
    public void onBackPressed() {
        if (clearSettingState()) {
            return;
        }
        saveNote();
        super.onBackPressed();
    }

    /**
     * 关闭所有悬浮选择器（背景色、字体色、字体大小）。
     *
     * @return 如果有选择器被关闭则返回 true
     */
    private boolean clearSettingState() {
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE) {
            mNoteBgColorSelector.setVisibility(View.GONE);
            return true;
        } else if (mFontColorSelector != null && mFontColorSelector.getVisibility() == View.VISIBLE) {
            mFontColorSelector.setVisibility(View.GONE);
            return true;
        } else if (mFontSizeSelector.getVisibility() == View.VISIBLE) {
            mFontSizeSelector.setVisibility(View.GONE);
            return true;
        }
        return false;
    }

    /**
     * 实现 NoteSettingChangedListener：背景色改变时更新 UI。
     */
    public void onBackgroundColorChanged() {
        findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId()))
                .setVisibility(View.VISIBLE);
        mNoteEditorPanel.setBackgroundResource(mWorkingNote.getBgColorResId());
        mHeadViewPanel.setBackgroundResource(mWorkingNote.getTitleBgResId());
    }

    /**
     * 实现 NoteSettingChangedListener：字体颜色改变时更新编辑器文字颜色及 checklist 项颜色。
     */
    public void onFontColorChanged() {
        findViewById(sFontColorSelectionMap.get(mWorkingNote.getFontColorId()))
                .setVisibility(View.VISIBLE);

        int colorRes;
        switch (mWorkingNote.getFontColorId()) {
            case 1:
                colorRes = R.color.font_color_gray;
                break;
            case 2:
                colorRes = R.color.font_color_blue;
                break;
            case 3:
                colorRes = R.color.font_color_green;
                break;
            case 4:
                colorRes = R.color.font_color_red;
                break;
            case 0:
            default:
                colorRes = R.color.font_color_black;
                break;
        }

        int color = this.getResources().getColor(colorRes);
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            for (int i = 0; i < mEditTextList.getChildCount(); i++) {
                NoteEditText edit = (NoteEditText) mEditTextList.getChildAt(i)
                        .findViewById(R.id.et_edit_text);
                if (edit != null) {
                    edit.setTextColor(color);
                    edit.setTextAppearance(this,
                            TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
                }
            }
        } else {
            mNoteEditor.setTextColor(color);
            mNoteEditor.setTextAppearance(this,
                    TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (isFinishing()) {
            return true;
        }
        clearSettingState();
        menu.clear();
        if (mWorkingNote.getFolderId() == Notes.ID_CALL_RECORD_FOLDER) {
            getMenuInflater().inflate(R.menu.call_note_edit, menu);
        } else {
            getMenuInflater().inflate(R.menu.note_edit, menu);
        }
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_normal_mode);
        } else {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_list_mode);
        }
        if (mWorkingNote.hasClockAlert()) {
            menu.findItem(R.id.menu_alert).setVisible(false);
        } else {
            menu.findItem(R.id.menu_delete_remind).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.menu_new_note) {
            createNewNote();
        } else if (itemId == R.id.menu_delete) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.alert_title_delete));
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setMessage(getString(R.string.alert_message_delete_note));
            builder.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            deleteCurrentNote();
                            finish();
                        }
                    });
            builder.setNegativeButton(android.R.string.cancel, null);
            builder.show();
        } else if (itemId == R.id.menu_font_size) {
            mFontSizeSelector.setVisibility(View.VISIBLE);
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.VISIBLE);
        } else if (itemId == R.id.menu_list_mode) {
            mWorkingNote.setCheckListMode(
                    mWorkingNote.getCheckListMode() == 0 ? TextNote.MODE_CHECK_LIST : 0);
        } else if (itemId == R.id.menu_share) {
            getWorkingText();
            sendTo(this, mWorkingNote.getContent());
        } else if (itemId == R.id.menu_send_to_desktop) {
            sendToDesktop();
        } else if (itemId == R.id.menu_alert) {
            setReminder();
        } else if (itemId == R.id.menu_delete_remind) {
            mWorkingNote.setAlertDate(0, false);
        }

        return true;
    }

    private void setReminder() {
        DateTimePickerDialog d = new DateTimePickerDialog(this, System.currentTimeMillis());
        d.setOnDateTimeSetListener(new OnDateTimeSetListener() {
            public void OnDateTimeSet(AlertDialog dialog, long date) {
                mWorkingNote.setAlertDate(date, true);
            }
        });
        d.show();
    }

    /**
     * 通过 ACTION_SEND 分享便签内容。
     */
    private void sendTo(Context context, String info) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, info);
        intent.setType("text/plain");
        context.startActivity(intent);
    }

    private void createNewNote() {
        saveNote();
        finish();
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mWorkingNote.getFolderId());
        startActivity(intent);
    }

    private void deleteCurrentNote() {
        if (mWorkingNote.existInDatabase()) {
            HashSet<Long> ids = new HashSet<Long>();
            long id = mWorkingNote.getNoteId();
            if (id != Notes.ID_ROOT_FOLDER) {
                ids.add(id);
            } else {
                Log.d(TAG, "Wrong note id, should not happen");
            }
            if (!isSyncMode()) {
                if (!DataUtils.batchDeleteNotes(getContentResolver(), ids)) {
                    Log.e(TAG, "Delete Note error");
                }
            } else {
                if (!DataUtils.batchMoveToFolder(getContentResolver(), ids, Notes.ID_TRASH_FOLER)) {
                    Log.e(TAG, "Move notes to trash folder error, should not happens");
                }
            }
        }
        mWorkingNote.markDeleted(true);
    }

    private boolean isSyncMode() {
        return NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0;
    }

    /**
     * 实现 NoteSettingChangedListener：当闹钟设置变化时，更新 AlarmManager。
     */
    public void onClockAlertChanged(long date, boolean set) {
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }
        if (mWorkingNote.getNoteId() > 0) {
            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mWorkingNote.getNoteId()));

            int flags;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
            } else {
                flags = PendingIntent.FLAG_UPDATE_CURRENT;
            }

            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, flags);
            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            showAlertHeader();
            if (!set) {
                alarmManager.cancel(pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, date, pendingIntent);
            }
        } else {
            Log.e(TAG, "Clock alert setting error");
            showToast(R.string.error_note_empty_for_clock);
        }
    }

    public void onWidgetChanged() {
        updateWidget();
    }

    /**
     * 实现 OnTextViewChangeListener：在 checklist 模式下删除某一项时调用。
     */
    public void onEditTextDelete(int index, String text) {
        int childCount = mEditTextList.getChildCount();
        if (childCount == 1) {
            return;
        }

        for (int i = index + 1; i < childCount; i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text))
                    .setIndex(i - 1);
        }

        mEditTextList.removeViewAt(index);
        NoteEditText edit;
        if (index == 0) {
            edit = (NoteEditText) mEditTextList.getChildAt(0).findViewById(
                    R.id.et_edit_text);
        } else {
            edit = (NoteEditText) mEditTextList.getChildAt(index - 1).findViewById(
                    R.id.et_edit_text);
        }
        int length = edit.length();
        edit.append(text);
        edit.requestFocus();
        edit.setSelection(length);
    }

    /**
     * 实现 OnTextViewChangeListener：在 checklist 模式下按回车键新增一项时调用。
     */
    public void onEditTextEnter(int index, String text) {
        if (index > mEditTextList.getChildCount()) {
            Log.e(TAG, "Index out of mEditTextList boundrary, should not happen");
        }

        View view = getListItem(text, index);
        mEditTextList.addView(view, index);
        NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
        edit.requestFocus();
        edit.setSelection(0);
        for (int i = index + 1; i < mEditTextList.getChildCount(); i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text))
                    .setIndex(i);
        }
    }

    /**
     * 将普通文本转换为 checklist 列表模式。
     * 按换行符分割文本，每一行变成一个带复选框的编辑框。
     *
     * @param text 原始文本内容（可能包含 ✓ 和 □ 标记）
     */
    private void switchToListMode(String text) {
        mEditTextList.removeAllViews();
        String[] items = text.split("\n");
        int index = 0;
        for (String item : items) {
            if (!TextUtils.isEmpty(item)) {
                mEditTextList.addView(getListItem(item, index));
                index++;
            }
        }
        mEditTextList.addView(getListItem("", index));
        mEditTextList.getChildAt(index).findViewById(R.id.et_edit_text).requestFocus();

        mNoteEditor.setVisibility(View.GONE);
        mEditTextList.setVisibility(View.VISIBLE);
    }

    /**
     * 高亮显示文本中的搜索关键词（支持模糊匹配）。
     * 将用户输入的查询词转换为“a.*b.*c”形式的模糊正则表达式，不区分大小写。
     *
     * @param fullText  原始全文
     * @param userQuery 用户输入的查询词
     * @return 带高亮样式的 Spannable 对象
     */
    private Spannable getHighlightQueryResult(String fullText, String userQuery) {
        String safeText = fullText == null ? "" : fullText;
        SpannableString spannable = new SpannableString(safeText);
        if (!TextUtils.isEmpty(userQuery)) {
            try {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < userQuery.length(); i++) {
                    sb.append(Pattern.quote(String.valueOf(userQuery.charAt(i))));
                    if (i != userQuery.length() - 1) {
                        sb.append(".*");
                    }
                }
                mPattern = Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            } catch (Exception e) {
                mPattern = Pattern.compile(Pattern.quote(userQuery), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            }
            Matcher m = mPattern.matcher(safeText);
            int start = 0;
            while (m.find(start)) {
                spannable.setSpan(
                        new BackgroundColorSpan(this.getResources().getColor(
                                R.color.user_query_highlight)), m.start(), m.end(),
                        Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                start = m.end();
            }
        }
        return spannable;
    }

    /**
     * 创建 checklist 模式下的单个列表项视图（包含复选框和编辑框）。
     *
     * @param item  行的文本内容
     * @param index 当前行的索引
     * @return 填充好的视图
     */
    private View getListItem(String item, int index) {
        View view = LayoutInflater.from(this).inflate(R.layout.note_edit_list_item, null);
        final NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
        edit.setTextAppearance(this, TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
        CheckBox cb = (CheckBox) view.findViewById(R.id.cb_edit_item);
        cb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                } else {
                    edit.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
                }
            }
        });

        if (item.startsWith(TAG_CHECKED)) {
            cb.setChecked(true);
            edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            item = item.substring(TAG_CHECKED.length()).trim();
        } else if (item.startsWith(TAG_UNCHECKED)) {
            cb.setChecked(false);
            edit.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
            item = item.substring(TAG_UNCHECKED.length()).trim();
        }

        edit.setOnTextViewChangeListener(this);
        edit.setIndex(index);
        edit.setText(getHighlightQueryResult(item, mUserQuery));
        return view;
    }

    public void onTextChange(int index, boolean hasText) {
        if (index >= mEditTextList.getChildCount()) {
            Log.e(TAG, "Wrong index, should not happen");
            return;
        }
        if (hasText) {
            mEditTextList.getChildAt(index).findViewById(R.id.cb_edit_item).setVisibility(View.VISIBLE);
        } else {
            mEditTextList.getChildAt(index).findViewById(R.id.cb_edit_item).setVisibility(View.GONE);
        }
    }

    /**
     * 实现 NoteSettingChangedListener：当 checklist 模式切换时，重新构建 UI。
     */
    public void onCheckListModeChanged(int oldMode, int newMode) {
        if (newMode == TextNote.MODE_CHECK_LIST) {
            switchToListMode(mNoteEditor.getText().toString());
        } else {
            if (!getWorkingText()) {
                mWorkingNote.setWorkingText(mWorkingNote.getContent().replace(TAG_UNCHECKED + " ",
                        ""));
            }
            mNoteEditor.setText(getHighlightQueryResult(mWorkingNote.getContent(), mUserQuery));
            mEditTextList.setVisibility(View.GONE);
            mNoteEditor.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 从 UI 控件中获取当前便签内容，并同步到 mWorkingNote。
     *
     * @return 如果 checklist 模式下有任意项被勾选，返回 true
     */
    private boolean getWorkingText() {
        boolean hasChecked = false;
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mEditTextList.getChildCount(); i++) {
                View view = mEditTextList.getChildAt(i);
                NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
                if (!TextUtils.isEmpty(edit.getText())) {
                    if (((CheckBox) view.findViewById(R.id.cb_edit_item)).isChecked()) {
                        sb.append(TAG_CHECKED).append(" ").append(edit.getText()).append("\n");
                        hasChecked = true;
                    } else {
                        sb.append(TAG_UNCHECKED).append(" ").append(edit.getText()).append("\n");
                    }
                }
            }
            mWorkingNote.setWorkingText(sb.toString());
        } else {
            mWorkingNote.setWorkingText(mNoteEditor.getText().toString());
        }
        return hasChecked;
    }

    /**
     * 保存便签内容到数据库。
     *
     * @return true 保存成功，false 失败
     */
    private boolean saveNote() {
        getWorkingText();
        boolean saved = mWorkingNote.saveNote();
        if (saved) {
            setResult(RESULT_OK);
        }
        return saved;
    }

    /**
     * 添加桌面快捷方式。
     */
    private void sendToDesktop() {
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }

        if (mWorkingNote.getNoteId() > 0) {
            Intent sender = new Intent();
            Intent shortcutIntent = new Intent(this, NoteEditActivity.class);
            shortcutIntent.setAction(Intent.ACTION_VIEW);
            shortcutIntent.putExtra(Intent.EXTRA_UID, mWorkingNote.getNoteId());
            sender.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
            sender.putExtra(Intent.EXTRA_SHORTCUT_NAME,
                    makeShortcutIconTitle(mWorkingNote.getContent()));
            sender.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                    Intent.ShortcutIconResource.fromContext(this, R.drawable.icon_app));
            sender.putExtra("duplicate", true);
            sender.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
            showToast(R.string.info_note_enter_desktop);
            sendBroadcast(sender);
        } else {
            Log.e(TAG, "Send to desktop error");
            showToast(R.string.error_note_empty_for_send_to_desktop);
        }
    }

    /**
     * 生成桌面快捷方式的标题（截断过长内容，并去除复选框标记）。
     */
    private String makeShortcutIconTitle(String content) {
        content = content.replace(TAG_CHECKED, "");
        content = content.replace(TAG_UNCHECKED, "");
        return content.length() > SHORTCUT_ICON_TITLE_MAX_LEN
                ? content.substring(0, SHORTCUT_ICON_TITLE_MAX_LEN)
                : content;
    }

    private void showToast(int resId) {
        showToast(resId, Toast.LENGTH_SHORT);
    }

    private void showToast(int resId, int duration) {
        Toast.makeText(this, resId, duration).show();
    }
}