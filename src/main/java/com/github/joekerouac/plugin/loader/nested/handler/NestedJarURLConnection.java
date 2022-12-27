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
package com.github.joekerouac.plugin.loader.nested.handler;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.DataFormatException;
import java.util.zip.ZipEntry;

import com.github.joekerouac.plugin.loader.ByteArrayData;
import com.github.joekerouac.plugin.loader.archive.Archive;
import com.github.joekerouac.plugin.loader.archive.impl.FileRandomAccessData;
import com.github.joekerouac.plugin.loader.archive.impl.ZipArchive;
import com.github.joekerouac.plugin.loader.util.ZIPUtils;

/**
 * 嵌套的jar url连接
 *
 * @author JoeKerouac
 * @date 2022-12-24 19:25
 * @since 2.0.0
 */
public class NestedJarURLConnection extends URLConnection {

    private final ZipArchive archive;

    public NestedJarURLConnection(URL url) throws IOException {
        super(url);
        String fileStr = url.getFile();
        int i = fileStr.indexOf(ZipArchive.separator);
        fileStr = fileStr.substring(0, i);
        File file = new File(new URL(fileStr).getFile());
        this.archive = new ZipArchive(new FileRandomAccessData(file), file.toURI().toString());
    }

    @Override
    public void connect() throws IOException {
        this.connected = true;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        String file = url.getFile();
        int i = file.indexOf(ZipArchive.separator);
        if (i < 0 || (i + ZipArchive.separator.length()) == file.length()) {
            return archive.getResource();
        }

        file = file.substring(ZipArchive.separator.length() + i);

        ZipArchive archive = this.archive;
        while (true) {
            i = file.indexOf(ZipArchive.separator);
            if (i < 0 || (i + ZipArchive.separator.length()) == file.length()) {
                Archive.Entry entry = archive.getEntry(file);
                if (entry.getMethod() == ZipEntry.DEFLATED) {
                    ByteArrayData byteArrayData;
                    byte[] data = entry.getData().read();
                    try {
                        byteArrayData = ZIPUtils.decompressNoWrap(data, entry.size());
                    } catch (DataFormatException e) {
                        throw new IOException("zip数据读取失败", e);
                    }
                    return new ByteArrayInputStream(byteArrayData.getData(), byteArrayData.getOffset(),
                        byteArrayData.getLen());
                } else {
                    return entry.getResource();
                }
            } else {
                String entryName = file.substring(0, i);
                Archive.Entry entry = archive.getEntry(entryName);
                if (entry.getMethod() != ZipEntry.STORED) {
                    throw new UnsupportedOperationException("不支持获取非STORED类型嵌套zip中的资源");
                }

                archive = new ZipArchive(entry.getData(), entry.getFullName());
                file = file.substring(i + ZipArchive.separator.length());
            }
        }
    }

}
