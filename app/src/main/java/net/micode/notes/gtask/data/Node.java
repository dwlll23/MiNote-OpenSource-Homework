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

import org.json.JSONObject;

/**
 * GTask 同步系统中节点的抽象基类。
 *
 * 定义了与 Google Tasks 服务同步所需的基本属性和操作。具体子类包括
 * {@link Task}（普通任务）和 {@link MetaData}（元数据节点）。
 *
 * 同步动作常量定义了节点在本地与远程之间的状态差异及需要执行的操作：
 *     {@link #SYNC_ACTION_NONE}：无同步需求
 *     {@link #SYNC_ACTION_ADD_REMOTE}：需要在远程添加
 *     {@link #SYNC_ACTION_ADD_LOCAL}：需要在本地添加
 *     {@link #SYNC_ACTION_DEL_REMOTE}：需要删除远程
 *     {@link #SYNC_ACTION_DEL_LOCAL}：需要删除本地
 *     {@link #SYNC_ACTION_UPDATE_REMOTE}：需要用本地更新远程
 *     {@link #SYNC_ACTION_UPDATE_LOCAL}：需要用远程更新本地
 *     {@link #SYNC_ACTION_UPDATE_CONFLICT}：存在冲突，需解决
 *     {@link #SYNC_ACTION_ERROR}：同步出错
 *
 */
public abstract class Node {
    public static final int SYNC_ACTION_NONE = 0;               // 无需操作
    public static final int SYNC_ACTION_ADD_REMOTE = 1;         // 远程新增（本地有，远程无）
    public static final int SYNC_ACTION_ADD_LOCAL = 2;          // 本地新增（远程有，本地无）
    public static final int SYNC_ACTION_DEL_REMOTE = 3;         // 删除远程（本地已删）
    public static final int SYNC_ACTION_DEL_LOCAL = 4;          // 删除本地（远程已删）
    public static final int SYNC_ACTION_UPDATE_REMOTE = 5;      // 用本地更新远程（本地较新）
    public static final int SYNC_ACTION_UPDATE_LOCAL = 6;       // 用远程更新本地（远程较新）
    public static final int SYNC_ACTION_UPDATE_CONFLICT = 7;    // 冲突（双方均有修改）
    public static final int SYNC_ACTION_ERROR = 8;              // 错误状态

    private String mGid;           // Google Tasks 中的全局唯一 ID
    private String mName;          // 节点名称（任务的标题或元数据标识）
    private long mLastModified;    // 最后修改时间（毫秒）
    private boolean mDeleted;      // 是否已被标记删除

    public Node() {
        mGid = null;
        mName = "";
        mLastModified = 0;
        mDeleted = false;
    }


    /**
     * 获取创建节点所需的 JSON 动作（用于向远程添加新节点）。
     *
     * @param actionId 动作标识（通常为某种操作码）
     * @return 包含创建信息的 JSON 对象
     */
    public abstract JSONObject getCreateAction(int actionId);

    /**
     * 获取更新节点所需的 JSON 动作（用于向远程更新已有节点）。
     *
     * @param actionId 动作标识
     * @return 包含更新信息的 JSON 对象
     */
    public abstract JSONObject getUpdateAction(int actionId);

    /**
     * 从远程返回的 JSON 数据解析并设置当前节点的内容。
     *
     * @param js 远程 JSON 对象
     */
    public abstract void setContentByRemoteJSON(JSONObject js);

    /**
     * 从本地存储的 JSON 数据恢复当前节点的内容。
     *
     * @param js 本地 JSON 对象
     */
    public abstract void setContentByLocalJSON(JSONObject js);

    /**
     * 将当前节点的内容转换为 JSON 对象，用于本地存储。
     *
     * @return 表示当前节点的 JSON 对象
     */
    public abstract JSONObject getLocalJSONFromContent();

    /**
     * 根据数据库游标中的记录判断当前节点需要执行的同步动作。
     *
     * @param c 指向本地数据库记录的 Cursor
     * @return 同步动作常量（如 SYNC_ACTION_UPDATE_REMOTE 等）
     */
    public abstract int getSyncAction(Cursor c);


    public void setGid(String gid) {
        this.mGid = gid;
    }

    public void setName(String name) {
        this.mName = name;
    }

    public void setLastModified(long lastModified) {
        this.mLastModified = lastModified;
    }

    public void setDeleted(boolean deleted) {
        this.mDeleted = deleted;
    }

    public String getGid() {
        return this.mGid;
    }

    public String getName() {
        return this.mName;
    }

    public long getLastModified() {
        return this.mLastModified;
    }

    public boolean getDeleted() {
        return this.mDeleted;
    }
}