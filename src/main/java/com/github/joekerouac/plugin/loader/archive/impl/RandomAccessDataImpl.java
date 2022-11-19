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
package com.github.joekerouac.plugin.loader.archive.impl;

import java.io.*;
import java.util.Arrays;

import com.github.joekerouac.plugin.loader.archive.RandomAccessData;

/**
 * @author JoeKerouac
 * @date 2022-01-08 17:55
 * @since 1.0.0
 */
public class RandomAccessDataImpl implements RandomAccessData {

    private final RandomAccessFile file;

    private final int offset;

    private final int size;

    public RandomAccessDataImpl(File file) throws FileNotFoundException {
        this(new RandomAccessFile(file, "r"), 0, file.length());
    }

    public RandomAccessDataImpl(RandomAccessFile file, long offset, long size) {
        assert offset <= Integer.MAX_VALUE;
        assert size <= Integer.MAX_VALUE;
        this.file = file;
        this.offset = (int)offset;
        this.size = (int)size;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(read());
    }

    @Override
    public RandomAccessData getSubsection(final long offset, final long length) {
        return new RandomAccessDataImpl(file, offset + this.offset, length);
    }

    @Override
    public byte[] read() throws IOException {
        return read(0, size);
    }

    @Override
    public byte[] read(final long offset, final long length) throws IOException {
        byte[] data = new byte[(int)length];
        synchronized (file) {
            file.seek(offset + this.offset);
            int readLen = file.read(data);

            if (readLen == -1) {
                return new byte[0];
            }

            if (readLen == length) {
                return data;
            }

            // 如果读取长度和实际要去读取的长度不一致，则将真正的数据copy出来返回
            return Arrays.copyOfRange(data, 0, readLen);
        }
    }

    @Override
    public long getSize() {
        return size;
    }
}
