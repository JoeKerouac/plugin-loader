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
import java.io.IOException;
import java.net.URI;
import java.net.URL;

import com.github.joekerouac.plugin.loader.jar.Handler;
import com.github.joekerouac.plugin.loader.jar.JarEntry;
import com.github.joekerouac.plugin.loader.jar.JarFile;

/**
 * @author JoeKerouac
 * @date 2023-06-28 17:45
 * @since 4.0.0
 */
public class JarFileUtil {

    private static final String FILE_PROTOCOL = "file:";

    /**
     * Spring Boot 3.2+ fat jar 使用的嵌套 jar 协议前缀。
     *
     * @see <a href=
     *      "https://docs.spring.io/spring-boot/specification/executable-jar/jarfile-class.html">NestedJarFile</a>
     */
    private static final String SPRING_BOOT_NESTED_PREFIX = "nested:";

    /**
     * Spring Boot nested jar 中，外层 jar 与内层 entry 的分隔符。
     */
    private static final String SPRING_BOOT_NESTED_ENTRY_SEPARATOR = "/!";

    /**
     * Spring Boot nested jar 位置信息。
     */
    public static final class SpringBootNestedLocation {

        private final File outerJarFile;

        private final String nestedEntryPath;

        public SpringBootNestedLocation(File outerJarFile, String nestedEntryPath) {
            this.outerJarFile = outerJarFile;
            this.nestedEntryPath = nestedEntryPath;
        }

        public File getOuterJarFile() {
            return outerJarFile;
        }

        public String getNestedEntryPath() {
            return nestedEntryPath;
        }

    }

    /**
     * 从url中获取jar file，支持:
     * <ul>
     * <li>file协议，例如: jar:file:/path/app.jar!/BOOT-INF/lib/inner.jar!/</li>
     * <li>Spring Boot 3.2+ nested协议，例如: jar:nested:/path/app.jar/!BOOT-INF/lib/inner.jar!/</li>
     * </ul>
     *
     * @param url
     *            url
     * @return JarFile
     * @throws IOException
     *             IO异常
     */
    public static JarFile fromUrl(URL url) throws IOException {
        SpringBootNestedLocation nestedLocation = parseSpringBootNestedUrl(url);
        if (nestedLocation != null) {
            return openFromSpringBootNestedLocation(nestedLocation, url.getFile());
        }

        String spec = url.getFile();
        String[] split = spec.split(Handler.SEPARATOR);

        String rootFile = split[0];

        if (!rootFile.startsWith(FILE_PROTOCOL)) {
            throw new IllegalStateException("Not a file URL: " + url);
        }

        File file = new File(URI.create(rootFile));

        JarFile jarFile = new JarFile(file);

        int i = 1;
        while (i < split.length) {
            String entryName = split[i++];
            if (entryName.isEmpty() || entryName.endsWith(".class")) {
                break;
            }
            JarEntry jarEntry = jarFile.getJarEntry(entryName);
            jarFile = jarFile.getNestedJarFile(jarEntry);
        }

        return jarFile;
    }

    /**
     * 解析 Spring Boot nested jar URL。
     *
     * @param url
     *            url
     * @return 解析结果；不是 nested URL 时返回 null
     */
    public static SpringBootNestedLocation parseSpringBootNestedUrl(URL url) {
        if (url == null) {
            return null;
        }

        String spec;
        if ("nested".equals(url.getProtocol())) {
            spec = SPRING_BOOT_NESTED_PREFIX + url.getFile();
        } else if ("jar".equals(url.getProtocol()) && url.getFile().startsWith(SPRING_BOOT_NESTED_PREFIX)) {
            spec = url.getFile();
        } else {
            return null;
        }

        return parseSpringBootNestedSpec(spec);
    }

    private static SpringBootNestedLocation parseSpringBootNestedSpec(String spec) {
        if (!spec.startsWith(SPRING_BOOT_NESTED_PREFIX)) {
            return null;
        }

        String remainder = spec.substring(SPRING_BOOT_NESTED_PREFIX.length());
        int entrySeparatorIndex = remainder.indexOf(SPRING_BOOT_NESTED_ENTRY_SEPARATOR);
        if (entrySeparatorIndex < 0) {
            throw new IllegalArgumentException(String.format("无效的 Spring Boot nested URL: %s", spec));
        }

        String outerJarPath = remainder.substring(0, entrySeparatorIndex);
        String entryAndMore = remainder.substring(entrySeparatorIndex + SPRING_BOOT_NESTED_ENTRY_SEPARATOR.length());
        int nestedJarEnd = entryAndMore.indexOf(Handler.SEPARATOR);
        String nestedEntryPath = nestedJarEnd >= 0 ? entryAndMore.substring(0, nestedJarEnd) : entryAndMore;
        if (nestedEntryPath.endsWith("/")) {
            nestedEntryPath = nestedEntryPath.substring(0, nestedEntryPath.length() - 1);
        }

        return new SpringBootNestedLocation(new File(outerJarPath), nestedEntryPath);
    }

    private static JarFile openFromSpringBootNestedLocation(SpringBootNestedLocation location, String spec)
        throws IOException {
        JarFile jarFile = new JarFile(location.getOuterJarFile());
        JarEntry jarEntry = jarFile.getJarEntry(location.getNestedEntryPath());
        if (jarEntry == null) {
            throw new IOException(
                String.format("在 [%s] 中未找到嵌套 jar 条目 [%s]", location.getOuterJarFile(), location.getNestedEntryPath()));
        }
        jarFile = jarFile.getNestedJarFile(jarEntry);

        int entrySeparatorIndex = spec.indexOf(SPRING_BOOT_NESTED_ENTRY_SEPARATOR);
        String entryAndMore = spec.substring(entrySeparatorIndex + SPRING_BOOT_NESTED_ENTRY_SEPARATOR.length());
        int nestedJarEnd = entryAndMore.indexOf(Handler.SEPARATOR);
        if (nestedJarEnd < 0) {
            return jarFile;
        }

        String[] moreParts = entryAndMore.substring(nestedJarEnd + Handler.SEPARATOR.length()).split(Handler.SEPARATOR);
        for (String part : moreParts) {
            if (part.isEmpty() || part.endsWith(".class")) {
                break;
            }
            if (!part.endsWith(".jar")) {
                break;
            }
            JarEntry nestedEntry = jarFile.getJarEntry(part);
            if (nestedEntry == null) {
                throw new IOException(String.format("在嵌套 jar 中未找到条目 [%s]", part));
            }
            jarFile = jarFile.getNestedJarFile(nestedEntry);
        }

        return jarFile;
    }

}
