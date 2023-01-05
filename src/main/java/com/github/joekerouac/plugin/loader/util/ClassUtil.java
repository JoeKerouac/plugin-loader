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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;

import com.github.joekerouac.plugin.loader.jar.Handler;

/**
 * class工具
 *
 * @author JoeKerouac
 * @date 2022-12-27 14:23
 * @since 2.0.0
 */
public class ClassUtil {

    /**
     * 获取指定class所在的jar文件，如果所在jar文件在是嵌套jar，则获取最外层jar
     * 
     * @param classInPlugin
     *            class
     * @return class所在的jar文件
     */
    public static File getRootJarFile(Class<?> classInPlugin) {
        URL jarUrl = ClassUtil.where(classInPlugin);
        // 校验当前类肯定在jar包中
        if (!jarUrl.getProtocol().equals("jar")) {
            throw new IllegalStateException(String.format("当前类 [%s] 没有在jar包中执行", classInPlugin.getName()));
        }

        String file = jarUrl.getFile();

        if (!file.startsWith("file:/")) {
            throw new IllegalStateException(String.format("路径格式不对，当前url：[%s]", jarUrl));
        }

        file = file.substring("file:".length(), file.indexOf(Handler.SEPARATOR));
        return new File(file);
    }

    /**
     * 获取指定类所在jar包的路径
     *
     * @param cls
     *            类
     * @return 该类的路径URL，获取失败返回null，注意，该URL可能不能调用{@link URL#openStream()}，例如如果类是lambda表达式的情况
     */
    public static URL where(final Class<?> cls) {
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
                            result = new URL(
                                "jar:".concat(result.toExternalForm()).concat(Handler.SEPARATOR).concat(clsAsResource));
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
