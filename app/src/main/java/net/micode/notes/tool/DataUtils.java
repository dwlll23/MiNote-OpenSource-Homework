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

package net.micode.notes.tool;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.os.RemoteException;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * 便签数据操作工具类。
 *
 * 提供对便签（note）和数据（data）的批量操作、查询、校验等辅助方法。
 * 主要包括：
 *     批量删除便签（{@link #batchDeleteNotes}）
 *     移动单个/批量便签到指定文件夹（{@link #moveNoteToFoler} / {@link #batchMoveToFolder}）
 *     查询用户文件夹数量、便签是否存在、文件夹名是否重复等
 *     获取与便签关联的小部件信息、通话记录号码等
 *     根据条件查找便签 ID 等</li>
 *
 */
public class DataUtils {
    public static final String TAG = "DataUtils";

    /**
     * 批量删除便签（支持多个 ID）。
     * 注意：根文件夹（ID_ROOT_FOLDER）不会被删除。
     *
     * @param resolver ContentResolver 实例
     * @param ids      要删除的便签 ID 集合
     * @return true 删除成功（或集合为空），false 删除失败
     */
    public static boolean batchDeleteNotes(ContentResolver resolver, HashSet<Long> ids) {
        if (ids == null) {
            Log.d(TAG, "the ids is null");
            return true;
        }
        if (ids.size() == 0) {
            Log.d(TAG, "no id is in the hashset");
            return true;
        }

        ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
        for (long id : ids) {
            if (id == Notes.ID_ROOT_FOLDER) {
                Log.e(TAG, "Don't delete system folder root");
                continue;
            }
            ContentProviderOperation.Builder builder = ContentProviderOperation
                    .newDelete(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id));
            operationList.add(builder.build());
        }
        try {
            ContentProviderResult[] results = resolver.applyBatch(Notes.AUTHORITY, operationList);
            if (results == null || results.length == 0 || results[0] == null) {
                Log.d(TAG, "delete notes failed, ids:" + ids.toString());
                return false;
            }
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
        return false;
    }

    /**
     * 将单个便签从一个文件夹移动到另一个文件夹。
     * 会记录原始父文件夹 ID（ORIGIN_PARENT_ID）并标记本地修改。
     *
     * @param resolver    ContentResolver 实例
     * @param id          便签 ID
     * @param srcFolderId 源文件夹 ID
     * @param desFolderId 目标文件夹 ID
     */
    public static void moveNoteToFoler(ContentResolver resolver, long id, long srcFolderId, long desFolderId) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.PARENT_ID, desFolderId);
        values.put(NoteColumns.ORIGIN_PARENT_ID, srcFolderId);
        values.put(NoteColumns.LOCAL_MODIFIED, 1);
        resolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id), values, null, null);
    }

    /**
     * 批量移动便签到指定文件夹。
     *
     * @param resolver ContentResolver 实例
     * @param ids      要移动的便签 ID 集合
     * @param folderId 目标文件夹 ID
     * @return true 移动成功（或集合为空），false 移动失败
     */
    public static boolean batchMoveToFolder(ContentResolver resolver, HashSet<Long> ids,
            long folderId) {
        if (ids == null) {
            Log.d(TAG, "the ids is null");
            return true;
        }

        ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
        for (long id : ids) {
            ContentProviderOperation.Builder builder = ContentProviderOperation
                    .newUpdate(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id));
            builder.withValue(NoteColumns.PARENT_ID, folderId);
            builder.withValue(NoteColumns.LOCAL_MODIFIED, 1);
            operationList.add(builder.build());
        }

        try {
            ContentProviderResult[] results = resolver.applyBatch(Notes.AUTHORITY, operationList);
            if (results == null || results.length == 0 || results[0] == null) {
                Log.d(TAG, "delete notes failed, ids:" + ids.toString());
                return false;
            }
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
        return false;
    }

    /**
     * 获取用户创建的文件夹数量（排除系统文件夹和废纸篓）。
     *
     * @param resolver ContentResolver 实例
     * @return 用户文件夹数量
     */
    public static int getUserFolderCount(ContentResolver resolver) {
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI,
                new String[] { "COUNT(*)" },
                NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                new String[] { String.valueOf(Notes.TYPE_FOLDER), String.valueOf(Notes.ID_TRASH_FOLER) },
                null);

        int count = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                try {
                    count = cursor.getInt(0);
                } catch (IndexOutOfBoundsException e) {
                    Log.e(TAG, "get folder count failed:" + e.toString());
                } finally {
                    cursor.close();
                }
            }
        }
        return count;
    }

    /**
     * 检查指定 ID 和类型的便签是否在可见范围内（即未被移入废纸篓）。
     *
     * @param resolver ContentResolver 实例
     * @param noteId   便签 ID
     * @param type     便签类型（NOTE 或 FOLDER）
     * @return true 存在且可见，false 不存在或已在废纸篓
     */
    public static boolean visibleInNoteDatabase(ContentResolver resolver, long noteId, int type) {
        Cursor cursor = resolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                null,
                NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER,
                new String[] { String.valueOf(type) },
                null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查指定 ID 的便签（或文件夹）是否存在于数据库中（包括废纸篓）。
     *
     * @param resolver ContentResolver 实例
     * @param noteId   便签 ID
     * @return true 存在，false 不存在
     */
    public static boolean existInNoteDatabase(ContentResolver resolver, long noteId) {
        Cursor cursor = resolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                null, null, null, null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查指定 ID 的 data 记录是否存在于数据库中。
     *
     * @param resolver ContentResolver 实例
     * @param dataId   data 表行 ID
     * @return true 存在，false 不存在
     */
    public static boolean existInDataDatabase(ContentResolver resolver, long dataId) {
        Cursor cursor = resolver.query(ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId),
                null, null, null, null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查可见范围内（非废纸篓）是否存在同名的文件夹。
     *
     * @param resolver ContentResolver 实例
     * @param name     文件夹名称
     * @return true 存在同名文件夹，false 不存在
     */
    public static boolean checkVisibleFolderName(ContentResolver resolver, String name) {
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI, null,
                NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER +
                        " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER +
                        " AND " + NoteColumns.SNIPPET + "=?",
                new String[] { name }, null);
        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 获取指定文件夹下所有便签关联的桌面小部件属性集合。
     *
     * @param resolver ContentResolver 实例
     * @param folderId 文件夹 ID
     * @return 小部件属性集合（可能为 null）
     */
    public static HashSet<AppWidgetAttribute> getFolderNoteWidget(ContentResolver resolver, long folderId) {
        Cursor c = resolver.query(Notes.CONTENT_NOTE_URI,
                new String[] { NoteColumns.WIDGET_ID, NoteColumns.WIDGET_TYPE },
                NoteColumns.PARENT_ID + "=?",
                new String[] { String.valueOf(folderId) },
                null);

        HashSet<AppWidgetAttribute> set = null;
        if (c != null) {
            if (c.moveToFirst()) {
                set = new HashSet<AppWidgetAttribute>();
                do {
                    try {
                        AppWidgetAttribute widget = new AppWidgetAttribute();
                        widget.widgetId = c.getInt(0);
                        widget.widgetType = c.getInt(1);
                        set.add(widget);
                    } catch (IndexOutOfBoundsException e) {
                        Log.e(TAG, e.toString());
                    }
                } while (c.moveToNext());
            }
            c.close();
        }
        return set;
    }

    /**
     * 根据便签 ID 获取通话记录便签中的电话号码。
     *
     * @param resolver ContentResolver 实例
     * @param noteId   便签 ID
     * @return 电话号码，若未找到则返回空字符串
     */
    public static String getCallNumberByNoteId(ContentResolver resolver, long noteId) {
        Cursor cursor = resolver.query(Notes.CONTENT_DATA_URI,
                new String[] { CallNote.PHONE_NUMBER },
                CallNote.NOTE_ID + "=? AND " + CallNote.MIME_TYPE + "=?",
                new String[] { String.valueOf(noteId), CallNote.CONTENT_ITEM_TYPE },
                null);

        if (cursor != null && cursor.moveToFirst()) {
            try {
                return cursor.getString(0);
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, "Get call number fails " + e.toString());
            } finally {
                cursor.close();
            }
        }
        return "";
    }

    /**
     * 根据电话号码和通话日期查找对应的通话记录便签 ID。
     * 使用 PHONE_NUMBERS_EQUAL SQL 函数进行号码匹配（忽略格式差异）。
     *
     * @param resolver    ContentResolver 实例
     * @param phoneNumber 电话号码
     * @param callDate    通话日期（毫秒）
     * @return 便签 ID，若未找到则返回 0
     */
    public static long getNoteIdByPhoneNumberAndCallDate(ContentResolver resolver, String phoneNumber, long callDate) {
        Cursor cursor = resolver.query(Notes.CONTENT_DATA_URI,
                new String[] { CallNote.NOTE_ID },
                CallNote.CALL_DATE + "=? AND " + CallNote.MIME_TYPE + "=? AND PHONE_NUMBERS_EQUAL("
                        + CallNote.PHONE_NUMBER + ",?)",
                new String[] { String.valueOf(callDate), CallNote.CONTENT_ITEM_TYPE, phoneNumber },
                null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                try {
                    return cursor.getLong(0);
                } catch (IndexOutOfBoundsException e) {
                    Log.e(TAG, "Get call note id fails " + e.toString());
                }
            }
            cursor.close();
        }
        return 0;
    }

    /**
     * 根据便签 ID 获取其摘要（SNIPPET）字段。
     *
     * @param resolver ContentResolver 实例
     * @param noteId   便签 ID
     * @return 摘要内容
     * @throws IllegalArgumentException 便签不存在时抛出
     */
    public static String getSnippetById(ContentResolver resolver, long noteId) {
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI,
                new String[] { NoteColumns.SNIPPET },
                NoteColumns.ID + "=?",
                new String[] { String.valueOf(noteId) },
                null);

        if (cursor != null) {
            String snippet = "";
            if (cursor.moveToFirst()) {
                snippet = cursor.getString(0);
            }
            cursor.close();
            return snippet;
        }
        throw new IllegalArgumentException("Note is not found with id: " + noteId);
    }

    /**
     * 格式化摘要：去除首尾空白，并截取第一行（换行符之前的内容）。
     *
     * @param snippet 原始摘要
     * @return 格式化后的摘要（可能为 null 输入返回 null）
     */
    public static String getFormattedSnippet(String snippet) {
        if (snippet != null) {
            snippet = snippet.trim();
            int index = snippet.indexOf('\n');
            if (index != -1) {
                snippet = snippet.substring(0, index);
            }
        }
        return snippet;
    }
}