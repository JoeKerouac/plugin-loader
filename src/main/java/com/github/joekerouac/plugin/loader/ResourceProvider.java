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
import java.util.List;
import java.util.function.Function;

/**
 * resource提供者，根据资源名提供URL
 * 
 * @since 1.0.0
 * @author JoeKerouac
 * @version 2.0.0
 * @date 2020-09-20 16:11
 */
public interface ResourceProvider extends Function<String, List<URL>> {

    /**
     * 根据提供的资源名获取资源
     * 
     * @param s
     *            资源名
     * @return 资源
     */
    List<URL> apply(String s);

}
