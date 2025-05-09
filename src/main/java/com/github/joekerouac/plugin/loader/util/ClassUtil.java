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
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.joekerouac.plugin.loader.PluginClassLoader;
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
     * ExtClassLoader的类名
     */
    private static final String EXT_CLASS_LOADER_CLASS_NAME;

    /** The package separator character: '.' */
    private static final char PACKAGE_SEPARATOR = '.';

    /** The ".class" file suffix */
    private static final String CLASS_FILE_SUFFIX = ".class";

    static {
        // java8以及以下版本号：1.8.x_xxx、1.7.x_xxx等
        // java8以上、Java17以下（目前是到17，以后升级不知道规则是否会变）版本号：9.x.x、11.x.x、17.x.x等
        int versionNum = parse();

        if (versionNum <= 8) {
            EXT_CLASS_LOADER_CLASS_NAME = "sun.misc.Launcher$ExtClassLoader";
        } else {
            // JDK9开始ExtClassLoader更改为了PlatformClassLoader
            EXT_CLASS_LOADER_CLASS_NAME = "jdk.internal.loader.ClassLoaders$PlatformClassLoader";
        }
    }

    /**
     * 获取extClassLoader
     * 
     * @param current
     *            当前class loader
     * @return extClassLoader
     */
    public static ClassLoader getExtClassLoader(ClassLoader current) {
        // 先尝试从本类的类加载器上查找
        ClassLoader extClassLoader = searchExtClassLoader(ClassUtil.class.getClassLoader());

        // 如果本类加载器上没有查找到，从传入的class loader中查找
        if (extClassLoader == null) {
            extClassLoader = searchExtClassLoader(current);
        }

        // 传入的class loader也没有查找到，从当前线程上下文的class loader中查找
        if (extClassLoader == null) {
            extClassLoader = searchExtClassLoader(Thread.currentThread().getContextClassLoader());
        }

        // 如果还是null，没办法，只能抛出异常了
        if (extClassLoader == null) {
            throw new IllegalArgumentException("当前没有传入ExtClassLoader，系统也无法决策出来ExtClassLoader");
        }

        // 如果类名不一致也抛出异常（此时是外部传入了ExtClassLoader，但是传错了）
        if (!extClassLoader.getClass().getName().startsWith(EXT_CLASS_LOADER_CLASS_NAME)) {
            throw new IllegalArgumentException(
                String.format("传入的ExtClassLoader不是 [%s] 的实例, [%s]", EXT_CLASS_LOADER_CLASS_NAME, extClassLoader));
        }

        return extClassLoader;
    }

    /**
     * 查找ExtClassLoader
     *
     * @param current
     *            当前ClassLoader
     * @return ExtClassLoader，可能为空
     */
    private static ClassLoader searchExtClassLoader(ClassLoader current) {
        if (current == null) {
            return null;
        }

        if (current.getClass().getName().equals(EXT_CLASS_LOADER_CLASS_NAME)) {
            return current;
        } else if (current instanceof PluginClassLoader) {
            return ((PluginClassLoader)current).getExtClassLoader();
        } else {
            return searchExtClassLoader(current.getParent());
        }
    }

    /**
     * 获取指定class的class文件的输入流
     *
     * @param clazz
     *            class
     * @return 对应的输入流
     */
    public static InputStream getClassAsStream(Class<?> clazz) {
        return clazz.getResourceAsStream(getClassFileName(clazz));
    }

    /**
     * 获取class的class文件名（不包含包名，例如：String.class）
     *
     * @param clazz
     *            the class
     * @return .class文件名
     */
    public static String getClassFileName(Class<?> clazz) {
        String className = clazz.getName();
        int lastDotIndex = className.lastIndexOf(PACKAGE_SEPARATOR);
        return className.substring(lastDotIndex + 1) + CLASS_FILE_SUFFIX;
    }

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

    /**
     * 解析java版本号
     * 
     * @return 当前java版本号，这里返回的是major version，如果是java8,则返回8,如果是java17,则返回17
     */
    private static int parse() {
        String s = System.getProperty("java.version");

        if (s == null) {
            throw new NullPointerException();
        }

        // Shortcut to avoid initializing VersionPattern when creating
        // feature-version constants during startup
        if (isSimpleNumber(s)) {
            return Integer.parseInt(s);
        }

        Matcher m = VersionPattern.VSTR_PATTERN.matcher(s);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid version string: '" + s + "'");
        }

        // $VNUM is a dot-separated list of integers of arbitrary length
        String[] split = m.group(VersionPattern.VNUM_GROUP).split("\\.");
        Integer[] version = new Integer[split.length];
        for (int i = 0; i < split.length; i++) {
            version[i] = Integer.parseInt(split[i]);
        }

        return version[0];
    }

    private static boolean isSimpleNumber(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            char lowerBound = (i > 0) ? '0' : '1';
            if (c < lowerBound || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static class VersionPattern {
        // $VNUM(-$PRE)?(\+($BUILD)?(\-$OPT)?)?
        // RE limits the format of version strings
        // ([1-9][0-9]*(?:(?:\.0)*\.[1-9][0-9]*)*)(?:-([a-zA-Z0-9]+))?(?:(\+)(0|[1-9][0-9]*)?)?(?:-([-a-zA-Z0-9.]+))?

        private static final String VNUM = "(?<VNUM>[1-9][0-9]*(?:(?:\\.0)*\\.[1-9][0-9]*)*)";
        private static final String PRE = "(?:-(?<PRE>[a-zA-Z0-9]+))?";
        private static final String BUILD = "(?:(?<PLUS>\\+)(?<BUILD>0|[1-9][0-9]*)?)?";
        private static final String OPT = "(?:-(?<OPT>[-a-zA-Z0-9.]+))?";
        private static final String VSTR_FORMAT = VNUM + PRE + BUILD + OPT;

        static final Pattern VSTR_PATTERN = Pattern.compile(VSTR_FORMAT);

        static final String VNUM_GROUP = "VNUM";
        static final String PRE_GROUP = "PRE";
        static final String PLUS_GROUP = "PLUS";
        static final String BUILD_GROUP = "BUILD";
        static final String OPT_GROUP = "OPT";
    }

}
