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
package com.github.joekerouac.plugin.loader.nested;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;

import com.github.joekerouac.plugin.loader.ByteArrayData;
import com.github.joekerouac.plugin.loader.ClassProvider;
import com.github.joekerouac.plugin.loader.archive.Archive;
import com.github.joekerouac.plugin.loader.archive.impl.RandomAccessDataImpl;
import com.github.joekerouac.plugin.loader.archive.impl.ZipArchive;
import com.github.joekerouac.plugin.loader.exception.ClassLoaderException;
import com.github.joekerouac.plugin.loader.util.ZIPUtils;

/**
 * 从嵌套jar中查找指定class，可以检索嵌套jar包中的类，前提是外部嵌套jar的压缩方式是STORED，即嵌套jar（jar中的jar）是以归档的形式存在与jar中，而不是压缩的形式；
 * 
 * 注意，外层jar中的class不会被加载，会交给父加载器加载
 *
 * @author JoeKerouac
 * @date 2021-12-25 09:38
 * @since 1.0.0
 */
public class NestedJarClassProvider implements ClassProvider {

    /**
     * class文件后缀
     */
    private static final String CLASS_SUFFIX = ".class";

    /**
     * 包含嵌套jar的jar包文件
     */
    private final File nestedJar;

    /**
     * entry缓存
     */
    private volatile Map<String, Archive.Entry> entryCache;

    public NestedJarClassProvider(final File nestedJar) {
        this.nestedJar = nestedJar;
    }

    /**
     * 构建缓存
     * 
     * @param archive
     *            archive
     * @param entryCache
     *            当前缓存
     * @throws IOException
     *             IO异常
     */
    private void buildCache(Archive archive, Map<String, Archive.Entry> entryCache) throws IOException {
        List<Archive> nestedArchives = new ArrayList<>();

        for (final Archive.Entry entry : archive) {
            entryCache.putIfAbsent(entry.getName(), entry);
            if (entry.getName().endsWith(".jar") && entry.getMethod() == ZipEntry.STORED) {
                nestedArchives.add(new ZipArchive(entry.getData(), entry.getFullName()));
            }
        }

        // 对嵌套jar进行构建
        for (final Archive nestedArchive : nestedArchives) {
            buildCache(nestedArchive, entryCache);
        }
    }

    @Override
    public ByteArrayData apply(final String className) {
        Map<String, Archive.Entry> entryCache = this.entryCache;

        if (entryCache == null) {
            synchronized (nestedJar) {
                entryCache = this.entryCache;

                if (entryCache == null) {
                    entryCache = new ConcurrentHashMap<>();
                    try {
                        buildCache(new ZipArchive(new RandomAccessDataImpl(nestedJar), nestedJar.getName()),
                            entryCache);
                    } catch (IOException e) {
                        throw new ClassLoaderException("jar包读取异常", e);
                    }
                }

                // cache构建完毕后再赋值给全局缓存
                this.entryCache = entryCache;
                // 构建缓存清理线程
                Thread thread = new Thread(() -> {
                    // 缓存保留5分钟，5分钟后删除缓存
                    long cacheAlive = 1000 * 60 * 5;

                    try {
                        Thread.sleep(cacheAlive);
                    } catch (InterruptedException throwable) {
                        // 忽略异常
                    }

                    // 清空cache，注意，这里直接等于null就行，不要调用clear，因为有可能有其他线程已经引用了这个map
                    this.entryCache = null;
                }, "嵌套jar包加载器类缓存清理线程");
                thread.setDaemon(true);
                thread.start();
            }
        }

        String classPath = className.replaceAll("\\.", "/").concat(CLASS_SUFFIX);
        Archive.Entry entry = entryCache.get(classPath);
        if (entry == null) {
            return null;
        }

        try {
            byte[] data = entry.getData().read();

            if (entry.getMethod() == ZipEntry.DEFLATED) {
                return ZIPUtils.decompressNoWrap(data, entry.size());
            } else {
                return new ByteArrayData(data, 0, data.length);
            }
        } catch (Throwable e) {
            throw new RuntimeException(String.format("class [%s] 读取失败，所在entry: [%s]", classPath, entry.getName()), e);
        }
    }

}
