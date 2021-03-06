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

package org.gradle.tooling.internal.provider.events;

import org.gradle.tooling.internal.protocol.events.InternalBuildResult;

import java.io.Serializable;

public abstract class AbstractBuildResult implements Serializable, InternalBuildResult {
    private final String description;
    private final long startTime;
    private final long endTime;

    public AbstractBuildResult(long startTime, long endTime, String description) {
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getOutcomeDescription() {
        return description;
    }

    @Override
    public long getEndTime() {
        return endTime;
    }

    @Override
    public long getStartTime() {
        return startTime;
    }
}
