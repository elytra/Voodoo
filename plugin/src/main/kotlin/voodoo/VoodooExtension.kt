package voodoo

import org.gradle.api.Project
import voodoo.data.CustomTask
import voodoo.data.TaskBuilder
import voodoo.poet.Poet
import voodoo.poet.generator.CurseGenerator
import voodoo.poet.generator.CurseSection
import voodoo.poet.generator.ForgeGenerator
import voodoo.util.SharedFolders
import java.io.File

open class VoodooExtension(project: Project) {
    init {
        SharedFolders.RootDir.default = project.rootDir
    }

    var local: Boolean = false
    val localVoodooProjectLocation: File = project.rootDir.parentFile

    internal var tasks: List<CustomTask> = listOf()
        private set

    @Deprecated("use add")
    fun addTask(name: String, description: String = "custom task $name", parameters: List<String>) {
        tasks += CustomTask(name, description, parameters)
    }

//    fun add(name: String, description: String = "custom task $name", parameters: List<TaskType>) {
//        tasks += CustomTask(name, description, parameters.map { it.command })
//    }

    fun addTask(name: String, description: String = "custom task $name", taskBuilder: TaskBuilder.() -> Unit) {
        val builder = TaskBuilder()
        builder.taskBuilder()

        tasks += CustomTask(name, description, builder.tasks.map { it.command })
    }

    fun rootDir(resolver: () -> File) {
        SharedFolders.RootDir.resolver = resolver
    }

    fun packDirectory(resolver: (rootDir: File) -> File) {
        SharedFolders.PackDir.resolver = resolver
    }

    fun tomeDirectory(resolver: (rootDir: File) -> File) {
        SharedFolders.TomeDir.resolver = resolver
    }

    fun includeDirectory(resolver: (rootDir: File) -> File) {
        SharedFolders.IncludeDir.resolver = resolver
    }

    fun generatedSource(resolver: (rootDir: File, id: String) -> File) {
        SharedFolders.GeneratedSrc.resolver = resolver
    }

    fun uploadDirectory(resolver: (rootDir: File, id: String) -> File) {
        SharedFolders.UploadDir.resolver = resolver
    }

    fun docDirectory(resolver: (rootDir: File, id: String) -> File) {
        SharedFolders.DocDir.resolver = resolver
    }

    internal val forgeGenerators: MutableList<ForgeGenerator> = mutableListOf()
    fun generateForge(name: String, vararg versions: String) {
        forgeGenerators += ForgeGenerator(name, listOf(*versions))
    }

    internal val curseGenerators: MutableList<CurseGenerator> = mutableListOf()
    fun generateCurseforgeMods(
        name: String,
        vararg versions: String,
        slugSanitizer: (String) -> String = Poet::defaultSlugSanitizer
    ) {
        curseGenerators += CurseGenerator(name, CurseSection.MODS, listOf(*versions), slugSanitizer)
    }
    fun generateCurseforgeTexturepacks(
        name: String,
        vararg versions: String,
        slugSanitizer: (String) -> String = Poet::defaultSlugSanitizer
    ) {
        curseGenerators += CurseGenerator(name, CurseSection.TEXTURE_PACKS, listOf(*versions), slugSanitizer)
    }
}