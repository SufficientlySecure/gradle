/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.filewatch;

import org.gradle.internal.concurrent.Stoppable;

import java.io.IOException;

/**
 * Stateful service for creating for multiple watches on different sets of inputs of {@link org.gradle.api.file.DirectoryTree} or individual {@link java.io.File}s
 *
 * All watching can be stopped by calling the stop method. This should be done before disposing the FileWatcher instance.
 *
 */
public interface FileWatcher extends Stoppable {
    /**
     * Starts watching for file changes to given inputs.
     * It is guaranteed that file watching gets activated before this method returns.
     *
     * @param inputs the directories and files to watch for changes
     */
    void watch(FileWatchInputs inputs) throws IOException;
}
