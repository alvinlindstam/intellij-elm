package org.elm.lang.core.stubs.index

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import org.elm.lang.core.psi.elements.ElmModuleDeclaration
import org.elm.lang.core.stubs.ElmFileStub
import org.elm.lang.core.stubs.ElmModuleDeclarationStub
import org.elm.openapiext.findFileByMaybeRelativePath
import org.elm.openapiext.findFileByPathTestAware
import org.elm.workspace.ElmProject

private val logger = Logger.getInstance(ElmModulesIndex::class.java)

/**
 * Find Elm modules within an Elm project.
 *
 * This API takes into account [ElmProject] structure to determine which modules
 * should be visible, resolving each module to the correct version for the project.
 */
class ElmModulesIndex : StringStubIndexExtension<ElmModuleDeclaration>() {

    override fun getVersion() =
            ElmFileStub.Type.stubVersion

    override fun getKey(): StubIndexKey<String, ElmModuleDeclaration> =
            KEY

    companion object {

        /**
         * Returns an Elm module named [moduleName] which is visible to [elmProject], if any
         */
        fun get(moduleName: String, intellijProject: Project, elmProject: ElmProject?): ElmModuleDeclaration? {
            if (elmProject == null) {
                return null
            }
            val elmModules = rawGet(moduleName, intellijProject)
            return elmModules.firstOrNull { elmProject.exposes(it) }
        }


        /**
         * Returns all Elm modules which are visible to [elmProject]
         */
        fun getAll(intellijProject: Project, elmProject: ElmProject?): List<ElmModuleDeclaration> {
            if (elmProject == null) {
                return emptyList()
            }
            val allModules = rawGetAll(intellijProject)
            return allModules.filter { elmProject.exposes(it) }
        }


        /**
         * Returns all Elm modules whose names match an element in [moduleNames] and which are visible to [elmProject]
         */
        fun getAll(moduleNames: Collection<String>, intellijProject: Project, elmProject: ElmProject?): List<ElmModuleDeclaration> {
            if (elmProject == null) {
                return emptyList()
            }
            val allModules = rawGetAll(moduleNames, intellijProject)
            return allModules.filter { elmProject.exposes(it) }
        }


        // INTERNALS


        val KEY: StubIndexKey<String, ElmModuleDeclaration> =
                StubIndexKey.createIndexKey("org.elm.lang.core.stubs.index.ElmModulesIndex")

        fun index(stub: ElmModuleDeclarationStub, indexSink: IndexSink) {
            val key = makeKey(stub.psi)
            indexSink.occurrence(KEY, key)
        }

        private fun makeKey(moduleDeclaration: ElmModuleDeclaration) =
                makeKey(moduleDeclaration.name)

        private fun makeKey(moduleName: String) =
                moduleName


        /**
         * Returns all module declarations with name [moduleName]
         */
        private fun rawGet(moduleName: String, project: Project): List<ElmModuleDeclaration> {
            val key = makeKey(moduleName)
            return StubIndex.getElements(KEY, key, project,
                    GlobalSearchScope.allScope(project),
                    ElmModuleDeclaration::class.java).toList()
        }


        /**
         * Returns all module declarations in [project] whose module name
         * matches an item in [moduleNames]
         */
        private fun rawGetAll(moduleNames: Collection<String>, project: Project): List<ElmModuleDeclaration> {
            val index = StubIndex.getInstance()
            val results = mutableListOf<ElmModuleDeclaration>()

            for (key in moduleNames) {
                index.processElements(KEY, key, project, GlobalSearchScope.allScope(project),
                        ElmModuleDeclaration::class.java) {
                    results.add(it)
                }
            }
            return results
        }


        /**
         * Returns all module declarations in [project]
         */
        private fun rawGetAll(project: Project): List<ElmModuleDeclaration> {
            return rawGetAll(StubIndex.getInstance().getAllKeys(KEY, project), project)
        }

    }
}

/**
 * Returns true if [moduleDeclaration] is visible within the receiver [ElmProject].
 */
private fun ElmProject.exposes(moduleDeclaration: ElmModuleDeclaration): Boolean {

    // Check if the module is reachable from this project's source directories.
    if (sourceDirectoryContains(moduleDeclaration))
        return true


    // Check if the module is reachable from this project's dependencies
    return allResolvedDependencies.asSequence()
            .filter { it.exposedModules.contains(moduleDeclaration.name) }
            .any { it.sourceDirectoryContains(moduleDeclaration) }
}


/**
 * Returns true if [moduleDeclaration] can be found in the receiver's source directories.
 */
private fun ElmProject.sourceDirectoryContains(moduleDeclaration: ElmModuleDeclaration): Boolean {

    val moduleDeclProject = moduleDeclaration.elmProject
            ?: return false

    val candidateSrcDirs = if (moduleDeclProject.manifestPath == manifestPath) {
        // They belong to the same Elm project, all source dirs are candidates
        absoluteSourceDirectories
    } else {
        // The module declaration does not belong to this Elm project.
        //
        // Normally this means that there's no match, but it is possible
        // to have 2 Elm projects that share one-or-more source directories.
        // There is no guarantee that they are mutually exclusive.
        //
        // The only valid candidates are those that are shared between the 2 projects.
        sharedSourceDirs(moduleDeclProject)
    }

    val elmModuleRelativePath = moduleDeclaration.name.replace('.', '/') + ".elm"
    return candidateSrcDirs
            .mapNotNull { findFileByPathTestAware(it) }
            .any { it.findFileByMaybeRelativePath(elmModuleRelativePath) != null }
}
