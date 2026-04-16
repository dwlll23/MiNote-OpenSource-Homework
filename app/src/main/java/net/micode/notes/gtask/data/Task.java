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

import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * GTask 同步中的任务节点（对应 Google Tasks 中的一项任务）。
 *
 * 继承自 {@link Node}，实现了任务在本地便签与远程 Google Tasks 服务之间的数据转换、
 * 同步动作决策以及 JSON 序列化/反序列化。每个 Task 实例可以关联一个便签（TYPE_NOTE），
 * 并维护其在任务列表中的位置（前驱兄弟节点和父列表）。
 *
 * 主要功能：
 *     生成创建/更新任务的 JSON 动作（{@link #getCreateAction} / {@link #getUpdateAction}）
 *     从远程 JSON 或本地便签 JSON 设置任务内容
 *     将任务内容转换为本地便签 JSON（用于保存到数据库）
 *     根据本地数据库记录和远程状态决定同步动作（{@link #getSyncAction}）
 *     管理任务的元数据（{@link #mMetaInfo}，来自 {@link MetaData} 节点）
 * 
 *
 */
public class Task extends Node {
    private static final String TAG = Task.class.getSimpleName();

    private boolean mCompleted;          // 任务是否已完成
    private String mNotes;               // 任务的备注信息（对应便签的详细内容）
    private JSONObject mMetaInfo;        // 从 MetaData 节点解析的元数据 JSON（包含原始便签结构）
    private Task mPriorSibling;          // 在同一父列表中的前一个兄弟任务（用于维护顺序）
    private TaskList mParent;            // 所属的任务列表（父节点）

    public Task() {
        super();
        mCompleted = false;
        mNotes = null;
        mPriorSibling = null;
        mParent = null;
        mMetaInfo = null;
    }

    /**
     * 生成创建任务的 JSON 动作（用于向远程服务器发送创建请求）。
     *
     * @param actionId 动作的唯一标识 ID
     * @return 包含创建动作信息的 JSON 对象
     * @throws ActionFailureException 生成失败时抛出
     */
    public JSONObject getCreateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // 动作类型：创建
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_CREATE);

            // 动作 ID
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // 在父列表中的位置索引（由父列表计算）
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mParent.getChildTaskIndex(this));

            // 实体数据（entity_delta）
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null");
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_TASK);
            if (getNotes() != null) {
                entity.put(GTaskStringUtils.GTASK_JSON_NOTES, getNotes());
            }
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

            // 父节点 ID（所属任务列表的 GID）
            js.put(GTaskStringUtils.GTASK_JSON_PARENT_ID, mParent.getGid());

            // 目标父节点类型：分组（任务列表）
            js.put(GTaskStringUtils.GTASK_JSON_DEST_PARENT_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);

            // 列表 ID（同父节点 ID）
            js.put(GTaskStringUtils.GTASK_JSON_LIST_ID, mParent.getGid());

            // 前驱兄弟节点 ID（用于维护顺序）
            if (mPriorSibling != null) {
                js.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID, mPriorSibling.getGid());
            }

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate task-create jsonobject");
        }

        return js;
    }

    /**
     * 生成更新任务的 JSON 动作（用于向远程服务器发送更新请求）。
     *
     * @param actionId 动作的唯一标识 ID
     * @return 包含更新动作信息的 JSON 对象
     * @throws ActionFailureException 生成失败时抛出
     */
    public JSONObject getUpdateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // 动作类型：更新
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_UPDATE);

            // 动作 ID
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // 任务的 GID
            js.put(GTaskStringUtils.GTASK_JSON_ID, getGid());

            // 实体数据（entity_delta）
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            if (getNotes() != null) {
                entity.put(GTaskStringUtils.GTASK_JSON_NOTES, getNotes());
            }
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, getDeleted());
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate task-update jsonobject");
        }

        return js;
    }

    /**
     * 从远程服务器返回的 JSON 设置当前任务的内容。
     *
     * @param js 远程 JSON 对象（包含 id, last_modified, name, notes, deleted, completed 等字段）
     * @throws ActionFailureException 解析失败时抛出
     */
    public void setContentByRemoteJSON(JSONObject js) {
        if (js != null) {
            try {
                if (js.has(GTaskStringUtils.GTASK_JSON_ID)) {
                    setGid(js.getString(GTaskStringUtils.GTASK_JSON_ID));
                }
                if (js.has(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED)) {
                    setLastModified(js.getLong(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED));
                }
                if (js.has(GTaskStringUtils.GTASK_JSON_NAME)) {
                    setName(js.getString(GTaskStringUtils.GTASK_JSON_NAME));
                }
                if (js.has(GTaskStringUtils.GTASK_JSON_NOTES)) {
                    setNotes(js.getString(GTaskStringUtils.GTASK_JSON_NOTES));
                }
                if (js.has(GTaskStringUtils.GTASK_JSON_DELETED)) {
                    setDeleted(js.getBoolean(GTaskStringUtils.GTASK_JSON_DELETED));
                }
                if (js.has(GTaskStringUtils.GTASK_JSON_COMPLETED)) {
                    setCompleted(js.getBoolean(GTaskStringUtils.GTASK_JSON_COMPLETED));
                }
            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("fail to get task content from jsonobject");
            }
        }
    }

    /**
     * 从本地便签的 JSON 表示设置任务内容。
     * 主要用于从数据库读取的便签数据（通过 SqlNote.getContent() 生成的 JSON）还原任务。
     *
     * @param js 本地便签 JSON（包含 META_HEAD_NOTE 和 META_HEAD_DATA）
     */
    public void setContentByLocalJSON(JSONObject js) {
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)
                || !js.has(GTaskStringUtils.META_HEAD_DATA)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable");
        }

        try {
            JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);

            if (note.getInt(NoteColumns.TYPE) != Notes.TYPE_NOTE) {
                Log.e(TAG, "invalid type");
                return;
            }

            // 从 data 数组中查找 MIME_TYPE 为 NOTE 的数据项，将其 CONTENT 作为任务名称
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject data = dataArray.getJSONObject(i);
                if (TextUtils.equals(data.getString(DataColumns.MIME_TYPE), DataConstants.NOTE)) {
                    setName(data.getString(DataColumns.CONTENT));
                    break;
                }
            }

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    /**
     * 将当前任务的内容转换为本地便签的 JSON 格式。
     * 如果任务没有关联的元数据（mMetaInfo 为 null），则根据任务名称新建一个简单便签 JSON；
     * 否则基于已有的元数据更新便签内容（仅更新 NAME 字段对应的 data 内容）。
     *
     * @return 本地便签 JSON 对象，若任务为空或转换失败则返回 null
     */
    public JSONObject getLocalJSONFromContent() {
        String name = getName();
        try {
            if (mMetaInfo == null) {
                // 从远程同步的新任务，没有关联的元数据：创建一个新的便签 JSON
                if (name == null) {
                    Log.w(TAG, "the note seems to be an empty one");
                    return null;
                }

                JSONObject js = new JSONObject();
                JSONObject note = new JSONObject();
                JSONArray dataArray = new JSONArray();
                JSONObject data = new JSONObject();
                data.put(DataColumns.CONTENT, name);
                dataArray.put(data);
                js.put(GTaskStringUtils.META_HEAD_DATA, dataArray);
                note.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);
                return js;
            } else {
                // 已有元数据（曾经同步过的任务）：复用原有 JSON 结构，只更新任务名称
                JSONObject note = mMetaInfo.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
                JSONArray dataArray = mMetaInfo.getJSONArray(GTaskStringUtils.META_HEAD_DATA);

                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject data = dataArray.getJSONObject(i);
                    if (TextUtils.equals(data.getString(DataColumns.MIME_TYPE), DataConstants.NOTE)) {
                        data.put(DataColumns.CONTENT, getName());
                        break;
                    }
                }

                note.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
                return mMetaInfo;
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 设置任务的元数据（来自 MetaData 节点）。
     * 元数据中保存了任务对应的原始便签结构（用于本地-远程映射）。
     *
     * @param metaData MetaData 对象，其 notes 字段为 JSON 字符串
     */
    public void setMetaInfo(MetaData metaData) {
        if (metaData != null && metaData.getNotes() != null) {
            try {
                mMetaInfo = new JSONObject(metaData.getNotes());
            } catch (JSONException e) {
                Log.w(TAG, e.toString());
                mMetaInfo = null;
            }
        }
    }

    /**
     * 根据本地数据库游标和当前任务状态，决定需要执行的同步动作。
     * 同步动作类型定义在 {@link Node} 中（如 ADD_REMOTE、UPDATE_LOCAL、UPDATE_CONFLICT 等）。
     *
     * @param c 指向 note 表中当前便签记录的游标（必须包含 SqlNote 中定义的投影列）
     * @return 同步动作常量，若出错则返回 {@link #SYNC_ACTION_ERROR}
     */
    public int getSyncAction(Cursor c) {
        try {
            JSONObject noteInfo = null;
            if (mMetaInfo != null && mMetaInfo.has(GTaskStringUtils.META_HEAD_NOTE)) {
                noteInfo = mMetaInfo.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            }

            if (noteInfo == null) {
                Log.w(TAG, "it seems that note meta has been deleted");
                return SYNC_ACTION_UPDATE_REMOTE;   // 本地元数据丢失，用本地数据覆盖远程
            }

            if (!noteInfo.has(NoteColumns.ID)) {
                Log.w(TAG, "remote note id seems to be deleted");
                return SYNC_ACTION_UPDATE_LOCAL;    // 远程便签 ID 丢失，用远程数据覆盖本地
            }

            // 校验本地便签 ID 是否与元数据中记录的一致
            if (c.getLong(SqlNote.ID_COLUMN) != noteInfo.getLong(NoteColumns.ID)) {
                Log.w(TAG, "note id doesn't match");
                return SYNC_ACTION_UPDATE_LOCAL;
            }

            if (c.getInt(SqlNote.LOCAL_MODIFIED_COLUMN) == 0) {
                // 本地无修改
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // 双方同步 ID 一致，无需同步
                    return SYNC_ACTION_NONE;
                } else {
                    // 远程有更新，用远程覆盖本地
                    return SYNC_ACTION_UPDATE_LOCAL;
                }
            } else {
                // 本地有修改
                if (!c.getString(SqlNote.GTASK_ID_COLUMN).equals(getGid())) {
                    Log.e(TAG, "gtask id doesn't match");
                    return SYNC_ACTION_ERROR;
                }
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // 仅本地修改，远程未变，用本地覆盖远程
                    return SYNC_ACTION_UPDATE_REMOTE;
                } else {
                    // 双方均有修改，产生冲突
                    return SYNC_ACTION_UPDATE_CONFLICT;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return SYNC_ACTION_ERROR;
    }

    /**
     * 判断当前任务是否值得保存（即是否包含有效数据）。
     *
     * @return true 如果任务包含元数据、非空名称或非空备注，否则 false
     */
    public boolean isWorthSaving() {
        return mMetaInfo != null || (getName() != null && getName().trim().length() > 0)
                || (getNotes() != null && getNotes().trim().length() > 0);
    }


    public void setCompleted(boolean completed) {
        this.mCompleted = completed;
    }

    public void setNotes(String notes) {
        this.mNotes = notes;
    }

    public void setPriorSibling(Task priorSibling) {
        this.mPriorSibling = priorSibling;
    }

    public void setParent(TaskList parent) {
        this.mParent = parent;
    }

    public boolean getCompleted() {
        return this.mCompleted;
    }

    public String getNotes() {
        return this.mNotes;
    }

    public Task getPriorSibling() {
        return this.mPriorSibling;
    }

    public TaskList getParent() {
        return this.mParent;
    }
}