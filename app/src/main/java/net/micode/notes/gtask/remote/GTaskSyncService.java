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
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

/**
 * GTask 同步的后台服务。
 *
 * 该 Service 负责在后台启动或取消 Google Tasks 同步任务，并通过广播向 UI 层
 * 通知同步状态（是否正在同步、进度消息）。外部组件通过静态方法启动服务。
 *
 * 主要功能：
 *     启动同步：创建 {@link GTaskASyncTask} 并执行
 *     取消同步：调用正在执行的 AsyncTask 的取消方法
 *     广播同步进度：在同步进度更新时发送广播，供 {@link net.micode.notes.ui.NotesPreferenceActivity}
 *         等界面接收并更新 UI
 *     同步完成后自动停止服务
 * 使用方式：
 * // 启动同步
 * GTaskSyncService.startSync(activity);
 *
 * // 取消同步
 * GTaskSyncService.cancelSync(context);
 *
 * // 检查是否正在同步
 * boolean isSyncing = GTaskSyncService.isSyncing();
 *
 */
public class GTaskSyncService extends Service {
    // Intent Extra 键名，用于指定同步动作类型
    public final static String ACTION_STRING_NAME = "sync_action_type";

    // 同步动作类型常量
    public final static int ACTION_START_SYNC = 0;   // 启动同步
    public final static int ACTION_CANCEL_SYNC = 1;  // 取消同步
    public final static int ACTION_INVALID = 2;      // 无效动作

    // 广播 Action 名称，用于通知 UI 同步状态变化
    public final static String GTASK_SERVICE_BROADCAST_NAME = "net.micode.notes.gtask.remote.gtask_sync_service";

    // 广播中携带的 Extra 键
    public final static String GTASK_SERVICE_BROADCAST_IS_SYNCING = "isSyncing";   // 是否正在同步（boolean）
    public final static String GTASK_SERVICE_BROADCAST_PROGRESS_MSG = "progressMsg"; // 进度消息（String）

    private static GTaskASyncTask mSyncTask = null;   // 当前正在执行的同步任务（单例模式）
    private static String mSyncProgress = "";         // 当前同步进度消息（供外部查询）

    /**
     * 启动同步任务（如果尚未启动）。
     */
    private void startSync() {
        if (mSyncTask == null) {
            mSyncTask = new GTaskASyncTask(this, new GTaskASyncTask.OnCompleteListener() {
                public void onComplete() {
                    // 同步完成（无论成功/失败/取消）后，清理任务引用并停止服务
                    mSyncTask = null;
                    sendBroadcast("");   // 发送空消息通知 UI 同步结束
                    stopSelf();          // 停止自身服务
                }
            });
            sendBroadcast("");   // 发送开始同步的广播（此时 mSyncTask 已非空）
            mSyncTask.execute(); // 执行异步同步
        }
    }

    /**
     * 取消正在进行的同步任务。
     */
    private void cancelSync() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }

    @Override
    public void onCreate() {
        mSyncTask = null;
    }

    /**
     * 处理通过 startService 传入的 Intent，根据动作类型启动或取消同步。
     *
     * @param intent  包含动作类型的 Intent（通过 ACTION_STRING_NAME 指定）
     * @param flags   启动标志（未使用）
     * @param startId 启动 ID（未使用）
     * @return START_STICKY 表示服务被异常终止后会尝试重新创建
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle bundle = intent.getExtras();
        if (bundle != null && bundle.containsKey(ACTION_STRING_NAME)) {
            switch (bundle.getInt(ACTION_STRING_NAME, ACTION_INVALID)) {
                case ACTION_START_SYNC:
                    startSync();
                    break;
                case ACTION_CANCEL_SYNC:
                    cancelSync();
                    break;
                default:
                    break;
            }
            return START_STICKY;
        }
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * 系统内存不足时，主动取消同步以释放资源。
     */
    @Override
    public void onLowMemory() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // 不提供绑定接口，仅作为启动型服务
    }

    /**
     * 发送广播通知 UI 同步状态变化。
     *
     * @param msg 进度消息（可为空字符串）
     */
    public void sendBroadcast(String msg) {
        mSyncProgress = msg;
        Intent intent = new Intent(GTASK_SERVICE_BROADCAST_NAME);
        intent.putExtra(GTASK_SERVICE_BROADCAST_IS_SYNCING, mSyncTask != null);
        intent.putExtra(GTASK_SERVICE_BROADCAST_PROGRESS_MSG, msg);
        sendBroadcast(intent);
    }

    /**
     * 启动同步（外部调用入口）。
     * 会设置 GTaskManager 的 Activity 上下文（用于获取 AuthToken），然后启动本 Service。
     *
     * @param activity 当前 Activity（用于登录认证）
     */
    public static void startSync(Activity activity) {
        GTaskManager.getInstance().setActivityContext(activity);
        Intent intent = new Intent(activity, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_START_SYNC);
        activity.startService(intent);
    }

    /**
     * 取消同步（外部调用入口）。
     *
     * @param context 上下文
     */
    public static void cancelSync(Context context) {
        Intent intent = new Intent(context, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_CANCEL_SYNC);
        context.startService(intent);
    }

    /**
     * 检查是否正在同步中。
     *
     * @return true 表示正在同步
     */
    public static boolean isSyncing() {
        return mSyncTask != null;
    }

    /**
     * 获取当前的同步进度消息。
     *
     * @return 进度消息字符串
     */
    public static String getProgressString() {
        return mSyncProgress;
    }
}