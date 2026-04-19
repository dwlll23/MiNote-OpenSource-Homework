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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;

import net.micode.notes.R;

/**
 * 下拉菜单控件，封装了一个带下拉箭头的按钮和点击后弹出的 {@link PopupMenu}。
 *
 * 主要功能：
 *     将指定的菜单资源与一个 Button 关联
 *     点击按钮时弹出菜单
 *     提供设置菜单项点击监听、查找菜单项、修改按钮文字的方法
 *
 */
public class DropdownMenu {
    private Button mButton;           // 触发下拉菜单的按钮
    private PopupMenu mPopupMenu;     // 弹出的菜单
    private Menu mMenu;               // 菜单对象（用于后续操作）

    /**
     * 构造下拉菜单控件。
     *
     * @param context  上下文
     * @param button   用于触发菜单的按钮（其背景会被设置为下拉箭头图标）
     * @param menuId   菜单资源 ID（如 R.menu.note_list_dropdown）
     */
    public DropdownMenu(Context context, Button button, int menuId) {
        mButton = button;
        mButton.setBackgroundResource(R.drawable.dropdown_icon);
        mPopupMenu = new PopupMenu(context, mButton);
        mMenu = mPopupMenu.getMenu();
        mPopupMenu.getMenuInflater().inflate(menuId, mMenu);
        mButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mPopupMenu.show();   // 点击按钮时弹出菜单
            }
        });
    }

    /**
     * 设置菜单项的点击监听器。
     *
     * @param listener 菜单项点击回调
     */
    public void setOnDropdownMenuItemClickListener(OnMenuItemClickListener listener) {
        if (mPopupMenu != null) {
            mPopupMenu.setOnMenuItemClickListener(listener);
        }
    }

    /**
     * 根据 ID 查找菜单项。
     *
     * @param id 菜单项 ID
     * @return 对应的 MenuItem，若不存在则返回 null
     */
    public MenuItem findItem(int id) {
        return mMenu.findItem(id);
    }

    /**
     * 设置按钮上显示的标题文字。
     *
     * @param title 标题文本
     */
    public void setTitle(CharSequence title) {
        mButton.setText(title);
    }
}