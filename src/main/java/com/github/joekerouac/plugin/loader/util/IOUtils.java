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

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * IO工具
 *
 * @author JoeKerouac
 * @date 2022-12-26 17:11
 * @since 2.0.0
 */
public class IOUtils {

    /**
     * 将输入流剩余内容全部读取到byte数组，IO异常会被捕获，抛出一个PluginException，cause by是对应的IO异常
     *
     * @param inputStream
     *            输入流，读取完毕会将流关闭
     * @return 输入流剩余的内容
     */
    public static byte[] read(InputStream inputStream) {
        return read(inputStream, true);
    }

    /**
     * 将输入流剩余内容全部读取到byte数组，IO异常会被捕获，抛出一个PluginException，cause by是对应的IO异常
     *
     * @param inputStream
     *            输入流
     * @param close
     *            是否关闭，true表示读取完毕关闭流
     * @return 输入流剩余的内容
     */
    public static byte[] read(InputStream inputStream, boolean close) {
        byte[] buffer = new byte[2048];
        int len;

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            while ((len = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, len);
            }

            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (close) {
                close(inputStream);
            }
        }
    }

    /**
     * 关闭指定资源
     *
     * @param closeable
     *            要关闭的资源
     */
    public static void close(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
