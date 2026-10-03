package com.dev.alex.portfolio.data.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Last good answer of every GET, as the raw JSON the server sent, one file per request.
 * Read back when the backend is out of reach (Tailscale off, PC asleep) so the screens
 * still show something, under a "stale since" banner. Raw JSON rather than a database:
 * the screens already decode exactly these bodies, so there is no second schema to keep
 * in step with the API.
 *
 * Lives in app-private storage and is wiped on sign-out.
 */
class ResponseCache(private val dir: File) {

    data class Entry(val body: String, val savedAt: Long)

    suspend fun read(key: String): Entry? = withContext(Dispatchers.IO) {
        val file = fileFor(key)
        if (!file.isFile) return@withContext null
        runCatching {
            val text = file.readText()
            val newline = text.indexOf('\n')
            if (newline <= 0) return@runCatching null
            val savedAt = text.substring(0, newline).toLong()
            Entry(text.substring(newline + 1), savedAt)
        }.getOrNull()
    }

    suspend fun write(key: String, body: String) = withContext(Dispatchers.IO) {
        runCatching {
            dir.mkdirs()
            val target = fileFor(key)
            val temp = File(dir, target.name + ".tmp")
            temp.writeText("${System.currentTimeMillis()}\n$body")
            // rename is atomic on one filesystem: a reader never sees half a file
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
        }
        Unit
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { it.delete() }
        Unit
    }

    private fun fileFor(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        val name = digest.joinToString("") { "%02x".format(it) }
        return File(dir, "$name.json")
    }
}
