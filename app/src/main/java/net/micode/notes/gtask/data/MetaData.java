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

import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 用于 GTask 同步的元数据节点。
 *
 * 在 Google Tasks 同步过程中，元数据节点用于存储与任务相关联的额外信息，
 * 例如关联任务的 GID（Google Task ID）。该类继承自 {@link Task}，但仅作为
 * 元数据容器，不参与常规的任务同步逻辑。
 *
 * 核心功能：
 *     通过 {@link #setMeta(String, JSONObject)} 将关联的 GID 写入元数据 JSON
 *     从远程 JSON 解析出关联的 GID（{@link #setContentByRemoteJSON}）
 *     其他需要本地操作的方法直接抛出异常，表示不应被调用
 * 
 *
 */
public class MetaData extends Task {
    private final static String TAG = MetaData.class.getSimpleName();

    /** 关联的 Google Tasks 任务 GID */
    private String mRelatedGid = null;

    /**
     * 设置元数据，将指定的 GID 放入 metaInfo JSON 对象中。
     *
     * @param gid      要关联的 Google Tasks 任务 ID
     * @param metaInfo 元数据 JSON 对象（会被修改，添加 META_HEAD_GTASK_ID 字段）
     */
    public void setMeta(String gid, JSONObject metaInfo) {
        try {
            metaInfo.put(GTaskStringUtils.META_HEAD_GTASK_ID, gid);
        } catch (JSONException e) {
            Log.e(TAG, "failed to put related gid");
        }
        setNotes(metaInfo.toString());      // 将 JSON 字符串存入 Task 的 notes 字段
        setName(GTaskStringUtils.META_NODE_NAME);  // 节点名固定为元数据标识
    }

    /**
     * 获取关联的 Google Tasks 任务 GID。
     *
     * @return 关联的 GID，若未解析到则返回 null
     */
    public String getRelatedGid() {
        return mRelatedGid;
    }

    /**
     * 判断该元数据节点是否值得保存。
     * 只有当 notes 字段非空时才需要保存（即含有有效的元数据 JSON）。
     *
     * @return true 如果 notes 不为空，否则 false
     */
    @Override
    public boolean isWorthSaving() {
        return getNotes() != null;
    }

    /**
     * 从远程 JSON 设置当前对象的内容。
     * 解析 notes 字段中的 JSON，提取出关联的 GID 并存入 mRelatedGid。
     *
     * @param js 远程返回的 JSON 对象（已包含 notes 字段）
     */
    @Override
    public void setContentByRemoteJSON(JSONObject js) {
        super.setContentByRemoteJSON(js);
        if (getNotes() != null) {
            try {
                JSONObject metaInfo = new JSONObject(getNotes().trim());
                mRelatedGid = metaInfo.getString(GTaskStringUtils.META_HEAD_GTASK_ID);
            } catch (JSONException e) {
                Log.w(TAG, "failed to get related gid");
                mRelatedGid = null;
            }
        }
    }

    /**
     * 从本地 JSON 设置内容。
     * 元数据节点不应通过本地 JSON 恢复，调用此方法会抛出异常。
     */
    @Override
    public void setContentByLocalJSON(JSONObject js) {
        // this function should not be called
        throw new IllegalAccessError("MetaData:setContentByLocalJSON should not be called");
    }

    /**
     * 获取本地 JSON 内容。
     * 元数据节点不应被本地序列化，调用此方法会抛出异常。
     */
    @Override
    public JSONObject getLocalJSONFromContent() {
        throw new IllegalAccessError("MetaData:getLocalJSONFromContent should not be called");
    }

    /**
     * 根据数据库 Cursor 确定同步动作。
     * 元数据节点不参与直接的数据表同步，调用此方法会抛出异常。
     *
     * @param c 数据库游标（未使用）
     * @return 无返回值，总是抛出异常
     */
    @Override
    public int getSyncAction(Cursor c) {
        throw new IllegalAccessError("MetaData:getSyncAction should not be called");
    }
}