package me.yummydroid.app

import android.content.Context
import java.io.File
import me.yummydroid.app.data.readJsonOrNull
import me.yummydroid.app.data.writeJson

class DownloadPlanStorage(context: Context) {
    private val directory = File(context.filesDir, "download_plans")

    fun save(plan: DownloadPlan): String {
        directory.mkdirs()
        planFile(plan.id).writeJson(plan)
        return plan.id
    }

    fun read(id: String): DownloadPlan? {
        val safeId = id.takeIf { it.isNotBlank() } ?: return null
        return planFile(safeId).readJsonOrNull()
    }

    fun delete(id: String) {
        if (id.isBlank()) return
        runCatching { planFile(id).delete() }
    }

    private fun planFile(id: String): File {
        val safeName = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .ifBlank { "plan" }
        return File(directory, "$safeName.json")
    }
}

