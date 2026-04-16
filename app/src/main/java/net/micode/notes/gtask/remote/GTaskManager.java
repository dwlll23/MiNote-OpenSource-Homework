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

package net.micode.notes.gtask.remote;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.data.MetaData;
import net.micode.notes.gtask.data.Node;
import net.micode.notes.gtask.data.SqlNote;
import net.micode.notes.gtask.data.Task;
import net.micode.notes.gtask.data.TaskList;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.gtask.exception.NetworkFailureException;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/**
 * GTask 同步的核心管理器（单例）。
 *
 * 负责协调本地数据库（便签/文件夹）与远程 Google Tasks 服务之间的双向同步。
 * 主要功能包括：
 *     登录 GTask 客户端并初始化远程任务列表结构
 *     对比本地与远程数据，根据同步动作类型（ADD/DEL/UPDATE/CONFLICT）执行相应操作
 *     维护 GID 与本地 ID 的映射关系（mGidToNid / mNidToGid）
 *     处理元数据（MetaData）节点，用于存储便签的额外信息
 *     支持取消同步（mCancelled）
 *     同步完成后刷新本地同步 ID
 *
 * 同步流程概览：
 *     登录 Google 账户并初始化 GTask 客户端
 *     获取远程任务列表和元数据，构建内存中的数据结构
 *     同步文件夹（TaskList）
 *     同步便签内容（Task）
 *     处理本地已删除、新增、冲突等各类情况
 *     最后刷新本地记录的 sync_id
 *
 */
public class GTaskManager {
    private static final String TAG = GTaskManager.class.getSimpleName();

    // 同步结果状态码
    public static final int STATE_SUCCESS = 0;              // 成功
    public static final int STATE_NETWORK_ERROR = 1;        // 网络错误
    public static final int STATE_INTERNAL_ERROR = 2;       // 内部错误（解析、数据库等）
    public static final int STATE_SYNC_IN_PROGRESS = 3;     // 同步正在进行中
    public static final int STATE_SYNC_CANCELLED = 4;       // 用户取消同步

    private static GTaskManager mInstance = null;

    private Activity mActivity;                 // 用于获取 AuthToken 的 Activity
    private Context mContext;                   // 应用上下文
    private ContentResolver mContentResolver;   // 内容解析器

    private boolean mSyncing;                   // 是否正在同步中
    private boolean mCancelled;                 // 是否被取消

    // 远程数据结构映射
    private HashMap<String, TaskList> mGTaskListHashMap;   // GID -> TaskList（任务列表）
    private HashMap<String, Node> mGTaskHashMap;           // GID -> Node（统一节点，Task 或 TaskList）
    private HashMap<String, MetaData> mMetaHashMap;        // GID -> MetaData（元数据节点）
    private TaskList mMetaList;                            // 特殊的元数据列表（存放所有 MetaData）

    private HashSet<Long> mLocalDeleteIdMap;               // 待删除的本地便签 ID 集合
    private HashMap<String, Long> mGidToNid;               // 远程 GID -> 本地 note ID
    private HashMap<Long, String> mNidToGid;               // 本地 note ID -> 远程 GID

    private GTaskManager() {
        mSyncing = false;
        mCancelled = false;
        mGTaskListHashMap = new HashMap<String, TaskList>();
        mGTaskHashMap = new HashMap<String, Node>();
        mMetaHashMap = new HashMap<String, MetaData>();
        mMetaList = null;
        mLocalDeleteIdMap = new HashSet<Long>();
        mGidToNid = new HashMap<String, Long>();
        mNidToGid = new HashMap<Long, String>();
    }

    /**
     * 获取单例实例。
     */
    public static synchronized GTaskManager getInstance() {
        if (mInstance == null) {
            mInstance = new GTaskManager();
        }
        return mInstance;
    }

    /**
     * 设置 Activity 上下文（用于登录时获取 AuthToken）。
     *
     * @param activity 当前 Activity
     */
    public synchronized void setActivityContext(Activity activity) {
        mActivity = activity;
    }

    /**
     * 执行同步操作（在后台线程调用）。
     *
     * @param context    上下文
     * @param asyncTask  用于发布进度的异步任务
     * @return 同步结果状态码（STATE_SUCCESS / STATE_NETWORK_ERROR / ...）
     */
    public int sync(Context context, GTaskASyncTask asyncTask) {
        if (mSyncing) {
            Log.d(TAG, "Sync is in progress");
            return STATE_SYNC_IN_PROGRESS;
        }
        mContext = context;
        mContentResolver = mContext.getContentResolver();
        mSyncing = true;
        mCancelled = false;
        // 清空临时数据结构
        mGTaskListHashMap.clear();
        mGTaskHashMap.clear();
        mMetaHashMap.clear();
        mLocalDeleteIdMap.clear();
        mGidToNid.clear();
        mNidToGid.clear();

        try {
            GTaskClient client = GTaskClient.getInstance();
            client.resetUpdateArray();

            // 登录 Google Tasks 服务
            if (!mCancelled) {
                if (!client.login(mActivity)) {
                    throw new NetworkFailureException("login google task failed");
                }
            }

            // 获取远程任务列表，构建内存结构
            asyncTask.publishProgess(mContext.getString(R.string.sync_progress_init_list));
            initGTaskList();

            // 执行数据同步
            asyncTask.publishProgess(mContext.getString(R.string.sync_progress_syncing));
            syncContent();
        } catch (NetworkFailureException e) {
            Log.e(TAG, e.toString());
            return STATE_NETWORK_ERROR;
        } catch (ActionFailureException e) {
            Log.e(TAG, e.toString());
            return STATE_INTERNAL_ERROR;
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return STATE_INTERNAL_ERROR;
        } finally {
            // 清理临时数据，无论成功与否
            mGTaskListHashMap.clear();
            mGTaskHashMap.clear();
            mMetaHashMap.clear();
            mLocalDeleteIdMap.clear();
            mGidToNid.clear();
            mNidToGid.clear();
            mSyncing = false;
        }

        return mCancelled ? STATE_SYNC_CANCELLED : STATE_SUCCESS;
    }

    /**
     * 初始化远程任务列表数据，包括普通任务列表和元数据列表。
     *
     * @throws NetworkFailureException 网络异常
     * @throws ActionFailureException  JSON 解析失败
     */
    private void initGTaskList() throws NetworkFailureException {
        if (mCancelled)
            return;
        GTaskClient client = GTaskClient.getInstance();
        try {
            JSONArray jsTaskLists = client.getTaskLists();

            // 首先初始化元数据列表（名为 "MIUI_Notes_Meta"）
            mMetaList = null;
            for (int i = 0; i < jsTaskLists.length(); i++) {
                JSONObject object = jsTaskLists.getJSONObject(i);
                String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);

                if (name
                        .equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_META)) {
                    mMetaList = new TaskList();
                    mMetaList.setContentByRemoteJSON(object);

                    // 加载该列表下的所有元数据任务
                    JSONArray jsMetas = client.getTaskList(gid);
                    for (int j = 0; j < jsMetas.length(); j++) {
                        object = (JSONObject) jsMetas.getJSONObject(j);
                        MetaData metaData = new MetaData();
                        metaData.setContentByRemoteJSON(object);
                        if (metaData.isWorthSaving()) {
                            mMetaList.addChildTask(metaData);
                            if (metaData.getGid() != null) {
                                mMetaHashMap.put(metaData.getRelatedGid(), metaData);
                            }
                        }
                    }
                }
            }

            // 若远程不存在元数据列表，则创建一个
            if (mMetaList == null) {
                mMetaList = new TaskList();
                mMetaList.setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                        + GTaskStringUtils.FOLDER_META);
                GTaskClient.getInstance().createTaskList(mMetaList);
            }

            // 初始化普通任务列表（排除元数据列表）
            for (int i = 0; i < jsTaskLists.length(); i++) {
                JSONObject object = jsTaskLists.getJSONObject(i);
                String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);

                if (name.startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX)
                        && !name.equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                                + GTaskStringUtils.FOLDER_META)) {
                    TaskList tasklist = new TaskList();
                    tasklist.setContentByRemoteJSON(object);
                    mGTaskListHashMap.put(gid, tasklist);
                    mGTaskHashMap.put(gid, tasklist);

                    // 加载该列表下的所有任务
                    JSONArray jsTasks = client.getTaskList(gid);
                    for (int j = 0; j < jsTasks.length(); j++) {
                        object = (JSONObject) jsTasks.getJSONObject(j);
                        gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                        Task task = new Task();
                        task.setContentByRemoteJSON(object);
                        if (task.isWorthSaving()) {
                            task.setMetaInfo(mMetaHashMap.get(gid));
                            tasklist.addChildTask(task);
                            mGTaskHashMap.put(gid, task);
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("initGTaskList: handing JSONObject failed");
        }
    }

    /**
     * 同步主要内容（便签/文件夹），包括删除、新增、更新、冲突处理。
     *
     * @throws NetworkFailureException 网络异常
     */
    private void syncContent() throws NetworkFailureException {
        int syncType;
        Cursor c = null;
        String gid;
        Node node;

        mLocalDeleteIdMap.clear();

        if (mCancelled) {
            return;
        }

        // 1. 处理本地已删除的便签（位于废纸篓中）
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type<>? AND parent_id=?)", new String[] {
                            String.valueOf(Notes.TYPE_SYSTEM), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, null);
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        doContentSync(Node.SYNC_ACTION_DEL_REMOTE, node, c);
                    }
                    mLocalDeleteIdMap.add(c.getLong(SqlNote.ID_COLUMN));
                }
            } else {
                Log.w(TAG, "failed to query trash folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // 2. 先同步文件夹（TaskList）
        syncFolder();

        // 3. 处理本地数据库中存在的便签（TYPE_NOTE）
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type=? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_NOTE), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN));
                        mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid);
                        syncType = node.getSyncAction(c);
                    } else {
                        if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                            // 本地新增，远程不存在
                            syncType = Node.SYNC_ACTION_ADD_REMOTE;
                        } else {
                            // 远程已删除
                            syncType = Node.SYNC_ACTION_DEL_LOCAL;
                        }
                    }
                    doContentSync(syncType, node, c);
                }
            } else {
                Log.w(TAG, "failed to query existing note in database");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // 4. 处理剩余的远程节点（本地不存在，需新增到本地）
        Iterator<Map.Entry<String, Node>> iter = mGTaskHashMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, Node> entry = iter.next();
            node = entry.getValue();
            doContentSync(Node.SYNC_ACTION_ADD_LOCAL, node, null);
        }

        // 5. 批量删除本地废纸篓中的便签
        if (!mCancelled) {
            if (!DataUtils.batchDeleteNotes(mContentResolver, mLocalDeleteIdMap)) {
                throw new ActionFailureException("failed to batch-delete local deleted notes");
            }
        }

        // 6. 提交所有更新并刷新本地 sync_id
        if (!mCancelled) {
            GTaskClient.getInstance().commitUpdate();
            refreshLocalSyncId();
        }
    }

    /**
     * 同步文件夹（TaskList），包括根文件夹、通话记录文件夹、普通文件夹。
     *
     * @throws NetworkFailureException 网络异常
     */
    private void syncFolder() throws NetworkFailureException {
        Cursor c = null;
        String gid;
        Node node;
        int syncType;

        if (mCancelled) {
            return;
        }

        // 根文件夹（ID_ROOT_FOLDER）
        try {
            c = mContentResolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                    Notes.ID_ROOT_FOLDER), SqlNote.PROJECTION_NOTE, null, null, null);
            if (c != null) {
                c.moveToNext();
                gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                node = mGTaskHashMap.get(gid);
                if (node != null) {
                    mGTaskHashMap.remove(gid);
                    mGidToNid.put(gid, (long) Notes.ID_ROOT_FOLDER);
                    mNidToGid.put((long) Notes.ID_ROOT_FOLDER, gid);
                    // 仅当远程名称与本地不一致时才更新远程
                    if (!node.getName().equals(
                            GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT))
                        doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
                } else {
                    doContentSync(Node.SYNC_ACTION_ADD_REMOTE, node, c);
                }
            } else {
                Log.w(TAG, "failed to query root folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // 通话记录文件夹（ID_CALL_RECORD_FOLDER）
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE, "(_id=?)",
                    new String[] {
                        String.valueOf(Notes.ID_CALL_RECORD_FOLDER)
                    }, null);
            if (c != null) {
                if (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, (long) Notes.ID_CALL_RECORD_FOLDER);
                        mNidToGid.put((long) Notes.ID_CALL_RECORD_FOLDER, gid);
                        if (!node.getName().equals(
                                GTaskStringUtils.MIUI_FOLDER_PREFFIX
                                        + GTaskStringUtils.FOLDER_CALL_NOTE))
                            doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
                    } else {
                        doContentSync(Node.SYNC_ACTION_ADD_REMOTE, node, c);
                    }
                }
            } else {
                Log.w(TAG, "failed to query call note folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // 普通文件夹（TYPE_FOLDER）
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type=? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_FOLDER), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN));
                        mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid);
                        syncType = node.getSyncAction(c);
                    } else {
                        if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                            syncType = Node.SYNC_ACTION_ADD_REMOTE;
                        } else {
                            syncType = Node.SYNC_ACTION_DEL_LOCAL;
                        }
                    }
                    doContentSync(syncType, node, c);
                }
            } else {
                Log.w(TAG, "failed to query existing folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // 处理远程新增的文件夹
        Iterator<Map.Entry<String, TaskList>> iter = mGTaskListHashMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, TaskList> entry = iter.next();
            gid = entry.getKey();
            node = entry.getValue();
            if (mGTaskHashMap.containsKey(gid)) {
                mGTaskHashMap.remove(gid);
                doContentSync(Node.SYNC_ACTION_ADD_LOCAL, node, null);
            }
        }

        if (!mCancelled)
            GTaskClient.getInstance().commitUpdate();
    }

    /**
     * 根据同步动作类型执行具体操作（新增本地、新增远程、删除、更新、冲突等）。
     *
     * @param syncType 同步动作类型（定义在 Node 中）
     * @param node     远程节点（可能为 null）
     * @param c        本地数据库游标（可能为 null）
     * @throws NetworkFailureException 网络异常
     */
    private void doContentSync(int syncType, Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        MetaData meta;
        switch (syncType) {
            case Node.SYNC_ACTION_ADD_LOCAL:
                addLocalNode(node);
                break;
            case Node.SYNC_ACTION_ADD_REMOTE:
                addRemoteNode(node, c);
                break;
            case Node.SYNC_ACTION_DEL_LOCAL:
                meta = mMetaHashMap.get(c.getString(SqlNote.GTASK_ID_COLUMN));
                if (meta != null) {
                    GTaskClient.getInstance().deleteNode(meta);
                }
                mLocalDeleteIdMap.add(c.getLong(SqlNote.ID_COLUMN));
                break;
            case Node.SYNC_ACTION_DEL_REMOTE:
                meta = mMetaHashMap.get(node.getGid());
                if (meta != null) {
                    GTaskClient.getInstance().deleteNode(meta);
                }
                GTaskClient.getInstance().deleteNode(node);
                break;
            case Node.SYNC_ACTION_UPDATE_LOCAL:
                updateLocalNode(node, c);
                break;
            case Node.SYNC_ACTION_UPDATE_REMOTE:
                updateRemoteNode(node, c);
                break;
            case Node.SYNC_ACTION_UPDATE_CONFLICT:
                // 冲突处理策略：简单使用本地更新覆盖远程
                updateRemoteNode(node, c);
                break;
            case Node.SYNC_ACTION_NONE:
                break;
            case Node.SYNC_ACTION_ERROR:
            default:
                throw new ActionFailureException("unkown sync action type");
        }
    }

    /**
     * 将远程节点新增到本地数据库。
     *
     * @param node 远程节点（TaskList 或 Task）
     * @throws NetworkFailureException 网络异常
     */
    private void addLocalNode(Node node) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote;
        if (node instanceof TaskList) {
            // 处理系统文件夹和普通文件夹
            if (node.getName().equals(
                    GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT)) {
                sqlNote = new SqlNote(mContext, Notes.ID_ROOT_FOLDER);
            } else if (node.getName().equals(
                    GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_CALL_NOTE)) {
                sqlNote = new SqlNote(mContext, Notes.ID_CALL_RECORD_FOLDER);
            } else {
                sqlNote = new SqlNote(mContext);
                sqlNote.setContent(node.getLocalJSONFromContent());
                sqlNote.setParentId(Notes.ID_ROOT_FOLDER);
            }
        } else {
            sqlNote = new SqlNote(mContext);
            JSONObject js = node.getLocalJSONFromContent();
            try {
                // 清理可能冲突的 ID 字段（若数据库中已存在则移除，让数据库自动生成）
                if (js.has(GTaskStringUtils.META_HEAD_NOTE)) {
                    JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
                    if (note.has(NoteColumns.ID)) {
                        long id = note.getLong(NoteColumns.ID);
                        if (DataUtils.existInNoteDatabase(mContentResolver, id)) {
                            note.remove(NoteColumns.ID);
                        }
                    }
                }

                if (js.has(GTaskStringUtils.META_HEAD_DATA)) {
                    JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);
                    for (int i = 0; i < dataArray.length(); i++) {
                        JSONObject data = dataArray.getJSONObject(i);
                        if (data.has(DataColumns.ID)) {
                            long dataId = data.getLong(DataColumns.ID);
                            if (DataUtils.existInDataDatabase(mContentResolver, dataId)) {
                                data.remove(DataColumns.ID);
                            }
                        }
                    }
                }
            } catch (JSONException e) {
                Log.w(TAG, e.toString());
                e.printStackTrace();
            }
            sqlNote.setContent(js);

            Long parentId = mGidToNid.get(((Task) node).getParent().getGid());
            if (parentId == null) {
                Log.e(TAG, "cannot find task's parent id locally");
                throw new ActionFailureException("cannot add local node");
            }
            sqlNote.setParentId(parentId.longValue());
        }

        sqlNote.setGtaskId(node.getGid());
        sqlNote.commit(false);

        // 更新映射关系
        mGidToNid.put(node.getGid(), sqlNote.getId());
        mNidToGid.put(sqlNote.getId(), node.getGid());

        updateRemoteMeta(node.getGid(), sqlNote);
    }

    /**
     * 用远程节点内容更新本地便签。
     *
     * @param node 远程节点
     * @param c    本地数据库游标
     * @throws NetworkFailureException 网络异常
     */
    private void updateLocalNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote = new SqlNote(mContext, c);
        sqlNote.setContent(node.getLocalJSONFromContent());

        Long parentId = (node instanceof Task) ? mGidToNid.get(((Task) node).getParent().getGid())
                : new Long(Notes.ID_ROOT_FOLDER);
        if (parentId == null) {
            Log.e(TAG, "cannot find task's parent id locally");
            throw new ActionFailureException("cannot update local node");
        }
        sqlNote.setParentId(parentId.longValue());
        sqlNote.commit(true);

        updateRemoteMeta(node.getGid(), sqlNote);
    }

    /**
     * 将本地便签新增到远程（创建新的 Task 或 TaskList）。
     *
     * @param node 可为 null（未使用）
     * @param c    本地游标
     * @throws NetworkFailureException 网络异常
     */
    private void addRemoteNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote = new SqlNote(mContext, c);
        Node n;

        if (sqlNote.isNoteType()) {
            // 便签 -> 远程 Task
            Task task = new Task();
            task.setContentByLocalJSON(sqlNote.getContent());

            String parentGid = mNidToGid.get(sqlNote.getParentId());
            if (parentGid == null) {
                Log.e(TAG, "cannot find task's parent tasklist");
                throw new ActionFailureException("cannot add remote task");
            }
            mGTaskListHashMap.get(parentGid).addChildTask(task);

            GTaskClient.getInstance().createTask(task);
            n = (Node) task;

            updateRemoteMeta(task.getGid(), sqlNote);
        } else {
            // 文件夹 -> 远程 TaskList
            TaskList tasklist = null;

            String folderName = GTaskStringUtils.MIUI_FOLDER_PREFFIX;
            if (sqlNote.getId() == Notes.ID_ROOT_FOLDER)
                folderName += GTaskStringUtils.FOLDER_DEFAULT;
            else if (sqlNote.getId() == Notes.ID_CALL_RECORD_FOLDER)
                folderName += GTaskStringUtils.FOLDER_CALL_NOTE;
            else
                folderName += sqlNote.getSnippet();

            // 检查是否已存在同名的远程任务列表
            Iterator<Map.Entry<String, TaskList>> iter = mGTaskListHashMap.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, TaskList> entry = iter.next();
                String gid = entry.getKey();
                TaskList list = entry.getValue();

                if (list.getName().equals(folderName)) {
                    tasklist = list;
                    if (mGTaskHashMap.containsKey(gid)) {
                        mGTaskHashMap.remove(gid);
                    }
                    break;
                }
            }

            if (tasklist == null) {
                tasklist = new TaskList();
                tasklist.setContentByLocalJSON(sqlNote.getContent());
                GTaskClient.getInstance().createTaskList(tasklist);
                mGTaskListHashMap.put(tasklist.getGid(), tasklist);
            }
            n = (Node) tasklist;
        }

        // 更新本地记录的 GTask ID
        sqlNote.setGtaskId(n.getGid());
        sqlNote.commit(false);
        sqlNote.resetLocalModified();
        sqlNote.commit(true);

        mGidToNid.put(n.getGid(), sqlNote.getId());
        mNidToGid.put(sqlNote.getId(), n.getGid());
    }

    /**
     * 将本地便签的更新推送到远程。
     *
     * @param node 远程节点（将被更新）
     * @param c    本地游标
     * @throws NetworkFailureException 网络异常
     */
    private void updateRemoteNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote = new SqlNote(mContext, c);

        node.setContentByLocalJSON(sqlNote.getContent());
        GTaskClient.getInstance().addUpdateNode(node);

        updateRemoteMeta(node.getGid(), sqlNote);

        // 处理任务移动（若父文件夹改变）
        if (sqlNote.isNoteType()) {
            Task task = (Task) node;
            TaskList preParentList = task.getParent();

            String curParentGid = mNidToGid.get(sqlNote.getParentId());
            if (curParentGid == null) {
                Log.e(TAG, "cannot find task's parent tasklist");
                throw new ActionFailureException("cannot update remote task");
            }
            TaskList curParentList = mGTaskListHashMap.get(curParentGid);

            if (preParentList != curParentList) {
                preParentList.removeChildTask(task);
                curParentList.addChildTask(task);
                GTaskClient.getInstance().moveTask(task, preParentList, curParentList);
            }
        }

        // 清除本地修改标志
        sqlNote.resetLocalModified();
        sqlNote.commit(true);
    }

    /**
     * 更新或创建与便签关联的元数据（MetaData）节点。
     *
     * @param gid      远程节点的 GID
     * @param sqlNote  本地便签对象
     * @throws NetworkFailureException 网络异常
     */
    private void updateRemoteMeta(String gid, SqlNote sqlNote) throws NetworkFailureException {
        if (sqlNote != null && sqlNote.isNoteType()) {
            MetaData metaData = mMetaHashMap.get(gid);
            if (metaData != null) {
                metaData.setMeta(gid, sqlNote.getContent());
                GTaskClient.getInstance().addUpdateNode(metaData);
            } else {
                metaData = new MetaData();
                metaData.setMeta(gid, sqlNote.getContent());
                mMetaList.addChildTask(metaData);
                mMetaHashMap.put(gid, metaData);
                GTaskClient.getInstance().createTask(metaData);
            }
        }
    }

    /**
     * 同步完成后刷新本地数据库中的 sync_id 字段。
     *
     * @throws NetworkFailureException 网络异常
     */
    private void refreshLocalSyncId() throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        // 重新获取最新的远程数据
        mGTaskHashMap.clear();
        mGTaskListHashMap.clear();
        mMetaHashMap.clear();
        initGTaskList();

        Cursor c = null;
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type<>? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_SYSTEM), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    String gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    Node node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        ContentValues values = new ContentValues();
                        values.put(NoteColumns.SYNC_ID, node.getLastModified());
                        mContentResolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                                c.getLong(SqlNote.ID_COLUMN)), values, null, null);
                    } else {
                        Log.e(TAG, "something is missed");
                        throw new ActionFailureException(
                                "some local items don't have gid after sync");
                    }
                }
            } else {
                Log.w(TAG, "failed to query local note to refresh sync id");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }
    }

    /**
     * 获取当前同步账户名称。
     *
     * @return 账户名
     */
    public String getSyncAccount() {
        return GTaskClient.getInstance().getSyncAccount().name;
    }

    /**
     * 取消正在进行的同步。
     */
    public void cancelSync() {
        mCancelled = true;
    }
}