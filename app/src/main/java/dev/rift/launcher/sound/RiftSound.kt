package dev.rift.launcher.sound

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.core.content.edit
import dev.rift.launcher.sound.pack.InstalledPacks
import java.io.File
import app.gamenative.R

/**
 * Interface sounds. A custom folder holds files named after these ids (ogg, wav, mp3); a DeckThemes pack holds
 * files named after Steam Deck's sounds, [deckNames] says which of them stand in for each one.
 */
enum class RiftSound(val id: String, val rawRes: Int, val deckNames: List<String>, val gain: Float = 1f) {
    Move("move", R.raw.rift_ui_move, listOf("deck_ui_navigation"), 0.6f),
    Select("select", R.raw.rift_ui_select, listOf("deck_ui_default_activation", "deck_ui_into_game_detail")),
    Back("back", R.raw.rift_ui_back, listOf("deck_ui_out_of_game_detail", "deck_ui_hide_modal")),
    Tab("tab", R.raw.rift_ui_tab, listOf("deck_ui_tab_transition_01")),
    SidebarOpen("open", R.raw.rift_ui_open, listOf("deck_ui_side_menu_fly_in")),
    SidebarClose("close", R.raw.rift_ui_close, listOf("deck_ui_side_menu_fly_out")),
    Launch("launch", R.raw.rift_ui_launch, listOf("deck_ui_launch_game")),
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
    const val KEY_SOUND_PACK_ID = "sound_pack_id"
    const val KEY_MUSIC_PACK_ID = "music_pack_id"
    const val KEY_MUSIC_ENABLED = "music_enabled"
    const val KEY_MUSIC_VOLUME = "music_volume"
    const val DEFAULT_MUSIC_VOLUME = 40

    // Soft sounds, but audible on a stock phone: the synthesized tones are short and peak at 80%.
    const val DEFAULT_VOLUME = 60

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, true)
    fun volume(context: Context) = prefs(context).getInt(KEY_VOLUME, DEFAULT_VOLUME)
    fun packUri(context: Context): String = prefs(context).getString(KEY_PACK_URI, "").orEmpty()

    /** Id of the downloaded DeckThemes sound pack in use, empty when none. It wins over [packUri]. */
    fun soundPackId(context: Context): String = prefs(context).getString(KEY_SOUND_PACK_ID, "").orEmpty()
    fun musicPackId(context: Context): String = prefs(context).getString(KEY_MUSIC_PACK_ID, "").orEmpty()
    fun isMusicEnabled(context: Context) = prefs(context).getBoolean(KEY_MUSIC_ENABLED, true)
    fun musicVolume(context: Context) = prefs(context).getInt(KEY_MUSIC_VOLUME, DEFAULT_MUSIC_VOLUME)

    fun setSoundPackId(context: Context, id: String) = prefs(context).edit { putString(KEY_SOUND_PACK_ID, id) }
    fun setMusicPackId(context: Context, id: String) = prefs(context).edit { putString(KEY_MUSIC_PACK_ID, id) }
    fun setMusicEnabled(context: Context, value: Boolean) = prefs(context).edit { putBoolean(KEY_MUSIC_ENABLED, value) }
    fun setMusicVolume(context: Context, percent: Int) = prefs(context).edit { putInt(KEY_MUSIC_VOLUME, percent.coerceIn(0, 100)) }

    fun setEnabled(context: Context, value: Boolean) = prefs(context).edit { putBoolean(KEY_ENABLED, value) }
    fun setVolume(context: Context, percent: Int) = prefs(context).edit { putInt(KEY_VOLUME, percent.coerceIn(0, 100)) }

    /** Pass null to go back to the built-in set. The caller keeps the persistable read permission. */
    fun setPackUri(context: Context, uri: Uri?) = prefs(context).edit { putString(KEY_PACK_URI, uri?.toString().orEmpty()) }
}

/** Low-latency player on top of [SoundPool]. Reads settings on every play, so changes apply at once. */
object RiftSounds {
    private const val MIN_GAP_MS = 35L

    /**
     * Set while a control that makes sound of its own has the focus (a pack's play button), so A/Enter and a tap on
     * it stay silent instead of mixing the interface sound into what is being played.
     */
    @Volatile
    var pressMuted = false

    /** Set by such a control when a finger goes down on it; the screen's tap sound reads and clears it. */
    @Volatile
    var tapMuted = false

    private var pool: SoundPool? = null
    private val ids = mutableMapOf<RiftSound, Int>()
    private var loadedPack: String? = null
    private var lastPlayed = 0L

    /** Loads the set ahead of the first press: SoundPool decodes asynchronously and drops sounds played before that. */
    fun preload(context: Context) {
        val app = context.applicationContext
        ensureLoaded(app)
    }

    fun play(context: Context, sound: RiftSound) {
        val app = context.applicationContext
        if (!RiftSoundSettings.isEnabled(app)) return
        val volume = RiftSoundSettings.volume(app) / 100f * sound.gain
        if (volume <= 0f) return
        val now = System.currentTimeMillis()
        if (sound == RiftSound.Move && now - lastPlayed < MIN_GAP_MS) return
        lastPlayed = now
        ensureLoaded(app)
        ids[sound]?.let { pool?.play(it, volume, volume, 1, 0, 1f) }
    }

    /** Reloads the set when the chosen pack changed: a downloaded pack wins over a picked folder, which wins over the built-in set. */
    @Synchronized
    private fun ensureLoaded(context: Context) {
        val packId = RiftSoundSettings.soundPackId(context)
        val folder = RiftSoundSettings.packUri(context)
        val key = "$packId|$folder"
        if (pool != null && loadedPack == key) return
        pool?.release()
        ids.clear()
        val newPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        val downloaded = InstalledPacks.get(context, packId)?.takeUnless { it.music }?.dir
        val custom = if (folder.isEmpty()) emptyMap() else runCatching { customFiles(context, Uri.parse(folder)) }.getOrDefault(emptyMap())
        for (sound in RiftSound.entries) {
            val id = downloaded?.let { dir -> loadDeck(newPool, dir, sound) }
                ?: custom[sound.id]?.let { loadCustom(context, newPool, it) }
                ?: newPool.load(context, sound.rawRes, 1)
            ids[sound] = id
        }
        pool = newPool
        loadedPack = key
    }

    private fun loadDeck(pool: SoundPool, dir: File, sound: RiftSound): Int? =
        sound.deckNames.firstNotNullOfOrNull { InstalledPacks.file(dir, it) }?.let { pool.load(it.path, 1) }

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
