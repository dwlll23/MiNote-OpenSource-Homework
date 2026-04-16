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

package net.micode.notes.gtask.data;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;
import net.micode.notes.gtask.exception.ActionFailureException;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * GTask 同步过程中对 data 表（便签详细内容）的操作封装。
 *
 * 该类负责将便签的详细数据（文本内容、MIME 类型、通用字段等）与 JSON 对象进行相互转换，
 * 并通过 ContentProvider 执行数据库的插入或更新操作。同时支持版本校验（乐观锁）以避免同步冲突。
 *
 * 主要功能：
 *     从数据库 Cursor 加载数据（{@link #loadFromCursor}）
 *     通过 JSON 设置数据，并记录差异字段（{@link #setContent(JSONObject)}）
 *     将当前对象转换为 JSON（{@link #getContent()}）
 *     提交差异到数据库（{@link #commit}），支持可选的版本校验
 *
 */
public class SqlData {
    private static final String TAG = SqlData.class.getSimpleName();

    private static final int INVALID_ID = -99999;   // 无效数据 ID

    // 查询 data 表时使用的投影列（ID, MIME_TYPE, CONTENT, DATA1, DATA3）
    public static final String[] PROJECTION_DATA = new String[] {
            DataColumns.ID, DataColumns.MIME_TYPE, DataColumns.CONTENT, DataColumns.DATA1,
            DataColumns.DATA3
    };

    // 投影列对应的索引常量
    public static final int DATA_ID_COLUMN = 0;
    public static final int DATA_MIME_TYPE_COLUMN = 1;
    public static final int DATA_CONTENT_COLUMN = 2;
    public static final int DATA_CONTENT_DATA_1_COLUMN = 3;
    public static final int DATA_CONTENT_DATA_3_COLUMN = 4;

    private ContentResolver mContentResolver;
    private boolean mIsCreate;          // 是否为新建对象（尚未插入数据库）
    private long mDataId;               // data 表行 ID
    private String mDataMimeType;       // MIME 类型（如文本便签或通话记录）
    private String mDataContent;        // 便签内容
    private long mDataContentData1;     // 通用数据列1（如 checklist 模式或通话日期）
    private String mDataContentData3;   // 通用数据列3（如电话号码）
    private ContentValues mDiffDataValues;  // 记录自上次提交后发生变化的字段

    /**
     * 创建一个新的空 SqlData 对象（用于插入新数据）。
     *
     * @param context 上下文，用于获取 ContentResolver
     */
    public SqlData(Context context) {
        mContentResolver = context.getContentResolver();
        mIsCreate = true;
        mDataId = INVALID_ID;
        mDataMimeType = DataConstants.NOTE;
        mDataContent = "";
        mDataContentData1 = 0;
        mDataContentData3 = "";
        mDiffDataValues = new ContentValues();
    }

    /**
     * 从数据库游标加载数据，创建一个已存在的 SqlData 对象（用于更新）。
     *
     * @param context 上下文
     * @param c       指向 data 表某行的游标（必须包含 PROJECTION_DATA 中的列）
     */
    public SqlData(Context context, Cursor c) {
        mContentResolver = context.getContentResolver();
        mIsCreate = false;
        loadFromCursor(c);
        mDiffDataValues = new ContentValues();
    }

    /**
     * 从游标中读取当前对象的字段值。
     */
    private void loadFromCursor(Cursor c) {
        mDataId = c.getLong(DATA_ID_COLUMN);
        mDataMimeType = c.getString(DATA_MIME_TYPE_COLUMN);
        mDataContent = c.getString(DATA_CONTENT_COLUMN);
        mDataContentData1 = c.getLong(DATA_CONTENT_DATA_1_COLUMN);
        mDataContentData3 = c.getString(DATA_CONTENT_DATA_3_COLUMN);
    }

    /**
     * 通过 JSON 对象设置当前数据对象的内容。
     * 比较新旧值，将差异记录到 mDiffDataValues 中，供后续 commit 使用。
     *
     * @param js 包含 data 表字段的 JSON 对象
     * @throws JSONException 解析 JSON 时出错
     */
    public void setContent(JSONObject js) throws JSONException {
        long dataId = js.has(DataColumns.ID) ? js.getLong(DataColumns.ID) : INVALID_ID;
        if (mIsCreate || mDataId != dataId) {
            mDiffDataValues.put(DataColumns.ID, dataId);
        }
        mDataId = dataId;

        String dataMimeType = js.has(DataColumns.MIME_TYPE) ? js.getString(DataColumns.MIME_TYPE)
                : DataConstants.NOTE;
        if (mIsCreate || !mDataMimeType.equals(dataMimeType)) {
            mDiffDataValues.put(DataColumns.MIME_TYPE, dataMimeType);
        }
        mDataMimeType = dataMimeType;

        String dataContent = js.has(DataColumns.CONTENT) ? js.getString(DataColumns.CONTENT) : "";
        if (mIsCreate || !mDataContent.equals(dataContent)) {
            mDiffDataValues.put(DataColumns.CONTENT, dataContent);
        }
        mDataContent = dataContent;

        long dataContentData1 = js.has(DataColumns.DATA1) ? js.getLong(DataColumns.DATA1) : 0;
        if (mIsCreate || mDataContentData1 != dataContentData1) {
            mDiffDataValues.put(DataColumns.DATA1, dataContentData1);
        }
        mDataContentData1 = dataContentData1;

        String dataContentData3 = js.has(DataColumns.DATA3) ? js.getString(DataColumns.DATA3) : "";
        if (mIsCreate || !mDataContentData3.equals(dataContentData3)) {
            mDiffDataValues.put(DataColumns.DATA3, dataContentData3);
        }
        mDataContentData3 = dataContentData3;
    }

    /**
     * 将当前对象的数据转换为 JSON 对象。
     *
     * @return 包含 data 表字段的 JSON 对象
     * @throws JSONException 构造 JSON 时出错
     */
    public JSONObject getContent() throws JSONException {
        if (mIsCreate) {
            Log.e(TAG, "it seems that we haven't created this in database yet");
            return null;
        }
        JSONObject js = new JSONObject();
        js.put(DataColumns.ID, mDataId);
        js.put(DataColumns.MIME_TYPE, mDataMimeType);
        js.put(DataColumns.CONTENT, mDataContent);
        js.put(DataColumns.DATA1, mDataContentData1);
        js.put(DataColumns.DATA3, mDataContentData3);
        return js;
    }

    /**
     * 将差异数据提交到数据库。
     * 如果对象为新建状态，则执行插入；否则执行更新。
     *
     * @param noteId         关联的便签 ID（note 表的主键）
     * @param validateVersion 是否启用版本校验（乐观锁）
     * @param version        期望的版本号（仅当 validateVersion 为 true 时有效）
     * @throws ActionFailureException 插入或更新失败时抛出
     */
    public void commit(long noteId, boolean validateVersion, long version) {

        if (mIsCreate) {
            // 新建时，如果 ID 为无效值但 ContentValues 中又包含 ID 字段，则移除该字段（让数据库自动生成）
            if (mDataId == INVALID_ID && mDiffDataValues.containsKey(DataColumns.ID)) {
                mDiffDataValues.remove(DataColumns.ID);
            }

            mDiffDataValues.put(DataColumns.NOTE_ID, noteId);
            Uri uri = mContentResolver.insert(Notes.CONTENT_DATA_URI, mDiffDataValues);
            try {
                mDataId = Long.valueOf(uri.getPathSegments().get(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Get note id error :" + e.toString());
                throw new ActionFailureException("create note failed");
            }
        } else {
            if (mDiffDataValues.size() > 0) {
                int result = 0;
                if (!validateVersion) {
                    // 无版本校验，直接更新
                    result = mContentResolver.update(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues, null, null);
                } else {
                    // 带版本校验：要求关联的 note 记录版本号与传入的 version 一致，否则更新失败（乐观锁）
                    result = mContentResolver.update(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues,
                            " ? in (SELECT " + NoteColumns.ID + " FROM " + TABLE.NOTE
                                    + " WHERE " + NoteColumns.VERSION + "=?)", new String[] {
                                    String.valueOf(noteId), String.valueOf(version)
                            });
                }
                if (result == 0) {
                    Log.w(TAG, "there is no update. maybe user updates note when syncing");
                }
            }
        }

        // 清空差异记录，并将对象标记为已存在
        mDiffDataValues.clear();
        mIsCreate = false;
    }

    /**
     * 获取 data 表行 ID。
     */
    public long getId() {
        return mDataId;
    }
}