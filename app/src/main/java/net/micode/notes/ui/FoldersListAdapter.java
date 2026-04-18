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
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 文件夹列表的 CursorAdapter，用于在对话框中显示可移动的目标文件夹列表。
 *
 * 该适配器将数据库中的文件夹（TYPE_FOLDER）和根文件夹（ID_ROOT_FOLDER）以列表形式展示。
 * 根文件夹会显示为特殊的文字（“上级文件夹”），其他文件夹显示其名称（SNIPPET）。
 * 内部使用自定义的 {@link FolderListItem} 作为列表项视图。
 *
 */
public class FoldersListAdapter extends CursorAdapter {

    // 查询文件夹所需的投影列
    public static final String[] PROJECTION = {
            NoteColumns.ID,
            NoteColumns.SNIPPET
    };

    public static final int ID_COLUMN   = 0;   // ID 列索引
    public static final int NAME_COLUMN = 1;   // 文件夹名称列索引

    public FoldersListAdapter(Context context, Cursor c) {
        super(context, c);
    }

    /**
     * 创建新的列表项视图。
     *
     * @param context 上下文
     * @param cursor  当前游标（未使用）
     * @param parent  父视图
     * @return 新建的 FolderListItem 实例
     */
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return new FolderListItem(context);
    }

    /**
     * 将游标中的数据绑定到已有视图上。
     * 对于根文件夹（ID_ROOT_FOLDER），显示特殊文字；否则显示文件夹名称。
     *
     * @param view    要绑定的视图（必须是 FolderListItem 类型）
     * @param context 上下文
     * @param cursor  指向当前行的游标
     */
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (view instanceof FolderListItem) {
            String folderName = (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER) ? context
                    .getString(R.string.menu_move_parent_folder) : cursor.getString(NAME_COLUMN);
            ((FolderListItem) view).bind(folderName);
        }
    }

    /**
     * 获取指定位置的文件夹名称（用于显示）。
     *
     * @param context  上下文
     * @param position 列表位置
     * @return 文件夹显示名称（根文件夹返回特殊文字，否则返回原名称）
     */
    public String getFolderName(Context context, int position) {
        Cursor cursor = (Cursor) getItem(position);
        return (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER) ? context
                .getString(R.string.menu_move_parent_folder) : cursor.getString(NAME_COLUMN);
    }

    /**
     * 文件夹列表项的自定义视图，包含一个 TextView 用于显示文件夹名称。
     */
    private class FolderListItem extends LinearLayout {
        private TextView mName;

        public FolderListItem(Context context) {
            super(context);
            inflate(context, R.layout.folder_list_item, this);
            mName = (TextView) findViewById(R.id.tv_folder_name);
        }

        /**
         * 绑定文件夹名称到 TextView。
         *
         * @param name 文件夹显示名称
         */
        public void bind(String name) {
            mName.setText(name);
        }
    }
}