package voodoo.builder

import aballano.kotlinmemoization.tuples.Quadruple
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import khttp.get
import mu.KLogging

/**
 * Created by nikky on 30/12/17.
 * @author Nikky
 * @version 1.0
 */
object Forge : KLogging() {
    var data: ForgeData = getForgeData()

    fun getForge(forgeVersion: String, mcVersion: String/*, spongeEntry: Entry?*/): Pair<Entry, String> {
        val (url, filename, longVersion, version) = getForgeUrl(forgeVersion, mcVersion)

        val entry = Entry(
                provider = Provider.DIRECT,
                name = "Minecraft Forge",
                url = url,
                fileName = filename,
//                    packageType = PackageType.LOADER,
                basePath = "loaders",
                targetPath = ".",
                path = ".",
                cacheRelpath = "FORGE/$longVersion"
        )
        return Pair(entry, version)
    }

    fun getForgeUrl(version: String, mcVersion: String): Quadruple<String, String, String, String> {
        var versionStr = ""
        if (version.equals("recommended", true) || version.equals("latest", true)) {
            val promoVersion = "$mcVersion-${version.toLowerCase()}"
            versionStr = data.promos[promoVersion]?.toString() ?: ""
        } else {
            if (data.number.containsKey(version)) {
                versionStr = version
            } else {
                versionStr = data.promos[version]?.toString() ?: ""
                if (versionStr.isBlank()) {
                    var versionList = data.branches.getOrDefault(version, emptyList())
                    if (versionList.isEmpty()) {
                        versionList = data.mcversion.getOrDefault(version, emptyList())
                    }

                    if (versionList.isNotEmpty()) {
                        versionList = versionList.filter { i -> data.number[i.toString()]?.mcversion == mcVersion }
                    }
                    if (versionList.isEmpty()) {
                        throw IllegalArgumentException("forge value is invalid")
                    }
                    versionStr = versionList.max().toString()
                }
            }
        }
        val webpath = data.webpath
        val artifact = data.number.get(versionStr)!!
        val mcversion = artifact.mcversion
        val forgeVersion = artifact.version
        val branch = artifact.branch
        var longVersion = "$mcVersion-$forgeVersion"
        if (branch != null) {
            longVersion += "-$branch"
        }
        val fileName = "forge-$longVersion-installer.jar"
        val url = "$webpath/$longVersion/$fileName"

        return Quadruple(url, fileName, longVersion, forgeVersion)
    }

    fun getForgeData(): ForgeData {
        val r = get("http://files.minecraftforge.net/maven/net/minecraftforge/forge/json")
        if (r.statusCode == 200) {
            val mapper = jacksonObjectMapper() // Enable YAML parsing
            mapper.registerModule(KotlinModule()) // Enable Kotlin support

            val state = mapper.readValue<ForgeData>(r.text)
            return state
        }

        throw Exception("cannot get forge data")
    }

}

data class ForgeData(
        val adfocus: String,
        val artifact: String,
        val homepage: String,
        val webpath: String,
        val name: String,
        val branches: Map<String, List<Int>>,
        val mcversion: Map<String, List<Int>>,
        val number: Map<String, Artifact>,
        val promos: Map<String, Int>
)

data class Artifact(
        val branch: String?,
        val build: Int,
        val files: List<Any>,
        val mcversion: String,
        val modified: Long,
        val version: String
)
