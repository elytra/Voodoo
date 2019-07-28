// Generated by delombok at Sat Jul 14 04:26:21 CEST 2018
/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */
package com.skcraft.launcher.model.modpack

import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Optional
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.Transient

@Serializable
data class FileInstall(
    var version: String? = null,
    var hash: String,
    var location: String,
    var to: String,

    var size: Long = 0,
    @SerialName("userFile")
    var isUserFile: Boolean = false,

    var manifest: Manifest? = null,
    @SerialName("when")
    var conditionWhen: Condition? = null
) {
    val type: String = "file"
    @Transient
    val targetPath: String
        get() = this.to

    @Serializer(forClass = FileInstall::class)
    companion object : KSerializer<FileInstall> {
        override fun serialize(encoder: Encoder, obj: FileInstall) {
            val elemOutput = encoder.beginStructure(descriptor)
            elemOutput.encodeStringElement(descriptor, 8, obj.type)
            obj.conditionWhen?.let { conditionWhen ->
                elemOutput.encodeSerializableElement(descriptor, 7, Condition.serializer(), conditionWhen)
            }
            obj.version?.let { version ->
                elemOutput.encodeStringElement(descriptor, 0, version)
            }
            elemOutput.encodeStringElement(descriptor, 1, obj.hash)
            elemOutput.encodeStringElement(descriptor, 2, obj.location)
            elemOutput.encodeStringElement(descriptor, 3, obj.to)
            if (obj.size != 0L) elemOutput.encodeLongElement(descriptor, 4, obj.size)
            if (obj.isUserFile) {
                elemOutput.encodeBooleanElement(descriptor, 5, obj.isUserFile)
            }
            obj.manifest?.let { manifest ->
                elemOutput.encodeSerializableElement(descriptor, 6, Manifest.serializer(), manifest)
            }
            elemOutput.endStructure(descriptor)
        }
    }
}
