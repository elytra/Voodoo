// Generated by delombok at Sat Jul 14 01:46:55 CEST 2018
/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */
package com.skcraft.launcher.builder

import com.beust.jcommander.JCommander
import com.beust.jcommander.ParameterException
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.Result
import com.google.common.base.Strings
import com.google.common.io.ByteStreams
import com.google.common.io.CharStreams
import com.google.common.io.Closer
import com.google.common.io.Files
import com.skcraft.launcher.LauncherUtils
import com.skcraft.launcher.model.loader.InstallProfile
import com.skcraft.launcher.model.minecraft.Library
import com.skcraft.launcher.model.minecraft.VersionManifest
import com.skcraft.launcher.model.modpack.Manifest
import com.skcraft.launcher.util.Environment
import com.skcraft.launcher.util.SimpleLogFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import voodoo.exceptionHandler
import java.io.*
import java.util.*
import java.util.jar.JarFile
import java.util.logging.Level
import java.util.regex.Pattern
import kotlin.coroutines.coroutineContext

/**
 * Builds packages for the launcher.
 */
class PackageBuilder
/**
 * Create a new package builder.
 *
 * @param mapper the mapper
 * @param manifest the manifest
 */
@Throws(IOException::class)
constructor(private val mapper: ObjectMapper, private val manifest: Manifest) {
    private val properties: Properties = LauncherUtils.loadProperties(LauncherUtils::class.java, "launcher.properties", "com.skcraft.launcher.propertiesFile")
    private var writer: ObjectWriter? = null
    private val applicator: PropertiesApplicator = PropertiesApplicator(manifest)
    var isPrettyPrint = false
        set(prettyPrint) {
            writer = if (prettyPrint) {
                mapper.writerWithDefaultPrettyPrinter()
            } else {
                mapper.writer()
            }
            field = prettyPrint
        }
    private val loaderLibraries = arrayListOf<Library>()
    private var mavenRepos: List<String>? = null

    init {
        isPrettyPrint = false // Set writer
        Closer.create().use { closer ->
            mavenRepos = mapper.readValue<List<String>>(
                    closer.register(
                            LauncherUtils::class.java.getResourceAsStream("maven_repos.json")
                    ),
                    object : TypeReference<List<String>>() {}
            )
        }
    }

    @Throws(IOException::class)
    fun scan(dir: File?) {
        logSection("Scanning for .info.json files...")
        val scanner = FileInfoScanner(mapper)
        scanner.walk(dir!!)
        for (pattern in scanner.patterns) {
            applicator.register(pattern)
        }
    }

    @Throws(IOException::class)
    fun addFiles(dir: File?, destDir: File?) {
        logSection("Adding files to modpack...")
        val collector = ClientFileCollector(this.manifest, applicator, destDir!!)
        collector.walk(dir!!)
    }

    fun addLoaders(dir: File?, librariesDir: File?) {
        logSection("Checking for mod loaders to install...")
        val collected = LinkedHashSet<Library>()
        val files = dir!!.listFiles(JarFileFilter())
        if (files != null) {
            for (file in files) {
                try {
                    processLoader(collected, file, librariesDir)
                } catch (e: IOException) {
                    log.log(Level.WARNING, "Failed to add the loader at " + file.absolutePath, e)
                }

            }
        }
        this.loaderLibraries.addAll(collected)
        val version = manifest.versionManifest!!
        collected.addAll(version.libraries!!)
        version.libraries = collected
    }

    @Throws(IOException::class)
    private fun processLoader(loaderLibraries: LinkedHashSet<Library>, file: File, librariesDir: File?) {
        log.info("Installing " + file.name + "...")
        val jarFile = JarFile(file)
        val closer = Closer.create()
        try {
            val profileEntry = BuilderUtils.getZipEntry(jarFile, "install_profile.json")
            if (profileEntry != null) {
                val stream = jarFile.getInputStream(profileEntry)
                // Read file
                var data = CharStreams.toString(closer.register(InputStreamReader(stream)))
                data = data.replace(",\\s*\\}".toRegex(), "}") // Fix issues with trailing commas
                val profile = mapper.readValue<InstallProfile>(data, InstallProfile::class.java)
                val version = manifest.versionManifest
                // Copy tweak class arguments
                val args = profile.versionInfo.minecraftArguments
                val existingArgs = Strings.nullToEmpty(version?.minecraftArguments)
                val m = TWEAK_CLASS_ARG.matcher(args)
                while (m.find()) {
                    version?.minecraftArguments = existingArgs + " " + m.group()
                    log.info("Adding " + m.group() + " to launch arguments")
                }

                // Add libraries
                val libraries = profile.versionInfo.libraries
                for (library in libraries) {
                    if (version?.libraries?.contains(library) != true) {
                        loaderLibraries.add(library)
                    }
                }

                // Copy main class
                val mainClass = profile.versionInfo.mainClass
                version?.mainClass = mainClass
                log.info("Using $mainClass as the main class")

                // Extract the library
                val filePath = profile.installData.filePath
                val libraryPath = profile.installData.path
                val libraryEntry = BuilderUtils.getZipEntry(jarFile, filePath)
                if (libraryEntry != null) {
                    val library = Library(name = libraryPath)
                    val extractPath = File(librariesDir, library.getPath(Environment.instance))
                    Files.createParentDirs(extractPath)
                    ByteStreams.copy(closer.register(jarFile.getInputStream(libraryEntry)), Files.newOutputStreamSupplier(extractPath))
                } else {
                    log.warning("Could not find the file \'" + filePath + "\' in " + file.absolutePath + ", which means that this mod loader will not work correctly")
                }

            } else {
                log.warning("The file at " + file.absolutePath + " did not appear to have an install_profile.json file inside -- is it actually an installer for a mod loader?")
            }
        } finally {
            closer.close()
            jarFile.close()
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    fun downloadLibraries(librariesDir: File?) {
        logSection("Downloading libraries...")
        // TODO: Download libraries for different environments -- As of writing, this is not an issue

        val pool = newFixedThreadPoolContext(Runtime.getRuntime().availableProcessors() + 1, "pool")
        val jobs = mutableListOf<Job>()

        val env = Environment.instance
        for (library in loaderLibraries) {
            jobs += launch(context = exceptionHandler + pool) {
                val outputPath = File(librariesDir, library.getPath(env))
                if (!outputPath.exists()) {
                    Files.createParentDirs(outputPath)
                    var found = false
                    // Gather a list of repositories to download from
                    val sources = arrayListOf<String>() //Lists.newArrayList<String>()
                    library.baseUrl?.let {
                        sources.add(it)
                    }
                    sources.addAll(mavenRepos!!)
                    // Try each repository
                    for (baseUrl in sources) {
                        var pathname = library.getPath(env)
                        // Some repositories compress their files
                        val compressors = BuilderUtils.getCompressors(baseUrl)
                        for (compressor in compressors.reversed()) {
                            pathname = compressor.transformPathname(pathname)
                        }
                        val url = baseUrl + pathname
                        val tempFile = File.createTempFile("launcherlib", null)
                        try {
                            log.info("Downloading library " + library.name + " from " + url + "...")
//                        HttpRequest.get(URL(url)).execute().expectResponseCode(200).saveContent(tempFile)
                            val (_, response, result) = url.httpGet().response()
                            when (result) {
                                is Result.Success -> {
                                    log.info("writing to $tempFile")
                                    tempFile.writeBytes(result.value)
                                }
                                is Result.Failure -> {
                                    throw IOException("Did not get expected response code, got ${response.statusCode} for $url")
                                }
                            }
                        } catch (e: IOException) {
                            log.info("Could not get file from " + url + ": " + e.message)
                            continue
                        }

                        // Decompress (if needed) and write to file
                        val closer = Closer.create()
                        var inputStream: InputStream = closer.register(FileInputStream(tempFile))
                        inputStream = closer.register(BufferedInputStream(inputStream))
                        for (compressor in compressors) {
                            inputStream = closer.register(compressor.createInputStream(inputStream))
                        }
                        ByteStreams.copy(inputStream, closer.register(FileOutputStream(outputPath)))
                        tempFile.delete()
                        found = true
                        break
                    }
                    if (!found) {
                        log.warning("!! Failed to download the library " + library.name + " -- this means your copy of the libraries will lack this file")
                    }
                }
            }
        }

        log.info("waiting for library jobs to finish")
        runBlocking { jobs.forEach { it.join() } }
    }

    private fun validateManifest() {
        if (manifest.name.isNullOrEmpty()) {
            throw IllegalStateException("Package name is not defined")
        }
        if (manifest.gameVersion.isNullOrEmpty()) {
            throw IllegalStateException("Game version is not defined")
        }
    }

    @Throws(IOException::class)
    fun readConfig(path: File?) {
        if (path != null) {
            val config = read<BuilderConfig>(path)
            config.update(manifest)
            config.registerProperties(applicator)
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun readVersionManifest(path: File?) {
        logSection("Reading version manifest...")
        if (path!!.exists()) {
            val versionManifest = read<VersionManifest>(path)
            manifest.versionManifest = versionManifest
            log.info("Loaded version manifest from " + path.absolutePath)
        } else {
            val url = String.format(properties.getProperty("versionManifestUrl"), manifest.gameVersion)
            log.info("Fetching version manifest from $url...")
            val (_, response, result) = url.httpGet().responseString()
            manifest.versionManifest = when (result) {
                is Result.Success -> {
                    mapper.readValue(result.value)
                }
                is Result.Failure -> {
                    throw Exception("cannot HTTP GET $url status: ${response.statusCode}")
                }
            }
        }
    }

    @Throws(IOException::class)
    fun writeManifest(path: File) {
        logSection("Writing manifest...")
        manifest.features = applicator.featuresInUse
        val versionManifest = manifest.versionManifest
        if (versionManifest != null) {
            versionManifest.id = manifest.gameVersion
        }
        validateManifest()
        path.absoluteFile.parentFile.mkdirs()
        writer!!.writeValue(path, manifest)
        log.info("Wrote manifest to " + path.absolutePath)
    }

    @Throws(IOException::class)
    private inline fun <reified V> read(path: File?): V {
        try {
            return if (path == null) {
                V::class.java.newInstance()
            } else {
                mapper.readValue(path, V::class.java)
            }
        } catch (e: InstantiationException) {
            throw IOException("Failed to create " + V::class.java.canonicalName, e)
        } catch (e: IllegalAccessException) {
            throw IOException("Failed to create " + V::class.java.canonicalName, e)
        }

    }

    companion object {
        private val log = java.util.logging.Logger.getLogger(PackageBuilder::class.java.name)
        private val TWEAK_CLASS_ARG = Pattern.compile("--tweakClass\\s+([^\\s]+)")

        private fun parseArgs(args: Array<String>): BuilderOptions {
            val options = BuilderOptions()
            JCommander(options, *args)
            options.choosePaths()
            return options
        }

        /**
         * Build a package given the arguments.
         *
         * @param args arguments
         * @throws IOException thrown on I/O error
         * @throws InterruptedException on interruption
         */
        @Throws(IOException::class, InterruptedException::class)
        @JvmStatic
        fun main(args: Array<String>) {
            val options: BuilderOptions
            try {
                options = parseArgs(args)
            } catch (e: ParameterException) {
                JCommander().usage()
                System.err.println("error: " + e.message)
                System.exit(1)
                return
            }

            // Initialize
            SimpleLogFormatter.configureGlobalLogger()
            val mapper = ObjectMapper()
                    .registerModule(KotlinModule())
            mapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT)
            val manifest = Manifest(
                    minimumVersion = Manifest.MIN_PROTOCOL_VERSION
            )
            val builder = PackageBuilder(mapper, manifest)
            builder.isPrettyPrint = options.isPrettyPrinting
            // From config
            builder.readConfig(options.configPath)
            builder.readVersionManifest(options.versionManifestPath)
            // From options
            manifest.updateName(options.name)
            manifest.updateTitle(options.title)
            manifest.updateGameVersion(options.gameVersion)
            manifest.version = options.version
            manifest.librariesLocation = options.librariesLocation
            manifest.objectsLocation = options.objectsLocation
            builder.scan(options.filesDir)
            builder.addFiles(options.filesDir, options.objectsDir)
            builder.addLoaders(options.loadersDir, options.librariesDir)
            builder.downloadLibraries(options.librariesDir)
            builder.writeManifest(options.manifestPath!!)
            logSection("Done")
            log.info("Now upload the contents of " + options.outputPath + " to your web server or CDN!")
        }

        private fun logSection(name: String) {
            log.info("")
            log.info("--- $name ---")
        }
    }
}
