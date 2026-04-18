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
import android.database.Cursor;
import android.text.TextUtils;

import net.micode.notes.data.Contact;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.DataUtils;

/**
 * 便签列表项的数据模型，封装从数据库游标中读取的便签或文件夹的完整信息。
 *
 * 该类用于 {@link NotesListAdapter} 中，为每个列表项提供所需的数据，
 * 包括 ID、提醒时间、背景色、创建/修改时间、摘要、类型等，并计算其在列表中的位置信息
 * （是否首项、末项、单项，以及是否跟在文件夹后的第一个便签等），以便列表项 UI 使用不同的背景资源。
 *
 * 对于通话记录文件夹下的便签，还会查询关联的联系人姓名和电话号码。
 *
 */
public class NoteItemData {
    // 查询 note 表时使用的投影列（包含列表显示所需的所有字段）
    static final String[] PROJECTION = new String[]{
            NoteColumns.ID,
            NoteColumns.ALERTED_DATE,
            NoteColumns.BG_COLOR_ID,
            NoteColumns.CREATED_DATE,
            NoteColumns.HAS_ATTACHMENT,
            NoteColumns.MODIFIED_DATE,
            NoteColumns.NOTES_COUNT,
            NoteColumns.PARENT_ID,
            NoteColumns.SNIPPET,
            NoteColumns.TYPE,
            NoteColumns.WIDGET_ID,
            NoteColumns.WIDGET_TYPE,
    };

    // 投影列索引常量
    private static final int ID_COLUMN = 0;
    private static final int ALERTED_DATE_COLUMN = 1;
    private static final int BG_COLOR_ID_COLUMN = 2;
    private static final int CREATED_DATE_COLUMN = 3;
    private static final int HAS_ATTACHMENT_COLUMN = 4;
    private static final int MODIFIED_DATE_COLUMN = 5;
    private static final int NOTES_COUNT_COLUMN = 6;
    private static final int PARENT_ID_COLUMN = 7;
    private static final int SNIPPET_COLUMN = 8;
    private static final int TYPE_COLUMN = 9;
    private static final int WIDGET_ID_COLUMN = 10;
    private static final int WIDGET_TYPE_COLUMN = 11;

    // 便签基本属性
    private long mId;
    private long mAlertDate;
    private int mBgColorId;
    private long mCreatedDate;
    private boolean mHasAttachment;
    private long mModifiedDate;
    private int mNotesCount;          // 仅对文件夹有效，表示其中便签数量
    private long mParentId;           // 父文件夹 ID
    private String mSnippet;          // 摘要（便签内容的前缀或文件夹名称）
    private int mType;                // 类型：NOTE / FOLDER / SYSTEM
    private int mWidgetId;            // 关联的小部件 ID
    private int mWidgetType;          // 小部件类型

    private String mName;             // 通话记录便签的联系人姓名（若存在）
    private String mPhoneNumber;      // 通话记录便签的电话号码

    // 位置信息，用于决定列表项的背景样式（圆角等）
    private boolean mIsLastItem;
    private boolean mIsFirstItem;
    private boolean mIsOnlyOneItem;
    private boolean mIsOneNoteFollowingFolder;      // 是否为一个文件夹后仅跟一个便签
    private boolean mIsMultiNotesFollowingFolder;   // 是否为一个文件夹后跟多个便签

    /**
     * 从数据库游标构造 NoteItemData 对象。
     *
     * @param context 上下文（用于查询联系人信息）
     * @param cursor  指向 note 表当前行的游标（必须包含 PROJECTION 中的列）
     */
    public NoteItemData(Context context, Cursor cursor) {
        mId = cursor.getLong(ID_COLUMN);
        mAlertDate = cursor.getLong(ALERTED_DATE_COLUMN);
        mBgColorId = cursor.getInt(BG_COLOR_ID_COLUMN);
        mCreatedDate = cursor.getLong(CREATED_DATE_COLUMN);
        mHasAttachment = (cursor.getInt(HAS_ATTACHMENT_COLUMN) > 0);
        mModifiedDate = cursor.getLong(MODIFIED_DATE_COLUMN);
        mNotesCount = cursor.getInt(NOTES_COUNT_COLUMN);
        mParentId = cursor.getLong(PARENT_ID_COLUMN);
        mSnippet = cursor.getString(SNIPPET_COLUMN);
        // 移除摘要中的复选框标记（✓ 和 □），避免在列表中显示
        mSnippet = mSnippet.replace(NoteEditActivity.TAG_CHECKED, "").replace(
                NoteEditActivity.TAG_UNCHECKED, "");
        mType = cursor.getInt(TYPE_COLUMN);
        mWidgetId = cursor.getInt(WIDGET_ID_COLUMN);
        mWidgetType = cursor.getInt(WIDGET_TYPE_COLUMN);

        mPhoneNumber = "";
        if (mParentId == Notes.ID_CALL_RECORD_FOLDER) {
            // 通话记录文件夹下的便签，查询电话号码和联系人姓名
            mPhoneNumber = DataUtils.getCallNumberByNoteId(context.getContentResolver(), mId);
            if (!TextUtils.isEmpty(mPhoneNumber)) {
                mName = Contact.getContact(context, mPhoneNumber);
                if (mName == null) {
                    mName = mPhoneNumber;
                }
            }
        }

        if (mName == null) {
            mName = "";
        }
        checkPostion(cursor);
    }

    /**
     * 检查当前项在列表中的位置关系（是否首项、末项、单项，以及是否跟在文件夹后的便签等）。
     * 这些信息用于列表项 UI 绘制时选择合适的背景资源（圆角位置）。
     *
     * @param cursor 指向当前行的游标（需要能够移动位置）
     */
    private void checkPostion(Cursor cursor) {
        mIsLastItem = cursor.isLast();
        mIsFirstItem = cursor.isFirst();
        mIsOnlyOneItem = (cursor.getCount() == 1);
        mIsMultiNotesFollowingFolder = false;
        mIsOneNoteFollowingFolder = false;

        if (mType == Notes.TYPE_NOTE && !mIsFirstItem) {
            int position = cursor.getPosition();
            if (cursor.moveToPrevious()) {
                // 检查前一项是否为文件夹
                if (cursor.getInt(TYPE_COLUMN) == Notes.TYPE_FOLDER
                        || cursor.getInt(TYPE_COLUMN) == Notes.TYPE_SYSTEM) {
                    if (cursor.getCount() > (position + 1)) {
                        mIsMultiNotesFollowingFolder = true;   // 文件夹后跟多个便签
                    } else {
                        mIsOneNoteFollowingFolder = true;       // 文件夹后只跟一个便签
                    }
                }
                // 将游标移回原位置
                if (!cursor.moveToNext()) {
                    throw new IllegalStateException("cursor move to previous but can't move back");
                }
            }
        }
    }


    public boolean isOneFollowingFolder() {
        return mIsOneNoteFollowingFolder;
    }

    public boolean isMultiFollowingFolder() {
        return mIsMultiNotesFollowingFolder;
    }

    public boolean isLast() {
        return mIsLastItem;
    }

    public String getCallName() {
        return mName;
    }

    public boolean isFirst() {
        return mIsFirstItem;
    }

    public boolean isSingle() {
        return mIsOnlyOneItem;
    }

    public long getId() {
        return mId;
    }

    public long getAlertDate() {
        return mAlertDate;
    }

    public long getCreatedDate() {
        return mCreatedDate;
    }

    public boolean hasAttachment() {
        return mHasAttachment;
    }

    public long getModifiedDate() {
        return mModifiedDate;
    }

    public int getBgColorId() {
        return mBgColorId;
    }

    public long getParentId() {
        return mParentId;
    }

    public int getNotesCount() {
        return mNotesCount;
    }

    public long getFolderId() {
        return mParentId;
    }

    public int getType() {
        return mType;
    }

    public int getWidgetType() {
        return mWidgetType;
    }

    public int getWidgetId() {
        return mWidgetId;
    }

    public String getSnippet() {
        return mSnippet;
    }

    public boolean hasAlert() {
        return (mAlertDate > 0);
    }

    public boolean isCallRecord() {
        return (mParentId == Notes.ID_CALL_RECORD_FOLDER && !TextUtils.isEmpty(mPhoneNumber));
    }

    /**
     * 静态工具方法：从游标中获取便签类型（不创建完整对象）。
     *
     * @param cursor 指向 note 表行的游标（必须包含 TYPE 列）
     * @return 便签类型（TYPE_NOTE / TYPE_FOLDER / TYPE_SYSTEM）
     */
    public static int getNoteType(Cursor cursor) {
        return cursor.getInt(TYPE_COLUMN);
    }
}