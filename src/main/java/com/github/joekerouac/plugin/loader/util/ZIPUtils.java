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

import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import com.github.joekerouac.plugin.loader.ByteArrayData;

/**
 * @author JoeKerouac
 * @date 2022-01-09 09:52
 * @since 1.0.0
 */
public class ZIPUtils {

    /**
     * 解压缩，注意，这个是解压缩不包含zip header的数据，直接把压缩后的数据解压缩，如果需要解压缩完整zip文件请勿调用本方法，如果不明白也请勿调用；
     *
     * @param data
     *            压缩数据
     * @param bufferLen
     *            buffer长度，最好是等于压缩数据实际解压后的大小，可以获得最佳性能；如果不能判断解压后的数据大小，如果不能确定解压后数据的实际大小，那么最好设置一个稍大一点儿的值；
     * @return 解压缩后的数据
     * @throws DataFormatException
     *             异常
     */
    public static ByteArrayData decompressNoWrap(byte[] data, int bufferLen) throws DataFormatException {
        Inflater inflater = new Inflater(true);
        inflater.setInput(data);
        byte[] buffer = new byte[bufferLen];
        int writePoint = 0;
        while (!inflater.finished()) {
            // 扩大buffer，每次扩容1.5倍
            if (writePoint == buffer.length) {
                buffer = new byte[buffer.length * 3 / 2];
            }

            int count = inflater.inflate(buffer, writePoint, buffer.length - writePoint);
            writePoint += count;
        }

        inflater.end();
        return new ByteArrayData(buffer, 0, writePoint);
    }

}
