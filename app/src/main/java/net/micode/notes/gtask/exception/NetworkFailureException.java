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

package net.micode.notes.gtask.exception;

/**
 * GTask 同步过程中网络操作失败时抛出的受检异常。
 *
 * 该类继承自 {@link Exception}，用于在网络请求（如连接超时、请求失败、
 * 响应异常等）发生时抛出。调用方必须显式处理此异常（try-catch 或向上抛出）。
 *
 * 典型使用场景：
 *     与 Google Tasks 服务器通信时网络不可用
 *     HTTP 请求返回错误状态码（如 401、500）
 *     JSON 解析前的网络数据获取失败
 *
 */
public class NetworkFailureException extends Exception {
    private static final long serialVersionUID = 2107610287180234136L;

    /**
     * 构造一个无详细消息的异常。
     */
    public NetworkFailureException() {
        super();
    }

    /**
     * 构造一个带有指定详细消息的异常。
     *
     * @param paramString 详细消息（通常用于描述网络失败原因）
     */
    public NetworkFailureException(String paramString) {
        super(paramString);
    }

    /**
     * 构造一个带有指定详细消息和原因（Throwable）的异常。
     *
     * @param paramString   详细消息
     * @param paramThrowable 导致此异常的根本原因（如 IOException）
     */
    public NetworkFailureException(String paramString, Throwable paramThrowable) {
        super(paramString, paramThrowable);
    }
}