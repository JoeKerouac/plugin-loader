/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.github.joekerouac.plugin.loader.util;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

/**
 * Enumeration工具
 *
 * @author JoeKerouac
 * @date 2022-12-26 17:25
 * @since 2.0.0
 */
public class EnumerationUtil {

    /**
     * 将list转换为Enumeration
     * 
     * @param data
     *            list数据
     * @param <T>
     *            数据类型
     * @return Enumeration
     */
    public static <T> Enumeration<T> convert(List<T> data) {
        Iterator<T> iterator = new ArrayList<>(data).iterator();

        return new Enumeration<T>() {
            @Override
            public boolean hasMoreElements() {
                return iterator.hasNext();
            }

            @Override
            public T nextElement() {
                return iterator.next();
            }
        };
    }

    /**
     * 将Enumeration中的数据添加到list中
     * 
     * @param list
     *            list
     * @param data
     *            Enumeration
     * @param <T>
     *            数据类型
     */
    public static <T> void addToList(List<T> list, Enumeration<T> data) {
        if (data == null) {
            return;
        }
        while (data.hasMoreElements()) {
            list.add(data.nextElement());
        }
    }

}
