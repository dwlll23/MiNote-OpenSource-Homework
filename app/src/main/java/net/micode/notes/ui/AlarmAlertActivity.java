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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowManager;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;

import java.io.IOException;

/**
 * 便签提醒闹钟的弹窗 Activity。
 *
 * 当设定的提醒时间到达时，系统会启动此 Activity，在屏幕上方显示一个对话框，
 * 展示便签的部分内容（摘要），并播放系统默认的闹钟铃声。用户可以选择：
 *     点击“确定”关闭提醒（停止铃声并退出）
 *     点击“进入”跳转到便签编辑页面（仅在屏幕亮起时显示此按钮）
 *
 * 主要功能：
 *     唤醒屏幕并保持亮屏（如果屏幕已关闭）
 *     从 Intent 中解析便签 ID，并查询便签摘要
 *     显示对话框并播放闹钟铃声（循环播放）
 *     在对话框关闭时停止铃声并结束 Activity
 *
 */
public class AlarmAlertActivity extends Activity implements OnClickListener, OnDismissListener {
    private long mNoteId;           // 触发提醒的便签 ID
    private String mSnippet;        // 便签摘要（截断后显示）
    private static final int SNIPPET_PREW_MAX_LEN = 60;   // 摘要显示最大长度
    MediaPlayer mPlayer;            // 闹钟铃声播放器

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 隐藏标题栏
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        final Window win = getWindow();
        // 允许在锁屏状态下显示此 Activity
        win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        // 如果屏幕当前是关闭的，则添加额外标志：点亮屏幕、保持亮屏等
        if (!isScreenOn()) {
            win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR);
        }

        Intent intent = getIntent();

        // 从 Intent 的 data URI 中解析便签 ID（格式：content://.../note/ID）
        try {
            mNoteId = Long.valueOf(intent.getData().getPathSegments().get(1));
            mSnippet = DataUtils.getSnippetById(this.getContentResolver(), mNoteId);
            // 截断过长的摘要，超出部分添加“...”提示
            mSnippet = mSnippet.length() > SNIPPET_PREW_MAX_LEN ? mSnippet.substring(0,
                    SNIPPET_PREW_MAX_LEN) + getResources().getString(R.string.notelist_string_info)
                    : mSnippet;
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return;
        }

        mPlayer = new MediaPlayer();
        // 检查便签是否存在且可见（未被删除或移入废纸篓）
        if (DataUtils.visibleInNoteDatabase(getContentResolver(), mNoteId, Notes.TYPE_NOTE)) {
            showActionDialog();
            playAlarmSound();
        } else {
            finish();
        }
    }

    /**
     * 检查屏幕是否处于点亮状态。
     *
     * @return true 屏幕亮起，false 屏幕关闭
     */
    private boolean isScreenOn() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm.isScreenOn();
    }

    /**
     * 播放系统默认的闹钟铃声（循环播放）。
     * 根据系统设置决定音频流类型（闹钟流或受静音模式影响的流）。
     */
    private void playAlarmSound() {
        Uri url = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);

        // 获取哪些音频流受静音模式影响
        int silentModeStreams = Settings.System.getInt(getContentResolver(),
                Settings.System.MODE_RINGER_STREAMS_AFFECTED, 0);

        if ((silentModeStreams & (1 << AudioManager.STREAM_ALARM)) != 0) {
            mPlayer.setAudioStreamType(silentModeStreams);
        } else {
            mPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
        }
        try {
            mPlayer.setDataSource(this, url);
            mPlayer.prepare();
            mPlayer.setLooping(true);   // 循环播放，直到用户关闭
            mPlayer.start();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 显示提醒对话框，展示便签摘要。
     * 如果屏幕是亮起的，则显示“进入”按钮；否则只显示“确定”。
     */
    private void showActionDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.app_name);
        dialog.setMessage(mSnippet);
        dialog.setPositiveButton(R.string.notealert_ok, this);          // 确定按钮：仅关闭提醒
        if (isScreenOn()) {
            dialog.setNegativeButton(R.string.notealert_enter, this);   // 进入按钮：跳转编辑页
        }
        dialog.show().setOnDismissListener(this);
    }

    /**
     * 对话框按钮点击回调。
     * - 点击“进入”时，启动 NoteEditActivity 打开对应的便签。
     * - 点击“确定”时，无额外动作（对话框关闭后会停止铃声）。
     *
     * @param dialog 对话框
     * @param which  点击的按钮（BUTTON_NEGATIVE 对应“进入”）
     */
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_NEGATIVE:
                Intent intent = new Intent(this, NoteEditActivity.class);
                intent.setAction(Intent.ACTION_VIEW);
                intent.putExtra(Intent.EXTRA_UID, mNoteId);
                startActivity(intent);
                break;
            default:
                break;
        }
    }

    /**
     * 对话框关闭时回调（无论点击哪个按钮或按返回键都会触发）。
     * 停止闹钟铃声并结束 Activity。
     */
    public void onDismiss(DialogInterface dialog) {
        stopAlarmSound();
        finish();
    }

    /**
     * 停止并释放 MediaPlayer 资源。
     */
    private void stopAlarmSound() {
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
    }
}