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

import android.content.Context;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser.NoteItemBgResources;

/**
 * 便签列表项的自定义视图。
 *
 * 用于 {@link NotesListAdapter} 中，展示单个便签或文件夹的 UI 元素，
 * 包括标题、修改时间、闹钟图标、复选框（多选模式）以及通话记录便签的联系人姓名。
 * 根据数据的不同类型（文件夹、普通便签、通话记录便签）显示不同的布局和样式。
 *
 */
public class NotesListItem extends LinearLayout {
    private ImageView mAlert;      // 闹钟提醒图标
    private TextView mTitle;       // 标题（便签摘要或文件夹名称）
    private TextView mTime;        // 最后修改时间
    private TextView mCallName;    // 通话记录便签的联系人姓名
    private NoteItemData mItemData; // 关联的数据对象
    private CheckBox mCheckBox;    // 多选模式下的复选框

    public NotesListItem(Context context) {
        super(context);
        inflate(context, R.layout.note_item, this);
        mAlert = (ImageView) findViewById(R.id.iv_alert_icon);
        mTitle = (TextView) findViewById(R.id.tv_title);
        mTime = (TextView) findViewById(R.id.tv_time);
        mCallName = (TextView) findViewById(R.id.tv_name);
        mCheckBox = (CheckBox) findViewById(android.R.id.checkbox);
    }

    /**
     * 绑定数据到列表项视图。
     *
     * @param context    上下文
     * @param data       便签数据
     * @param choiceMode 是否处于多选模式
     * @param checked    在多选模式下当前项是否被选中
     */
    public void bind(Context context, NoteItemData data, boolean choiceMode, boolean checked) {
        // 多选模式下且为便签类型时显示复选框，否则隐藏
        if (choiceMode && data.getType() == Notes.TYPE_NOTE) {
            mCheckBox.setVisibility(View.VISIBLE);
            mCheckBox.setChecked(checked);
        } else {
            mCheckBox.setVisibility(View.GONE);
        }

        mItemData = data;

        // 根据数据类型和父文件夹决定显示内容
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            // 通话记录文件夹：显示特殊图标和文件夹名称（含便签数量）
            mCallName.setVisibility(View.GONE);
            mAlert.setVisibility(View.VISIBLE);
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem);
            mTitle.setText(context.getString(R.string.call_record_folder_name)
                    + context.getString(R.string.format_folder_files_count, data.getNotesCount()));
            mAlert.setImageResource(R.drawable.call_record);
        } else if (data.getParentId() == Notes.ID_CALL_RECORD_FOLDER) {
            // 通话记录文件夹下的便签：显示联系人姓名、格式化后的摘要、闹钟图标
            mCallName.setVisibility(View.VISIBLE);
            mCallName.setText(data.getCallName());
            mTitle.setTextAppearance(context, R.style.TextAppearanceSecondaryItem);
            mTitle.setText(DataUtils.getFormattedSnippet(data.getSnippet()));
            if (data.hasAlert()) {
                mAlert.setImageResource(R.drawable.clock);
                mAlert.setVisibility(View.VISIBLE);
            } else {
                mAlert.setVisibility(View.GONE);
            }
        } else {
            // 普通文件夹或便签
            mCallName.setVisibility(View.GONE);
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem);

            if (data.getType() == Notes.TYPE_FOLDER) {
                // 文件夹：显示名称和其中便签数量
                mTitle.setText(data.getSnippet()
                        + context.getString(R.string.format_folder_files_count,
                                data.getNotesCount()));
                mAlert.setVisibility(View.GONE);
            } else {
                // 普通便签：显示格式化后的摘要
                mTitle.setText(DataUtils.getFormattedSnippet(data.getSnippet()));
                if (data.hasAlert()) {
                    mAlert.setImageResource(R.drawable.clock);
                    mAlert.setVisibility(View.VISIBLE);
                } else {
                    mAlert.setVisibility(View.GONE);
                }
            }
        }

        // 显示相对时间（如“5分钟前”）
        mTime.setText(DateUtils.getRelativeTimeSpanString(data.getModifiedDate()));

        // 根据数据类型和位置设置背景资源（用于实现圆角等效果）
        setBackground(data);
    }

    /**
     * 根据便签数据设置列表项的背景图片。
     * 对于便签，根据其在列表中的位置（首项、中间项、末项、单项、跟在文件夹后）使用不同的背景资源；
     * 对于文件夹，使用固定的文件夹背景。
     *
     * @param data 便签数据
     */
    private void setBackground(NoteItemData data) {
        int id = data.getBgColorId();
        if (data.getType() == Notes.TYPE_NOTE) {
            if (data.isSingle() || data.isOneFollowingFolder()) {
                setBackgroundResource(NoteItemBgResources.getNoteBgSingleRes(id));
            } else if (data.isLast()) {
                setBackgroundResource(NoteItemBgResources.getNoteBgLastRes(id));
            } else if (data.isFirst() || data.isMultiFollowingFolder()) {
                setBackgroundResource(NoteItemBgResources.getNoteBgFirstRes(id));
            } else {
                setBackgroundResource(NoteItemBgResources.getNoteBgNormalRes(id));
            }
        } else {
            setBackgroundResource(NoteItemBgResources.getFolderBgRes());
        }
    }

    public NoteItemData getItemData() {
        return mItemData;
    }
}