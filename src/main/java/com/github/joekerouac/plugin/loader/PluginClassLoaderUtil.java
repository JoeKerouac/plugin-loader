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
package com.github.joekerouac.plugin.loader;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.github.joekerouac.plugin.loader.archive.Archive;
import com.github.joekerouac.plugin.loader.exception.ClassLoaderException;

/**
 * PluginClassLoader工具
 *
 * @author JoeKerouac
 * @date 2023-03-20 18:38
 * @since 4.0.0
 */
public class PluginClassLoaderUtil {

    /**
     * sdk中的类应该让父加载器来加载，防止出现不可预知的问题
     */
    private static final String[] NEED_PARENT_LOAD = new String[] {"com.github.joekerouac.plugin.loader."};

    /**
     * 构造器
     *
     * @param archives
     *            添加到class path上的jar集合
     */
    public static PluginClassLoader build(List<Archive> archives) {
        return build(archives, Collections.emptyList(), null, false, null);
    }

    /**
     * 构造器
     *
     * @param archives
     *            添加到class path上的jar集合
     * @param needParentLoad
     *            需要父加载器加载的类
     */
    public static PluginClassLoader build(List<Archive> archives, String[] needParentLoad) {
        return build(archives, Collections.emptyList(), needParentLoad, false, null);
    }

    /**
     * 构造器
     *
     * @param archives
     *            添加到class path上的jar集合
     * @param classpath
     *            classpath
     */
    public static PluginClassLoader build(List<Archive> archives, List<URL> classpath) {
        return build(archives, classpath, null, false, null);
    }

    /**
     * 构造器
     *
     * @param archives
     *            添加到class path上的jar集合，会自动遍历该jar中lib目录下的包
     * @param classpath
     *            添加到class path的其他内容
     * @param needParentLoad
     *            需要父加载器加载的类
     * @param loadByParentAfterFail
     *            当本加载器加载类失败时是否允许父加载器加载，true表示允许
     * @param parent
     *            父加载器，如果为空则使用extClassLoader
     */
    public static PluginClassLoader build(List<Archive> archives, List<URL> classpath, String[] needParentLoad,
        boolean loadByParentAfterFail, ClassLoader parent) {
        List<URL> classpathUrl = new ArrayList<>(classpath == null ? Collections.emptyList() : classpath);
        for (Archive archive : archives) {
            try {
                classpathUrl.add(archive.getUrl());
                Iterator<Archive> nestedArchives = archive.getNestedArchives(Archive.FILTER_ALL,
                    entry -> entry.getName().startsWith("lib/") && entry.getName().endsWith(".jar"));
                nestedArchives.forEachRemaining(a -> {
                    try {
                        classpathUrl.add(a.getUrl());
                    } catch (MalformedURLException e) {
                        // 理论上这里不应该发生的
                        throw new RuntimeException(e);
                    }
                });

            } catch (IOException e) {
                throw new ClassLoaderException(String.format("jar文件读取异常, jar: %s", archive), e);
            }
        }

        String[] finalNeedParentLoad = needParentLoad;

        if (finalNeedParentLoad == null) {
            finalNeedParentLoad = Arrays.copyOf(NEED_PARENT_LOAD, NEED_PARENT_LOAD.length);
        } else {
            finalNeedParentLoad =
                Arrays.copyOf(finalNeedParentLoad, finalNeedParentLoad.length + NEED_PARENT_LOAD.length);
            System.arraycopy(NEED_PARENT_LOAD, 0, finalNeedParentLoad,
                finalNeedParentLoad.length - NEED_PARENT_LOAD.length, NEED_PARENT_LOAD.length);
        }

        return new PluginClassLoader(classpathUrl.toArray(new URL[0]), parent, finalNeedParentLoad,
            loadByParentAfterFail);
    }

}
