/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.util.Computable
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.configuration.ui.notifications.ConfigureKotlinNotification
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.idea.util.projectStructure.allModules
import org.jetbrains.kotlin.idea.versions.getKotlinJvmRuntimeMarkerClass
import org.jetbrains.kotlin.idea.versions.hasKotlinJsKjsmFile
import org.jetbrains.kotlin.idea.vfilefinder.IDEVirtualFileFinder
import org.jetbrains.kotlin.utils.ifEmpty

data class RepositoryDescription(val id: String, val name: String, val url: String, val bintrayUrl: String?, val isSnapshot: Boolean)

val SNAPSHOT_REPOSITORY = RepositoryDescription(
        "sonatype.oss.snapshots",
        "Sonatype OSS Snapshot Repository",
        "http://oss.sonatype.org/content/repositories/snapshots",
        null,
        isSnapshot = true)

val EAP_REPOSITORY = RepositoryDescription(
        "bintray.kotlin.eap",
        "Bintray Kotlin EAP Repository",
        "http://dl.bintray.com/kotlin/kotlin-eap",
        "https://bintray.com/kotlin/kotlin-eap/kotlin/",
        isSnapshot = false)

val EAP_11_REPOSITORY = RepositoryDescription(
        "bintray.kotlin.eap",
        "Bintray Kotlin 1.1 EAP Repository",
        "http://dl.bintray.com/kotlin/kotlin-eap-1.1",
        "https://bintray.com/kotlin/kotlin-eap-1.1/kotlin/",
        isSnapshot = false)

val EAP_12_REPOSITORY = RepositoryDescription(
        "bintray.kotlin.eap",
        "Bintray Kotlin 1.2 EAP Repository",
        "http://dl.bintray.com/kotlin/kotlin-eap-1.2",
        "https://bintray.com/kotlin/kotlin-eap-1.2/kotlin/",
        isSnapshot = false)

val MAVEN_CENTRAL = "mavenCentral()"

val JCENTER = "jcenter()"

val KOTLIN_GROUP_ID = "org.jetbrains.kotlin"

fun isRepositoryConfigured(repositoriesBlockText: String): Boolean =
        repositoriesBlockText.contains(MAVEN_CENTRAL) || repositoriesBlockText.contains(JCENTER)

fun DependencyScope.toGradleCompileScope(isAndroidModule: Boolean) = when (this) {
    DependencyScope.COMPILE -> "compile"
    // TODO: We should add testCompile or androidTestCompile
    DependencyScope.TEST -> if (isAndroidModule) "compile" else "testCompile"
    DependencyScope.RUNTIME -> "runtime"
    DependencyScope.PROVIDED -> "compile"
    else -> "compile"
}

fun RepositoryDescription.toGroovyRepositorySnippet() = "maven {\nurl '$url'\n}"

fun RepositoryDescription.toKotlinRepositorySnippet() = "maven {\nsetUrl(\"$url\")\n}"

fun getRepositoryForVersion(version: String): RepositoryDescription? = when {
    isSnapshot(version) -> SNAPSHOT_REPOSITORY
    useEapRepository(2, version) -> EAP_12_REPOSITORY
    useEapRepository(1, version) -> EAP_11_REPOSITORY
    isEap(version) -> EAP_REPOSITORY
    else -> null
}

fun isModuleConfigured(module: Module, sourceRootModules: List<Module>): Boolean {
    return allConfigurators().any {
        it.getStatus(module, sourceRootModules) == ConfigureKotlinStatus.CONFIGURED
    }
}

fun getModulesWithKotlinFiles(project: Project): Collection<Module> {
    if (project.isDisposed) {
        return emptyList()
    }

    return runReadAction {
        if (!FileTypeIndex.containsFileOfType(KotlinFileType.INSTANCE, GlobalSearchScope.projectScope(project))) {
            return@runReadAction emptyList()
        }

        project.allModules()
                .filter { module ->
                    FileTypeIndex.containsFileOfType(KotlinFileType.INSTANCE, module.getModuleScope(true))
                }
    }
}

fun getConfigurableModulesWithKotlinFiles(project: Project): Map<Module, List<Module>> {
    val modules = getModulesWithKotlinFiles(project)
    if (modules.isEmpty()) return emptyMap()

    return ModuleSourceRootMap(project).groupByBaseModules(modules)
}

fun showConfigureKotlinNotificationIfNeeded(module: Module) {
    val moduleWithBase = ModuleSourceRootMap(module.project).groupByBaseModules(listOf(module)).entries.single()
    if (isModuleConfigured(moduleWithBase.key, moduleWithBase.value)) return

    ConfigureKotlinNotificationManager.notify(module.project)
}

fun showConfigureKotlinNotificationIfNeeded(project: Project, excludeModules: List<Module> = emptyList()) {
    val notificationString = DumbService.getInstance(project).runReadActionInSmartMode(Computable {
        val modules = getConfigurableModulesWithKotlinFiles(project) - excludeModules
        if (modules.all { (module, sourceRootModules) -> isModuleConfigured(module, sourceRootModules) })
            null
        else
            ConfigureKotlinNotification.getNotificationString(project, excludeModules)
    })

    if (notificationString != null) {
        ApplicationManager.getApplication().invokeLater {
            ConfigureKotlinNotificationManager.notify(project, ConfigureKotlinNotification(project, excludeModules, notificationString))
        }
    }
}

fun getAbleToRunConfigurators(project: Project): Collection<KotlinProjectConfigurator> {
    val modules = getConfigurableModules(project)

    return allConfigurators().filter { configurator ->
        modules.any { (module, sourceRootModules) ->
            configurator.getStatus(module, sourceRootModules) == ConfigureKotlinStatus.CAN_BE_CONFIGURED
        }
    }
}

fun getConfigurableModules(project: Project): Map<Module, List<Module>> {
    return getConfigurableModulesWithKotlinFiles(project).ifEmpty {
        ModuleSourceRootMap(project).groupByBaseModules(project.allModules())
    }
}

fun getAbleToRunConfigurators(module: Module): Collection<KotlinProjectConfigurator> {
    val moduleWithBase = ModuleSourceRootMap(module.project).groupByBaseModules(listOf(module)).entries.single()
    return allConfigurators().filter {
        it.getStatus(moduleWithBase.key, moduleWithBase.value) == ConfigureKotlinStatus.CAN_BE_CONFIGURED
    }
}

fun getConfiguratorByName(name: String): KotlinProjectConfigurator? {
    return allConfigurators().firstOrNull { it.name == name }
}

fun allConfigurators() = Extensions.getExtensions(KotlinProjectConfigurator.EP_NAME)

fun getCanBeConfiguredModules(project: Project, configurator: KotlinProjectConfigurator): List<Module> {
    return ModuleSourceRootMap(project).groupByBaseModules(project.allModules())
            .filter { (module, sourceRootModules) -> configurator.canConfigure(module, sourceRootModules) }
            .keys.toList()
}

private fun KotlinProjectConfigurator.canConfigure(module: Module, sourceRootModules: List<Module>) =
        getStatus(module, sourceRootModules) == ConfigureKotlinStatus.CAN_BE_CONFIGURED &&
        (allConfigurators().toList() - this).none { it.getStatus(module, sourceRootModules) == ConfigureKotlinStatus.CONFIGURED }

fun getCanBeConfiguredModulesWithKotlinFiles(project: Project, configurator: KotlinProjectConfigurator): List<Module> {
    val modules = getConfigurableModulesWithKotlinFiles(project)
    return modules.filter { (module, sourceRootModules) ->
        configurator.getStatus(module, sourceRootModules) == ConfigureKotlinStatus.CAN_BE_CONFIGURED
    }.keys.toList()
}

fun getCanBeConfiguredModulesWithKotlinFiles(project: Project, excludeModules: Collection<Module> = emptyList()): Collection<Module> {
    val modulesWithKotlinFiles: Map<Module, List<Module>> = getConfigurableModulesWithKotlinFiles(project) - excludeModules
    val configurators = allConfigurators()
    return modulesWithKotlinFiles.filter { (module, sourceRootModules) ->
        configurators.any { it.getStatus(module, sourceRootModules) == ConfigureKotlinStatus.CAN_BE_CONFIGURED }
    }.keys
}

fun Map<Module, List<Module>>.allConfigured() =
        all { (module, sourceRootModules) -> isModuleConfigured(module, sourceRootModules) }


fun hasAnyKotlinRuntimeInScope(module: Module): Boolean {
    val scope = module.getModuleWithDependenciesAndLibrariesScope(hasKotlinFilesOnlyInTests(module))
    return getKotlinJvmRuntimeMarkerClass(module.project, scope) != null ||
           hasKotlinJsKjsmFile(module.project, scope) ||
           hasKotlinCommonRuntimeInScope(scope)
}

fun hasKotlinJvmRuntimeInScope(module: Module): Boolean {
    val scope = module.getModuleWithDependenciesAndLibrariesScope(hasKotlinFilesOnlyInTests(module))
    return getKotlinJvmRuntimeMarkerClass(module.project, scope) != null
}

fun hasKotlinJsRuntimeInScope(module: Module): Boolean {
    val scope = module.getModuleWithDependenciesAndLibrariesScope(hasKotlinFilesOnlyInTests(module))
    return hasKotlinJsKjsmFile(module.project, scope)
}

fun hasKotlinCommonRuntimeInScope(scope: GlobalSearchScope): Boolean {
    return IDEVirtualFileFinder(scope).hasMetadataPackage(KotlinBuiltIns.BUILT_INS_PACKAGE_FQ_NAME)
}

fun hasKotlinFilesOnlyInTests(module: Module): Boolean {
    return !hasKotlinFilesInSources(module) && FileTypeIndex.containsFileOfType(KotlinFileType.INSTANCE, module.getModuleScope(true))
}

fun hasKotlinFilesInSources(module: Module): Boolean {
    return FileTypeIndex.containsFileOfType(KotlinFileType.INSTANCE, module.getModuleScope(false))
}

fun isSnapshot(version: String): Boolean {
    return version.contains("SNAPSHOT", ignoreCase = true)
}

fun isEap(version: String): Boolean {
    return version.contains("rc") || version.contains("eap")
}

fun useEapRepository(minorKotlinVersion: Int, version: String): Boolean {
    return Regex("1\\.$minorKotlinVersion(\\.\\d)?-[A-Za-z][A-Za-z0-9-]*").matches(version) &&
           !version.startsWith("1.$minorKotlinVersion.0-dev")
}