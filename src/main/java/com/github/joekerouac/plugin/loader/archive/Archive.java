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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.jar.Manifest;

/**
 *
 * @author JoeKerouac
 * @date 2023-01-04 13:30
 * @since 3.0.0
 * @see JarFileArchive
 */
public interface Archive extends AutoCloseable {

    EntryFilter FILTER_ALL = entry -> true;

    /**
     * Returns a URL that can be used to load the archive.
     * 
     * @return the archive URL
     * @throws MalformedURLException
     *             if the URL is malformed
     */
    URL getUrl() throws MalformedURLException;

    /**
     * Returns the manifest of the archive.
     * 
     * @return the manifest
     * @throws IOException
     *             if the manifest cannot be read
     */
    Manifest getManifest() throws IOException;

    /**
     * Returns nested {@link Archive}s for entries that match the specified filters.
     * 
     * @param searchFilter
     *            filter used to limit when additional sub-entry searching is required or {@code null} if all entries
     *            should be considered.
     * @param includeFilter
     *            filter used to determine which entries should be included in the result or {@code null} if all entries
     *            should be included
     * @return the nested archives
     * @throws IOException
     *             on IO error
     */
    Iterator<Archive> getNestedArchives(EntryFilter searchFilter, EntryFilter includeFilter) throws IOException;

    /**
     * Return if the archive is exploded (already unpacked).
     * 
     * @return if the archive is exploded
     */
    default boolean isExploded() {
        return false;
    }

    /**
     * Closes the {@code Archive}, releasing any open resources.
     * 
     * @throws Exception
     *             if an error occurs during close processing
     */
    @Override
    default void close() throws Exception {

    }

    /**
     * Represents a single entry in the archive.
     */
    interface Entry {

        /**
         * Returns {@code true} if the entry represents a directory.
         * 
         * @return if the entry is a directory
         */
        boolean isDirectory();

        /**
         * Returns the name of the entry.
         * 
         * @return the name of the entry
         */
        String getName();

    }

    /**
     * Strategy interface to filter {@link Entry Entries}.
     */
    @FunctionalInterface
    interface EntryFilter {

        /**
         * Apply the jar entry filter.
         * 
         * @param entry
         *            the entry to filter
         * @return {@code true} if the filter matches
         */
        boolean matches(Entry entry);

    }

}
