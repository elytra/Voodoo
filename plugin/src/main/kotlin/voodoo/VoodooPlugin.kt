package voodoo

import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.AbstractTask
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.task
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import voodoo.plugin.PluginConstants
import voodoo.poet.Poet
import voodoo.util.SharedFolders

open class VoodooPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val voodooExtension = project.run {
            logger.lifecycle("version: ${PluginConstants.FULL_VERSION}")

            pluginManager.apply("org.gradle.idea")
            pluginManager.apply("org.jetbrains.kotlin.jvm")

            extensions.create<VoodooExtension>("voodoo", project)
        }

        val voodooConfiguration = project.configurations.create("voodoo")

        project.afterEvaluate {
            SharedFolders.PackDir.get().mkdirs()
            SharedFolders.IncludeDir.get().mkdirs()
            SharedFolders.TomeDir.get().mkdirs()

            // runs poet when the plugin is applied
//            Poet.generateAll(getRootDir = project.getRootDir, generatedSrcDir = voodooExtension.getGeneratedSrc)

//            val compileKotlin = tasks.getByName<KotlinCompile>("compileKotlin")

//            tasks.withType<KotlinCompile> {
//                kotlinOptions {
//                    languageVersion = "1.3"
//                    jvmTarget = "1.8"
//                }
// //                dependsOn(poet)
//            }
            val generatedSrcDir = SharedFolders.GeneratedSrcShared.get()

            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility = JavaVersion.VERSION_1_8
            }

            extensions.configure<KotlinJvmProjectExtension> {
                sourceSets.maybeCreate("main").kotlin.apply {
                    srcDir(SharedFolders.IncludeDir.get())
                    srcDir(SharedFolders.PackDir.get())
                    srcDir(SharedFolders.TomeDir.get())
                }
            }


            val (downloadVoodoo, voodooJar) = if (PluginConstants.BUILD == "dev") {
                val downloadTask = task<MavenLocalVoodooJarTask>("localVoodoo") {
                    group = "voodoo"
                    description = "Copies the voodoo jar from mavenLocal()"
                }
                downloadTask as DefaultTask to downloadTask.jarFile
            } else {
                val downloadTask = task<DownloadVoodooTask>("downloadVoodoo") {
                    group = "voodoo"
                    description = "Downloads the voodoo jar from jenkins"
                }
                downloadTask to downloadTask.jarFile
            }

//            project.dependencies {
//                add("api", files(voodooJar))
//            }

            task<AbstractTask>("voodooVersion") {
                group = "voodoo"
                description = "prints the used voodoo version"
                doFirst {
                    logger.lifecycle("version: ${PluginConstants.FULL_VERSION}")
                }
            }

            task<CreatePackTask>("createpack") {
                rootDir = SharedFolders.RootDir.get()
                packsDir = SharedFolders.PackDir.get()

                doLast {
                    logger.lifecycle("created pack $id")
                }
            }
            task<CurseImportTask>("importCurse") {
                rootDir = SharedFolders.RootDir.get()
                packsDir = SharedFolders.PackDir.get()
            }

            val libs = project.rootDir.resolve("libs")

            val copyLibs = task<AbstractTask>("copyVoodooLibs") {
                dependsOn(downloadVoodoo)
                doFirst {
                    val libraries = voodooConfiguration.resolve()
                    libs.deleteRecursively()
                    if (libraries.isNotEmpty()) libs.mkdirs()
                    for (file in libraries) {
                        file.copyTo(libs.resolve(file.name))
                    }
                }
            }

//            val javac = File(JavaEnvUtils.getJdkExecutable("javac"))
//            val jdkHome = javac.parentFile.parentFile
//            logger.lifecycle("jdkHome: $jdkHome")

            extensions.configure<SourceSetContainer> {
//                val runtimeClasspath = maybeCreate("main").runtimeClasspath
                extensions.configure<KotlinJvmProjectExtension> {
                    sourceSets.maybeCreate("main").kotlin.apply {
                        srcDir(generatedSrcDir)
                    }
                }

                extensions.configure<IdeaModel> {
                    module {
                        generatedSourceDirs.add(generatedSrcDir)
                    }
                }

                val curseGeneratorTasks = voodooExtension.curseGenerators.map { generator ->
                    logger.info("adding generate ${generator.name}")
                    task<AbstractTask>("generate${generator.name}") {
                        group = "generators"
                        outputs.upToDateWhen { false }
//                        outputs.cacheIf { true }
                        dependsOn(downloadVoodoo)
                        doLast {
                            generatedSrcDir.mkdirs()
                            val generatedFile = runBlocking {
                                Poet.generateCurseforge(
                                    name = generator.name,
                                    slugIdMap = Poet.requestSlugIdMap(
                                        section = generator.section.sectionName,
                                        gameVersions = generator.mcVersions.toList(),
                                        categories = generator.categories
                                    ),
                                    slugSanitizer = generator.slugSanitizer,
                                    folder = generatedSrcDir,
                                    section = generator.section,
                                    gameVersions = generator.mcVersions.toList()
                                )
                            }
                            logger.lifecycle("generated: $generatedFile")
                        }
                    }
                }
                val forgeGeneratorTasks = voodooExtension.forgeGenerators.map { generator ->
                    logger.info("adding generate ${generator.name}")
                    task<AbstractTask>("generate${generator.name}") {
                        group = "generators"
                        outputs.upToDateWhen { false }
//                        outputs.cacheIf { true }
                        dependsOn(downloadVoodoo)
                        doLast {
                            generatedSrcDir.mkdirs()
                            val generatedFile = runBlocking {
                                Poet.generateForge(
                                    name = generator.name,
                                    mcVersionFilters = generator.mcVersions.toList(),
                                    folder = generatedSrcDir
                                )
                            }
                            logger.lifecycle("generated: $generatedFile")
                        }
                    }
                }

                val fabricGeneratorTasks = voodooExtension.fabricGenerators.map { generator ->
                    logger.info("adding generate ${generator.name}")
                    task<AbstractTask>("generate${generator.name}") {
                        group = "generators"
                        outputs.upToDateWhen { false }
//                        outputs.cacheIf { true }
                        dependsOn(downloadVoodoo)
                        doLast {
                            generatedSrcDir.mkdirs()
                            val generatedFile = runBlocking {
                                Poet.generateFabric(
                                    name = generator.name,
                                    mcVersionFilters = generator.mcVersions.toList(),
                                    stable = generator.stable,
                                    folder = generatedSrcDir
                                )
                            }
                            logger.lifecycle("generated: $generatedFile")
                        }
                    }
                }

                val generateAllTask = task<AbstractTask>("generateAll") {
                    group = "generators"
                    dependsOn(curseGeneratorTasks)
                    dependsOn(forgeGeneratorTasks)
                }

                SharedFolders.PackDir.get()
                    .listFiles { _, name -> name.endsWith(".voodoo.kts") }
                    .forEach { sourceFile ->
                        val id = sourceFile.name.substringBeforeLast(".voodoo.kts").toLowerCase()

                        // add pack specific generated sources
                        extensions.configure<KotlinJvmProjectExtension> {
                            sourceSets.maybeCreate("main").kotlin.apply {
                                srcDir(SharedFolders.GeneratedSrc.get(id = id))
                            }
                        }

                        extensions.configure<IdeaModel> {
                            module {
                                generatedSourceDirs.add(SharedFolders.GeneratedSrc.get(id = id))
                            }
                        }

                        task<VoodooTask>(id.toLowerCase()) {
//                            dependsOn(poet)
                            dependsOn(copyLibs)
                            dependsOn(downloadVoodoo)
                            dependsOn(generateAllTask)

                            classpath(voodooJar)

                            scriptFile = sourceFile.canonicalPath
                            description = id
                            group = id

                            SharedFolders.setSystemProperties(id) { name: String, value: Any ->
                                systemProperty(name, value)
                            }
                            doFirst {
                                logger.lifecycle("classpath: $voodooJar")
                                logger.lifecycle("classpath.length(): ${voodooJar.length()}")
                            }
//                            systemProperty("voodoo.jdkHome", jdkHome.path)
                        }

                        voodooExtension.tasks.forEach { customTask ->
                            val (taskName, taskDescription, arguments) = customTask
                            task<VoodooTask>(id + "_" + taskName) {
//                                dependsOn(poet)
                                dependsOn(copyLibs)
                                dependsOn(downloadVoodoo)
                                dependsOn(generateAllTask)

                                classpath(voodooJar)

                                scriptFile = sourceFile.canonicalPath
                                description = taskDescription
                                group = id
                                val nestedArgs = arguments.map { it.split(" ") }
                                args = nestedArgs.reduceRight { acc, list -> acc + "-" + list }

                                SharedFolders.setSystemProperties(id) { name: String, value: Any ->
                                    systemProperty(name, value)
                                }
//                                systemProperty("voodoo.jdkHome", jdkHome.path)
                            }
                        }
                    }
            }
        }
    }
}