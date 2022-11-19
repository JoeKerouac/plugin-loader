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

import java.util.function.Function;

/**
 * class数据提供者，提供class数据，根据类名返回类数据，允许返回空
 * 
 * @since 1.0.0
 * @author JoeKerouac
 * @version 1.0
 * @date 2020-09-20 16:11
 */
public interface ClassProvider extends Function<String, ByteArrayData> {

    /**
     * 根据提供的类名获取类数据
     * 
     * @param s
     *            类名
     * @return 类数据
     */
    ByteArrayData apply(String s);

}
