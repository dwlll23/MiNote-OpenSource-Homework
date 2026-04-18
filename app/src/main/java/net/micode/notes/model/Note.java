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

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;

import java.util.ArrayList;

/**
 * 便签数据模型（用于数据库操作的封装）。
 *
 * 该类封装了一个便签的修改操作，包括：
 *     区分便签基本字段（{@link NoteColumns}）和详细数据（文本内容、通话记录）的修改
 *     提供静态方法 {@link #getNewNoteId} 在数据库中创建新便签并返回 ID
 *     通过 {@link #syncNote} 将本地修改同步到数据库（支持批量操作）
 *
 * 内部类 {@link NoteData} 负责管理文本数据和通话记录数据的 ContentValues，并执行实际的数据库插入/更新。
 *
 */
public class Note {
    private ContentValues mNoteDiffValues;   // 便签基本字段的修改（如父文件夹、提醒时间等）
    private NoteData mNoteData;              // 便签的详细数据（文本内容 / 通话记录）
    private static final String TAG = "Note";

    /**
     * 在数据库中创建一个新的空白便签，并返回其 ID。
     *
     * @param context  上下文
     * @param folderId 所属文件夹 ID
     * @return 新创建的便签 ID（大于 0）
     * @throws IllegalStateException 如果创建失败或返回的 ID 无效
     */
    public static synchronized long getNewNoteId(Context context, long folderId) {
        // 向 note 表插入一条新记录
        ContentValues values = new ContentValues();
        long createdTime = System.currentTimeMillis();
        values.put(NoteColumns.CREATED_DATE, createdTime);
        values.put(NoteColumns.MODIFIED_DATE, createdTime);
        values.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
        values.put(NoteColumns.LOCAL_MODIFIED, 1);
        values.put(NoteColumns.PARENT_ID, folderId);
        Uri uri = context.getContentResolver().insert(Notes.CONTENT_NOTE_URI, values);

        long noteId = 0;
        try {
            noteId = Long.valueOf(uri.getPathSegments().get(1));
        } catch (NumberFormatException e) {
            Log.e(TAG, "Get note id error :" + e.toString());
            noteId = 0;
        }
        if (noteId == -1) {
            throw new IllegalStateException("Wrong note id:" + noteId);
        }
        return noteId;
    }

    public Note() {
        mNoteDiffValues = new ContentValues();
        mNoteData = new NoteData();
    }

    /**
     * 设置便签基本字段的值（如父文件夹 ID、提醒时间等）。
     * 同时自动标记本地修改标志和修改时间。
     *
     * @param key   字段名（使用 {@link NoteColumns} 中的常量）
     * @param value 字段值
     */
    public void setNoteValue(String key, String value) {
        mNoteDiffValues.put(key, value);
        mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
        mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
    }

    /**
     * 设置文本数据（便签正文）的字段值。
     *
     * @param key   字段名（通常为 {@link DataColumns#CONTENT}）
     * @param value 文本内容
     */
    public void setTextData(String key, String value) {
        mNoteData.setTextData(key, value);
    }

    public void setTextDataId(long id) {
        mNoteData.setTextDataId(id);
    }

    public long getTextDataId() {
        return mNoteData.mTextDataId;
    }

    public void setCallDataId(long id) {
        mNoteData.setCallDataId(id);
    }

    /**
     * 设置通话记录数据的字段值。
     *
     * @param key   字段名（如 {@link CallNote#CALL_DATE} 或 {@link CallNote#PHONE_NUMBER}）
     * @param value 对应的值
     */
    public void setCallData(String key, String value) {
        mNoteData.setCallData(key, value);
    }

    /**
     * 判断便签是否有本地未同步的修改。
     *
     * @return true 表示有修改（基本字段或详细数据有变化）
     */
    public boolean isLocalModified() {
        return mNoteDiffValues.size() > 0 || mNoteData.isLocalModified();
    }

    /**
     * 将本地修改同步到数据库。
     * 先更新 note 表的基本字段，再通过 NoteData 更新 data 表中的详细内容。
     *
     * @param context 上下文
     * @param noteId  便签 ID
     * @return true 同步成功，false 失败（详细数据写入出错）
     */
    public boolean syncNote(Context context, long noteId) {
        if (noteId <= 0) {
            throw new IllegalArgumentException("Wrong note id:" + noteId);
        }

        if (!isLocalModified()) {
            return true;
        }

        /**
         * 更新 note 表的基本字段。理论上只要数据变化，LOCAL_MODIFIED 和 MODIFIED_DATE 应已更新。
         * 即使更新失败，仍继续处理详细数据，以保证数据安全。
         */
        if (context.getContentResolver().update(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), mNoteDiffValues, null,
                null) == 0) {
            Log.e(TAG, "Update note error, should not happen");
            // 不返回，继续执行后续操作
        }
        mNoteDiffValues.clear();

        if (mNoteData.isLocalModified()
                && (mNoteData.pushIntoContentResolver(context, noteId) == null)) {
            return false;
        }

        return true;
    }

    /**
     * 便签详细数据（文本内容和通话记录）的封装类。
     */
    private class NoteData {
        private long mTextDataId;           // 文本数据在 data 表中的 ID
        private ContentValues mTextDataValues; // 文本数据的待修改字段
        private long mCallDataId;           // 通话记录数据在 data 表中的 ID
        private ContentValues mCallDataValues; // 通话记录数据的待修改字段

        private static final String TAG = "NoteData";

        public NoteData() {
            mTextDataValues = new ContentValues();
            mCallDataValues = new ContentValues();
            mTextDataId = 0;
            mCallDataId = 0;
        }

        boolean isLocalModified() {
            return mTextDataValues.size() > 0 || mCallDataValues.size() > 0;
        }

        void setTextDataId(long id) {
            if (id <= 0) {
                throw new IllegalArgumentException("Text data id should larger than 0");
            }
            mTextDataId = id;
        }

        void setCallDataId(long id) {
            if (id <= 0) {
                throw new IllegalArgumentException("Call data id should larger than 0");
            }
            mCallDataId = id;
        }

        void setCallData(String key, String value) {
            mCallDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        void setTextData(String key, String value) {
            mTextDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /**
         * 将详细数据的修改提交到数据库（data 表）。
         * 若某类数据（文本/通话）尚无对应的 data 记录，则插入新行；否则更新已有行。
         * 支持批量操作（使用 ContentProviderOperation）以提高效率。
         *
         * @param context 上下文
         * @param noteId  所属便签 ID
         * @return 成功返回便签的 Uri，失败返回 null
         */
        Uri pushIntoContentResolver(Context context, long noteId) {
            if (noteId <= 0) {
                throw new IllegalArgumentException("Wrong note id:" + noteId);
            }

            ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
            ContentProviderOperation.Builder builder = null;

            // 处理文本数据
            if (mTextDataValues.size() > 0) {
                mTextDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mTextDataId == 0) {
                    // 新建文本数据行
                    mTextDataValues.put(DataColumns.MIME_TYPE, TextNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mTextDataValues);
                    try {
                        setTextDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new text data fail with noteId" + noteId);
                        mTextDataValues.clear();
                        return null;
                    }
                } else {
                    // 更新已有文本数据
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mTextDataId));
                    builder.withValues(mTextDataValues);
                    operationList.add(builder.build());
                }
                mTextDataValues.clear();
            }

            // 处理通话记录数据
            if (mCallDataValues.size() > 0) {
                mCallDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mCallDataId == 0) {
                    // 新建通话记录数据行
                    mCallDataValues.put(DataColumns.MIME_TYPE, CallNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mCallDataValues);
                    try {
                        setCallDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new call data fail with noteId" + noteId);
                        mCallDataValues.clear();
                        return null;
                    }
                } else {
                    // 更新已有通话记录数据
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mCallDataId));
                    builder.withValues(mCallDataValues);
                    operationList.add(builder.build());
                }
                mCallDataValues.clear();
            }

            // 执行批量操作
            if (operationList.size() > 0) {
                try {
                    ContentProviderResult[] results = context.getContentResolver().applyBatch(
                            Notes.AUTHORITY, operationList);
                    return (results == null || results.length == 0 || results[0] == null) ? null
                            : ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                } catch (RemoteException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                } catch (OperationApplicationException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                }
            }
            return null;
        }
    }
}