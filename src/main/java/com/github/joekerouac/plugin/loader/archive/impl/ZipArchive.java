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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.github.joekerouac.plugin.loader.archive.Archive;
import com.github.joekerouac.plugin.loader.archive.RandomAccessData;
import com.github.joekerouac.plugin.loader.util.Bytes;

import lombok.AllArgsConstructor;

/**
 * @author JoeKerouac
 * @date 2022-01-08 14:57
 * @since 1.0.0
 */
public class ZipArchive implements Archive {

    /**
     * 嵌套jar中的分隔符
     */
    public static final String separator = "!/";

    /**
     * 中央目录header最小大小
     */
    private static final long CENTRAL_DIRECTORY_MINIMUM_SIZE = 46;

    /**
     * local file header固定大小（不包含文件名和扩展字段）
     */
    private static final long LOCAL_FILE_HEADER_SIZE = 30;

    /**
     * 所有entry
     */
    private final Map<String, Entry> entryMap;

    /**
     * entry data
     */
    private final RandomAccessData accessData;

    /**
     * archive的名字
     */
    private final String name;

    public ZipArchive(RandomAccessData randomAccessData, String name) throws IOException {
        this.accessData = randomAccessData;
        this.name = name;
        this.entryMap = new ConcurrentHashMap<>();
        init();
    }

    private void init() throws IOException {
        CentralDirectoryEndRecord centralDirectoryEndRecord = new CentralDirectoryEndRecord(accessData);
        long centralDirectorySize = centralDirectoryEndRecord.getCentralDirectorySize();
        long centralDirectoryStart = centralDirectoryEndRecord.getCentralDirectoryOffset();
        long centralDirectoryOffset = centralDirectoryStart;
        long startOfArchive = centralDirectoryEndRecord.getStartOfArchive();

        while (centralDirectoryOffset - centralDirectoryStart < centralDirectorySize) {
            // 读取中央目录header
            byte[] centralDirectoryHeader = accessData.read(centralDirectoryOffset, CENTRAL_DIRECTORY_MINIMUM_SIZE);

            // 压缩方法
            int method = (int)Bytes.littleEndianValue(centralDirectoryHeader, 10, 2);
            // 文件压缩后的大小，单位byte
            long compressedSize = Bytes.littleEndianValue(centralDirectoryHeader, 20, 4);
            // 文件压缩前的大小，单位byte
            long size = Bytes.littleEndianValue(centralDirectoryHeader, 24, 4);
            // 文件名大小，单位byte
            long fileNameLen = Bytes.littleEndianValue(centralDirectoryHeader, 28, 2);
            // 扩展字段大小，单位byte
            long extFieldLen = Bytes.littleEndianValue(centralDirectoryHeader, 30, 2);
            // 备注大小，单位byte
            long remarkLen = Bytes.littleEndianValue(centralDirectoryHeader, 32, 2);
            // entry的local file header相对于ZIP文件的起始位置
            long localFileHeaderOffset = startOfArchive + Bytes.littleEndianValue(centralDirectoryHeader, 42, 4);

            byte[] fileNameData = accessData.read(centralDirectoryOffset + CENTRAL_DIRECTORY_MINIMUM_SIZE, fileNameLen);
            // 注意，这里实际是有点儿问题的，有些ZIP是不支持UTF8的，我们这里认为都支持，而且目前应该都已经支持了
            String fileName = new String(fileNameData, StandardCharsets.UTF_8);

            // 本地文件头中的扩展字段长度（注意，这个与中央目录中的extFieldLen可能不一样）
            long localFileHeaderExtFieldLen =
                Bytes.littleEndianValue(accessData.read(localFileHeaderOffset + 28, 2), 0, 2);
            // entry起始位置，跳过header
            long entryOffset = localFileHeaderOffset + startOfArchive + LOCAL_FILE_HEADER_SIZE + fileNameLen
                + localFileHeaderExtFieldLen;
            RandomAccessData entryData = accessData.getSubsection(entryOffset, compressedSize);
            entryMap.put(fileName, new ZipEntry(entryData, method, name, fileName, (int)size));

            // 中央目录header实际长度
            long headerLen = CENTRAL_DIRECTORY_MINIMUM_SIZE + fileNameLen + extFieldLen + remarkLen;
            centralDirectoryOffset = centralDirectoryOffset + headerLen;
        }
    }

    @Override
    public Entry getEntry(final String name) {
        return entryMap.get(name);
    }

    @Override
    public InputStream getResource() throws IOException {
        return accessData.getInputStream();
    }

    @Override
    public Iterator<Entry> iterator() {
        return entryMap.values().iterator();
    }

    @AllArgsConstructor
    private static class ZipEntry implements Entry {

        /**
         * 压缩数据
         */
        private final RandomAccessData accessData;

        /**
         * 枚举：
         * <li>{@link java.util.zip.ZipEntry#STORED}</li>
         * <li>{@link java.util.zip.ZipEntry#DEFLATED}</li>
         */
        private final int method;

        /**
         * entry所在的archive的name
         */
        private final String archiveName;

        /**
         * 文件名
         */
        private final String name;

        /**
         * 文件未压缩时的大小
         */
        private final int size;

        @Override
        public boolean isDirectory() {
            return name.endsWith("/");
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getFullName() {
            return archiveName + separator + name;
        }

        @Override
        public InputStream getResource() throws IOException {
            return accessData.getInputStream();
        }

        @Override
        public RandomAccessData getData() {
            return accessData;
        }

        @Override
        public int getMethod() {
            return method;
        }

        @Override
        public int size() {
            return size;
        }
    }

}
