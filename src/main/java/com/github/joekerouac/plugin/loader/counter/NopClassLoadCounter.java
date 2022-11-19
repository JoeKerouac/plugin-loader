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
package com.github.joekerouac.plugin.loader.counter;

import com.github.joekerouac.plugin.loader.ClassLoadCounter;

/**
 * 类计数器在JDK17的实现，因为到了JDK17中sun.misc.PerfCounter变成了jdk.internal.perf.PerfCounter，并且该包不可访问了，所以我们这里搞一个空实现
 *
 * @author JoeKerouac
 * @date 2022-01-13 11:58
 * @since 1.0.0
 */
public class NopClassLoadCounter implements ClassLoadCounter {

    @Override
    public void addTime(final long usedTime) {

    }

    @Override
    public void addElapsedTimeFrom(final long endTime) {

    }

    @Override
    public void increment() {

    }
}
