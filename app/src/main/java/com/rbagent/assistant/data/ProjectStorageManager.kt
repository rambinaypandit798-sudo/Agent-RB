package com.rbagent.assistant.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * ProjectStorageManager
 * Persistent local store for user projects (grouped chats / workspaces).
 * Backed by SharedPreferences "rb_agent_projects".
 */
class ProjectStorageManager private constructor(context: Context) {

    companion object {
        private const val TAG = "ProjectStorageManager"
        private const val PREFS = "rb_agent_projects"
        private const val K_PROJECTS = "projects_json"

        @Volatile private var INSTANCE: ProjectStorageManager? = null
        fun getInstance(context: Context): ProjectStorageManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProjectStorageManager(context.applicationContext).also { INSTANCE = it }
            }
    }

    data class Project(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val description: String = "",
        val createdAt: Long = System.currentTimeMillis()
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id); put("name", name)
            put("description", description); put("createdAt", createdAt)
        }
        companion object {
            fun fromJson(o: JSONObject): Project = Project(
                id = o.optString("id", UUID.randomUUID().toString()),
                name = o.optString("name", "Untitled"),
                description = o.optString("description", ""),
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun getAll(): List<Project> {
        val raw = prefs.getString(K_PROJECTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<Project>(arr.length())
            for (i in 0 until arr.length()) out.add(Project.fromJson(arr.getJSONObject(i)))
            out.sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            Log.e(TAG, "parse failed — resetting", e)
            prefs.edit().remove(K_PROJECTS).apply(); emptyList()
        }
    }

    @Synchronized
    fun addProject(name: String, description: String): Project? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        val existing = getAll().toMutableList()
        if (existing.any { it.name.equals(clean, ignoreCase = true) }) return null
        val p = Project(name = clean, description = description.trim())
        existing.add(0, p)
        persist(existing)
        return p
    }

    @Synchronized
    fun deleteProject(id: String): Boolean =
        persist(getAll().filterNot { it.id == id })

    @Synchronized
    fun clearAll() { prefs.edit().remove(K_PROJECTS).apply() }

    @Synchronized
    fun count(): Int = getAll().size

    private fun persist(list: List<Project>): Boolean = try {
        val arr = JSONArray(); list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(K_PROJECTS, arr.toString()).apply(); true
    } catch (e: Exception) { Log.e(TAG, "persist failed", e); false }
}
