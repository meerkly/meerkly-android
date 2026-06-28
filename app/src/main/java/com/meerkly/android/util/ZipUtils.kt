package com.meerkly.android.util

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipUtils {
    /** Zips each (entryPath -> file) pair into [dest], skipping non-files. */
    fun zip(entries: List<Pair<String, File>>, dest: File) {
        dest.parentFile?.mkdirs()
        ZipOutputStream(dest.outputStream().buffered()).use { zos ->
            for ((path, file) in entries) {
                if (!file.isFile) continue
                zos.putNextEntry(ZipEntry(path))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }
}
