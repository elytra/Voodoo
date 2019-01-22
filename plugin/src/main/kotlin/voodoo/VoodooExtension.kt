package voodoo

import org.gradle.api.Project
import voodoo.data.CustomTask
import java.io.File

open class VoodooExtension(project: Project) {
    fun rootDir(resolver: () -> File) {
        rootDirResolver = resolver
    }

    fun generatedSource(resolver: (rootDir: File) -> File) {
        generatedSourceResolver = resolver
    }

    fun packDirectory(resolver: (rootDir: File) -> File) {
        packDirectoryResolver = resolver
    }
    fun tomeDirectory(resolver: (rootDir: File) -> File) {
        tomeDirectoryResolver = resolver
    }
    fun docDirectory(resolver: (rootDir: File) -> File) {
        docDirectoryResolver = resolver
    }

    internal var tasks: List<CustomTask> = listOf()
        private set

    fun addTask(name: String, description: String = "custom task $name", parameters: List<String>) {
        tasks += CustomTask(name, description, parameters)
    }

    private var rootDirResolver: () -> File = { project.rootDir }
    private var generatedSourceResolver: (rootDir: File) -> File = { getRootDir.resolve(".voodoo") }
    private var packDirectoryResolver: (rootDir: File) -> File = { getRootDir.resolve("packs") }
    private var tomeDirectoryResolver: (rootDir: File) -> File = { getRootDir.resolve("tome") }
    private var docDirectoryResolver: (rootDir: File) -> File = { getRootDir.resolve("docs") }

    internal val getRootDir: File get() = rootDirResolver()
    internal val getGeneratedSrc: File get() = generatedSourceResolver(getRootDir)
    internal val getPackDir: File get() = packDirectoryResolver(getRootDir)
    internal val getTomeDir: File get() = tomeDirectoryResolver(getRootDir)
    internal val getDocDir: File get() = docDirectoryResolver(getRootDir)
}