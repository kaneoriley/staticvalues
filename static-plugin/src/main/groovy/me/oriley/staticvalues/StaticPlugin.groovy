/*
 * Copyright (C) 2016 Kane O'Riley
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package me.oriley.staticvalues

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.Task

class StaticPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        def variants = null
        def plugin = project.plugins.findPlugin("android")
        if (plugin != null) {
            variants = "applicationVariants"
        } else {
            plugin = project.plugins.findPlugin("android-library")
            if (plugin != null) {
                variants = "libraryVariants"
            }
        }

        if (variants == null) {
            throw new ProjectConfigurationException("android or android-library plugin must be applied", null)
        }

        project.extensions.create('staticValues', StaticExtension)

        project.afterEvaluate {
            project.android[variants].all { variant ->
                //noinspection GroovyAssignabilityCheck
                String flavorString = capitalise(variant.flavorName) + capitalise(variant.buildType.name)

                boolean debugLogging = project.staticValues.debugLogging

                String variantBuildDir = "${project.buildDir}/generated/source/static/${variant.dirName}"
                String variantResourceFile = "${project.buildDir}/intermediates/res/merged/${variant.dirName}/values/values.xml"

                // Add source to variant source set
                variant.sourceSets.each { sourceSet ->
                    if (sourceSet.name == variant.dirName) {
                        sourceSet.java.srcDir "${variantBuildDir}"
                    }
                }

                String taskName = ":" + project.name + ":staticValues" + flavorString
                //noinspection GrUnresolvedAccess,GroovyAssignabilityCheck
                StaticGenerator generator = new StaticGenerator(variantBuildDir, taskName, variantResourceFile, debugLogging)
                Task mergeResourcesTask = project.tasks["merge${flavorString}Resources"]
                mergeResourcesTask.doLast {
                    generator.buildStatic()
                }

                variant.preBuild.doFirst {
                    if (!generator.isStaticHashValid()) {
                        mergeResourcesTask.outputs.upToDateWhen {
                            false
                        }
                    }
                }

                variant.javaCompile.dependsOn mergeResourcesTask
                variant.javaCompile.mustRunAfter mergeResourcesTask
                variant.registerJavaGeneratingTask(mergeResourcesTask, project.file(variantBuildDir))
            }
        }
    }

    private static String capitalise(final String line) {
        if (line == null || line.isEmpty()) {
            return ""
        } else {
            return Character.toUpperCase(line.charAt(0)).toString() + line.substring(1)
        }
    }
}