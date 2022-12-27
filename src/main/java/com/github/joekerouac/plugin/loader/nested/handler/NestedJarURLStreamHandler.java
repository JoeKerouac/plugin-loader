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
package com.github.joekerouac.plugin.loader.nested.handler;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * 嵌套jar url处理器
 *
 * @author JoeKerouac
 * @date 2022-12-24 19:24
 * @since 2.0.0
 */
public class NestedJarURLStreamHandler extends URLStreamHandler {

    public static final String PROTOCOL = "nested";

    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        return new NestedJarURLConnection(u);
    }

    @Override
    protected void parseURL(URL u, String spec, int start, int limit) {
        if (!spec.startsWith(PROTOCOL)) {
            throw new UnsupportedOperationException("不支持的协议：" + spec);
        }

        setURL(u, PROTOCOL, null, -1, null, null, spec.substring(PROTOCOL.length() + 1), null, null);
    }
}
