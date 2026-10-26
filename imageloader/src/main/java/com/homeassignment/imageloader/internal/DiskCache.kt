package com.homeassignment.imageloader.internal

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/**
 * Stores downloaded files under [directory], named by the SHA-256 of their URL.
 *
 * A file's last-modified time is its download time; files older than [ttlMillis] are treated as
 * missing and deleted on access. When the cache grows past [maxSizeBytes], the oldest files are
 * evicted first.
 */
internal class DiskCache(
    private val directory: File,
    private val maxSizeBytes: Long,
    private val ttlMillis: Long,
    private val clock: Clock,
) {
    private val lock = Any()

    operator fun get(url: String): File? {
        val file = fileFor(url)
        if (!file.isFile) return null
        if (isExpired(file)) {
            file.delete()
            return null
        }
        return file
    }

    /**
     * Lets [write] fill a temporary file, then atomically moves it into the cache, so readers never
     * see a partially written file.
     */
    suspend fun put(url: String, write: suspend (File) -> Unit): File {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Cannot create cache directory $directory")
        }
        val target = fileFor(url)
        val temp = File(directory, "${target.name}.${UUID.randomUUID()}$TEMP_SUFFIX")
        try {
            write(temp)
            temp.setLastModified(clock.now())
            synchronized(lock) {
                if (!temp.renameTo(target)) {
                    target.delete()
                    if (!temp.renameTo(target)) throw IOException("Cannot move $temp to $target")
                }
                trimToSize()
            }
            return target
        } finally {
            temp.delete()
        }
    }

    fun remove(url: String) {
        synchronized(lock) { fileFor(url).delete() }
    }

    fun clear() {
        synchronized(lock) { entries().forEach { it.delete() } }
    }

    private fun trimToSize() {
        val files = entries()
        var size = files.sumOf { it.length() }
        if (size <= maxSizeBytes) return
        for (file in files.sortedBy { it.lastModified() }) {
            val length = file.length()
            if (file.delete()) size -= length
            if (size <= maxSizeBytes) break
        }
    }

    private fun isExpired(file: File): Boolean {
        val age = clock.now() - file.lastModified()
        // A negative age means the device clock moved backwards; don't trust the entry.
        return age < 0 || age >= ttlMillis
    }

    /** Cached files, excluding temporary files still being written. */
    private fun entries(): List<File> =
        directory.listFiles()?.filter { it.isFile && !it.name.endsWith(TEMP_SUFFIX) }.orEmpty()

    private fun fileFor(url: String): File = File(directory, sha256(url))

    private companion object {
        const val TEMP_SUFFIX = ".tmp"

        fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}
