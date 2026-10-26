package com.smassistant.app.scheduler

import android.content.Context

/**
 * Saved schedulers (SC-8), as one JSON list in SharedPreferences. Not secret; excluded from
 * backup with the rest of the app data. The screens and the background worker share it, so every
 * change is read, modified and written under one lock.
 */
class SchedulerStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("schedulers", Context.MODE_PRIVATE)

    fun all(): List<AutoPostScheduler> = synchronized(LOCK) { SchedulerJson.decode(prefs.getString(KEY_LIST, null)) }

    fun get(id: String): AutoPostScheduler? = all().firstOrNull { it.id == id }

    /** Adds [scheduler], or replaces the saved one with the same id. */
    fun save(scheduler: AutoPostScheduler) = edit { list ->
        if (list.any { it.id == scheduler.id }) list.map { if (it.id == scheduler.id) scheduler else it } else list + scheduler
    }

    fun delete(id: String) = edit { list -> list.filterNot { it.id == id } }

    /** Changes the saved scheduler [id] with [change]; returns the result, or null when it was deleted meanwhile. */
    fun update(id: String, change: (AutoPostScheduler) -> AutoPostScheduler): AutoPostScheduler? {
        var updated: AutoPostScheduler? = null
        edit { list ->
            list.map {
                if (it.id == id) change(it).also { new -> updated = new } else it
            }
        }
        return updated
    }

    private fun edit(change: (List<AutoPostScheduler>) -> List<AutoPostScheduler>) {
        synchronized(LOCK) {
            val list = SchedulerJson.decode(prefs.getString(KEY_LIST, null))
            prefs.edit().putString(KEY_LIST, SchedulerJson.encode(change(list))).commit()
        }
    }

    private companion object {
        const val KEY_LIST = "list"
        val LOCK = Any()
    }
}
