package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.yummydroid.app.data.ContentLanguage

internal enum class UiStringKey {
    BrowseCatalog,
    BrowseSchedule,
    BrowseHistory,
    BrowseDownloads,
}

@Composable
internal fun uiText(key: UiStringKey): String {
    val language = LocalUiLanguage.current
    return remember(language, key) {
        when (language) {
            ContentLanguage.Russian -> key.russian
            ContentLanguage.English -> key.english
            ContentLanguage.Ukrainian -> key.ukrainian
        }
    }
}

private val UiStringKey.russian: String
    get() = when (this) {
        UiStringKey.BrowseCatalog -> "Каталог"
        UiStringKey.BrowseSchedule -> "Расписание"
        UiStringKey.BrowseHistory -> "История"
        UiStringKey.BrowseDownloads -> "Загрузки"
    }

private val UiStringKey.english: String
    get() = when (this) {
        UiStringKey.BrowseCatalog -> "Catalog"
        UiStringKey.BrowseSchedule -> "Schedule"
        UiStringKey.BrowseHistory -> "History"
        UiStringKey.BrowseDownloads -> "Downloads"
    }

private val UiStringKey.ukrainian: String
    get() = when (this) {
        UiStringKey.BrowseCatalog -> "Каталог"
        UiStringKey.BrowseSchedule -> "Розклад"
        UiStringKey.BrowseHistory -> "Історія"
        UiStringKey.BrowseDownloads -> "Завантаження"
    }
