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

package net.micode.notes.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 闹钟提醒的广播接收器。
 *
 * 当 {@link android.app.AlarmManager} 设定的时间到达时，系统会发送广播，
 * 该类接收广播后启动 {@link AlarmAlertActivity} 来显示提醒对话框并播放铃声。
 *
 * 注意：由于广播接收器不在 Activity 上下文中，启动 Activity 时必须添加
 * {@link Intent#FLAG_ACTIVITY_NEW_TASK} 标志，否则无法启动。
 *
 */
public class AlarmReceiver extends BroadcastReceiver {

    /**
     * 接收到闹钟广播时调用，启动提醒界面。
     *
     * @param context 上下文（广播接收器上下文）
     * @param intent  触发此接收器的 Intent（通常包含便签的 URI 数据）
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // 将 Intent 的目标 Activity 设为 AlarmAlertActivity
        intent.setClass(context, AlarmAlertActivity.class);
        // 由于是在广播接收器中启动 Activity，必须使用 NEW_TASK 标志
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}