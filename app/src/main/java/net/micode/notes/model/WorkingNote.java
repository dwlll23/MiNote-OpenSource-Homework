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

package net.micode.notes.model;

import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.tool.ResourceParser.NoteBgResources;

/**
 * 当前正在编辑的便签的数据模型（工作副本）。
 *
 * 该类封装了一个便签在内存中的完整状态，包括：
 *     便签基本属性（ID、文件夹、背景色、提醒时间、修改时间、小部件信息等）
 *     便签内容（文本内容、checklist 模式、字体颜色）
 *     与底层 {@link Note} 对象的双向同步（通过 {@link #saveNote()} 持久化）
 *     提供修改各种属性的便捷方法，并在属性变化时通过 {@link NoteSettingChangedListener}
 *         通知 UI 刷新
 *
 * 创建方式：
 *     新建空白便签：{@link #createEmptyNote}
 *     从数据库加载已有便签：{@link #load}
 *
 */
public class WorkingNote {
    // 关联的底层 Note 对象（负责实际的数据库操作）
    private Note mNote;

    // 便签 ID（>0 表示已存在于数据库）
    private long mNoteId;

    // 便签的文本内容
    private String mContent;

    // 模式：0 普通文本，1 Checklist 模式
    private int mMode;

    // 字体颜色 ID
    private int mFontColorId;

    // 提醒时间（毫秒）

    private long mAlertDate;

    // 最后修改时间（毫秒）
    private long mModifiedDate;

    // 背景颜色 ID
    private int mBgColorId;

    // 关联的桌面小部件 ID
    private int mWidgetId;

    // 桌面小部件类型（2x2 或 4x4）
    private int mWidgetType;

    // 所属文件夹 ID
    private long mFolderId;

    private Context mContext;

    private static final String TAG = "WorkingNote";

    // 是否已被标记删除
    private boolean mIsDeleted;

    // 属性变化监听器（用于 UI 刷新）
    private NoteSettingChangedListener mNoteSettingStatusListener;

    // 查询 data 表时使用的投影列
    public static final String[] DATA_PROJECTION = new String[] {
            DataColumns.ID,
            DataColumns.CONTENT,
            DataColumns.MIME_TYPE,
            DataColumns.DATA1,
            DataColumns.DATA2,
            DataColumns.DATA3,
            DataColumns.DATA4,
    };

    // 查询 note 表时使用的投影列
    public static final String[] NOTE_PROJECTION = new String[] {
            NoteColumns.PARENT_ID,
            NoteColumns.ALERTED_DATE,
            NoteColumns.BG_COLOR_ID,
            NoteColumns.WIDGET_ID,
            NoteColumns.WIDGET_TYPE,
            NoteColumns.MODIFIED_DATE
    };

    // DATA_PROJECTION 列索引
    private static final int DATA_ID_COLUMN = 0;
    private static final int DATA_CONTENT_COLUMN = 1;
    private static final int DATA_MIME_TYPE_COLUMN = 2;
    private static final int DATA_MODE_COLUMN = 3;

    private static final int DATA_FONT_COLOR_COLUMN = 4;
  

    private static final int NOTE_PARENT_ID_COLUMN = 0;

    // NOTE_PROJECTION 列索引

    private static final int NOTE_PARENT_ID_COLUMN = 0;
    private static final int NOTE_ALERTED_DATE_COLUMN = 1;
    private static final int NOTE_BG_COLOR_ID_COLUMN = 2;
    private static final int NOTE_WIDGET_ID_COLUMN = 3;
    private static final int NOTE_WIDGET_TYPE_COLUMN = 4;
    private static final int NOTE_MODIFIED_DATE_COLUMN = 5;

    /**
     * 私有构造方法：创建一个新的空白便签（尚未保存到数据库）。
     *
     * @param context  上下文
     * @param folderId 所属文件夹 ID
     */
    private WorkingNote(Context context, long folderId) {
        mContext = context;
        mAlertDate = 0;
        mModifiedDate = System.currentTimeMillis();
        mFolderId = folderId;
        mNote = new Note();
        mNoteId = 0;
        mIsDeleted = false;
        mMode = 0;
        mFontColorId = 0;
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE;
    }

    /**
     * 私有构造方法：从数据库加载已有便签。
     *
     * @param context  上下文
     * @param noteId   便签 ID
     * @param folderId 所属文件夹 ID（此参数未在实现中使用，保留用于扩展）
     */
    private WorkingNote(Context context, long noteId, long folderId) {
        mContext = context;
        mNoteId = noteId;
        mFolderId = folderId;
        mIsDeleted = false;
        mNote = new Note();
        loadNote();
    }

    /**
     * 从数据库加载便签的基本字段（note 表）。
     */
    private void loadNote() {
        Cursor cursor = mContext.getContentResolver().query(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mNoteId), NOTE_PROJECTION, null,
                null, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                mFolderId = cursor.getLong(NOTE_PARENT_ID_COLUMN);
                mBgColorId = cursor.getInt(NOTE_BG_COLOR_ID_COLUMN);
                mWidgetId = cursor.getInt(NOTE_WIDGET_ID_COLUMN);
                mWidgetType = cursor.getInt(NOTE_WIDGET_TYPE_COLUMN);
                mAlertDate = cursor.getLong(NOTE_ALERTED_DATE_COLUMN);
                mModifiedDate = cursor.getLong(NOTE_MODIFIED_DATE_COLUMN);
            }
            cursor.close();
        } else {
            Log.e(TAG, "No note with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note with id " + mNoteId);
        }
        loadNoteData();
    }

    /**
     * 从数据库加载便签的详细数据（data 表），包括文本内容、模式、字体颜色等。
     */
    private void loadNoteData() {
        Cursor cursor = mContext.getContentResolver().query(Notes.CONTENT_DATA_URI, DATA_PROJECTION,
                DataColumns.NOTE_ID + "=?", new String[] {
                    String.valueOf(mNoteId)
                }, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    String type = cursor.getString(DATA_MIME_TYPE_COLUMN);
                    if (DataConstants.NOTE.equals(type)) {
                        mContent = cursor.getString(DATA_CONTENT_COLUMN);
                        mMode = cursor.getInt(DATA_MODE_COLUMN);
                        mFontColorId = cursor.getInt(DATA_FONT_COLOR_COLUMN);
                        mNote.setTextDataId(cursor.getLong(DATA_ID_COLUMN));
                    } else if (DataConstants.CALL_NOTE.equals(type)) {
                        mNote.setCallDataId(cursor.getLong(DATA_ID_COLUMN));
                    } else {
                        Log.d(TAG, "Wrong note type with type:" + type);
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();
        } else {
            Log.e(TAG, "No data with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note's data with id " + mNoteId);
        }
    }

    /**
     * 创建一个新的空白便签（未保存到数据库）。
     *
     * @param context           上下文
     * @param folderId          所属文件夹 ID
     * @param widgetId          关联的小部件 ID（可为 INVALID_APPWIDGET_ID）
     * @param widgetType        小部件类型
     * @param defaultBgColorId  默认背景颜色 ID
     * @return 新创建的 WorkingNote 对象
     */
    public static WorkingNote createEmptyNote(Context context, long folderId, int widgetId,
            int widgetType, int defaultBgColorId) {
        WorkingNote note = new WorkingNote(context, folderId);
        note.setBgColorId(defaultBgColorId);
        note.setWidgetId(widgetId);
        note.setWidgetType(widgetType);
        return note;
    }

    /**
     * 从数据库加载指定 ID 的便签。
     *
     * @param context 上下文
     * @param id      便签 ID
     * @return 加载完成的 WorkingNote 对象
     */
    public static WorkingNote load(Context context, long id) {
        return new WorkingNote(context, id, 0);
    }

    /**
     * 将当前工作副本保存到数据库。
     * 如果便签尚未存在，则先创建新记录；否则更新已有记录。
     *
     * @return true 保存成功，false 失败（无有效内容或保存出错）
     */
    public synchronized boolean saveNote() {
        if (isWorthSaving()) {
            if (!existInDatabase()) {
                if ((mNoteId = Note.getNewNoteId(mContext, mFolderId)) == 0) {
                    Log.e(TAG, "Create new note fail with id:" + mNoteId);
                    return false;
                }
            }

            mNote.syncNote(mContext, mNoteId);

            /**
             * 如果该便签关联了桌面小部件，则在保存后通知小部件更新内容
             */
            if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                    && mWidgetType != Notes.TYPE_WIDGET_INVALIDE
                    && mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onWidgetChanged();
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * 判断便签是否已存在于数据库中。
     *
     * @return true 已存在（mNoteId > 0）
     */
    public boolean existInDatabase() {
        return mNoteId > 0;
    }

    /**
     * 判断当前便签是否值得保存（即是否包含有效修改）。
     * 以下情况不保存：
     *     已标记删除
     *     新建且内容为空
     *     已存在但没有本地修改
     *
     * @return true 需要保存，false 无需保存
     */
    private boolean isWorthSaving() {
        if (mIsDeleted || (!existInDatabase() && TextUtils.isEmpty(mContent))
                || (existInDatabase() && !mNote.isLocalModified())) {
            return false;
        } else {
            return true;
        }
    }

    public void setOnSettingStatusChangedListener(NoteSettingChangedListener l) {
        mNoteSettingStatusListener = l;
    }

    /**
     * 设置提醒时间。
     *
     * @param date 提醒时间（毫秒），0 表示取消提醒
     * @param set  true 表示设置提醒，false 表示取消（此参数目前仅传递给监听器）
     */
    public void setAlertDate(long date, boolean set) {
        if (date != mAlertDate) {
            mAlertDate = date;
            mNote.setNoteValue(NoteColumns.ALERTED_DATE, String.valueOf(mAlertDate));
        }
        if (mNoteSettingStatusListener != null) {
            mNoteSettingStatusListener.onClockAlertChanged(date, set);
        }
    }

    /**
     * 标记便签为已删除（用于移动到废纸篓）。
     * 如果有关联的小部件，会触发小部件更新。
     *
     * @param mark true 标记删除
     */
    public void markDeleted(boolean mark) {
        mIsDeleted = mark;
        if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                && mWidgetType != Notes.TYPE_WIDGET_INVALIDE && mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onWidgetChanged();
        }
    }

    /**
     * 设置背景颜色 ID。
     *
     * @param id 颜色 ID（来自 ResourceParser）
     */
    public void setBgColorId(int id) {
        if (id != mBgColorId) {
            mBgColorId = id;
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onBackgroundColorChanged();
            }
            mNote.setNoteValue(NoteColumns.BG_COLOR_ID, String.valueOf(id));
        }
    }

    /**
     * 设置字体颜色 ID。
     *
     * @param id 颜色 ID
     */
    public void setFontColorId(int id) {
        if (id != mFontColorId) {
            mFontColorId = id;
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onFontColorChanged();
            }
            mNote.setTextData(TextNote.FONT_COLOR, String.valueOf(id));
        }
    }


    /**
     * 设置 Checklist 模式（普通文本 ↔ 任务列表模式）。
     *
     * @param mode 模式：0 普通，1 Checklist
     */

    public void setCheckListMode(int mode) {
        if (mMode != mode) {
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onCheckListModeChanged(mMode, mode);
            }
            mMode = mode;
            mNote.setTextData(TextNote.MODE, String.valueOf(mMode));
        }
    }

    public void setWidgetType(int type) {
        if (type != mWidgetType) {
            mWidgetType = type;
            mNote.setNoteValue(NoteColumns.WIDGET_TYPE, String.valueOf(mWidgetType));
        }
    }

    public void setWidgetId(int id) {
        if (id != mWidgetId) {
            mWidgetId = id;
            mNote.setNoteValue(NoteColumns.WIDGET_ID, String.valueOf(mWidgetId));
        }
    }

    /**
     * 设置便签的文本内容。
     *
     * @param text 新文本内容
     */
    public void setWorkingText(String text) {
        if (!TextUtils.equals(mContent, text)) {
            mContent = text;
            mNote.setTextData(DataColumns.CONTENT, mContent);
        }
    }

    /**
     * 将当前便签转换为通话记录便签（自动设置通话日期、电话号码，并移至通话记录文件夹）。
     *
     * @param phoneNumber 电话号码
     * @param callDate    通话日期（毫秒）
     */
    public void convertToCallNote(String phoneNumber, long callDate) {
        mNote.setCallData(CallNote.CALL_DATE, String.valueOf(callDate));
        mNote.setCallData(CallNote.PHONE_NUMBER, phoneNumber);
        mNote.setNoteValue(NoteColumns.PARENT_ID, String.valueOf(Notes.ID_CALL_RECORD_FOLDER));
    }


    public boolean hasClockAlert() {
        return (mAlertDate > 0 ? true : false);
    }

    public String getContent() {
        return mContent;
    }

    public long getAlertDate() {
        return mAlertDate;
    }

    public long getModifiedDate() {
        return mModifiedDate;
    }

    public int getBgColorResId() {
        return NoteBgResources.getNoteBgResource(mBgColorId);
    }

    public int getBgColorId() {
        return mBgColorId;
    }

    public int getFontColorId() {
        return mFontColorId;
    }

    public int getTitleBgResId() {
        return NoteBgResources.getNoteTitleBgResource(mBgColorId);
    }

    public int getCheckListMode() {
        return mMode;
    }

    public long getNoteId() {
        return mNoteId;
    }

    public long getFolderId() {
        return mFolderId;
    }

    public int getWidgetId() {
        return mWidgetId;
    }

    public int getWidgetType() {
        return mWidgetType;
    }

    /**
     * 便签属性变化监听器接口。
     * 用于在 WorkingNote 的某个属性发生改变时通知 UI 层进行相应更新。
     */
    public interface NoteSettingChangedListener {
        /**
         * 当便签背景颜色改变时调用。
         */
        void onBackgroundColorChanged();

        /**
         * Called when the font color of current note has just changed
         */
        void onFontColorChanged();

        /**
         * Called when user set clock

         * 当字体颜色改变时调用。
         */
        void onFontColorChanged();

        /**
         * 当提醒时间被设置或取消时调用。
         *
         * @param date 提醒时间（毫秒）
         * @param set  true 设置提醒，false 取消提醒

         */
        void onClockAlertChanged(long date, boolean set);

        /**
         * 当便签关联的小部件内容需要更新时调用（如保存后）。
         */
        void onWidgetChanged();

        /**
         * 当在普通模式和 Checklist 模式之间切换时调用。
         *
         * @param oldMode 切换前的模式
         * @param newMode 切换后的模式
         */
        void onCheckListModeChanged(int oldMode, int newMode);
    }
}