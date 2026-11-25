package com.visionit.vcut.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.visionit.vcut.model.EditorProjectState
import com.visionit.vcut.model.SavedDraftSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProjectRepository(context: Context) {
    private val prefs = context.getSharedPreferences("visioncut_drafts_v1", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val keyDrafts = "saved_drafts_list"

    fun loadDrafts(): List<SavedDraftSummary> {
        val raw = prefs.getString(keyDrafts, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SavedDraftSummary>>() {}.type
            gson.fromJson(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveProject(state: EditorProjectState) {
        if (state.clips.isEmpty()) return
        val current = loadDrafts().toMutableList()
        val dateLabel = SimpleDateFormat("MMdd-HH", Locale.US).format(Date())
        val summary = SavedDraftSummary(
            projectId = state.projectId,
            title = state.title,
            dateLabel = dateLabel,
            thumbnailUri = state.clips.first().uriString,
            durationMs = state.totalDurationMs,
            stateJson = gson.toJson(state)
        )
        current.removeAll { it.projectId == state.projectId }
        current.add(0, summary)
        val trimmed = current.take(15)
        prefs.edit().putString(keyDrafts, gson.toJson(trimmed)).apply()
    }

    fun deleteDraft(projectId: String) {
        val updated = loadDrafts().filterNot { it.projectId == projectId }
        prefs.edit().putString(keyDrafts, gson.toJson(updated)).apply()
    }

    fun parseState(json: String): EditorProjectState? {
        return try {
            gson.fromJson(json, EditorProjectState::class.java)
        } catch (_: Exception) {
            null
        }
    }
}
