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

import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLStreamHandlerFactory;
import java.util.Arrays;
import java.util.Optional;

import com.github.joekerouac.plugin.loader.counter.NopClassLoadCounter;
import com.github.joekerouac.plugin.loader.counter.SunClassLoadCounter;

import lombok.Builder;

/**
 * 定制插件类加载器，特性如下：
 * 
 * <li>打破双亲委派，只有指定类才会优先从父加载器中加载，否则优先从本加载器中加载（Java核心包系统已经强制指定从父加载器中加载了）</li>
 *
 * @author JoeKerouac
 * @date 2021-12-23 11:05
 * @since 1.0.0
 */
public final class PluginClassLoader extends URLClassLoader {

    /**
     * ExtClassLoader的类名
     */
    private static final String EXT_CLASS_LOADER_CLASS_NAME;

    /**
     * 类加载信息计数
     */
    private static final ClassLoadCounter COUNTER;

    static {
        // java8以及以下版本号：1.8.x_xxx、1.7.x_xxx等
        // java8以上、Java17以下（目前是到17，以后升级不知道规则是否会变）版本号：9.x.x、11.x.x、17.x.x等
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            version = version.substring(0, version.indexOf("."));
        }

        int versionNum = Integer.parseInt(version);

        if (versionNum <= 8) {
            EXT_CLASS_LOADER_CLASS_NAME = "sun.misc.Launcher$ExtClassLoader";
        } else {
            // JDK9开始ExtClassLoader更改为了PlatformClassLoader
            EXT_CLASS_LOADER_CLASS_NAME = "jdk.internal.loader.ClassLoaders$PlatformClassLoader";
        }

        try {
            ClassLoadCounter counter;
            try {
                // JDK11中还有这个类，17中没有了
                Class.forName("sun.misc.PerfCounter");
                // 上边类反射异常后这里不会被调用，SunClassLoadCounter类也不会加载
                counter = new SunClassLoadCounter();
            } catch (ClassNotFoundException e) {
                counter = new NopClassLoadCounter();
            }

            COUNTER = counter;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 需要父加载器加载的类
     */
    private final String[] needLoadByParent;

    /**
     * 当本加载器加载类失败时是否允许父加载器加载
     */
    private final boolean loadByParentAfterFail;

    /**
     * 父加载器，不能为空
     */
    private final ClassLoader parent;

    /**
     * sun.misc.Launcher$ExtClassLoader，父级是BootstrapClassLoader，BootstrapClassLoader用于加载系统核心class，而ExtClassLoader
     * 加载系统扩展包，最后我们应用的class以及命令行指定的class path上的class是由sun.misc.Launcher$AppClassLoader（继承ExtClassLoader）加载的
     */
    private final ClassLoader extClassLoader;

    /**
     * class提供者，如果urls里边的jar包都找不到指定类，那么将会去这里搜索
     */
    private final ClassProvider provider;

    /**
     * 插件类加载器
     *
     * @param classpath
     *            插件jar包链接，允许为null
     * @param parent
     *            父加载器，不能为空
     * @param extClassLoader
     *            ExtClassLoader，允许为空，为空时必须能从parent或者本类的加载器上获取到
     * @param urlStreamHandlerFactory
     *            通过URL获取输入流的工厂，允许为null
     * @param needLoadByParent
     *            必须父加载器来加载的类，允许为null
     * @param loadByParentAfterFail
     *            当本加载器加载类失败时是否允许父加载器加载，true表示允许
     * @param classProvider
     *            class提供者，如果urls里边的jar包都找不到指定类，那么将会去这里搜索，允许为null
     */
    @Builder
    private PluginClassLoader(URL[] classpath, ClassLoader parent, ClassLoader extClassLoader,
        URLStreamHandlerFactory urlStreamHandlerFactory, String[] needLoadByParent, boolean loadByParentAfterFail,
        ClassProvider classProvider) {
        super(classpath == null ? new URL[0] : classpath, parent, urlStreamHandlerFactory);
        if (parent == null) {
            throw new NullPointerException("parent不能为空");
        }

        this.parent = parent;
        this.provider = classProvider;
        this.needLoadByParent =
            needLoadByParent == null ? new String[0] : Arrays.copyOfRange(needLoadByParent, 0, needLoadByParent.length);

        this.loadByParentAfterFail = loadByParentAfterFail;

        ClassLoader usedExtClassLoader = extClassLoader;
        // 如果外部没有传，我们自己决策
        // 先尝试从本类的类加载器上查找
        if (usedExtClassLoader == null) {
            usedExtClassLoader = searchExtClassLoader(PluginClassLoader.class.getClassLoader());
        }

        // 如果本类加载器上没有查找到，从传入的class loader中查找
        if (usedExtClassLoader == null) {
            usedExtClassLoader = searchExtClassLoader(parent);
        }

        // 传入的class loader也没有查找到，从当前线程上下文的class loader中查找
        if (usedExtClassLoader == null) {
            usedExtClassLoader = searchExtClassLoader(Thread.currentThread().getContextClassLoader());
        }

        // 如果还是null，没办法，只能抛出异常了
        if (usedExtClassLoader == null) {
            throw new IllegalArgumentException("当前没有传入ExtClassLoader，系统也无法决策出来ExtClassLoader");
        }

        // 如果类名不一致也抛出异常（此时是外部传入了ExtClassLoader，但是传错了）
        if (!usedExtClassLoader.getClass().getName().startsWith(EXT_CLASS_LOADER_CLASS_NAME)) {
            throw new IllegalArgumentException(
                String.format("传入的ExtClassLoader不是 [%s] 的实例, [%s]", EXT_CLASS_LOADER_CLASS_NAME, usedExtClassLoader));
        }
        this.extClassLoader = usedExtClassLoader;
    }

    /**
     * 查找ExtClassLoader
     * 
     * @param current
     *            当前ClassLoader
     * @return ExtClassLoader，可能为空
     */
    private ClassLoader searchExtClassLoader(ClassLoader current) {
        if (current == null) {
            return null;
        }

        if (current.getClass().getName().equals(EXT_CLASS_LOADER_CLASS_NAME)) {
            return current;
        } else {
            return searchExtClassLoader(current.getParent());
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> clazz = Optional.ofNullable(provider).map(p -> p.apply(name))
            .map(data -> defClass(name, data.getData(), data.getOffset(), data.getLen())).orElse(null);

        // 如果provider中不能提供类，那么调用URLClassLoader的findClass尝试从提供的URL中查找类，如果还查找不到就抛出异常
        if (clazz == null) {
            clazz = super.findClass(name);
        }

        return clazz;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // 先判断是不是必须父类加载的，如果类是系统类或者是用户指定了需要父加载器加载的，使用父加载器加载
        boolean loadByParent = extClassLoader.getResource(name.replaceAll("\\.", "/").concat(".class")) != null
            || Arrays.stream(needLoadByParent).anyMatch(name::startsWith);

        // 加锁，准备加载
        synchronized (getClassLoadingLock(name)) {
            // 先查找已经加载过的类
            Class<?> clazz = findLoadedClass(name);

            if (clazz == null) {
                long t0 = System.nanoTime();
                // 如果是需要父加载器加载则直接调用父类加载器加载
                if (loadByParent) {
                    // 这里不应该抛出异常，找不到了还可以使用子加载器加载
                    clazz = loadByParent(name, false);
                }

                if (clazz == null) {
                    try {
                        // 构造的时候传入的URL，从那个里边搜索类定义并加载
                        clazz = findClass(name);

                    } catch (ClassNotFoundException e) {
                        // 如果我们没有优先使用父加载器加载，并且允许对加载失败的类使用父加载器加载，则尝试使用父加载器加载，加载不到就抛出异常，否则直接抛出异常
                        if (!loadByParent && loadByParentAfterFail) {
                            clazz = loadByParent(name, true);
                        } else {
                            throw e;
                        }
                    }
                }

                long t1 = System.nanoTime();
                // 统计信息，因为我们没有调用父类的loadClass（没有走到这段逻辑），所以需要自己统计
                COUNTER.addTime(t1 - t0);
                COUNTER.addElapsedTimeFrom(t1);
                COUNTER.increment();
            }

            // 链接
            if (resolve) {
                resolveClass(clazz);
            }

            return clazz;
        }
    }

    /**
     * 定义Class
     *
     * @param name
     *            class名
     * @param data
     *            class数据
     * @param offset
     *            class数据在data数组中的起始位置
     * @param len
     *            class数据的实际长度
     * @return class
     */
    public Class<?> defClass(String name, byte[] data, int offset, int len) {
        return super.defineClass(name, data, offset, len);
    }

    /**
     * 使用父加载器加载class
     *
     * @param name
     *            class name
     * @param throwIfClassNotFound
     *            如果父加载器加载不到指定类是抛出异常还是返回null，true表示抛出异常，false表示返回null
     * @return 加载到的class，throwIfClassNotFound为false的情况下可能返回null
     * @throws ClassNotFoundException
     *             如果父加载器不能找到指定class并且throwIfClassNotFound是true时抛出该异常
     */
    private Class<?> loadByParent(String name, boolean throwIfClassNotFound) throws ClassNotFoundException {
        try {
            // 注意，这里要传入parent，使用父加载器去加载，不应该传入this，会死循环，而且达不到我们只从父加载器加载类的目的；
            return parent.loadClass(name);
        } catch (ClassNotFoundException e) {
            // 如果父加载器加载不到，这里应该根据传入标识判断是否抛出异常
            if (throwIfClassNotFound) {
                throw e;
            }
            return null;
        }
    }

}
