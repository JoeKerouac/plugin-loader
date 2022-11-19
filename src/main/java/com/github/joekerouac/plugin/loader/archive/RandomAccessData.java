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
 * @date 2022-01-08 15:14
 * @since 1.0.0
 */
public interface RandomAccessData {

    /**
     * 获取数据输入流
     * 
     * @return 输入流
     * @throws IOException
     *             IO异常
     */
    InputStream getInputStream() throws IOException;

    /**
     * 获取一个子data
     * 
     * @param offset
     *            起始位置
     * @param length
     *            长度
     * @return 子data
     */
    RandomAccessData getSubsection(long offset, long length);

    /**
     * 读取所有数据
     * 
     * @return 数据
     * @throws IOException
     *             IO异常
     */
    byte[] read() throws IOException;

    /**
     * 从指定位置读取指定长度的数据
     * 
     * @param offset
     *            起始位置，注意，实际只支持int范围的值
     * @param length
     *            读取长度，注意，实际只支持int范围的值
     * @return 读取到的数据
     * @throws IOException
     *             IO异常
     */
    byte[] read(long offset, long length) throws IOException;

    /**
     * 总数据长度
     * 
     * @return 总数据长度
     */
    long getSize();

}
