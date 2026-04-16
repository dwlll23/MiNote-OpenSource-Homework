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
 * GTask 同步操作失败时抛出的运行时异常。
 *
 * 该类继承自 {@link RuntimeException}，用于在 GTask 同步过程中
 * （如生成 JSON 动作、解析远程数据、数据库操作等）发生不可恢复的错误时抛出。
 * 使用非受检异常可以简化代码，避免在每一层都显式声明或捕获。
 *
 * 典型使用场景：
 *     {@link net.micode.notes.gtask.data.Task#getCreateAction} 生成 JSON 失败
 *     {@link net.micode.notes.gtask.data.SqlData#commit} 插入或更新失败
 *     网络响应解析异常等
 *
 */
public class ActionFailureException extends RuntimeException {
    private static final long serialVersionUID = 4425249765923293627L;

    /**
     * 构造一个无详细消息的异常。
     */
    public ActionFailureException() {
        super();
    }

    /**
     * 构造一个带有指定详细消息的异常。
     *
     * @param paramString 详细消息（通常用于描述失败原因）
     */
    public ActionFailureException(String paramString) {
        super(paramString);
    }

    /**
     * 构造一个带有指定详细消息和原因（Throwable）的异常。
     *
     * @param paramString   详细消息
     * @param paramThrowable 导致此异常的根本原因
     */
    public ActionFailureException(String paramString, Throwable paramThrowable) {
        super(paramString, paramThrowable);
    }
}