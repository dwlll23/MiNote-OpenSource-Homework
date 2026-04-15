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

package net.micode.notes.data;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.util.HashMap;

/**
 * 联系人工具类，用于根据电话号码查询联系人姓名。
 *
 * 主要功能：
 *     通过系统通讯录数据库，根据电话号码匹配联系人姓名
 *     使用 HashMap 缓存查询结果，避免重复查询数据库
 *
 * 使用场景：在便签中记录通话记录时，将电话号码转换为可读的联系人姓名。
 *
 */
public class Contact {
    // 缓存：电话号码 -> 联系人姓名
    private static HashMap<String, String> sContactCache;
    private static final String TAG = "Contact";

    /**
     * 查询联系人的 SQL 选择条件（模板）。
     * 使用 PHONE_NUMBERS_EQUAL 函数进行号码匹配，并限定 MIME 类型为电话号码。
     * 其中的 "+" 占位符会被 PhoneNumberUtils.toCallerIDMinMatch(phoneNumber) 替换。
     */
    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
    + ",?) AND " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
    + " AND " + Data.RAW_CONTACT_ID + " IN "
            + "(SELECT raw_contact_id "
            + " FROM phone_lookup"
            + " WHERE min_match = '+')";

    /**
     * 根据电话号码获取联系人姓名。
     *
     * 查询逻辑：
     *     先检查缓存，命中则直接返回
     *     未命中则构造查询条件，查询 Data.CONTENT_URI
     *     将查询结果存入缓存后返回
     *
     * @param context      上下文对象，用于获取 ContentResolver
     * @param phoneNumber  电话号码
     * @return 联系人姓名；如果未找到或出错则返回 null
     */
    public static String getContact(Context context, String phoneNumber) {
        if(sContactCache == null) {
            sContactCache = new HashMap<String, String>();
        }

        // 命中缓存，直接返回
        if(sContactCache.containsKey(phoneNumber)) {
            return sContactCache.get(phoneNumber);
        }

        // 将电话号码转换为用于匹配的最小格式（如去掉国家代码、非数字字符），并替换 SQL 模板中的 "+"
        String selection = CALLER_ID_SELECTION.replace("+",
                PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));
        Cursor cursor = context.getContentResolver().query(
                Data.CONTENT_URI,
                new String [] { Phone.DISPLAY_NAME },  // 只查询联系人姓名
                selection,
                new String[] { phoneNumber },          // 用于 PHONE_NUMBERS_EQUAL 的比较参数
                null);

        if (cursor != null && cursor.moveToFirst()) {
            try {
                String name = cursor.getString(0);
                sContactCache.put(phoneNumber, name);  // 加入缓存
                return name;
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, " Cursor get string error " + e.toString());
                return null;
            } finally {
                cursor.close();  // 确保关闭游标
            }
        } else {
            Log.d(TAG, "No contact matched with number:" + phoneNumber);
            return null;
        }
    }
}