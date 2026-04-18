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

/**
 * GTask 同步协议中使用的字符串常量工具类。
 *
 * 定义了与 Google Tasks 服务通信时 JSON 请求/响应中的键名、动作类型、
 * 固定文件夹名称以及元数据标记等。所有常量均为 public final static，供
 * {@link net.micode.notes.gtask.remote.GTaskClient} 和相关数据类使用。
 *
 */
public class GTaskStringUtils {

    // ==================== JSON 动作相关键名 ====================
    public final static String GTASK_JSON_ACTION_ID = "action_id";
    public final static String GTASK_JSON_ACTION_LIST = "action_list";
    public final static String GTASK_JSON_ACTION_TYPE = "action_type";

    // 动作类型值
    public final static String GTASK_JSON_ACTION_TYPE_CREATE = "create";
    public final static String GTASK_JSON_ACTION_TYPE_GETALL = "get_all";
    public final static String GTASK_JSON_ACTION_TYPE_MOVE = "move";
    public final static String GTASK_JSON_ACTION_TYPE_UPDATE = "update";

    // ==================== JSON 实体相关键名 ====================
    public final static String GTASK_JSON_CREATOR_ID = "creator_id";
    public final static String GTASK_JSON_CHILD_ENTITY = "child_entity";
    public final static String GTASK_JSON_CLIENT_VERSION = "client_version";
    public final static String GTASK_JSON_COMPLETED = "completed";
    public final static String GTASK_JSON_CURRENT_LIST_ID = "current_list_id";
    public final static String GTASK_JSON_DEFAULT_LIST_ID = "default_list_id";
    public final static String GTASK_JSON_DELETED = "deleted";
    public final static String GTASK_JSON_DEST_LIST = "dest_list";
    public final static String GTASK_JSON_DEST_PARENT = "dest_parent";
    public final static String GTASK_JSON_DEST_PARENT_TYPE = "dest_parent_type";
    public final static String GTASK_JSON_ENTITY_DELTA = "entity_delta";
    public final static String GTASK_JSON_ENTITY_TYPE = "entity_type";
    public final static String GTASK_JSON_GET_DELETED = "get_deleted";
    public final static String GTASK_JSON_ID = "id";
    public final static String GTASK_JSON_INDEX = "index";
    public final static String GTASK_JSON_LAST_MODIFIED = "last_modified";
    public final static String GTASK_JSON_LATEST_SYNC_POINT = "latest_sync_point";
    public final static String GTASK_JSON_LIST_ID = "list_id";
    public final static String GTASK_JSON_LISTS = "lists";
    public final static String GTASK_JSON_NAME = "name";
    public final static String GTASK_JSON_NEW_ID = "new_id";
    public final static String GTASK_JSON_NOTES = "notes";
    public final static String GTASK_JSON_PARENT_ID = "parent_id";
    public final static String GTASK_JSON_PRIOR_SIBLING_ID = "prior_sibling_id";
    public final static String GTASK_JSON_RESULTS = "results";
    public final static String GTASK_JSON_SOURCE_LIST = "source_list";
    public final static String GTASK_JSON_TASKS = "tasks";
    public final static String GTASK_JSON_TYPE = "type";
    public final static String GTASK_JSON_TYPE_GROUP = "GROUP";
    public final static String GTASK_JSON_TYPE_TASK = "TASK";
    public final static String GTASK_JSON_USER = "user";

    // ==================== MIUI 便签同步专用常量 ====================
    /** 同步文件夹的前缀，用于区分普通文件夹和 GTask 同步文件夹 */
    public final static String MIUI_FOLDER_PREFFIX = "[MIUI_Notes]";

    /** 默认文件夹名称（在 GTask 中显示为 "Default"） */
    public final static String FOLDER_DEFAULT = "Default";

    /** 通话记录文件夹名称 */
    public final static String FOLDER_CALL_NOTE = "Call_Note";

    /** 元数据文件夹名称（存放便签与任务的关联信息） */
    public final static String FOLDER_META = "METADATA";

    // ==================== 元数据 JSON 键名 ====================
    /** 元数据中关联的 GTask ID */
    public final static String META_HEAD_GTASK_ID = "meta_gid";

    /** 元数据中的便签信息（对应 note 表） */
    public final static String META_HEAD_NOTE = "meta_note";

    /** 元数据中的详细数据（对应 data 表） */
    public final static String META_HEAD_DATA = "meta_data";

    /** 元数据节点的名称（在 GTask 中显示为固定文本，用于标识） */
    public final static String META_NOTE_NAME = "[META INFO] DON'T UPDATE AND DELETE";

    /** META_NOTE_NAME 的别名，供 MetaData 类使用（保持兼容） */
    public final static String META_NODE_NAME = META_NOTE_NAME;

}
