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
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import com.github.joekerouac.plugin.loader.archive.Archive;
import com.github.joekerouac.plugin.loader.exception.ClassLoaderException;
import com.github.joekerouac.plugin.loader.function.ThrowableTaskWithResult;

import lombok.Getter;

/**
 * 自加载服务
 *
 * @author JoeKerouac
 * @date 2023-01-04 16:55
 * @since 3.0.0
 */
public class AutoloadService {

    /**
     * sdk中的类应该让父加载器来加载，防止出现不可预知的问题
     */
    private static final String[] NEED_PARENT_LOAD = new String[] {"com.github.joekerouac.plugin.loader."};

    /**
     * 插件类加载器
     */
    @Getter
    private final PluginClassLoader loader;

    /**
     * 构造器
     *
     * @param archives
     *            添加到class path上的jar集合
     */
    public AutoloadService(List<Archive> archives) {
        this(archives, Collections.emptyList(), null, false, null);
    }

    /**
     * 构造器
     *
     * @param archives
     *            添加到class path上的jar集合
     * @param needParentLoad
     *            需要父加载器加载的类
     */
    public AutoloadService(List<Archive> archives, String[] needParentLoad) {
        this(archives, Collections.emptyList(), needParentLoad, false, null);
    }

    /**
     * 构造器
     *
     * @param archives
     *            添加到class path上的jar集合
     * @param classpath
     *            添加到class path的其他内容
     * @param needParentLoad
     *            需要父加载器加载的类
     * @param loadByParentAfterFail
     *            当本加载器加载类失败时是否允许父加载器加载，true表示允许
     * @param parent
     *            父加载器，如果为空则使用当前类的加载器作为父加载器
     */
    public AutoloadService(List<Archive> archives, List<URL> classpath, String[] needParentLoad,
        boolean loadByParentAfterFail, ClassLoader parent) {
        List<URL> classpathUrl = new ArrayList<>(classpath);
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

        this.loader = new PluginClassLoader(classpathUrl.toArray(new URL[0]),
            parent == null ? AutoloadService.class.getClassLoader() : parent, finalNeedParentLoad,
            loadByParentAfterFail);
    }

    /**
     * 创建指定类型的对象
     *
     * @param className
     *            类型名
     * @param parameterTypes
     *            要调用的构造器的入参类型
     * @param params
     *            调用指定构造器的参数
     * @param <T>
     *            对象实际类型
     * @return 对象
     * @throws Throwable
     *             异常
     */
    @SuppressWarnings("unchecked")
    public <T> T newInstance(String className, Class<?>[] parameterTypes, Object[] params) throws Throwable {
        if (className == null || className.trim().isEmpty()) {
            throw new IllegalArgumentException("要创建的对象的类型不能为空");
        }

        int sizeType = parameterTypes == null ? 0 : parameterTypes.length;
        int sizeParam = params == null ? 0 : params.length;

        if (sizeType != sizeParam) {
            throw new IllegalArgumentException("传入构造器参数类型列表与参数列表长度不一致");
        }

        return runWithWrapper(() -> {
            Class<?> clazz = loader.loadClass(className);
            Constructor<?> constructor = clazz.getConstructor(parameterTypes);
            return (T)constructor.newInstance(params);
        });
    }

    /**
     * 创建指定类型的对象的代理，调用接口方法时会自动设置上下文ClassLoader，这在方法中依赖线程上下文ClassLoader时很有效，此时使用代理调用接口方法时
     * 就不用再每次调用{@link #runWithWrapper(ThrowableTaskWithResult)}了
     *
     * @param interfaceClass
     *            对象所属接口，该接口类应该是外部ClassLoader加载的，不能为空
     * @param className
     *            类型名，不能为空
     * @param parameterTypes
     *            要调用的构造器的入参类型
     * @param params
     *            调用指定构造器的参数
     * @param <T>
     *            对象实际类型
     * @return 对象
     * @throws Throwable
     *             异常
     */
    @SuppressWarnings("unchecked")
    public <T> T newInstanceProxy(Class<T> interfaceClass, String className, Class<?>[] parameterTypes, Object[] params)
        throws Throwable {
        if (interfaceClass == null) {
            throw new IllegalArgumentException("对象所属接口不能为空");
        }

        if (!interfaceClass.isInterface()) {
            throw new IllegalArgumentException(String.format("提供的接口类实际不是接口, class: %s", interfaceClass));
        }

        T obj = newInstance(className, parameterTypes, params);
        return (T)Proxy.newProxyInstance(loader, new Class[] {interfaceClass},
            (proxy, method, args) -> runWithWrapper(() -> method.invoke(obj, args)));

    }

    /**
     * 使用插件类加载器作为当前线程上下文类加载器执行任务
     *
     * @param task
     *            待执行的任务
     * @param <T>
     *            任务结果类型
     * @return 任务结果
     * @throws Throwable
     *             异常
     */
    public <T> T runWithWrapper(ThrowableTaskWithResult<T> task) throws Throwable {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        try {
            return task.run();
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    /**
     * 使用插件类加载器作为当前线程上下文类加载器执行任务
     *
     * @param task
     *            待执行的任务
     */
    public void runWithWrapper(Runnable task) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        try {
            task.run();
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

}
