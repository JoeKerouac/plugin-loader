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
package com.github.joekerouac.plugin.loader.archive;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author JoeKerouac
 * @date 2022-01-07 16:55
 * @since 1.0.0
 */
public interface Archive extends Iterable<Archive.Entry> {

    /**
     * 根据路径获取entry
     * 
     * @param name
     *            entry名，例如com/test/Test.class
     * @return entry
     */
    Entry getEntry(String name);

    /**
     * 获取archive对应的输入流
     *
     * @return 输入流，如果是目录则返回null
     */
    InputStream getResource() throws IOException;

    /**
     * entry
     */
    interface Entry {

        /**
         * 判断entry是否是目录
         * 
         * @return 如果entry是目录则返回true
         */
        boolean isDirectory();

        /**
         * 获取entry的名字
         * 
         * @return entry的名字
         */
        String getName();

        /**
         * 获取entry的完整名字
         * 
         * @return entry的完整名字
         */
        String getFullName();

        /**
         * 获取entry data
         * 
         * @return entry data
         */
        RandomAccessData getData();

        /**
         * 获取entry对应的输入流
         * 
         * @return 输入流，如果是目录则返回null
         */
        InputStream getResource() throws IOException;

        /**
         * 获取压缩方法，枚举：
         * <li>{@link java.util.zip.ZipEntry#STORED}</li>
         * <li>{@link java.util.zip.ZipEntry#DEFLATED}</li>
         * 
         * @return 压缩方法
         */
        int getMethod();

        /**
         * entry的大小（如果entry被压缩，则表示压缩后的大小）
         * 
         * @return 大小
         */
        int size();

    }

}
