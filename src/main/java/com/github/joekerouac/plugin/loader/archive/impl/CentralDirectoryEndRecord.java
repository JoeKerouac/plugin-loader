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

import java.io.IOException;

import com.github.joekerouac.plugin.loader.archive.RandomAccessData;
import com.github.joekerouac.plugin.loader.util.Bytes;

import lombok.Getter;

/**
 * @author JoeKerouac
 * @date 2022-01-08 15:17
 * @since 1.0.0
 */
public class CentralDirectoryEndRecord {

    /**
     * EOCD最小长度
     */
    private static final int MINIMUM_SIZE = 22;

    /**
     * EOCD结尾的comment最大长度
     */
    private static final int MAXIMUM_COMMENT_LENGTH = 0xffff;

    /**
     * EOCD签名
     */
    private static final int SIGNATURE = 0x06054b50;

    /**
     * EOCD最大长度
     */
    private static final int MAXIMUM_SIZE = MINIMUM_SIZE + MAXIMUM_COMMENT_LENGTH;

    /**
     * 中央目录大小，单位byte
     */
    @Getter
    private final long centralDirectorySize;

    /**
     * 中央目录实际起始位置
     */
    @Getter
    private final long centralDirectoryOffset;

    /**
     * 第一个entry的起始位置
     */
    @Getter
    private final long startOfArchive;

    public CentralDirectoryEndRecord(RandomAccessData randomAccessData) throws IOException {
        if (randomAccessData.getSize() < MINIMUM_SIZE) {
            throw new RuntimeException("zip格式不对");
        }

        long dataSize = randomAccessData.getSize();
        // 初始化读取大小，对于大多数ZIP来说EOCD都不会太大
        int bufferSize = (int)Math.min(256, dataSize);

        byte[] block = randomAccessData.read(dataSize - bufferSize, bufferSize);
        long eocdSize = MINIMUM_SIZE;
        long offset = block.length - eocdSize;

        boolean foundEOCD;
        while (true) {
            // 校验签名，如果签名一致，冗余校验下header中声明的EOCD长度与实际长度是否一致
            foundEOCD = Bytes.littleEndianValue(block, (int)offset, 4) == SIGNATURE
                && (Bytes.littleEndianValue(block, (int)offset + 20, 2) + 22) == eocdSize;

            if (foundEOCD) {
                break;
            }

            eocdSize += 1;

            if (eocdSize > block.length) {
                if (eocdSize > dataSize) {
                    // 数据已经遍历完了还没有找到
                    break;
                }

                long read = Math.min(block.length * 3L / 2, dataSize);
                block = randomAccessData.read(dataSize - read, read);
            }

            offset = block.length - eocdSize;
        }

        if (!foundEOCD) {
            throw new RuntimeException("数据中没有找到EOCD");
        }

        centralDirectorySize = Bytes.littleEndianValue(block, ((int)offset + 12), 4);

        if (centralDirectorySize == 0xffffffff) {
            throw new RuntimeException("不支持ZIP64");
        }

        // 中央目录实际的偏移位置
        centralDirectoryOffset = dataSize - eocdSize - centralDirectorySize;

        long actualOffset = dataSize - eocdSize - centralDirectorySize;
        // EOCD中记录的中央目录的偏移位置，从第一个entry开始起算
        long specifiedOffset = Bytes.littleEndianValue(block, ((int)offset + 16), 4);
        // 使用实际的central directory起始位置减去EOCD中声明的central directory起始位置计算zip文件中data的起始位置
        this.startOfArchive = actualOffset - specifiedOffset;
    }
}
