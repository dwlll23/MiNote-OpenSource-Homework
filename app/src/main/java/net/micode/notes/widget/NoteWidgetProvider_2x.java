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

package net.micode.notes.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.ResourceParser;

/**
 * 2x2 尺寸的桌面小部件提供者。
 *
 * 继承自 {@link NoteWidgetProvider}，实现 2x2 小部件特有的布局、背景资源和类型。
 * 当系统需要更新 2x2 小部件时，会调用 {@link #onUpdate} 方法，最终由父类的通用更新逻辑完成。
 *
 */
public class NoteWidgetProvider_2x extends NoteWidgetProvider {

    /**
     * 当小部件需要更新时调用（如添加小部件或系统请求更新）。
     * 直接调用父类的 update 方法，使用默认的非隐私模式。
     *
     * @param context          上下文
     * @param appWidgetManager 小部件管理器
     * @param appWidgetIds     需要更新的小部件 ID 数组
     */
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.update(context, appWidgetManager, appWidgetIds);
    }

    /**
     * 返回 2x2 小部件的布局资源 ID。
     *
     * @return layout/widget_2x 的布局 ID
     */
    @Override
    protected int getLayoutId() {
        return R.layout.widget_2x;
    }

    /**
     * 根据背景颜色 ID 获取 2x2 小部件对应的背景图片资源。
     *
     * @param bgId 背景颜色 ID（来自 ResourceParser）
     * @return 对应颜色的 2x2 小部件背景 drawable 资源 ID
     */
    @Override
    protected int getBgResourceId(int bgId) {
        return ResourceParser.WidgetBgResources.getWidget2xBgResource(bgId);
    }

    /**
     * 返回小部件类型为 2x2。
     *
     * @return Notes.TYPE_WIDGET_2X
     */
    @Override
    protected int getWidgetType() {
        return Notes.TYPE_WIDGET_2X;
    }
}