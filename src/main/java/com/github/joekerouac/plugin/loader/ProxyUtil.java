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

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;

import com.github.joekerouac.plugin.loader.function.ThrowableTaskWithResult;

/**
 * 代理工具
 *
 * @author JoeKerouac
 * @date 2023-03-20 18:40
 * @since 4.0.0
 */
public class ProxyUtil {

    /**
     * 创建指定类型的对象
     *
     * @param loader
     *            class loader
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
    public static <T> T newInstance(ClassLoader loader, String className, Class<?>[] parameterTypes, Object[] params)
        throws Throwable {
        if (className == null || className.trim().isEmpty()) {
            throw new IllegalArgumentException("要创建的对象的类型不能为空");
        }

        int sizeType = parameterTypes == null ? 0 : parameterTypes.length;
        int sizeParam = params == null ? 0 : params.length;

        if (sizeType != sizeParam) {
            throw new IllegalArgumentException("传入构造器参数类型列表与参数列表长度不一致");
        }

        return runWithWrapper(loader, () -> {
            Class<?> clazz = loader.loadClass(className);
            Constructor<?> constructor = clazz.getConstructor(parameterTypes);
            constructor.setAccessible(true);
            return (T)constructor.newInstance(params);
        });
    }

    /**
     * 创建指定类型的对象的代理，调用接口方法时会自动设置上下文ClassLoader，这在方法中依赖线程上下文ClassLoader时很有效，此时使用代理调用接口方法时
     * 就不用再每次调用{@link #runWithWrapper(ClassLoader, ThrowableTaskWithResult)} )}了
     *
     * @param loader
     *            class loader
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
    public static <T> T newInstanceProxy(ClassLoader loader, Class<T> interfaceClass, String className,
        Class<?>[] parameterTypes, Object[] params) throws Throwable {
        if (interfaceClass == null) {
            throw new IllegalArgumentException("对象所属接口不能为空");
        }

        if (!interfaceClass.isInterface()) {
            throw new IllegalArgumentException(String.format("提供的接口类实际不是接口, class: %s", interfaceClass));
        }

        T obj = newInstance(loader, className, parameterTypes, params);
        return (T)Proxy.newProxyInstance(loader, new Class[] {interfaceClass},
            (proxy, method, args) -> runWithWrapper(loader, () -> method.invoke(obj, args)));

    }

    /**
     * 使用插件类加载器作为当前线程上下文类加载器执行任务
     *
     * @param loader
     *            class loader
     * @param task
     *            待执行的任务
     * @param <T>
     *            任务结果类型
     * @return 任务结果
     * @throws Throwable
     *             异常
     */
    public static <T> T runWithWrapper(ClassLoader loader, ThrowableTaskWithResult<T> task) throws Throwable {
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
     * @param loader
     *            class loader
     * @param task
     *            待执行的任务
     */
    public static void runWithWrapper(ClassLoader loader, Runnable task) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        try {
            task.run();
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

}
