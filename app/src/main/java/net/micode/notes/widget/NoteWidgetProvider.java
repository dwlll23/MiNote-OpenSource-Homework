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

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;
import android.widget.RemoteViews;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.ui.NoteEditActivity;
import net.micode.notes.ui.NotesListActivity;

/**
 * 桌面小部件（Widget）的基类，提供小部件更新的通用逻辑。
 *
 * 子类需要实现：
 *     {@link #getBgResourceId(int)}：根据背景颜色 ID 返回对应的图片资源
 *     {@link #getLayoutId()}：返回小部件的布局文件 ID
 *     {@link #getWidgetType()}：返回小部件类型（2x2 或 4x4）
 *
 * 主要功能：
 *     在小部件被删除时清除数据库中的关联记录（{@link #onDeleted}）
 *     根据小部件 ID 查询关联的便签信息（摘要、背景色、便签 ID）
 *     更新小部件界面：显示便签摘要或“无内容”提示，设置点击跳转
 *     支持隐私模式（隐私模式下不显示内容，仅提示）
 *
 */
public abstract class NoteWidgetProvider extends AppWidgetProvider {

    // 查询小部件关联便签时使用的投影列
    public static final String[] PROJECTION = new String[]{
            NoteColumns.ID,
            NoteColumns.BG_COLOR_ID,
            NoteColumns.SNIPPET
    };

    // 投影列索引
    public static final int COLUMN_ID = 0;
    public static final int COLUMN_BG_COLOR_ID = 1;
    public static final int COLUMN_SNIPPET = 2;

    private static final String TAG = "NoteWidgetProvider";

    /**
     * 当小部件被删除时，将对应便签的 widget_id 字段重置为无效值，解除关联。
     *
     * @param context      上下文
     * @param appWidgetIds 被删除的小部件 ID 数组
     */
    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        for (int i = 0; i < appWidgetIds.length; i++) {
            context.getContentResolver().update(Notes.CONTENT_NOTE_URI,
                    values,
                    NoteColumns.WIDGET_ID + "=?",
                    new String[]{String.valueOf(appWidgetIds[i])});
        }
    }

    /**
     * 根据小部件 ID 查询关联的便签信息（排除已移至废纸篓的便签）。
     *
     * @param context  上下文
     * @param widgetId 小部件 ID
     * @return 游标，包含便签的 ID、背景色、摘要；若无关联则返回空游标
     */
    private Cursor getNoteWidgetInfo(Context context, int widgetId) {
        return context.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                PROJECTION,
                NoteColumns.WIDGET_ID + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                new String[]{String.valueOf(widgetId), String.valueOf(Notes.ID_TRASH_FOLER)},
                null);
    }

    /**
     * 更新小部件（非隐私模式）。
     *
     * @param context          上下文
     * @param appWidgetManager 小部件管理器
     * @param appWidgetIds     要更新的小部件 ID 数组
     */
    protected void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        update(context, appWidgetManager, appWidgetIds, false);
    }

    /**
     * 更新小部件（可指定隐私模式）。
     * 隐私模式下不显示便签内容，仅提示“访客模式”。
     *
     * @param context          上下文
     * @param appWidgetManager 小部件管理器
     * @param appWidgetIds     要更新的小部件 ID 数组
     * @param privacyMode      是否开启隐私模式（true 时隐藏内容）
     */
    private void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds,
                        boolean privacyMode) {
        for (int i = 0; i < appWidgetIds.length; i++) {
            if (appWidgetIds[i] != AppWidgetManager.INVALID_APPWIDGET_ID) {
                int bgId = ResourceParser.getDefaultBgId(context);
                String snippet = "";
                Intent intent = new Intent(context, NoteEditActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtra(Notes.INTENT_EXTRA_WIDGET_ID, appWidgetIds[i]);
                intent.putExtra(Notes.INTENT_EXTRA_WIDGET_TYPE, getWidgetType());

                Cursor c = getNoteWidgetInfo(context, appWidgetIds[i]);
                if (c != null && c.moveToFirst()) {
                    if (c.getCount() > 1) {
                        Log.e(TAG, "Multiple message with same widget id:" + appWidgetIds[i]);
                        c.close();
                        return;
                    }
                    snippet = c.getString(COLUMN_SNIPPET);
                    bgId = c.getInt(COLUMN_BG_COLOR_ID);
                    intent.putExtra(Intent.EXTRA_UID, c.getLong(COLUMN_ID));
                    intent.setAction(Intent.ACTION_VIEW);
                } else {
                    snippet = context.getResources().getString(R.string.widget_havenot_content);
                    intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
                }

                if (c != null) {
                    c.close();
                }

                RemoteViews rv = new RemoteViews(context.getPackageName(), getLayoutId());
                rv.setImageViewResource(R.id.widget_bg_image, getBgResourceId(bgId));
                intent.putExtra(Notes.INTENT_EXTRA_BACKGROUND_ID, bgId);

                PendingIntent pendingIntent;
                if (privacyMode) {
                    rv.setTextViewText(R.id.widget_text,
                            context.getString(R.string.widget_under_visit_mode));
                    pendingIntent = PendingIntent.getActivity(context, appWidgetIds[i], new Intent(
                            context, NotesListActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
                } else {
                    rv.setTextViewText(R.id.widget_text, snippet);
                    pendingIntent = PendingIntent.getActivity(context, appWidgetIds[i], intent,
                            PendingIntent.FLAG_UPDATE_CURRENT);
                }

                rv.setOnClickPendingIntent(R.id.widget_text, pendingIntent);
                appWidgetManager.updateAppWidget(appWidgetIds[i], rv);
            }
        }
    }


    /**
     * 根据背景颜色 ID 获取对应的小部件背景图片资源。
     *
     * @param bgId 背景颜色 ID（来自 ResourceParser）
     * @return drawable 资源 ID
     */
    protected abstract int getBgResourceId(int bgId);

    /**
     * 获取小部件的布局资源 ID。
     *
     * @return layout 资源 ID
     */
    protected abstract int getLayoutId();

    /**
     * 获取小部件类型（2x2 或 4x4）。
     *
     * @return 小部件类型常量（Notes.TYPE_WIDGET_2X 或 TYPE_WIDGET_4X）
     */
    protected abstract int getWidgetType();
}