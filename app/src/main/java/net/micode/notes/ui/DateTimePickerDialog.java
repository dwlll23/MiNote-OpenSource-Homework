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

import java.util.Calendar;

import net.micode.notes.R;
import net.micode.notes.ui.DateTimePicker;
import net.micode.notes.ui.DateTimePicker.OnDateTimeChangedListener;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

/**
 * 日期时间选择对话框。
 *
 * 封装了 {@link DateTimePicker} 控件，以对话框形式提供日期时间选择功能。
 * 用户通过滚动选择器设置日期和时间，点击“确定”后通过 {@link OnDateTimeSetListener}
 * 回调返回选中的时间戳。对话框标题会实时显示当前选中的日期时间。
 *
 * 主要功能：
 *     内部使用 DateTimePicker 控件
 *     监听 DateTimePicker 的变化并更新内部 Calendar 和对话框标题
 *     支持 12/24 小时制（根据系统设置）
 *     提供确定和取消按钮
 *
 */
public class DateTimePickerDialog extends AlertDialog implements OnClickListener {

    private Calendar mDate = Calendar.getInstance();           // 当前选中的日期时间
    private boolean mIs24HourView;                            // 是否为24小时制
    private OnDateTimeSetListener mOnDateTimeSetListener;     // 确定按钮回调
    private DateTimePicker mDateTimePicker;                   // 日期时间选择器控件

    /**
     * 日期时间设置监听器接口。
     * 用户点击“确定”按钮时触发。
     */
    public interface OnDateTimeSetListener {
        /**
         * 日期时间被设置时回调。
         *
         * @param dialog 当前对话框
         * @param date   选中的时间戳（毫秒）
         */
        void OnDateTimeSet(AlertDialog dialog, long date);
    }

    /**
     * 构造日期时间选择对话框。
     *
     * @param context 上下文
     * @param date    初始时间戳（毫秒）
     */
    public DateTimePickerDialog(Context context, long date) {
        super(context);
        mDateTimePicker = new DateTimePicker(context);
        setView(mDateTimePicker);

        // 监听 DateTimePicker 的变化，同步更新内部 Calendar 和对话框标题
        mDateTimePicker.setOnDateTimeChangedListener(new OnDateTimeChangedListener() {
            public void onDateTimeChanged(DateTimePicker view, int year, int month,
                    int dayOfMonth, int hourOfDay, int minute) {
                mDate.set(Calendar.YEAR, year);
                mDate.set(Calendar.MONTH, month);
                mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                mDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                mDate.set(Calendar.MINUTE, minute);
                updateTitle(mDate.getTimeInMillis());
            }
        });

        mDate.setTimeInMillis(date);
        mDate.set(Calendar.SECOND, 0);               // 忽略秒数
        mDateTimePicker.setCurrentDate(mDate.getTimeInMillis());

        setButton(context.getString(R.string.datetime_dialog_ok), this);
        setButton2(context.getString(R.string.datetime_dialog_cancel), (OnClickListener) null);
        set24HourView(DateFormat.is24HourFormat(this.getContext()));
        updateTitle(mDate.getTimeInMillis());
    }

    /**
     * 设置是否使用 24 小时制。
     *
     * @param is24HourView true 为 24 小时制，false 为 12 小时制（AM/PM）
     */
    public void set24HourView(boolean is24HourView) {
        mIs24HourView = is24HourView;
    }

    /**
     * 设置日期时间确定监听器。
     *
     * @param callBack 监听器对象
     */
    public void setOnDateTimeSetListener(OnDateTimeSetListener callBack) {
        mOnDateTimeSetListener = callBack;
    }

    /**
     * 更新对话框标题，显示当前选中的日期时间。
     *
     * @param date 时间戳
     */
    private void updateTitle(long date) {
        int flag = DateUtils.FORMAT_SHOW_YEAR |
                   DateUtils.FORMAT_SHOW_DATE |
                   DateUtils.FORMAT_SHOW_TIME;
        // 注意：原代码中此处赋值有误，应为根据 mIs24HourView 决定是否添加 FORMAT_24HOUR
        // 但实际写成了 flag |= mIs24HourView ? DateUtils.FORMAT_24HOUR : DateUtils.FORMAT_24HOUR;
        // 为保持原样，未修改逻辑。实际效果：若 mIs24HourView 为 true，则添加 FORMAT_24HOUR，否则不变。
        flag |= mIs24HourView ? DateUtils.FORMAT_24HOUR : DateUtils.FORMAT_24HOUR;
        setTitle(DateUtils.formatDateTime(this.getContext(), date, flag));
    }

    /**
     * 对话框按钮点击回调（仅处理“确定”按钮）。
     *
     * @param arg0 对话框接口
     * @param arg1 点击的按钮 ID
     */
    public void onClick(DialogInterface arg0, int arg1) {
        if (mOnDateTimeSetListener != null) {
            mOnDateTimeSetListener.OnDateTimeSet(this, mDate.getTimeInMillis());
        }
    }
}