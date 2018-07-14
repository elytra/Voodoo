// Generated by delombok at Sat Jul 14 01:46:55 CEST 2018
/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */
package com.skcraft.launcher.builder

import java.io.File
import java.io.IOException

/**
 * Abstract class to recursively walk a directory, keep track of a relative
 * path (which may be modified by dropping certain directory entries),
 * and call [.onFile] with each file.
 */
abstract class DirectoryWalker {

    enum class DirectoryBehavior {
        /**
         * Continue and add the given directory to the relative path.
         */
        CONTINUE,
        /**
         * Continue but don't add the given directory to the relative path.
         */
        IGNORE,
        /**
         * Don't walk this directory.
         */
        SKIP
    }

    /**
     * Recursively walk the given directory and keep track of the relative path.
     *
     * @param dir the directory
     * @param basePath the base path
     * @throws IOException
     */
    @Throws(IOException::class)
    fun walk(dir: File, basePath: String = "") {
        if (!dir.isDirectory) {
            throw IllegalArgumentException(dir.absolutePath + " is not a directory")
        }
        val files = dir.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory) {
                    var newPath = basePath
                    when (getBehavior(file.name)) {
                        DirectoryWalker.DirectoryBehavior.CONTINUE -> {
                            newPath += file.name + "/"
                            walk(file, newPath)
                        }

                        DirectoryWalker.DirectoryBehavior.IGNORE -> walk(file, newPath)

                        DirectoryWalker.DirectoryBehavior.SKIP -> {
                        }
                    }
                } else {
                    onFile(file, basePath + file.name)
                }
            }
        }
    }

    /**
     * Return the behavior for the given directory name.
     *
     * @param name the directory name
     * @return the behavor
     */
    protected open fun getBehavior(name: String): DirectoryBehavior {
        return DirectoryBehavior.CONTINUE
    }

    /**
     * Callback on each file.
     *
     * @param file the file
     * @param relPath the relative path
     * @throws IOException thrown on I/O error
     */
    @Throws(IOException::class)
    protected abstract fun onFile(file: File, relPath: String)
}
