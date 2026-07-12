package me.yummydroid.app.ui

import android.app.Activity
import android.content.res.ColorStateList
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.R as Media3R
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TimeBar
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.Collator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import me.yummydroid.app.AppLog
import me.yummydroid.app.AppRoute
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.BuildConfig
import me.yummydroid.app.HCaptchaActivity
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.DownloadTaskState
import me.yummydroid.app.formatByteSize
import me.yummydroid.app.PagingUiState
import me.yummydroid.app.PipPlayerHandle
import me.yummydroid.app.PlayerPipController
import me.yummydroid.app.R
import me.yummydroid.app.UpdateDownloadService
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.AnimeComment
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AnimeGenreFilter
import me.yummydroid.app.data.AnimeRatingSummary
import me.yummydroid.app.data.AnimeSort
import me.yummydroid.app.data.AnimeStatusFilter
import me.yummydroid.app.data.AnimeTrailer
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.ContentLanguage
import me.yummydroid.app.data.FilterCatalog
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PlayerDecoderMode
import me.yummydroid.app.data.PlayerSpeed
import me.yummydroid.app.data.PosterCardSize
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.RelatedAnime
import me.yummydroid.app.data.RatingDetails
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.ScheduleAnime
import me.yummydroid.app.data.SiteDomainResolver
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.UserProfile
import me.yummydroid.app.data.VideoSkipSegment
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.downloadedEpisodeCountForVoice
import me.yummydroid.app.data.isSubscribedTo
import me.yummydroid.app.data.matchingVoiceTitle
import me.yummydroid.app.data.ageRatingFilterOptions
import me.yummydroid.app.data.qualityHeight
import me.yummydroid.app.data.seasonFilterOptions
import me.yummydroid.app.data.statusFilterOptions
import me.yummydroid.app.data.translateFilterOptions
import me.yummydroid.app.data.userMarkFilterOptions
import me.yummydroid.app.data.normalizeSiteBaseUrl
import me.yummydroid.app.data.normalizedSiteBaseUrls
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.components.focusRing
import okhttp3.OkHttpClient

private val LocalUiLanguage = staticCompositionLocalOf { ContentLanguage.Russian }

@Composable
private fun uiText(ru: String): String {
    val language = LocalUiLanguage.current
    return remember(language, ru) { translateUiText(ru, language) }
}

private fun translateUiText(ru: String, language: ContentLanguage): String {
    if (language == ContentLanguage.Russian) return ru
    val dictionary = when (language) {
        ContentLanguage.English -> englishUiDictionary
        ContentLanguage.Ukrainian -> ukrainianUiDictionary
        ContentLanguage.Russian -> emptyMap()
    }
    return dictionary[ru] ?: ru
}

@Composable
private fun localizedPluralWord(
    count: Long,
    oneRu: String,
    fewRu: String,
    manyRu: String,
    englishOne: String,
    englishMany: String,
): String {
    if (LocalUiLanguage.current == ContentLanguage.English) {
        return if (kotlin.math.abs(count) == 1L) englishOne else englishMany
    }
    val normalized = kotlin.math.abs(count)
    val mod100 = normalized % 100
    val mod10 = normalized % 10
    val key = when {
        mod100 in 11..14 -> manyRu
        mod10 == 1L -> oneRu
        mod10 in 2L..4L -> fewRu
        else -> manyRu
    }
    return uiText(key)
}

@Composable
private fun localizedEpisodesWord(count: Int): String {
    return localizedPluralWord(
        count = count.toLong(),
        oneRu = "серия",
        fewRu = "серии",
        manyRu = "серий",
        englishOne = "episode",
        englishMany = "episodes",
    )
}

@Composable
private fun localizedVotesWord(count: Long): String {
    return localizedPluralWord(
        count = count,
        oneRu = "голос",
        fewRu = "голоса",
        manyRu = "голосов",
        englishOne = "vote",
        englishMany = "votes",
    )
}

@Composable
private fun BrowseSection.localizedTitle(): String = uiText(
    when (this) {
        BrowseSection.Catalog -> "Каталог"
        BrowseSection.Schedule -> "Расписание"
        BrowseSection.Downloads -> "Загрузки"
    },
)

@Composable
private fun PreferredQuality.localizedTitle(): String = when (this) {
    PreferredQuality.Auto -> uiText("Авто")
    else -> title
}

@Composable
private fun PlayerDecoderMode.localizedTitle(): String = when (this) {
    PlayerDecoderMode.Auto -> uiText("Авто")
    PlayerDecoderMode.Hardware -> uiText("Аппаратный")
    PlayerDecoderMode.Software -> uiText("Программный")
}

@Composable
private fun PosterCardSize.localizedTitle(): String = when (this) {
    PosterCardSize.Compact -> uiText("Компактные")
    PosterCardSize.Standard -> uiText("Стандартные")
    PosterCardSize.Large -> uiText("Крупные")
}

private fun PosterCardSize.resolveCatalogColumns(screenWidthDp: Int): Int {
    return when {
        screenWidthDp >= 1200 -> when (this) {
            PosterCardSize.Compact -> 6
            PosterCardSize.Standard -> 5
            PosterCardSize.Large -> 4
        }
        screenWidthDp >= 900 -> when (this) {
            PosterCardSize.Compact -> 5
            PosterCardSize.Standard -> 4
            PosterCardSize.Large -> 3
        }
        screenWidthDp >= 600 -> when (this) {
            PosterCardSize.Compact -> 4
            PosterCardSize.Standard -> 3
            PosterCardSize.Large -> 2
        }
        screenWidthDp >= 430 -> when (this) {
            PosterCardSize.Compact -> 3
            PosterCardSize.Standard -> 2
            PosterCardSize.Large -> 1
        }
        else -> when (this) {
            PosterCardSize.Compact -> 2
            PosterCardSize.Standard -> 2
            PosterCardSize.Large -> 1
        }
    }
}

@Composable
private fun ContentLanguage.localizedTitle(): String = when (this) {
    ContentLanguage.Russian -> "Русский"
    ContentLanguage.English -> "English"
    ContentLanguage.Ukrainian -> "Українська"
}

@Composable
private fun AnimeSort.localizedTitle(): String = uiText(
    when (this) {
        AnimeSort.Rating -> "Рейтинг"
        AnimeSort.RatingCounters -> "Оценок"
        AnimeSort.Views -> "Просмотры"
        AnimeSort.Year -> "Новые"
        AnimeSort.Top -> "Топ"
        AnimeSort.Title -> "А-Я"
        AnimeSort.Id -> "По добавлению"
        AnimeSort.Random -> "Случайно"
    },
)

@Composable
private fun FilterOption.localizedTitle(): String = when (value) {
    "released" -> uiText("Вышло")
    "ongoing" -> uiText("Онгоинг")
    "announcement" -> uiText("Анонсы")
    "winter" -> uiText("Зима")
    "spring" -> uiText("Весна")
    "summer" -> uiText("Лето")
    "fall" -> uiText("Осень")
    "dubbing" -> uiText("Полное дублирование")
    "multivoice" -> uiText("Многоголосый")
    "duet" -> uiText("Двухголосый")
    "onevoice" -> uiText("Одноголосый")
    "subtitles" -> uiText("Субтитры")
    "0" -> uiText("Смотрю")
    "1" -> uiText("В планах")
    "2" -> uiText("Просмотрено")
    "3" -> uiText("Брошено")
    "4" -> uiText("Любимые")
    "5" -> uiText("Отложено")
    else -> title
}

private fun ContentLanguage.voiceRecognizerTag(): String = when (this) {
    ContentLanguage.Russian -> "ru-RU"
    ContentLanguage.English -> "en-US"
    ContentLanguage.Ukrainian -> "uk-UA"
}

private val englishUiDictionary = mapOf(
    "Поиск" to "Search",
    "Найти аниме" to "Find anime",
    "Голосовой поиск" to "Voice search",
    "Голосовой поиск недоступен на этом устройстве" to "Voice search is not available on this device",
    "Что найти?" to "What should I find?",
    "Готово" to "Done",
    "Фильтры" to "Filters",
    "Сбросить" to "Reset",
    "Отмена" to "Cancel",
    "Применить" to "Apply",
    "Вход" to "Sign in",
    "Email" to "Email",
    "Пароль" to "Password",
    "Регистрация" to "Sign up",
    "Забыли пароль?" to "Forgot password?",
    "Войти" to "Sign in",
    "Аккаунт" to "Account",
    "Профиль" to "Profile",
    "ЛК" to "Profile",
    "Подписки" to "Subscriptions",
    "Подписок нет" to "No subscriptions",
    "Отключить" to "Disable",
    "Аниме" to "Anime",
    "Выйти" to "Sign out",
    "Закрыть" to "Close",
    "Настройки" to "Settings",
    "Хранилище" to "Storage",
    "Скачанные серии" to "Downloaded episodes",
    "Очистить кэш" to "Clear cache",
    "Видео, карточки и прогресс" to "Videos, cards and progress",
    "Потоки загрузки" to "Download threads",
    "Скачивать через мобильный интернет" to "Download over mobile data",
    "Воспроизведение" to "Playback",
    "Качество по умолчанию" to "Default quality",
    "Декодер" to "Decoder",
    "Автовоспроизведение следующей серии" to "Autoplay next episode",
    "Каталог и оформление" to "Catalog and appearance",
    "Размер карточек" to "Card size",
    "Язык контента" to "App and content language",
    "Автоматические метки" to "Automatic marks",
    "Ставить «Смотрю» при воспроизведении" to "Mark as watching on playback",
    "Ставить «Просмотрено» после последней серии" to "Mark as watched after final episode",
    "Сеть" to "Network",
    "Уведомления приложения" to "App notifications",
    "Домены сайта" to "Site domains",
    "Проверять обновления при запуске" to "Check updates on startup",
    "О программе" to "About",
    "Версия" to "Version",
    "Проверить" to "Check",
    "Качество" to "Quality",
    "Скачать" to "Download",
    "Скачать всё" to "Download all",
    "Скачать все серии" to "Download all episodes",
    "Смотреть" to "Watch",
    "Продолжить" to "Continue",
    "Подписка" to "Subscription",
    "Подписан" to "Subscribed",
    "Озвучка" to "Voice",
    "Просмотр" to "Watch",
    "Похожие" to "Similar",
    "Комментарии" to "Comments",
    "Комментарий" to "Comment",
    "Трейлеры" to "Trailers",
    "Трейлер" to "Trailer",
    "Описание" to "Description",
    "Описание пока не добавлено." to "No description yet.",
    "Оценка" to "Rating",
    "Повторить" to "Retry",
    "Обновить" to "Refresh",
    "Назад" to "Back",
    "Каталог" to "Catalog",
    "Расписание" to "Schedule",
    "Загрузки" to "Downloads",
    "Очередь загрузок" to "Download queue",
    "Очистить" to "Clear",
    "Пусто" to "Empty",
    "Загрузка" to "Loading",
    "Ошибка" to "Error",
    "Скачано" to "Downloaded",
    "Пауза" to "Paused",
    "Добавлено" to "Added",
    "Авто" to "Auto",
    "Аппаратный" to "Hardware",
    "Программный" to "Software",
    "Компактные" to "Compact",
    "Стандартные" to "Standard",
    "Крупные" to "Large",
    "Оффлайн" to "Offline",
    "Ничего не найдено" to "Nothing found",
    "Каталог пуст" to "Catalog is empty",
    "Расписание пока пустое" to "Schedule is empty",
    "По выбранным фильтрам ничего не найдено" to "No items match the selected filters",
    "Ближайших выходов пока нет" to "No upcoming releases yet",
    "Прошедшие скрыты" to "Past releases hidden",
    "Прошедшие показаны" to "Past releases shown",
    "Доступно офлайн" to "Available offline",
    "В очереди" to "Queued",
    "Отменено" to "Cancelled",
    "Язык приложения и контента" to "App and content language",
    "Скачанных серий пока нет" to "No downloaded episodes yet",
    "скрыто" to "hidden",
    "прошедших" to "past",
    "серий" to "episodes",
    "доменов" to "domains",
    "Будут удалены скачанные серии, кэш карточек аниме и локальный прогресс просмотра. Аккаунт и авторизация останутся." to
        "Downloaded episodes, cached anime cards and local playback progress will be deleted. Account and authorization will remain.",
    "Вы не авторизованы." to "You are not signed in.",
    "Пользователь" to "User",
    "Аккаунт заблокирован на сайте." to "The account is blocked on the site.",
    "О себе" to "About",
    "Роли" to "Roles",
    "Нет" to "No",
    "Уведомления" to "Notifications",
    "Сообщения" to "Messages",
    "Библиотека" to "Library",
    "Не удалось открыть сайт" to "Could not open the site",
    "Сортировка" to "Sorting",
    "Статус" to "Status",
    "Жанры" to "Genres",
    "Исключить жанры" to "Exclude genres",
    "Тип" to "Type",
    "Год" to "Year",
    "Сезон" to "Season",
    "Ограничение" to "Restriction",
    "Рейтинг" to "Rating",
    "Серии" to "Episodes",
    "Метки" to "Marks",
    "От" to "From",
    "До" to "To",
    "Вышло" to "Released",
    "Онгоинг" to "Ongoing",
    "Анонсы" to "Announcements",
    "Зима" to "Winter",
    "Весна" to "Spring",
    "Лето" to "Summer",
    "Осень" to "Fall",
    "Полное дублирование" to "Full dubbing",
    "Многоголосый" to "Multi-voice",
    "Двухголосый" to "Two-voice",
    "Одноголосый" to "Single-voice",
    "Субтитры" to "Subtitles",
    "Смотрю" to "Watching",
    "В планах" to "Planned",
    "Просмотрено" to "Watched",
    "Брошено" to "Dropped",
    "Любимые" to "Favorites",
    "Отложено" to "Postponed",
    "Оценок" to "Votes",
    "Просмотры" to "Views",
    "Новые" to "New",
    "Топ" to "Top",
    "А-Я" to "A-Z",
    "По добавлению" to "Recently added",
    "Случайно" to "Random",
    "Оффлайн: фильтруются только скачанные аниме" to "Offline: only downloaded anime are shown",
    "Все" to "All",
    "от" to "from",
    "до" to "to",
    "выбрано" to "selected",
    "Удалить аниме" to "Delete anime",
    "Удалить серию" to "Delete episode",
    "Обновления" to "Updates",
    "Проверка еще не выполнена." to "The update check has not been run yet.",
    "Описание версии пока не добавлено." to "No release notes yet.",
    "Выбери озвучку" to "Choose voice",
    "Далее" to "Next",
    "Удалить домен" to "Remove domain",
    "Новый домен" to "New domain",
    "Добавить" to "Add",
    "Некорректный домен" to "Invalid domain",
    "Домен уже в списке" to "Domain is already in the list",
    "Карточка не найдена" to "Anime card not found",
    "Войти в аккаунт" to "Sign in to account",
    "Синхронизация" to "Syncing",
    "Жанр" to "Genre",
    "Год выхода" to "Year",
    "Альт. названия" to "Alt. titles",
    "Первоисточник" to "Source",
    "Студия" to "Studio",
    "Режиссёр" to "Director",
    "До выхода" to "Until release",
    "Длительность" to "Duration",
    "В списках" to "In lists",
    "Кадры" to "Screenshots",
    "Порядок просмотра" to "Watch order",
    "Загружено" to "Downloaded",
    "серия" to "episode",
    "серии" to "episodes",
    "голос" to "vote",
    "голоса" to "votes",
    "голосов" to "votes",
    "Пока нет оценок" to "No ratings yet",
    "загружено" to "loaded",
    "Отправить" to "Send",
    "Видео для этого аниме пока нет" to "No videos for this anime yet",
    "Скачать серию" to "Download episode",
    "Удалить скачанную серию" to "Delete downloaded episode",
    "Удалить" to "Delete",
    "Все скачанные варианты" to "All downloaded variants",
    "Предыдущая серия" to "Previous episode",
    "Следующая серия" to "Next episode",
    "Еще раз" to "Try again",
    "офлайн" to "offline",
    "локально" to "local",
    "скачано" to "downloaded",
    "Пропустить" to "Skip",
    "Следующая" to "Next",
    "из" to "of",
    "Войдите в аккаунт, чтобы открыть библиотеку" to "Sign in to open the library",
    "Библиотека пока пустая" to "The library is empty",
    "Поставить на паузу" to "Pause",
    "Возобновить загрузку" to "Resume download",
    "Отменить загрузку" to "Cancel download",
    "неизвестно" to "unknown",
    "с" to "s",
)

private val ukrainianUiDictionary = mapOf(
    "Поиск" to "Пошук",
    "Найти аниме" to "Знайти аніме",
    "Голосовой поиск" to "Голосовий пошук",
    "Голосовой поиск недоступен на этом устройстве" to "Голосовий пошук недоступний на цьому пристрої",
    "Что найти?" to "Що знайти?",
    "Готово" to "Готово",
    "Фильтры" to "Фільтри",
    "Сбросить" to "Скинути",
    "Отмена" to "Скасувати",
    "Применить" to "Застосувати",
    "Вход" to "Вхід",
    "Email" to "Email",
    "Пароль" to "Пароль",
    "Регистрация" to "Реєстрація",
    "Забыли пароль?" to "Забули пароль?",
    "Войти" to "Увійти",
    "Аккаунт" to "Акаунт",
    "Профиль" to "Профіль",
    "ЛК" to "Профіль",
    "Подписки" to "Підписки",
    "Подписок нет" to "Підписок немає",
    "Отключить" to "Вимкнути",
    "Аниме" to "Аніме",
    "Выйти" to "Вийти",
    "Закрыть" to "Закрити",
    "Настройки" to "Налаштування",
    "Хранилище" to "Сховище",
    "Скачанные серии" to "Завантажені серії",
    "Очистить кэш" to "Очистити кеш",
    "Видео, карточки и прогресс" to "Відео, картки та прогрес",
    "Потоки загрузки" to "Потоки завантаження",
    "Скачивать через мобильный интернет" to "Завантажувати через мобільний інтернет",
    "Воспроизведение" to "Відтворення",
    "Качество по умолчанию" to "Якість за замовчуванням",
    "Декодер" to "Декодер",
    "Автовоспроизведение следующей серии" to "Автовідтворення наступної серії",
    "Каталог и оформление" to "Каталог і вигляд",
    "Размер карточек" to "Розмір карток",
    "Язык контента" to "Мова застосунку й контенту",
    "Автоматические метки" to "Автоматичні мітки",
    "Ставить «Смотрю» при воспроизведении" to "Ставити «Дивлюся» під час відтворення",
    "Ставить «Просмотрено» после последней серии" to "Ставити «Переглянуто» після останньої серії",
    "Сеть" to "Мережа",
    "Уведомления приложения" to "Сповіщення застосунку",
    "Домены сайта" to "Домени сайту",
    "Проверять обновления при запуске" to "Перевіряти оновлення під час запуску",
    "О программе" to "Про застосунок",
    "Версия" to "Версія",
    "Проверить" to "Перевірити",
    "Качество" to "Якість",
    "Скачать" to "Завантажити",
    "Скачать всё" to "Завантажити все",
    "Скачать все серии" to "Завантажити всі серії",
    "Смотреть" to "Дивитися",
    "Продолжить" to "Продовжити",
    "Подписка" to "Підписка",
    "Подписан" to "Підписано",
    "Озвучка" to "Озвучення",
    "Просмотр" to "Перегляд",
    "Похожие" to "Схожі",
    "Комментарии" to "Коментарі",
    "Комментарий" to "Коментар",
    "Трейлеры" to "Трейлери",
    "Трейлер" to "Трейлер",
    "Описание" to "Опис",
    "Описание пока не добавлено." to "Опис поки не додано.",
    "Оценка" to "Оцінка",
    "Повторить" to "Повторити",
    "Обновить" to "Оновити",
    "Назад" to "Назад",
    "Каталог" to "Каталог",
    "Расписание" to "Розклад",
    "Загрузки" to "Завантаження",
    "Очередь загрузок" to "Черга завантажень",
    "Очистить" to "Очистити",
    "Пусто" to "Порожньо",
    "Загрузка" to "Завантаження",
    "Ошибка" to "Помилка",
    "Скачано" to "Завантажено",
    "Пауза" to "Пауза",
    "Авто" to "Авто",
    "Аппаратный" to "Апаратний",
    "Программный" to "Програмний",
    "Компактные" to "Компактні",
    "Стандартные" to "Стандартні",
    "Крупные" to "Великі",
    "Оффлайн" to "Офлайн",
    "Ничего не найдено" to "Нічого не знайдено",
    "Каталог пуст" to "Каталог порожній",
    "Расписание пока пустое" to "Розклад поки порожній",
    "По выбранным фильтрам ничего не найдено" to "За вибраними фільтрами нічого не знайдено",
    "Ближайших выходов пока нет" to "Найближчих виходів поки немає",
    "Прошедшие скрыты" to "Минулі приховано",
    "Прошедшие показаны" to "Минулі показано",
    "Доступно офлайн" to "Доступно офлайн",
    "В очереди" to "У черзі",
    "Отменено" to "Скасовано",
    "Язык приложения и контента" to "Мова застосунку й контенту",
    "Скачанных серий пока нет" to "Завантажених серій поки немає",
    "скрыто" to "приховано",
    "прошедших" to "минулих",
    "серий" to "серій",
    "доменов" to "доменів",
    "Будут удалены скачанные серии, кэш карточек аниме и локальный прогресс просмотра. Аккаунт и авторизация останутся." to
        "Буде видалено завантажені серії, кеш карток аніме та локальний прогрес перегляду. Акаунт і авторизація залишаться.",
    "Вы не авторизованы." to "Ви не авторизовані.",
    "Пользователь" to "Користувач",
    "Аккаунт заблокирован на сайте." to "Акаунт заблоковано на сайті.",
    "О себе" to "Про себе",
    "Роли" to "Ролі",
    "Нет" to "Немає",
    "Уведомления" to "Сповіщення",
    "Сообщения" to "Повідомлення",
    "Библиотека" to "Бібліотека",
    "Не удалось открыть сайт" to "Не вдалося відкрити сайт",
    "Сортировка" to "Сортування",
    "Статус" to "Статус",
    "Жанры" to "Жанри",
    "Исключить жанры" to "Виключити жанри",
    "Тип" to "Тип",
    "Год" to "Рік",
    "Сезон" to "Сезон",
    "Ограничение" to "Обмеження",
    "Рейтинг" to "Рейтинг",
    "Серии" to "Серії",
    "Метки" to "Мітки",
    "От" to "Від",
    "До" to "До",
    "Вышло" to "Вийшло",
    "Онгоинг" to "Онгоїнг",
    "Анонсы" to "Анонси",
    "Зима" to "Зима",
    "Весна" to "Весна",
    "Лето" to "Літо",
    "Осень" to "Осінь",
    "Полное дублирование" to "Повне дублювання",
    "Многоголосый" to "Багатоголосий",
    "Двухголосый" to "Двоголосий",
    "Одноголосый" to "Одноголосий",
    "Субтитры" to "Субтитри",
    "Смотрю" to "Дивлюся",
    "В планах" to "У планах",
    "Просмотрено" to "Переглянуто",
    "Брошено" to "Кинуто",
    "Любимые" to "Улюблені",
    "Отложено" to "Відкладено",
    "Оценок" to "Оцінок",
    "Просмотры" to "Перегляди",
    "Новые" to "Нові",
    "Топ" to "Топ",
    "А-Я" to "А-Я",
    "По добавлению" to "За додаванням",
    "Случайно" to "Випадково",
    "Оффлайн: фильтруются только скачанные аниме" to "Офлайн: показуються лише завантажені аніме",
    "Все" to "Усі",
    "от" to "від",
    "до" to "до",
    "выбрано" to "вибрано",
    "Удалить аниме" to "Видалити аніме",
    "Удалить серию" to "Видалити серію",
    "Обновления" to "Оновлення",
    "Проверка еще не выполнена." to "Перевірку оновлень ще не виконано.",
    "Описание версии пока не добавлено." to "Опис версії поки не додано.",
    "Выбери озвучку" to "Вибери озвучення",
    "Далее" to "Далі",
    "Удалить домен" to "Видалити домен",
    "Новый домен" to "Новий домен",
    "Добавить" to "Додати",
    "Некорректный домен" to "Некоректний домен",
    "Домен уже в списке" to "Домен уже у списку",
    "Карточка не найдена" to "Картку аніме не знайдено",
    "Войти в аккаунт" to "Увійти в акаунт",
    "Синхронизация" to "Синхронізація",
    "Жанр" to "Жанр",
    "Год выхода" to "Рік виходу",
    "Альт. названия" to "Альт. назви",
    "Первоисточник" to "Першоджерело",
    "Студия" to "Студія",
    "Режиссёр" to "Режисер",
    "До выхода" to "До виходу",
    "Длительность" to "Тривалість",
    "В списках" to "У списках",
    "Кадры" to "Кадри",
    "Порядок просмотра" to "Порядок перегляду",
    "Загружено" to "Завантажено",
    "серия" to "серія",
    "серии" to "серії",
    "голос" to "голос",
    "голоса" to "голоси",
    "голосов" to "голосів",
    "Пока нет оценок" to "Оцінок поки немає",
    "загружено" to "завантажено",
    "Отправить" to "Надіслати",
    "Видео для этого аниме пока нет" to "Відео для цього аніме поки немає",
    "Скачать серию" to "Завантажити серію",
    "Удалить скачанную серию" to "Видалити завантажену серію",
    "Удалить" to "Видалити",
    "Все скачанные варианты" to "Усі завантажені варіанти",
    "Предыдущая серия" to "Попередня серія",
    "Следующая серия" to "Наступна серія",
    "Еще раз" to "Ще раз",
    "офлайн" to "офлайн",
    "локально" to "локально",
    "скачано" to "завантажено",
    "Пропустить" to "Пропустити",
    "Следующая" to "Наступна",
    "из" to "з",
    "Войдите в аккаунт, чтобы открыть библиотеку" to "Увійдіть в акаунт, щоб відкрити бібліотеку",
    "Библиотека пока пустая" to "Бібліотека поки порожня",
    "Поставить на паузу" to "Поставити на паузу",
    "Возобновить загрузку" to "Відновити завантаження",
    "Отменить загрузку" to "Скасувати завантаження",
    "неизвестно" to "невідомо",
    "с" to "с",
)

@Composable
fun YummyDroidApp(
    state: YummyDroidUiState,
    isInPictureInPicture: Boolean,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMoreAnime: () -> Unit,
    onBrowseSectionChange: (BrowseSection) -> Unit,
    onFiltersChange: (BrowseFilters) -> Unit,
    onResetFilters: () -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
    onOpenAnime: (Long) -> Unit,
    onFilterByGenre: (FilterOption) -> Unit,
    onFilterByYear: (Int) -> Unit,
    onFilterByStudio: (FilterOption) -> Unit,
    onFilterByCreator: (FilterOption) -> Unit,
    onSelectVideoGroup: (String) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    onRetryVideo: () -> Unit,
    onPlaybackFailed: (VideoVariant, Long) -> Unit,
    onPlaybackStarted: (VideoVariant) -> Unit,
    onPlaybackEnded: (VideoVariant) -> Unit,
    onPlaybackProgress: (VideoVariant, Long, Long) -> Unit,
    canUsePictureInPicture: Boolean,
    onEnterPictureInPicture: () -> Unit,
    onLogin: (String, String, String?) -> Unit,
    onLogout: () -> Unit,
    onOpenLibraryFilter: () -> Unit,
    onSelectAnimeListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    onSetAnimeRating: (Int?) -> Unit,
    onAddAnimeComment: (String) -> Unit,
    onLoadMoreAnimeComments: () -> Unit,
    onToggleVideoSubscription: (VideoVariant) -> Unit,
    onUnsubscribeVideoSubscription: (VideoSubscription) -> Unit,
    onResolveDownloadQualities: suspend (VideoVariant, List<VideoVariant>, Boolean) -> List<PreferredQuality>,
    onDownloadVideo: (VideoVariant, PreferredQuality) -> Unit,
    onDownloadAllVideos: (String?, PreferredQuality) -> Unit,
    onDeleteOfflineVideo: (Long, Long, String?) -> Unit,
    onDeleteOfflineAnime: (Long) -> Unit,
    onClearAppContentCache: () -> Unit,
    onClearDownloadHistory: () -> Unit,
    onCancelDownload: (Long) -> Unit,
    onPauseDownload: (Long) -> Unit,
    onResumeDownload: (Long) -> Unit,
    onCheckForUpdates: () -> Unit,
    onBack: () -> Unit,
    registerInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var loginDialogOpen by remember { mutableStateOf(false) }
    var profileDialogOpen by remember { mutableStateOf(false) }
    var settingsDialogOpen by remember { mutableStateOf(false) }
    var autoUpdatePromptDismissed by remember { mutableStateOf(false) }
    var modalInputActionHandler by remember { mutableStateOf<((InputAction) -> Boolean)?>(null) }
    var playerInputActionHandler by remember { mutableStateOf<((InputAction) -> Boolean)?>(null) }
    var focusedCatalogAnimeId by rememberSaveable { mutableLongStateOf(0L) }
    var pendingCatalogFocusAnimeId by rememberSaveable { mutableLongStateOf(0L) }
    val catalogGridState = rememberLazyGridState()
    val openAnimeFromCatalog = remember(onOpenAnime) {
        { animeId: Long ->
            focusedCatalogAnimeId = animeId
            pendingCatalogFocusAnimeId = animeId
            onOpenAnime(animeId)
        }
    }
    val playAdjacentEpisode = playAdjacentEpisode@{ forward: Boolean ->
        val route = state.route as? AppRoute.Player ?: return@playAdjacentEpisode false
        val adjacent = findAdjacentPlayerVideo(
            currentVideo = route.video,
            allVideos = (state.videos as? LoadState.Ready)?.data.orEmpty(),
            selectedGroup = state.selectedVideoGroup,
            forward = forward,
        ) ?: return@playAdjacentEpisode false
        onSelectVideoGroup(adjacent.groupKey)
        onPlayVideo(adjacent)
        true
    }
    val inputActionHandler by rememberUpdatedState {
            action: InputAction ->
        modalInputActionHandler?.let { handler ->
            if (handler(action)) {
                return@rememberUpdatedState true
            }
        }
        if (state.route is AppRoute.Player) {
            when {
                playerInputActionHandler?.invoke(action) == true -> true
                action == InputAction.PreviousEpisode -> playAdjacentEpisode(false)
                action == InputAction.NextEpisode -> playAdjacentEpisode(true)
                action == InputAction.Back && state.canNavigateBack -> {
                    onBack()
                    true
                }
                else -> false
            }
        } else {
            when (action) {
                InputAction.Up -> focusManager.moveFocus(FocusDirection.Up)
                InputAction.Down -> focusManager.moveFocus(FocusDirection.Down)
                InputAction.Left -> focusManager.moveFocus(FocusDirection.Left)
                InputAction.Right -> focusManager.moveFocus(FocusDirection.Right)
                InputAction.PreviousEpisode -> playAdjacentEpisode(false)
                InputAction.NextEpisode -> playAdjacentEpisode(true)
                InputAction.Play,
                InputAction.Pause,
                InputAction.PlayPause -> false
                InputAction.Back -> {
                    if (state.canNavigateBack) {
                        onBack()
                        true
                    } else {
                        false
                    }
                }
                InputAction.Confirm -> false
            }
        }
    }

    DisposableEffect(Unit) {
        registerInputActionHandler { action -> inputActionHandler(action) }
        onDispose { registerInputActionHandler(null) }
    }

    CompositionLocalProvider(LocalUiLanguage provides state.settings.contentLanguage) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (state.route is AppRoute.Player) {
                        Modifier
                    } else {
                        Modifier
                            .navigationBarsPadding()
                    },
                )
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        return@onKeyEvent false
                    }

                    when (event.key) {
                        Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                        Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                        Key.DirectionLeft -> focusManager.moveFocus(FocusDirection.Left)
                        Key.DirectionRight -> focusManager.moveFocus(FocusDirection.Right)
                        else -> false
                    }
                },
        ) {
        when (val route = state.route) {
            AppRoute.Home -> BrowseScreen(
                state = state,
                catalogGridState = catalogGridState,
                pendingCatalogFocusAnimeId = pendingCatalogFocusAnimeId,
                onCatalogFocusRestored = { pendingCatalogFocusAnimeId = 0L },
                onCatalogAnimeFocused = { animeId -> focusedCatalogAnimeId = animeId },
                onQueryChange = onQueryChange,
                onRefresh = onRefresh,
                onLoadMoreAnime = onLoadMoreAnime,
                onBrowseSectionChange = onBrowseSectionChange,
                onFiltersChange = onFiltersChange,
                onResetFilters = onResetFilters,
                onOpenSettings = { settingsDialogOpen = true },
                onOpenDownloads = { onBrowseSectionChange(BrowseSection.Downloads) },
                onClearDownloadHistory = onClearDownloadHistory,
                onCancelDownload = onCancelDownload,
                onPauseDownload = onPauseDownload,
                onResumeDownload = onResumeDownload,
                onOpenLogin = { loginDialogOpen = true },
                onOpenProfile = { profileDialogOpen = true },
                onOpenAnime = openAnimeFromCatalog,
            )
            is AppRoute.Details -> DetailsScreenModern(
                state = state,
                onBack = onBack,
                onRefresh = onRefresh,
                onOpenAnime = onOpenAnime,
                onOpenLogin = { loginDialogOpen = true },
                onOpenProfile = { profileDialogOpen = true },
                onGenreFilterSelected = onFilterByGenre,
                onYearFilterSelected = onFilterByYear,
                onStudioFilterSelected = onFilterByStudio,
                onCreatorFilterSelected = onFilterByCreator,
                onSelectVideoGroup = onSelectVideoGroup,
                onPlayVideo = onPlayVideo,
                onPlayVideoAt = onPlayVideoAt,
                onSelectAnimeListMark = onSelectAnimeListMark,
                onToggleFavorite = onToggleFavorite,
                onSetAnimeRating = onSetAnimeRating,
                onAddAnimeComment = onAddAnimeComment,
                onLoadMoreAnimeComments = onLoadMoreAnimeComments,
                onToggleVideoSubscription = onToggleVideoSubscription,
                onResolveDownloadQualities = onResolveDownloadQualities,
                onDownloadVideo = onDownloadVideo,
                onDownloadAllVideos = onDownloadAllVideos,
                onDeleteOfflineVideo = onDeleteOfflineVideo,
                onRegisterModalInputActionHandler = { modalInputActionHandler = it },
            )
            is AppRoute.Player -> PlayerScreen(
                animeTitle = route.animeTitle,
                video = route.video,
                settings = state.settings,
                startPositionMs = route.startPositionMs,
                allVideos = (state.videos as? LoadState.Ready)?.data.orEmpty(),
                selectedGroup = state.selectedVideoGroup,
                streamState = state.playerStream,
                isInPictureInPicture = isInPictureInPicture,
                forcedOfflineMode = state.forcedOfflineMode,
                allowSubscriptions = state.auth.profile != null &&
                    !state.forcedOfflineMode &&
                    ((state.details as? LoadState.Ready)?.data?.canShowVideoSubscriptions() == true),
                subscriptions = (state.detailsExtras as? LoadState.Ready)?.data?.subscriptions.orEmpty(),
                onSelectGroup = onSelectVideoGroup,
                onPlayVideo = onPlayVideo,
                onPlayVideoAt = onPlayVideoAt,
                onToggleVideoSubscription = onToggleVideoSubscription,
                onRetry = onRetryVideo,
                onPlaybackFailed = onPlaybackFailed,
                onPlaybackStarted = onPlaybackStarted,
                onPlaybackEnded = onPlaybackEnded,
                onPlaybackProgress = onPlaybackProgress,
                canUsePictureInPicture = canUsePictureInPicture,
                onEnterPictureInPicture = onEnterPictureInPicture,
                onSettingsChange = onSettingsChange,
                onBack = onBack,
                onRegisterPlayerInputActionHandler = { playerInputActionHandler = it },
            )
        }

        if (loginDialogOpen) {
            LoginDialog(
                auth = state.auth,
                siteBaseUrl = state.siteBaseUrl,
                onLogin = onLogin,
                onDismiss = { loginDialogOpen = false },
            )
        }

        if (profileDialogOpen) {
            ProfileDialog(
                auth = state.auth,
                siteBaseUrl = state.siteBaseUrl,
                subscriptionsState = state.globalSubscriptions,
                onOpenLogin = {
                    profileDialogOpen = false
                    loginDialogOpen = true
                },
                onOpenLibrary = {
                    profileDialogOpen = false
                    onOpenLibraryFilter()
                },
                onUnsubscribeVideoSubscription = onUnsubscribeVideoSubscription,
                onLogout = {
                    profileDialogOpen = false
                    onLogout()
                },
                onDismiss = { profileDialogOpen = false },
            )
        }

        if (settingsDialogOpen) {
            SettingsDialog(
                settings = state.settings,
                offlineEntries = state.offlineEntries,
                updateState = state.updateState,
                onSettingsChange = onSettingsChange,
                onDeleteOfflineVideo = onDeleteOfflineVideo,
                onDeleteOfflineAnime = onDeleteOfflineAnime,
                onClearAppContentCache = onClearAppContentCache,
                onCheckForUpdates = onCheckForUpdates,
                onDismiss = { settingsDialogOpen = false },
            )
        }
        val pendingUpdate = (state.updateState as? LoadState.Ready)
            ?.data
            ?.takeIf { it.isNewerThanInstalled() && !autoUpdatePromptDismissed && !settingsDialogOpen }
        if (pendingUpdate != null) {
            UpdateCheckDialog(
                updateState = LoadState.Ready(pendingUpdate),
                onInstallUpdate = { info ->
                    autoUpdatePromptDismissed = true
                    UpdateDownloadService.start(context, info.apkUrl, info.version)
                },
                onDismiss = { autoUpdatePromptDismissed = true },
            )
        }
        if (state.forcedOfflineMode && state.route !is AppRoute.Player) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp),
            ) {
                OfflineModeChip()
            }
        }
        }
    }

}

@Composable
private fun BrowseScreen(
    state: YummyDroidUiState,
    catalogGridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    pendingCatalogFocusAnimeId: Long,
    onCatalogFocusRestored: () -> Unit,
    onCatalogAnimeFocused: (Long) -> Unit,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMoreAnime: () -> Unit,
    onBrowseSectionChange: (BrowseSection) -> Unit,
    onFiltersChange: (BrowseFilters) -> Unit,
    onResetFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    onClearDownloadHistory: () -> Unit,
    onCancelDownload: (Long) -> Unit,
    onPauseDownload: (Long) -> Unit,
    onResumeDownload: (Long) -> Unit,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAnime: (Long) -> Unit,
) {
    val isCatalog = state.homeSection == BrowseSection.Catalog
    val isSearching = isCatalog && state.searchQuery.isNotBlank()
    val contentState = if (isSearching) state.searchResults else state.featured
    val pagingState = if (isSearching) state.searchPaging else state.featuredPaging
    val configuration = LocalConfiguration.current
    val isWide = configuration.screenWidthDp >= 720
    var searchDialogOpen by remember { mutableStateOf(false) }
    var filtersDialogOpen by remember { mutableStateOf(false) }
    val activeDownloadCount = state.downloadQueue.tasks.count { task ->
        task.state == DownloadTaskState.Queued ||
            task.state == DownloadTaskState.Running ||
            task.state == DownloadTaskState.Paused
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        BrowseTopBarModern(
            onRefresh = onRefresh,
            onOpenSearch = { searchDialogOpen = true },
            onOpenFilters = { filtersDialogOpen = true },
            onOpenSettings = onOpenSettings,
            onOpenDownloads = onOpenDownloads,
            auth = state.auth,
            activeFilters = state.filters.activeCount,
            activeDownloadCount = activeDownloadCount,
            forcedOfflineMode = state.forcedOfflineMode,
            onOpenLogin = onOpenLogin,
            onOpenProfile = onOpenProfile,
            isWide = isWide,
            activeSection = state.homeSection,
            onSectionSelected = onBrowseSectionChange,
        )

        Box(modifier = Modifier.weight(1f)) {
            when (state.homeSection) {
                BrowseSection.Catalog -> AnimeGridSection(
                    contentState = contentState,
                    pagingState = pagingState,
                    gridState = catalogGridState,
                    cardSize = state.settings.posterCardSize,
                    pendingFocusAnimeId = pendingCatalogFocusAnimeId,
                    emptyMessage = if (isSearching) uiText("Ничего не найдено") else uiText("Каталог пуст"),
                    onRetry = onRefresh,
                    onLoadMore = onLoadMoreAnime,
                    onFocusRestored = onCatalogFocusRestored,
                    onAnimeFocused = onCatalogAnimeFocused,
                    onOpenAnime = onOpenAnime,
                )
                BrowseSection.Schedule -> ScheduleSection(
                    state = state.schedule,
                    filters = state.filters,
                    catalog = (state.filterCatalog as? LoadState.Ready)?.data ?: FilterCatalog.Empty,
                    onRetry = onRefresh,
                    onOpenAnime = onOpenAnime,
                )
                BrowseSection.Downloads -> DownloadsSection(
                    state = state,
                    onClearHistory = onClearDownloadHistory,
                    onCancelDownload = onCancelDownload,
                    onPauseDownload = onPauseDownload,
                    onResumeDownload = onResumeDownload,
                    onOpenAnime = onOpenAnime,
                )
            }
        }
    }

    if (searchDialogOpen) {
        SearchDialog(
            query = state.searchQuery,
            onQueryChange = onQueryChange,
            onDismiss = { searchDialogOpen = false },
        )
    }

    if (filtersDialogOpen) {
        FiltersDialogAccordion(
            filters = state.filters,
            auth = state.auth,
            catalogState = state.filterCatalog,
            offlineEntries = (state.offlineEntries as? LoadState.Ready)?.data.orEmpty(),
            forcedOfflineMode = state.forcedOfflineMode,
            onApply = onFiltersChange,
            onReset = onResetFilters,
            onDismiss = { filtersDialogOpen = false },
        )
    }
}

@Composable
private fun AnimeGridSection(
    contentState: LoadState<List<Anime>>,
    pagingState: PagingUiState,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    cardSize: PosterCardSize,
    pendingFocusAnimeId: Long,
    emptyMessage: String,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onFocusRestored: () -> Unit,
    onAnimeFocused: (Long) -> Unit,
    onOpenAnime: (Long) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val columnsCount = remember(screenWidth, cardSize) {
        cardSize.resolveCatalogColumns(screenWidth)
    }
    AnimeListStateContent(
        state = contentState,
        onRetry = onRetry,
        emptyMessage = emptyMessage,
    ) { animes ->
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(pendingFocusAnimeId, animes) {
            if (pendingFocusAnimeId <= 0L) return@LaunchedEffect
            val targetIndex = animes.indexOfFirst { it.id == pendingFocusAnimeId }
            if (targetIndex < 0) return@LaunchedEffect
            gridState.scrollToItem(targetIndex)
            delay(80)
            runCatching { focusRequester.requestFocus() }
            onFocusRestored()
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columnsCount),
            state = gridState,
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(animes, key = { index, anime -> "anime-grid:$index:${anime.id}:${anime.title}" }) { index, anime ->
                AnimeCard(
                    anime = anime,
                    onClick = { onOpenAnime(anime.id) },
                    modifier = Modifier
                        .then(
                            if (anime.id == pendingFocusAnimeId) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier
                            },
                        )
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) onAnimeFocused(anime.id)
                        }
                        .stopGridLineHorizontalFocusEscape(index, animes.size, columnsCount),
                )
            }

            if (pagingState.isLoadingMore || pagingState.canLoadMore || pagingState.error != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PagingGridFooter(
                        paging = pagingState,
                        onLoadMore = onLoadMore,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleSection(
    state: LoadState<List<ScheduleAnime>>,
    filters: BrowseFilters,
    catalog: FilterCatalog,
    onRetry: () -> Unit,
    onOpenAnime: (Long) -> Unit,
) {
    when (state) {
        LoadState.Loading -> LoadingPane(Modifier.fillMaxSize())
        is LoadState.Error -> ErrorPane(
            message = state.message,
            onRetry = onRetry,
            modifier = Modifier.fillMaxSize(),
        )
        is LoadState.Ready -> {
            var hidePastItems by rememberSaveable { mutableStateOf(true) }
            val filteredItems = remember(state.data, filters, catalog) {
                state.data.filteredAndSortedSchedule(filters, catalog)
            }
            val upcomingItems = remember(filteredItems) { upcomingScheduleItems(filteredItems) }
            val visibleItems = if (hidePastItems) upcomingItems else filteredItems
            if (state.data.isEmpty()) {
                EmptyPane(message = uiText("Расписание пока пустое"), modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        SchedulePastFilterToggle(
                            hidePastItems = hidePastItems,
                            hiddenCount = filteredItems.size - upcomingItems.size,
                            onToggle = { hidePastItems = !hidePastItems },
                        )
                    }

                    if (visibleItems.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (filteredItems.isEmpty()) {
                                        uiText("По выбранным фильтрам ничего не найдено")
                                    } else {
                                        uiText("Ближайших выходов пока нет")
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    lazyItemsIndexed(
                        visibleItems,
                        key = { index, item -> "schedule:$index:${item.anime.id}:${item.nextEpisodeAtSeconds}" },
                    ) { _, item ->
                        ScheduleRow(item = item, onOpenAnime = onOpenAnime)
                    }
                }
            }
        }
    }
}

@Composable
private fun SchedulePastFilterToggle(
    hidePastItems: Boolean,
    hiddenCount: Int,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val shape = RoundedCornerShape(8.dp)
        Surface(
            modifier = Modifier
                .height(36.dp)
                .dpadClickable(shape, onToggle),
            color = if (hidePastItems) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            contentColor = if (hidePastItems) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            shape = shape,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(17.dp))
                Text(
                    text = if (hidePastItems) uiText("Прошедшие скрыты") else uiText("Прошедшие показаны"),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (hiddenCount > 0) {
            Text(
                text = if (hidePastItems) "$hiddenCount ${uiText("скрыто")}" else "$hiddenCount ${uiText("прошедших")}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ScheduleRow(
    item: ScheduleAnime,
    onOpenAnime: (Long) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .dpadClickable(RoundedCornerShape(8.dp)) { onOpenAnime(item.anime.id) },
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosterImage(
                url = item.anime.posterUrl,
                contentDescription = item.anime.title,
                modifier = Modifier
                    .width(72.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.anime.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${uiText("Вышло")} ${item.airedEpisodes}" +
                        if (item.totalEpisodes > 0) " ${uiText("из")} ${item.totalEpisodes}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                item.nextEpisodeAtSeconds.takeIf { it > 0L }?.let { next ->
                    Text(
                        text = "${uiText("Следующая")}: ${formatScheduleTimestamp(next)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadsSection(
    state: YummyDroidUiState,
    onClearHistory: () -> Unit,
    onCancelDownload: (Long) -> Unit,
    onPauseDownload: (Long) -> Unit,
    onResumeDownload: (Long) -> Unit,
    onOpenAnime: (Long) -> Unit,
) {
    val offlineEntries = (state.offlineEntries as? LoadState.Ready)?.data.orEmpty()
    val tasks = state.downloadQueue.tasks

    if (tasks.isEmpty() && offlineEntries.isEmpty()) {
        EmptyPane(
            message = uiText("Скачанных серий пока нет"),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (tasks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = uiText("Очередь загрузок"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f),
                    )
                    if (tasks.any { !it.isActive && it.state != DownloadTaskState.Paused }) {
                        DialogActionButton(
                            text = uiText("Очистить"),
                            onClick = onClearHistory,
                        )
                    }
                }
            }
            items(tasks, key = { it.id }) { task ->
                DownloadTaskCard(
                    task = task,
                    onOpenAnime = { onOpenAnime(task.animeId) },
                    onCancelDownload = { onCancelDownload(task.id) },
                    onPauseDownload = { onPauseDownload(task.id) },
                    onResumeDownload = { onResumeDownload(task.id) },
                )
            }
        }

        if (offlineEntries.isNotEmpty()) {
            item {
                Text(
                    text = uiText("Доступно офлайн"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(top = if (tasks.isEmpty()) 0.dp else 12.dp),
                )
            }
            lazyItemsIndexed(
                offlineEntries,
                key = { index, entry -> "offline-entry:$index:${entry.anime.id}:${entry.anime.title}" },
            ) { _, entry ->
                OfflineAnimeRow(
                    entry = entry,
                    onOpenAnime = onOpenAnime,
                )
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: me.yummydroid.app.DownloadTaskUi,
    onOpenAnime: () -> Unit,
    onCancelDownload: () -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .dpadClickable(shape, onOpenAnime),
        color = MaterialTheme.colorScheme.surface,
        shape = shape,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOf(task.episodeTitle, task.qualityTitle).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = task.state.localizedTitle(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (task.state == DownloadTaskState.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    fontWeight = FontWeight.Bold,
                )
                if (task.state == DownloadTaskState.Running || task.state == DownloadTaskState.Queued) {
                    IconButton(
                        onClick = onPauseDownload,
                        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = uiText("Поставить на паузу"))
                    }
                }
                if (task.canResume) {
                    IconButton(
                        onClick = onResumeDownload,
                        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = uiText("Возобновить загрузку"))
                    }
                }
                if (task.isActive || task.state == DownloadTaskState.Paused || task.state == DownloadTaskState.Failed) {
                    IconButton(
                        onClick = onCancelDownload,
                        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = uiText("Отменить загрузку"))
                    }
                }
            }

            if (task.isActive || task.state == DownloadTaskState.Completed) {
                LinearProgressIndicator(
                    progress = { task.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (task.message.isNotBlank()) {
                Text(
                    text = task.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val transferText = task.transferStatusText()
            if (transferText.isNotBlank()) {
                Text(
                    text = transferText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun me.yummydroid.app.DownloadTaskUi.transferStatusText(): String {
    if (!isActive && state != DownloadTaskState.Completed && state != DownloadTaskState.Paused && state != DownloadTaskState.Failed) return ""
    val percent = "${(progress.coerceIn(0f, 1f) * 100f).roundToInt()}%"
    val size = when {
        totalBytes > 0L && downloadedBytes > 0L -> "${formatFileSize(downloadedBytes)} / ${formatFileSize(totalBytes)}"
        downloadedBytes > 0L && isActive -> "${formatFileSize(downloadedBytes)} / ${uiText("неизвестно")}"
        downloadedBytes > 0L -> formatFileSize(downloadedBytes)
        else -> ""
    }
    val speed = if (isActive && bytesPerSecond > 0L) "${formatFileSize(bytesPerSecond)}/${uiText("с")}" else ""
    return listOf(percent, size, speed)
        .filter { it.isNotBlank() }
        .joinToString(" • ")
}

@Composable
private fun OfflineAnimeRow(
    entry: OfflineAnimeEntry,
    onOpenAnime: (Long) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .dpadClickable(RoundedCornerShape(8.dp)) { onOpenAnime(entry.anime.id) },
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PosterImage(
                url = entry.anime.posterUrl,
                contentDescription = entry.anime.title,
                modifier = Modifier
                    .width(58.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = entry.anime.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${entry.downloadedVideos.size} ${localizedEpisodesWord(entry.downloadedVideos.size)} • ${formatFileSize(entry.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DownloadTaskState.localizedTitle(): String = when (this) {
    DownloadTaskState.Queued -> uiText("В очереди")
    DownloadTaskState.Running -> uiText("Загрузка")
    DownloadTaskState.Paused -> uiText("Пауза")
    DownloadTaskState.Added -> uiText("Добавлено")
    DownloadTaskState.Completed -> uiText("Скачано")
    DownloadTaskState.Failed -> uiText("Ошибка")
    DownloadTaskState.Cancelled -> uiText("Отменено")
}

private fun formatScheduleTimestamp(seconds: Long): String {
    return java.text.SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        .format(java.util.Date(seconds * 1000L))
}

private fun formatCommentTimestamp(seconds: Long): String {
    if (seconds <= 0L) return ""
    val millis = if (seconds > 10_000_000_000L) seconds else seconds * 1000L
    return java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        .format(java.util.Date(millis))
}

private fun List<ScheduleAnime>.filteredAndSortedSchedule(
    filters: BrowseFilters,
    catalog: FilterCatalog,
): List<ScheduleAnime> {
    val genreInclude = catalog.tokensFor(filters.genres, catalog.genres)
    val genreExclude = catalog.tokensFor(filters.excludedGenres, catalog.genres)
    val typeInclude = catalog.tokensFor(filters.types, catalog.types)

    return asSequence()
        .filter { item -> item.matchesScheduleFilters(filters, genreInclude, genreExclude, typeInclude) }
        .toList()
        .sortedForSchedule(filters.sort)
}

private fun FilterCatalog.tokensFor(
    selected: Set<String>,
    options: List<FilterOption>,
): Set<String> {
    if (selected.isEmpty()) return emptySet()
    val byValue = options.filter { it.value in selected }
    return (selected + byValue.flatMap { option -> listOf(option.title, option.value) })
        .flatMap { value -> listOf(value, value.substringAfterLast('/')) }
        .map { it.normalizedScheduleToken() }
        .filterTo(mutableSetOf()) { it.isNotBlank() }
}

private fun List<OfflineAnimeEntry>.toOfflineFilterCatalog(): FilterCatalog {
    fun List<String>.toFilterOptions(): List<FilterOption> {
        return asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
            .map { FilterOption(title = it, value = it) }
            .toList()
    }

    return FilterCatalog(
        genres = flatMap { entry ->
            entry.details.genreTags.map { it.title }.ifEmpty { entry.anime.genres }
        }.toFilterOptions(),
        types = map { entry -> entry.details.type.ifBlank { entry.anime.type } }.toFilterOptions(),
    )
}

private fun ScheduleAnime.matchesScheduleFilters(
    filters: BrowseFilters,
    genreInclude: Set<String>,
    genreExclude: Set<String>,
    typeInclude: Set<String>,
): Boolean {
    val anime = anime
    val year = anime.year?.takeIf { it > 0 }
    if (filters.fromYear != null && (year == null || year < filters.fromYear)) return false
    if (filters.toYear != null && (year == null || year > filters.toYear)) return false
    if (filters.minRating != null && (anime.rating == null || anime.rating < filters.minRating)) return false
    if (filters.maxRating != null && (anime.rating == null || anime.rating > filters.maxRating)) return false
    if (filters.episodeFrom != null && airedEpisodes < filters.episodeFrom) return false
    if (filters.episodeTo != null && airedEpisodes > filters.episodeTo) return false
    if (filters.offlineOnly) return false

    if (filters.statuses.isNotEmpty() && filters.statuses.none { anime.status.matchesScheduleStatus(it) }) {
        return false
    }
    if (typeInclude.isNotEmpty() && anime.type.normalizedScheduleToken() !in typeInclude) {
        return false
    }

    val animeGenres = anime.genres.mapTo(mutableSetOf()) { it.normalizedScheduleToken() }
    if (genreInclude.isNotEmpty() && animeGenres.none { genre -> genreInclude.any { genre.matchesScheduleToken(it) } }) {
        return false
    }
    if (genreExclude.isNotEmpty() && animeGenres.any { genre -> genreExclude.any { genre.matchesScheduleToken(it) } }) {
        return false
    }

    return true
}

private fun List<ScheduleAnime>.sortedForSchedule(sort: AnimeSort): List<ScheduleAnime> {
    val collator = Collator.getInstance(Locale.forLanguageTag("ru-RU")).apply {
        strength = Collator.PRIMARY
    }
    return when (sort) {
        AnimeSort.Title -> sortedWith { first, second ->
            collator.compare(first.anime.title, second.anime.title)
        }
        AnimeSort.Views -> sortedByDescending { it.anime.views }
        AnimeSort.Year -> sortedWith(
            compareByDescending<ScheduleAnime> { it.anime.year ?: 0 }
                .thenBy { it.nextEpisodeAtSeconds.takeIf { next -> next > 0L } ?: Long.MAX_VALUE },
        )
        AnimeSort.Id -> sortedByDescending { it.anime.id }
        AnimeSort.Random -> sortedBy { ((it.anime.id * 1103515245L) + 12345L) and 0x7fffffffL }
        AnimeSort.Rating,
        AnimeSort.RatingCounters,
        AnimeSort.Top -> sortedWith(
            compareByDescending<ScheduleAnime> { it.anime.rating ?: -1.0 }
                .thenByDescending { it.anime.views }
                .thenBy { it.nextEpisodeAtSeconds.takeIf { next -> next > 0L } ?: Long.MAX_VALUE },
        )
    }
}

private fun String.matchesScheduleStatus(selected: String): Boolean {
    val status = normalizedScheduleToken()
    return when (selected.normalizedScheduleToken()) {
        "ongoing" -> status.contains("онго") || status.contains("ongo")
        "released" -> status.contains("выш") || status.contains("релиз") || status.contains("released")
        "announcement" -> status.contains("анонс") || status.contains("announce")
        else -> status.matchesScheduleToken(selected.normalizedScheduleToken())
    }
}

private fun String.matchesScheduleToken(token: String): Boolean {
    if (isBlank() || token.isBlank()) return false
    return this == token || contains(token) || token.contains(this)
}

private fun String.normalizedScheduleToken(): String {
    return trim()
        .lowercase(Locale.ROOT)
        .replace('ё', 'е')
        .replace(Regex("[^a-zа-я0-9]+"), " ")
        .trim()
}

internal fun upcomingScheduleItems(
    items: List<ScheduleAnime>,
    nowSeconds: Long = System.currentTimeMillis() / 1000L,
): List<ScheduleAnime> {
    return items.filter { it.nextEpisodeAtSeconds > nowSeconds }
}
@Composable
private fun BrowseTopBarModern(
    onRefresh: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    auth: AuthUiState,
    activeFilters: Int,
    activeDownloadCount: Int,
    forcedOfflineMode: Boolean,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    isWide: Boolean,
    activeSection: BrowseSection,
    onSectionSelected: (BrowseSection) -> Unit,
) {
    val horizontalPadding = if (isWide) 32.dp else 16.dp
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val stackActions = !isWide && screenWidthDp < 360

    if (isWide) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .padding(horizontal = horizontalPadding, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "YummyDroid",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                if (forcedOfflineMode) {
                    OfflineModeChip()
                }

                BrowseTopBarActions(
                    onRefresh = onRefresh,
                    onOpenSearch = onOpenSearch,
                    onOpenFilters = onOpenFilters,
                    onOpenSettings = onOpenSettings,
                    onOpenDownloads = onOpenDownloads,
                    auth = auth,
                    activeFilters = activeFilters,
                    activeDownloadCount = activeDownloadCount,
                    onOpenLogin = onOpenLogin,
                    onOpenProfile = onOpenProfile,
                )
            }

            BrowseSectionTabs(
                activeSection = activeSection,
                onSectionSelected = onSectionSelected,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .padding(horizontal = horizontalPadding, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "YummyDroid",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (forcedOfflineMode) {
                    OfflineModeChip()
                }
            }

            BrowseTopBarActions(
                onRefresh = onRefresh,
                onOpenSearch = onOpenSearch,
                onOpenFilters = onOpenFilters,
                onOpenSettings = onOpenSettings,
                onOpenDownloads = onOpenDownloads,
                auth = auth,
                activeFilters = activeFilters,
                activeDownloadCount = activeDownloadCount,
                onOpenLogin = onOpenLogin,
                onOpenProfile = onOpenProfile,
                modifier = Modifier.fillMaxWidth(),
                spreadActions = !stackActions,
                stackActions = stackActions,
            )

            BrowseSectionTabs(
                activeSection = activeSection,
                onSectionSelected = onSectionSelected,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BrowseSectionTabs(
    activeSection: BrowseSection,
    onSectionSelected: (BrowseSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleSections = listOf(BrowseSection.Catalog, BrowseSection.Schedule)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visibleSections.forEach { section ->
            val selected = section == activeSection
            val shape = RoundedCornerShape(8.dp)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .dpadClickable(shape) { onSectionSelected(section) },
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                border = if (selected) {
                    null
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.72f))
                },
                shape = shape,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = section.localizedTitle(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineModeChip() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                text = uiText("Оффлайн"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BrowseTopBarActions(
    onRefresh: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    auth: AuthUiState,
    activeFilters: Int,
    activeDownloadCount: Int,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    spreadActions: Boolean = false,
    stackActions: Boolean = false,
) {
    if (stackActions) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SearchActionButton(onOpenSearch)
                FiltersActionButton(activeFilters, onOpenFilters)
                DownloadsActionButton(activeDownloadCount, onOpenDownloads)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SettingsActionButton(onOpenSettings)
                RefreshActionButton(onRefresh)
                ProfileActionButton(auth, onOpenLogin, onOpenProfile)
            }
        }
        return
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (spreadActions) Arrangement.SpaceBetween else Arrangement.spacedBy(10.dp),
    ) {
        SearchActionButton(onOpenSearch)
        FiltersActionButton(activeFilters, onOpenFilters)
        DownloadsActionButton(activeDownloadCount, onOpenDownloads)
        SettingsActionButton(onOpenSettings)
        RefreshActionButton(onRefresh)
        ProfileActionButton(auth, onOpenLogin, onOpenProfile)
    }
}

@Composable
private fun ProfileActionButton(
    auth: AuthUiState,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    IconButton(
        onClick = if (auth.profile == null) onOpenLogin else onOpenProfile,
        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
    ) {
        Icon(
            Icons.Default.AccountCircle,
            contentDescription = if (auth.profile == null) uiText("Войти") else uiText("Профиль"),
        )
    }
}

@Composable
private fun SearchActionButton(onOpenSearch: () -> Unit) {
    IconButton(
        onClick = onOpenSearch,
        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
    ) {
        Icon(Icons.Default.Search, contentDescription = uiText("Поиск"))
    }
}

@Composable
private fun DownloadsActionButton(
    activeDownloadCount: Int,
    onOpenDownloads: () -> Unit,
) {
    IconButton(
        onClick = onOpenDownloads,
        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(Icons.Default.Download, contentDescription = uiText("Загрузки"))
            if (activeDownloadCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 1.dp)
                        .widthIn(min = 17.dp)
                        .height(17.dp),
                ) {
                    Text(
                        text = if (activeDownloadCount > 9) "9+" else activeDownloadCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FiltersActionButton(
    activeFilters: Int,
    onOpenFilters: () -> Unit,
) {
    IconButton(
        onClick = onOpenFilters,
        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(Icons.Default.FilterList, contentDescription = uiText("Фильтры"))
            if (activeFilters > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .size(18.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = activeFilters.coerceAtMost(9).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RefreshActionButton(onRefresh: () -> Unit) {
    IconButton(
        onClick = onRefresh,
        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
    ) {
        Icon(Icons.Default.Refresh, contentDescription = uiText("Обновить"))
    }
}

@Composable
private fun SearchDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val uiLanguage = LocalUiLanguage.current
    val voicePrompt = uiText("Что найти?")
    val voiceUnavailable = uiText("Голосовой поиск недоступен на этом устройстве")
    val focusRequester = remember { FocusRequester() }
    val micFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val isTelevision = remember(configuration.uiMode) {
        val uiMode = configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK
        uiMode == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }
    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val recognizedText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (recognizedText.isNotBlank()) {
                onQueryChange(recognizedText)
            }
        }
    }
    val launchVoiceSearch = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, uiLanguage.voiceRecognizerTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, voicePrompt)
        }
        runCatching {
            keyboardController?.hide()
            voiceSearchLauncher.launch(intent)
        }.onFailure { throwable ->
            if (throwable is ActivityNotFoundException) {
                Toast.makeText(
                    context,
                    voiceUnavailable,
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                throw throwable
            }
        }
        Unit
    }

    LaunchedEffect(Unit) {
        delay(120)
        if (isTelevision) {
            micFocusRequester.requestFocus()
        } else {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("Поиск")) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = launchVoiceSearch,
                    modifier = Modifier
                        .size(56.dp)
                        .focusRequester(micFocusRequester)
                        .focusRing(RoundedCornerShape(8.dp)),
                ) {
                    Icon(Icons.Default.Mic, contentDescription = uiText("Голосовой поиск"))
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text(uiText("Найти аниме")) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .padding(2.dp)
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                                micFocusRequester.requestFocus()
                                true
                            } else {
                                false
                            }
                        },
                )
            }
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(
                    text = uiText("Готово"),
                    primary = true,
                    onClick = onDismiss,
                )
            }
        },
    )
}

@Composable
private fun DialogActionRow(
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun DialogActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val shape = RoundedCornerShape(8.dp)
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f)
            },
            contentColor = if (primary) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f),
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = modifier
            .widthIn(min = if (primary) 94.dp else 78.dp)
            .defaultMinSize(minWidth = 0.dp, minHeight = 40.dp)
            .focusRing(shape),
    ) {
        if (loading) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun FiltersDialogAccordion(
    filters: BrowseFilters,
    auth: AuthUiState,
    catalogState: LoadState<FilterCatalog>,
    offlineEntries: List<OfflineAnimeEntry>,
    forcedOfflineMode: Boolean,
    onApply: (BrowseFilters) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isAuthorized = auth.profile != null && !forcedOfflineMode
    var draft by remember(filters, isAuthorized, forcedOfflineMode) {
        val baseFilters = if (isAuthorized) filters else filters.copy(userMarks = emptySet())
        mutableStateOf(if (forcedOfflineMode) baseFilters.copy(offlineOnly = true, userMarks = emptySet()) else baseFilters)
    }
    var expandedSection by remember { mutableStateOf("") }
    val catalog = remember(catalogState, offlineEntries, forcedOfflineMode) {
        if (forcedOfflineMode) {
            offlineEntries.toOfflineFilterCatalog()
        } else {
            (catalogState as? LoadState.Ready)?.data ?: FilterCatalog.Empty
        }
    }
    val containerScrollState = rememberScrollState()
    val applyFocusRequester = remember { FocusRequester() }
    val moveFocusToActions: () -> Unit = remember {
        {
            applyFocusRequester.requestFocus()
            Unit
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("Фильтры")) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .verticalScroll(
                        state = containerScrollState,
                        enabled = expandedSection.isBlank(),
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SortAccordionSection(
                    expanded = expandedSection == "sort",
                    selected = draft.sort,
                    onToggleExpanded = {
                        expandedSection = if (expandedSection == "sort") "" else "sort"
                    },
                    onSelected = { draft = draft.copy(sort = it) },
                    onSideExit = moveFocusToActions,
                )

                FilterAccordionSection(
                    id = "status",
                    title = uiText("Статус"),
                    options = statusFilterOptions,
                    selected = draft.statuses,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(statuses = draft.statuses.toggle(value)) },
                    onSideExit = moveFocusToActions,
                )

                FilterAccordionSection(
                    id = "genres",
                    title = uiText("Жанры"),
                    options = catalog.genres,
                    selected = draft.genres,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(genres = draft.genres.toggle(value)) },
                    onSideExit = moveFocusToActions,
                )

                FilterAccordionSection(
                    id = "excluded_genres",
                    title = uiText("Исключить жанры"),
                    options = catalog.genres,
                    selected = draft.excludedGenres,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(excludedGenres = draft.excludedGenres.toggle(value)) },
                    onSideExit = moveFocusToActions,
                )

                FilterAccordionSection(
                    id = "types",
                    title = uiText("Тип"),
                    options = catalog.types,
                    selected = draft.types,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(types = draft.types.toggle(value)) },
                    onSideExit = moveFocusToActions,
                )

                RangeAccordionSection(
                    id = "years",
                    title = uiText("Год"),
                    summary = rangeSummary(draft.fromYear, draft.toYear),
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    startLabel = uiText("От"),
                    endLabel = uiText("До"),
                    startText = draft.fromYear?.toString().orEmpty(),
                    endText = draft.toYear?.toString().orEmpty(),
                    keyboardType = KeyboardType.Number,
                    sanitizeInput = ::integerInput,
                    onStartChange = { value -> draft = draft.copy(fromYear = value.yearFilterValue()) },
                    onEndChange = { value -> draft = draft.copy(toYear = value.yearFilterValue()) },
                    onSideExit = moveFocusToActions,
                )

                FilterAccordionSection(
                    id = "seasons",
                    title = uiText("Сезон"),
                    options = seasonFilterOptions,
                    selected = draft.seasons,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(seasons = draft.seasons.toggle(value)) },
                    onSideExit = moveFocusToActions,
                )

                FilterAccordionSection(
                    id = "translates",
                    title = uiText("Озвучка"),
                    options = translateFilterOptions,
                    selected = draft.translates,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(translates = draft.translates.toggle(value)) },
                    onSideExit = moveFocusToActions,
                )

                FilterAccordionSection(
                    id = "age",
                    title = uiText("Ограничение"),
                    options = ageRatingFilterOptions,
                    selected = draft.ageRatings,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(ageRatings = draft.ageRatings.toggle(value)) },
                    onSideExit = moveFocusToActions,
                )

                RangeAccordionSection(
                    id = "rating_range",
                    title = uiText("Рейтинг"),
                    summary = rangeSummary(draft.minRating, draft.maxRating),
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    startLabel = uiText("От"),
                    endLabel = uiText("До"),
                    startText = draft.minRating.filterText(),
                    endText = draft.maxRating.filterText(),
                    keyboardType = KeyboardType.Decimal,
                    sanitizeInput = ::decimalInput,
                    onStartChange = { value -> draft = draft.copy(minRating = value.ratingFilterValue()) },
                    onEndChange = { value -> draft = draft.copy(maxRating = value.ratingFilterValue()) },
                    onSideExit = moveFocusToActions,
                )

                RangeAccordionSection(
                    id = "episodes",
                    title = uiText("Серии"),
                    summary = rangeSummary(draft.episodeFrom, draft.episodeTo),
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    startLabel = uiText("От"),
                    endLabel = uiText("До"),
                    startText = draft.episodeFrom?.toString().orEmpty(),
                    endText = draft.episodeTo?.toString().orEmpty(),
                    keyboardType = KeyboardType.Number,
                    sanitizeInput = ::integerInput,
                    onStartChange = { value -> draft = draft.copy(episodeFrom = value.episodeFilterValue()) },
                    onEndChange = { value -> draft = draft.copy(episodeTo = value.episodeFilterValue()) },
                    onSideExit = moveFocusToActions,
                )

                if (isAuthorized) {
                    FilterAccordionSection(
                        id = "user_marks",
                        title = uiText("Метки"),
                        options = userMarkFilterOptions,
                        selected = draft.userMarks,
                        expandedSection = expandedSection,
                        onExpandedChange = { expandedSection = it },
                        onToggle = { value -> draft = draft.copy(userMarks = draft.userMarks.toggle(value)) },
                        onSideExit = moveFocusToActions,
                    )
                }

                if (forcedOfflineMode) {
                    OfflineFilterNotice()
                } else {
                    SettingsSwitchRow(
                        title = uiText("Доступно офлайн"),
                        checked = draft.offlineOnly,
                        onCheckedChange = { checked -> draft = draft.copy(offlineOnly = checked) },
                    )
                }

                if (!forcedOfflineMode && catalogState is LoadState.Error) {
                    Text(
                        text = catalogState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(
                    text = uiText("Сбросить"),
                    onClick = {
                        draft = if (forcedOfflineMode) BrowseFilters(offlineOnly = true) else BrowseFilters()
                        onReset()
                        onDismiss()
                    },
                )
                DialogActionButton(
                    text = uiText("Отмена"),
                    onClick = onDismiss,
                )
                DialogActionButton(
                    text = uiText("Применить"),
                    primary = true,
                    modifier = Modifier.focusRequester(applyFocusRequester),
                    onClick = {
                        onApply(
                            when {
                                forcedOfflineMode -> draft.copy(offlineOnly = true, userMarks = emptySet())
                                isAuthorized -> draft
                                else -> draft.copy(userMarks = emptySet())
                            },
                        )
                        onDismiss()
                    },
                )
            }
        },
    )
}

@Composable
private fun OfflineFilterNotice() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = uiText("Оффлайн: фильтруются только скачанные аниме"),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SortAccordionSection(
    expanded: Boolean,
    selected: AnimeSort,
    onToggleExpanded: () -> Unit,
    onSelected: (AnimeSort) -> Unit,
    onSideExit: () -> Unit,
) {
    AccordionHeader(
        title = uiText("Сортировка"),
        summary = selected.localizedTitle(),
        expanded = expanded,
        active = selected != AnimeSort.Rating,
        onClick = onToggleExpanded,
    )

    if (expanded) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(AnimeSort.entries, key = { it.name }) { sort ->
                SelectableFilterRow(
                    title = sort.localizedTitle(),
                    selected = selected == sort,
                    onClick = { onSelected(sort) },
                    onSideExit = onSideExit,
                )
            }
        }
    }
}

@Composable
private fun FilterAccordionSection(
    id: String,
    title: String,
    options: List<FilterOption>,
    selected: Set<String>,
    expandedSection: String,
    onExpandedChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onSideExit: () -> Unit,
) {
    if (options.isEmpty()) return

    val sortedOptions = remember(options) { options.sortedByTitle() }
    val expanded = expandedSection == id
    AccordionHeader(
        title = title,
        summary = selectedFilterSummary(sortedOptions, selected),
        expanded = expanded,
        active = selected.isNotEmpty(),
        onClick = { onExpandedChange(if (expanded) "" else id) },
    )

    if (expanded) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            lazyItemsIndexed(
                sortedOptions,
                key = { index, option -> "filter-option:$id:$index:${option.value}" },
            ) { _, option ->
                SelectableFilterRow(
                    title = option.localizedTitle(),
                    selected = option.value in selected,
                    onClick = { onToggle(option.value) },
                    onSideExit = onSideExit,
                )
            }
        }
    }
}

@Composable
private fun RangeAccordionSection(
    id: String,
    title: String,
    summary: String,
    expandedSection: String,
    onExpandedChange: (String) -> Unit,
    startLabel: String,
    endLabel: String,
    startText: String,
    endText: String,
    keyboardType: KeyboardType,
    sanitizeInput: (String) -> String,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onSideExit: () -> Unit,
) {
    val expanded = expandedSection == id
    var localStart by remember(id, startText) { mutableStateOf(startText) }
    var localEnd by remember(id, endText) { mutableStateOf(endText) }

    AccordionHeader(
        title = title,
        summary = summary,
        expanded = expanded,
        active = startText.isNotBlank() || endText.isNotBlank(),
        onClick = { onExpandedChange(if (expanded) "" else id) },
    )

    if (expanded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = localStart,
                onValueChange = { value ->
                    val sanitized = sanitizeInput(value)
                    localStart = sanitized
                    onStartChange(sanitized)
                },
                label = { Text(startLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier
                    .weight(1f)
                    .padding(2.dp)
                    .defaultMinSize(minWidth = 0.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.isHorizontalFilterExit()) {
                            onSideExit()
                            true
                        } else {
                            false
                        }
                    },
            )
            OutlinedTextField(
                value = localEnd,
                onValueChange = { value ->
                    val sanitized = sanitizeInput(value)
                    localEnd = sanitized
                    onEndChange(sanitized)
                },
                label = { Text(endLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier
                    .weight(1f)
                    .padding(2.dp)
                    .defaultMinSize(minWidth = 0.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.isHorizontalFilterExit()) {
                            onSideExit()
                            true
                        } else {
                            false
                        }
                    },
            )
        }
    }
}

@Composable
private fun AccordionHeader(
    title: String,
    summary: String,
    expanded: Boolean,
    active: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (active) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (expanded) 0.78f else 0.58f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val contentColor = if (active) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val summaryColor = if (active) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .dpadClickable(RoundedCornerShape(8.dp), onClick),
        color = backgroundColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = summaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun SelectableFilterRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    onSideExit: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .onPreviewKeyEvent { event ->
                if (event.isHorizontalFilterExit()) {
                    onSideExit?.invoke()
                    onSideExit != null
                } else {
                    false
                }
            }
            .dpadClickable(RoundedCornerShape(8.dp), onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onClick() },
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun androidx.compose.ui.input.key.KeyEvent.isHorizontalFilterExit(): Boolean {
    return type == KeyEventType.KeyDown && (key == Key.DirectionLeft || key == Key.DirectionRight)
}

private fun Modifier.stopHorizontalFocusEscape(index: Int, total: Int): Modifier {
    if (total <= 1 || index < 0) return this
    val isFirst = index == 0
    val isLast = index >= total - 1
    return focusProperties {
        if (isFirst) left = FocusRequester.Cancel
        if (isLast) right = FocusRequester.Cancel
    }.onPreviewKeyEvent { event ->
        event.type == KeyEventType.KeyDown &&
            (
                (event.key == Key.DirectionLeft && isFirst) ||
                    (event.key == Key.DirectionRight && isLast)
                )
    }
}

private fun Modifier.stopGridLineHorizontalFocusEscape(index: Int, total: Int, columns: Int): Modifier {
    if (total <= 1 || index < 0 || columns <= 0) return this
    val isFirstInLine = index % columns == 0
    val isLastInLine = index % columns == columns - 1 || index >= total - 1
    return focusProperties {
        if (isFirstInLine) left = FocusRequester.Cancel
        if (isLastInLine) right = FocusRequester.Cancel
    }.onPreviewKeyEvent { event ->
        event.type == KeyEventType.KeyDown &&
            (
                (event.key == Key.DirectionLeft && isFirstInLine) ||
                    (event.key == Key.DirectionRight && isLastInLine)
                )
    }
}

@Composable
private fun rangeSummary(from: Number?, to: Number?): String {
    val start = from.filterText()
    val end = to.filterText()
    return when {
        start.isBlank() && end.isBlank() -> uiText("Все")
        start.isNotBlank() && end.isNotBlank() -> "$start - $end"
        start.isNotBlank() -> "${uiText("от")} $start"
        else -> "${uiText("до")} $end"
    }
}

private fun Number?.filterText(): String {
    return when (this) {
        null -> ""
        is Double -> if (this % 1.0 == 0.0) toInt().toString() else toString()
        else -> toString()
    }
}

private fun integerInput(value: String): String {
    return value.filter { it.isDigit() }.take(5)
}

private fun decimalInput(value: String): String {
    val normalized = value.replace(',', '.')
    val builder = StringBuilder()
    var dotSeen = false
    normalized.forEach { char ->
        when {
            char.isDigit() -> builder.append(char)
            char == '.' && !dotSeen -> {
                builder.append(char)
                dotSeen = true
            }
        }
    }
    return builder.toString().take(4)
}

private fun String.yearFilterValue(): Int? {
    return toIntOrNull()?.takeIf { it in 1900..2100 }
}

private fun String.episodeFilterValue(): Int? {
    return toIntOrNull()?.takeIf { it in 0..10000 }
}

private fun String.ratingFilterValue(): Double? {
    return toDoubleOrNull()?.takeIf { it in 0.0..10.0 }
}

private fun List<FilterOption>.sortedByTitle(): List<FilterOption> {
    val collator = Collator.getInstance(Locale.forLanguageTag("ru-RU")).apply {
        strength = Collator.PRIMARY
    }
    return sortedWith { first, second ->
        val titleCompare = collator.compare(first.title, second.title)
        if (titleCompare != 0) titleCompare else first.value.compareTo(second.value)
    }
}

@Composable
private fun selectedFilterSummary(
    options: List<FilterOption>,
    selected: Set<String>,
): String {
    if (selected.isEmpty()) return uiText("Все")

    val titles = options
        .filter { it.value in selected }
        .map { it.localizedTitle() }

    return when {
        titles.isEmpty() -> "${selected.size} ${uiText("выбрано")}"
        titles.size <= 2 -> titles.joinToString(", ")
        else -> titles.take(2).joinToString(", ") + " +${titles.size - 2}"
    }
}

private fun Set<String>.toggle(value: String): Set<String> {
    return if (value in this) this - value else this + value
}

@Composable
private fun LoginDialog(
    auth: AuthUiState,
    siteBaseUrl: String,
    onLogin: (String, String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var handledCaptchaNonce by remember { mutableLongStateOf(0L) }
    val captchaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val token = result.data
                ?.getStringExtra(HCaptchaActivity.EXTRA_CAPTCHA_TOKEN)
                .orEmpty()
            if (token.isNotBlank()) {
                onLogin(login, password, token)
            }
        }
    }

    LaunchedEffect(auth.profile) {
        if (auth.profile != null) {
            onDismiss()
        }
    }

    LaunchedEffect(auth.captchaRequestNonce) {
        val nonce = auth.captchaRequestNonce
        if (nonce > 0L && nonce != handledCaptchaNonce) {
            handledCaptchaNonce = nonce
            captchaLauncher.launch(Intent(context, HCaptchaActivity::class.java))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("Вход")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                    label = { Text(uiText("Email")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(1.dp),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(uiText("Пароль")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(1.dp),
                )
                auth.error?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(sitePageUrl(siteBaseUrl, "register"))),
                            )
                        },
                        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
                    ) {
                        Text(uiText("Регистрация"), maxLines = 1, softWrap = false)
                    }
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(sitePageUrl(siteBaseUrl, "login/reset-password"))),
                            )
                        },
                        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
                    ) {
                        Text(uiText("Забыли пароль?"), maxLines = 1, softWrap = false)
                    }
                }
            }
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(
                    text = uiText("Отмена"),
                    onClick = onDismiss,
                )
                DialogActionButton(
                    text = uiText("Войти"),
                    primary = true,
                    enabled = !auth.loading,
                    loading = auth.loading,
                    onClick = { onLogin(login, password, null) },
                )
            }
        },
    )
}

@Composable
private fun ProfileDialog(
    auth: AuthUiState,
    siteBaseUrl: String,
    subscriptionsState: LoadState<List<VideoSubscription>>,
    onOpenLogin: () -> Unit,
    onOpenLibrary: () -> Unit,
    onUnsubscribeVideoSubscription: (VideoSubscription) -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
) {
    val profile = auth.profile
    val context = LocalContext.current
    val openSiteError = uiText("Не удалось открыть сайт")
    var subscriptionsDialogOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (profile == null) uiText("Аккаунт") else uiText("Профиль")) },
        text = {
            if (profile == null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = uiText("Вы не авторизованы."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    auth.error?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (profile.avatarUrl.isBlank()) {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp),
                                )
                            } else {
                                PosterImage(
                                    url = profile.avatarUrl,
                                    contentDescription = profile.nickname,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = profile.nickname.ifBlank { uiText("Пользователь") },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "ID: ${profile.id}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (profile.banned) {
                        Text(
                            text = uiText("Аккаунт заблокирован на сайте."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    if (profile.about.isNotBlank()) {
                        ProfileProperty(label = uiText("О себе"), value = profile.about)
                    }

                    ProfileProperty(
                        label = uiText("Роли"),
                        value = profile.roles.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: uiText("Нет"),
                    )
                    ProfileProperty(
                        label = uiText("Уведомления"),
                        value = profile.unreadNotifications.toString(),
                    )
                    ProfileProperty(
                        label = uiText("Сообщения"),
                        value = profile.unreadMessages.toString(),
                    )
                }
            }
        },
        confirmButton = {
            if (profile == null) {
                DialogActionRow {
                    DialogActionButton(
                        text = uiText("Закрыть"),
                        onClick = onDismiss,
                    )
                    DialogActionButton(
                        text = uiText("Войти"),
                        primary = true,
                        onClick = onOpenLogin,
                    )
                }
            } else {
                DialogActionRow {
                    DialogActionButton(
                        text = uiText("Библиотека"),
                        onClick = onOpenLibrary,
                    )
                    DialogActionButton(
                        text = uiText("Подписки"),
                        onClick = { subscriptionsDialogOpen = true },
                    )
                    DialogActionButton(
                        text = uiText("ЛК"),
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(profile.siteProfileUrl(siteBaseUrl)),
                                    ),
                                )
                            }.onFailure {
                                Toast.makeText(context, openSiteError, Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    DialogActionButton(
                        text = uiText("Закрыть"),
                        onClick = onDismiss,
                    )
                    DialogActionButton(
                        text = uiText("Выйти"),
                        primary = true,
                        onClick = onLogout,
                    )
                }
            }
        },
    )

    if (subscriptionsDialogOpen && profile != null) {
        ProfileSubscriptionsDialog(
            subscriptionsState = subscriptionsState,
            onUnsubscribe = onUnsubscribeVideoSubscription,
            onDismiss = { subscriptionsDialogOpen = false },
        )
    }
}

@Composable
private fun ProfileSubscriptionsDialog(
    subscriptionsState: LoadState<List<VideoSubscription>>,
    onUnsubscribe: (VideoSubscription) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("Подписки")) },
        text = {
            when (subscriptionsState) {
                LoadState.Loading -> LoadingPane(
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                )
                is LoadState.Error -> Text(
                    text = subscriptionsState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                is LoadState.Ready -> {
                    val subscriptions = subscriptionsState.data.sortedWith(
                        compareBy<VideoSubscription> { it.title.lowercase(Locale.ROOT) }
                            .thenBy { it.dubbing.lowercase(Locale.ROOT) },
                    )
                    if (subscriptions.isEmpty()) {
                        Text(
                            text = uiText("Подписок нет"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(subscriptions, key = { "${it.animeId}:${it.player}:${it.dubbing}" }) { subscription ->
                                SubscriptionManagementRow(
                                    subscription = subscription,
                                    onUnsubscribe = { onUnsubscribe(subscription) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(text = uiText("Закрыть"), primary = true, onClick = onDismiss)
            }
        },
    )
}

@Composable
private fun SubscriptionManagementRow(
    subscription: VideoSubscription,
    onUnsubscribe: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PosterImage(
                url = subscription.posterUrl,
                contentDescription = subscription.title,
                modifier = Modifier
                    .width(48.dp)
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = subscription.title.ifBlank { uiText("Аниме") },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subscription.dubbing.cleanVideoLabel("Озвучка")
                        .ifBlank { subscription.dubbing.ifBlank { subscription.player } },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onUnsubscribe,
                modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
            ) {
                Icon(Icons.Default.Close, contentDescription = uiText("Отключить"))
            }
        }
    }
}

@Composable
private fun SettingsActionButton(onOpenSettings: () -> Unit) {
    IconButton(
        onClick = onOpenSettings,
        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
    ) {
        Icon(Icons.Default.Settings, contentDescription = uiText("Настройки"))
    }
}

@Composable
private fun ProfileProperty(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SettingsVersionRow(
    version: String,
    onCheckForUpdates: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProfileProperty(
            label = uiText("Версия"),
            value = version,
        )
        Spacer(Modifier.weight(1f))
        DialogActionButton(
            text = uiText("Проверить"),
            onClick = onCheckForUpdates,
        )
    }
}

@Composable
private fun SettingsDialog(
    settings: AppSettings,
    offlineEntries: LoadState<List<OfflineAnimeEntry>>,
    updateState: LoadState<me.yummydroid.app.data.AppUpdateInfo?>,
    onSettingsChange: (AppSettings) -> Unit,
    onDeleteOfflineVideo: (Long, Long, String?) -> Unit,
    onDeleteOfflineAnime: (Long) -> Unit,
    onClearAppContentCache: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var downloadsDialogOpen by remember { mutableStateOf(false) }
    var clearCacheDialogOpen by remember { mutableStateOf(false) }
    var updateDialogOpen by remember { mutableStateOf(false) }
    var qualityPickerOpen by remember { mutableStateOf(false) }
    var decoderPickerOpen by remember { mutableStateOf(false) }
    var cardSizePickerOpen by remember { mutableStateOf(false) }
    var languagePickerOpen by remember { mutableStateOf(false) }
    var domainsDialogOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("Настройки")) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SettingsSectionTitle(uiText("Хранилище"))
                SettingsActionRow(
                    title = uiText("Скачанные серии"),
                    value = offlineEntries.offlineSummary(),
                    onClick = { downloadsDialogOpen = true },
                )
                SettingsActionRow(
                    title = uiText("Очистить кэш"),
                    value = uiText("Видео, карточки и прогресс"),
                    onClick = { clearCacheDialogOpen = true },
                )
                SettingsSliderRow(
                    title = uiText("Потоки загрузки"),
                    value = settings.downloadParallelism,
                    valueRange = 1..4,
                    onValueChange = { onSettingsChange(settings.copy(downloadParallelism = it)) },
                )
                SettingsSwitchRow(
                    title = uiText("Скачивать через мобильный интернет"),
                    checked = settings.allowMeteredDownloads,
                    onCheckedChange = { onSettingsChange(settings.copy(allowMeteredDownloads = it)) },
                )

                SettingsSectionTitle(uiText("Воспроизведение"))
                SettingsActionRow(
                    title = uiText("Качество по умолчанию"),
                    value = settings.defaultQuality.localizedTitle(),
                    onClick = { qualityPickerOpen = true },
                    isPicker = true,
                )
                SettingsActionRow(
                    title = uiText("Декодер"),
                    value = settings.decoderMode.localizedTitle(),
                    onClick = { decoderPickerOpen = true },
                    isPicker = true,
                )
                SettingsSwitchRow(
                    title = uiText("Автовоспроизведение следующей серии"),
                    checked = settings.autoplayNextEpisode,
                    onCheckedChange = { onSettingsChange(settings.copy(autoplayNextEpisode = it)) },
                )

                SettingsSectionTitle(uiText("Каталог и оформление"))
                SettingsActionRow(
                    title = uiText("Размер карточек"),
                    value = settings.posterCardSize.localizedTitle(),
                    onClick = { cardSizePickerOpen = true },
                    isPicker = true,
                )
                SettingsActionRow(
                    title = uiText("Язык приложения и контента"),
                    value = settings.contentLanguage.localizedTitle(),
                    onClick = { languagePickerOpen = true },
                    isPicker = true,
                )

                SettingsSectionTitle(uiText("Автоматические метки"))
                SettingsSwitchRow(
                    title = uiText("Ставить «Смотрю» при воспроизведении"),
                    checked = settings.autoMarkWatchingOnPlayback,
                    onCheckedChange = { onSettingsChange(settings.copy(autoMarkWatchingOnPlayback = it)) },
                )
                SettingsSwitchRow(
                    title = uiText("Ставить «Просмотрено» после последней серии"),
                    checked = settings.autoMarkWatchedOnCompletedFinalEpisode,
                    onCheckedChange = {
                        onSettingsChange(settings.copy(autoMarkWatchedOnCompletedFinalEpisode = it))
                    },
                )

                SettingsSectionTitle(uiText("Сеть"))
                SettingsSwitchRow(
                    title = uiText("Уведомления приложения"),
                    checked = settings.notificationsEnabled,
                    onCheckedChange = { onSettingsChange(settings.copy(notificationsEnabled = it)) },
                )
                SettingsActionRow(
                    title = uiText("Домены сайта"),
                    value = "${settings.siteDomains.size} ${uiText("доменов")}",
                    onClick = { domainsDialogOpen = true },
                )
                SettingsSwitchRow(
                    title = uiText("Проверять обновления при запуске"),
                    checked = settings.autoCheckUpdates,
                    onCheckedChange = { onSettingsChange(settings.copy(autoCheckUpdates = it)) },
                )

                SettingsSectionTitle(uiText("О программе"))
                SettingsVersionRow(
                    version = "${BuildConfig.VERSION_NAME} ${BuildConfig.BUILD_TYPE}",
                    onCheckForUpdates = {
                        updateDialogOpen = true
                        onCheckForUpdates()
                    },
                )
            }
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(
                    text = uiText("Готово"),
                    primary = true,
                    onClick = onDismiss,
                )
            }
        },
    )

    if (downloadsDialogOpen) {
        OfflineDownloadsDialog(
            entriesState = offlineEntries,
            onDeleteVideo = onDeleteOfflineVideo,
            onDeleteAnime = onDeleteOfflineAnime,
            onDismiss = { downloadsDialogOpen = false },
        )
    }

    if (clearCacheDialogOpen) {
        AlertDialog(
            onDismissRequest = { clearCacheDialogOpen = false },
            title = { Text(uiText("Очистить кэш")) },
            text = {
                Text(uiText("Будут удалены скачанные серии, кэш карточек аниме и локальный прогресс просмотра. Аккаунт и авторизация останутся."))
            },
            confirmButton = {
                DialogActionRow {
                    DialogActionButton(text = uiText("Отмена"), onClick = { clearCacheDialogOpen = false })
                    DialogActionButton(
                        text = uiText("Очистить"),
                        primary = true,
                        onClick = {
                            clearCacheDialogOpen = false
                            onClearAppContentCache()
                        },
                    )
                }
            },
        )
    }

    if (updateDialogOpen) {
        UpdateCheckDialog(
            updateState = updateState,
            onInstallUpdate = { info ->
                updateDialogOpen = false
                UpdateDownloadService.start(context, info.apkUrl, info.version)
            },
            onDismiss = { updateDialogOpen = false },
        )
    }

    if (qualityPickerOpen) {
        SettingsPickerDialog(
            title = uiText("Качество по умолчанию"),
            options = PreferredQuality.entries,
            selected = settings.defaultQuality,
            optionTitle = { it.localizedTitle() },
            onSelected = {
                onSettingsChange(settings.copy(defaultQuality = it))
                qualityPickerOpen = false
            },
            onDismiss = { qualityPickerOpen = false },
        )
    }

    if (decoderPickerOpen) {
        SettingsPickerDialog(
            title = uiText("Декодер"),
            options = PlayerDecoderMode.entries,
            selected = settings.decoderMode,
            optionTitle = { it.localizedTitle() },
            onSelected = {
                onSettingsChange(settings.copy(decoderMode = it))
                decoderPickerOpen = false
            },
            onDismiss = { decoderPickerOpen = false },
        )
    }

    if (cardSizePickerOpen) {
        SettingsPickerDialog(
            title = uiText("Размер карточек"),
            options = PosterCardSize.entries,
            selected = settings.posterCardSize,
            optionTitle = { it.localizedTitle() },
            onSelected = {
                onSettingsChange(settings.copy(posterCardSize = it))
                cardSizePickerOpen = false
            },
            onDismiss = { cardSizePickerOpen = false },
        )
    }

    if (languagePickerOpen) {
        SettingsPickerDialog(
            title = uiText("Язык приложения и контента"),
            options = ContentLanguage.entries,
            selected = settings.contentLanguage,
            optionTitle = { it.localizedTitle() },
            onSelected = {
                onSettingsChange(settings.copy(contentLanguage = it))
                languagePickerOpen = false
            },
            onDismiss = { languagePickerOpen = false },
        )
    }

    if (domainsDialogOpen) {
        SettingsDomainsDialog(
            settings = settings,
            onSettingsChange = onSettingsChange,
            onDismiss = { domainsDialogOpen = false },
        )
    }
}

@Composable
private fun LoadState<List<OfflineAnimeEntry>>.offlineSummary(): String {
    return when (this) {
        LoadState.Loading -> uiText("Загрузка")
        is LoadState.Error -> uiText("Ошибка")
        is LoadState.Ready -> {
            val videos = data.sumOf { it.downloadedVideos.size }
            val bytes = data.sumOf { it.totalBytes }
            if (videos == 0) uiText("Пусто") else "$videos ${localizedEpisodesWord(videos)} • ${formatFileSize(bytes)}"
        }
    }
}

@Composable
private fun OfflineDownloadsDialog(
    entriesState: LoadState<List<OfflineAnimeEntry>>,
    onDeleteVideo: (Long, Long, String?) -> Unit,
    onDeleteAnime: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("Скачанные серии")) },
        text = {
            when (entriesState) {
                LoadState.Loading -> LoadingPane(Modifier.height(160.dp))
                is LoadState.Error -> Text(
                    text = entriesState.message,
                    color = MaterialTheme.colorScheme.error,
                )
                is LoadState.Ready -> {
                    if (entriesState.data.isEmpty()) {
                        Text(uiText("Скачанных серий пока нет"))
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 460.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            lazyItemsIndexed(
                                entriesState.data,
                                key = { index, entry -> "offline-cache:$index:${entry.anime.id}:${entry.anime.title}" },
                            ) { _, entry ->
                                OfflineAnimeCacheCard(
                                    entry = entry,
                                    onDeleteVideo = onDeleteVideo,
                                    onDeleteAnime = onDeleteAnime,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(text = uiText("Закрыть"), primary = true, onClick = onDismiss)
            }
        },
    )
}

@Composable
private fun OfflineAnimeCacheCard(
    entry: OfflineAnimeEntry,
    onDeleteVideo: (Long, Long, String?) -> Unit,
    onDeleteAnime: (Long) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.anime.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${entry.downloadedVideos.size} ${localizedEpisodesWord(entry.downloadedVideos.size)} • ${formatFileSize(entry.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { onDeleteAnime(entry.anime.id) },
                    modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = uiText("Удалить аниме"))
                }
            }
            entry.downloadedVideos.forEach { video ->
                val files = listOf(video).offlineDeleteFiles()
                if (files.isEmpty()) {
                    OfflineDownloadFileRow(
                        title = listOf(video.episodeTitle, video.voiceTitle)
                            .filter { it.isNotBlank() }
                            .joinToString(" • "),
                        size = video.localBytes.takeIf { it > 0L }?.let(::formatFileSize).orEmpty(),
                        onDelete = { onDeleteVideo(entry.anime.id, video.id, null) },
                    )
                } else {
                    files.forEach { item ->
                        OfflineDownloadFileRow(
                            title = listOf(
                                video.episodeTitle,
                                item.file.voiceTitle.ifBlank { video.voiceTitle },
                                item.file.qualityDisplayTitle(),
                            ).filter { it.isNotBlank() }.joinToString(" • "),
                            size = item.file.bytes.takeIf { it > 0L }?.let(::formatFileSize).orEmpty(),
                            onDelete = { onDeleteVideo(entry.anime.id, video.id, item.file.playbackUrl) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineDownloadFileRow(
    title: String,
    size: String,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (size.isNotBlank()) {
            Text(
                text = size,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
        ) {
            Icon(Icons.Default.Close, contentDescription = uiText("Удалить серию"))
        }
    }
}

@Composable
private fun UpdateCheckDialog(
    updateState: LoadState<me.yummydroid.app.data.AppUpdateInfo?>,
    onInstallUpdate: (me.yummydroid.app.data.AppUpdateInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("Обновления")) },
        text = {
            when (updateState) {
                LoadState.Loading -> LoadingPane(Modifier.height(120.dp))
                is LoadState.Error -> Text(
                    text = updateState.message,
                    color = MaterialTheme.colorScheme.error,
                )
                is LoadState.Ready -> {
                    val info = updateState.data
                    if (info == null) {
                        Text(uiText("Проверка еще не выполнена."))
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = info.title.ifBlank { "YummyDroid ${info.version}" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                Text(
                                    text = info.body.ifBlank { uiText("Описание версии пока не добавлено.") },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val info = (updateState as? LoadState.Ready)?.data
            DialogActionRow {
                DialogActionButton(text = uiText("Закрыть"), onClick = onDismiss)
                if (info?.apkUrl?.isNotBlank() == true && info.isNewerThanInstalled()) {
                    DialogActionButton(
                        text = uiText("Обновить"),
                        primary = true,
                        onClick = { onInstallUpdate(info) },
                    )
                }
            }
        },
    )
}

private fun me.yummydroid.app.data.AppUpdateInfo.isNewerThanInstalled(): Boolean {
    fun String.versionParts(): List<Int> = trim()
        .removePrefix("v")
        .split('.', '-', '_')
        .mapNotNull { it.toIntOrNull() }

    val latest = normalizedVersion.versionParts()
    val current = BuildConfig.VERSION_NAME.versionParts()
    val size = maxOf(latest.size, current.size)
    for (index in 0 until size) {
        val left = latest.getOrNull(index) ?: 0
        val right = current.getOrNull(index) ?: 0
        if (left != right) return left > right
    }
    return false
}

private fun formatFileSize(bytes: Long): String {
    return formatByteSize(bytes)
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SettingsActionRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    isPicker: Boolean = false,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .focusRing(shape),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = if (isPicker) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun <T> SettingsPickerDialog(
    title: String,
    options: List<T>,
    selected: T,
    optionTitle: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(options, key = { it.toString() }) { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .dpadClickable(RoundedCornerShape(8.dp)) { onSelected(option) }
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = { onSelected(option) },
                        )
                        Text(
                            text = optionTitle(option),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(
                    text = uiText("Закрыть"),
                    primary = true,
                    onClick = onDismiss,
                )
            }
        },
    )
}

@Composable
private fun DownloadSelectionDialog(
    title: String,
    videos: List<VideoVariant>,
    selectedVideo: VideoVariant?,
    selected: PreferredQuality,
    allEpisodes: Boolean,
    onResolveQualities: suspend (VideoVariant, List<VideoVariant>, Boolean) -> List<PreferredQuality>,
    confirmText: String,
    onConfirm: (VideoVariant, PreferredQuality) -> Unit,
    onDismiss: () -> Unit,
) {
    val voiceOptions = remember(videos, selectedVideo) {
        videos.downloadVoiceOptions(selectedVideo)
    }
    if (voiceOptions.isEmpty()) {
        onDismiss()
        return
    }

    var selectedVoiceKey by remember(voiceOptions, selectedVideo) {
        mutableStateOf(
            selectedVideo?.groupKey?.takeIf { groupKey -> voiceOptions.any { it.groupKey == groupKey } }
                ?: selectedVideo?.voiceKey?.let { voiceKey ->
                    voiceOptions.firstOrNull { it.voiceKey == voiceKey }?.groupKey
                }
                ?: voiceOptions.first().groupKey,
        )
    }
    var selectedQuality by remember(selected) { mutableStateOf(selected) }
    var showQualityStep by remember { mutableStateOf(false) }
    val selectedVoice = voiceOptions.firstOrNull { it.groupKey == selectedVoiceKey } ?: voiceOptions.first()
    var qualityOptions by remember(selectedVoiceKey, videos, allEpisodes) { mutableStateOf<List<PreferredQuality>?>(null) }
    var qualityError by remember(selectedVoiceKey, videos, allEpisodes) { mutableStateOf<String?>(null) }

    LaunchedEffect(showQualityStep, selectedVoiceKey, videos, allEpisodes) {
        if (!showQualityStep) return@LaunchedEffect
        qualityOptions = null
        qualityError = null
        runCatching { onResolveQualities(selectedVoice, videos, allEpisodes) }
            .onSuccess { options ->
                qualityOptions = options
                selectedQuality = options.firstOrNull { it == selected }
                    ?: options.firstOrNull()
                    ?: PreferredQuality.Auto
            }
            .onFailure { throwable ->
                qualityOptions = emptyList()
                qualityError = throwable.message?.takeIf { it.isNotBlank() } ?: "Не удалось проверить качества"
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (showQualityStep) uiText("Качество") else title) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!showQualityStep) {
                    item("voice-hint") {
                        Text(
                            text = uiText("Выбери озвучку"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(voiceOptions, key = { "voice:${it.groupKey}" }) { option ->
                        DialogRadioRow(
                            title = option.voiceTitle,
                            subtitle = option.downloadVoiceSubtitle(videos),
                            downloadedCount = option.downloadedVoiceEpisodeCount(videos),
                            selected = option.groupKey == selectedVoiceKey,
                            onClick = { selectedVoiceKey = option.groupKey },
                        )
                    }
                } else {
                    item("quality-hint") {
                        Text(
                            text = selectedVoice.voiceTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    when {
                        qualityOptions == null -> {
                            item("quality-loading") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                    Text(
                                        text = uiText("Проверка доступных качеств"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        qualityOptions.orEmpty().isEmpty() -> {
                            item("quality-empty") {
                                Text(
                                    text = qualityError ?: uiText("Для выбранной озвучки нет доступных качеств"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(vertical = 12.dp),
                                )
                            }
                        }
                        else -> {
                            items(qualityOptions.orEmpty(), key = { "quality:${it.name}" }) { option ->
                                DialogRadioRow(
                                    title = option.localizedTitle(),
                                    selected = option == selectedQuality,
                                    onClick = { selectedQuality = option },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            DialogActionRow {
                if (showQualityStep) {
                    DialogActionButton(
                        text = uiText("Назад"),
                        onClick = { showQualityStep = false },
                    )
                    DialogActionButton(
                        text = confirmText,
                        primary = true,
                        enabled = qualityOptions.orEmpty().isNotEmpty(),
                        onClick = { onConfirm(selectedVoice, selectedQuality) },
                    )
                } else {
                    DialogActionButton(
                        text = uiText("Отмена"),
                        onClick = onDismiss,
                    )
                    DialogActionButton(
                        text = uiText("Далее"),
                        primary = true,
                        onClick = { showQualityStep = true },
                    )
                }
            }
        },
    )
}

@Composable
private fun DialogRadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    downloadedCount: Int = 0,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .dpadClickable(RoundedCornerShape(8.dp), onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (downloadedCount > 0) {
            DownloadedVoiceBadge(downloadedCount)
        }
    }
}

@Composable
private fun DownloadedVoiceBadge(count: Int) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)),
        shape = RoundedCornerShape(999.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SettingsDomainsDialog(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var newDomain by remember(settings.siteDomains) { mutableStateOf("") }
    var domainError by remember(settings.siteDomains) { mutableStateOf<String?>(null) }
    val invalidDomainText = uiText("Некорректный домен")
    val duplicateDomainText = uiText("Домен уже в списке")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${uiText("Домены сайта")} (${settings.siteDomains.size})") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                    .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(settings.siteDomains, key = { it }) { domain ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = domain.domainDisplayTitle(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    enabled = settings.siteDomains.size > 1,
                                    onClick = {
                                        onSettingsChange(settings.copy(siteDomains = settings.siteDomains - domain))
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .focusRing(RoundedCornerShape(8.dp)),
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = uiText("Удалить домен"))
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = newDomain,
                    onValueChange = {
                        newDomain = it
                        domainError = null
                    },
                    singleLine = true,
                    label = { Text(uiText("Новый домен")) },
                    isError = domainError != null,
                    supportingText = domainError?.let { message -> { Text(message) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(1.dp),
                )
            }
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(
                    text = uiText("Сбросить"),
                    onClick = {
                        newDomain = ""
                        domainError = null
                        onSettingsChange(settings.copy(siteDomains = SiteDomainResolver.DEFAULT_SITE_DOMAINS))
                    },
                )
                DialogActionButton(
                    text = uiText("Закрыть"),
                    onClick = onDismiss,
                )
                DialogActionButton(
                    text = uiText("Добавить"),
                    primary = true,
                    onClick = {
                        val normalized = normalizeSiteBaseUrl(newDomain)
                        when {
                            normalized == null -> domainError = invalidDomainText
                            settings.siteDomains.any { it.trimEnd('/').equals(normalized.trimEnd('/'), ignoreCase = true) } ->
                                domainError = duplicateDomainText
                            else -> {
                                onSettingsChange(
                                    settings.copy(
                                        siteDomains = (settings.siteDomains + normalized).normalizedSiteBaseUrls(),
                                    ),
                                )
                                newDomain = ""
                                domainError = null
                            }
                        }
                    },
                )
            }
        }
    )
}

private fun String.domainDisplayTitle(): String {
    return removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dpadClickable(RoundedCornerShape(8.dp)) { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SettingsSliderRow(
    title: String,
    value: Int,
    valueRange: IntRange,
    onValueChange: (Int) -> Unit,
) {
    val coercedValue = value.coerceIn(valueRange.first, valueRange.last)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = coercedValue.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = coercedValue.toFloat(),
            onValueChange = { raw -> onValueChange(raw.roundToInt().coerceIn(valueRange.first, valueRange.last)) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = (valueRange.count() - 2).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AnimeCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .dpadClickable(RoundedCornerShape(8.dp), onClick),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            PosterImage(
                url = anime.posterUrl,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxSize(),
            )

            if (anime.rating != null || anime.views > 0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    anime.rating?.let { rating ->
                        RatingBadge(rating = rating, modifier = Modifier.widthIn(min = 62.dp))
                    }

                    if (anime.views > 0) {
                        ViewsBadge(
                            views = anime.views,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .widthIn(max = 128.dp),
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = anime.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            )

            Text(
                text = anime.meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp),
            )
        }
    }
}

@Composable
private fun DetailsScreenModern(
    state: YummyDroidUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenAnime: (Long) -> Unit,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    onGenreFilterSelected: (FilterOption) -> Unit,
    onYearFilterSelected: (Int) -> Unit,
    onStudioFilterSelected: (FilterOption) -> Unit,
    onCreatorFilterSelected: (FilterOption) -> Unit,
    onSelectVideoGroup: (String) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    onSelectAnimeListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    onSetAnimeRating: (Int?) -> Unit,
    onAddAnimeComment: (String) -> Unit,
    onLoadMoreAnimeComments: () -> Unit,
    onToggleVideoSubscription: (VideoVariant) -> Unit,
    onResolveDownloadQualities: suspend (VideoVariant, List<VideoVariant>, Boolean) -> List<PreferredQuality>,
    onDownloadVideo: (VideoVariant, PreferredQuality) -> Unit,
    onDownloadAllVideos: (String?, PreferredQuality) -> Unit,
    onDeleteOfflineVideo: (Long, Long, String?) -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        DetailsStateContent(
            state = state.details,
            onRetry = onRefresh,
            emptyMessage = uiText("Карточка не найдена"),
        ) { details ->
            DetailsContentModern(
                details = details,
                settings = state.settings,
                videos = state.videos,
                selectedGroup = state.selectedVideoGroup,
                auth = state.auth,
                animeMark = state.animeMark,
                detailsExtras = state.detailsExtras,
                forcedOfflineMode = state.forcedOfflineMode,
                playbackProgress = state.playbackProgress,
                onBack = onBack,
                onRefresh = onRefresh,
                onOpenAnime = onOpenAnime,
                onOpenLogin = onOpenLogin,
                onOpenProfile = onOpenProfile,
                onGenreFilterSelected = onGenreFilterSelected,
                onYearFilterSelected = onYearFilterSelected,
                onStudioFilterSelected = onStudioFilterSelected,
                onCreatorFilterSelected = onCreatorFilterSelected,
                onSelectVideoGroup = onSelectVideoGroup,
                onPlayVideo = onPlayVideo,
                onPlayVideoAt = onPlayVideoAt,
                onSelectAnimeListMark = onSelectAnimeListMark,
                onToggleFavorite = onToggleFavorite,
                onSetAnimeRating = onSetAnimeRating,
                onAddAnimeComment = onAddAnimeComment,
                onLoadMoreAnimeComments = onLoadMoreAnimeComments,
                onToggleVideoSubscription = onToggleVideoSubscription,
                onResolveDownloadQualities = onResolveDownloadQualities,
                onDownloadVideo = onDownloadVideo,
                onDownloadAllVideos = onDownloadAllVideos,
                onDeleteOfflineVideo = onDeleteOfflineVideo,
                onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
                onRetry = onRefresh,
            )
        }
        if (state.forcedOfflineMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp),
            ) {
                OfflineModeChip()
            }
        }
    }
}

@Composable
private fun DetailsContentModern(
    details: AnimeDetails,
    settings: AppSettings,
    videos: LoadState<List<VideoVariant>>,
    selectedGroup: String?,
    auth: AuthUiState,
    animeMark: LoadState<UserAnimeMark?>,
    detailsExtras: LoadState<AnimeDetailsExtras>,
    forcedOfflineMode: Boolean,
    playbackProgress: PlaybackProgress?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenAnime: (Long) -> Unit,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    onGenreFilterSelected: (FilterOption) -> Unit,
    onYearFilterSelected: (Int) -> Unit,
    onStudioFilterSelected: (FilterOption) -> Unit,
    onCreatorFilterSelected: (FilterOption) -> Unit,
    onSelectVideoGroup: (String) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    onSelectAnimeListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    onSetAnimeRating: (Int?) -> Unit,
    onAddAnimeComment: (String) -> Unit,
    onLoadMoreAnimeComments: () -> Unit,
    onToggleVideoSubscription: (VideoVariant) -> Unit,
    onResolveDownloadQualities: suspend (VideoVariant, List<VideoVariant>, Boolean) -> List<PreferredQuality>,
    onDownloadVideo: (VideoVariant, PreferredQuality) -> Unit,
    onDownloadAllVideos: (String?, PreferredQuality) -> Unit,
    onDeleteOfflineVideo: (Long, Long, String?) -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    onRetry: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isWide = configuration.screenWidthDp >= 900
    val useThreeColumnHero = configuration.screenWidthDp >= 1180
    val compactWideHero = isWide && configuration.screenHeightDp < 560
    val heroHeight = if (!isWide) {
        null
    } else if (compactWideHero) {
        (configuration.screenHeightDp * 0.66f).dp.coerceIn(280.dp, 340.dp)
    } else if (useThreeColumnHero) {
        (configuration.screenHeightDp * 0.54f).dp.coerceIn(340.dp, 430.dp)
    } else {
        (configuration.screenHeightDp * 0.54f).dp.coerceIn(320.dp, 400.dp)
    }
    val readyVideos = (videos as? LoadState.Ready)?.data.orEmpty()
    val playableVideos = remember(readyVideos, forcedOfflineMode) {
        if (forcedOfflineMode) readyVideos.filter { it.isOfflineAvailable } else readyVideos
    }
    val downloadedSummary = readyVideos.downloadedEpisodeSummary()
    val episodeSummary = details.effectiveEpisodeSummary(readyVideos)
    val watchVideo = remember(playableVideos, selectedGroup) {
        playableVideos.heroStartVideo(selectedGroup)
    }
    val resumeTarget = remember(playableVideos, playbackProgress) {
        playbackProgress.resolveResumeTarget(playableVideos)
    }
    val detailsScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(detailsScrollState),
    ) {
        DetailsHeroModern(
            details = details,
            isWide = isWide,
            useThreeColumnHero = useThreeColumnHero,
            watchVideo = watchVideo,
            resumeTarget = resumeTarget,
            downloadVideos = playableVideos,
            downloadedSummary = downloadedSummary,
            episodeSummary = episodeSummary,
            auth = auth,
            animeMark = animeMark,
            detailsExtras = detailsExtras,
            showMarkPanel = isWide && !forcedOfflineMode,
            showHeroRating = isWide && !forcedOfflineMode,
            onOpenLogin = onOpenLogin,
            onOpenProfile = onOpenProfile,
            onSelectListMark = onSelectAnimeListMark,
            onToggleFavorite = onToggleFavorite,
            onSetAnimeRating = onSetAnimeRating,
            onResolveDownloadQualities = onResolveDownloadQualities,
            onBack = onBack,
            onRefresh = onRefresh,
            onPlayVideo = onPlayVideo,
            onPlayVideoAt = onPlayVideoAt,
            defaultDownloadQuality = settings.defaultQuality,
            onDownloadAllVideos = onDownloadAllVideos,
            canDownload = !forcedOfflineMode,
            modifier = Modifier.fillMaxWidth().then(
                if (heroHeight != null) Modifier.height(heroHeight) else Modifier,
            ),
        )

        if (!forcedOfflineMode) {
            DetailsCompactRatingSection(
                extrasState = detailsExtras,
                auth = auth,
                showRating = !isWide,
                onSetAnimeRating = onSetAnimeRating,
            )
            if (!isWide) {
                AnimeMarkPanelModern(
                    auth = auth,
                    animeMark = animeMark,
                    onOpenLogin = onOpenLogin,
                    onOpenProfile = onOpenProfile,
                    onSelectListMark = onSelectAnimeListMark,
                    onToggleFavorite = onToggleFavorite,
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 4.dp)
                        .fillMaxWidth(),
                )
            }
            if (!isWide) {
                DetailsRatingStrip(
                    ratingDetails = details.ratingDetails,
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 4.dp)
                        .fillMaxWidth(),
                )
            }
        }
        DetailsFactsSection(
            details = details,
            onGenreClick = onGenreFilterSelected,
            onYearClick = onYearFilterSelected,
            onStudioClick = onStudioFilterSelected,
            onCreatorClick = onCreatorFilterSelected,
        )
        DetailsScreenshotsSection(
            screenshots = details.screenshots,
            onRegisterInputActionHandler = onRegisterModalInputActionHandler,
        )
        DetailsRelatedAnimeSection(
            relatedAnime = details.relatedAnime,
            onOpenAnime = onOpenAnime,
        )
        if (!forcedOfflineMode) {
            DetailsExtrasTopSection(
                extrasState = detailsExtras,
                auth = auth,
                videos = readyVideos,
                onSetAnimeRating = onSetAnimeRating,
                onToggleVideoSubscription = onToggleVideoSubscription,
            )
        }

        when (videos) {
            LoadState.Loading -> LoadingPane(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp),
            )
            is LoadState.Error -> ErrorPane(
                message = videos.message,
                onRetry = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp),
            )
            is LoadState.Ready -> VideoPickerModern(
                videos = videos.data,
                selectedGroup = selectedGroup,
                onSelectGroup = onSelectVideoGroup,
                onPlayVideo = onPlayVideo,
                onResolveDownloadQualities = onResolveDownloadQualities,
                onDownloadVideo = onDownloadVideo,
                onDeleteOfflineVideo = onDeleteOfflineVideo,
                defaultDownloadQuality = settings.defaultQuality,
                forcedOfflineMode = forcedOfflineMode,
                canDownload = !forcedOfflineMode,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!forcedOfflineMode) {
            DetailsSubscriptionsHostSection(
                extrasState = detailsExtras,
                auth = auth,
                videos = readyVideos,
                allowSubscriptions = details.canShowVideoSubscriptions(),
                onToggleVideoSubscription = onToggleVideoSubscription,
            )
            DetailsRecommendationsSection(
                extrasState = detailsExtras,
                onOpenAnime = onOpenAnime,
            )
            DetailsCommentsHostSection(
                extrasState = detailsExtras,
                isAuthorized = auth.profile != null,
                scrollState = detailsScrollState,
                onAddAnimeComment = onAddAnimeComment,
                onLoadMoreAnimeComments = onLoadMoreAnimeComments,
            )
        }
    }
}

@Composable
private fun DetailsHeroModern(
    details: AnimeDetails,
    isWide: Boolean,
    useThreeColumnHero: Boolean,
    watchVideo: VideoVariant?,
    resumeTarget: HeroResumeTarget?,
    downloadVideos: List<VideoVariant>,
    downloadedSummary: String?,
    episodeSummary: String,
    auth: AuthUiState,
    animeMark: LoadState<UserAnimeMark?>,
    modifier: Modifier = Modifier,
    detailsExtras: LoadState<AnimeDetailsExtras> = LoadState.Ready(AnimeDetailsExtras()),
    showMarkPanel: Boolean,
    showHeroRating: Boolean = false,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    onSelectListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    onSetAnimeRating: (Int?) -> Unit = {},
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    defaultDownloadQuality: PreferredQuality,
    onResolveDownloadQualities: suspend (VideoVariant, List<VideoVariant>, Boolean) -> List<PreferredQuality>,
    onDownloadAllVideos: (String?, PreferredQuality) -> Unit,
    canDownload: Boolean,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
        details.backdropUrl?.let { backdrop ->
            PosterImage(
                url = backdrop,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (isWide) 1f else 0.72f
                    },
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (isWide) 0.18f else 0.36f)),
            )
            if (!isWide) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.42f),
                                    Color.Black.copy(alpha = 0.28f),
                                    Color.Black.copy(alpha = 0.50f),
                                ),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.36f),
                                    Color.Black.copy(alpha = 0.28f),
                                    Color.Black.copy(alpha = 0.52f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.86f),
                                ),
                            ),
                        ),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = if (isWide) {
                                listOf(
                                    Color.Black.copy(alpha = 0.32f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                                    MaterialTheme.colorScheme.background,
                                )
                            } else {
                                listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.32f),
                                    Color.Black.copy(alpha = 0.24f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.74f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                                    MaterialTheme.colorScheme.background,
                                )
                            },
                        ),
                    ),
            )
        }
        if (!isWide) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF050912).copy(alpha = 0.10f)),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .zIndex(20f)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FloatingHeroButton(onClick = onBack, compact = isWide) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            FloatingHeroButton(onClick = onRefresh, compact = isWide) {
                Icon(Icons.Default.Refresh, contentDescription = "Обновить")
            }
        }

        if (isWide) {
            val screenWidthDp = LocalConfiguration.current.screenWidthDp
            val screenHeightDp = LocalConfiguration.current.screenHeightDp
            val compactWideHero = screenHeightDp < 560
            val posterWidth = when {
                compactWideHero -> 92.dp
                useThreeColumnHero -> 128.dp
                else -> 120.dp
            }
            val sidePanelWidth = when {
                compactWideHero -> 292.dp
                screenWidthDp >= 1280 -> 368.dp
                screenWidthDp >= 1100 -> 340.dp
                else -> 300.dp
            }
            val horizontalGap = if (compactWideHero) 10.dp else 14.dp
            val topPadding = if (compactWideHero) 38.dp else 48.dp
            val bottomPadding = if (compactWideHero) 8.dp else 12.dp
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = topPadding, end = 20.dp, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(if (compactWideHero) 5.dp else 9.dp),
            ) {
                DetailsHeroWideHeading(
                    details = details,
                    compact = compactWideHero,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 76.dp, end = 76.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(horizontalGap),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    DetailsPoster(
                        posterUrl = details.posterUrl,
                        title = details.title,
                        modifier = Modifier.width(posterWidth),
                    )
                    DetailsHeroText(
                        details = details,
                        compact = compactWideHero,
                        showHeading = false,
                        showGenres = false,
                        downloadedSummary = downloadedSummary,
                        episodeSummary = episodeSummary,
                        watchVideo = watchVideo,
                        resumeTarget = resumeTarget,
                        downloadVideos = downloadVideos,
                        auth = auth,
                        animeMark = animeMark,
                        detailsExtras = detailsExtras,
                        showMarkPanel = false,
                        showHeroRating = false,
                        onOpenLogin = onOpenLogin,
                        onOpenProfile = onOpenProfile,
                        onSelectListMark = onSelectListMark,
                        onToggleFavorite = onToggleFavorite,
                        onSetAnimeRating = onSetAnimeRating,
                        onPlayVideo = onPlayVideo,
                        onPlayVideoAt = onPlayVideoAt,
                        defaultDownloadQuality = defaultDownloadQuality,
                        onResolveDownloadQualities = onResolveDownloadQualities,
                        onDownloadAllVideos = onDownloadAllVideos,
                        canDownload = canDownload,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(max = if (compactWideHero) 128.dp else 176.dp),
                    )
                    DetailsHeroSidePanel(
                        ratingDetails = details.ratingDetails,
                        auth = auth,
                        animeMark = animeMark,
                        detailsExtras = detailsExtras,
                        showMarkPanel = showMarkPanel,
                        showHeroRating = showHeroRating,
                        onOpenLogin = onOpenLogin,
                        onOpenProfile = onOpenProfile,
                        onSelectListMark = onSelectListMark,
                        onToggleFavorite = onToggleFavorite,
                        onSetAnimeRating = onSetAnimeRating,
                        modifier = Modifier
                            .width(sidePanelWidth)
                            .heightIn(max = if (compactWideHero) 150.dp else 176.dp),
                    )
                }
            }
        } else {
            DetailsHeroMobile(
                details = details,
                watchVideo = watchVideo,
                resumeTarget = resumeTarget,
                downloadedSummary = downloadedSummary,
                episodeSummary = episodeSummary,
                downloadVideos = downloadVideos,
                onPlayVideo = onPlayVideo,
                onPlayVideoAt = onPlayVideoAt,
                defaultDownloadQuality = defaultDownloadQuality,
                onResolveDownloadQualities = onResolveDownloadQualities,
                onDownloadAllVideos = onDownloadAllVideos,
                canDownload = canDownload,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(Color(0xFF050912).copy(alpha = 0.14f))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF050912).copy(alpha = 0.42f),
                                Color(0xFF050912).copy(alpha = 0.30f),
                                Color.Black.copy(alpha = 0.62f),
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    )
                    .padding(start = 18.dp, top = 66.dp, end = 18.dp, bottom = 14.dp),
            )
        }
    }
}

@Composable
private fun DetailsHeroWideHeading(
    details: AnimeDetails,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
    ) {
        Text(
            text = details.title,
            style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            maxLines = if (compact) 2 else 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (details.meta.isNotBlank()) {
            Text(
                text = details.meta,
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FloatingHeroButton(
    onClick: () -> Unit,
    compact: Boolean = false,
    content: @Composable () -> Unit,
) {
    val size = if (compact) 44.dp else 52.dp
    Surface(
        color = Color(0xE6121825),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 4.dp,
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(size)
                .focusRing(RoundedCornerShape(8.dp)),
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnimeMarkPanelModern(
    auth: AuthUiState,
    animeMark: LoadState<UserAnimeMark?>,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    onSelectListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile = auth.profile
    val mark = (animeMark as? LoadState.Ready)?.data ?: UserAnimeMark()

    if (profile == null) {
        Box(modifier = modifier) {
        DialogActionButton(
            text = uiText("Войти"),
                primary = true,
                onClick = onOpenLogin,
                enabled = !auth.loading,
            )
        }
        return
    }

    AnimeMarkSegmentedControl(
        mark = mark,
        onSelectListMark = onSelectListMark,
        onToggleFavorite = onToggleFavorite,
        modifier = modifier,
    )
}

@Composable
private fun AnimeMarkSegmentedControl(
    mark: UserAnimeMark,
    onSelectListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = modifier.widthIn(max = 392.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)),
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .height(48.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val listMarks = UserAnimeListMark.displayOrder
            val totalMarks = listMarks.size + 1
            listMarks.forEachIndexed { index, listMark ->
                AnimeMarkSegment(
                    icon = listMark.icon(),
                    title = listMark.title,
                    color = listMark.siteColor(),
                    selected = mark.list == listMark,
                    onClick = { onSelectListMark(listMark) },
                    index = index,
                    total = totalMarks,
                    modifier = Modifier.weight(1f),
                )
                MarkDivider()
            }
            AnimeMarkSegment(
                icon = Icons.Default.Favorite,
                title = uiText("Любимые"),
                color = favoriteMarkColor,
                selected = mark.isFavorite,
                onClick = onToggleFavorite,
                index = totalMarks - 1,
                total = totalMarks,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DetailsHeroSidePanel(
    ratingDetails: RatingDetails,
    auth: AuthUiState,
    animeMark: LoadState<UserAnimeMark?>,
    detailsExtras: LoadState<AnimeDetailsExtras>,
    showMarkPanel: Boolean,
    showHeroRating: Boolean,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    onSelectListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    onSetAnimeRating: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showHeroRating && detailsExtras is LoadState.Ready) {
                CompactRatingScale(
                    rating = detailsExtras.data.rating,
                    isAuthorized = auth.profile != null,
                    onSetAnimeRating = onSetAnimeRating,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (showMarkPanel) {
                AnimeMarkPanelModern(
                    auth = auth,
                    animeMark = animeMark,
                    onOpenLogin = onOpenLogin,
                    onOpenProfile = onOpenProfile,
                    onSelectListMark = onSelectListMark,
                    onToggleFavorite = onToggleFavorite,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            DetailsRatingStrip(
                ratingDetails = ratingDetails,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CompactRatingScale(
    rating: AnimeRatingSummary,
    isAuthorized: Boolean,
    onSetAnimeRating: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isAuthorized) return
    RatingScale(
        selected = rating.userRating,
        onSelected = onSetAnimeRating,
        modifier = modifier,
    )
}

@Composable
private fun AnimeMarkSegment(
    icon: ImageVector,
    title: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = -1,
    total: Int = 0,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .stopHorizontalFocusEscape(index, total)
            .background(if (selected) color else Color.Transparent)
            .focusRing(shape)
            .dpadClickable(shape, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (selected) Color.White else color,
            modifier = Modifier.size(23.dp),
        )
    }
}

@Composable
private fun MarkDivider() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
    )
}

private fun UserProfile.siteProfileUrl(siteBaseUrl: String): String {
    val base = siteBaseUrl.trim().ifBlank { "https://old.yummyani.me/" }.trimEnd('/')
    return "$base/users/id$id"
}

private fun sitePageUrl(siteBaseUrl: String, path: String): String {
    val base = siteBaseUrl.trim().ifBlank { "https://old.yummyani.me/" }.trimEnd('/')
    return "$base/${path.trim().trimStart('/')}"
}

private fun Context.openUrl(url: String) {
    val normalized = url.trim()
    if (normalized.isBlank()) return
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
    }.onFailure {
        Toast.makeText(this, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun DetailsHeroMobile(
    details: AnimeDetails,
    watchVideo: VideoVariant?,
    resumeTarget: HeroResumeTarget?,
    downloadedSummary: String?,
    episodeSummary: String,
    downloadVideos: List<VideoVariant>,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    defaultDownloadQuality: PreferredQuality,
    onResolveDownloadQualities: suspend (VideoVariant, List<VideoVariant>, Boolean) -> List<PreferredQuality>,
    onDownloadAllVideos: (String?, PreferredQuality) -> Unit,
    canDownload: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            text = details.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            maxLines = when {
                details.title.length > 90 -> 6
                details.title.length > 55 -> 5
                else -> 3
            },
            overflow = TextOverflow.Clip,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            DetailsPoster(
                posterUrl = details.posterUrl,
                title = details.title,
                modifier = Modifier.width(112.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DetailsHeroMeta(
                    details = details,
                    compact = true,
                    downloadedSummary = downloadedSummary,
                    episodeSummary = episodeSummary,
                    modifier = Modifier.fillMaxWidth(),
                )
                DetailsHeroActions(
                    watchVideo = watchVideo,
                    resumeTarget = resumeTarget,
                    downloadVideos = downloadVideos,
                    onPlayVideo = onPlayVideo,
                    onPlayVideoAt = onPlayVideoAt,
                    defaultDownloadQuality = defaultDownloadQuality,
                    onResolveDownloadQualities = onResolveDownloadQualities,
                    onDownloadAllVideos = onDownloadAllVideos,
                    canDownload = canDownload,
                )
            }
        }
    }
}

@Composable
private fun DetailsHeroMeta(
    details: AnimeDetails,
    compact: Boolean,
    downloadedSummary: String?,
    episodeSummary: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (details.meta.isNotBlank()) {
            Text(
                text = details.meta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (compact) 3 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            details.rating?.let { rating ->
                AssistChip(
                    onClick = {},
                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = { Text(formatRating(rating)) },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                )
            }
            AssistChip(
                onClick = {},
                leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text(formatViews(details.views)) },
            )
        }

        if (episodeSummary.isNotBlank()) {
            Text(
                text = episodeSummary,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        downloadedSummary?.let { summary ->
            Text(
                text = summary,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DetailsHeroText(
    details: AnimeDetails,
    compact: Boolean,
    modifier: Modifier = Modifier,
    showHeading: Boolean = true,
    showGenres: Boolean = true,
    downloadedSummary: String? = null,
    episodeSummary: String = details.episodeSummary,
    watchVideo: VideoVariant? = null,
    resumeTarget: HeroResumeTarget? = null,
    downloadVideos: List<VideoVariant> = emptyList(),
    auth: AuthUiState = AuthUiState(),
    animeMark: LoadState<UserAnimeMark?> = LoadState.Ready(null),
    detailsExtras: LoadState<AnimeDetailsExtras> = LoadState.Ready(AnimeDetailsExtras()),
    showMarkPanel: Boolean = false,
    showHeroRating: Boolean = false,
    onOpenLogin: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onSelectListMark: (UserAnimeListMark) -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onSetAnimeRating: (Int?) -> Unit = {},
    onPlayVideo: (VideoVariant) -> Unit = {},
    onPlayVideoAt: (VideoVariant, Long) -> Unit = { _, _ -> },
    defaultDownloadQuality: PreferredQuality = PreferredQuality.Auto,
    onResolveDownloadQualities: suspend (VideoVariant, List<VideoVariant>, Boolean) -> List<PreferredQuality> = { _, _, _ -> emptyList() },
    onDownloadAllVideos: (String?, PreferredQuality) -> Unit = { _, _ -> },
    canDownload: Boolean = true,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 10.dp),
    ) {
        if (showHeading) {
            Text(
                text = details.title,
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                maxLines = if (compact) 3 else 5,
                overflow = TextOverflow.Clip,
            )

            if (details.meta.isNotBlank()) {
                Text(
                    text = details.meta,
                    style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
            details.rating?.let { rating ->
                AssistChip(
                    onClick = {},
                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = { Text(formatRating(rating)) },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                )
            }
            AssistChip(
                onClick = {},
                leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text(formatViews(details.views)) },
            )
        }

        if (showGenres && details.genres.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(details.genres.take(if (compact) 6 else 12)) { genre ->
                    AssistChip(onClick = {}, label = { Text(genre) })
                }
            }
        }

        if (episodeSummary.isNotBlank()) {
            Text(
                text = episodeSummary,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        downloadedSummary?.let { summary ->
            Text(
                text = summary,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (showMarkPanel) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DetailsHeroActions(
                    watchVideo = watchVideo,
                    resumeTarget = resumeTarget,
                    downloadVideos = downloadVideos,
                    onPlayVideo = onPlayVideo,
                    onPlayVideoAt = onPlayVideoAt,
                    defaultDownloadQuality = defaultDownloadQuality,
                    onResolveDownloadQualities = onResolveDownloadQualities,
                    onDownloadAllVideos = onDownloadAllVideos,
                    canDownload = canDownload,
                )
                AnimeMarkPanelModern(
                    auth = auth,
                    animeMark = animeMark,
                    onOpenLogin = onOpenLogin,
                    onOpenProfile = onOpenProfile,
                    onSelectListMark = onSelectListMark,
                    onToggleFavorite = onToggleFavorite,
                    modifier = Modifier.widthIn(max = 392.dp),
                )
            }
        } else {
            DetailsHeroActions(
                watchVideo = watchVideo,
                resumeTarget = resumeTarget,
                downloadVideos = downloadVideos,
                onPlayVideo = onPlayVideo,
                onPlayVideoAt = onPlayVideoAt,
                defaultDownloadQuality = defaultDownloadQuality,
                onResolveDownloadQualities = onResolveDownloadQualities,
                onDownloadAllVideos = onDownloadAllVideos,
                canDownload = canDownload,
            )
        }

        if (showHeroRating && detailsExtras is LoadState.Ready) {
            DetailsRatingSection(
                rating = detailsExtras.data.rating,
                isAuthorized = auth.profile != null,
                onSetAnimeRating = onSetAnimeRating,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsHeroActions(
    watchVideo: VideoVariant?,
    resumeTarget: HeroResumeTarget?,
    downloadVideos: List<VideoVariant>,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    defaultDownloadQuality: PreferredQuality,
    onResolveDownloadQualities: suspend (VideoVariant, List<VideoVariant>, Boolean) -> List<PreferredQuality>,
    onDownloadAllVideos: (String?, PreferredQuality) -> Unit,
    canDownload: Boolean,
) {
    if (watchVideo == null) return
    var downloadDialogOpen by remember { mutableStateOf(false) }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (resumeTarget != null) {
            DialogActionButton(
                text = uiText("Продолжить"),
                primary = true,
                onClick = { onPlayVideoAt(resumeTarget.video, resumeTarget.positionMs) },
            )
        } else {
            DialogActionButton(
                text = uiText("Смотреть"),
                primary = true,
                onClick = { onPlayVideo(watchVideo) },
            )
        }
        if (canDownload && downloadVideos.isNotEmpty()) {
            DialogActionButton(
                text = uiText("Скачать всё"),
                onClick = { downloadDialogOpen = true },
            )
        }
    }

    if (downloadDialogOpen) {
        DownloadSelectionDialog(
            title = uiText("Скачать все серии"),
            videos = downloadVideos,
            selectedVideo = resumeTarget?.video ?: watchVideo,
            selected = defaultDownloadQuality,
            allEpisodes = true,
            onResolveQualities = onResolveDownloadQualities,
            confirmText = uiText("Скачать"),
            onConfirm = { voiceVideo, quality ->
                downloadDialogOpen = false
                onDownloadAllVideos(voiceVideo.groupKey, quality)
            },
            onDismiss = { downloadDialogOpen = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsFactsSection(
    details: AnimeDetails,
    onGenreClick: (FilterOption) -> Unit,
    onYearClick: (Int) -> Unit,
    onStudioClick: (FilterOption) -> Unit,
    onCreatorClick: (FilterOption) -> Unit,
) {
    var expanded by rememberSaveable(details.id) { mutableStateOf(false) }
    val description = details.description.trim()
    val facts = buildList<Pair<String, String>> {
        add("Тип" to details.type)
        add("Ограничение" to details.minAge)
        add("Статус" to details.status)
        details.year?.let { add("Год выхода" to it.toString()) }
        if (details.otherTitles.isNotEmpty()) add("Альт. названия" to details.otherTitles.take(4).joinToString(" | "))
        details.original.takeIf { it.isPresentFactValue() }?.let { add("Первоисточник" to it) }
        if (details.studios.isNotEmpty()) add("Студия" to details.studios.joinToString { it.title })
        if (details.creators.isNotEmpty()) add("Режиссёр" to details.creators.joinToString { it.title })
        if (details.genreTags.isNotEmpty()) add("Жанры" to details.genreTags.joinToString { it.title })
        details.nextEpisodeText.takeIf { it.isPresentFactValue() }?.let { add("До выхода" to it) }
        details.durationSeconds.takeIf { it > 0 }?.let { seconds ->
            formatDuration(seconds)?.let { add("Длительность" to it) }
        }
        details.commentsCount.takeIf { it > 0L }?.let { add("Комментарии" to formatViews(it)) }
        details.listsCount.takeIf { it > 0L }?.let { add("В списках" to formatViews(it)) }
    }.filter { (_, value) -> value.isPresentFactValue() }

    if (facts.isEmpty() && description.isBlank()) return
    val shape = RoundedCornerShape(8.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.46f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRing(shape)
                    .dpadClickable(shape) { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = uiText("Описание"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    facts.forEach { (label, value) ->
                        DetailsFactRow(label = uiText(label)) {
                            when (label) {
                                "Год выхода" -> details.year?.let { year ->
                                    ClickableFactText(text = value, onClick = { onYearClick(year) })
                                }
                                "Студия" -> FactChips(options = details.studios, onClick = onStudioClick)
                                "Режиссёр" -> FactChips(options = details.creators, onClick = onCreatorClick)
                                "Жанры" -> FactChips(options = details.genreTags, onClick = onGenreClick)
                                else -> Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 5,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    if (description.isNotBlank()) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = if (facts.isEmpty()) 0.dp else 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsRatingStrip(
    ratingDetails: RatingDetails,
    modifier: Modifier = Modifier,
) {
    val entries = buildList {
        ratingDetails.myAnimeList?.let { add("MAL" to formatRating(it)) }
        ratingDetails.shikimori?.let { add("Шики" to formatRating(it)) }
        ratingDetails.kinopoisk?.let { add("КП" to formatRating(it)) }
        ratingDetails.worldArt?.let { add("WA" to formatRating(it)) }
        ratingDetails.aniDub?.let { add("AniDUB" to formatRating(it)) }
    }
    if (entries.isEmpty()) return

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        lazyItemsIndexed(entries, key = { index, entry -> "rating-strip:$index:${entry.first}" }) { index, entry ->
            val (label, value) = entry
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        text = "$label $value",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier.stopHorizontalFocusEscape(index, entries.size),
            )
        }
    }
}

@Composable
private fun DetailsFactRow(
    label: String,
    content: @Composable () -> Unit,
) {
    val labelWidth = if (LocalConfiguration.current.screenWidthDp < 430) 116.dp else 156.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(labelWidth),
        )
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

private fun String.isPresentFactValue(): Boolean {
    val normalized = trim()
    return normalized.isNotBlank() &&
        !normalized.equals("unknown", ignoreCase = true) &&
        !normalized.equals("null", ignoreCase = true) &&
        normalized != "-" &&
        normalized != "—"
}

@Composable
private fun ClickableFactText(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.dpadClickable(RoundedCornerShape(6.dp), onClick),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FactChips(
    options: List<FilterOption>,
    onClick: (FilterOption) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val shape = RoundedCornerShape(8.dp)
            Surface(
                modifier = Modifier
                    .widthIn(max = 236.dp)
                    .focusRing(shape)
                    .dpadClickable(shape) { onClick(option) },
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)),
                shape = shape,
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsScreenshotsSection(
    screenshots: List<String>,
    onRegisterInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
) {
    if (screenshots.isEmpty()) return
    val visibleScreenshots = remember(screenshots) { screenshots.take(24) }
    var selectedIndex by remember(visibleScreenshots) { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = uiText("Кадры"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            lazyItemsIndexed(
                visibleScreenshots,
                key = { index, screenshot -> "screenshot:$index:$screenshot" },
            ) { index, screenshot ->
                val shape = RoundedCornerShape(8.dp)
                PosterImage(
                    url = screenshot,
                    contentDescription = null,
                    modifier = Modifier
                        .width(320.dp)
                        .aspectRatio(16f / 9f)
                        .stopHorizontalFocusEscape(index, visibleScreenshots.size)
                        .dpadClickable(shape) { selectedIndex = index },
                )
            }
        }
    }

    selectedIndex?.let { index ->
        ScreenshotViewerDialog(
            screenshots = visibleScreenshots,
            initialIndex = index,
            onDismiss = { selectedIndex = null },
            onRegisterInputActionHandler = onRegisterInputActionHandler,
        )
    }
}

@Composable
private fun ScreenshotViewerDialog(
    screenshots: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onRegisterInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
) {
    if (screenshots.isEmpty()) return
    var currentIndex by remember(screenshots, initialIndex) {
        mutableIntStateOf(initialIndex.coerceIn(0, screenshots.lastIndex))
    }
    var scale by remember(currentIndex) { mutableFloatStateOf(1f) }
    var offset by remember(currentIndex) { mutableStateOf(Offset.Zero) }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    var isClosing by remember { mutableStateOf(false) }

    fun closeViewer() {
        if (!isClosing) {
            isClosing = true
            onDismiss()
        }
    }

    fun showPrevious() {
        if (currentIndex > 0) currentIndex -= 1
    }

    fun showNext() {
        if (currentIndex < screenshots.lastIndex) currentIndex += 1
    }

    val inputActionHandler by rememberUpdatedState { action: InputAction ->
        when (action) {
            InputAction.Back,
            InputAction.Up,
            InputAction.Down -> {
                closeViewer()
                true
            }
            InputAction.Left -> {
                showPrevious()
                true
            }
            InputAction.Right -> {
                showNext()
                true
            }
            InputAction.Confirm,
            InputAction.Play,
            InputAction.Pause,
            InputAction.PlayPause,
            InputAction.PreviousEpisode,
            InputAction.NextEpisode -> false
        }
    }

    DisposableEffect(Unit) {
        onRegisterInputActionHandler { action -> inputActionHandler(action) }
        onDispose { onRegisterInputActionHandler(null) }
    }

    fun handleSwipe(delta: Offset) {
        val absX = kotlin.math.abs(delta.x)
        val absY = kotlin.math.abs(delta.y)
        when {
            absY > 90f && absY > absX -> closeViewer()
            absX > 90f && absX > absY && delta.x > 0f -> showPrevious()
            absX > 90f && absX > absY && delta.x < 0f -> showNext()
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = ::closeViewer,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .navigationBarsPadding()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> {
                            showPrevious()
                            true
                        }
                        Key.DirectionRight -> {
                            showNext()
                            true
                        }
                        Key.DirectionUp,
                        Key.DirectionDown -> {
                            closeViewer()
                            true
                        }
                        Key.Escape,
                        Key.NavigateOut -> {
                            closeViewer()
                            true
                        }
                        else -> false
                    }
                },
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(screenshots[currentIndex])
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentIndex, scale) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var totalPan = Offset.Zero
                            var usedTransform = false
                            do {
                                val event = awaitPointerEvent()
                                val pan = event.calculatePan()
                                val zoom = event.calculateZoom()
                                val pointerCount = event.changes.count { it.pressed }
                                val transformGesture = pointerCount > 1 ||
                                    scale > 1.01f ||
                                    kotlin.math.abs(zoom - 1f) > 0.01f

                                if (transformGesture) {
                                    usedTransform = true
                                    val nextScale = (scale * zoom).coerceIn(1f, 5f)
                                    val maxX = size.width * (nextScale - 1f) / 2f
                                    val maxY = size.height * (nextScale - 1f) / 2f
                                    scale = nextScale
                                    offset = if (nextScale <= 1.01f) {
                                        Offset.Zero
                                    } else {
                                        Offset(
                                            x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                            y = (offset.y + pan.y).coerceIn(-maxY, maxY),
                                        )
                                    }
                                } else {
                                    totalPan += pan
                                }
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })

                            if (!usedTransform && scale <= 1.01f) {
                                handleSwipe(totalPan)
                            }
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.56f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${currentIndex + 1} / ${screenshots.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
        }
    }
}

private data class HeroResumeTarget(
    val video: VideoVariant,
    val positionMs: Long,
)

@Composable
private fun List<VideoVariant>.downloadedEpisodeSummary(): String? {
    val allEpisodes = distinctBy { it.episodeSlotKey }
    val downloaded = filter { it.isOfflineAvailable }
        .distinctBy { it.episodeSlotKey }
        .sortedWith(compareBy<VideoVariant> { it.episodeOrderValue() ?: Double.MAX_VALUE }.thenBy { it.index })
    if (downloaded.isEmpty()) return null

    return if (allEpisodes.isNotEmpty() && downloaded.size >= allEpisodes.size) {
        "${uiText("Загружено")} ${downloaded.size}"
    } else {
        val episodeWord = uiText("серия")
        val labels = downloaded.joinToString(", ") { it.shortEpisodeLabel(episodeWord) }
        "${uiText("Загружено")}: $labels"
    }
}

@Composable
private fun AnimeDetails.effectiveEpisodeSummary(videos: List<VideoVariant>): String {
    val actualEpisodes = remember(videos) {
        val bySlot = videos.map { it.episodeSlotKey }
            .filter { it.isNotBlank() }
            .distinct()
            .size
        if (bySlot > 0) {
            bySlot
        } else {
            videos.distinctBy { variant ->
                variant.episode.takeIf { it.isNotBlank() } ?: variant.index.toString()
            }.size
        }
    }
    val aired = if (episodeCount > 0) {
        maxOf(episodeAired, actualEpisodes).coerceAtMost(episodeCount)
    } else {
        maxOf(episodeAired, actualEpisodes)
    }
    return when {
        aired > 0 && episodeCount > 0 -> "${uiText("Вышло")} $aired ${uiText("из")} $episodeCount"
        aired > 0 -> "${uiText("Вышло")} $aired"
        episodeSummary.isNotBlank() -> episodeSummary
        episodeCount > 0 -> "$episodeCount ${localizedEpisodesWord(episodeCount)}"
        else -> ""
    }
}

private fun VideoVariant.shortEpisodeLabel(episodeWord: String): String {
    return episode.takeIf { it.isNotBlank() }?.let { "$episodeWord $it" } ?: episodeTitle.lowercase(Locale.ROOT)
}

private fun VideoVariant.episodeOrderValue(): Double? {
    return episode.trim().replace(',', '.').toDoubleOrNull()
}

private fun List<VideoVariant>.downloadVoiceOptions(selectedVideo: VideoVariant?): List<VideoVariant> {
    return groupBy { it.voiceKey }
        .values
        .mapNotNull { group ->
            group.minWithOrNull(
                compareBy<VideoVariant> { if (selectedVideo != null && it.groupKey == selectedVideo.groupKey) 0 else 1 }
                    .thenBy { it.playerPriority() }
                    .thenByDescending { it.isOfflineAvailable }
                    .thenBy { it.index }
                    .thenBy { it.id },
            )
        }
        .sortedWith(compareBy<VideoVariant> { if (selectedVideo != null && it.voiceKey == selectedVideo.voiceKey) 0 else 1 }.thenBy { it.voiceTitle })
}

private fun List<VideoVariant>.downloadEpisodeCandidates(video: VideoVariant): List<VideoVariant> {
    return filter { it.sameEpisodeSlot(video) }.ifEmpty { listOf(video) }
}

@Composable
private fun VideoVariant.downloadVoiceSubtitle(videos: List<VideoVariant>): String {
    val count = videos
        .asSequence()
        .filter { it.voiceKey == voiceKey }
        .map { it.episodeSlotKey }
        .distinct()
        .count()
        .coerceAtLeast(1)
    return "$count ${localizedEpisodesWord(count)}"
}

private fun VideoVariant.downloadedVoiceEpisodeCount(videos: List<VideoVariant>): Int {
    return downloadedEpisodeCountForVoice(videos)
}

private fun List<VideoVariant>.heroStartVideo(selectedGroup: String?): VideoVariant? {
    if (isEmpty()) return null
    val preferredGroup = selectedGroup?.takeIf { groupKey -> any { it.groupKey == groupKey } }
    return sortedForPlayer(preferredGroup).firstOrNull()
        ?: sortedForPlayer().firstOrNull()
}

private fun PlaybackProgress?.resolveResumeTarget(videos: List<VideoVariant>): HeroResumeTarget? {
    val progress = this ?: return null
    if (progress.positionMs <= 0L || videos.isEmpty()) return null
    val video = videos.firstOrNull { it.id == progress.videoId }
        ?: videos.firstOrNull { candidate ->
            progress.groupKey.isNotBlank() &&
                candidate.groupKey == progress.groupKey &&
                candidate.episode == progress.episode
        }
        ?: videos.firstOrNull { candidate -> candidate.episode.matchesProgressEpisode(progress.episode) }
        ?: return null

    val durationMs = progress.durationMs.takeIf { it > 0L }
    val safePosition = if (durationMs != null) {
        progress.positionMs.coerceIn(0L, (durationMs - 5_000L).coerceAtLeast(0L))
    } else {
        progress.positionMs.coerceAtLeast(0L)
    }
    if (safePosition <= 0L) return null
    return HeroResumeTarget(video, safePosition)
}

private fun String.matchesProgressEpisode(progressEpisode: String): Boolean {
    val current = trim()
    val saved = progressEpisode.trim()
    if (current == saved) return true
    val currentNumber = current.replace(',', '.').toDoubleOrNull()
    val savedNumber = saved.replace(',', '.').toDoubleOrNull()
    return currentNumber != null && savedNumber != null && currentNumber == savedNumber
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsRelatedAnimeSection(
    relatedAnime: List<RelatedAnime>,
    onOpenAnime: (Long) -> Unit,
) {
    if (relatedAnime.isEmpty()) return
    var expanded by remember(relatedAnime) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .dpadClickable(RoundedCornerShape(8.dp)) { expanded = !expanded },
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.76f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(8.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text = uiText("Порядок просмотра"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (expanded) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    relatedAnime.forEachIndexed { index, related ->
                        RelatedAnimeOrderRow(
                            index = index + 1,
                            relatedAnime = related,
                            onClick = { onOpenAnime(related.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RelatedAnimeOrderRow(
    index: Int,
    relatedAnime: RelatedAnime,
    onClick: () -> Unit,
) {
    val isCompact = LocalConfiguration.current.screenWidthDp < 680
    val titleColor = if (relatedAnime.isCurrent) {
        Color(0xFF48D882)
    } else {
        MaterialTheme.colorScheme.primary
    }
    val meta = listOfNotNull(
        relatedAnime.type.takeIf { it.isNotBlank() },
        relatedAnime.relation.takeIf { it.isNotBlank() },
        relatedAnime.year?.toString(),
    ).joinToString(", ")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .dpadClickable(RoundedCornerShape(8.dp), onClick),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "$index.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(34.dp),
            )
            if (isCompact) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = relatedAnime.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = titleColor,
                    )
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Text(
                    text = relatedAnime.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor,
                    modifier = Modifier.weight(1.3f),
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            relatedAnime.rating?.let { rating ->
                Surface(
                    color = Color(0xFF48D882),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = formatRating(rating),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsExtrasTopSection(
    extrasState: LoadState<AnimeDetailsExtras>,
    auth: AuthUiState,
    videos: List<VideoVariant>,
    onSetAnimeRating: (Int?) -> Unit,
    onToggleVideoSubscription: (VideoVariant) -> Unit,
) {
    when (extrasState) {
        LoadState.Loading -> {
            Unit
        }
        is LoadState.Error -> Unit
        is LoadState.Ready -> {
            DetailsTrailersSection(trailers = extrasState.data.trailers)
        }
    }
}

@Composable
private fun DetailsCompactRatingSection(
    extrasState: LoadState<AnimeDetailsExtras>,
    auth: AuthUiState,
    showRating: Boolean,
    onSetAnimeRating: (Int?) -> Unit,
) {
    if (!showRating) return
    when (extrasState) {
        LoadState.Loading -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
        is LoadState.Error -> Unit
        is LoadState.Ready -> {
            val extras = extrasState.data
            CompactRatingScale(
                rating = extras.rating,
                isAuthorized = auth.profile != null,
                onSetAnimeRating = onSetAnimeRating,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 4.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DetailsSubscriptionsHostSection(
    extrasState: LoadState<AnimeDetailsExtras>,
    auth: AuthUiState,
    videos: List<VideoVariant>,
    allowSubscriptions: Boolean,
    onToggleVideoSubscription: (VideoVariant) -> Unit,
) {
    if (!allowSubscriptions) return
    when (extrasState) {
        LoadState.Loading -> Unit
        is LoadState.Error -> Unit
        is LoadState.Ready -> {
            DetailsSubscriptionsSection(
                auth = auth,
                videos = videos,
                subscriptions = extrasState.data.subscriptions,
                onToggleVideoSubscription = onToggleVideoSubscription,
            )
        }
    }
}

@Composable
private fun DetailsRecommendationsSection(
    extrasState: LoadState<AnimeDetailsExtras>,
    onOpenAnime: (Long) -> Unit,
) {
    if (extrasState !is LoadState.Ready) return
    DetailsAnimeRowSection(
        title = uiText("Похожие"),
        animes = extrasState.data.recommendations,
        onOpenAnime = onOpenAnime,
    )
}

@Composable
private fun DetailsCommentsHostSection(
    extrasState: LoadState<AnimeDetailsExtras>,
    isAuthorized: Boolean,
    scrollState: ScrollState,
    onAddAnimeComment: (String) -> Unit,
    onLoadMoreAnimeComments: () -> Unit,
) {
    when (extrasState) {
        LoadState.Loading -> Unit
        is LoadState.Error -> Unit
        is LoadState.Ready -> {
            DetailsCommentsSection(
                comments = extrasState.data.comments,
                commentsPaging = extrasState.data.commentsPaging,
                isAuthorized = isAuthorized,
                scrollState = scrollState,
                onAddAnimeComment = onAddAnimeComment,
                onLoadMoreAnimeComments = onLoadMoreAnimeComments,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsRatingSection(
    rating: AnimeRatingSummary,
    isAuthorized: Boolean,
    onSetAnimeRating: (Int?) -> Unit,
) {
    if (rating.votes <= 0L && !isAuthorized) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = uiText("Оценка"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = listOfNotNull(
                rating.average?.let { formatRating(it) },
                rating.votes.takeIf { it > 0L }?.let { "${formatViews(it)} ${localizedVotesWord(it)}" },
            ).joinToString(" • ").ifBlank { uiText("Пока нет оценок") },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isAuthorized) {
            RatingScale(
                selected = rating.userRating,
                onSelected = onSetAnimeRating,
            )
        }
    }
}

@Composable
private fun RatingScale(
    selected: Int?,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.58f)),
        shape = shape,
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            (1..10).forEach { value ->
                val active = selected != null && value <= selected
                val itemShape = when (value) {
                    1 -> RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                    10 -> RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                    else -> RoundedCornerShape(0.dp)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .stopHorizontalFocusEscape(value - 1, 10)
                        .background(
                            color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.90f) else Color.Transparent,
                            shape = itemShape,
                        )
                        .dpadClickable(itemShape) { onSelected(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "${uiText("Оценка")} $value",
                        modifier = Modifier.size(19.dp),
                        tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (value < 10) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsSubscriptionsSection(
    auth: AuthUiState,
    videos: List<VideoVariant>,
    subscriptions: List<VideoSubscription>,
    onToggleVideoSubscription: (VideoVariant) -> Unit,
) {
    if (auth.profile == null || videos.isEmpty()) return
    val groups = videos
        .groupBy { it.voiceKey }
        .values
        .mapNotNull { group -> group.minByOrNull { it.player } }
        .sortedBy { it.voiceTitle }
        .take(18)
    if (groups.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val activeCount = groups.count { subscriptions.isVideoVoiceSubscribed(it) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .focusRing(shape)
                .dpadClickable(shape) { expanded = !expanded },
            color = if (activeCount > 0) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.54f)
            },
            contentColor = if (activeCount > 0) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            shape = shape,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = if (activeCount > 0) "${uiText("Подписка")} • $activeCount" else uiText("Подписка"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (expanded) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                groups.forEachIndexed { index, video ->
                    val subscribed = subscriptions.isVideoVoiceSubscribed(video)
                    val itemShape = RoundedCornerShape(8.dp)
                    Surface(
                        modifier = Modifier
                            .stopHorizontalFocusEscape(index, groups.size)
                            .focusRing(itemShape)
                            .dpadClickable(itemShape) { onToggleVideoSubscription(video) },
                        color = if (subscribed) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                        } else {
                            Color.Transparent
                        },
                        contentColor = if (subscribed) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)),
                        shape = itemShape,
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = video.voiceTitle,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsTrailersSection(trailers: List<AnimeTrailer>) {
    if (trailers.isEmpty()) return
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = uiText("Трейлеры"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            lazyItemsIndexed(
                trailers,
                key = { index, trailer -> "trailer:$index:${trailer.id}:${trailer.url}" },
            ) { index, trailer ->
                AssistChip(
                    onClick = { context.openUrl(trailer.url) },
                    label = {
                        Text(
                            text = trailer.title.ifBlank { trailer.player.ifBlank { uiText("Трейлер") } },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier
                        .stopHorizontalFocusEscape(index, trailers.size)
                        .focusRing(RoundedCornerShape(8.dp)),
                )
            }
        }
    }
}

@Composable
private fun DetailsAnimeRowSection(
    title: String,
    animes: List<Anime>,
    onOpenAnime: (Long) -> Unit,
) {
    if (animes.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            lazyItemsIndexed(
                animes,
                key = { index, anime -> "details-anime-row:$title:$index:${anime.id}:${anime.title}" },
            ) { index, anime ->
                AnimeCard(
                    anime = anime,
                    onClick = { onOpenAnime(anime.id) },
                    modifier = Modifier
                        .width(172.dp)
                        .stopHorizontalFocusEscape(index, animes.size),
                )
            }
        }
    }
}

@Composable
private fun DetailsCommentsSection(
    comments: List<AnimeComment>,
    commentsPaging: PagingUiState,
    isAuthorized: Boolean,
    scrollState: ScrollState,
    onAddAnimeComment: (String) -> Unit,
    onLoadMoreAnimeComments: () -> Unit,
) {
    if (comments.isEmpty() && !isAuthorized) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(
        expanded,
        comments.size,
        commentsPaging.canLoadMore,
        commentsPaging.isLoadingMore,
    ) {
        if (!expanded) return@LaunchedEffect
        snapshotFlow { scrollState.value to scrollState.maxValue }
            .collectLatest { (current, max) ->
                val nearBottom = max - current < 720
                if (nearBottom && commentsPaging.canLoadMore && !commentsPaging.isLoadingMore) {
                    onLoadMoreAnimeComments()
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .dpadClickable(RoundedCornerShape(8.dp)) { expanded = !expanded },
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.76f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(8.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text = uiText("Комментарии"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (comments.isNotEmpty()) {
                    Text(
                        text = "${comments.size} ${uiText("загружено")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (expanded) {
            if (isAuthorized) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text(uiText("Комментарий")) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(1.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    DialogActionButton(
                        text = uiText("Отправить"),
                        primary = true,
                        onClick = {
                            val text = draft.trim()
                            if (text.isNotBlank()) {
                                onAddAnimeComment(text)
                                draft = ""
                            }
                        },
                    )
                }
            }

            comments.forEach { comment ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val commentDate = remember(comment.createdAtSeconds) {
                            formatCommentTimestamp(comment.createdAtSeconds)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = comment.userName.ifBlank { uiText("Пользователь") },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (commentDate.isNotBlank()) {
                                Text(
                                    text = commentDate,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                        Text(
                            text = comment.text,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            when {
                commentsPaging.isLoadingMore -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
                commentsPaging.error != null -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = commentsPaging.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    DialogActionButton(
                        text = uiText("Повторить"),
                        primary = true,
                        onClick = onLoadMoreAnimeComments,
                    )
                }
            }
        }
    }
}

private fun UserAnimeListMark.icon() = when (this) {
    UserAnimeListMark.Watching -> Icons.Default.RemoveRedEye
    UserAnimeListMark.Planned -> Icons.Default.Cloud
    UserAnimeListMark.Watched -> Icons.Default.Flag
    UserAnimeListMark.Postponed -> Icons.Default.Schedule
    UserAnimeListMark.Dropped -> Icons.Default.VisibilityOff
}

private fun UserAnimeListMark.siteColor() = when (this) {
    UserAnimeListMark.Watching -> Color(0xFFFF5E66)
    UserAnimeListMark.Planned -> Color(0xFFB66DFF)
    UserAnimeListMark.Watched -> Color(0xFF35D47A)
    UserAnimeListMark.Postponed -> Color(0xFFFFB71B)
    UserAnimeListMark.Dropped -> Color(0xFF9EA3AA)
}

private val favoriteMarkColor = Color(0xFFC94DDB)

@Composable
private fun VideoPickerModern(
    videos: List<VideoVariant>,
    selectedGroup: String?,
    onSelectGroup: (String) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onResolveDownloadQualities: suspend (VideoVariant, List<VideoVariant>, Boolean) -> List<PreferredQuality>,
    onDownloadVideo: (VideoVariant, PreferredQuality) -> Unit,
    onDeleteOfflineVideo: (Long, Long, String?) -> Unit,
    defaultDownloadQuality: PreferredQuality,
    forcedOfflineMode: Boolean,
    canDownload: Boolean,
    modifier: Modifier = Modifier,
) {
    if (videos.isEmpty()) {
        EmptyPane(
            message = uiText("Видео для этого аниме пока нет"),
            modifier = modifier.heightIn(min = 180.dp),
        )
        return
    }

    val configuration = LocalConfiguration.current
    val cardWidth = when {
        configuration.screenWidthDp >= 1180 -> 330.dp
        configuration.screenWidthDp >= 760 -> 300.dp
        configuration.screenWidthDp >= 560 -> 280.dp
        else -> 300.dp
    }
    val groups = videos.groupBy { it.groupKey }
    val selectedKey = selectedGroup?.takeIf(groups::containsKey) ?: groups.keys.first()
    val displayVideos = remember(videos, selectedKey) {
        videos.sortedForPlayer(selectedKey)
    }
    var pendingDownloadVideo by remember { mutableStateOf<VideoVariant?>(null) }
    var pendingDeleteVideo by remember { mutableStateOf<VideoVariant?>(null) }

    Column(
        modifier = modifier.padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = uiText("Просмотр"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            lazyItemsIndexed(
                displayVideos,
                key = { index, video -> "episode-row:$index:${video.id}:${video.groupKey}:${video.episode}" },
            ) { index, video ->
                val enabled = !forcedOfflineMode || video.isOfflineAvailable
                val downloadedVariants = videos.downloadEpisodeCandidates(video).filter { it.isOfflineAvailable }
                EpisodeCard(
                    video = video,
                    downloadedVariants = downloadedVariants,
                    enabled = enabled,
                    canDownload = canDownload,
                    onClick = { if (enabled) onPlayVideo(video) },
                    onDownloadClick = { pendingDownloadVideo = video },
                    onDeleteClick = {
                        val targets = downloadedVariants.offlineDeleteTargets()
                        if (targets.size <= 1) {
                            targets.firstOrNull()?.let {
                                onDeleteOfflineVideo(it.animeId, it.videoId, it.playbackUrl)
                            }
                        } else {
                            pendingDeleteVideo = video
                        }
                    },
                    modifier = Modifier
                        .width(cardWidth)
                        .stopHorizontalFocusEscape(index, displayVideos.size),
                )
            }
        }
    }

    pendingDownloadVideo?.let { video ->
        DownloadSelectionDialog(
            title = "${uiText("Скачать")} ${video.episodeTitle}",
            videos = videos.downloadEpisodeCandidates(video),
            selectedVideo = video,
            selected = defaultDownloadQuality,
            allEpisodes = false,
            onResolveQualities = onResolveDownloadQualities,
            confirmText = uiText("Скачать"),
            onConfirm = { selectedVideo, quality ->
                pendingDownloadVideo = null
                onDownloadVideo(selectedVideo, quality)
            },
            onDismiss = { pendingDownloadVideo = null },
        )
    }

    pendingDeleteVideo?.let { video ->
        EpisodeDeleteDialog(
            video = video,
            downloadedVariants = videos.downloadEpisodeCandidates(video).filter { it.isOfflineAvailable },
            onDelete = { targets ->
                pendingDeleteVideo = null
                targets.forEach { onDeleteOfflineVideo(it.animeId, it.videoId, it.playbackUrl) }
            },
            onDismiss = { pendingDeleteVideo = null },
        )
    }
}

@Composable
private fun DetailsPoster(
    posterUrl: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        PosterImage(
            url = posterUrl,
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun EpisodeCard(
    video: VideoVariant,
    modifier: Modifier = Modifier,
    downloadedVariants: List<VideoVariant> = if (video.isOfflineAvailable) listOf(video) else emptyList(),
    onClick: () -> Unit,
    onDownloadClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    enabled: Boolean = true,
    canDownload: Boolean = true,
) {
    val contentAlpha = if (enabled) 1f else 0.46f
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(106.dp)
            .dpadClickable(RoundedCornerShape(8.dp), enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .graphicsLayer { alpha = contentAlpha },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(104.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (video.previewUrl.isNotBlank()) {
                    PosterImage(
                        url = video.previewUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.30f)),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(18.dp),
                    )
                }
                if (video.isOfflineAvailable) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(5.dp),
                        color = Color(0xFF48D882),
                        contentColor = Color.Black,
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = "OFF",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 0.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = video.episodeTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        formatDuration(video.durationSeconds),
                        formatViews(video.views),
                        if (video.isOfflineAvailable) uiText("офлайн") else null,
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (canDownload || downloadedVariants.isNotEmpty()) {
                Column(
                    modifier = Modifier.width(36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    if (canDownload) {
                        IconButton(
                            onClick = onDownloadClick,
                            enabled = canDownload,
                            modifier = Modifier
                                .size(36.dp)
                                .focusRing(RoundedCornerShape(8.dp)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = uiText("Скачать серию"),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    if (downloadedVariants.isNotEmpty()) {
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier
                                .size(36.dp)
                                .focusRing(RoundedCornerShape(8.dp)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = uiText("Удалить скачанную серию"),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }

}

private data class OfflineDeleteTarget(
    val animeId: Long,
    val videoId: Long,
    val playbackUrl: String?,
)

private data class OfflineDeleteFile(
    val variant: VideoVariant,
    val file: OfflineVideoFile,
) {
    val target: OfflineDeleteTarget
        get() = OfflineDeleteTarget(variant.animeId, variant.id, file.playbackUrl)
}

private fun List<VideoVariant>.offlineDeleteFiles(): List<OfflineDeleteFile> {
    return flatMap { variant ->
        variant.offlineFiles
            .filter { it.playbackUrl.isNotBlank() }
            .distinctBy { it.playbackUrl }
            .map { OfflineDeleteFile(variant, it) }
    }
        .distinctBy { it.file.playbackUrl }
        .sortedWith(
            compareBy<OfflineDeleteFile> { it.displayVoiceTitle().lowercase(Locale.ROOT) }
                .thenByDescending { it.file.qualityHeight() }
                .thenBy { it.file.bytes },
        )
}

private fun List<VideoVariant>.offlineDeleteTargets(): List<OfflineDeleteTarget> {
    val fileTargets = offlineDeleteFiles().map { it.target }
    if (fileTargets.isNotEmpty()) return fileTargets.distinctBy { it.playbackUrl }
    return filter { it.isOfflineAvailable }
        .map { OfflineDeleteTarget(it.animeId, it.id, null) }
        .distinctBy { Triple(it.animeId, it.videoId, it.playbackUrl) }
}

private fun OfflineDeleteFile.displayVoiceTitle(): String {
    return file.voiceTitle
        .ifBlank { file.voiceTitleFromDownloadPath() }
        .ifBlank { variant.voiceTitle }
        .ifBlank { file.player.cleanVideoLabel("Плеер") }
        .ifBlank { variant.player.cleanVideoLabel("Плеер") }
        .ifBlank { "Озвучка" }
}

private fun OfflineDeleteFile.displayKey(): String {
    return listOf(
        displayVoiceTitle().lowercase(Locale.ROOT),
        file.qualityDisplayTitle().lowercase(Locale.ROOT),
        file.bytes.coerceAtLeast(0L).toString(),
    ).joinToString("|")
}

private fun OfflineDeleteFile.displayTitle(totalBytes: Long = file.bytes): String {
    return listOf(
        displayVoiceTitle(),
        file.qualityDisplayTitle(),
        totalBytes.takeIf { it > 0L }?.let(::formatFileSize),
    ).filterNot { it.isNullOrBlank() }.joinToString(" • ")
}

private fun OfflineVideoFile.voiceTitleFromDownloadPath(): String {
    val path = Uri.parse(playbackUrl).path.orEmpty()
    val parts = path.split('/').filter { it.isNotBlank() }
    val rootIndex = parts.indexOfLast { it.equals("YummyDroid", ignoreCase = true) }
    val voicePart = parts.getOrNull(rootIndex + 2).orEmpty()
    return Uri.decode(voicePart)
        .replace('_', ' ')
        .takeIf { it.isNotBlank() && !it.equals("auto", ignoreCase = true) }
        .orEmpty()
}

@Composable
private fun EpisodeDeleteDialog(
    video: VideoVariant,
    downloadedVariants: List<VideoVariant>,
    onDelete: (List<OfflineDeleteTarget>) -> Unit,
    onDismiss: () -> Unit,
) {
    if (downloadedVariants.isEmpty()) return
    val voiceGroups = downloadedVariants
        .groupBy { it.voiceKey }
        .values
        .map { variants -> variants.sortedForPlayer() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${uiText("Удалить")} ${video.episodeTitle}") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    SelectableFilterRow(
                        title = uiText("Все скачанные варианты"),
                        selected = false,
                        onClick = { onDelete(downloadedVariants.offlineDeleteTargets()) },
                    )
                }
                items(voiceGroups, key = { variants -> "delete-offline:${variants.first().voiceKey}" }) { variants ->
                    val files = variants.offlineDeleteFiles()
                    val fileRows = files
                        .groupBy { it.displayKey() }
                        .values
                        .map { group -> group.sortedBy { it.file.playbackUrl } }
                    val representative = files.firstOrNull()
                    val qualities = files.map { it.file.qualityDisplayTitle() }.distinct().joinToString(", ")
                    val bytes = files.sumOf { it.file.bytes.coerceAtLeast(0L) }
                    val info = listOf(
                        representative?.displayVoiceTitle(),
                        qualities.ifBlank { null },
                        bytes.takeIf { it > 0L }?.let(::formatFileSize),
                    ).filterNot { it.isNullOrBlank() }.joinToString(" • ")
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SelectableFilterRow(
                            title = info,
                            selected = false,
                            onClick = { onDelete(variants.offlineDeleteTargets()) },
                        )
                        if (fileRows.size > 1) {
                            fileRows.forEach { row ->
                                val fileInfo = row.first().displayTitle(
                                    totalBytes = row.sumOf { it.file.bytes.coerceAtLeast(0L) },
                                )
                                SelectableFilterRow(
                                    title = "  $fileInfo",
                                    selected = false,
                                    onClick = { onDelete(row.map { it.target }) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(
                    text = uiText("Закрыть"),
                    onClick = onDismiss,
                )
            }
        },
    )
}

private const val PLAYER_CONTROLS_AUTO_HIDE_MS = 4_000L
private const val VOICE_MENU_GROUP_ID = 19
private const val QUALITY_MENU_GROUP_ID = 20
private const val SPEED_MENU_GROUP_ID = 21
private const val PIP_ENTER_DELAY_MS = 120L
private const val PLAYER_TIMELINE_SCRUB_COMMIT_DELAY_MS = 650L
private const val PLAYER_TIMELINE_SCRUB_ACCEL_WINDOW_MS = 700L
private const val PLAYBACK_PROGRESS_SAVE_INTERVAL_MS = 15_000L
private const val SKIP_PROMPT_COUNTDOWN_SECONDS = 5
private const val SKIP_PROMPT_POLL_MS = 500L

private data class VideoZoomGestureState(
    var scale: Float = 1f,
    var offsetX: Float = 0f,
    var offsetY: Float = 0f,
    var lastX: Float = 0f,
    var lastY: Float = 0f,
    var moved: Boolean = false,
)

private data class ActiveSkipPrompt(
    val key: String,
    val segment: VideoSkipSegment,
)

private data class SkipCountdownState(
    var remainingSeconds: Int,
    var autoSkipEnabled: Boolean,
)

@Composable
private fun PlayerScreen(
    animeTitle: String,
    video: VideoVariant,
    settings: AppSettings,
    startPositionMs: Long,
    allVideos: List<VideoVariant>,
    selectedGroup: String?,
    streamState: LoadState<ResolvedVideoStream>,
    isInPictureInPicture: Boolean,
    forcedOfflineMode: Boolean,
    allowSubscriptions: Boolean,
    subscriptions: List<VideoSubscription>,
    onSelectGroup: (String) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    onToggleVideoSubscription: (VideoVariant) -> Unit,
    onRetry: () -> Unit,
    onPlaybackFailed: (VideoVariant, Long) -> Unit,
    onPlaybackStarted: (VideoVariant) -> Unit,
    onPlaybackEnded: (VideoVariant) -> Unit,
    onPlaybackProgress: (VideoVariant, Long, Long) -> Unit,
    canUsePictureInPicture: Boolean,
    onEnterPictureInPicture: () -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
    onRegisterPlayerInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
) {
    val sourceVideos = allVideos.ifEmpty { listOf(video) }
    val videos = if (forcedOfflineMode) {
        sourceVideos.filter { it.isOfflineAvailable }
            .ifEmpty { listOf(video).filter { it.isOfflineAvailable } }
    } else {
        sourceVideos
    }
    val groups = remember(videos) { videos.groupBy { it.voiceKey } }
    val selectedKey = selectedGroup
        ?.let { groupKey -> videos.firstOrNull { it.groupKey == groupKey }?.voiceKey }
        ?.takeIf(groups::containsKey)
        ?: video.voiceKey.takeIf(groups::containsKey)
        ?: groups.keys.firstOrNull()
    val preferredGroupKey = selectedGroup?.takeIf { groupKey -> videos.any { it.groupKey == groupKey } }
        ?: video.groupKey
    val previousVideo = remember(video, videos, selectedGroup) {
        findAdjacentPlayerVideo(
            currentVideo = video,
            allVideos = videos,
            selectedGroup = selectedGroup,
            forward = false,
        )
    }
    val nextVideo = remember(video, videos, selectedGroup) {
        findAdjacentPlayerVideo(
            currentVideo = video,
            allVideos = videos,
            selectedGroup = selectedGroup,
            forward = true,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when (streamState) {
            LoadState.Loading -> PlayerShellPane(
                animeTitle = animeTitle,
                currentVideo = video,
                settings = settings,
                groups = groups,
                selectedKey = selectedKey,
                previousVideo = previousVideo,
                nextVideo = nextVideo,
                allowSubscription = allowSubscriptions,
                subscriptionActive = subscriptions.isVideoVoiceSubscribed(video),
                canUsePictureInPicture = canUsePictureInPicture,
                onToggleSubscription = { onToggleVideoSubscription(video) },
                onSelectGroup = { groupKey, replacement ->
                    if (replacement != null) {
                        onSelectGroup(replacement.groupKey)
                        onPlayVideoAt(replacement, startPositionMs)
                    } else {
                        onSelectGroup(groupKey)
                    }
                },
                onPlayVideo = { next ->
                    onSelectGroup(next.groupKey)
                    onPlayVideo(next)
                },
                onRetry = onRetry,
                onBack = onBack,
                modifier = Modifier.fillMaxSize(),
            )
            is LoadState.Error -> PlayerShellPane(
                animeTitle = animeTitle,
                currentVideo = video,
                settings = settings,
                groups = groups,
                selectedKey = selectedKey,
                previousVideo = previousVideo,
                nextVideo = nextVideo,
                allowSubscription = allowSubscriptions,
                subscriptionActive = subscriptions.isVideoVoiceSubscribed(video),
                canUsePictureInPicture = canUsePictureInPicture,
                onToggleSubscription = { onToggleVideoSubscription(video) },
                onSelectGroup = { groupKey, replacement ->
                    if (replacement != null) {
                        onSelectGroup(replacement.groupKey)
                        onPlayVideoAt(replacement, startPositionMs)
                    } else {
                        onSelectGroup(groupKey)
                    }
                },
                onPlayVideo = { next ->
                    onSelectGroup(next.groupKey)
                    onPlayVideo(next)
                },
                message = streamState.message,
                onRetry = onRetry,
                onBack = onBack,
                modifier = Modifier.fillMaxSize(),
            )
            is LoadState.Ready -> NativeVideoPlayer(
                stream = streamState.data,
                animeTitle = animeTitle,
                currentVideo = video,
                settings = settings,
                startPositionMs = startPositionMs,
                groups = groups,
                selectedKey = selectedKey,
                previousVideo = previousVideo,
                nextVideo = nextVideo,
                allowSubscription = allowSubscriptions,
                subscriptionActive = subscriptions.isVideoVoiceSubscribed(video),
                onToggleSubscription = { onToggleVideoSubscription(video) },
                onSelectGroup = { groupKey, replacement, positionMs ->
                    if (replacement != null) {
                        onSelectGroup(replacement.groupKey)
                        onPlayVideoAt(replacement, positionMs)
                    } else {
                        onSelectGroup(groupKey)
                    }
                },
                onPlayVideo = { next ->
                    onSelectGroup(next.groupKey)
                    onPlayVideo(next)
                },
                onPlayVideoAt = { next, positionMs ->
                    onSelectGroup(next.groupKey)
                    onPlayVideoAt(next, positionMs)
                },
                onPlaybackFailed = onPlaybackFailed,
                onPlaybackStarted = onPlaybackStarted,
                onPlaybackEnded = onPlaybackEnded,
                onPlaybackProgress = onPlaybackProgress,
                canUsePictureInPicture = canUsePictureInPicture,
                isInPictureInPicture = isInPictureInPicture,
                onEnterPictureInPicture = onEnterPictureInPicture,
                onSettingsChange = onSettingsChange,
                onBack = onBack,
                onRegisterPlayerInputActionHandler = onRegisterPlayerInputActionHandler,
                offlineMode = forcedOfflineMode,
                modifier = Modifier.fillMaxSize(),
            )
        }

    }
}

@Composable
@OptIn(UnstableApi::class)
private fun PlayerShellPane(
    animeTitle: String,
    currentVideo: VideoVariant,
    settings: AppSettings,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    previousVideo: VideoVariant?,
    nextVideo: VideoVariant?,
    allowSubscription: Boolean,
    subscriptionActive: Boolean,
    canUsePictureInPicture: Boolean,
    onToggleSubscription: () -> Unit,
    onSelectGroup: (String, VideoVariant?) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    Box(
        modifier = modifier.background(Color.Black),
    ) {
        AndroidView(
            factory = { viewContext ->
                val parent = FrameLayout(viewContext)
                LayoutInflater.from(viewContext).inflate(R.layout.yummy_player_view, parent, false) as PlayerView
            },
            update = { view ->
                view.player = null
                view.useController = true
                view.controllerAutoShow = true
                view.setControllerAnimationEnabled(false)
                view.setControllerShowTimeoutMs(0)
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                view.keepScreenOn = true
                view.bindYummyShellController(
                    animeTitle = animeTitle,
                    currentVideo = currentVideo,
                    settings = settings,
                    groups = groups,
                    selectedKey = selectedKey,
                    previousVideo = previousVideo,
                    nextVideo = nextVideo,
                    allowSubscription = allowSubscription,
                    subscriptionActive = subscriptionActive,
                    canUsePictureInPicture = canUsePictureInPicture,
                    onToggleSubscription = onToggleSubscription,
                    onSelectGroup = onSelectGroup,
                    onPlayVideo = onPlayVideo,
                    onBack = onBack,
                )
                view.showController()
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (message == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                DialogActionButton(
                    text = uiText("Повторить"),
                    primary = true,
                    onClick = onRetry,
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
private fun PlayerView.bindYummyShellController(
    animeTitle: String,
    currentVideo: VideoVariant,
    settings: AppSettings,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    previousVideo: VideoVariant?,
    nextVideo: VideoVariant?,
    allowSubscription: Boolean,
    subscriptionActive: Boolean,
    canUsePictureInPicture: Boolean,
    onToggleSubscription: () -> Unit,
    onSelectGroup: (String, VideoVariant?) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onBack: () -> Unit,
) {
    findViewById<TextView>(R.id.yummy_player_title)?.text = animeTitle.ifBlank { defaultPlayerControlTexts.title }
    findViewById<TextView>(R.id.yummy_player_subtitle)?.text = currentVideo.playbackSubtitle()
    findViewById<TextView>(R.id.yummy_player_info)?.text = currentVideo.playbackSourceLabel(false)
    findViewById<TextView>(Media3R.id.exo_position)?.text = "00:00"
    findViewById<TextView>(Media3R.id.exo_duration)?.text = "00:00"

    findViewById<View>(Media3R.id.exo_settings)?.visibility = View.GONE
    findViewById<View>(R.id.yummy_skip_controls)?.visibility = View.GONE
    findViewById<View>(Media3R.id.exo_play_pause)?.visibility = View.GONE
    findViewById<View>(R.id.yummy_player_back)?.setOnClickListener { onBack() }

    findViewById<View>(R.id.yummy_episode_previous)?.apply {
        visibility = if (previousVideo != null) View.VISIBLE else View.GONE
        setOnClickListener { previousVideo?.let(onPlayVideo) }
    }
    findViewById<View>(R.id.yummy_episode_next)?.apply {
        visibility = if (nextVideo != null) View.VISIBLE else View.GONE
        setOnClickListener { nextVideo?.let(onPlayVideo) }
    }

    findViewById<TextView>(R.id.yummy_player_voice)?.apply {
        text = defaultPlayerControlTexts.voice
        visibility = if (groups.size > 1) View.VISIBLE else View.GONE
        setPlayerControlEnabled(groups.size > 1)
        setOnClickListener {
            showController()
            showVoicePopup(
                anchor = this,
                groups = groups,
                selectedKey = selectedKey,
                preferredGroupKey = currentVideo.groupKey,
                currentVideo = currentVideo,
                onSelectGroup = onSelectGroup,
            )
        }
    }

    findViewById<TextView>(R.id.yummy_player_quality)?.apply {
        text = defaultPlayerControlTexts.quality
        visibility = View.VISIBLE
        setPlayerControlEnabled(false)
    }
    findViewById<TextView>(R.id.yummy_player_subscription)?.apply {
        text = if (subscriptionActive) defaultPlayerControlTexts.subscribed else defaultPlayerControlTexts.subscription
        visibility = if (allowSubscription) View.VISIBLE else View.GONE
        setPlayerControlEnabled(allowSubscription)
        applyPlayerSubscriptionState(subscriptionActive)
        setOnClickListener {
            showController()
            onToggleSubscription()
        }
    }
    findViewById<TextView>(R.id.yummy_player_speed)?.apply {
        text = settings.playerSpeed.title
        visibility = View.VISIBLE
        setPlayerControlEnabled(false)
    }
    findViewById<TextView>(R.id.yummy_player_pip)?.apply {
        text = "PiP"
        visibility = if (canUsePictureInPicture) View.VISIBLE else View.GONE
        setPlayerControlEnabled(false)
    }

    findViewById<View>(Media3R.id.exo_progress)?.apply {
        isEnabled = false
        isFocusable = false
    }
}

private fun List<VideoVariant>.sortedForPlayer(): List<VideoVariant> {
    return sortedWith(
        compareBy<VideoVariant> { it.index }
            .thenBy { it.episode.toDoubleOrNull() ?: 0.0 }
            .thenBy { if (it.isOfflineAvailable) 0 else 1 }
            .thenBy { it.id },
    )
}

@Composable
private fun PagingGridFooter(
    paging: PagingUiState,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(paging.isLoadingMore, paging.canLoadMore, paging.error) {
        if (paging.canLoadMore && !paging.isLoadingMore && paging.error == null) {
            onLoadMore()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            paging.isLoadingMore -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
            paging.error != null -> Button(
                onClick = onLoadMore,
                modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(uiText("Еще раз"))
            }
        }
    }

}

private fun List<VideoVariant>.sortedForPlayer(preferredGroupKey: String?): List<VideoVariant> {
    return groupBy { it.episodeSlotKey }
        .values
        .mapNotNull { variants ->
            variants.minWithOrNull(
                compareBy<VideoVariant> { if (it.isOfflineAvailable) 0 else 1 }
                    .thenBy { if (it.groupKey == preferredGroupKey) 0 else 1 }
                    .thenBy { it.playerPriority() }
                    .thenBy { it.index }
                    .thenBy { it.id },
            )
        }
        .sortedForPlayer()
}

private fun VideoVariant.sameEpisodeSlot(other: VideoVariant): Boolean {
    return (episode.isNotBlank() && episode == other.episode) || (index > 0 && index == other.index)
}

private val VideoVariant.voiceTitle: String
    get() = dubbing.cleanVideoLabel("Озвучка")
        .ifBlank { player.cleanVideoLabel("Плеер") }
        .ifBlank { matchingVoiceTitle }
        .ifBlank { "Озвучка" }

private val VideoVariant.voiceKey: String
    get() = voiceTitle.playbackVoiceKey()

private fun String.playbackVoiceKey(): String {
    return trim()
        .lowercase(Locale.ROOT)
        .replace('ё', 'е')
        .replace(Regex("""\s+"""), " ")
        .trim()
}

private fun List<VideoSubscription>.isVideoVoiceSubscribed(video: VideoVariant): Boolean {
    return isSubscribedTo(video)
}

private val VideoVariant.episodeSlotKey: String
    get() = episode.trim().takeIf { it.isNotBlank() }
        ?: index.takeIf { it > 0 }?.let { "index:$it" }
        ?: "video:$id"

private fun VideoVariant.playbackSourceLabel(isLocalPlayback: Boolean = localPlaybackUrl.isNotBlank()): String {
    return if (isLocalPlayback) {
        "Local"
    } else {
        player.cleanVideoLabel("Плеер").ifBlank { player }.ifBlank { "HLS" }
    }
}

private fun VideoVariant.localQualityOptions(): List<QualityOption> {
    return offlineFiles
        .filter { it.playbackUrl.isNotBlank() }
        .sortedWith(compareByDescending<OfflineVideoFile> { it.qualityHeight() }.thenBy { it.qualityTitle })
        .distinctBy { it.qualityOptionIdentity() }
        .map { file ->
            QualityOption(
                group = null,
                trackIndex = -1,
                label = file.qualityDisplayTitle(),
                height = file.qualityHeight(),
                bitrate = 0,
                key = file.qualityKey(),
                localFile = file,
            )
        }
}

private fun VideoVariant.withOfflineFile(file: OfflineVideoFile): VideoVariant {
    val mergedLocalFiles = (localFiles + file)
        .filter { it.playbackUrl.isNotBlank() }
        .distinctBy { it.playbackUrl }
        .sortedWith(compareByDescending<OfflineVideoFile> { it.qualityHeight() }.thenBy { it.qualityTitle })
    return copy(
        localPlaybackUrl = file.playbackUrl,
        localMimeType = file.mimeType,
        localBytes = file.bytes,
        localFiles = mergedLocalFiles,
    )
}

private fun VideoVariant.selectedLocalQualityKey(streamUrl: String): String? {
    val selectedUrl = streamUrl.takeIf { it.startsWith("file:", ignoreCase = true) }
        ?: localPlaybackUrl.takeIf { it.isNotBlank() }
    return offlineFiles.firstOrNull { it.playbackUrl == selectedUrl }?.qualityKey()
}

private fun OfflineVideoFile.qualityDisplayTitle(): String {
    return qualityTitle
        .replace('_', ' ')
        .takeIf { it.isNotBlank() }
        ?: "локально"
}

private fun OfflineVideoFile.qualityKey(): String {
    return "local:${playbackUrl}:${qualityTitle}"
}

private fun OfflineVideoFile.qualityOptionIdentity(): String {
    return qualityHeight()
        .takeIf { it > 0 }
        ?.let { "height:$it" }
        ?: qualityDisplayTitle().qualityIdentityFromLabel()
}

private fun QualityOption.qualityOptionIdentity(): String {
    return height
        .takeIf { it > 0 }
        ?.let { "height:$it" }
        ?: label.qualityIdentityFromLabel()
}

private fun String.qualityIdentityFromLabel(): String {
    val cleaned = replace("скачано", "", ignoreCase = true)
        .replace("downloaded", "", ignoreCase = true)
    val height = Regex("""(?i)(2160|1440|1080|720|576|540|480|360|240|144)p""")
        .find(cleaned)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    if (height != null) return "height:$height"
    return cleaned
        .lowercase(Locale.ROOT)
        .replace(Regex("""[\s•|:_\-]+"""), "")
        .trim()
}

private fun QualityOption.withDownloadedLabel(): QualityOption {
    if (localFile == null || label.contains("скачано", ignoreCase = true)) return this
    return copy(label = "$label • скачано")
}

private fun mergeVideoQualityOptions(
    onlineOptions: List<QualityOption>,
    localOptions: List<QualityOption>,
    offlineMode: Boolean,
): List<QualityOption> {
    val uniqueLocalOptions = localOptions.distinctBy { it.qualityOptionIdentity() }
    if (offlineMode) {
        return uniqueLocalOptions
            .map { it.withDownloadedLabel() }
            .sortedByQuality()
    }

    val localByIdentity = uniqueLocalOptions.associateBy { it.qualityOptionIdentity() }
    val onlineWithLocalFiles = onlineOptions.map { online ->
        val local = localByIdentity[online.qualityOptionIdentity()] ?: return@map online
        online.copy(
            label = if (online.label.contains("скачано", ignoreCase = true)) {
                online.label
            } else {
                "${online.label} • скачано"
            },
            localFile = local.localFile,
        )
    }
    val onlineIdentities = onlineOptions.mapTo(mutableSetOf()) { it.qualityOptionIdentity() }
    val localOnlyOptions = uniqueLocalOptions
        .filterNot { it.qualityOptionIdentity() in onlineIdentities }
        .map { it.withDownloadedLabel() }

    return (onlineWithLocalFiles + localOnlyOptions)
        .distinctBy { it.qualityOptionIdentity() }
        .sortedByQuality()
}

private fun List<QualityOption>.sortedByQuality(): List<QualityOption> {
    return sortedWith(
        compareByDescending<QualityOption> { it.height.coerceAtLeast(0) }
            .thenByDescending { it.bitrate.coerceAtLeast(0) }
            .thenBy { it.label },
    )
}

private fun QualityOption.matchesSelectedQualityKey(selectedQualityKey: String?): Boolean {
    val selected = selectedQualityKey?.takeIf { it.isNotBlank() } ?: return false
    return key == selected ||
        localFile?.qualityKey() == selected ||
        qualityOptionIdentity() == selected ||
        qualityOptionIdentity() == selected.qualityIdentityFromLabel()
}

private fun VideoVariant.playbackSubtitle(): String {
    val voice = dubbing.cleanVideoLabel("Озвучка")
    return listOf(voice, episodeTitle)
        .filterNot { it.isNullOrBlank() }
        .joinToString(" • ")
}

private fun AnimeDetails.canShowVideoSubscriptions(): Boolean {
    val normalizedStatus = status.lowercase(Locale.ROOT).replace('ё', 'е')
    return listOf(
        "вышел",
        "вышло",
        "заверш",
        "released",
        "completed",
        "complete",
        "finished",
    ).none(normalizedStatus::contains)
}

private fun String.cleanVideoLabel(prefix: String): String {
    return trim().removePrefix(prefix).trim()
}

private fun VideoVariant.playerPriority(): Int {
    val normalized = player.lowercase(Locale.ROOT)
    return when {
        "cvh" in normalized || "cdnvideohub" in normalized -> 10
        "alloha" in normalized -> 20
        "kodik" in normalized -> 30
        "aksor" in normalized -> 40
        "sibnet" in normalized -> 50
        else -> 100
    }
}

private fun findAdjacentPlayerVideo(
    currentVideo: VideoVariant,
    allVideos: List<VideoVariant>,
    selectedGroup: String?,
    forward: Boolean,
): VideoVariant? {
    val videos = allVideos.ifEmpty { listOf(currentVideo) }
    val preferredVoiceKey = selectedGroup
        ?.let { groupKey -> videos.firstOrNull { it.groupKey == groupKey }?.voiceKey }
        ?: currentVideo.voiceKey
    val preferredGroupKey = selectedGroup?.takeIf { groupKey -> videos.any { it.groupKey == groupKey } }
        ?: currentVideo.groupKey

    val episodeVideos = videos
        .groupBy { it.episodeSlotKey }
        .values
        .mapNotNull { variants ->
            variants.minWithOrNull(
                compareBy<VideoVariant> { if (it.voiceKey == preferredVoiceKey) 0 else 1 }
                    .thenBy { if (it.groupKey == preferredGroupKey) 0 else 1 }
                    .thenBy { if (it.isOfflineAvailable) 0 else 1 }
                    .thenBy { it.playerPriority() }
                    .thenBy { it.index }
                    .thenBy { it.id },
            )
        }
        .sortedForPlayer()

    val currentIndex = episodeVideos.indexOfFirst { it.sameEpisodeSlot(currentVideo) }
        .takeIf { it >= 0 }
        ?: return null
    val nextIndex = if (forward) currentIndex + 1 else currentIndex - 1
    return episodeVideos.getOrNull(nextIndex)
}

private fun showVoiceFallbackToast(
    context: Context,
    previousVideo: VideoVariant,
    nextVideo: VideoVariant,
) {
    if (previousVideo.voiceKey == nextVideo.voiceKey) return
    Toast.makeText(
        context,
        "Озвучка «${previousVideo.voiceTitle}» недоступна для ${nextVideo.episodeTitle}. Включена «${nextVideo.voiceTitle}».",
        Toast.LENGTH_LONG,
    ).show()
}

private data class PlayerControlTexts(
    val title: String,
    val watch: String,
    val voice: String,
    val quality: String,
    val subscription: String,
    val subscribed: String,
    val skip: String,
)

private val defaultPlayerControlTexts = PlayerControlTexts(
    title = "Просмотр",
    watch = "Смотреть",
    voice = "Озвучка",
    quality = "Качество",
    subscription = "Подписка",
    subscribed = "Подписан",
    skip = "Пропустить",
)

@OptIn(UnstableApi::class)
@Composable
private fun NativeVideoPlayer(
    stream: ResolvedVideoStream,
    animeTitle: String,
    currentVideo: VideoVariant,
    settings: AppSettings,
    startPositionMs: Long,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    previousVideo: VideoVariant?,
    nextVideo: VideoVariant?,
    allowSubscription: Boolean,
    subscriptionActive: Boolean,
    onToggleSubscription: () -> Unit,
    onSelectGroup: (String, VideoVariant?, Long) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    onPlaybackFailed: (VideoVariant, Long) -> Unit,
    onPlaybackStarted: (VideoVariant) -> Unit,
    onPlaybackEnded: (VideoVariant) -> Unit,
    onPlaybackProgress: (VideoVariant, Long, Long) -> Unit,
    canUsePictureInPicture: Boolean,
    isInPictureInPicture: Boolean,
    onEnterPictureInPicture: () -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
    onRegisterPlayerInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    offlineMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val playerControlTexts = PlayerControlTexts(
        title = uiText("Просмотр"),
        watch = uiText("Смотреть"),
        voice = uiText("Озвучка"),
        quality = uiText("Качество"),
        subscription = uiText("Подписка"),
        subscribed = uiText("Подписан"),
        skip = uiText("Пропустить"),
    )
    val currentSettings by rememberUpdatedState(settings)
    val currentProgressCallback by rememberUpdatedState(onPlaybackProgress)
    val currentProgressVideo by rememberUpdatedState(currentVideo)
    val latestCurrentVideo by rememberUpdatedState(currentVideo)
    val latestPreviousVideo by rememberUpdatedState(previousVideo)
    val latestNextVideo by rememberUpdatedState(nextVideo)
    val latestPlayVideoAt by rememberUpdatedState(onPlayVideoAt)
    val httpClient = remember {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
    val renderersFactory = remember(context, settings.decoderMode) {
        DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(settings.decoderMode.mediaCodecSelector())
    }
    val player = remember(stream.url, stream.headers, startPositionMs, httpClient, renderersFactory) {
        AppLog.w("YummyDroidPlayer", "Stream headers=${stream.headers.keys.sorted()}")
        val userAgent = stream.headers["User-Agent"] ?: "YummyDroid Android TV"
        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                .setMaxVideoBitrate(Int.MAX_VALUE)
                .build()
        }
        val dataSourceFactory: DataSource.Factory = if (stream.url.startsWith("file:", ignoreCase = true)) {
            DefaultDataSource.Factory(context)
        } else {
            OkHttpDataSource.Factory(httpClient)
                .setUserAgent(userAgent)
                .setDefaultRequestProperties(stream.headers)
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply {
                val mediaItemBuilder = MediaItem.Builder().setUri(stream.url)
                stream.mimeType?.let { mediaItemBuilder.setMimeType(it) }
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true,
                )
                setMediaItem(mediaItemBuilder.build(), startPositionMs.coerceAtLeast(0L))
                playWhenReady = true
                prepare()
            }
    }
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    val onlineQualityOptions = remember(tracks) { tracks.videoQualityOptions() }
    val localQualityOptions = remember(currentVideo.id, currentVideo.localPlaybackUrl, currentVideo.localFiles) {
        currentVideo.localQualityOptions()
    }
    val qualityOptions = remember(onlineQualityOptions, localQualityOptions, offlineMode) {
        mergeVideoQualityOptions(
            onlineOptions = onlineQualityOptions,
            localOptions = localQualityOptions,
            offlineMode = offlineMode,
        )
    }
    var selectedQualityKey by remember(stream.url) {
        mutableStateOf(currentVideo.selectedLocalQualityKey(stream.url))
    }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    DisposableEffect(player, isInPictureInPicture) {
        onRegisterPlayerInputActionHandler { action ->
            val view = playerView
            if (view == null || isInPictureInPicture) {
                false
            } else {
                view.handleRemoteInputAction(action)
            }
        }
        onDispose { onRegisterPlayerInputActionHandler(null) }
    }
    val pipPlayerHandle = remember(player) {
        object : PipPlayerHandle {
            override val isPlaying: Boolean
                get() = player.isPlaying

            override val canPlayPreviousEpisode: Boolean
                get() = latestPreviousVideo != null

            override val canPlayNextEpisode: Boolean
                get() = latestNextVideo != null

            override fun play() {
                player.play()
            }

            override fun pause() {
                player.pause()
            }

            override fun playPreviousEpisode() {
                latestPreviousVideo?.let { previous ->
                    showVoiceFallbackToast(context, latestCurrentVideo, previous)
                    player.pause()
                    latestPlayVideoAt(previous, 0L)
                }
            }

            override fun playNextEpisode() {
                latestNextVideo?.let { next ->
                    showVoiceFallbackToast(context, latestCurrentVideo, next)
                    player.pause()
                    latestPlayVideoAt(next, 0L)
                }
            }

            override fun setPictureInPictureMode(enabled: Boolean) {
                playerView?.applyPictureInPictureControllerMode(enabled)
            }

            override fun hideAppControls() {
                playerView?.hideController()
            }
        }
    }
    LaunchedEffect(previousVideo?.id, nextVideo?.id, player) {
        PlayerPipController.notifyPlayingChanged()
    }

    LaunchedEffect(qualityOptions) {
        if (selectedQualityKey != null && qualityOptions.none { it.key == selectedQualityKey }) {
            selectedQualityKey = null
        }
    }

    LaunchedEffect(onlineQualityOptions, settings.defaultQuality, stream.url) {
        val preferredOption = onlineQualityOptions.preferredOption(settings.defaultQuality)
        if (preferredOption != null && selectedQualityKey != preferredOption.key) {
            player.selectQuality(preferredOption)
            selectedQualityKey = preferredOption.key
            playerView?.findViewById<TextView>(R.id.yummy_player_quality)
                ?.setTag(R.id.yummy_player_quality, preferredOption.key)
        }
    }

    LaunchedEffect(player, settings.playerSpeed) {
        player.setPlaybackSpeed(settings.playerSpeed.value)
    }

    LaunchedEffect(player, currentVideo.id) {
        while (true) {
            delay(PLAYBACK_PROGRESS_SAVE_INTERVAL_MS)
            if (player.playbackState != Player.STATE_IDLE) {
                currentProgressCallback(
                    currentProgressVideo,
                    player.currentPosition.coerceAtLeast(0L),
                    player.duration.normalizedDurationMs(),
                )
            }
        }
    }

    DisposableEffect(player) {
        var fallbackReported = false
        var autoAdvanceReported = false
        var playbackStartedReported = false
        var playbackEndedReported = false
        PlayerPipController.registerPlayer(pipPlayerHandle)
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                PlayerPipController.notifyPlayingChanged()
                if (isPlaying && !playbackStartedReported) {
                    playbackStartedReported = true
                    onPlaybackStarted(currentVideo)
                }
            }

            override fun onTracksChanged(currentTracks: Tracks) {
                tracks = currentTracks
                val qualityLabels = currentTracks.videoQualityOptions()
                    .joinToString { it.label }
                    .ifBlank { "нет явных вариантов" }
                AppLog.w(
                    "YummyDroidPlayer",
                    "Available qualities=$qualityLabels, sourceMax=${stream.maxVideoHeight ?: 0}, " +
                        "source=${currentVideo.groupTitle}",
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && !playbackEndedReported) {
                    playbackEndedReported = true
                    onPlaybackEnded(currentVideo)
                }
                if (
                    playbackState == Player.STATE_ENDED &&
                    currentSettings.autoplayNextEpisode &&
                    !autoAdvanceReported
                ) {
                    autoAdvanceReported = true
                    nextVideo?.let { next ->
                        showVoiceFallbackToast(context, currentVideo, next)
                        onPlayVideoAt(next, 0L)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val httpError = error.cause as? HttpDataSource.InvalidResponseCodeException
                if (httpError != null) {
                    val uri = httpError.dataSpec.uri
                    AppLog.w(
                        "YummyDroidPlayer",
                        "Playback HTTP ${httpError.responseCode}: host=${uri.host}, file=${uri.lastPathSegment}, headers=${httpError.headerFields.keys}",
                    )
                } else {
                    AppLog.w("YummyDroidPlayer", "Playback failed: ${error.errorCodeName}", error)
                }
                if (!fallbackReported) {
                    fallbackReported = true
                    onPlaybackFailed(currentVideo, player.currentPosition.coerceAtLeast(0L))
                }
            }
        }
        player.addListener(listener)
        onDispose {
            currentProgressCallback(
                currentProgressVideo,
                player.currentPosition.coerceAtLeast(0L),
                player.duration.normalizedDurationMs(),
            )
            player.removeListener(listener)
            PlayerPipController.unregisterPlayer(pipPlayerHandle)
            playerView?.unbindSkipControls()
            player.release()
        }
    }

    AndroidView(
        factory = { viewContext ->
            val parent = FrameLayout(viewContext)
            LayoutInflater.from(viewContext).inflate(R.layout.yummy_player_view, parent, false) as PlayerView
        },
        update = { view ->
            playerView = view
            view.player = player
            view.setControllerAnimationEnabled(false)
            view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            view.installVideoZoomGestures(token = "${currentVideo.id}:${stream.url}")
            view.keepScreenOn = true
            view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            view.requestFocus()
            val previousPictureInPictureMode = view.getTag(R.id.yummy_player_view) as? Boolean
            if (previousPictureInPictureMode != isInPictureInPicture) {
                view.setTag(R.id.yummy_player_view, isInPictureInPicture)
                view.applyPictureInPictureControllerMode(isInPictureInPicture)
            }
            if (isInPictureInPicture) {
                view.hideController()
            } else {
                view.bindYummyController(
                    player = player,
                    animeTitle = animeTitle,
                    currentVideo = currentVideo,
                    isLocalPlayback = stream.url.startsWith("file:", ignoreCase = true) ||
                        currentVideo.localPlaybackUrl.isNotBlank(),
                    groups = groups,
                    selectedKey = selectedKey,
                    previousVideo = previousVideo,
                    nextVideo = nextVideo,
                    allowSubscription = allowSubscription,
                    subscriptionActive = subscriptionActive,
                    onToggleSubscription = onToggleSubscription,
                    qualityOptions = qualityOptions,
                    selectedQualityKey = selectedQualityKey,
                    onSelectedQualityKeyChange = { selectedQualityKey = it },
                    onSelectLocalQuality = { localFile ->
                        val positionMs = player.currentPosition.coerceAtLeast(0L)
                        player.pause()
                        onPlayVideoAt(currentVideo.withOfflineFile(localFile), positionMs)
                    },
                    onSelectGroup = onSelectGroup,
                    onPlayVideo = onPlayVideo,
                    onPlayVideoAt = onPlayVideoAt,
                    canUsePictureInPicture = canUsePictureInPicture,
                    onEnterPictureInPicture = onEnterPictureInPicture,
                    settings = settings,
                    texts = playerControlTexts,
                    onSettingsChange = onSettingsChange,
                    onBack = onBack,
                )
                if (previousPictureInPictureMode != false) {
                    view.restoreControllerAfterPictureInPicture()
                }
            }
        },
        modifier = modifier,
    )
}

@OptIn(UnstableApi::class)
private fun PlayerView.installVideoZoomGestures(token: String) {
    val currentToken = getTag(R.id.yummy_video_zoom_token_tag) as? String
    val currentState = getTag(R.id.yummy_video_zoom_state_tag) as? VideoZoomGestureState
    if (currentToken == token && currentState != null) {
        post { applyVideoZoom(currentState) }
        return
    }

    resetVideoZoom()
    val state = VideoZoomGestureState()
    val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val previousScale = state.scale
                state.scale = (state.scale * detector.scaleFactor).coerceIn(1f, 4f)
                if (state.scale <= 1.01f) {
                    state.scale = 1f
                    state.offsetX = 0f
                    state.offsetY = 0f
                } else if (previousScale > 0f) {
                    val scaleRatio = state.scale / previousScale
                    state.offsetX = (state.offsetX * scaleRatio) + ((detector.focusX - width / 2f) * (1f - scaleRatio))
                    state.offsetY = (state.offsetY * scaleRatio) + ((detector.focusY - height / 2f) * (1f - scaleRatio))
                }
                applyVideoZoom(state)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                if (state.scale <= 1.01f) {
                    state.scale = 1f
                    state.offsetX = 0f
                    state.offsetY = 0f
                    applyVideoZoom(state)
                }
            }
        },
    )

    setTag(R.id.yummy_video_zoom_token_tag, token)
    setTag(R.id.yummy_video_zoom_state_tag, state)
    setOnTouchListener { view, event ->
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                state.lastX = event.x
                state.lastY = event.y
                state.moved = false
                false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                hideController()
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 1 || scaleDetector.isInProgress) {
                    true
                } else if (state.scale > 1f) {
                    val dx = event.x - state.lastX
                    val dy = event.y - state.lastY
                    state.lastX = event.x
                    state.lastY = event.y
                    if (abs(dx) > 0.5f || abs(dy) > 0.5f) {
                        state.offsetX += dx
                        state.offsetY += dy
                        state.moved = state.moved || abs(dx) > 6f || abs(dy) > 6f
                        applyVideoZoom(state)
                    }
                    true
                } else {
                    false
                }
            }
            MotionEvent.ACTION_UP -> {
                if (state.scale > 1f && !state.moved) {
                    view.performClick()
                    showController()
                    true
                } else {
                    state.scale > 1f
                }
            }
            MotionEvent.ACTION_CANCEL -> false
            else -> event.pointerCount > 1 || state.scale > 1f
        }
    }
    post { applyVideoZoom(state) }
}

@OptIn(UnstableApi::class)
private fun PlayerView.applyVideoZoom(state: VideoZoomGestureState) {
    val surface = videoSurfaceView ?: return
    val scale = state.scale.coerceIn(1f, 4f)
    val maxOffsetX = surface.width * (scale - 1f) / 2f
    val maxOffsetY = surface.height * (scale - 1f) / 2f
    state.offsetX = if (maxOffsetX > 0f) state.offsetX.coerceIn(-maxOffsetX, maxOffsetX) else 0f
    state.offsetY = if (maxOffsetY > 0f) state.offsetY.coerceIn(-maxOffsetY, maxOffsetY) else 0f

    surface.pivotX = surface.width / 2f
    surface.pivotY = surface.height / 2f
    surface.scaleX = scale
    surface.scaleY = scale
    surface.translationX = state.offsetX
    surface.translationY = state.offsetY
}

@OptIn(UnstableApi::class)
private fun PlayerView.resetVideoZoom() {
    (getTag(R.id.yummy_video_zoom_state_tag) as? VideoZoomGestureState)?.let { state ->
        state.scale = 1f
        state.offsetX = 0f
        state.offsetY = 0f
        state.moved = false
    }
    videoSurfaceView?.apply {
        scaleX = 1f
        scaleY = 1f
        translationX = 0f
        translationY = 0f
    }
}

@OptIn(UnstableApi::class)
private fun PlayerView.applyPictureInPictureControllerMode(enabled: Boolean) {
    useController = !enabled
    controllerAutoShow = !enabled
    if (enabled) {
        hideController()
    }
    requestLayout()
    invalidate()
}

@OptIn(UnstableApi::class)
private fun PlayerView.restoreControllerAfterPictureInPicture() {
    useController = true
    controllerAutoShow = true
    hideController()
    requestLayout()
    post {
        requestLayout()
        invalidate()
        postDelayed({ showController() }, 220L)
    }
}

@OptIn(UnstableApi::class)
private fun PlayerView.handleRemoteInputAction(action: InputAction): Boolean {
    if (!useController) return false
    cancelSkipAutoCountdown()
    return when (action) {
        InputAction.Back -> {
            if (isControllerFullyVisible) {
                hideController()
                true
            } else {
                false
            }
        }
        InputAction.Up,
        InputAction.Down,
        InputAction.Confirm -> {
            if (!isControllerFullyVisible) {
                showController()
                post {
                    val focused = findViewById<View>(Media3R.id.exo_play_pause)?.requestFocus() == true
                    if (!focused) requestFocus()
                }
                true
            } else {
                false
            }
        }
        InputAction.Left,
        InputAction.Right -> {
            if (!isControllerFullyVisible) {
                showController()
                post {
                    val focused = findViewById<View>(Media3R.id.exo_play_pause)?.requestFocus() == true
                    if (!focused) requestFocus()
                }
                true
            } else {
                seekTimelineIfFocused(forward = action == InputAction.Right)
            }
        }
        InputAction.Play -> {
            player?.play()
            true
        }
        InputAction.Pause -> {
            player?.pause()
            true
        }
        InputAction.PlayPause -> {
            player?.let { currentPlayer ->
                if (currentPlayer.isPlaying) {
                    currentPlayer.pause()
                } else {
                    currentPlayer.play()
                }
                true
            } ?: (findViewById<View>(Media3R.id.exo_play_pause)?.performClick() == true)
        }
        InputAction.PreviousEpisode,
        InputAction.NextEpisode -> false
    }
}

@OptIn(UnstableApi::class)
private fun PlayerView.seekTimelineIfFocused(forward: Boolean): Boolean {
    val timeBarView = findViewById<View>(Media3R.id.exo_progress) ?: return false
    if (!timeBarView.hasFocus()) return false

    val currentPlayer = player ?: return false
    val duration = currentPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: return false
    val now = SystemClock.uptimeMillis()
    val direction = if (forward) 1 else -1
    val state = (getTag(R.id.yummy_player_timeline_scrub_state) as? TimelineScrubState)
        ?: TimelineScrubState(pendingPositionMs = currentPlayer.currentPosition.coerceIn(0L, duration))
    val keepsScrubbing = now - state.lastInputAtMs <= PLAYER_TIMELINE_SCRUB_ACCEL_WINDOW_MS &&
        state.lastDirection == direction

    state.repeatedInputCount = if (keepsScrubbing) state.repeatedInputCount + 1 else 1
    state.lastDirection = direction
    state.lastInputAtMs = now
    state.pendingPositionMs = (state.pendingPositionMs + direction.toLong() * state.stepMs()).coerceIn(0L, duration)

    state.commitRunnable?.let(::removeCallbacks)
    val commitRunnable = Runnable {
        val latestState = getTag(R.id.yummy_player_timeline_scrub_state) as? TimelineScrubState
            ?: return@Runnable
        currentPlayer.seekTo(latestState.pendingPositionMs.coerceIn(0L, duration))
        setTag(R.id.yummy_player_timeline_scrub_state, null)
    }
    state.commitRunnable = commitRunnable
    setTag(R.id.yummy_player_timeline_scrub_state, state)
    (timeBarView as? TimeBar)?.setPosition(state.pendingPositionMs)
    findViewById<TextView>(Media3R.id.exo_position)?.text = state.pendingPositionMs.formatPlaybackTime()
    postDelayed(commitRunnable, PLAYER_TIMELINE_SCRUB_COMMIT_DELAY_MS)
    showController()
    return true
}

private data class TimelineScrubState(
    var pendingPositionMs: Long,
    var lastInputAtMs: Long = 0L,
    var repeatedInputCount: Int = 0,
    var lastDirection: Int = 0,
    var commitRunnable: Runnable? = null,
) {
    fun stepMs(): Long {
        return when {
            repeatedInputCount <= 3 -> 5_000L
            repeatedInputCount <= 7 -> 10_000L
            repeatedInputCount <= 13 -> 30_000L
            else -> 60_000L
        }
    }
}

private fun Long.formatPlaybackTime(): String {
    val totalSeconds = (this / 1_000L).coerceAtLeast(0L)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3_600
    return if (hours > 0L) {
        "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
    } else {
        "%02d:%02d".format(Locale.ROOT, minutes, seconds)
    }
}

@OptIn(UnstableApi::class)
private fun PlayerView.bindYummyController(
    player: ExoPlayer,
    animeTitle: String,
    currentVideo: VideoVariant,
    isLocalPlayback: Boolean,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    previousVideo: VideoVariant?,
    nextVideo: VideoVariant?,
    allowSubscription: Boolean,
    subscriptionActive: Boolean,
    onToggleSubscription: () -> Unit,
    qualityOptions: List<QualityOption>,
    selectedQualityKey: String?,
    onSelectedQualityKeyChange: (String) -> Unit,
    onSelectLocalQuality: (OfflineVideoFile) -> Unit,
    onSelectGroup: (String, VideoVariant?, Long) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    canUsePictureInPicture: Boolean,
    onEnterPictureInPicture: () -> Unit,
    settings: AppSettings,
    texts: PlayerControlTexts,
    onSettingsChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    findViewById<TextView>(R.id.yummy_player_title)?.text = animeTitle.ifBlank { texts.title }
    findViewById<TextView>(R.id.yummy_player_subtitle)?.text =
        currentVideo.playbackSubtitle()
    findViewById<TextView>(R.id.yummy_player_info)?.text =
        currentVideo.playbackSourceLabel(isLocalPlayback)

    findViewById<View>(Media3R.id.exo_settings)?.visibility = View.GONE
    findViewById<View>(R.id.yummy_player_back)?.setOnClickListener { onBack() }

    findViewById<View>(R.id.yummy_episode_previous)?.apply {
        visibility = if (previousVideo != null) View.VISIBLE else View.GONE
        setOnClickListener {
            previousVideo?.let {
                showVoiceFallbackToast(context, currentVideo, it)
                player.pause()
                onPlayVideoAt(it, 0L)
            }
        }
    }

    findViewById<View>(R.id.yummy_episode_next)?.apply {
        visibility = if (nextVideo != null) View.VISIBLE else View.GONE
        setOnClickListener {
            nextVideo?.let {
                showVoiceFallbackToast(context, currentVideo, it)
                player.pause()
                onPlayVideoAt(it, 0L)
            }
        }
    }

    findViewById<TextView>(R.id.yummy_player_voice)?.apply {
        text = texts.voice
        visibility = if (groups.size > 1) View.VISIBLE else View.GONE
        setOnClickListener {
            showController()
            showVoicePopup(
                anchor = this,
                groups = groups,
                selectedKey = selectedKey,
                preferredGroupKey = currentVideo.groupKey,
                currentVideo = currentVideo,
                onSelectGroup = { groupKey, replacement ->
                    player.pause()
                    onSelectGroup(groupKey, replacement, player.currentPosition.coerceAtLeast(0L))
                },
            )
        }
    }

    findViewById<TextView>(R.id.yummy_player_quality)?.apply {
        text = texts.quality
        visibility = if (qualityOptions.isNotEmpty()) View.VISIBLE else View.GONE
        setOnClickListener {
            showController()
            showQualityPopup(
                anchor = this,
                player = player,
                options = qualityOptions,
                selectedQualityKey = selectedQualityKey,
                onSelectedQualityKeyChange = onSelectedQualityKeyChange,
                onSelectLocalQuality = onSelectLocalQuality,
            )
        }
    }

    findViewById<TextView>(R.id.yummy_player_subscription)?.apply {
        text = if (subscriptionActive) texts.subscribed else texts.subscription
        visibility = if (allowSubscription) View.VISIBLE else View.GONE
        applyPlayerSubscriptionState(subscriptionActive)
        setOnClickListener {
            showController()
            onToggleSubscription()
        }
    }

    findViewById<TextView>(R.id.yummy_player_speed)?.apply {
        text = settings.playerSpeed.title
        visibility = View.VISIBLE
        setOnClickListener {
            showController()
            showSpeedPopup(
                anchor = this,
                selected = settings.playerSpeed,
                onSelected = { onSettingsChange(settings.copy(playerSpeed = it)) },
            )
        }
    }

    findViewById<TextView>(R.id.yummy_player_pip)?.apply {
        text = "PiP"
        visibility = if (canUsePictureInPicture) View.VISIBLE else View.GONE
        setOnClickListener {
            hideController()
            postDelayed({ onEnterPictureInPicture() }, PIP_ENTER_DELAY_MS)
        }
    }

    bindSkipControls(player = player, currentVideo = currentVideo, texts = texts)
    configurePlayerFocusNavigation(previousVideo != null, nextVideo != null)
}

private fun PlayerView.configurePlayerFocusNavigation(
    hasPreviousVideo: Boolean,
    hasNextVideo: Boolean,
) {
    val back = findViewById<View>(R.id.yummy_player_back)
    val previous = findViewById<View>(R.id.yummy_episode_previous)
    val playPause = findViewById<View>(Media3R.id.exo_play_pause)
    val next = findViewById<View>(R.id.yummy_episode_next)
    val timeBar = findViewById<View>(Media3R.id.exo_progress)
    val bottomControls = listOfNotNull(
        findViewById<View>(R.id.yummy_player_voice)?.takeIf { it.isVisible },
        findViewById<View>(R.id.yummy_player_quality)?.takeIf { it.isVisible },
        findViewById<View>(R.id.yummy_player_subscription)?.takeIf { it.isVisible },
        findViewById<View>(R.id.yummy_player_speed)?.takeIf { it.isVisible },
        findViewById<View>(R.id.yummy_player_pip)?.takeIf { it.isVisible },
    )
    val firstBottomControl = bottomControls.firstOrNull()
    val timeBarFocusId = timeBar?.id ?: firstBottomControl?.id ?: Media3R.id.exo_play_pause

    playPause?.apply {
        nextFocusLeftId = if (hasPreviousVideo) R.id.yummy_episode_previous else id
        nextFocusRightId = if (hasNextVideo) R.id.yummy_episode_next else id
        nextFocusUpId = R.id.yummy_player_back
        nextFocusDownId = timeBarFocusId
    }

    previous?.apply {
        nextFocusLeftId = id
        nextFocusRightId = Media3R.id.exo_play_pause
        nextFocusUpId = R.id.yummy_player_back
        nextFocusDownId = timeBarFocusId
    }

    next?.apply {
        nextFocusLeftId = Media3R.id.exo_play_pause
        nextFocusRightId = id
        nextFocusUpId = R.id.yummy_player_back
        nextFocusDownId = timeBarFocusId
    }

    back?.nextFocusDownId = Media3R.id.exo_play_pause

    timeBar?.apply {
        isFocusable = true
        isFocusableInTouchMode = false
        nextFocusLeftId = id
        nextFocusRightId = id
        nextFocusUpId = Media3R.id.exo_play_pause
        nextFocusDownId = firstBottomControl?.id ?: Media3R.id.exo_play_pause
    }

    bottomControls.forEachIndexed { index, view ->
        view.nextFocusUpId = timeBar?.id ?: Media3R.id.exo_play_pause
        view.nextFocusDownId = view.id
        view.nextFocusLeftId = bottomControls.getOrNull(index - 1)?.id ?: view.id
        view.nextFocusRightId = bottomControls.getOrNull(index + 1)?.id ?: view.id
    }
}

private fun TextView.applyPlayerSubscriptionState(active: Boolean) {
    backgroundTintList = ColorStateList.valueOf(if (active) PLAYER_ACCENT_COLOR else PLAYER_CONTROL_COLOR)
    setTextColor(if (active) PLAYER_ACCENT_CONTENT_COLOR else PLAYER_CONTROL_CONTENT_COLOR)
}

private val PLAYER_ACCENT_COLOR: Int = 0xFFFFB454.toInt()
private val PLAYER_ACCENT_CONTENT_COLOR: Int = 0xFF1B1305.toInt()
private val PLAYER_CONTROL_COLOR: Int = 0xFF111827.toInt()
private val PLAYER_CONTROL_CONTENT_COLOR: Int = 0xFFF3F6FA.toInt()

@OptIn(UnstableApi::class)
private fun PlayerView.bindSkipControls(
    player: ExoPlayer,
    currentVideo: VideoVariant,
    texts: PlayerControlTexts,
) {
    if ((getTag(R.id.yummy_player_skip_video_id) as? Long) != currentVideo.id) {
        unbindSkipControls()
        setTag(R.id.yummy_player_skip_video_id, currentVideo.id)
        setTag(R.id.yummy_player_skip_dismissed_keys, mutableSetOf<String>())
    }

    val container = findViewById<View>(R.id.yummy_skip_controls) ?: return
    val skipButton = findViewById<TextView>(R.id.yummy_skip_skip) ?: return
    val watchButton = findViewById<TextView>(R.id.yummy_skip_watch) ?: return
    setTag(R.id.yummy_player_skip_text_tag, texts.skip)
    watchButton.text = texts.watch
    if (currentVideo.skipSegments.isEmpty()) {
        container.visibility = View.GONE
        return
    }
    (getTag(R.id.yummy_player_skip_poll_runnable) as? Runnable)?.let(::removeCallbacks)

    fun dismissedKeys(): MutableSet<String> {
        @Suppress("UNCHECKED_CAST")
        return getTag(R.id.yummy_player_skip_dismissed_keys) as? MutableSet<String>
            ?: mutableSetOf<String>().also { setTag(R.id.yummy_player_skip_dismissed_keys, it) }
    }

    fun clearActivePrompt() {
        (getTag(R.id.yummy_player_skip_countdown_runnable) as? Runnable)?.let(::removeCallbacks)
        setTag(R.id.yummy_player_skip_countdown_runnable, null)
        setTag(R.id.yummy_player_active_skip_key, null)
        setTag(R.id.yummy_player_active_skip_segment, null)
        setTag(R.id.yummy_player_skip_auto_cancelled, null)
        container.visibility = View.GONE
    }

    fun dismissActivePrompt() {
        val prompt = getTag(R.id.yummy_player_active_skip_segment) as? ActiveSkipPrompt
        if (prompt != null) {
            dismissedKeys().add(prompt.key)
        }
        clearActivePrompt()
    }

    fun skipActivePrompt() {
        val prompt = getTag(R.id.yummy_player_active_skip_segment) as? ActiveSkipPrompt ?: return
        dismissedKeys().add(prompt.key)
        clearActivePrompt()
        player.seekTo(prompt.segment.endMs)
    }

    fun updateSkipButtonText(state: SkipCountdownState) {
        val suffix = if (state.autoSkipEnabled) " ${state.remainingSeconds}" else ""
        skipButton.text = "${texts.skip}$suffix"
    }

    fun scheduleCountdown(prompt: ActiveSkipPrompt) {
        val state = SkipCountdownState(
            remainingSeconds = SKIP_PROMPT_COUNTDOWN_SECONDS,
            autoSkipEnabled = true,
        )
        setTag(R.id.yummy_player_skip_auto_cancelled, state)
        updateSkipButtonText(state)

        fun tick() {
            val activeKey = getTag(R.id.yummy_player_active_skip_key) as? String
            if (activeKey != prompt.key || !state.autoSkipEnabled) return
            state.remainingSeconds -= 1
            if (state.remainingSeconds <= 0) {
                skipActivePrompt()
            } else {
                updateSkipButtonText(state)
                val nextTick = Runnable { tick() }
                setTag(R.id.yummy_player_skip_countdown_runnable, nextTick)
                postDelayed(nextTick, 1_000L)
            }
        }

        val firstTick = Runnable { tick() }
        setTag(R.id.yummy_player_skip_countdown_runnable, firstTick)
        postDelayed(firstTick, 1_000L)
    }

    fun showPrompt(segment: VideoSkipSegment) {
        val key = segment.key
        if ((getTag(R.id.yummy_player_active_skip_key) as? String) == key) return
        val prompt = ActiveSkipPrompt(key = key, segment = segment)
        setTag(R.id.yummy_player_active_skip_key, key)
        setTag(R.id.yummy_player_active_skip_segment, prompt)
        container.visibility = View.VISIBLE
        showController()
        skipButton.setOnClickListener { skipActivePrompt() }
        watchButton.setOnClickListener { dismissActivePrompt() }
        skipButton.nextFocusRightId = R.id.yummy_skip_watch
        skipButton.nextFocusLeftId = R.id.yummy_skip_skip
        watchButton.nextFocusLeftId = R.id.yummy_skip_skip
        watchButton.nextFocusRightId = R.id.yummy_skip_watch
        scheduleCountdown(prompt)
        post { skipButton.requestFocus() }
    }

    val pollRunnable = object : Runnable {
        override fun run() {
            val position = player.currentPosition.coerceAtLeast(0L)
            val activePrompt = getTag(R.id.yummy_player_active_skip_segment) as? ActiveSkipPrompt
            if (activePrompt != null && !activePrompt.segment.isActive(position)) {
                dismissedKeys().add(activePrompt.key)
                clearActivePrompt()
            }
            if (container.visibility != View.VISIBLE) {
                val segment = currentVideo.skipSegments.firstOrNull { segment ->
                    segment.key !in dismissedKeys() && segment.isActive(position)
                }
                if (segment != null) {
                    showPrompt(segment)
                }
            }
            postDelayed(this, SKIP_PROMPT_POLL_MS)
        }
    }

    setTag(R.id.yummy_player_skip_poll_runnable, pollRunnable)
    post(pollRunnable)
}

private fun PlayerView.cancelSkipAutoCountdown() {
    val state = getTag(R.id.yummy_player_skip_auto_cancelled) as? SkipCountdownState ?: return
    if (!state.autoSkipEnabled) return
    state.autoSkipEnabled = false
    val skipText = getTag(R.id.yummy_player_skip_text_tag) as? String ?: defaultPlayerControlTexts.skip
    findViewById<TextView>(R.id.yummy_skip_skip)?.text = skipText
    (getTag(R.id.yummy_player_skip_countdown_runnable) as? Runnable)?.let(::removeCallbacks)
    setTag(R.id.yummy_player_skip_countdown_runnable, null)
}

private fun PlayerView.unbindSkipControls() {
    (getTag(R.id.yummy_player_skip_poll_runnable) as? Runnable)?.let(::removeCallbacks)
    (getTag(R.id.yummy_player_skip_countdown_runnable) as? Runnable)?.let(::removeCallbacks)
    setTag(R.id.yummy_player_skip_poll_runnable, null)
    setTag(R.id.yummy_player_skip_countdown_runnable, null)
    setTag(R.id.yummy_player_active_skip_key, null)
    setTag(R.id.yummy_player_active_skip_segment, null)
    setTag(R.id.yummy_player_skip_auto_cancelled, null)
    findViewById<View>(R.id.yummy_skip_controls)?.visibility = View.GONE
}

private fun Long.normalizedDurationMs(): Long {
    return takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
}

private fun TextView.setPlayerControlEnabled(enabled: Boolean) {
    isEnabled = enabled
    isFocusable = enabled
    alpha = if (enabled) 1f else 0.45f
}

private fun showVoicePopup(
    anchor: View,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    preferredGroupKey: String?,
    currentVideo: VideoVariant,
    onSelectGroup: (String, VideoVariant?) -> Unit,
) {
    val entries = groups.entries.sortedBy { it.value.firstOrNull()?.voiceTitle.orEmpty() }
    val totalEpisodeCount = groups.values
        .flatten()
        .map { it.episodeSlotKey }
        .distinct()
        .size
        .coerceAtLeast(1)
    PopupMenu(anchor.context, anchor).apply {
        entries.forEachIndexed { index, entry ->
            val voiceTitle = entry.value.firstOrNull()?.voiceTitle.orEmpty().ifBlank { "Озвучка ${index + 1}" }
            val availableEpisodes = entry.value.map { it.episodeSlotKey }.distinct().size
            val title = "$voiceTitle  $availableEpisodes / $totalEpisodeCount"
            menu.add(VOICE_MENU_GROUP_ID, index, index, title).apply {
                isCheckable = true
                isChecked = entry.key == selectedKey
            }
        }
        menu.setGroupCheckable(VOICE_MENU_GROUP_ID, true, true)
        setOnMenuItemClickListener { item ->
            val entry = entries.getOrNull(item.itemId) ?: return@setOnMenuItemClickListener false
            val sortedVideos = entry.value.sortedForPlayer(preferredGroupKey)
            val replacement = sortedVideos.firstOrNull { it.sameEpisodeSlot(currentVideo) }
                ?: sortedVideos.firstOrNull()
            val groupKey = replacement?.groupKey ?: entry.value.firstOrNull()?.groupKey ?: entry.key
            anchor.post { onSelectGroup(groupKey, replacement) }
            true
        }
        show()
    }
}

@OptIn(UnstableApi::class)
private fun showQualityPopup(
    anchor: View,
    player: ExoPlayer,
    options: List<QualityOption>,
    selectedQualityKey: String?,
    onSelectedQualityKeyChange: (String) -> Unit,
    onSelectLocalQuality: (OfflineVideoFile) -> Unit,
) {
    PopupMenu(anchor.context, anchor).apply {
        val effectiveSelectedQualityKey = anchor.getTag(R.id.yummy_player_quality) as? String
            ?: selectedQualityKey
            ?: player.currentQualityKey()
        options.forEachIndexed { index, option ->
            menu.add(QUALITY_MENU_GROUP_ID, index, index, option.label).apply {
                isCheckable = true
                isChecked = option.matchesSelectedQualityKey(effectiveSelectedQualityKey)
            }
        }
        menu.setGroupCheckable(QUALITY_MENU_GROUP_ID, true, true)
        setOnMenuItemClickListener { item ->
            val option = options.getOrNull(item.itemId) ?: return@setOnMenuItemClickListener false
            option.localFile?.let { localFile ->
                anchor.post { onSelectLocalQuality(localFile) }
            } ?: player.selectQuality(option)
            anchor.setTag(R.id.yummy_player_quality, option.key)
            onSelectedQualityKeyChange(option.key)
            true
        }
        show()
    }
}

private fun showSpeedPopup(
    anchor: View,
    selected: PlayerSpeed,
    onSelected: (PlayerSpeed) -> Unit,
) {
    PopupMenu(anchor.context, anchor).apply {
        PlayerSpeed.entries.forEachIndexed { index, speed ->
            menu.add(SPEED_MENU_GROUP_ID, index, index, speed.title).apply {
                isCheckable = true
                isChecked = speed == selected
            }
        }
        menu.setGroupCheckable(SPEED_MENU_GROUP_ID, true, true)
        setOnMenuItemClickListener { item ->
            val speed = PlayerSpeed.entries.getOrNull(item.itemId) ?: return@setOnMenuItemClickListener false
            onSelected(speed)
            true
        }
        show()
    }
}

@OptIn(UnstableApi::class)
private fun ExoPlayer.selectQuality(option: QualityOption) {
    val group = option.group ?: return
    trackSelectionParameters = trackSelectionParameters
        .buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
        .setMaxVideoBitrate(Int.MAX_VALUE)
        .addOverride(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
        .build()
}

private fun List<QualityOption>.preferredOption(preferredQuality: PreferredQuality): QualityOption? {
    val preferredHeight = preferredQuality.height ?: return null
    return firstOrNull { it.height == preferredHeight }
}

@OptIn(UnstableApi::class)
private fun PlayerDecoderMode.mediaCodecSelector(): MediaCodecSelector {
    return when (this) {
        PlayerDecoderMode.Auto -> MediaCodecSelector.DEFAULT
        PlayerDecoderMode.Hardware -> MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val defaults = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder,
            )
            defaults.filter { it.hardwareAccelerated }.ifEmpty { defaults }
        }
        PlayerDecoderMode.Software -> MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val defaults = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder,
            )
            defaults.filter { it.softwareOnly }.ifEmpty { defaults }
        }
    }
}

@OptIn(UnstableApi::class)
private fun Player.currentQualityKey(): String? {
    return currentTracks
        .groups
        .asSequence()
        .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
        .flatMap { group ->
            (0 until group.length)
                .asSequence()
                .filter { trackIndex -> group.isTrackSelected(trackIndex) }
                .map { trackIndex ->
                    val format = group.getTrackFormat(trackIndex)
                    "${format.height}:${format.bitrate}:${format.qualityLabel()}"
                }
        }
        .firstOrNull()
}

private data class QualityOption(
    val group: Tracks.Group?,
    val trackIndex: Int,
    val label: String,
    val height: Int,
    val bitrate: Int,
    val key: String,
    val localFile: OfflineVideoFile? = null,
)

@OptIn(UnstableApi::class)
private fun Tracks.videoQualityOptions(): List<QualityOption> {
    return groups
        .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSupported }
        .flatMap { group ->
            (0 until group.length)
                .filter { trackIndex -> group.isTrackSupported(trackIndex) }
                .map { trackIndex ->
                    val format = group.getTrackFormat(trackIndex)
                    QualityOption(
                        group = group,
                        trackIndex = trackIndex,
                        label = format.qualityLabel(),
                        height = format.height,
                        bitrate = format.bitrate,
                        key = "${format.height}:${format.bitrate}:${format.qualityLabel()}",
                    )
                }
        }
        .distinctBy { "${it.height}:${it.bitrate}:${it.label}" }
        .sortedWith(
            compareByDescending<QualityOption> { it.height.takeIf { height -> height > 0 } ?: 0 }
                .thenByDescending { it.bitrate.takeIf { bitrate -> bitrate > 0 } ?: 0 },
        )
}

@OptIn(UnstableApi::class)
private fun androidx.media3.common.Format.qualityLabel(): String {
    val resolution = when {
        height > 0 -> "${height}p"
        width > 0 -> "${width}px"
        else -> "Видео"
    }
    val bitrateLabel = bitrate.takeIf { it > 0 }?.let { "${it / 1000} кбит/с" }
    return listOfNotNull(resolution, bitrateLabel).joinToString(" • ")
}

@Composable
private fun <T> AnimeListStateContent(
    state: LoadState<List<T>>,
    onRetry: () -> Unit,
    emptyMessage: String,
    content: @Composable (List<T>) -> Unit,
) {
    when (state) {
        LoadState.Loading -> LoadingPane(Modifier.fillMaxSize())
        is LoadState.Error -> ErrorPane(
            message = state.message,
            onRetry = onRetry,
            modifier = Modifier.fillMaxSize(),
        )
        is LoadState.Ready -> {
            if (state.data.isEmpty()) {
                EmptyPane(emptyMessage, Modifier.fillMaxSize())
            } else {
                content(state.data)
            }
        }
    }
}

@Composable
private fun DetailsStateContent(
    state: LoadState<AnimeDetails>,
    onRetry: () -> Unit,
    emptyMessage: String,
    content: @Composable (AnimeDetails) -> Unit,
) {
    when (state) {
        LoadState.Loading -> LoadingPane(Modifier.fillMaxSize())
        is LoadState.Error -> ErrorPane(
            message = state.message.ifBlank { emptyMessage },
            onRetry = onRetry,
            modifier = Modifier.fillMaxSize(),
        )
        is LoadState.Ready -> content(state.data)
    }
}

@Composable
private fun LoadingPane(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorPane(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = onRetry, modifier = Modifier.focusRing(RoundedCornerShape(8.dp))) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(uiText("Повторить"))
            }
        }
    }
}

@Composable
private fun EmptyPane(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PosterImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

@Composable
private fun RatingBadge(
    rating: Double,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(15.dp))
            Text(
                text = formatRating(rating),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ViewsBadge(
    views: Long,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp))
            Text(
                text = formatViews(views),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatRating(value: Double): String {
    return String.format(Locale.US, "%.1f", value)
}

private fun formatDuration(seconds: Int?): String? {
    if (seconds == null || seconds <= 0) return null
    val minutes = seconds / 60
    val rest = seconds % 60
    return "%d:%02d".format(Locale.US, minutes, rest)
}

internal fun formatViews(views: Long): String {
    return when {
        views >= 10_000_000 -> String.format(Locale.US, "%.0f млн", views / 1_000_000.0)
        views >= 1_000_000 -> String.format(Locale.US, "%.1f млн", views / 1_000_000.0)
        views >= 100_000 -> String.format(Locale.US, "%.0f тыс", views / 1_000.0)
        views >= 1_000 -> String.format(Locale.US, "%.1f тыс", views / 1_000.0)
        else -> "$views"
    }
}
