package dev.rift.launcher.sound.pack

import android.content.Context
import org.json.JSONObject
import java.io.File

/** A pack on the device: [dir] holds its pack.json and audio files. */
data class InstalledPack(val id: String, val name: String, val author: String, val music: Boolean, val dir: File)

/** The packs the user downloaded, kept in the app's own files. Nothing here ships with the app. */
object InstalledPacks {
    private val AUDIO = setOf("wav", "mp3", "ogg", "m4a", "flac", "opus")

    private fun root(context: Context) = File(context.applicationContext.filesDir, "audio_packs")

    fun dir(context: Context, id: String) = File(root(context), id.filter { it.isLetterOrDigit() || it == '-' })

    fun list(context: Context): List<InstalledPack> =
        root(context).listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.endsWith(".tmp") }
            .mapNotNull { read(it) }
            .sortedBy { it.name.lowercase() }

    fun get(context: Context, id: String): InstalledPack? =
        id.takeIf { it.isNotBlank() }?.let { read(dir(context, it)) }

    fun remove(context: Context, id: String) {
        dir(context, id).deleteRecursively()
    }

    private fun read(dir: File): InstalledPack? = runCatching {
        val json = JSONObject(File(dir, "pack.json").readText())
        InstalledPack(
            id = dir.name,
            name = json.optString("name").ifEmpty { dir.name },
            author = json.optString("author"),
            music = json.optBoolean("music"),
            dir = dir,
        )
    }.getOrNull()

    fun hasAudio(dir: File): Boolean = dir.listFiles().orEmpty().any { it.extension.lowercase() in AUDIO }

    /** The audio file called [baseName] in the pack, whatever its extension. */
    fun file(dir: File, baseName: String): File? =
        dir.listFiles().orEmpty().firstOrNull { it.isFile && it.nameWithoutExtension == baseName && it.extension.lowercase() in AUDIO }

    /** The track a music pack plays: Steam's `menu_music`, or else the first audio file. */
    fun musicTrack(dir: File): File? =
        file(dir, "menu_music") ?: dir.listFiles().orEmpty().sortedBy { it.name }.firstOrNull { it.isFile && it.extension.lowercase() in AUDIO }
}
