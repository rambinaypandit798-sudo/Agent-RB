package com.rbagent.assistant.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MemoryManager private constructor(context: Context) {

    companion object {
        private const val TAG = "MemoryManager"
        private const val PREFS = "rb_agent_memory"
        private const val KEY_FACTS = "facts_json"
        private const val MAX_FACTS = 200
        @Volatile private var INSTANCE: MemoryManager? = null
        fun getInstance(context: Context): MemoryManager =
            INSTANCE ?: synchronized(this) { INSTANCE ?: MemoryManager(context.applicationContext).also { INSTANCE = it } }
    }

    enum class Source { MANUAL, AUTO_LEARNED, SYSTEM }

    data class MemoryFact(
        val id: String = UUID.randomUUID().toString(),
        val text: String,
        val timestamp: Long = System.currentTimeMillis(),
        val source: Source = Source.MANUAL
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id); put("text", text); put("timestamp", timestamp); put("source", source.name)
        }
        companion object {
            fun fromJson(o: JSONObject): MemoryFact = MemoryFact(
                id = o.optString("id", UUID.randomUUID().toString()),
                text = o.optString("text", ""),
                timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                source = runCatching { Source.valueOf(o.optString("source", "MANUAL")) }.getOrDefault(Source.MANUAL)
            )
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized fun getAll(): List<MemoryFact> {
        val raw = prefs.getString(KEY_FACTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<MemoryFact>(arr.length())
            for (i in 0 until arr.length()) out.add(MemoryFact.fromJson(arr.getJSONObject(i)))
            out.sortedByDescending { it.timestamp }
        } catch (e: Exception) { Log.e(TAG, "parse failed — resetting", e); prefs.edit().remove(KEY_FACTS).apply(); emptyList() }
    }

    @Synchronized fun getFactStrings(): List<String> = getAll().map { it.text }.filter { it.isNotBlank() }
    @Synchronized fun count(): Int = getAll().size

    @Synchronized fun addFact(text: String, source: Source = Source.MANUAL): Boolean {
        val clean = text.trim(); if (clean.length < 2) return false
        val existing = getAll().toMutableList()
        if (existing.any { it.text.lowercase() == clean.lowercase() }) return false
        existing.add(0, MemoryFact(text = clean, source = source))
        val trimmed = if (existing.size > MAX_FACTS) existing.sortedByDescending { it.timestamp }.take(MAX_FACTS) else existing
        return persist(trimmed)
    }

    @Synchronized fun removeFact(id: String): Boolean = persist(getAll().filterNot { it.id == id })

    @Synchronized fun updateFact(id: String, newText: String): Boolean {
        val clean = newText.trim(); if (clean.length < 2) return false
        return persist(getAll().map { if (it.id == id) it.copy(text = clean, timestamp = System.currentTimeMillis()) else it })
    }

    @Synchronized fun clearAll() { prefs.edit().remove(KEY_FACTS).apply() }

    private fun persist(list: List<MemoryFact>): Boolean = try {
        val arr = JSONArray(); list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_FACTS, arr.toString()).apply(); true
    } catch (e: Exception) { Log.e(TAG, "persist failed", e); false }

    @Synchronized fun autoLearnFromUserMessage(message: String) {
        val text = message.trim()
        if (text.length < 6 || text.length > 300) return
        val lower = text.lowercase()
        val triggers = listOf("my name is","i am ","i'm ","call me","i like","i love",
            "i hate","i work","i live","my birthday","remember that","मेरा नाम","मुझे पसंद","मैं रहता","याद रखना")
        if (triggers.none { lower.contains(it) }) return
        if (text.endsWith("?")) return
        addFact(text, Source.AUTO_LEARNED)
    }
}
