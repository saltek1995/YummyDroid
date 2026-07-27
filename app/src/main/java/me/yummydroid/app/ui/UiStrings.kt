package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.yummydroid.app.data.ContentLanguage

internal enum class UiStringKey {
    BrowseCatalog,
    BrowseSchedule,
    BrowseHistory,
    BrowseDownloads,
    DownloadSpeedLimit,
    DownloadSpeedMegabytesPerSecond,
    DownloadSpeedLimitWarning,
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
        UiStringKey.DownloadSpeedLimit -> "Ограничение скорости"
        UiStringKey.DownloadSpeedMegabytesPerSecond -> "МБ/с"
        UiStringKey.DownloadSpeedLimitWarning -> "На скорости 10 МБ/с и выше возможны сбои из-за ограничений плееров"
    }

private val UiStringKey.english: String
    get() = when (this) {
        UiStringKey.BrowseCatalog -> "Catalog"
        UiStringKey.BrowseSchedule -> "Schedule"
        UiStringKey.BrowseHistory -> "History"
        UiStringKey.BrowseDownloads -> "Downloads"
        UiStringKey.DownloadSpeedLimit -> "Speed limit"
        UiStringKey.DownloadSpeedMegabytesPerSecond -> "MB/s"
        UiStringKey.DownloadSpeedLimitWarning -> "At 10 MB/s and higher, player-side limits can cause download failures"
    }

private val UiStringKey.ukrainian: String
    get() = when (this) {
        UiStringKey.BrowseCatalog -> "Каталог"
        UiStringKey.BrowseSchedule -> "Розклад"
        UiStringKey.BrowseHistory -> "Історія"
        UiStringKey.BrowseDownloads -> "Завантаження"
        UiStringKey.DownloadSpeedLimit -> "Обмеження швидкості"
        UiStringKey.DownloadSpeedMegabytesPerSecond -> "МБ/с"
        UiStringKey.DownloadSpeedLimitWarning -> "На швидкості 10 МБ/с і вище можливі збої через обмеження плеєрів"
    }
