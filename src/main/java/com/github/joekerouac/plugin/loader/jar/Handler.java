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
package com.github.joekerouac.plugin.loader.jar;

import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.github.joekerouac.plugin.loader.annotation.SuppressFBWarnings;

/**
 * jar协议处理器，用于处理嵌套jar url
 *
 * @author JoeKerouac
 * @date 2023-01-04 13:30
 * @since 3.0.0
 */
public class Handler extends URLStreamHandler {

    private static final String JAR_PROTOCOL = "jar:";

    private static final String FILE_PROTOCOL = "file:";

    public static final String SEPARATOR = "!/";

    private static final Pattern SEPARATOR_PATTERN = Pattern.compile(SEPARATOR, Pattern.LITERAL);

    private static final String CURRENT_DIR = "/./";

    private static final Pattern CURRENT_DIR_PATTERN = Pattern.compile(CURRENT_DIR, Pattern.LITERAL);

    private static final String PARENT_DIR = "/../";

    private static final String PROTOCOL_HANDLER = "java.protocol.handler.pkgs";

    private static final String[] FALLBACK_HANDLERS = {"sun.net.www.protocol.jar.Handler"};

    private static URL jarContextUrl;

    private static SoftReference<Map<File, JarFile>> rootFileCache;

    static {
        rootFileCache = new SoftReference<>(null);
    }

    private final JarFile jarFile;

    private URLStreamHandler fallbackHandler;

    public Handler() {
        this(null);
    }

    public Handler(JarFile jarFile) {
        this.jarFile = jarFile;
    }

    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        if (this.jarFile != null && isUrlInJarFile(url, this.jarFile)) {
            return JarURLConnection.get(url, this.jarFile);
        }
        try {
            return JarURLConnection.get(url, getRootJarFileFromUrl(url));
        } catch (Exception ex) {
            return openFallbackConnection(url, ex);
        }
    }

    /**
     * 判断url指向是否是指定jar文件
     * 
     * @param url
     *            url
     * @param jarFile
     *            jar文件
     * @return true表示url指向jar文件
     * @throws MalformedURLException
     *             MalformedURLException
     */
    private boolean isUrlInJarFile(URL url, JarFile jarFile) throws MalformedURLException {
        return url.getPath().startsWith(jarFile.getUrl().getPath())
            && url.toString().startsWith(jarFile.getUrlString());
    }

    private URLConnection openFallbackConnection(URL url, Exception reason) throws IOException {
        try {
            URLConnection connection = openFallbackContextConnection(url);
            return (connection != null) ? connection : openFallbackHandlerConnection(url);
        } catch (Exception ex) {
            if (reason instanceof IOException) {
                log(false, "Unable to open fallback handler", ex);
                throw (IOException)ex;
            }
            log(true, "Unable to open fallback handler", ex);
            if (reason instanceof RuntimeException) {
                throw (RuntimeException)reason;
            }
            throw new IllegalStateException(reason);
        }
    }

    private URLConnection openFallbackContextConnection(URL url) {
        try {
            if (jarContextUrl != null) {
                return new URL(jarContextUrl, url.toExternalForm()).openConnection();
            }
        } catch (Exception ex) {
            // 忽略异常
        }
        return null;
    }

    private URLConnection openFallbackHandlerConnection(URL url) throws Exception {
        URLStreamHandler fallbackHandler = getFallbackHandler();
        return new URL(null, url.toExternalForm(), fallbackHandler).openConnection();
    }

    private URLStreamHandler getFallbackHandler() {
        if (this.fallbackHandler != null) {
            return this.fallbackHandler;
        }
        for (String handlerClassName : FALLBACK_HANDLERS) {
            try {
                Class<?> handlerClass = Class.forName(handlerClassName);
                this.fallbackHandler = (URLStreamHandler)handlerClass.getDeclaredConstructor().newInstance();
                return this.fallbackHandler;
            } catch (Exception ex) {
                // Ignore
            }
        }
        throw new IllegalStateException("Unable to find fallback handler");
    }

    private void log(boolean warning, String message, Exception cause) {
        try {
            Level level = warning ? Level.WARNING : Level.FINEST;
            Logger.getLogger(getClass().getName()).log(level, message, cause);
        } catch (Exception ex) {
            if (warning) {
                System.err.println("WARNING: " + message);
            }
        }
    }

    @Override
    protected void parseURL(URL context, String spec, int start, int limit) {
        if (spec.regionMatches(true, 0, JAR_PROTOCOL, 0, JAR_PROTOCOL.length())) {
            setFile(context, getFileFromSpec(spec.substring(start, limit)));
        } else {
            setFile(context, getFileFromContext(context, spec.substring(start, limit)));
        }
    }

    @SuppressWarnings("checkstyle:multiplestringliterals")
    private String getFileFromSpec(String spec) {
        int separatorIndex = spec.lastIndexOf(SEPARATOR);
        if (separatorIndex == -1) {
            throw new IllegalArgumentException("No !/ in spec '" + spec + "'");
        }
        try {
            new URL(spec.substring(0, separatorIndex));
            return spec;
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException("Invalid spec URL '" + spec + "'", ex);
        }
    }

    private String getFileFromContext(URL context, String spec) {
        String file = context.getFile();
        if (spec.startsWith("/")) {
            return trimToJarRoot(file) + SEPARATOR + spec.substring(1);
        }
        if (file.endsWith("/")) {
            return file + spec;
        }
        int lastSlashIndex = file.lastIndexOf('/');
        if (lastSlashIndex == -1) {
            throw new IllegalArgumentException("No / found in context URL's file '" + file + "'");
        }
        return file.substring(0, lastSlashIndex + 1) + spec;
    }

    private String trimToJarRoot(String file) {
        int lastSeparatorIndex = file.lastIndexOf(SEPARATOR);
        if (lastSeparatorIndex == -1) {
            throw new IllegalArgumentException("No !/ found in context URL's file '" + file + "'");
        }
        return file.substring(0, lastSeparatorIndex);
    }

    private void setFile(URL context, String file) {
        String path = normalize(file);
        String query = null;
        int queryIndex = path.lastIndexOf('?');
        if (queryIndex != -1) {
            query = path.substring(queryIndex + 1);
            path = path.substring(0, queryIndex);
        }
        setURL(context, JAR_PROTOCOL, null, -1, null, null, path, query, context.getRef());
    }

    private String normalize(String file) {
        if (!file.contains(CURRENT_DIR) && !file.contains(PARENT_DIR)) {
            return file;
        }
        int afterLastSeparatorIndex = file.lastIndexOf(SEPARATOR) + SEPARATOR.length();
        String afterSeparator = file.substring(afterLastSeparatorIndex);
        afterSeparator = replaceParentDir(afterSeparator);
        afterSeparator = replaceCurrentDir(afterSeparator);
        return file.substring(0, afterLastSeparatorIndex) + afterSeparator;
    }

    private String replaceParentDir(String file) {
        int parentDirIndex;
        while ((parentDirIndex = file.indexOf(PARENT_DIR)) >= 0) {
            int precedingSlashIndex = file.lastIndexOf('/', parentDirIndex - 1);
            if (precedingSlashIndex >= 0) {
                file = file.substring(0, precedingSlashIndex) + file.substring(parentDirIndex + 3);
            } else {
                file = file.substring(parentDirIndex + 4);
            }
        }
        return file;
    }

    private String replaceCurrentDir(String file) {
        return CURRENT_DIR_PATTERN.matcher(file).replaceAll("/");
    }

    @Override
    protected int hashCode(URL u) {
        return hashCode(u.getProtocol(), u.getFile());
    }

    @SuppressFBWarnings("DMI_BLOCKING_METHODS_ON_URL")
    private int hashCode(String protocol, String file) {
        int result = (protocol != null) ? protocol.hashCode() : 0;
        int separatorIndex = file.indexOf(SEPARATOR);
        if (separatorIndex == -1) {
            return result + file.hashCode();
        }
        String source = file.substring(0, separatorIndex);
        String entry = canonicalize(file.substring(separatorIndex + 2));
        try {
            result += new URL(source).hashCode();
        } catch (MalformedURLException ex) {
            result += source.hashCode();
        }
        result += entry.hashCode();
        return result;
    }

    @Override
    protected boolean sameFile(URL u1, URL u2) {
        if (!u1.getProtocol().equals("jar") || !u2.getProtocol().equals("jar")) {
            return false;
        }
        int separator1 = u1.getFile().indexOf(SEPARATOR);
        int separator2 = u2.getFile().indexOf(SEPARATOR);
        if (separator1 == -1 || separator2 == -1) {
            return super.sameFile(u1, u2);
        }
        // 先比较entry，因为可能是嵌套的，父级处理不了，我们自己处理
        String nested1 = u1.getFile().substring(separator1 + SEPARATOR.length());
        String nested2 = u2.getFile().substring(separator2 + SEPARATOR.length());
        if (!nested1.equals(nested2)) {
            String canonical1 = canonicalize(nested1);
            String canonical2 = canonicalize(nested2);
            if (!canonical1.equals(canonical2)) {
                return false;
            }
        }
        // 再比较文件，这里主要就是父级处理了
        String root1 = u1.getFile().substring(0, separator1);
        String root2 = u2.getFile().substring(0, separator2);
        try {
            return super.sameFile(new URL(root1), new URL(root2));
        } catch (MalformedURLException ex) {
            // Continue
        }
        return super.sameFile(u1, u2);
    }

    private String canonicalize(String path) {
        return SEPARATOR_PATTERN.matcher(path).replaceAll("/");
    }

    public JarFile getRootJarFileFromUrl(URL url) throws IOException {
        String spec = url.getFile();
        int separatorIndex = spec.indexOf(SEPARATOR);
        if (separatorIndex == -1) {
            throw new MalformedURLException("Jar URL does not contain !/ separator");
        }
        String name = spec.substring(0, separatorIndex);
        return getRootJarFile(name);
    }

    private JarFile getRootJarFile(String name) throws IOException {
        try {
            if (!name.startsWith(FILE_PROTOCOL)) {
                throw new IllegalStateException("Not a file URL");
            }
            File file = new File(URI.create(name));
            Map<File, JarFile> cache = rootFileCache.get();
            JarFile result = (cache != null) ? cache.get(file) : null;
            if (result == null) {
                result = new JarFile(file);
                addToRootFileCache(file, result);
            }
            return result;
        } catch (Exception ex) {
            throw new IOException("Unable to open root Jar file '" + name + "'", ex);
        }
    }

    /**
     * 将给定JarFile加入缓存
     * 
     * @param sourceFile
     *            jar源文件
     * @param jarFile
     *            JarFile
     */
    static void addToRootFileCache(File sourceFile, JarFile jarFile) {
        Map<File, JarFile> cache = rootFileCache.get();
        if (cache == null) {
            cache = new ConcurrentHashMap<>();
            rootFileCache = new SoftReference<>(cache);
        }
        cache.put(sourceFile, jarFile);
    }

    /**
     * 保存系统自带的jar协议处理器（URLStreamHandler），以便于我们后续将其用作回退上下文
     */
    static void captureJarContextUrl() {
        /*
         * 只有允许重置全局URLStreamHandler缓存时才进行后续操作，因为后续操作会导致生成全局URLStreamHandler缓存，而我们需要重置全局URLStreamHandler缓
         * 存，否则会导致我们的Handler注册不上
         */
        if (canResetCachedUrlHandlers()) {
            String handlers = System.getProperty(PROTOCOL_HANDLER);
            try {
                System.clearProperty(PROTOCOL_HANDLER);
                try {
                    resetCachedUrlHandlers();
                    jarContextUrl = new URL("jar:file:context.jar!/");
                    URLConnection connection = jarContextUrl.openConnection();
                    if (connection instanceof JarURLConnection) {
                        jarContextUrl = null;
                    }
                } catch (Exception ex) {
                    // 忽略异常
                }
            } finally {
                if (handlers == null) {
                    System.clearProperty(PROTOCOL_HANDLER);
                } else {
                    System.setProperty(PROTOCOL_HANDLER, handlers);
                }
            }
            resetCachedUrlHandlers();
        }
    }

    private static boolean canResetCachedUrlHandlers() {
        try {
            resetCachedUrlHandlers();
            return true;
        } catch (Error ex) {
            return false;
        }
    }

    private static void resetCachedUrlHandlers() {
        // 如果当前URLStreamHandlerFactory为空，清空URLStreamHandler缓存，如果当前URLStreamHandlerFactory不为空，则抛出Error
        URL.setURLStreamHandlerFactory(null);
    }

    /**
     * 设置是否可以在url无法打开的时候抛出静态异常，用于类加载期间优化，抛出的异常通常会被忽略，所以也无需每次都重新构建异常
     * 
     * @param useFastConnectionExceptions
     *            true表示抛出静态异常
     */
    public static void setUseFastConnectionExceptions(boolean useFastConnectionExceptions) {
        JarURLConnection.setUseFastExceptions(useFastConnectionExceptions);
    }

}
