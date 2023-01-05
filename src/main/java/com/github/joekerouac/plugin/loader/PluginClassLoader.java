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
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import com.github.joekerouac.plugin.loader.counter.NopClassLoadCounter;
import com.github.joekerouac.plugin.loader.counter.SunClassLoadCounter;
import com.github.joekerouac.plugin.loader.jar.Handler;

/**
 *
 * @author JoeKerouac
 * @date 2023-01-04 13:30
 * @since 2.0.0
 */
public class PluginClassLoader extends URLClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
        com.github.joekerouac.plugin.loader.jar.JarFile.registerUrlProtocolHandler();
    }

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
     * sun.misc.Launcher$ExtClassLoader，父级是BootstrapClassLoader，BootstrapClassLoader用于加载系统核心class，而ExtClassLoader
     * 加载系统扩展包，最后我们应用的class以及命令行指定的class path上的class是由sun.misc.Launcher$AppClassLoader（继承ExtClassLoader）加载的
     */
    private final ClassLoader extClassLoader;

    /**
     * 需要父加载器加载的类
     */
    private final String[] needLoadByParent;

    /**
     * 父级加载器
     */
    private final ClassLoader parent;

    /**
     * 当本加载器加载类失败时是否允许父加载器加载
     */
    private final boolean loadByParentAfterFail;

    public PluginClassLoader(URL[] urls, ClassLoader parent, String[] needLoadByParent, boolean loadByParentAfterFail) {
        super(urls, null);
        this.parent = parent;
        this.loadByParentAfterFail = loadByParentAfterFail;
        this.needLoadByParent =
            needLoadByParent == null ? new String[0] : Arrays.copyOfRange(needLoadByParent, 0, needLoadByParent.length);

        // 先尝试从本类的类加载器上查找
        ClassLoader usedExtClassLoader = searchExtClassLoader(PluginClassLoader.class.getClassLoader());

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
    public URL findResource(String name) {
        Handler.setUseFastConnectionExceptions(true);
        try {
            return super.findResource(name);
        } finally {
            Handler.setUseFastConnectionExceptions(false);
        }
    }

    @Override
    public Enumeration<URL> findResources(String name) throws IOException {
        Handler.setUseFastConnectionExceptions(true);
        try {
            return new UseFastConnectionExceptionsEnumeration(super.findResources(name));
        } finally {
            Handler.setUseFastConnectionExceptions(false);
        }
    }

    /**
     * 使用父加载器加载class
     *
     * @param loader
     *            用于加载class的ClassLoader
     * @param name
     *            class name
     * @param throwIfClassNotFound
     *            如果父加载器加载不到指定类是抛出异常还是返回null，true表示抛出异常，false表示返回null
     * @return 加载到的class，throwIfClassNotFound为false的情况下可能返回null
     * @throws ClassNotFoundException
     *             如果父加载器不能找到指定class并且throwIfClassNotFound是true时抛出该异常
     */
    private Class<?> loadClass(ClassLoader loader, String name, boolean throwIfClassNotFound)
        throws ClassNotFoundException {
        try {
            return loader.loadClass(name);
        } catch (ClassNotFoundException e) {
            // 如果父加载器加载不到，这里应该根据传入标识判断是否抛出异常
            if (throwIfClassNotFound) {
                throw e;
            }
            return null;
        }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // 先判断是不是必须父类加载的，如果类是系统类或者是用户指定了需要父加载器加载的，使用父加载器加载
        boolean loadByExt = extClassLoader.getResource(name.replaceAll("\\.", "/").concat(".class")) != null;
        boolean loadByParent = Arrays.stream(needLoadByParent).anyMatch(name::startsWith);

        // 加锁，准备加载
        synchronized (getClassLoadingLock(name)) {
            // 先查找已经加载过的类
            Class<?> clazz = findLoadedClass(name);

            if (clazz == null) {
                // 如果是需要父加载器加载则直接调用父类加载器加载
                if (loadByExt) {
                    clazz = loadClass(extClassLoader, name, false);
                } else if (loadByParent) {
                    // 这里不应该抛出异常，找不到了还可以使用子加载器加载
                    clazz = loadClass(parent, name, false);
                }

                if (clazz == null) {
                    try {
                        long t0 = System.nanoTime();
                        // 调用本类加载器查找类
                        clazz = findClass(name);
                        long t1 = System.nanoTime();
                        // 统计信息，因为我们没有调用父类的loadClass（没有走到这段逻辑），所以需要自己统计
                        COUNTER.addTime(t1 - t0);
                        COUNTER.addElapsedTimeFrom(t1);
                        COUNTER.increment();
                    } catch (ClassNotFoundException e) {
                        // 如果我们没有优先使用父加载器加载，并且允许对加载失败的类使用父加载器加载，则尝试使用父加载器加载，加载不到就抛出异常，否则直接抛出异常
                        if (!loadByExt && !loadByParent && loadByParentAfterFail) {
                            clazz = loadClass(parent, name, true);
                        } else {
                            throw e;
                        }
                    }
                }

            }

            // 链接
            if (resolve) {
                resolveClass(clazz);
            }

            return clazz;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Handler.setUseFastConnectionExceptions(true);
        try {
            definePackageIfNecessary(name);
            return super.findClass(name);
        } finally {
            Handler.setUseFastConnectionExceptions(false);
        }
    }

    /**
     * Define a package before a {@code findClass} call is made. This is necessary to ensure that the appropriate
     * manifest for nested JARs is associated with the package.
     * 
     * @param className
     *            the class name being found
     */
    private void definePackageIfNecessary(String className) {
        int lastDot = className.lastIndexOf('.');
        if (lastDot >= 0) {
            String packageName = className.substring(0, lastDot);
            if (getPackage(packageName) == null) {
                try {
                    definePackage(className, packageName);
                } catch (IllegalArgumentException ex) {
                    // 因为我们允许并行，所以可能会出现并行创建package的场景，其中会有一个成功，其他失败，并且抛出IllegalArgumentException异常
                    if (getPackage(packageName) == null) {
                        // 理论上不可能走到这里
                        throw new AssertionError(
                            "Package " + packageName + " has already been defined but it could not be found");
                    }
                }
            }
        }
    }

    private void definePackage(String className, String packageName) {
        String packageEntryName = packageName.replace('.', '/') + "/";
        String classEntryName = className.replace('.', '/') + ".class";
        for (URL url : getURLs()) {
            try {
                URLConnection connection = url.openConnection();
                if (connection instanceof JarURLConnection) {
                    JarURLConnection jarURLConnection = (JarURLConnection)connection;
                    JarFile jarFile = jarURLConnection.getJarFile();
                    if (jarFile.getEntry(classEntryName) != null && jarFile.getEntry(packageEntryName) != null
                        && jarFile.getManifest() != null) {
                        definePackage(packageName, jarFile.getManifest(), url);
                        return;
                    }
                }
            } catch (IOException ex) {
                // Ignore
            }
        }
    }

    @Override
    protected Package definePackage(String name, Manifest man, URL url) throws IllegalArgumentException {
        return super.definePackage(name, man, url);
    }

    @Override
    protected Package definePackage(String name, String specTitle, String specVersion, String specVendor,
        String implTitle, String implVersion, String implVendor, URL sealBase) throws IllegalArgumentException {
        return super.definePackage(name, specTitle, specVersion, specVendor, implTitle, implVersion, implVendor,
            sealBase);
    }

    /**
     * Clear URL caches.
     */
    public void clearCache() {
        for (URL url : getURLs()) {
            try {
                URLConnection connection = url.openConnection();
                if (connection instanceof JarURLConnection) {
                    clearCache(connection);
                }
            } catch (IOException ex) {
                // Ignore
            }
        }

    }

    private void clearCache(URLConnection connection) throws IOException {
        Object jarFile = ((JarURLConnection)connection).getJarFile();
        if (jarFile instanceof com.github.joekerouac.plugin.loader.jar.JarFile) {
            ((com.github.joekerouac.plugin.loader.jar.JarFile)jarFile).clearCache();
        }
    }

    private static class UseFastConnectionExceptionsEnumeration implements Enumeration<URL> {

        private final Enumeration<URL> delegate;

        UseFastConnectionExceptionsEnumeration(Enumeration<URL> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasMoreElements() {
            Handler.setUseFastConnectionExceptions(true);
            try {
                return this.delegate.hasMoreElements();
            } finally {
                Handler.setUseFastConnectionExceptions(false);
            }

        }

        @Override
        public URL nextElement() {
            Handler.setUseFastConnectionExceptions(true);
            try {
                return this.delegate.nextElement();
            } finally {
                Handler.setUseFastConnectionExceptions(false);
            }
        }

    }

}
