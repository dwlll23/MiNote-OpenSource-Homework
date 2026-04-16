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
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * GTask 同步中的任务列表节点（对应 Google Tasks 中的一个任务列表/分组）。
 *
 * 继承自 {@link Node}，表示一个包含多个 {@link Task} 的容器。在本地便签应用中，
 * 任务列表通常对应一个文件夹（TYPE_FOLDER）或系统文件夹（TYPE_SYSTEM，如默认文件夹、
 * 通话记录文件夹）。该类负责：
 *     生成创建/更新任务列表的 JSON 动作
 *     从远程 JSON 或本地文件夹 JSON 设置列表内容
 *     将列表内容转换为本地文件夹 JSON
 *     根据本地数据库记录和远程状态决定同步动作
 *     管理子任务（Task）的增删改查及顺序维护
 *
 */
public class TaskList extends Node {
    private static final String TAG = TaskList.class.getSimpleName();

    private int mIndex;                     // 列表在远程服务中的位置索引（用于排序）
    private ArrayList<Task> mChildren;      // 子任务列表（按顺序存储）

    public TaskList() {
        super();
        mChildren = new ArrayList<Task>();
        mIndex = 1;                         // 默认索引从 1 开始
    }

    /**
     * 生成创建任务列表的 JSON 动作（用于向远程服务器发送创建请求）。
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

            // 列表索引
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mIndex);

            // 实体数据（entity_delta）
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null");
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-create jsonobject");
        }

        return js;
    }

    /**
     * 生成更新任务列表的 JSON 动作（用于向远程服务器发送更新请求）。
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

            // 列表的 GID
            js.put(GTaskStringUtils.GTASK_JSON_ID, getGid());

            // 实体数据（entity_delta）
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, getDeleted());
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-update jsonobject");
        }

        return js;
    }

    /**
     * 从远程服务器返回的 JSON 设置当前任务列表的内容。
     *
     * @param js 远程 JSON 对象（包含 id, last_modified, name 等字段）
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
            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("fail to get tasklist content from jsonobject");
            }
        }
    }

    /**
     * 从本地文件夹的 JSON 表示设置任务列表内容。
     * 本地文件夹 JSON 来源于 {@link SqlNote#getContent()}，根据文件夹类型（TYPE_FOLDER 或 TYPE_SYSTEM）
     * 构造列表名称（添加 MIUI 前缀）。
     *
     * @param js 本地文件夹 JSON（包含 META_HEAD_NOTE）
     */
    public void setContentByLocalJSON(JSONObject js) {
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable");
        }

        try {
            JSONObject folder = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);

            if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_FOLDER) {
                String name = folder.getString(NoteColumns.SNIPPET);
                setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + name);   // 添加 MIUI 文件夹前缀
            } else if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_SYSTEM) {
                if (folder.getLong(NoteColumns.ID) == Notes.ID_ROOT_FOLDER)
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT);
                else if (folder.getLong(NoteColumns.ID) == Notes.ID_CALL_RECORD_FOLDER)
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                            + GTaskStringUtils.FOLDER_CALL_NOTE);
                else
                    Log.e(TAG, "invalid system folder");
            } else {
                Log.e(TAG, "error type");
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    /**
     * 将当前任务列表的内容转换为本地文件夹的 JSON 格式。
     * 若列表名称以 MIUI 前缀开头，则去除该前缀；根据文件夹名称判断类型（系统文件夹或普通文件夹）。
     *
     * @return 本地文件夹 JSON 对象，转换失败时返回 null
     */
    public JSONObject getLocalJSONFromContent() {
        try {
            JSONObject js = new JSONObject();
            JSONObject folder = new JSONObject();

            String folderName = getName();
            // 去除 MIUI 前缀，得到实际的便签文件夹名称
            if (getName().startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX))
                folderName = folderName.substring(GTaskStringUtils.MIUI_FOLDER_PREFFIX.length(),
                        folderName.length());
            folder.put(NoteColumns.SNIPPET, folderName);
            if (folderName.equals(GTaskStringUtils.FOLDER_DEFAULT)
                    || folderName.equals(GTaskStringUtils.FOLDER_CALL_NOTE))
                folder.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
            else
                folder.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);

            js.put(GTaskStringUtils.META_HEAD_NOTE, folder);

            return js;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 根据本地数据库游标和当前任务列表状态，决定需要执行的同步动作。
     * 与 Task 的决策类似，但对于冲突情况，直接采用本地修改（覆盖远程）。
     *
     * @param c 指向 note 表中当前文件夹记录的游标（必须包含 SqlNote 中定义的投影列）
     * @return 同步动作常量，若出错则返回 {@link #SYNC_ACTION_ERROR}
     */
    public int getSyncAction(Cursor c) {
        try {
            if (c.getInt(SqlNote.LOCAL_MODIFIED_COLUMN) == 0) {
                // 本地无修改
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    return SYNC_ACTION_NONE;
                } else {
                    return SYNC_ACTION_UPDATE_LOCAL;   // 远程有更新，用远程覆盖本地
                }
            } else {
                // 本地有修改
                if (!c.getString(SqlNote.GTASK_ID_COLUMN).equals(getGid())) {
                    Log.e(TAG, "gtask id doesn't match");
                    return SYNC_ACTION_ERROR;
                }
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    return SYNC_ACTION_UPDATE_REMOTE;   // 仅本地修改，用本地覆盖远程
                } else {
                    // 对于文件夹冲突，直接采用本地修改（覆盖远程），避免数据丢失
                    return SYNC_ACTION_UPDATE_REMOTE;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return SYNC_ACTION_ERROR;
    }


    /**
     * 获取子任务数量。
     *
     * @return 子任务个数
     */
    public int getChildTaskCount() {
        return mChildren.size();
    }

    /**
     * 在列表末尾添加一个子任务。
     * 会自动设置该任务的前驱兄弟节点为原列表最后一个任务，并将其父节点指向当前列表。
     *
     * @param task 要添加的任务
     * @return true 添加成功，false 任务为空或已存在于列表中
     */
    public boolean addChildTask(Task task) {
        boolean ret = false;
        if (task != null && !mChildren.contains(task)) {
            ret = mChildren.add(task);
            if (ret) {
                // 设置前驱兄弟节点和父节点
                task.setPriorSibling(mChildren.isEmpty() ? null : mChildren
                        .get(mChildren.size() - 1));
                task.setParent(this);
            }
        }
        return ret;
    }

    /**
     * 在指定索引位置插入一个子任务。
     * 会更新受影响任务的前驱兄弟关系。
     *
     * @param task  要添加的任务
     * @param index 插入位置（0 到 size()）
     * @return true 添加成功，false 索引无效或任务已存在
     */
    public boolean addChildTask(Task task, int index) {
        if (index < 0 || index > mChildren.size()) {
            Log.e(TAG, "add child task: invalid index");
            return false;
        }

        int pos = mChildren.indexOf(task);
        if (task != null && pos == -1) {
            mChildren.add(index, task);

            // 更新受影响的任务的前驱兄弟关系
            Task preTask = null;
            Task afterTask = null;
            if (index != 0)
                preTask = mChildren.get(index - 1);
            if (index != mChildren.size() - 1)
                afterTask = mChildren.get(index + 1);

            task.setPriorSibling(preTask);
            if (afterTask != null)
                afterTask.setPriorSibling(task);
        }

        return true;
    }

    /**
     * 从列表中移除一个子任务。
     * 会清空该任务的前驱兄弟和父节点引用，并更新后续任务的前驱兄弟关系。
     *
     * @param task 要移除的任务
     * @return true 移除成功，false 任务不在列表中
     */
    public boolean removeChildTask(Task task) {
        boolean ret = false;
        int index = mChildren.indexOf(task);
        if (index != -1) {
            ret = mChildren.remove(task);

            if (ret) {
                // 清空任务的前驱兄弟和父节点引用
                task.setPriorSibling(null);
                task.setParent(null);

                // 更新列表中被移除位置之后的任务的前驱兄弟
                if (index != mChildren.size()) {
                    mChildren.get(index).setPriorSibling(
                            index == 0 ? null : mChildren.get(index - 1));
                }
            }
        }
        return ret;
    }

    /**
     * 将已存在的子任务移动到新的索引位置。
     *
     * @param task  要移动的任务
     * @param index 目标索引
     * @return true 移动成功，false 索引无效或任务不在列表中
     */
    public boolean moveChildTask(Task task, int index) {

        if (index < 0 || index >= mChildren.size()) {
            Log.e(TAG, "move child task: invalid index");
            return false;
        }

        int pos = mChildren.indexOf(task);
        if (pos == -1) {
            Log.e(TAG, "move child task: the task should in the list");
            return false;
        }

        if (pos == index)
            return true;
        return (removeChildTask(task) && addChildTask(task, index));
    }

    /**
     * 根据 GID 查找子任务。
     *
     * @param gid 任务的 Google Tasks ID
     * @return 找到的任务，未找到返回 null
     */
    public Task findChildTaskByGid(String gid) {
        for (int i = 0; i < mChildren.size(); i++) {
            Task t = mChildren.get(i);
            if (t.getGid().equals(gid)) {
                return t;
            }
        }
        return null;
    }

    /**
     * 获取子任务在列表中的索引。
     *
     * @param task 目标任务
     * @return 索引（从 0 开始），若任务不在列表中返回 -1
     */
    public int getChildTaskIndex(Task task) {
        return mChildren.indexOf(task);
    }

    /**
     * 根据索引获取子任务。
     *
     * @param index 索引（0 到 size()-1）
     * @return 任务对象，索引无效时返回 null
     */
    public Task getChildTaskByIndex(int index) {
        if (index < 0 || index >= mChildren.size()) {
            Log.e(TAG, "getTaskByIndex: invalid index");
            return null;
        }
        return mChildren.get(index);
    }

    /**
     * 根据 GID 获取子任务（与 findChildTaskByGid 功能相同，保留命名兼容）。
     *
     * @param gid 任务的 GID
     * @return 找到的任务，未找到返回 null
     */
    public Task getChilTaskByGid(String gid) {
        for (Task task : mChildren) {
            if (task.getGid().equals(gid))
                return task;
        }
        return null;
    }

    /**
     * 获取所有子任务的列表（只读引用，修改需通过提供的方法）。
     *
     * @return 子任务 ArrayList
     */
    public ArrayList<Task> getChildTaskList() {
        return this.mChildren;
    }

 

    public void setIndex(int index) {
        this.mIndex = index;
    }

    public int getIndex() {
        return this.mIndex;
    }
}