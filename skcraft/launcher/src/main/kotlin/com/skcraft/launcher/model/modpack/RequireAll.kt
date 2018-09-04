// Generated by delombok at Sat Jul 14 04:26:21 CEST 2018
/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */
package com.skcraft.launcher.model.modpack

import java.util.ArrayList

class RequireAll(
        private var features: MutableList<Feature> = ArrayList()
) : Condition {
    override fun matches(): Boolean {
        for (feature in features) {
            if (!feature.selected) {
                return false
            }
        }
        return true
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is RequireAll) return false
        if (!other.canEqual(this as Any)) return false
        return this.features == other.features
    }

    private fun canEqual(other: Any): Boolean {
        return other is RequireAll
    }

    override fun hashCode(): Int {
        val PRIME = 59
        var result = 1
        result = result * PRIME + features.hashCode()
        return result
    }

    override fun toString(): String {
        return "RequireAll(features=" + this.features + ")"
    }
}
