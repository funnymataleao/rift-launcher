package dev.rift.launcher.assistant.chat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * The assistant's earlier conversations, on the device only. The text of each message is kept; attached
 * screenshots and proposals are not (a proposal is applied while its answer is on the page).
 */
internal object ChatHistoryStore {

    const val MAX = 30
    private const val PREFS = "rift_assistant_history"
    private const val KEY = "chats"

    fun load(context: Context): List<SavedChat> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        val array = JSONArray(raw)
        List(array.length()) { i ->
            val chat = array.getJSONObject(i)
            val game = chat.optJSONObject("game")?.let { ChatGame(it.getString("appId"), it.getString("name")) }
            val entries = chat.getJSONArray("entries").let { list ->
                List(list.length()) { j ->
                    val e = list.getJSONObject(j)
                    ChatEntry(
                        id = e.getLong("id"),
                        fromUser = e.getBoolean("fromUser"),
                        text = e.getString("text"),
                        wire = e.optString("wire", e.getString("text")),
                        model = e.optString("model").takeIf { it.isNotEmpty() },
                    )
                }
            }
            SavedChat(chat.getLong("id"), chat.getString("title"), game, entries)
        }
    }.onFailure { Timber.w(it, "Assistant history: unreadable, starting empty") }.getOrDefault(emptyList())

    fun save(context: Context, chats: List<SavedChat>) {
        val array = JSONArray()
        chats.take(MAX).forEach { chat ->
            array.put(
                JSONObject()
                    .put("id", chat.id)
                    .put("title", chat.title)
                    .put("game", chat.game?.let { JSONObject().put("appId", it.appId).put("name", it.name) })
                    .put(
                        "entries",
                        JSONArray().apply {
                            chat.entries.forEach { e ->
                                put(
                                    JSONObject()
                                        .put("id", e.id)
                                        .put("fromUser", e.fromUser)
                                        .put("text", e.text)
                                        .put("wire", e.wire)
                                        .put("model", e.model ?: ""),
                                )
                            }
                        },
                    ),
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
