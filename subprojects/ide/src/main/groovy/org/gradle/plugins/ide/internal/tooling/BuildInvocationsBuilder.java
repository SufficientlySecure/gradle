/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectTaskLister;
import org.gradle.api.internal.tasks.PublicTaskSpecification;
import org.gradle.tooling.internal.consumer.converters.TaskNameComparator;
import org.gradle.tooling.internal.impl.DefaultBuildInvocations;
import org.gradle.tooling.internal.impl.LaunchableGradleTask;
import org.gradle.tooling.internal.impl.LaunchableGradleTaskSelector;
import org.gradle.tooling.model.internal.ProjectSensitiveToolingModelBuilder;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.plugins.ide.internal.tooling.ToolingModelBuilderSupport.buildFromTask;

public class BuildInvocationsBuilder extends ProjectSensitiveToolingModelBuilder {

    private final ProjectTaskLister taskLister;
    private final TaskNameComparator taskNameComparator;

    public BuildInvocationsBuilder(ProjectTaskLister taskLister) {
        this.taskLister = taskLister;
        this.taskNameComparator = new TaskNameComparator();
    }

    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.gradle.BuildInvocations");
    }

    public DefaultBuildInvocations buildAll(String modelName, Project project, boolean implicitProject) {
        return buildAll(modelName, implicitProject ? project.getRootProject() : project);
    }

    @SuppressWarnings("StringEquality")
    public DefaultBuildInvocations buildAll(String modelName, Project project) {
        if (!canBuild(modelName)) {
            throw new GradleException("Unknown model name " + modelName);
        }

        // construct task selectors
        List<LaunchableGradleTaskSelector> selectors = Lists.newArrayList();
        Map<String, LaunchableGradleTaskSelector> selectorsByName = Maps.newTreeMap(Ordering.natural());
        Set<String> visibleTasks = Sets.newLinkedHashSet();
        findTasks(project, selectorsByName, visibleTasks);
        for (String selectorName : selectorsByName.keySet()) {
            LaunchableGradleTaskSelector selector = selectorsByName.get(selectorName);
            selectors.add(selector.
                    setName(selectorName).
                    setTaskName(selectorName).
                    setProjectPath(project.getPath()).
                    setDisplayName(String.format("%s in %s and subprojects.", selectorName, project.toString())).
                    setPublic(visibleTasks.contains(selectorName)));
        }

        // construct project tasks
        List<LaunchableGradleTask> projectTasks = tasks(project);

        // construct build invocations from task selectors and project tasks
        return new DefaultBuildInvocations().setSelectors(selectors).setTasks(projectTasks);
    }

    // build tasks without project reference
    private List<LaunchableGradleTask> tasks(Project project) {
        List<LaunchableGradleTask> tasks = Lists.newArrayList();
        for (Task task : taskLister.listProjectTasks(project)) {
            tasks.add(buildFromTask(LaunchableGradleTask.class, task));
        }
        return tasks;
    }

    private void findTasks(Project project, Map<String, LaunchableGradleTaskSelector> taskSelectors, Collection<String> visibleTasks) {
        for (Project child : project.getChildProjects().values()) {
            findTasks(child, taskSelectors, visibleTasks);
        }

        for (Task task : taskLister.listProjectTasks(project)) {
            // in the map, store a minimally populated LaunchableGradleTaskSelector that contains just the description and the path
            // replace the LaunchableGradleTaskSelector stored in the map iff we come across a task with the same name whose path has a smaller ordering
            // this way, for each task selector, its description will be the one from the selected task with the 'smallest' path
            if (!taskSelectors.containsKey(task.getName())) {
                LaunchableGradleTaskSelector taskSelector = new LaunchableGradleTaskSelector().
                        setDescription(task.getDescription()).setProjectPath(task.getPath());
                taskSelectors.put(task.getName(), taskSelector);
            } else {
                LaunchableGradleTaskSelector taskSelector = taskSelectors.get(task.getName());
                if (hasPathWithLowerOrdering(task, taskSelector)) {
                    taskSelector.setDescription(task.getDescription()).setProjectPath(task.getPath());
                }
            }

            // visible tasks are specified as those that have a non-empty group
            if (PublicTaskSpecification.INSTANCE.isSatisfiedBy(task)) {
                visibleTasks.add(task.getName());
            }
        }
    }

    private boolean hasPathWithLowerOrdering(Task task, LaunchableGradleTaskSelector referenceTaskSelector) {
        return taskNameComparator.compare(task.getPath(), referenceTaskSelector.getProjectPath()) < 0;
    }

}
