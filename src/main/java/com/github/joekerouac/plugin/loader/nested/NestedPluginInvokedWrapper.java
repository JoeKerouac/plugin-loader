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
import java.net.MalformedURLException;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Arrays;

import com.github.joekerouac.plugin.loader.PluginClassLoader;
import com.github.joekerouac.plugin.loader.archive.impl.ZipArchive;
import com.github.joekerouac.plugin.loader.function.ThrowableTaskWithResult;

import lombok.Getter;

/**
 * 插件启动类，插件中对任意方法的调用都应该使用该包装器包装
 * 
 * @author JoeKerouac
 * @date 2021-12-25 11:05
 * @since 1.0.0
 */
public class NestedPluginInvokedWrapper {

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
     * @param classInPlugin
     *            插件中的类
     * @param needParentLoad
     *            需要父加载器加载的类
     */
    public NestedPluginInvokedWrapper(Class<?> classInPlugin, String[] needParentLoad) {
        this(classInPlugin, needParentLoad, false, null);
    }

    /**
     * 构造器
     *
     * @param classInPlugin
     *            插件中的类
     * @param needParentLoad
     *            需要父加载器加载的类
     * @param loadByParentAfterFail
     *            当本加载器加载类失败时是否允许父加载器加载，true表示允许
     * @param parent
     *            父加载器，如果为空则使用当前类的加载器作为父加载器
     */
    public NestedPluginInvokedWrapper(Class<?> classInPlugin, String[] needParentLoad, boolean loadByParentAfterFail,
        ClassLoader parent) {
        URL jarUrl = where(classInPlugin);
        // 校验当前类肯定在jar包中
        if (!jarUrl.getProtocol().equals("jar")) {
            throw new IllegalStateException(String.format("当前类 [%s] 没有在jar包中执行", classInPlugin.getName()));
        }
        String file = jarUrl.getFile();

        if (!file.startsWith("file:/")) {
            throw new IllegalStateException(String.format("路径格式不对，当前url：[%s]", jarUrl));
        }

        file = file.substring("file:".length(), file.indexOf(ZipArchive.separator));

        String[] finalNeedParentLoad = needParentLoad;

        if (finalNeedParentLoad == null) {
            finalNeedParentLoad = Arrays.copyOf(NEED_PARENT_LOAD, NEED_PARENT_LOAD.length);
        } else {
            finalNeedParentLoad =
                Arrays.copyOf(finalNeedParentLoad, finalNeedParentLoad.length + NEED_PARENT_LOAD.length);
            System.arraycopy(NEED_PARENT_LOAD, 0, finalNeedParentLoad,
                finalNeedParentLoad.length - NEED_PARENT_LOAD.length, NEED_PARENT_LOAD.length);
        }

        this.loader = PluginClassLoader.builder().loadByParentAfterFail(loadByParentAfterFail)
            .parent(parent != null ? parent : NestedPluginInvokedWrapper.class.getClassLoader())
            .needLoadByParent(finalNeedParentLoad).provider(new NestedJarResourceProvider(new File(file))).build();
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
     * 获取指定类所在jar包的路径
     *
     * @param cls
     *            类
     * @return 该类的路径URL，获取失败返回null，注意，该URL可能不能调用{@link URL#openStream()}，例如如果类是lambda表达式的情况
     */
    private static URL where(final Class<?> cls) {
        if (cls == null) {
            throw new IllegalArgumentException("null input: cls");
        }

        URL result = null;
        final String clsAsResource = cls.getName().replace('.', '/').concat(".class");
        final ProtectionDomain pd = cls.getProtectionDomain();
        if (pd != null) {
            final CodeSource cs = pd.getCodeSource();
            if (cs != null) {
                result = cs.getLocation();
            }

            if (result != null) {
                if ("file".equals(result.getProtocol())) {
                    try {
                        if (result.toExternalForm().endsWith(".jar") || result.toExternalForm().endsWith(".zip")) {
                            result = new URL("jar:".concat(result.toExternalForm()).concat(ZipArchive.separator)
                                .concat(clsAsResource));
                        } else if (new File(result.getFile()).isDirectory()) {
                            result = new URL(result, clsAsResource);
                        }
                    } catch (MalformedURLException ignore) {
                    }
                }
            }
        }
        if (result == null) {
            final ClassLoader clsLoader = cls.getClassLoader();
            result =
                clsLoader != null ? clsLoader.getResource(clsAsResource) : ClassLoader.getSystemResource(clsAsResource);
        }

        return result;
    }

}
