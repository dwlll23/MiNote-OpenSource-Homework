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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import net.micode.notes.R;
import net.micode.notes.ui.NotesListActivity;
import net.micode.notes.ui.NotesPreferenceActivity;

/**
 * GTask 同步的异步任务封装。
 *
 * 继承自 {@link AsyncTask}，在后台线程中执行 Google Tasks 同步操作，
 * 并通过通知栏向用户展示同步进度和结果。同时支持取消同步和进度更新。
 *
 * 主要功能：
 *     在后台执行 {@link GTaskManager#sync} 同步逻辑
 *     通过通知栏显示同步进度、成功、失败或取消状态
 *     支持取消同步（{@link #cancelSync()}）
 *     同步完成后通过 {@link OnCompleteListener} 回调通知调用方
 */
public class GTaskASyncTask extends AsyncTask<Void, String, Integer> {

    private static final int GTASK_SYNC_NOTIFICATION_ID = 5234235;       // 通知 ID
    private static final String GTASK_SYNC_CHANNEL_ID = "gtask_sync_channel"; // 通知渠道 ID（Android 8.0+）

    /**
     * 同步完成监听器接口。
     */
    public interface OnCompleteListener {
        /**
         * 同步任务完成时回调（无论成功或失败）。
         */
        void onComplete();
    }

    private Context mContext;
    private NotificationManager mNotifiManager;
    private GTaskManager mTaskManager;                // GTask 同步管理器
    private OnCompleteListener mOnCompleteListener;

    /**
     * 构造 GTask 异步任务。
     *
     * @param context  上下文，用于获取通知服务和显示资源
     * @param listener 同步完成后的回调（可为 null）
     */
    public GTaskASyncTask(Context context, OnCompleteListener listener) {
        mContext = context;
        mOnCompleteListener = listener;
        mNotifiManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
        mTaskManager = GTaskManager.getInstance();
        createNotificationChannelIfNeeded();   // Android 8.0+ 创建通知渠道
    }

    /**
     * 取消正在进行的同步。
     */
    public void cancelSync() {
        mTaskManager.cancelSync();
    }

    /**
     * 发布进度消息（供 GTaskManager 调用）。
     *
     * @param message 进度文本
     */
    public void publishProgess(String message) {
        publishProgress(new String[]{message});
    }

    /**
     * 创建通知渠道（Android 8.0 以上需要）。
     */
    private void createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    GTASK_SYNC_CHANNEL_ID,
                    mContext.getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Google Task sync notifications");
            mNotifiManager.createNotificationChannel(channel);
        }
    }

    /**
     * 显示通知栏消息。
     *
     * @param tickerId 通知栏提示文字的资源 ID（如 R.string.ticker_syncing）
     * @param content  通知内容文本
     */
    private void showNotification(int tickerId, String content) {
        Intent intent;
        // 根据同步结果决定点击通知后跳转的界面：成功跳转便签列表，失败/进行中跳转设置页
        if (tickerId != R.string.ticker_success) {
            intent = new Intent(mContext, NotesPreferenceActivity.class);
        } else {
            intent = new Intent(mContext, NotesListActivity.class);
        }

        int flags;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        } else {
            flags = PendingIntent.FLAG_UPDATE_CURRENT;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, flags);

        Notification notification = new NotificationCompat.Builder(mContext, GTASK_SYNC_CHANNEL_ID)
                .setSmallIcon(R.drawable.notification)
                .setContentTitle(mContext.getString(R.string.app_name))
                .setContentText(content)
                .setTicker(mContext.getString(tickerId))
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_LIGHTS)
                .setContentIntent(pendingIntent)
                .build();

        NotificationManagerCompat.from(mContext)
                .notify(GTASK_SYNC_NOTIFICATION_ID, notification);
    }

    /**
     * 后台执行同步操作。
     *
     * @param unused 无参数
     * @return 同步结果状态码（如 STATE_SUCCESS、STATE_NETWORK_ERROR 等）
     */
    @Override
    protected Integer doInBackground(Void... unused) {
        publishProgess(mContext.getString(
                R.string.sync_progress_login,
                NotesPreferenceActivity.getSyncAccountName(mContext)
        ));
        return mTaskManager.sync(mContext, this);
    }

    /**
     * 进度更新时调用（在主线程），显示通知栏进度并可选地发送广播。
     *
     * @param progress 进度消息数组
     */
    @Override
    protected void onProgressUpdate(String... progress) {
        showNotification(R.string.ticker_syncing, progress[0]);
        // 如果当前上下文是 GTaskSyncService，则通过广播发送进度（用于 UI 更新）
        if (mContext instanceof GTaskSyncService) {
            ((GTaskSyncService) mContext).sendBroadcast(progress[0]);
        }
    }

    /**
     * 同步完成时调用（在主线程），根据结果显示相应的通知，并触发完成回调。
     *
     * @param result 同步结果状态码
     */
    @Override
    protected void onPostExecute(Integer result) {
        if (result == GTaskManager.STATE_SUCCESS) {
            showNotification(
                    R.string.ticker_success,
                    mContext.getString(R.string.success_sync_account, mTaskManager.getSyncAccount())
            );
            NotesPreferenceActivity.setLastSyncTime(mContext, System.currentTimeMillis());
        } else if (result == GTaskManager.STATE_NETWORK_ERROR) {
            showNotification(
                    R.string.ticker_fail,
                    mContext.getString(R.string.error_sync_network)
            );
        } else if (result == GTaskManager.STATE_INTERNAL_ERROR) {
            showNotification(
                    R.string.ticker_fail,
                    mContext.getString(R.string.error_sync_internal)
            );
        } else if (result == GTaskManager.STATE_SYNC_CANCELLED) {
            showNotification(
                    R.string.ticker_cancel,
                    mContext.getString(R.string.error_sync_cancelled)
            );
        }

        // 触发完成回调（在新线程中执行，避免阻塞主线程）
        if (mOnCompleteListener != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    mOnCompleteListener.onComplete();
                }
            }).start();
        }
    }
}