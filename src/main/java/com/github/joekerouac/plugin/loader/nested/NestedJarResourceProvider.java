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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.zip.ZipEntry;

import com.github.joekerouac.plugin.loader.ResourceProvider;
import com.github.joekerouac.plugin.loader.archive.Archive;
import com.github.joekerouac.plugin.loader.archive.impl.FileRandomAccessData;
import com.github.joekerouac.plugin.loader.archive.impl.ZipArchive;
import com.github.joekerouac.plugin.loader.exception.ClassLoaderException;
import com.github.joekerouac.plugin.loader.nested.handler.NestedJarURLStreamHandler;

/**
 * 从嵌套jar中查找指定resource，可以检索嵌套jar包中的类，前提是外部嵌套jar的压缩方式是STORED，即嵌套jar（jar中的jar）是以归档的形式存在与jar中，而不是压缩的形式；
 * 
 * 注意，外层jar中的class不会被加载，会交给父加载器加载
 *
 * @author JoeKerouac
 * @date 2021-12-25 09:38
 * @since 2.0.0
 */
public class NestedJarResourceProvider implements ResourceProvider {

    /**
     * 包含嵌套jar的jar包文件
     */
    private final File nestedJar;

    /**
     * entry缓存
     */
    private volatile Map<String, List<URL>> entryCache;

    public NestedJarResourceProvider(final File nestedJar) {
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
    private void buildCache(Archive archive, Map<String, List<URL>> entryCache) throws IOException {
        List<Archive> nestedArchives = new ArrayList<>();

        for (final Archive.Entry entry : archive) {
            entryCache.compute(entry.getName(), (name, list) -> {
                if (list == null) {
                    list = new ArrayList<>();
                }

                try {
                    list.add(new URL(null, NestedJarURLStreamHandler.PROTOCOL + ":" + entry.getFullName(),
                        new NestedJarURLStreamHandler()));
                } catch (MalformedURLException e) {
                    // 理论上不会发生
                    throw new UnsupportedOperationException(e);
                }
                return list;
            });

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
    public List<URL> apply(final String resourceName) {
        Map<String, List<URL>> entryCache = this.entryCache;

        if (entryCache == null) {
            synchronized (nestedJar) {
                entryCache = this.entryCache;

                if (entryCache == null) {
                    entryCache = new HashMap<>();
                    try {
                        buildCache(new ZipArchive(new FileRandomAccessData(nestedJar), nestedJar.toURI().toString()),
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

        return entryCache.getOrDefault(resourceName, Collections.emptyList());
    }

}
