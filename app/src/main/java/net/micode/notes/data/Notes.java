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

package net.micode.notes.data;

import android.net.Uri;

/**
 * 便签应用的数据契约类。
 *
 * 定义了 ContentProvider 使用的常量，包括 authority、表列名、系统文件夹 ID、
 * Intent extra 键、桌面小部件类型以及 Content URI。
 * 类似于 Android 的 {@link android.provider.ContactsContract}，为数据访问提供统一接口。
 *
 * 主要包含：
 *     {@link #AUTHORITY}：ContentProvider 授权字符串
 *     便签/文件夹类型常量：{@link #TYPE_NOTE}、{@link #TYPE_FOLDER}、{@link #TYPE_SYSTEM}
 *     系统文件夹 ID（根文件夹、临时文件夹、通话记录文件夹、废纸篓）
 *     Intent Extra 键名（用于 Activity 间传递参数）
 *     桌面小部件类型：2x2、4x4
 *     Content URI：{@link #CONTENT_NOTE_URI}（便签表）和 {@link #CONTENT_DATA_URI}（数据表）
 *     {@link NoteColumns}：便签表（note）的列名
 *     {@link DataColumns}：数据表（data）的列名
 *     {@link TextNote}：文本便签的数据格式
 *     {@link CallNote}：通话记录便签的数据格式
 *
 */
public class Notes {
    public static final String AUTHORITY = "micode_notes";
    public static final String TAG = "Notes";

    // 类型常量
    public static final int TYPE_NOTE     = 0;
    public static final int TYPE_FOLDER   = 1;
    public static final int TYPE_SYSTEM   = 2;

    // 系统文件夹 ID（负数用于区分系统文件夹）
    public static final int ID_ROOT_FOLDER = 0;
    public static final int ID_TEMPARAY_FOLDER = -1;
    public static final int ID_CALL_RECORD_FOLDER = -2;
    public static final int ID_TRASH_FOLER = -3;

    // Intent Extra 键名
    public static final String INTENT_EXTRA_ALERT_DATE = "net.micode.notes.alert_date";
    public static final String INTENT_EXTRA_BACKGROUND_ID = "net.micode.notes.background_color_id";
    public static final String INTENT_EXTRA_WIDGET_ID = "net.micode.notes.widget_id";
    public static final String INTENT_EXTRA_WIDGET_TYPE = "net.micode.notes.widget_type";
    public static final String INTENT_EXTRA_FOLDER_ID = "net.micode.notes.folder_id";
    public static final String INTENT_EXTRA_CALL_DATE = "net.micode.notes.call_date";

    // 桌面小部件类型
    public static final int TYPE_WIDGET_INVALIDE      = -1;
    public static final int TYPE_WIDGET_2X            = 0;
    public static final int TYPE_WIDGET_4X            = 1;

    /**
     * 数据类型的 MIME 常量汇总。
     */
    public static class DataConstants {
        public static final String NOTE = TextNote.CONTENT_ITEM_TYPE;
        public static final String CALL_NOTE = CallNote.CONTENT_ITEM_TYPE;
    }

    // Content URI
    public static final Uri CONTENT_NOTE_URI = Uri.parse("content://" + AUTHORITY + "/note");
    public static final Uri CONTENT_DATA_URI = Uri.parse("content://" + AUTHORITY + "/data");

    /**
     * 便签表（note）的列名接口。
     * 存储便签和文件夹的公共属性，如 ID、父文件夹、时间戳、摘要、背景色等。
     */
    public interface NoteColumns {
        public static final String ID = "_id";
        public static final String PARENT_ID = "parent_id";
        public static final String CREATED_DATE = "created_date";
        public static final String MODIFIED_DATE = "modified_date";
        public static final String ALERTED_DATE = "alert_date";
        public static final String SNIPPET = "snippet";
        public static final String WIDGET_ID = "widget_id";
        public static final String WIDGET_TYPE = "widget_type";
        public static final String BG_COLOR_ID = "bg_color_id";
        public static final String HAS_ATTACHMENT = "has_attachment";
        public static final String NOTES_COUNT = "notes_count";
        public static final String TYPE = "type";
        public static final String SYNC_ID = "sync_id";
        public static final String LOCAL_MODIFIED = "local_modified";
        public static final String ORIGIN_PARENT_ID = "origin_parent_id";
        public static final String GTASK_ID = "gtask_id";
        public static final String VERSION = "version";
    }

    /**
     * 数据表（data）的列名接口。
     * 存储便签的详细内容，支持一对多（一个便签可对应多条数据）。
     * 通用列 DATA1~DATA5 的含义由 MIME_TYPE 决定。
     */
    public interface DataColumns {
        public static final String ID = "_id";
        public static final String MIME_TYPE = "mime_type";
        public static final String NOTE_ID = "note_id";
        public static final String CREATED_DATE = "created_date";
        public static final String MODIFIED_DATE = "modified_date";
        public static final String CONTENT = "content";
        public static final String DATA1 = "data1";
        public static final String DATA2 = "data2";
        public static final String DATA3 = "data3";
        public static final String DATA4 = "data4";
        public static final String DATA5 = "data5";
    }

    /**
     * 文本便签的数据格式。
     * 使用 DATA1 列存储模式（普通 / Checklist）。
     */
    public static final class TextNote implements DataColumns {
        public static final String MODE = DATA1;
        public static final int MODE_CHECK_LIST = 1;

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/text_note";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/text_note";
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/text_note");
    }

    /**
     * 通话记录便签的数据格式。
     * 使用 DATA1 列存储通话日期，DATA3 列存储电话号码。
     */
    public static final class CallNote implements DataColumns {
        public static final String CALL_DATE = DATA1;
        public static final String PHONE_NUMBER = DATA3;

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/call_note";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/call_note";
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/call_note");
    }
}