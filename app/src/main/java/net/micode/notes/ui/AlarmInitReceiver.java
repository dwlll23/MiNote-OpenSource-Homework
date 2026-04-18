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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 开机或系统时间变化后重新初始化闹钟提醒的广播接收器。
 *
 * <p>当设备重启或系统时间发生改变时，系统会发送 {@link android.intent.action.BOOT_COMPLETED}
 * 或 {@link android.intent.action.TIME_SET} 等广播。该类接收此类广播后，
 * 查询数据库中所有未过期的提醒（alert_date > 当前时间），并为每个提醒重新设置
 * {@link AlarmManager}，确保闹钟在设备重启后仍然有效。
 *
 * 主要功能：
 *     查询所有提醒时间大于当前时间的便签（TYPE_NOTE）
 *     为每个便签创建一个指向 {@link AlarmReceiver} 的 PendingIntent
 *     通过 AlarmManager 设置定时任务，在指定时间唤醒设备并触发广播
 *
 */
public class AlarmInitReceiver extends BroadcastReceiver {

    // 查询提醒便签所需的投影列
    private static final String[] PROJECTION = new String[]{
            NoteColumns.ID,
            NoteColumns.ALERTED_DATE
    };

    // 投影列索引
    private static final int COLUMN_ID = 0;
    private static final int COLUMN_ALERTED_DATE = 1;

    /**
     * 接收到广播时执行：查询所有未过期的提醒便签，并重新设置 AlarmManager。
     *
     * @param context 上下文
     * @param intent  触发该接收器的 Intent（通常为 BOOT_COMPLETED 或 TIME_SET）
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        long currentDate = System.currentTimeMillis();
        // 查询所有提醒时间大于当前时间且类型为便签的记录
        Cursor c = context.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                PROJECTION,
                NoteColumns.ALERTED_DATE + ">? AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE,
                new String[]{String.valueOf(currentDate)},
                null);

        if (c != null) {
            if (c.moveToFirst()) {
                do {
                    long alertDate = c.getLong(COLUMN_ALERTED_DATE);
                    Intent sender = new Intent(context, AlarmReceiver.class);
                    sender.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, c.getLong(COLUMN_ID)));
                    // 使用 FLAG_UPDATE_CURRENT 确保 PendingIntent 更新（如果已存在则更新额外数据）
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, sender, 0);
                    AlarmManager alarmManager = (AlarmManager) context
                            .getSystemService(Context.ALARM_SERVICE);
                    // 设置精确闹钟，在提醒时间唤醒设备
                    alarmManager.set(AlarmManager.RTC_WAKEUP, alertDate, pendingIntent);
                } while (c.moveToNext());
            }
            c.close();
        }
    }
}