// Generated by delombok at Sat Jul 14 04:26:21 CEST 2018
/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */
package com.skcraft.launcher.model.modpack


abstract class ManifestEntry {
    var manifest: Manifest? = null
    var conditionWhen: Condition? = null


    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is ManifestEntry) return false
        if (!other.canEqual(this as Any)) return false
        if (if (this.manifest == null) other.manifest != null else this.manifest != other.manifest) return false
        return !if (this.conditionWhen == null) other.conditionWhen != null else this.conditionWhen != other.conditionWhen
    }

    protected open fun canEqual(other: Any): Boolean {
        return other is ManifestEntry
    }

    override fun hashCode(): Int {
        val PRIME = 59
        var result = 1
        result = result * PRIME + if (manifest == null) 43 else manifest!!.hashCode()
        result = result * PRIME + if (conditionWhen == null) 43 else conditionWhen!!.hashCode()
        return result
    }

    override fun toString(): String {
        return "ManifestEntry(conditionWhen=" + this.conditionWhen + ")"
    }
}
