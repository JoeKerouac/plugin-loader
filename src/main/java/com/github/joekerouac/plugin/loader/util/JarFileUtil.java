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
     * 从url中获取jar file，仅支持file协议，例如:
     * jar:file:/D:/dev/idea/gitlab/pay/target/pay-server-1.0.0.jar!/BOOT-INF/lib/test-tools-4.1.0-SNAPSHOT.jar!/
     * 
     * @param url
     *            url
     * @return JarFile
     * @throws IOException
     *             IO异常
     */
    public static JarFile fromUrl(URL url) throws IOException {
        String spec = url.getFile();
        String[] split = spec.split(Handler.SEPARATOR);

        String rootFile = split[0];

        if (!rootFile.startsWith(FILE_PROTOCOL)) {
            throw new IllegalStateException("Not a file URL");
        }

        File file = new File(URI.create(rootFile));

        JarFile jarFile = new JarFile(file);

        int i = 1;
        while (i++ < split.length) {
            JarEntry jarEntry = jarFile.getJarEntry(split[i - 1]);
            jarFile = jarFile.getNestedJarFile(jarEntry);
        }

        return jarFile;
    }

}
