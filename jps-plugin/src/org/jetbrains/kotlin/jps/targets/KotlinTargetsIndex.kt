/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.jps.targets

import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.util.JpsPathUtil
import org.jetbrains.kotlin.config.TargetPlatformKind
import org.jetbrains.kotlin.jps.build.KotlinChunk
import org.jetbrains.kotlin.jps.build.KotlinCompileContext
import org.jetbrains.kotlin.jps.build.ModuleBuildTarget
import org.jetbrains.kotlin.jps.model.targetPlatform
import org.jetbrains.kotlin.utils.LibraryUtils

class KotlinTargetsIndex(
    val byJpsTarget: Map<ModuleBuildTarget, KotlinModuleBuildTarget<*>>,
    val chunks: List<KotlinChunk>,
    val chunksByJpsRepresentativeTarget: Map<ModuleBuildTarget, KotlinChunk>
)

internal class KotlinTargetsIndexBuilder internal constructor(
    private val uninitializedContext: KotlinCompileContext
) {
    private val byJpsModuleBuildTarget = mutableMapOf<ModuleBuildTarget, KotlinModuleBuildTarget<*>>()
    private val isKotlinJsStdlibJar = mutableMapOf<String, Boolean>()
    private val chunks = mutableListOf<KotlinChunk>()

    fun build(): KotlinTargetsIndex {
        val jpsContext = uninitializedContext.jpsContext

        // visit all kotlin build targets
        jpsContext.projectDescriptor.buildTargetIndex.getSortedTargetChunks(jpsContext).forEach { chunk ->
            val moduleBuildTargets = chunk.targets.mapNotNull {
                if (it is ModuleBuildTarget) ensureLoaded(it)!!
                else null
            }

            if (moduleBuildTargets.isNotEmpty()) {
                val kotlinChunk = KotlinChunk(uninitializedContext, moduleBuildTargets)
                moduleBuildTargets.forEach {
                    it.chunk = kotlinChunk
                }

                chunks.add(kotlinChunk)
            }
        }

        calculateChunkDependencies()

        return KotlinTargetsIndex(
            byJpsModuleBuildTarget,
            chunks,
            chunks.associateBy { it.representativeTarget.jpsModuleBuildTarget }
        )
    }

    private fun calculateChunkDependencies() {
        chunks.forEach { chunk ->
            val dependencies = mutableSetOf<KotlinModuleBuildTarget.Dependency>()

            chunk.targets.forEach {
                dependencies.addAll(calculateTargetDependencies(it))
            }

            chunk.dependencies = dependencies.toList()
            chunk.dependencies.forEach { dependency ->
                dependency.target.chunk._dependent!!.add(dependency)
            }
        }

        chunks.forEach {
            it.dependent = it._dependent!!.toList()
            it._dependent = null
        }
    }

    private fun calculateTargetDependencies(srcTarget: KotlinModuleBuildTarget<*>): List<KotlinModuleBuildTarget.Dependency> {
        val dependencies = mutableListOf<KotlinModuleBuildTarget.Dependency>()
        val classpathKind = JpsJavaClasspathKind.compile(srcTarget.isTests)

        // TODO(1.2.80): Ask for JPS API
        // Unfortunately JPS has no API for accessing "exported" flag while enumerating module dependencies,
        // but has API for getting all and exported only dependent modules.
        // So, lets first get set of all dependent targets, then remove exported only.
        val dependentTargets = mutableSetOf<KotlinModuleBuildTarget<*>>()

        JpsJavaExtensionService.dependencies(srcTarget.module)
            .includedIn(classpathKind)
            .processModules { destModule ->
                val destKotlinTarget = byJpsModuleBuildTarget[ModuleBuildTarget(destModule, srcTarget.isTests)]
                if (destKotlinTarget != null) {
                    dependentTargets.add(destKotlinTarget)
                }
            }

        JpsJavaExtensionService.dependencies(srcTarget.module)
            .includedIn(classpathKind)
            .exportedOnly()
            .processModules { module ->
                val destKotlinTarget = byJpsModuleBuildTarget[ModuleBuildTarget(module, srcTarget.isTests)]
                if (destKotlinTarget != null) {
                    dependentTargets.remove(destKotlinTarget)
                    dependencies.add(KotlinModuleBuildTarget.Dependency(srcTarget, destKotlinTarget, true))
                }
            }

        dependentTargets.forEach { destTarget ->
            dependencies.add(KotlinModuleBuildTarget.Dependency(srcTarget, destTarget, false))
        }

        if (srcTarget.isTests) {
            val srcProductionTarget = byJpsModuleBuildTarget[ModuleBuildTarget(srcTarget.module, false)]
            if (srcProductionTarget != null) {
                dependencies.add(KotlinModuleBuildTarget.Dependency(srcTarget, srcProductionTarget, true))
            }
        }

        return dependencies
    }


    private fun ensureLoaded(target: ModuleBuildTarget): KotlinModuleBuildTarget<*>? {
        return byJpsModuleBuildTarget.computeIfAbsent(target) {
            when (target.module.targetPlatform ?: detectTargetPlatform(target)) {
                is TargetPlatformKind.Common -> KotlinCommonModuleBuildTarget(uninitializedContext, target)
                is TargetPlatformKind.JavaScript -> KotlinJsModuleBuildTarget(uninitializedContext, target)
                is TargetPlatformKind.Jvm -> KotlinJvmModuleBuildTarget(uninitializedContext, target)
            }
        }
    }

    /**
     * Compatibility for KT-14082
     * todo: remove when all projects migrated to facets
     */
    private fun detectTargetPlatform(target: ModuleBuildTarget): TargetPlatformKind<*> {
        if (hasJsStdLib(target)) return TargetPlatformKind.JavaScript

        return TargetPlatformKind.DEFAULT_PLATFORM
    }

    private fun hasJsStdLib(target: ModuleBuildTarget): Boolean {
        JpsJavaExtensionService.dependencies(target.module)
            .recursively()
            .exportedOnly()
            .includedIn(JpsJavaClasspathKind.compile(target.isTests))
            .libraries
            .forEach { library ->
                for (root in library.getRoots(JpsOrderRootType.COMPILED)) {
                    val url = root.url

                    val isKotlinJsLib = isKotlinJsStdlibJar.computeIfAbsent(url) {
                        LibraryUtils.isKotlinJavascriptStdLibrary(JpsPathUtil.urlToFile(url))
                    }

                    if (isKotlinJsLib) return true
                }
            }

        return false
    }
}