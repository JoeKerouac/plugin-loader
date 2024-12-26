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
package com.github.joekerouac.plugin.loader.archive;

import com.github.joekerouac.plugin.loader.jar.Handler;
import com.github.joekerouac.plugin.loader.jar.JarFile;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.jar.JarEntry;
import java.util.jar.Manifest;

/**
 * {@link Archive} implementation backed by a {@link com.github.joekerouac.plugin.loader.jar.JarFile}.
 *
 * @author JoeKerouac
 * @date 2023-01-04 13:30
 * @since 3.0.0
 */
public class JarFileArchive implements Archive {

    private final JarFile jarFile;

    private URL url;

    public JarFileArchive(URL url) throws IOException {
        String protocol = url.getProtocol();
        JarFile jarFile;
        if ("file".equals(protocol)) {
            jarFile = new JarFile(new File(url.getFile()));
        } else if ("jar".equals(protocol)) {
            String[] split = url.getFile().split(Handler.SEPARATOR);
            String file = split[0];
            if (!file.startsWith("file:/")) {
                throw new IllegalArgumentException(String.format("不支持的协议: %s", url));
            }
            jarFile = new JarFile(new File(file.substring(6)));
            if (split.length > 1) {
                for (int i = 1; i < split.length; i++) {
                    com.github.joekerouac.plugin.loader.jar.JarEntry jarEntry = jarFile.getJarEntry(split[i]);
                    if (jarEntry.isDirectory()) {
                        throw new IllegalArgumentException(String.format("当前路径是目录不是嵌套jar，不支持, %s", url));
                    }
                    jarFile = jarFile.getNestedJarFile(jarEntry);
                }
            }
        } else {
            throw new IllegalArgumentException(String.format("不支持的协议: %s", url));
        }

        this.jarFile = jarFile;
        this.url = url;
    }

    public JarFileArchive(File file) throws IOException {
        this(file, file.toURI().toURL());
    }

    public JarFileArchive(File file, URL url) throws IOException {
        this(new JarFile(file));
        this.url = url;
    }

    public JarFileArchive(JarFile jarFile) {
        this.jarFile = jarFile;
    }

    @Override
    public URL getUrl() throws MalformedURLException {
        if (this.url != null) {
            return this.url;
        }
        return this.jarFile.getUrl();
    }

    @Override
    public Manifest getManifest() throws IOException {
        return this.jarFile.getManifest();
    }

    @Override
    public Iterator<Archive> getNestedArchives(EntryFilter searchFilter, EntryFilter includeFilter) throws IOException {
        return new NestedArchiveIterator(this.jarFile.iterator(), searchFilter, includeFilter);
    }

    @Override
    public void close() throws IOException {
        this.jarFile.close();
    }

    protected Archive getNestedArchive(Entry entry) throws IOException {
        JarEntry jarEntry = ((JarFileEntry)entry).getJarEntry();
        try {
            JarFile jarFile = this.jarFile.getNestedJarFile(jarEntry);
            return new JarFileArchive(jarFile);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to get nested archive for entry " + entry.getName(), ex);
        }
    }

    @Override
    public String toString() {
        try {
            return getUrl().toString();
        } catch (Exception ex) {
            return "jar archive";
        }
    }

    /**
     * Abstract base class for iterator implementations.
     */
    private abstract static class AbstractIterator<T> implements Iterator<T> {

        private final Iterator<JarEntry> iterator;

        private final EntryFilter searchFilter;

        private final EntryFilter includeFilter;

        private Entry current;

        AbstractIterator(Iterator<JarEntry> iterator, EntryFilter searchFilter, EntryFilter includeFilter) {
            this.iterator = iterator;
            this.searchFilter = searchFilter;
            this.includeFilter = includeFilter;
            this.current = poll();
        }

        @Override
        public boolean hasNext() {
            return this.current != null;
        }

        @Override
        public T next() {
            T result = adapt(this.current);
            this.current = poll();
            return result;
        }

        private Entry poll() {
            while (this.iterator.hasNext()) {
                JarFileEntry candidate = new JarFileEntry(this.iterator.next());
                if ((this.searchFilter == null || this.searchFilter.matches(candidate))
                    && (this.includeFilter == null || this.includeFilter.matches(candidate))) {
                    return candidate;
                }
            }
            return null;
        }

        protected abstract T adapt(Entry entry);

    }

    /**
     * {@link Archive.Entry} iterator implementation backed by {@link JarEntry}.
     */
    private static class EntryIterator extends AbstractIterator<Entry> {

        EntryIterator(Iterator<JarEntry> iterator, EntryFilter searchFilter, EntryFilter includeFilter) {
            super(iterator, searchFilter, includeFilter);
        }

        @Override
        protected Entry adapt(Entry entry) {
            return entry;
        }

    }

    /**
     * Nested {@link Archive} iterator implementation backed by {@link JarEntry}.
     */
    private class NestedArchiveIterator extends AbstractIterator<Archive> {

        NestedArchiveIterator(Iterator<JarEntry> iterator, EntryFilter searchFilter, EntryFilter includeFilter) {
            super(iterator, searchFilter, includeFilter);
        }

        @Override
        protected Archive adapt(Entry entry) {
            try {
                return getNestedArchive(entry);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

    }

    /**
     * {@link Archive.Entry} implementation backed by a {@link JarEntry}.
     */
    private static class JarFileEntry implements Entry {

        private final JarEntry jarEntry;

        JarFileEntry(JarEntry jarEntry) {
            this.jarEntry = jarEntry;
        }

        JarEntry getJarEntry() {
            return this.jarEntry;
        }

        @Override
        public boolean isDirectory() {
            return this.jarEntry.isDirectory();
        }

        @Override
        public String getName() {
            return this.jarEntry.getName();
        }

    }

}
