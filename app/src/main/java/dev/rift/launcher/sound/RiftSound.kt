package dev.rift.launcher.sound

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.core.content.edit
import app.gamenative.R

/** Interface sounds. A custom pack is a folder with files named after these ids (ogg, wav, mp3). */
enum class RiftSound(val id: String, val rawRes: Int, val gain: Float = 1f) {
    Move("move", R.raw.rift_ui_move, 0.5f),
    Select("select", R.raw.rift_ui_select),
    Back("back", R.raw.rift_ui_back),
    Tab("tab", R.raw.rift_ui_tab),
    SidebarOpen("open", R.raw.rift_ui_open),
    SidebarClose("close", R.raw.rift_ui_close),
    Launch("launch", R.raw.rift_ui_launch),
}

/**
 * Settings, kept in their own prefs file. Keys for the "Sounds" settings section:
 * [KEY_ENABLED] (boolean), [KEY_VOLUME] (int 0..100), [KEY_PACK_URI] (SAF tree uri, empty = built-in set).
 */
object RiftSoundSettings {
    const val PREFS = "rift_sounds"
    const val KEY_ENABLED = "enabled"
    const val KEY_VOLUME = "volume"
    const val KEY_PACK_URI = "pack_uri"

    // Quiet by default: this is background feedback, not music.
    const val DEFAULT_VOLUME = 35

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, true)
    fun volume(context: Context) = prefs(context).getInt(KEY_VOLUME, DEFAULT_VOLUME)
    fun packUri(context: Context): String = prefs(context).getString(KEY_PACK_URI, "").orEmpty()

    fun setEnabled(context: Context, value: Boolean) = prefs(context).edit { putBoolean(KEY_ENABLED, value) }
    fun setVolume(context: Context, percent: Int) = prefs(context).edit { putInt(KEY_VOLUME, percent.coerceIn(0, 100)) }

    /** Pass null to go back to the built-in set. The caller keeps the persistable read permission. */
    fun setPackUri(context: Context, uri: Uri?) = prefs(context).edit { putString(KEY_PACK_URI, uri?.toString().orEmpty()) }
}

/** Low-latency player on top of [SoundPool]. Reads settings on every play, so changes apply at once. */
object RiftSounds {
    private const val MIN_GAP_MS = 35L

    private var pool: SoundPool? = null
    private val ids = mutableMapOf<RiftSound, Int>()
    private var loadedPack: String? = null
    private var lastPlayed = 0L

    fun play(context: Context, sound: RiftSound) {
        val app = context.applicationContext
        if (!RiftSoundSettings.isEnabled(app)) return
        val volume = RiftSoundSettings.volume(app) / 100f * sound.gain
        if (volume <= 0f) return
        val now = System.currentTimeMillis()
        if (sound == RiftSound.Move && now - lastPlayed < MIN_GAP_MS) return
        lastPlayed = now
        val pack = RiftSoundSettings.packUri(app)
        ensureLoaded(app, pack)
        ids[sound]?.let { pool?.play(it, volume, volume, 1, 0, 1f) }
    }

    @Synchronized
    private fun ensureLoaded(context: Context, pack: String) {
        if (pool != null && loadedPack == pack) return
        pool?.release()
        ids.clear()
        val newPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        val custom = if (pack.isEmpty()) emptyMap() else runCatching { customFiles(context, Uri.parse(pack)) }.getOrDefault(emptyMap())
        for (sound in RiftSound.entries) {
            val id = custom[sound.id]?.let { loadCustom(context, newPool, it) } ?: newPool.load(context, sound.rawRes, 1)
            ids[sound] = id
        }
        pool = newPool
        loadedPack = pack
    }

    /** Maps sound id to the document uri of a file with that name in the picked folder. */
    private fun customFiles(context: Context, tree: Uri): Map<String, Uri> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val found = mutableMapOf<String, Uri>()
        context.contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1).substringBeforeLast('.').lowercase()
                if (RiftSound.entries.any { it.id == name }) {
                    found.putIfAbsent(name, DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)))
                }
            }
        }
        return found
    }

    private fun loadCustom(context: Context, pool: SoundPool, uri: Uri): Int? = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { pool.load(it, 1) }
    }.getOrNull()
}
