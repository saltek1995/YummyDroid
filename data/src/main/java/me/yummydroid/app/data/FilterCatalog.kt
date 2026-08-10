package me.yummydroid.app.data

import kotlinx.serialization.Serializable

@Serializable
data class FilterCatalog(
    val genres: List<FilterOption> = emptyList(),
    val types: List<FilterOption> = emptyList(),
    val studios: List<FilterOption> = emptyList(),
    val creators: List<FilterOption> = emptyList(),
) {
    companion object {
        val Empty = FilterCatalog()
    }
}

@Serializable
data class FilterOption(
    val title: String,
    val value: String,
)
