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

package net.micode.notes.tool;

import android.content.Context;
import android.preference.PreferenceManager;

import net.micode.notes.R;
import net.micode.notes.ui.NotesPreferenceActivity;

/**
 * 资源解析工具类，集中管理便签的背景颜色、字体大小及对应的图片资源。
 *
 * 定义了便签支持的 5 种背景颜色（黄、蓝、白、绿、红）和 4 种字体大小（小、中、大、超大）。
 * 内部类分别提供：
 *     {@link NoteBgResources}：编辑界面中便签的背景和标题栏图片资源
 *     {@link NoteItemBgResources}：列表项的背景图片资源（首项、中间项、末项、单项、文件夹）
 *     {@link WidgetBgResources}：桌面小部件的背景图片资源（2x2 和 4x4）
 *     {@link TextAppearanceResources}：字体样式资源及安全获取方法
 *
 */
public class ResourceParser {

    public static final int YELLOW           = 0;
    public static final int BLUE             = 1;
    public static final int WHITE            = 2;
    public static final int GREEN            = 3;
    public static final int RED              = 4;

    /** 默认背景颜色（黄色） */
    public static final int BG_DEFAULT_COLOR = YELLOW;

    public static final int TEXT_SMALL       = 0;
    public static final int TEXT_MEDIUM      = 1;
    public static final int TEXT_LARGE       = 2;
    public static final int TEXT_SUPER       = 3;

    /** 默认字体大小（中等） */
    public static final int BG_DEFAULT_FONT_SIZE = TEXT_MEDIUM;

    /**
     * 便签编辑界面的背景图片资源。
     */
    public static class NoteBgResources {
        // 便签主体背景资源数组（顺序与颜色 ID 对应）
        private final static int [] BG_EDIT_RESOURCES = new int [] {
            R.drawable.edit_yellow,
            R.drawable.edit_blue,
            R.drawable.edit_white,
            R.drawable.edit_green,
            R.drawable.edit_red
        };

        // 便签标题栏背景资源数组
        private final static int [] BG_EDIT_TITLE_RESOURCES = new int [] {
            R.drawable.edit_title_yellow,
            R.drawable.edit_title_blue,
            R.drawable.edit_title_white,
            R.drawable.edit_title_green,
            R.drawable.edit_title_red
        };

        /**
         * 根据颜色 ID 获取便签主体背景资源。
         *
         * @param id 颜色 ID（YELLOW/BLUE/WHITE/GREEN/RED）
         * @return 对应的 drawable 资源 ID
         */
        public static int getNoteBgResource(int id) {
            return BG_EDIT_RESOURCES[id];
        }

        /**
         * 根据颜色 ID 获取便签标题栏背景资源。
         *
         * @param id 颜色 ID
         * @return 对应的 drawable 资源 ID
         */
        public static int getNoteTitleBgResource(int id) {
            return BG_EDIT_TITLE_RESOURCES[id];
        }
    }

    /**
     * 获取默认背景颜色 ID。
     * 如果用户在设置中开启了随机背景，则返回随机颜色；否则返回默认黄色。
     *
     * @param context 上下文
     * @return 背景颜色 ID
     */
    public static int getDefaultBgId(Context context) {
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                NotesPreferenceActivity.PREFERENCE_SET_BG_COLOR_KEY, false)) {
            return (int) (Math.random() * NoteBgResources.BG_EDIT_RESOURCES.length);
        } else {
            return BG_DEFAULT_COLOR;
        }
    }

    /**
     * 列表项的背景图片资源（首项、中间项、末项、单项、文件夹）。
     */
    public static class NoteItemBgResources {
        // 列表首项背景资源（圆角顶部）
        private final static int [] BG_FIRST_RESOURCES = new int [] {
            R.drawable.list_yellow_up,
            R.drawable.list_blue_up,
            R.drawable.list_white_up,
            R.drawable.list_green_up,
            R.drawable.list_red_up
        };

        // 列表中间项背景资源（无圆角）
        private final static int [] BG_NORMAL_RESOURCES = new int [] {
            R.drawable.list_yellow_middle,
            R.drawable.list_blue_middle,
            R.drawable.list_white_middle,
            R.drawable.list_green_middle,
            R.drawable.list_red_middle
        };

        // 列表末项背景资源（圆角底部）
        private final static int [] BG_LAST_RESOURCES = new int [] {
            R.drawable.list_yellow_down,
            R.drawable.list_blue_down,
            R.drawable.list_white_down,
            R.drawable.list_green_down,
            R.drawable.list_red_down,
        };

        // 列表单项背景资源（全圆角，当列表只有一个项时使用）
        private final static int [] BG_SINGLE_RESOURCES = new int [] {
            R.drawable.list_yellow_single,
            R.drawable.list_blue_single,
            R.drawable.list_white_single,
            R.drawable.list_green_single,
            R.drawable.list_red_single
        };

        public static int getNoteBgFirstRes(int id) {
            return BG_FIRST_RESOURCES[id];
        }

        public static int getNoteBgLastRes(int id) {
            return BG_LAST_RESOURCES[id];
        }

        public static int getNoteBgSingleRes(int id) {
            return BG_SINGLE_RESOURCES[id];
        }

        public static int getNoteBgNormalRes(int id) {
            return BG_NORMAL_RESOURCES[id];
        }

        /** 获取文件夹列表项的背景资源（固定图标） */
        public static int getFolderBgRes() {
            return R.drawable.list_folder;
        }
    }

    /**
     * 桌面小部件的背景图片资源。
     */
    public static class WidgetBgResources {
        // 2x2 小部件背景资源数组
        private final static int [] BG_2X_RESOURCES = new int [] {
            R.drawable.widget_2x_yellow,
            R.drawable.widget_2x_blue,
            R.drawable.widget_2x_white,
            R.drawable.widget_2x_green,
            R.drawable.widget_2x_red,
        };

        public static int getWidget2xBgResource(int id) {
            return BG_2X_RESOURCES[id];
        }

        // 4x4 小部件背景资源数组
        private final static int [] BG_4X_RESOURCES = new int [] {
            R.drawable.widget_4x_yellow,
            R.drawable.widget_4x_blue,
            R.drawable.widget_4x_white,
            R.drawable.widget_4x_green,
            R.drawable.widget_4x_red
        };

        public static int getWidget4xBgResource(int id) {
            return BG_4X_RESOURCES[id];
        }
    }

    /**
     * 字体样式资源。
     */
    public static class TextAppearanceResources {
        // 字体样式资源数组（顺序与字体大小常量对应）
        private final static int [] TEXTAPPEARANCE_RESOURCES = new int [] {
            R.style.TextAppearanceNormal,
            R.style.TextAppearanceMedium,
            R.style.TextAppearanceLarge,
            R.style.TextAppearanceSuper
        };

        /**
         * 安全获取字体样式资源 ID。
         * 若传入的 ID 超出数组长度，则返回默认字体大小（中等）。
         *
         * @param id 字体大小 ID（TEXT_SMALL/MEDIUM/LARGE/SUPER）
         * @return 样式资源 ID
         */
        public static int getTexAppearanceResource(int id) {
            /**
             * HACKME: Fix bug of store the resource id in shared preference.
             * The id may larger than the length of resources, in this case,
             * return the {@link ResourceParser#BG_DEFAULT_FONT_SIZE}
             */
            if (id >= TEXTAPPEARANCE_RESOURCES.length) {
                return BG_DEFAULT_FONT_SIZE;
            }
            return TEXTAPPEARANCE_RESOURCES[id];
        }

        /**
         * 获取字体样式资源的数量。
         *
         * @return 资源数组长度
         */
        public static int getResourcesSize() {
            return TEXTAPPEARANCE_RESOURCES.length;
        }
    }
}