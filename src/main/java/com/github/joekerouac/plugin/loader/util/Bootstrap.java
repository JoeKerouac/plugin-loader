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

import com.github.joekerouac.plugin.loader.ManifestConst;
import com.github.joekerouac.plugin.loader.PluginClassLoader;
import com.github.joekerouac.plugin.loader.PluginClassLoaderUtil;
import com.github.joekerouac.plugin.loader.ProxyUtil;
import com.github.joekerouac.plugin.loader.archive.Archive;
import com.github.joekerouac.plugin.loader.archive.JarFileArchive;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;

/**
 * 用于构建可执行jar时使用，如果需要构建可执行jar，需要把plugin-loader的类解压打包到jar中，然后将jar包的main-class设置为本类，另
 * 外设置${@link ManifestConst#BIZ_MAIN_CLASS}，同时将项目依赖打包到jar包中的lib目录
 *
 * @author JoeKerouac
 * @date 2025-03-21 10:33:22
 * @since 1.0.0
 */
public class Bootstrap {

    public static void main(String[] args) throws Throwable {
        run(args);
    }

    /**
     * 执行main方法
     *
     * @param args
     *            参数
     * @throws Throwable
     *             异常
     */
    private static void run(String[] args) throws Throwable {
        Class<?> clazz = Bootstrap.class;
        ClassLoader currentClassLoader = Thread.currentThread().getContextClassLoader();
        currentClassLoader = currentClassLoader == null ? clazz.getClassLoader() : currentClassLoader;

        URL where = ClassUtil.where(clazz);
        List<Archive> archives = new ArrayList<>();
        List<URL> classpath = PluginClassLoaderUtil.getClasspath();

        JarFileArchive mainArchive;
        // 如果协议是jar，说明当前是已经打包为可执行jar了，那么需要将本jar加入archive，遍历里边的lib
        if (where.getProtocol().equals("jar")) {
            // 获取指定类所在的jar包
            File rootJarFile = ClassUtil.getRootJarFile(clazz);
            mainArchive = new JarFileArchive(rootJarFile);
        } else {
            throw new RuntimeException("不支持解压后执行，解压后直接执行业务main class即可");
        }

        archives.add(mainArchive);
        PluginClassLoader classLoader =
            PluginClassLoaderUtil.build(archives, classpath, new String[0], true, currentClassLoader);

        String bizMainClassName = (String)mainArchive.getManifest().getMainAttributes()
            .get(new Attributes.Name(ManifestConst.BIZ_MAIN_CLASS));
        if (bizMainClassName == null || bizMainClassName.trim().isEmpty()) {
            throw new RuntimeException(String.format("当前Manifest文件中没有指定 [%s]", ManifestConst.BIZ_MAIN_CLASS));
        }

        ProxyUtil.runWithWrapper(classLoader, () -> {
            Class<?> bizMainClass = classLoader.loadClass(bizMainClassName);
            bizMainClass.getMethod("main", String[].class).invoke(null, (Object)args);
            return null;
        });
    }

}
