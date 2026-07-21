package me.yummydroid.app.ui

import android.app.Activity
import android.app.UiModeManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
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
import androidx.core.net.toUri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.R as Media3R
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TimeBar
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.Collator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import me.yummydroid.app.AppLog
import me.yummydroid.app.AppRoute
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.BuildConfig
import me.yummydroid.app.HCaptchaActivity
import me.yummydroid.app.InputAction
import me.yummydroid.app.InputActionEvent
import me.yummydroid.app.LoadState
import me.yummydroid.app.DownloadTaskState
import me.yummydroid.app.formatByteSize
import me.yummydroid.app.formatCommentTimestamp
import me.yummydroid.app.formatDuration
import me.yummydroid.app.formatPlaybackTime
import me.yummydroid.app.formatRating
import me.yummydroid.app.formatScheduleTimestamp
import me.yummydroid.app.formatViews
import me.yummydroid.app.formatWatchedAtTimestamp
import me.yummydroid.app.PagingUiState
import me.yummydroid.app.PipPlayerHandle
import me.yummydroid.app.PlaybackRecoveryCandidate
import me.yummydroid.app.PlayerPipController
import me.yummydroid.app.R
import me.yummydroid.app.readyDataOrNull
import me.yummydroid.app.readyListOrEmpty
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
import me.yummydroid.app.data.APP_USER_AGENT
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.ContentLanguage
import me.yummydroid.app.data.DEFAULT_SITE_BASE_URL
import me.yummydroid.app.data.FilterCatalog
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PlayerBufferPreset
import me.yummydroid.app.data.PlayerDecoderMode
import me.yummydroid.app.data.PlayerSpeed
import me.yummydroid.app.data.PosterCardSize
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.RelatedAnime
import me.yummydroid.app.data.RatingDetails
import me.yummydroid.app.data.ResolvedSubtitleTrack
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.ScheduleAnime
import me.yummydroid.app.data.SiteDomainResolver
import me.yummydroid.app.data.SourceQuality
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.UserProfile
import me.yummydroid.app.data.VideoSkipSegment
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.canShowVideoSubscriptions
import me.yummydroid.app.data.bestSourceQualityPerHeight
import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.defaultVideoResolveClient
import me.yummydroid.app.data.downloadedEpisodeCountForVoice
import me.yummydroid.app.data.episodeOrderValue
import me.yummydroid.app.data.isSubscribedTo
import me.yummydroid.app.data.isNewerThanVersion
import me.yummydroid.app.data.isSameEpisodeAs
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingDubbingKey
import me.yummydroid.app.data.matchingDubbingTitle
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.matchingVoiceTitle
import me.yummydroid.app.data.ageRatingFilterOptions
import me.yummydroid.app.data.qualityHeight
import me.yummydroid.app.data.seasonFilterOptions
import me.yummydroid.app.data.sourceProviderRank
import me.yummydroid.app.data.statusFilterOptions
import me.yummydroid.app.data.translateFilterOptions
import me.yummydroid.app.data.userMarkFilterOptions
import me.yummydroid.app.data.normalizeSiteBaseUrl
import me.yummydroid.app.data.normalizedSiteBaseUrls
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.components.focusRing
import me.yummydroid.app.ui.theme.YummyAlpha
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySizes
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.YummySurfaceRole
import me.yummydroid.app.ui.theme.yummySurfaceBorder
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceContentColor
import me.yummydroid.app.data.preferredProfileSubscription
import me.yummydroid.app.data.profileDisplayKey
import me.yummydroid.app.data.profileVoiceTitle

internal val LocalUiLanguage = staticCompositionLocalOf { ContentLanguage.Russian }

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
        BrowseSection.Catalog -> UiStringKey.BrowseCatalog
        BrowseSection.Schedule -> UiStringKey.BrowseSchedule
        BrowseSection.History -> UiStringKey.BrowseHistory
        BrowseSection.Downloads -> UiStringKey.BrowseDownloads
    },
)

private fun visibleBrowseSections(isAuthorized: Boolean): List<BrowseSection> {
    return if (isAuthorized) {
        listOf(BrowseSection.Catalog, BrowseSection.Schedule, BrowseSection.History)
    } else {
        listOf(BrowseSection.Catalog, BrowseSection.Schedule)
    }
}

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
private fun PlayerBufferPreset.localizedTitle(): String = uiText(title)

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
    "Установлена актуальная версия" to "The latest version is installed",
    "Проверка еще не выполнена." to "Update check has not run yet.",
    "Фильтры" to "Filters",
    "Расширенный режим" to "Advanced mode",
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
    "Объём буфера" to "Buffer size",
    "Автоподстройка экрана под видео" to "Match display to video",
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
    "Серия" to "Episode",
    "Эпизод" to "Episode",
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
    "Компактный" to "Compact",
    "Стандартный" to "Standard",
    "Большой" to "Large",
    "Максимальный" to "Maximum",
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
    "Выкл." to "Off",
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
    "Установлена актуальная версия" to "Встановлено актуальну версію",
    "Проверка еще не выполнена." to "Перевірку ще не виконано.",
    "Фильтры" to "Фільтри",
    "Расширенный режим" to "Розширений режим",
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
    "Объём буфера" to "Обсяг буфера",
    "Автоподстройка экрана под видео" to "Автопідлаштування екрана під відео",
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
    "Серия" to "Серія",
    "Эпизод" to "Епізод",
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
    "Компактный" to "Компактний",
    "Стандартный" to "Стандартний",
    "Большой" to "Великий",
    "Максимальный" to "Максимальний",
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
    "Выкл." to "Вимк.",
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
    onPlayVideoAtQuality: (VideoVariant, Long, PreferredQuality) -> Unit,
    onRetryVideo: () -> Unit,
    onPlaybackFailed: (VideoVariant, Long) -> Unit,
    onPrepareFallbackSource: (VideoVariant) -> Unit,
    onSwitchToPreparedFallbackSource: (VideoVariant, Long) -> Boolean,
    onRecoveryPrebufferReady: (Long, Long) -> Boolean,
    onRecoveryPrebufferFailed: (Long) -> Unit,
    onPlaybackStarted: (VideoVariant) -> Unit,
    onPlaybackEnded: (VideoVariant) -> Unit,
    onPlaybackProgress: (VideoVariant, Long, Long) -> Unit,
    canUsePictureInPicture: Boolean,
    onEnterPictureInPicture: () -> Unit,
    onLogin: (String, String, String?) -> Unit,
    onCaptchaSolved: (String) -> Unit,
    onCaptchaCanceled: (String?) -> Unit,
    onLogout: () -> Unit,
    onOpenLibraryFilter: () -> Unit,
    onSelectAnimeListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    onSetAnimeRating: (Int?) -> Unit,
    onAddAnimeComment: (String) -> Unit,
    onLoadMoreAnimeComments: () -> Unit,
    onToggleVideoSubscription: (VideoVariant) -> Unit,
    onUnsubscribeVideoSubscription: (VideoSubscription) -> Unit,
    onRefreshVideoSubscriptions: () -> Unit,
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
    registerInputActionHandler: (((InputActionEvent) -> Boolean)?) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val appScope = rememberCoroutineScope()
    var loginDialogOpen by remember { mutableStateOf(false) }
    var profileDialogOpen by remember { mutableStateOf(false) }
    var settingsDialogOpen by remember { mutableStateOf(false) }
    var autoUpdatePromptDismissed by remember { mutableStateOf(false) }
    var modalInputActionHandler by remember { mutableStateOf<((InputAction) -> Boolean)?>(null) }
    var playerInputActionHandler by remember { mutableStateOf<((InputActionEvent) -> Boolean)?>(null) }
    var focusedCatalogAnimeId by rememberSaveable { mutableLongStateOf(0L) }
    var pendingCatalogFocusAnimeId by rememberSaveable { mutableLongStateOf(0L) }
    var homeBackFocusResetNonce by rememberSaveable { mutableLongStateOf(0L) }
    CaptchaChallengeEffect(
        requestNonce = state.auth.captchaRequestNonce,
        onSolved = onCaptchaSolved,
        onCanceled = onCaptchaCanceled,
    )
    val catalogGridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    val scheduleListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val historyGridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    val detailsScreenStates = rememberSaveable(saver = AnimeDetailsScreenStatesSaver) {
        mutableStateMapOf<Long, AnimeDetailsScreenState>()
    }
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
            allVideos = state.videos.readyListOrEmpty(),
            selectedGroup = state.selectedVideoGroup,
            forward = forward,
        ) ?: return@playAdjacentEpisode false
        onSelectVideoGroup(adjacent.groupKey)
        onPlayVideoAtQuality(adjacent, 0L, route.preferredQuality)
        true
    }
    fun isHomeAtTop(): Boolean {
        if (state.route != AppRoute.Home) return false
        return when (state.homeSection) {
            BrowseSection.Catalog ->
                catalogGridState.firstVisibleItemIndex == 0 && catalogGridState.firstVisibleItemScrollOffset == 0
            BrowseSection.Schedule ->
                scheduleListState.firstVisibleItemIndex == 0 && scheduleListState.firstVisibleItemScrollOffset == 0
            BrowseSection.History ->
                historyGridState.firstVisibleItemIndex == 0 && historyGridState.firstVisibleItemScrollOffset == 0
            BrowseSection.Downloads -> true
        }
    }

    fun scrollHomeToTopFromBack(): Boolean {
        if (state.route != AppRoute.Home) return false
        val isAtTop = isHomeAtTop()
        if (isAtTop) return false
        appScope.launch {
            when (state.homeSection) {
                BrowseSection.Catalog -> catalogGridState.animateScrollToItem(0)
                BrowseSection.Schedule -> scheduleListState.animateScrollToItem(0)
                BrowseSection.History -> historyGridState.animateScrollToItem(0)
                BrowseSection.Downloads -> Unit
            }
            homeBackFocusResetNonce += 1L
        }
        return true
    }
    val inputActionHandler by rememberUpdatedState {
            event: InputActionEvent ->
        val action = event.action
        modalInputActionHandler?.let { handler ->
            if (handler(action)) {
                return@rememberUpdatedState true
            }
        }
        if (state.route is AppRoute.Player) {
            when {
                playerInputActionHandler?.invoke(event) == true -> true
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
                InputAction.Up,
                InputAction.Down,
                InputAction.Left,
                InputAction.Right -> false
                InputAction.PreviousEpisode -> playAdjacentEpisode(false)
                InputAction.NextEpisode -> playAdjacentEpisode(true)
                InputAction.Play,
                InputAction.Pause,
                InputAction.PlayPause -> false
                InputAction.Back -> {
                    if (scrollHomeToTopFromBack()) {
                        true
                    } else if (state.canNavigateBack) {
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

    val shouldHandleSystemBack = state.canNavigateBack ||
        state.route is AppRoute.Player ||
        (state.route == AppRoute.Home && !isHomeAtTop())

    BackHandler(enabled = shouldHandleSystemBack) {
        inputActionHandler(InputActionEvent(InputAction.Back))
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
                scheduleListState = scheduleListState,
                historyGridState = historyGridState,
                pendingCatalogFocusAnimeId = pendingCatalogFocusAnimeId,
                homeBackFocusResetNonce = homeBackFocusResetNonce,
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
                onRequestHomeFocusReset = { homeBackFocusResetNonce += 1L },
            )
            is AppRoute.Details -> DetailsScreenModern(
                state = state,
                screenStates = detailsScreenStates,
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
                preferredQuality = route.preferredQuality,
                allVideos = state.videos.readyListOrEmpty(),
                selectedGroup = state.selectedVideoGroup,
                streamState = state.playerStream,
                pendingPlaybackRecovery = state.pendingPlaybackRecovery,
                isInPictureInPicture = isInPictureInPicture,
                forcedOfflineMode = state.forcedOfflineMode,
                allowSubscriptions = state.auth.profile != null &&
                    !state.forcedOfflineMode &&
                    (state.details.readyDataOrNull()?.canShowVideoSubscriptions() == true),
                subscriptions = state.detailsExtras.readyDataOrNull()?.subscriptions.orEmpty(),
                onSelectGroup = onSelectVideoGroup,
                onPlayVideo = onPlayVideo,
                onPlayVideoAt = onPlayVideoAt,
                onPlayVideoAtQuality = onPlayVideoAtQuality,
                onToggleVideoSubscription = onToggleVideoSubscription,
                onRetry = onRetryVideo,
                onPlaybackFailed = onPlaybackFailed,
                onPrepareFallbackSource = onPrepareFallbackSource,
                onSwitchToPreparedFallbackSource = onSwitchToPreparedFallbackSource,
                onRecoveryPrebufferReady = onRecoveryPrebufferReady,
                onRecoveryPrebufferFailed = onRecoveryPrebufferFailed,
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
                onOpenAnime = { animeId ->
                    profileDialogOpen = false
                    onOpenAnime(animeId)
                },
                onUnsubscribeVideoSubscription = onUnsubscribeVideoSubscription,
                onRefreshVideoSubscriptions = onRefreshVideoSubscriptions,
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
        val pendingUpdate = state.updateState
            .readyDataOrNull()
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
    catalogGridState: LazyGridState,
    scheduleListState: LazyListState,
    historyGridState: LazyGridState,
    pendingCatalogFocusAnimeId: Long,
    homeBackFocusResetNonce: Long,
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
    onRequestHomeFocusReset: () -> Unit,
) {
    val isAuthorized = state.auth.profile != null
    val browsePagerSections = remember(isAuthorized) { visibleBrowseSections(isAuthorized) }
    val effectiveHomeSection = if (state.homeSection == BrowseSection.History && !isAuthorized) {
        BrowseSection.Catalog
    } else {
        state.homeSection
    }
    LaunchedEffect(state.homeSection, isAuthorized) {
        if (state.homeSection == BrowseSection.History && !isAuthorized) {
            onBrowseSectionChange(BrowseSection.Catalog)
        }
    }
    val isCatalog = effectiveHomeSection == BrowseSection.Catalog
    val isSearching = isCatalog && state.searchQuery.isNotBlank()
    val contentState = if (isSearching) state.searchResults else state.featured
    val pagingState = if (isSearching) state.searchPaging else state.featuredPaging
    val configuration = LocalConfiguration.current
    val isWide = configuration.screenWidthDp >= 720
    val isTelevision = remember(configuration.uiMode) {
        val uiMode = configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK
        uiMode == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }
    var searchDialogOpen by remember { mutableStateOf(false) }
    var filtersDialogOpen by remember { mutableStateOf(false) }
    val activeDownloadCount = state.downloadQueue.tasks.count { task ->
        task.state == DownloadTaskState.Queued ||
            task.state == DownloadTaskState.Running ||
            task.state == DownloadTaskState.Paused
    }
    val tvInitialCatalogFocusNonce = if (isTelevision && effectiveHomeSection == BrowseSection.Catalog) 1L else 0L
    val tvInitialScheduleFocusNonce = if (isTelevision && effectiveHomeSection == BrowseSection.Schedule) 1L else 0L
    val tvInitialHistoryFocusNonce = if (isTelevision && effectiveHomeSection == BrowseSection.History) 1L else 0L
    val browseSwipeThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    val latestOnBrowseSectionChange by rememberUpdatedState(onBrowseSectionChange)

    fun selectAdjacentBrowseSection(delta: Int) {
        val currentIndex = browsePagerSections.indexOf(effectiveHomeSection)
        if (currentIndex < 0) return
        val nextSection = browsePagerSections.getOrNull(currentIndex + delta) ?: return
        latestOnBrowseSectionChange(nextSection)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        BrowseTopBarModern(
            onOpenSearch = { searchDialogOpen = true },
            onOpenFilters = { filtersDialogOpen = true },
            onOpenSettings = onOpenSettings,
            onOpenDownloads = onOpenDownloads,
            auth = state.auth,
            activeFilters = state.filters.activeCount,
            activeSearch = isSearching,
            activeDownloadCount = activeDownloadCount,
            forcedOfflineMode = state.forcedOfflineMode,
            onOpenLogin = onOpenLogin,
            onOpenProfile = onOpenProfile,
            isWide = isWide,
            activeSection = effectiveHomeSection,
            visibleSections = browsePagerSections,
            onSectionSelected = onBrowseSectionChange,
            showCompactControls = false,
        )

        Box(modifier = Modifier.weight(1f)) {
            if (effectiveHomeSection == BrowseSection.Downloads) {
                DownloadsSection(
                    state = state,
                    onClearHistory = onClearDownloadHistory,
                    onCancelDownload = onCancelDownload,
                    onPauseDownload = onPauseDownload,
                    onResumeDownload = onResumeDownload,
                    onOpenAnime = onOpenAnime,
                )
            } else {
                AnimatedContent(
                        targetState = effectiveHomeSection,
                        transitionSpec = {
                            val initialIndex = browsePagerSections.indexOf(initialState).takeIf { it >= 0 } ?: 0
                            val targetIndex = browsePagerSections.indexOf(targetState).takeIf { it >= 0 } ?: initialIndex
                            if (targetIndex >= initialIndex) {
                                slideInHorizontally { width -> width } togetherWith
                                    slideOutHorizontally { width -> -width }
                            } else {
                                slideInHorizontally { width -> -width } togetherWith
                                    slideOutHorizontally { width -> width }
                            }
                        },
                        label = "browse-section",
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(browseSwipeThresholdPx, effectiveHomeSection, browsePagerSections) {
                                var totalDrag = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { totalDrag = 0f },
                                    onHorizontalDrag = { change, dragAmount ->
                                        totalDrag += dragAmount
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        if (abs(totalDrag) >= browseSwipeThresholdPx) {
                                            selectAdjacentBrowseSection(if (totalDrag < 0f) 1 else -1)
                                        }
                                        totalDrag = 0f
                                    },
                                    onDragCancel = { totalDrag = 0f },
                                )
                            },
                ) { section ->
                        when (section) {
                            BrowseSection.Catalog -> AnimeGridSection(
                                contentState = contentState,
                                pagingState = pagingState,
                                gridState = catalogGridState,
                                cardSize = state.settings.posterCardSize,
                                pendingFocusAnimeId = pendingCatalogFocusAnimeId,
                                focusFirstRequestNonce = state.homeFocusResetNonce +
                                    homeBackFocusResetNonce +
                                    tvInitialCatalogFocusNonce,
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
                                catalog = state.filterCatalog.readyDataOrNull() ?: FilterCatalog.Empty,
                                listState = scheduleListState,
                                focusFirstRequestNonce = homeBackFocusResetNonce + tvInitialScheduleFocusNonce,
                                onRetry = onRefresh,
                                onOpenAnime = onOpenAnime,
                            )
                            BrowseSection.History -> AnimeGridSection(
                                contentState = state.historyAnime,
                                pagingState = PagingUiState(canLoadMore = false),
                                gridState = historyGridState,
                                cardSize = state.settings.posterCardSize,
                                pendingFocusAnimeId = 0L,
                                focusFirstRequestNonce = homeBackFocusResetNonce + tvInitialHistoryFocusNonce,
                                emptyMessage = uiText("История пуста"),
                                onRetry = onRefresh,
                                onLoadMore = {},
                                onFocusRestored = {},
                                onAnimeFocused = {},
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
        }

        if (!isWide) {
            BrowseBottomBarModern(
                onOpenSearch = { searchDialogOpen = true },
                onOpenFilters = { filtersDialogOpen = true },
                onOpenSettings = onOpenSettings,
                onOpenDownloads = onOpenDownloads,
                auth = state.auth,
                activeFilters = state.filters.activeCount,
                activeSearch = isSearching,
                activeDownloadCount = activeDownloadCount,
                onOpenLogin = onOpenLogin,
                onOpenProfile = onOpenProfile,
                activeSection = effectiveHomeSection,
                visibleSections = browsePagerSections,
                onSectionSelected = onBrowseSectionChange,
            )
        }
    }

    if (searchDialogOpen) {
        SearchDialog(
            query = state.searchQuery,
            onQueryChange = onQueryChange,
            onDismiss = { searchDialogOpen = false },
            onExitDown = {
                searchDialogOpen = false
                onRequestHomeFocusReset()
            },
        )
    }

    if (filtersDialogOpen) {
        FiltersDialogAccordion(
            filters = state.filters,
            auth = state.auth,
            catalogState = state.filterCatalog,
            offlineEntries = state.offlineEntries.readyListOrEmpty(),
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
    gridState: LazyGridState,
    cardSize: PosterCardSize,
    pendingFocusAnimeId: Long,
    focusFirstRequestNonce: Long,
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
        val gridFocusRequester = remember { FocusRequester() }
        val focusScope = rememberCoroutineScope()
        var focusedAnimeIndex by rememberSaveable(columnsCount) { mutableIntStateOf(-1) }
        var handledFocusResetNonce by rememberSaveable { mutableLongStateOf(0L) }
        var gridNavigationJob by remember(columnsCount) { mutableStateOf<Job?>(null) }

        fun rowStartIndex(index: Int): Int {
            return if (columnsCount > 0) (index / columnsCount) * columnsCount else index
        }

        fun requestGridFocus(index: Int, alignRowToTop: Boolean) {
            if (index !in animes.indices) return
            gridNavigationJob?.cancel()
            focusedAnimeIndex = index
            onAnimeFocused(animes[index].id)
            runCatching { gridFocusRequester.requestFocus() }
            if (alignRowToTop) {
                val rowStart = rowStartIndex(index)
                gridNavigationJob = focusScope.launch {
                    gridState.animateScrollToItem(rowStart, 0)
                }
            } else {
                gridNavigationJob = null
            }
        }

        fun handleGridKey(key: Key): Boolean {
            if (columnsCount <= 0) return false
            val currentIndex = focusedAnimeIndex.takeIf { it in animes.indices } ?: 0
            val currentColumn = currentIndex % columnsCount
            val currentRow = currentIndex / columnsCount

            fun visiblePageRows(): Int {
                return gridState.layoutInfo.visibleItemsInfo
                    .asSequence()
                    .map { it.index }
                    .filter { it in animes.indices }
                    .map { it / columnsCount }
                    .distinct()
                    .count()
                    .minus(1)
                    .coerceAtLeast(1)
            }

            fun indexInRow(row: Int): Int {
                val maxRow = animes.lastIndex / columnsCount
                val rowStart = row.coerceIn(0, maxRow) * columnsCount
                return (rowStart + currentColumn).coerceAtMost(animes.lastIndex)
            }

            val targetIndex = when (key) {
                Key.DirectionLeft -> if (currentColumn > 0) currentIndex - 1 else currentIndex
                Key.DirectionRight -> if (currentColumn < columnsCount - 1 && currentIndex < animes.lastIndex) {
                    currentIndex + 1
                } else {
                    currentIndex
                }
                Key.DirectionUp -> {
                    val target = currentIndex - columnsCount
                    if (target >= 0) target else return false
                }
                Key.DirectionDown -> {
                    val target = currentIndex + columnsCount
                    if (target <= animes.lastIndex) {
                        target
                    } else {
                        if (pagingState.canLoadMore && !pagingState.isLoadingMore) onLoadMore()
                        return true
                    }
                }
                Key.PageDown -> {
                    val target = indexInRow(currentRow + visiblePageRows())
                    if (
                        target >= animes.lastIndex - columnsCount * 2 &&
                        pagingState.canLoadMore &&
                        !pagingState.isLoadingMore
                    ) {
                        onLoadMore()
                    }
                    target
                }
                Key.PageUp -> indexInRow(currentRow - visiblePageRows())
                Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.Spacebar -> {
                    onOpenAnime(animes[currentIndex].id)
                    return true
                }
                else -> return false
            }
            requestGridFocus(
                index = targetIndex,
                alignRowToTop = key == Key.DirectionUp ||
                    key == Key.DirectionDown ||
                    key == Key.PageUp ||
                    key == Key.PageDown,
            )
            return true
        }

        LaunchedEffect(pendingFocusAnimeId, focusFirstRequestNonce, animes, columnsCount) {
            if (animes.isEmpty()) return@LaunchedEffect
            val pendingFocusIndex = if (pendingFocusAnimeId > 0L) {
                animes.indexOfFirst { it.id == pendingFocusAnimeId }
            } else {
                -1
            }
            val shouldFocusPending = pendingFocusIndex >= 0
            val shouldFocusFirst = !shouldFocusPending &&
                focusFirstRequestNonce > 0L &&
                focusFirstRequestNonce != handledFocusResetNonce
            val targetIndex = when {
                shouldFocusPending -> pendingFocusIndex
                shouldFocusFirst -> 0
                else -> -1
            }
            if (targetIndex < 0) return@LaunchedEffect
            val targetRowStart = rowStartIndex(targetIndex)
            val targetIsVisible = gridState.layoutInfo.visibleItemsInfo.any { item ->
                item.index == targetIndex || item.index == targetRowStart
            }
            if (shouldFocusFirst || !targetIsVisible) {
                gridState.scrollToItem(targetRowStart, 0)
            }
            withFrameNanos { }
            focusedAnimeIndex = targetIndex
            onAnimeFocused(animes[targetIndex].id)
            runCatching { gridFocusRequester.requestFocus() }
            withFrameNanos { }
            if (shouldFocusFirst || !targetIsVisible) {
                gridState.scrollToItem(targetRowStart, 0)
            }
            if (shouldFocusFirst) {
                handledFocusResetNonce = focusFirstRequestNonce
            } else if (shouldFocusPending) {
                onFocusRestored()
            }
        }

        LaunchedEffect(animes.size) {
            if (animes.isEmpty()) {
                focusedAnimeIndex = -1
            } else if (focusedAnimeIndex > animes.lastIndex) {
                focusedAnimeIndex = animes.lastIndex
                onAnimeFocused(animes[focusedAnimeIndex].id)
            }
        }

        LaunchedEffect(
            focusedAnimeIndex,
            animes.size,
            columnsCount,
            pagingState.canLoadMore,
            pagingState.isLoadingMore,
            pagingState.error,
        ) {
            if (
                focusedAnimeIndex < 0 ||
                columnsCount <= 0 ||
                !pagingState.canLoadMore ||
                pagingState.isLoadingMore ||
                pagingState.error != null
            ) {
                return@LaunchedEffect
            }
            val focusedRow = focusedAnimeIndex / columnsCount
            val lastLoadedRow = animes.lastIndex.coerceAtLeast(0) / columnsCount
            if (lastLoadedRow - focusedRow < 2) {
                onLoadMore()
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columnsCount),
            state = gridState,
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(gridFocusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused && focusedAnimeIndex !in animes.indices && animes.isNotEmpty()) {
                        focusedAnimeIndex = 0
                        onAnimeFocused(animes.first().id)
                    }
                }
                .onPreviewKeyEvent { event ->
                    event.type == KeyEventType.KeyDown && handleGridKey(event.key)
                }
                .focusable(),
        ) {
            itemsIndexed(animes, key = { index, anime -> "anime-grid:$index:${anime.id}:${anime.title}" }) { index, anime ->
                AnimeCard(
                    anime = anime,
                    onClick = { onOpenAnime(anime.id) },
                    focused = index == focusedAnimeIndex,
                    modifier = Modifier
                        .focusProperties { canFocus = false },
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
    listState: LazyListState,
    focusFirstRequestNonce: Long,
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
            val firstItemFocusRequester = remember(visibleItems) { FocusRequester() }
            var handledFocusResetNonce by rememberSaveable { mutableLongStateOf(0L) }

            LaunchedEffect(focusFirstRequestNonce, visibleItems) {
                if (
                    visibleItems.isNotEmpty() &&
                    focusFirstRequestNonce > 0L &&
                    focusFirstRequestNonce != handledFocusResetNonce
                ) {
                    listState.scrollToItem(0)
                    delay(80)
                    runCatching { firstItemFocusRequester.requestFocus() }
                    handledFocusResetNonce = focusFirstRequestNonce
                }
            }

            if (state.data.isEmpty()) {
                EmptyPane(message = uiText("Расписание пока пустое"), modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    state = listState,
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
                    ) { index, item ->
                        ScheduleRow(
                            item = item,
                            onOpenAnime = onOpenAnime,
                            modifier = if (index == 0) {
                                Modifier.focusRequester(firstItemFocusRequester)
                            } else {
                                Modifier
                            },
                        )
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
    val role = if (hidePastItems) YummySurfaceRole.ActiveRow else YummySurfaceRole.Row
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
            color = yummySurfaceColor(role),
            contentColor = yummySurfaceContentColor(role),
            border = yummySurfaceBorder(role),
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
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .dpadClickable(shape) { onOpenAnime(item.anime.id) },
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
        shape = shape,
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
    val offlineEntries = state.offlineEntries.readyListOrEmpty()
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
    val shape = YummyRadii.smallShape
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .dpadClickable(shape, onOpenAnime),
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
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
        totalBytes > 0L && downloadedBytes > 0L -> "${formatByteSize(downloadedBytes)} / ${formatByteSize(totalBytes)}"
        downloadedBytes > 0L && isActive -> "${formatByteSize(downloadedBytes)} / ${uiText("неизвестно")}"
        downloadedBytes > 0L -> formatByteSize(downloadedBytes)
        else -> ""
    }
    val speed = if (isActive && bytesPerSecond > 0L) "${formatByteSize(bytesPerSecond)}/${uiText("с")}" else ""
    return listOf(percent, size, speed)
        .filter { it.isNotBlank() }
        .joinToString(" • ")
}

@Composable
private fun OfflineAnimeRow(
    entry: OfflineAnimeEntry,
    onOpenAnime: (Long) -> Unit,
) {
    val shape = YummyRadii.smallShape
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .dpadClickable(shape) { onOpenAnime(entry.anime.id) },
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
        border = yummySurfaceBorder(YummySurfaceRole.Row),
        shape = shape,
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
                    text = "${entry.downloadedVideos.size} ${localizedEpisodesWord(entry.downloadedVideos.size)} • ${formatByteSize(entry.totalBytes)}",
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
    fun List<FilterOption>.toDistinctFilterOptions(): List<FilterOption> {
        return asSequence()
            .filter { it.title.isNotBlank() && it.value.isNotBlank() }
            .distinctBy { it.value }
            .toList()
            .sortedByTitle()
    }

    return FilterCatalog(
        genres = flatMap { entry ->
            entry.details.genreTags.map { it.title }.ifEmpty { entry.anime.genres }
        }.toFilterOptions(),
        types = map { entry -> entry.details.type.ifBlank { entry.anime.type } }.toFilterOptions(),
        studios = flatMap { entry -> entry.details.studios }.toDistinctFilterOptions(),
        creators = flatMap { entry -> entry.details.creators }.toDistinctFilterOptions(),
    )
}

private fun mergedFilterOptions(
    catalogOptions: List<FilterOption>,
    selectedValues: Set<String>,
    selectedTitles: Map<String, String>,
): List<FilterOption> {
    return (catalogOptions + selectedValues.map { value ->
        FilterOption(title = selectedTitles[value] ?: value, value = value)
    })
        .filter { it.title.isNotBlank() && it.value.isNotBlank() }
        .distinctBy { it.value }
        .sortedByTitle()
}

private fun BrowseFilters.toggleStudioFilter(value: String, title: String?): BrowseFilters {
    return if (value in studios) {
        copy(studios = studios - value, studioTitles = studioTitles - value)
    } else {
        copy(
            studios = studios + value,
            studioTitles = studioTitles + (value to (title?.takeIf { it.isNotBlank() } ?: value)),
        )
    }
}

private fun BrowseFilters.toggleCreatorFilter(value: String, title: String?): BrowseFilters {
    return if (value in creators) {
        copy(creators = creators - value, creatorTitles = creatorTitles - value)
    } else {
        copy(
            creators = creators + value,
            creatorTitles = creatorTitles + (value to (title?.takeIf { it.isNotBlank() } ?: value)),
        )
    }
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
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    auth: AuthUiState,
    activeFilters: Int,
    activeSearch: Boolean,
    activeDownloadCount: Int,
    forcedOfflineMode: Boolean,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    isWide: Boolean,
    activeSection: BrowseSection,
    visibleSections: List<BrowseSection>,
    onSectionSelected: (BrowseSection) -> Unit,
    showCompactControls: Boolean = true,
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
                AppWordmark(
                    modifier = Modifier.weight(1f),
                    height = 52.dp,
                )

                if (forcedOfflineMode) {
                    OfflineModeChip()
                }

                BrowseTopBarActions(
                    onOpenSearch = onOpenSearch,
                    onOpenFilters = onOpenFilters,
                    onOpenSettings = onOpenSettings,
                    onOpenDownloads = onOpenDownloads,
                    auth = auth,
                    activeFilters = activeFilters,
                    activeSearch = activeSearch,
                    activeDownloadCount = activeDownloadCount,
                    onOpenLogin = onOpenLogin,
                    onOpenProfile = onOpenProfile,
                )
            }

            BrowseSectionTabs(
                activeSection = activeSection,
                visibleSections = visibleSections,
                onSectionSelected = onSectionSelected,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
            )

            if (forcedOfflineMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    OfflineModeChip()
                }
            }

            if (showCompactControls) {
                BrowseSectionTabs(
                    activeSection = activeSection,
                    visibleSections = visibleSections,
                    onSectionSelected = onSectionSelected,
                    modifier = Modifier.fillMaxWidth(),
                )

                BrowseTopBarActions(
                    onOpenSearch = onOpenSearch,
                    onOpenFilters = onOpenFilters,
                    onOpenSettings = onOpenSettings,
                    onOpenDownloads = onOpenDownloads,
                    auth = auth,
                    activeFilters = activeFilters,
                    activeSearch = activeSearch,
                    activeDownloadCount = activeDownloadCount,
                    onOpenLogin = onOpenLogin,
                    onOpenProfile = onOpenProfile,
                    modifier = Modifier.fillMaxWidth(),
                    spreadActions = !stackActions,
                    stackActions = stackActions,
                )
            }
        }
    }
}

@Composable
private fun AppWordmark(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = modifier.height(height),
        contentAlignment = Alignment.CenterStart,
    ) {
        Image(
            painter = painterResource(R.drawable.app_wordmark),
            contentDescription = "YummyDroid",
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxHeight()
                .width(height * 5.45f),
        )
    }
}

@Composable
private fun BrowseBottomBarModern(
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    auth: AuthUiState,
    activeFilters: Int,
    activeSearch: Boolean,
    activeDownloadCount: Int,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    activeSection: BrowseSection,
    visibleSections: List<BrowseSection>,
    onSectionSelected: (BrowseSection) -> Unit,
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val stackActions = screenWidthDp < 360
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BrowseSectionTabs(
            activeSection = activeSection,
            visibleSections = visibleSections,
            onSectionSelected = onSectionSelected,
            modifier = Modifier.fillMaxWidth(),
        )
        BrowseTopBarActions(
            onOpenSearch = onOpenSearch,
            onOpenFilters = onOpenFilters,
            onOpenSettings = onOpenSettings,
            onOpenDownloads = onOpenDownloads,
            auth = auth,
            activeFilters = activeFilters,
            activeSearch = activeSearch,
            activeDownloadCount = activeDownloadCount,
            onOpenLogin = onOpenLogin,
            onOpenProfile = onOpenProfile,
            modifier = Modifier.fillMaxWidth(),
            spreadActions = !stackActions,
            stackActions = stackActions,
        )
    }
}

@Composable
private fun BrowseSectionTabs(
    activeSection: BrowseSection,
    visibleSections: List<BrowseSection>,
    onSectionSelected: (BrowseSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visibleSections.forEach { section ->
            val selected = section == activeSection
            val shape = YummyRadii.smallShape
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(YummySizes.tabHeight)
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = YummySpacing.sm),
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
        shape = YummyRadii.pillShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = YummySpacing.md, vertical = YummySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.xs),
        ) {
            Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(YummySizes.badgeIcon))
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
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    auth: AuthUiState,
    activeFilters: Int,
    activeSearch: Boolean,
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
                SearchActionButton(activeSearch, onOpenSearch)
                FiltersActionButton(activeFilters, onOpenFilters)
                DownloadsActionButton(activeDownloadCount, onOpenDownloads)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SettingsActionButton(onOpenSettings)
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
        SearchActionButton(activeSearch, onOpenSearch)
        FiltersActionButton(activeFilters, onOpenFilters)
        DownloadsActionButton(activeDownloadCount, onOpenDownloads)
        SettingsActionButton(onOpenSettings)
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
private fun SearchActionButton(
    activeSearch: Boolean,
    onOpenSearch: () -> Unit,
) {
    IconButton(
        onClick = onOpenSearch,
        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(Icons.Default.Search, contentDescription = uiText("Поиск"))
            if (activeSearch) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .padding(start = 17.dp, top = 1.dp)
                        .size(9.dp),
                ) {}
            }
        }
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
private fun SearchDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onExitDown: () -> Unit = onDismiss,
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

    Popup(
        alignment = Alignment.TopCenter,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = if (isTelevision) 40.dp else 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = YummyRadii.mediumShape,
                border = yummySurfaceBorder(YummySurfaceRole.Row),
                shadowElevation = 10.dp,
            ) {
                Row(
                    modifier = Modifier.padding(YummySpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = launchVoiceSearch,
                        modifier = Modifier
                            .size(56.dp)
                            .focusRequester(micFocusRequester)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                    keyboardController?.hide()
                                    onExitDown()
                                    true
                                } else {
                                    false
                                }
                            }
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
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionLeft -> {
                                        micFocusRequester.requestFocus()
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        keyboardController?.hide()
                                        onExitDown()
                                        true
                                    }
                                    else -> false
                                }
                            },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogActionRow(
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(YummySpacing.sm),
        content = content,
    )
}

@Composable
private fun DialogActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false,
) {
    val shape = YummyRadii.smallShape
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) {
                MaterialTheme.colorScheme.primary
            } else {
                yummySurfaceColor(YummySurfaceRole.Row)
            },
            contentColor = if (primary) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = YummyAlpha.disabledSurface),
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = if (primary) {
            null
        } else {
            yummySurfaceBorder(YummySurfaceRole.Row)
        },
        contentPadding = if (compact) {
            PaddingValues(horizontal = 6.dp, vertical = YummySpacing.xs)
        } else {
            PaddingValues(horizontal = YummySpacing.md, vertical = YummySpacing.sm)
        },
        modifier = modifier
            .then(
                if (compact) {
                    Modifier
                } else {
                    Modifier.widthIn(
                        min = if (primary) {
                            YummySizes.primaryDialogButtonMinWidth
                        } else {
                            YummySizes.dialogButtonMinWidth
                        },
                    )
                },
            )
            .defaultMinSize(minWidth = 0.dp, minHeight = YummySizes.dialogButtonHeight)
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
            overflow = if (compact) TextOverflow.Ellipsis else TextOverflow.Clip,
            textAlign = if (compact) TextAlign.Center else TextAlign.Unspecified,
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
        val baseFilters = if (isAuthorized) {
            filters
        } else {
            filters.copy(userMarks = emptySet(), excludedUserMarks = emptySet())
        }
        mutableStateOf(
            if (forcedOfflineMode) {
                baseFilters.copy(offlineOnly = true, userMarks = emptySet(), excludedUserMarks = emptySet())
            } else {
                baseFilters
            },
        )
    }
    var expandedSection by remember { mutableStateOf("") }
    var advancedVisible by remember(filters) { mutableStateOf(false) }
    val catalog = remember(catalogState, offlineEntries, forcedOfflineMode) {
        if (forcedOfflineMode) {
            offlineEntries.toOfflineFilterCatalog()
        } else {
            catalogState.readyDataOrNull() ?: FilterCatalog.Empty
        }
    }
    val studioOptions = remember(catalog.studios, draft.studios, draft.studioTitles) {
        mergedFilterOptions(catalog.studios, draft.studios, draft.studioTitles)
    }
    val creatorOptions = remember(catalog.creators, draft.creators, draft.creatorTitles) {
        mergedFilterOptions(catalog.creators, draft.creators, draft.creatorTitles)
    }
    val studioOptionTitles = remember(studioOptions) {
        studioOptions.associate { it.value to it.title }
    }
    val creatorOptionTitles = remember(creatorOptions) {
        creatorOptions.associate { it.value to it.title }
    }
    val hiddenActiveCount = remember(draft, isAuthorized) { draft.advancedFilterCount(isAuthorized) }
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
                    .verticalScroll(state = containerScrollState),
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
                    searchable = true,
                )

                if (!advancedVisible) {
                    AdvancedFiltersButton(
                        activeCount = hiddenActiveCount,
                        onClick = { advancedVisible = true },
                    )
                }

                if (advancedVisible) {
                FilterAccordionSection(
                    id = "excluded_genres",
                    title = uiText("Исключить жанры"),
                    options = catalog.genres,
                    selected = draft.excludedGenres,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(excludedGenres = draft.excludedGenres.toggle(value)) },
                    onSideExit = moveFocusToActions,
                    searchable = true,
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

                FilterAccordionSection(
                    id = "studios",
                    title = uiText("\u0421\u0442\u0443\u0434\u0438\u044f"),
                    options = studioOptions,
                    selected = draft.studios,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value ->
                        draft = draft.toggleStudioFilter(value, studioOptionTitles[value])
                    },
                    onSideExit = moveFocusToActions,
                    searchable = true,
                )

                FilterAccordionSection(
                    id = "creators",
                    title = uiText("\u0420\u0435\u0436\u0438\u0441\u0441\u0451\u0440"),
                    options = creatorOptions,
                    selected = draft.creators,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value ->
                        draft = draft.toggleCreatorFilter(value, creatorOptionTitles[value])
                    },
                    onSideExit = moveFocusToActions,
                    searchable = true,
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
                    title = uiText("Возраст"),
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
                    FilterAccordionSection(
                        id = "excluded_user_marks",
                        title = uiText("Исключить метки"),
                        options = userMarkFilterOptions,
                        selected = draft.excludedUserMarks,
                        expandedSection = expandedSection,
                        onExpandedChange = { expandedSection = it },
                        onToggle = { value -> draft = draft.copy(excludedUserMarks = draft.excludedUserMarks.toggle(value)) },
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
                }

                if (!forcedOfflineMode && catalogState is LoadState.Error) {
                    InlineErrorMessage(
                        message = catalogState.message,
                        modifier = Modifier.padding(top = YummySpacing.xs),
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DialogActionButton(
                    text = uiText("Сбросить"),
                    modifier = Modifier.weight(1f),
                    compact = true,
                    onClick = {
                        draft = if (forcedOfflineMode) BrowseFilters(offlineOnly = true) else BrowseFilters()
                        onReset()
                        onDismiss()
                    },
                )
                DialogActionButton(
                    text = uiText("Отмена"),
                    modifier = Modifier.weight(1f),
                    compact = true,
                    onClick = onDismiss,
                )
                DialogActionButton(
                    text = uiText("Применить"),
                    primary = true,
                    compact = true,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(applyFocusRequester),
                    onClick = {
                        onApply(
                            when {
                                forcedOfflineMode -> draft.copy(
                                    offlineOnly = true,
                                    userMarks = emptySet(),
                                    excludedUserMarks = emptySet(),
                                )
                                isAuthorized -> draft
                                else -> draft.copy(userMarks = emptySet(), excludedUserMarks = emptySet())
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
        color = yummySurfaceColor(YummySurfaceRole.Row),
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
private fun AdvancedFiltersButton(
    activeCount: Int,
    onClick: () -> Unit,
) {
    val title = if (activeCount > 0) {
        "${uiText("Расширенный режим")} • $activeCount"
    } else {
        uiText("Расширенный режим")
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .dpadClickable(RoundedCornerShape(8.dp), onClick),
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AnimeSort.entries.forEach { sort ->
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
    searchable: Boolean = false,
) {
    if (options.isEmpty()) return

    val sortedOptions = remember(options) { options.sortedByTitle() }
    val expanded = expandedSection == id
    var query by remember(id, expanded) { mutableStateOf("") }
    val visibleOptions = remember(sortedOptions, query, searchable) {
        if (!searchable || query.isBlank()) {
            sortedOptions
        } else {
            sortedOptions.filter { option ->
                option.title.contains(query.trim(), ignoreCase = true) ||
                    option.value.contains(query.trim(), ignoreCase = true)
            }
        }
    }
    AccordionHeader(
        title = title,
        summary = selectedFilterSummary(sortedOptions, selected),
        expanded = expanded,
        active = selected.isNotEmpty(),
        onClick = { onExpandedChange(if (expanded) "" else id) },
    )

    if (expanded) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (searchable) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(uiText("\u041f\u043e\u0438\u0441\u043a")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
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
            visibleOptions.forEach { option ->
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
    summary: String = "",
    expanded: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
) {
    val backgroundColor = if (active) {
        yummySurfaceColor(YummySurfaceRole.ActiveRow)
    } else {
        yummySurfaceColor(YummySurfaceRole.Row)
    }
    val contentColor = if (active) {
        yummySurfaceContentColor(YummySurfaceRole.ActiveRow)
    } else {
        yummySurfaceContentColor(YummySurfaceRole.Row)
    }
    val summaryColor = if (active) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier
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
                if (summary.isNotBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = summaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!trailingText.isNullOrBlank()) {
                Text(
                    text = trailingText,
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

private fun Modifier.stopHorizontalFocusEscape(
    index: Int,
    total: Int,
    leftExit: FocusRequester? = null,
    rightExit: FocusRequester? = null,
): Modifier {
    if (total <= 1 || index < 0) return this
    val isFirst = index == 0
    val isLast = index >= total - 1
    return focusProperties {
        if (isFirst) left = leftExit ?: FocusRequester.Cancel
        if (isLast) right = rightExit ?: FocusRequester.Cancel
    }.onPreviewKeyEvent { event ->
        event.type == KeyEventType.KeyDown &&
            (
                (event.key == Key.DirectionLeft && isFirst && leftExit == null) ||
                    (event.key == Key.DirectionRight && isLast && rightExit == null)
                )
    }
}

private fun Modifier.stopGridLineFocusEscape(
    index: Int,
    total: Int,
    columns: Int,
    upTarget: FocusRequester?,
    downTarget: FocusRequester?,
    onMoveToIndex: (Int, Boolean) -> Unit,
): Modifier {
    if (total <= 1 || index < 0 || columns <= 0) return this
    val isFirstInLine = index % columns == 0
    val isLastInLine = index % columns == columns - 1 || index >= total - 1
    val upIndex = index - columns
    val downIndex = index + columns
    val hasUpTarget = upIndex >= 0
    val hasDownTarget = downIndex < total
    return focusProperties {
        left = FocusRequester.Cancel
        right = FocusRequester.Cancel
        if (upTarget != null) up = upTarget
        if (downTarget != null) down = downTarget else down = FocusRequester.Cancel
    }.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) {
            false
        } else {
            when (event.key) {
                Key.DirectionLeft -> {
                    if (!isFirstInLine) {
                        onMoveToIndex(index - 1, false)
                    }
                    true
                }
                Key.DirectionRight -> {
                    if (!isLastInLine) {
                        onMoveToIndex(index + 1, false)
                    }
                    true
                }
                Key.DirectionUp -> {
                    if (hasUpTarget) {
                        onMoveToIndex(upIndex, true)
                        true
                    } else {
                        false
                    }
                }
                Key.DirectionDown -> {
                    if (hasDownTarget) {
                        onMoveToIndex(downIndex, true)
                    }
                    true
                }
                else -> false
            }
        }
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

private fun BrowseFilters.advancedFilterCount(isAuthorized: Boolean): Int {
    return excludedGenres.size +
        seasons.size +
        types.size +
        studios.size +
        creators.size +
        translates.size +
        ageRatings.size +
        listOfNotNull(fromYear, toYear, minRating, maxRating, episodeFrom, episodeTo).size +
        (if (isAuthorized) userMarks.size + excludedUserMarks.size else 0) +
        if (offlineOnly) 1 else 0
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
private fun CaptchaChallengeEffect(
    requestNonce: Long,
    onSolved: (String) -> Unit,
    onCanceled: (String?) -> Unit,
) {
    val context = LocalContext.current
    var handledNonce by remember { mutableLongStateOf(0L) }
    val captchaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val token = result.data
            ?.getStringExtra(HCaptchaActivity.EXTRA_CAPTCHA_TOKEN)
            .orEmpty()
        if (result.resultCode == Activity.RESULT_OK && token.isNotBlank()) {
            onSolved(token)
        } else {
            onCanceled(result.data?.getStringExtra(HCaptchaActivity.EXTRA_CAPTCHA_ERROR))
        }
    }

    LaunchedEffect(requestNonce) {
        if (requestNonce > 0L && requestNonce != handledNonce) {
            handledNonce = requestNonce
            captchaLauncher.launch(Intent(context, HCaptchaActivity::class.java))
        }
    }
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

    LaunchedEffect(auth.profile) {
        if (auth.profile != null) {
            onDismiss()
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
                    InlineErrorMessage(message = message)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, sitePageUrl(siteBaseUrl, "register").toUri()),
                            )
                        },
                        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
                    ) {
                        Text(uiText("Регистрация"), maxLines = 1, softWrap = false)
                    }
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, sitePageUrl(siteBaseUrl, "login/reset-password").toUri()),
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
    onOpenAnime: (Long) -> Unit,
    onUnsubscribeVideoSubscription: (VideoSubscription) -> Unit,
    onRefreshVideoSubscriptions: () -> Unit,
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
                        InlineErrorMessage(message = message)
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
                        InlineErrorMessage(message = uiText("Аккаунт заблокирован на сайте."))
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
                ProfileDialogActions(
                    onOpenLibrary = onOpenLibrary,
                    onOpenSubscriptions = {
                        onRefreshVideoSubscriptions()
                        subscriptionsDialogOpen = true
                    },
                    onOpenSite = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    profile.siteProfileUrl(siteBaseUrl).toUri(),
                                ),
                            )
                        }.onFailure {
                            Toast.makeText(context, openSiteError, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onLogout = onLogout,
                    onDismiss = onDismiss,
                )
            }
        },
    )

    if (subscriptionsDialogOpen && profile != null) {
        ProfileSubscriptionsDialog(
            subscriptionsState = subscriptionsState,
            onOpenAnime = { animeId ->
                subscriptionsDialogOpen = false
                onDismiss()
                onOpenAnime(animeId)
            },
            onUnsubscribe = onUnsubscribeVideoSubscription,
            onDismiss = { subscriptionsDialogOpen = false },
        )
    }
}

@Composable
private fun ProfileDialogActions(
    onOpenLibrary: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenSite: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(YummySpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
        ) {
            DialogActionButton(
                text = uiText("Библиотека"),
                onClick = onOpenLibrary,
                modifier = Modifier.weight(1f),
            )
            DialogActionButton(
                text = uiText("Подписки"),
                onClick = onOpenSubscriptions,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
        ) {
            DialogActionButton(
                text = uiText("ЛК"),
                onClick = onOpenSite,
                modifier = Modifier.weight(1f),
            )
            DialogActionButton(
                text = uiText("Выйти"),
                primary = true,
                onClick = onLogout,
                modifier = Modifier.weight(1f),
            )
        }
        DialogActionButton(
            text = uiText("Закрыть"),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ProfileSubscriptionsDialog(
    subscriptionsState: LoadState<List<VideoSubscription>>,
    onOpenAnime: (Long) -> Unit,
    onUnsubscribe: (VideoSubscription) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("Подписки")) },
        text = {
            Box(modifier = Modifier.fillMaxWidth()) {
                when (subscriptionsState) {
                    LoadState.Loading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingPane(
                            Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                        )
                    }
                    is LoadState.Error -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        InlineErrorMessage(message = subscriptionsState.message)
                    }
                    is LoadState.Ready -> {
                        val subscriptions = subscriptionsState.data
                            .groupBy { it.profileDisplayKey }
                            .values
                            .map { group -> group.preferredProfileSubscription() }
                            .filter { it.profileVoiceTitle.isNotBlank() }
                            .sortedWith(
                                compareBy<VideoSubscription> { it.title.lowercase(Locale.ROOT) }
                                    .thenBy { it.profileVoiceTitle.lowercase(Locale.ROOT) },
                            )
                        if (subscriptions.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 160.dp, max = 420.dp)
                                    .verticalScroll(rememberScrollState()),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    text = uiText("Подписок нет"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 420.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                lazyItemsIndexed(
                                    subscriptions,
                                    key = { index, subscription ->
                                        "profile-subscription:${subscription.profileDisplayKey}:$index"
                                    },
                                ) { _, subscription ->
                                    SubscriptionManagementRow(
                                        subscription = subscription,
                                        onOpenAnime = { onOpenAnime(subscription.animeId) },
                                        onUnsubscribe = { onUnsubscribe(subscription) },
                                    )
                                }
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
    onOpenAnime: () -> Unit,
    onUnsubscribe: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = Modifier.dpadClickable(shape, onOpenAnime),
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
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
                    text = subscription.profileVoiceTitle,
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
    autoCheckUpdates: Boolean,
    onAutoCheckUpdatesChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(YummySpacing.sm),
    ) {
        SettingsSwitchRow(
            title = uiText("Проверять обновления при запуске"),
            checked = autoCheckUpdates,
            onCheckedChange = onAutoCheckUpdatesChange,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = YummySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.md),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = uiText("Версия"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = version,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            DialogActionButton(
                text = uiText("Проверить"),
                onClick = onCheckForUpdates,
            )
        }
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
    var bufferPickerOpen by remember { mutableStateOf(false) }
    var cardSizePickerOpen by remember { mutableStateOf(false) }
    var languagePickerOpen by remember { mutableStateOf(false) }
    var domainsDialogOpen by remember { mutableStateOf(false) }
    val displayModeMatchingAvailable = remember(context) { context.supportsDisplayModeMatching() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("Настройки")) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingsGroup(title = uiText("Хранилище")) {
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
                }

                SettingsGroup(title = uiText("Загрузки")) {
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
                }

                SettingsGroup(title = uiText("Воспроизведение")) {
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
                    SettingsActionRow(
                        title = uiText("Объём буфера"),
                        value = settings.playerBufferPreset.localizedTitle(),
                        onClick = { bufferPickerOpen = true },
                        isPicker = true,
                    )
                    if (displayModeMatchingAvailable) {
                        SettingsSwitchRow(
                            title = uiText("Автоподстройка экрана под видео"),
                            checked = settings.matchDisplayModeToVideo,
                            onCheckedChange = { onSettingsChange(settings.copy(matchDisplayModeToVideo = it)) },
                        )
                    }
                    SettingsSwitchRow(
                        title = uiText("Пропускать OP/ED"),
                        checked = settings.skipOpeningsAndEndings,
                        onCheckedChange = { onSettingsChange(settings.copy(skipOpeningsAndEndings = it)) },
                    )
                    SettingsSwitchRow(
                        title = uiText("Автовоспроизведение следующей серии"),
                        checked = settings.autoplayNextEpisode,
                        onCheckedChange = { onSettingsChange(settings.copy(autoplayNextEpisode = it)) },
                    )
                }

                SettingsGroup(title = uiText("Каталог и оформление")) {
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
                }

                SettingsGroup(title = uiText("Автоматические метки")) {
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
                }

                SettingsGroup(title = uiText("Сеть")) {
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
                }

                SettingsGroup(title = uiText("О программе")) {
                    SettingsVersionRow(
                        version = "${BuildConfig.VERSION_NAME} ${BuildConfig.BUILD_TYPE}",
                        autoCheckUpdates = settings.autoCheckUpdates,
                        onAutoCheckUpdatesChange = { onSettingsChange(settings.copy(autoCheckUpdates = it)) },
                        onCheckForUpdates = {
                            updateDialogOpen = true
                            onCheckForUpdates()
                        },
                    )
                }
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

    if (bufferPickerOpen) {
        SettingsPickerDialog(
            title = uiText("Объём буфера"),
            options = PlayerBufferPreset.entries,
            selected = settings.playerBufferPreset,
            optionTitle = { it.localizedTitle() },
            onSelected = {
                onSettingsChange(settings.copy(playerBufferPreset = it))
                bufferPickerOpen = false
            },
            onDismiss = { bufferPickerOpen = false },
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
            if (videos == 0) uiText("Пусто") else "$videos ${localizedEpisodesWord(videos)} • ${formatByteSize(bytes)}"
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
                is LoadState.Error -> InlineErrorMessage(message = entriesState.message)
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
    val shape = YummyRadii.smallShape
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
        shape = shape,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val fileRows = remember(entry.videos) {
                entry.downloadedVideos
                    .offlineDeleteFiles()
                    .groupBy { it.cacheRowKey() }
                    .values
                    .map { group -> group.maxBy { it.file.bytes.coerceAtLeast(0L) } }
                    .sortedWith(
                        compareBy<OfflineDeleteFile> { it.variant.offlineEpisodeSortKey() }
                            .thenBy { it.displayVoiceTitle().lowercase(Locale.ROOT) }
                            .thenByDescending { it.file.qualityHeight() },
                    )
            }
            val episodeCount = remember(fileRows, entry.downloadedVideos) {
                fileRows
                    .map { it.variant.offlineEpisodeIdentity() }
                    .distinct()
                    .size
                    .takeIf { it > 0 }
                    ?: entry.downloadedVideos.map { it.offlineEpisodeIdentity() }.distinct().size
            }
            val totalBytes = remember(fileRows, entry.totalBytes) {
                fileRows.sumOf { it.file.bytes.coerceAtLeast(0L) }.takeIf { it > 0L } ?: entry.totalBytes
            }
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
                        text = "$episodeCount ${localizedEpisodesWord(episodeCount)} • ${formatByteSize(totalBytes)}",
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
            if (fileRows.isEmpty()) {
                entry.downloadedVideos
                    .distinctBy { it.offlineEpisodeIdentity() to it.matchingVoiceKey }
                    .forEach { video ->
                        OfflineDownloadFileRow(
                            title = listOf(video.episodeTitle, video.matchingVoiceTitle)
                                .filter { it.isNotBlank() }
                                .joinToString(" • "),
                            size = video.localBytes.takeIf { it > 0L }?.let(::formatByteSize).orEmpty(),
                            onDelete = { onDeleteVideo(entry.anime.id, video.id, null) },
                        )
                    }
            } else {
                fileRows.forEach { item ->
                    OfflineDownloadFileRow(
                        title = listOf(
                            item.variant.episodeTitle,
                            item.displayVoiceTitle(),
                            item.file.qualityDisplayTitle(),
                        ).filter { it.isNotBlank() }.joinToString(" • "),
                        size = item.file.bytes.takeIf { it > 0L }?.let(::formatByteSize).orEmpty(),
                        onDelete = { onDeleteVideo(entry.anime.id, item.variant.id, item.file.playbackUrl) },
                    )
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
                is LoadState.Error -> InlineErrorMessage(message = updateState.message)
                is LoadState.Ready -> {
                    val info = updateState.data
                    if (info == null) {
                        Text(uiText("Проверка еще не выполнена."))
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val title = if (info.title == "Установлена актуальная версия") {
                                uiText("Установлена актуальная версия")
                            } else {
                                info.title.ifBlank { "YummyDroid ${info.version}" }
                            }
                            Text(
                                text = title,
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
            val info = updateState.readyDataOrNull()
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
    return isNewerThanVersion(BuildConfig.VERSION_NAME)
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
private fun SettingsGroup(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = yummySurfaceColor(YummySurfaceRole.Panel),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Panel),
        shape = YummyRadii.smallShape,
    ) {
        Column(
            modifier = Modifier.padding(YummySpacing.md),
            verticalArrangement = Arrangement.spacedBy(YummySpacing.sm),
        ) {
            SettingsSectionTitle(title)
            content()
        }
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    isPicker: Boolean = false,
) {
    val shape = YummyRadii.smallShape
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .focusRing(shape),
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = YummySpacing.md, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.md),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(YummySpacing.xxs),
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
                verticalArrangement = Arrangement.spacedBy(YummySpacing.xs),
            ) {
                items(options, key = { it.toString() }) { option ->
                    val shape = YummyRadii.smallShape
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = YummySizes.tabHeight)
                            .dpadClickable(shape) { onSelected(option) }
                            .padding(horizontal = YummySpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
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
                ?: selectedVideo?.matchingVoiceKey?.let { voiceKey ->
                    voiceOptions.firstOrNull { it.matchingVoiceKey == voiceKey }?.groupKey
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
                            title = option.matchingVoiceTitle,
                            subtitle = option.downloadVoiceSubtitle(videos),
                            downloadedCount = option.downloadedVoiceEpisodeCount(videos),
                            selected = option.groupKey == selectedVoiceKey,
                            onClick = { selectedVoiceKey = option.groupKey },
                        )
                    }
                } else {
                    item("quality-hint") {
                        Text(
                            text = selectedVoice.matchingVoiceTitle,
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
                                        text = uiText("Поиск вариантов качества"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        qualityOptions.orEmpty().isEmpty() -> {
                            item("quality-empty") {
                                InlineErrorMessage(
                                    message = qualityError ?: uiText("Для выбранной озвучки нет доступных качеств"),
                                    modifier = Modifier.padding(vertical = 12.dp),
                                )
                            }
                        }
                        else -> {
                            items(qualityOptions.orEmpty(), key = { "quality:${it.name}" }) { option ->
                                DialogRadioRow(
                                    title = option.localizedTitle(),
                                    downloadedCount = selectedVoice.downloadedQualityEpisodeCount(videos, option),
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
                            color = yummySurfaceColor(YummySurfaceRole.Row),
                            contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
                            shape = YummyRadii.smallShape,
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
    val shape = YummyRadii.smallShape
    Surface(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier
            .fillMaxWidth()
            .focusRing(shape),
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = YummySpacing.md, vertical = YummySpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.md),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = checked,
                onCheckedChange = null,
            )
        }
    }
}

@Composable
private fun SettingsSliderRow(
    title: String,
    value: Int,
    valueRange: IntRange,
    onValueChange: (Int) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val coercedValue = value.coerceIn(valueRange.first, valueRange.last)
    val shape = YummyRadii.smallShape
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .focusRing(shape),
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
        shape = shape,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = YummySpacing.md, vertical = YummySpacing.sm),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(YummySpacing.md),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent false
                        }
                        when (event.key) {
                            Key.DirectionLeft -> {
                                onValueChange((coercedValue - 1).coerceIn(valueRange.first, valueRange.last))
                                true
                            }
                            Key.DirectionRight -> {
                                onValueChange((coercedValue + 1).coerceIn(valueRange.first, valueRange.last))
                                true
                            }
                            Key.DirectionUp -> {
                                focusManager.moveFocus(FocusDirection.Up)
                                true
                            }
                            Key.DirectionDown -> {
                                focusManager.moveFocus(FocusDirection.Down)
                                true
                            }
                            else -> false
                        }
                    },
            )
        }
    }
}

@Composable
private fun AnimeCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focused: Boolean? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var localFocused by remember { mutableStateOf(false) }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused = focused ?: localFocused
    val expanded = isFocused || isPressed

    Box(
        modifier = modifier
            .zIndex(if (expanded) 8f else 0f)
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (focused == null) {
                    localFocused = state.isFocused || state.hasFocus
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        AnimeCardSurface(
            anime = anime,
            expanded = false,
            focused = isFocused,
            modifier = Modifier.fillMaxWidth(),
        )

        if (expanded) {
            AnimeCardSurface(
                anime = anime,
                expanded = true,
                focused = isFocused,
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(1f)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(minHeight = 0))
                        layout(placeable.width, 0) {
                            placeable.place(0, 0)
                        }
                    },
            )
        }
    }
}

@Composable
private fun AnimeCardSurface(
    anime: Anime,
    expanded: Boolean,
    focused: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = YummyRadii.smallShape
    ElevatedCard(
        modifier = modifier.then(
            if (focused) {
                Modifier.border(BorderStroke(3.dp, YummyColors.focus), shape)
            } else {
                Modifier
            },
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = shape,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(topStart = YummyRadii.small, topEnd = YummyRadii.small))
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
                        .padding(YummySpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(YummySpacing.xs),
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
                .then(if (expanded) Modifier.heightIn(min = 92.dp) else Modifier.height(92.dp))
                .padding(horizontal = YummySpacing.md, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = anime.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (expanded) 8 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            )

            Text(
                text = anime.meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
            )
        }
    }
}

@Composable
private fun DetailsScreenModern(
    state: YummyDroidUiState,
    screenStates: MutableMap<Long, AnimeDetailsScreenState>,
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
                screenStates = screenStates,
                settings = state.settings,
                videos = state.videos,
                selectedGroup = state.selectedVideoGroup,
                auth = state.auth,
                animeMark = state.animeMark,
                detailsExtras = state.detailsExtras,
                forcedOfflineMode = state.forcedOfflineMode,
                playbackProgress = state.playbackProgress,
                playbackHistory = state.playbackHistory,
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
    screenStates: MutableMap<Long, AnimeDetailsScreenState>,
    settings: AppSettings,
    videos: LoadState<List<VideoVariant>>,
    selectedGroup: String?,
    auth: AuthUiState,
    animeMark: LoadState<UserAnimeMark?>,
    detailsExtras: LoadState<AnimeDetailsExtras>,
    forcedOfflineMode: Boolean,
    playbackProgress: PlaybackProgress?,
    playbackHistory: List<PlaybackProgress>,
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
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val isWide = configuration.screenWidthDp >= 900 || (isLandscape && configuration.screenWidthDp >= 600)
    val useThreeColumnHero = configuration.screenWidthDp >= 1180
    val compactWideHero = isWide && configuration.screenHeightDp < 560
    val heroHeight = if (!isWide) {
        null
    } else if (compactWideHero) {
        (configuration.screenHeightDp * 0.44f).dp.coerceIn(210.dp, 250.dp)
    } else if (useThreeColumnHero) {
        (configuration.screenHeightDp * 0.42f).dp.coerceIn(270.dp, 340.dp)
    } else {
        (configuration.screenHeightDp * 0.42f).dp.coerceIn(250.dp, 320.dp)
    }
    val readyVideos = videos.readyListOrEmpty()
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
    fun updateScreenState(transform: (AnimeDetailsScreenState) -> AnimeDetailsScreenState) {
        val current = screenStates[details.id] ?: AnimeDetailsScreenState()
        screenStates[details.id] = transform(current)
    }

    val screenState = screenStates[details.id] ?: AnimeDetailsScreenState()
    val detailsScrollState = rememberScrollState()

    LaunchedEffect(details.id) {
        if (detailsScrollState.value != screenState.scrollValue) {
            detailsScrollState.scrollTo(screenState.scrollValue)
        }
    }

    LaunchedEffect(details.id, detailsScrollState) {
        snapshotFlow { detailsScrollState.value }
            .collect { value -> updateScreenState { it.copy(scrollValue = value) } }
    }

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
            expanded = screenState.factsExpanded,
            onExpandedChange = { expanded -> updateScreenState { it.copy(factsExpanded = expanded) } },
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
            expanded = screenState.relatedExpanded,
            onExpandedChange = { expanded -> updateScreenState { it.copy(relatedExpanded = expanded) } },
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
                playbackHistory = playbackHistory,
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
                expanded = screenState.subscriptionsExpanded,
                onExpandedChange = { expanded -> updateScreenState { it.copy(subscriptionsExpanded = expanded) } },
                onToggleVideoSubscription = onToggleVideoSubscription,
            )
            DetailsRecommendationsSection(
                extrasState = detailsExtras,
                onOpenAnime = onOpenAnime,
            )
            DetailsCommentsHostSection(
                extrasState = detailsExtras,
                totalComments = details.commentsCount,
                isAuthorized = auth.profile != null,
                scrollState = detailsScrollState,
                expanded = screenState.commentsExpanded,
                onExpandedChange = { expanded -> updateScreenState { it.copy(commentsExpanded = expanded) } },
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
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    defaultDownloadQuality: PreferredQuality,
    onResolveDownloadQualities: suspend (VideoVariant, List<VideoVariant>, Boolean) -> List<PreferredQuality>,
    onDownloadAllVideos: (String?, PreferredQuality) -> Unit,
    canDownload: Boolean,
) {
    val wideHeroActionsFocusRequester = remember { FocusRequester() }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
        details.backdropUrl?.let { backdrop ->
            PosterImage(
                url = backdrop,
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = if (isWide) 1f else 0.72f
                    },
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = if (isWide) 0.18f else 0.36f)),
            )
            if (!isWide) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
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
                        .matchParentSize()
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
                    .matchParentSize()
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
                    .matchParentSize()
                    .background(Color(0xFF050912).copy(alpha = 0.10f)),
            )
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
            val topPadding = if (compactWideHero) 8.dp else 14.dp
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
                        heroActionsFocusRequester = wideHeroActionsFocusRequester,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = if (compactWideHero) 156.dp else 0.dp),
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
                        leftExitRequester = wideHeroActionsFocusRequester,
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
                    .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 14.dp),
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
        verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp),
    ) {
        Text(
            text = details.title,
            style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            maxLines = if (compact) 2 else 3,
            overflow = TextOverflow.Ellipsis,
        )
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
    leftExitRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val profile = auth.profile
    val mark = animeMark.readyDataOrNull() ?: UserAnimeMark()

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
        leftExitRequester = leftExitRequester,
        modifier = modifier,
    )
}

@Composable
private fun AnimeMarkSegmentedControl(
    mark: UserAnimeMark,
    onSelectListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    leftExitRequester: FocusRequester? = null,
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
                    leftExitRequester = leftExitRequester,
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
                leftExitRequester = leftExitRequester,
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
    leftExitRequester: FocusRequester? = null,
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
                    leftExitRequester = leftExitRequester,
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
                    leftExitRequester = leftExitRequester,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            DetailsRatingStrip(
                ratingDetails = ratingDetails,
                leftExitRequester = leftExitRequester,
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
    leftExitRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    if (!isAuthorized) return
    RatingScale(
        selected = rating.userRating,
        onSelected = onSetAnimeRating,
        leftExitRequester = leftExitRequester,
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
    leftExitRequester: FocusRequester? = null,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .stopHorizontalFocusEscape(index, total, leftExit = leftExitRequester)
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
    val base = siteBaseUrl.trim().ifBlank { DEFAULT_SITE_BASE_URL }.trimEnd('/')
    return "$base/users/id$id"
}

private fun sitePageUrl(siteBaseUrl: String, path: String): String {
    val base = siteBaseUrl.trim().ifBlank { DEFAULT_SITE_BASE_URL }.trimEnd('/')
    return "$base/${path.trim().trimStart('/')}"
}

private fun Context.openUrl(url: String) {
    val normalized = url.trim()
    if (normalized.isBlank()) return
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, normalized.toUri()))
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
    heroActionsFocusRequester: FocusRequester? = null,
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
        } else if (details.meta.isNotBlank()) {
            Text(
                text = details.meta,
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
                    externalPrimaryFocusRequester = heroActionsFocusRequester,
                )
                AnimeMarkPanelModern(
                    auth = auth,
                    animeMark = animeMark,
                    onOpenLogin = onOpenLogin,
                    onOpenProfile = onOpenProfile,
                    onSelectListMark = onSelectListMark,
                    onToggleFavorite = onToggleFavorite,
                    leftExitRequester = heroActionsFocusRequester,
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
                externalPrimaryFocusRequester = heroActionsFocusRequester,
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
    externalPrimaryFocusRequester: FocusRequester? = null,
) {
    if (watchVideo == null) return
    var downloadDialogOpen by remember { mutableStateOf(false) }
    val internalPrimaryActionFocusRequester = remember(watchVideo.id, resumeTarget?.video?.id) { FocusRequester() }
    val primaryActionFocusRequester = externalPrimaryFocusRequester ?: internalPrimaryActionFocusRequester

    LaunchedEffect(watchVideo.id, resumeTarget?.video?.id) {
        delay(120)
        runCatching { primaryActionFocusRequester.requestFocus() }
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (resumeTarget != null) {
            DialogActionButton(
                text = uiText("Продолжить"),
                primary = true,
                modifier = Modifier.focusRequester(primaryActionFocusRequester),
                onClick = { onPlayVideoAt(resumeTarget.video, resumeTarget.positionMs) },
            )
        } else {
            DialogActionButton(
                text = uiText("Смотреть"),
                primary = true,
                modifier = Modifier.focusRequester(primaryActionFocusRequester),
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
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onGenreClick: (FilterOption) -> Unit,
    onYearClick: (Int) -> Unit,
    onStudioClick: (FilterOption) -> Unit,
    onCreatorClick: (FilterOption) -> Unit,
) {
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
                    .dpadClickable(shape) { onExpandedChange(!expanded) }
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
    leftExitRequester: FocusRequester? = null,
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
                modifier = Modifier.stopHorizontalFocusEscape(index, entries.size, leftExit = leftExitRequester),
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
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
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
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, screenshots.lastIndex),
        pageCount = { screenshots.size },
    )
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isClosing by remember { mutableStateOf(false) }
    var verticalDrag by remember { mutableFloatStateOf(0f) }

    fun closeViewer() {
        if (!isClosing) {
            isClosing = true
            onDismiss()
        }
    }

    fun showPrevious() {
        if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        }
    }

    fun showNext() {
        if (pagerState.currentPage < screenshots.lastIndex) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        }
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
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (abs(verticalDrag) > 120f) {
                                closeViewer()
                            }
                            verticalDrag = 0f
                        },
                        onDragCancel = { verticalDrag = 0f },
                    ) { _, dragAmount ->
                        verticalDrag += dragAmount
                    }
                }
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
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { index ->
                val zoomableState = rememberZoomableState()
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(screenshots[index])
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .zoomable(zoomableState),
                )
            }

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
                    text = "${pagerState.currentPage + 1} / ${screenshots.size}",
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

private data class AnimeDetailsScreenState(
    val scrollValue: Int = 0,
    val factsExpanded: Boolean = false,
    val relatedExpanded: Boolean = false,
    val subscriptionsExpanded: Boolean = false,
    val commentsExpanded: Boolean = false,
)

private const val DETAILS_STATE_ANIME_ID = 0
private const val DETAILS_STATE_SCROLL = 1
private const val DETAILS_STATE_FACTS = 2
private const val DETAILS_STATE_RELATED = 3
private const val DETAILS_STATE_SUBSCRIPTIONS = 4
private const val DETAILS_STATE_COMMENTS = 5

private val AnimeDetailsScreenStatesSaver = Saver<MutableMap<Long, AnimeDetailsScreenState>, List<List<Any>>>(
    save = { states ->
        states.map { (animeId, screenState) ->
            listOf(
                animeId,
                screenState.scrollValue,
                screenState.factsExpanded,
                screenState.relatedExpanded,
                screenState.subscriptionsExpanded,
                screenState.commentsExpanded,
            )
        }
    },
    restore = { rows ->
        mutableStateMapOf<Long, AnimeDetailsScreenState>().apply {
            rows.forEach { row ->
                val animeId = row.longAt(DETAILS_STATE_ANIME_ID) ?: return@forEach
                put(
                    animeId,
                    AnimeDetailsScreenState(
                        scrollValue = row.intAt(DETAILS_STATE_SCROLL) ?: 0,
                        factsExpanded = row.booleanAt(DETAILS_STATE_FACTS),
                        relatedExpanded = row.booleanAt(DETAILS_STATE_RELATED),
                        subscriptionsExpanded = row.booleanAt(DETAILS_STATE_SUBSCRIPTIONS),
                        commentsExpanded = row.booleanAt(DETAILS_STATE_COMMENTS),
                    ),
                )
            }
        }
    },
)

private fun List<Any>.longAt(index: Int): Long? {
    return (getOrNull(index) as? Number)?.toLong()
}

private fun List<Any>.intAt(index: Int): Int? {
    return (getOrNull(index) as? Number)?.toInt()
}

private fun List<Any>.booleanAt(index: Int): Boolean {
    return getOrNull(index) as? Boolean ?: false
}

@Composable
private fun List<VideoVariant>.downloadedEpisodeSummary(): String? {
    val allEpisodes = distinctBy { it.matchingEpisodeKey }
    val downloaded = filter { it.isOfflineAvailable }
        .distinctBy { it.matchingEpisodeKey }
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
        val bySlot = videos.map { it.matchingEpisodeKey }
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

private fun VideoVariant.localizedEpisodeTitle(episodeWord: String, fallback: String): String {
    return episode.takeIf { it.isNotBlank() }?.let { "$episodeWord $it" } ?: fallback
}

@Composable
private fun VideoVariant.localizedEpisodeTitle(): String {
    return localizedEpisodeTitle(
        episodeWord = uiText("Серия"),
        fallback = uiText("Эпизод"),
    )
}

private fun List<VideoVariant>.downloadVoiceOptions(selectedVideo: VideoVariant?): List<VideoVariant> {
    return groupBy { it.matchingVoiceKey }
        .values
        .mapNotNull { group ->
            group.minWithOrNull(
                compareBy<VideoVariant> { if (selectedVideo != null && it.groupKey == selectedVideo.groupKey) 0 else 1 }
                    .thenBy { sourceProviderRank(it.player) }
                    .thenByDescending { it.isOfflineAvailable }
                    .thenBy { it.index }
                    .thenBy { it.id },
            )
        }
        .sortedWith(
            compareBy<VideoVariant> {
                if (selectedVideo != null && it.matchingVoiceKey == selectedVideo.matchingVoiceKey) 0 else 1
            }.thenBy { it.matchingVoiceTitle },
        )
}

private fun List<VideoVariant>.downloadEpisodeCandidates(video: VideoVariant): List<VideoVariant> {
    return filter { it.isSameEpisodeAs(video) }.ifEmpty { listOf(video) }
}

@Composable
private fun VideoVariant.downloadVoiceSubtitle(videos: List<VideoVariant>): String {
    val count = videos
        .asSequence()
        .filter { it.matchingVoiceKey == matchingVoiceKey }
        .map { it.matchingEpisodeKey }
        .distinct()
        .count()
        .coerceAtLeast(1)
    return "$count ${localizedEpisodesWord(count)}"
}

private fun VideoVariant.downloadedVoiceEpisodeCount(videos: List<VideoVariant>): Int {
    return downloadedEpisodeCountForVoice(videos)
}

private fun VideoVariant.downloadedQualityEpisodeCount(
    videos: List<VideoVariant>,
    quality: PreferredQuality,
): Int {
    val targetHeight = quality.height
    return videos
        .asSequence()
        .filter { it.matchingVoiceKey == matchingVoiceKey }
        .filter { candidate ->
            candidate.offlineFiles.any { file ->
                targetHeight == null || file.qualityHeight() == targetHeight
            }
        }
        .map { it.matchingEpisodeKey }
        .distinct()
        .count()
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
    val video = videos.firstOrNull { candidate -> candidate.matchesPlaybackProgress(progress, requireGroup = true) }
        ?: videos.firstOrNull { candidate -> candidate.matchesPlaybackProgress(progress, requireGroup = false) }
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

private fun List<PlaybackProgress>.progressFor(video: VideoVariant): PlaybackProgress? {
    return firstOrNull { progress -> video.matchesPlaybackProgress(progress, requireGroup = true) }
        ?: firstOrNull { progress -> video.matchesPlaybackProgress(progress, requireGroup = false) }
}

private fun VideoVariant.matchesPlaybackProgress(
    progress: PlaybackProgress,
    requireGroup: Boolean,
): Boolean {
    if (progress.videoId > 0L && id == progress.videoId) return true
    if (requireGroup && (progress.groupKey.isBlank() || groupKey != progress.groupKey)) return false
    if (progress.episode.isBlank()) return false
    return episode.matchesProgressEpisode(progress.episode) ||
        matchingEpisodeKey == progress.episode ||
        matchingEpisodeKey.matchesProgressEpisode(progress.episode)
}

private fun PlaybackProgress.watchedAtText(): String? {
    return formatWatchedAtTimestamp(updatedAtMs)
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
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenAnime: (Long) -> Unit,
) {
    if (relatedAnime.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = YummySpacing.xl, vertical = YummySpacing.sm),
        verticalArrangement = Arrangement.spacedBy(YummySpacing.sm),
    ) {
        val shape = YummyRadii.smallShape
        AccordionHeader(
            title = uiText("Порядок просмотра"),
            expanded = expanded,
            active = false,
            onClick = { onExpandedChange(!expanded) },
        )

        if (expanded) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = yummySurfaceColor(YummySurfaceRole.Panel),
                contentColor = yummySurfaceContentColor(YummySurfaceRole.Panel),
                border = yummySurfaceBorder(YummySurfaceRole.Panel),
                shape = shape,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = YummySpacing.lg, vertical = YummySpacing.md),
                    verticalArrangement = Arrangement.spacedBy(YummySpacing.sm),
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
        YummyColors.offline
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
            .dpadClickable(YummyRadii.smallShape, onClick),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = YummyRadii.smallShape,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
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
                    verticalArrangement = Arrangement.spacedBy(YummySpacing.xs),
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
                    color = YummyColors.offline,
                    contentColor = Color.White,
                    shape = YummyRadii.pillShape,
                ) {
                    Text(
                        text = formatRating(rating),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = YummySpacing.sm, vertical = YummySpacing.xs),
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
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
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
                expanded = expanded,
                onExpandedChange = onExpandedChange,
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
    totalComments: Long,
    isAuthorized: Boolean,
    scrollState: ScrollState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAddAnimeComment: (String) -> Unit,
    onLoadMoreAnimeComments: () -> Unit,
) {
    when (extrasState) {
        LoadState.Loading -> Unit
        is LoadState.Error -> Unit
        is LoadState.Ready -> {
            DetailsCommentsSection(
                comments = extrasState.data.comments,
                totalComments = totalComments,
                commentsPaging = extrasState.data.commentsPaging,
                isAuthorized = isAuthorized,
                scrollState = scrollState,
                expanded = expanded,
                onExpandedChange = onExpandedChange,
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
    leftExitRequester: FocusRequester? = null,
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
                        .stopHorizontalFocusEscape(value - 1, 10, leftExit = leftExitRequester)
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
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onToggleVideoSubscription: (VideoVariant) -> Unit,
) {
    if (auth.profile == null || videos.isEmpty()) return
    val groups = videos
        .filter { it.matchingDubbingKey.isNotBlank() }
        .groupBy { it.matchingDubbingKey }
        .values
        .mapNotNull { group -> group.minByOrNull { it.player } }
        .sortedBy { it.matchingDubbingTitle }
        .take(18)
    if (groups.isEmpty()) return
    val activeCount = groups.count { subscriptions.isVideoVoiceSubscribed(it) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AccordionHeader(
            title = uiText("Подписка"),
            expanded = expanded,
            active = activeCount > 0,
            onClick = { onExpandedChange(!expanded) },
            trailingText = activeCount.takeIf { it > 0 }?.toString(),
        )

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
                            yummySurfaceColor(YummySurfaceRole.ActiveRow)
                        } else {
                            Color.Transparent
                        },
                        contentColor = if (subscribed) {
                            yummySurfaceContentColor(YummySurfaceRole.ActiveRow)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        border = yummySurfaceBorder(if (subscribed) YummySurfaceRole.ActiveRow else YummySurfaceRole.Row),
                        shape = itemShape,
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = video.matchingDubbingTitle,
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
    totalComments: Long,
    commentsPaging: PagingUiState,
    isAuthorized: Boolean,
    scrollState: ScrollState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAddAnimeComment: (String) -> Unit,
    onLoadMoreAnimeComments: () -> Unit,
) {
    if (comments.isEmpty() && !isAuthorized) return
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
        val commentsProgressText = if (comments.isNotEmpty()) {
            if (totalComments > 0L) {
                "${comments.size} ${uiText("из")} ${formatViews(totalComments)} ${uiText("загружено")}"
            } else {
                "${comments.size} ${uiText("загружено")}"
            }
        } else {
            null
        }
        AccordionHeader(
            title = uiText("Комментарии"),
            expanded = expanded,
            active = false,
            onClick = { onExpandedChange(!expanded) },
            trailingText = commentsProgressText,
        )

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
    playbackHistory: List<PlaybackProgress> = emptyList(),
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
    val episodeViewsByKey = remember(videos) {
        videos
            .distinctBy { it.id }
            .groupBy { it.matchingEpisodeKey }
            .mapValues { (_, episodeVideos) -> episodeVideos.sumOf { it.views } }
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
                val watchProgress = remember(playbackHistory, video.id, video.episode) {
                    playbackHistory.progressFor(video)
                }
                EpisodeCard(
                    video = video,
                    episodeViews = episodeViewsByKey[video.matchingEpisodeKey] ?: video.views,
                    watchProgress = watchProgress,
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
            title = "${uiText("Скачать")} ${video.localizedEpisodeTitle()}",
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
    episodeViews: Long,
    modifier: Modifier = Modifier,
    watchProgress: PlaybackProgress? = null,
    downloadedVariants: List<VideoVariant> = if (video.isOfflineAvailable) listOf(video) else emptyList(),
    onClick: () -> Unit,
    onDownloadClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    enabled: Boolean = true,
    canDownload: Boolean = true,
) {
    val contentAlpha = if (enabled) 1f else 0.46f
    val watchedAtText = watchProgress?.watchedAtText()
    val shape = YummyRadii.smallShape
    Surface(
        shape = shape,
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
        border = yummySurfaceBorder(YummySurfaceRole.Row),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(if (watchedAtText == null) YummySizes.episodeHeight else YummySizes.episodeWatchedHeight)
            .dpadClickable(shape, enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = YummySpacing.md, vertical = 10.dp)
                .graphicsLayer { alpha = contentAlpha },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.md),
        ) {
            Surface(
                shape = YummyRadii.pillShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(11.dp)
                        .size(YummySizes.episodePlayIcon),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 0.dp),
                verticalArrangement = Arrangement.spacedBy(YummySpacing.xxs),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
                ) {
                    Text(
                        text = video.localizedEpisodeTitle(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (video.isOfflineAvailable) {
                        Surface(
                            color = YummyColors.offline,
                            contentColor = Color.Black,
                            shape = YummyRadii.pillShape,
                        ) {
                            Text(
                                text = "OFF",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    text = listOfNotNull(
                        formatDuration(video.durationSeconds),
                        formatViews(episodeViews),
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (watchedAtText != null) {
                    Text(
                        text = "\u2713 $watchedAtText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (canDownload || downloadedVariants.isNotEmpty()) {
                Column(
                    modifier = Modifier.width(YummySizes.compactIconButton),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(YummySpacing.xxs, Alignment.CenterVertically),
                ) {
                    if (canDownload) {
                        IconButton(
                            onClick = onDownloadClick,
                            enabled = canDownload,
                            modifier = Modifier
                                .size(YummySizes.compactIconButton)
                                .focusRing(shape),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = uiText("Скачать серию"),
                                modifier = Modifier.size(YummySizes.actionIcon),
                            )
                        }
                    }
                    if (downloadedVariants.isNotEmpty()) {
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier
                                .size(YummySizes.compactIconButton)
                                .focusRing(shape),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = uiText("Удалить скачанную серию"),
                                modifier = Modifier.size(YummySizes.actionIcon),
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
        .ifBlank { variant.matchingVoiceTitle }
        .ifBlank { file.player.cleanVideoSourceLabel() }
        .ifBlank { variant.player.cleanVideoSourceLabel() }
        .ifBlank { "Озвучка" }
}

private fun OfflineDeleteFile.displayKey(): String {
    return cacheRowKey()
}

private fun OfflineDeleteFile.cacheRowKey(): String {
    return listOf(
        variant.offlineEpisodeIdentity(),
        displayVoiceTitle().lowercase(Locale.ROOT),
        file.qualityDisplayTitle().lowercase(Locale.ROOT),
    ).joinToString("|")
}

private fun OfflineDeleteFile.displayTitle(totalBytes: Long = file.bytes): String {
    return listOf(
        displayVoiceTitle(),
        file.qualityDisplayTitle(),
        totalBytes.takeIf { it > 0L }?.let(::formatByteSize),
    ).filterNot { it.isNullOrBlank() }.joinToString(" • ")
}

private fun VideoVariant.offlineEpisodeIdentity(): String {
    return episode.trim().takeIf { it.isNotBlank() }
        ?: index.takeIf { it > 0 }?.toString()
        ?: id.toString()
}

private fun VideoVariant.offlineEpisodeSortKey(): Double {
    return offlineEpisodeIdentity().toDoubleOrNull() ?: index.takeIf { it > 0 }?.toDouble() ?: Double.MAX_VALUE
}

private fun OfflineVideoFile.voiceTitleFromDownloadPath(): String {
    val path = playbackUrl.toUri().path.orEmpty()
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
        .groupBy { it.matchingVoiceKey }
        .values
        .map { variants -> variants.sortedForPlayer() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${uiText("Удалить")} ${video.localizedEpisodeTitle()}") },
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
                items(voiceGroups, key = { variants -> "delete-offline:${variants.first().matchingVoiceKey}" }) { variants ->
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
                        bytes.takeIf { it > 0L }?.let(::formatByteSize),
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
private const val SUBTITLE_MENU_GROUP_ID = 22
private const val SUBTITLE_OFF_KEY = "off"
private const val PIP_ENTER_DELAY_MS = 120L
private const val PLAYER_TIMELINE_SCRUB_COMMIT_DELAY_MS = 900L
private const val PLAYER_TIMELINE_MANUAL_FREEZE_MS = 2_000L
private const val PLAYER_TIMELINE_BASE_STEP_MS = 5_000L
private const val PLAYER_TIMELINE_MAX_STEP_DIVISOR = 20L
private const val PLAYBACK_PROGRESS_SAVE_INTERVAL_MS = 15_000L
private const val PLAYBACK_BUFFERING_FALLBACK_DELAY_MS = 900L
private const val PLAYBACK_SEEK_BUFFER_GRACE_MS = 4_500L
private const val PLAYBACK_BUFFER_STALL_CONFIRM_MS = 1_000L
private const val PLAYBACK_BUFFER_STALL_SWITCH_MS = 1_500L
private const val PLAYBACK_BUFFER_STALL_POLL_MS = 350L
private const val PLAYBACK_BUFFER_GROWTH_EPSILON_MS = 500L
private const val PLAYBACK_BUFFER_END_IGNORE_MS = 30_000L
private const val PLAYBACK_BUFFER_END_EPSILON_MS = 1_000L
private const val PLAYBACK_RECOVERY_PREBUFFER_MIN_MS = 3_000L
private const val PLAYBACK_RECOVERY_PREBUFFER_TIMEOUT_MS = 20_000L
private const val PLAYBACK_RECOVERY_PREBUFFER_POLL_MS = 250L
private const val SKIP_PROMPT_COUNTDOWN_SECONDS = 8
private const val SKIP_PROMPT_POLL_MS = 500L
private const val SKIP_PROMPT_ZERO_DISPLAY_MS = 350L

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
    val startedAtMs: Long,
    val deadlineMs: Long,
    var autoSkipEnabled: Boolean,
)

@Composable
private fun PlayerScreen(
    animeTitle: String,
    video: VideoVariant,
    settings: AppSettings,
    startPositionMs: Long,
    preferredQuality: PreferredQuality,
    allVideos: List<VideoVariant>,
    selectedGroup: String?,
    streamState: LoadState<ResolvedVideoStream>,
    pendingPlaybackRecovery: PlaybackRecoveryCandidate?,
    isInPictureInPicture: Boolean,
    forcedOfflineMode: Boolean,
    allowSubscriptions: Boolean,
    subscriptions: List<VideoSubscription>,
    onSelectGroup: (String) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    onPlayVideoAtQuality: (VideoVariant, Long, PreferredQuality) -> Unit,
    onToggleVideoSubscription: (VideoVariant) -> Unit,
    onRetry: () -> Unit,
    onPlaybackFailed: (VideoVariant, Long) -> Unit,
    onPrepareFallbackSource: (VideoVariant) -> Unit,
    onSwitchToPreparedFallbackSource: (VideoVariant, Long) -> Boolean,
    onRecoveryPrebufferReady: (Long, Long) -> Boolean,
    onRecoveryPrebufferFailed: (Long) -> Unit,
    onPlaybackStarted: (VideoVariant) -> Unit,
    onPlaybackEnded: (VideoVariant) -> Unit,
    onPlaybackProgress: (VideoVariant, Long, Long) -> Unit,
    canUsePictureInPicture: Boolean,
    onEnterPictureInPicture: () -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
    onRegisterPlayerInputActionHandler: (((InputActionEvent) -> Boolean)?) -> Unit,
) {
    val sourceVideos = allVideos.ifEmpty { listOf(video) }
    val videos = if (forcedOfflineMode) {
        sourceVideos.filter { it.isOfflineAvailable }
            .ifEmpty { listOf(video).filter { it.isOfflineAvailable } }
    } else {
        sourceVideos
    }
    val groups = remember(videos) { videos.groupBy { it.matchingVoiceKey } }
    val selectedKey = selectedGroup
        ?.let { groupKey -> videos.firstOrNull { it.groupKey == groupKey }?.matchingVoiceKey }
        ?.takeIf(groups::containsKey)
        ?: video.matchingVoiceKey.takeIf(groups::containsKey)
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
                        onPlayVideoAtQuality(replacement, startPositionMs, preferredQuality)
                    } else {
                        onSelectGroup(groupKey)
                    }
                },
                onPlayVideo = { next ->
                    onSelectGroup(next.groupKey)
                    onPlayVideoAtQuality(next, 0L, preferredQuality)
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
                        onPlayVideoAtQuality(replacement, startPositionMs, preferredQuality)
                    } else {
                        onSelectGroup(groupKey)
                    }
                },
                onPlayVideo = { next ->
                    onSelectGroup(next.groupKey)
                    onPlayVideoAtQuality(next, 0L, preferredQuality)
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
                playbackPreferredQuality = preferredQuality,
                pendingPlaybackRecovery = pendingPlaybackRecovery,
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
                        onPlayVideoAtQuality(replacement, positionMs, preferredQuality)
                    } else {
                        onSelectGroup(groupKey)
                    }
                },
                onPlayVideo = { next ->
                    onSelectGroup(next.groupKey)
                    onPlayVideoAtQuality(next, 0L, preferredQuality)
                },
                onPlayVideoAt = { next, positionMs ->
                    onSelectGroup(next.groupKey)
                    onPlayVideoAtQuality(next, positionMs, preferredQuality)
                },
                onPlayVideoAtQuality = { next, positionMs, preferredQuality ->
                    onSelectGroup(next.groupKey)
                    onPlayVideoAtQuality(next, positionMs, preferredQuality)
                },
                onPlaybackFailed = onPlaybackFailed,
                onPrepareFallbackSource = onPrepareFallbackSource,
                onSwitchToPreparedFallbackSource = onSwitchToPreparedFallbackSource,
                onRecoveryPrebufferReady = onRecoveryPrebufferReady,
                onRecoveryPrebufferFailed = onRecoveryPrebufferFailed,
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
    val configuration = LocalConfiguration.current
    val playerControlTexts = rememberPlayerControlTexts()
    Box(
        modifier = modifier.background(Color.Black),
    ) {
        key(
            configuration.orientation,
            configuration.screenWidthDp,
            configuration.screenHeightDp,
            configuration.smallestScreenWidthDp,
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
                        showCenterControls = message == null,
                        texts = playerControlTexts,
                        onToggleSubscription = onToggleSubscription,
                        onSelectGroup = onSelectGroup,
                        onPlayVideo = onPlayVideo,
                        onBack = onBack,
                    )
                    view.showController()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

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
                    .fillMaxSize()
                    .padding(horizontal = 28.dp)
                    .padding(top = 112.dp, bottom = 176.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                DialogActionButton(
                    text = uiText("Повторить"),
                    primary = true,
                    onClick = onRetry,
                )
            }
        }
    }
}

private inline fun <reified T> View.tagValue(tagId: Int): T? {
    return getTag(tagId) as? T
}

private fun View.clearTagValue(tagId: Int) {
    setTag(tagId, null)
}

private fun View.removeTaggedRunnable(tagId: Int) {
    tagValue<Runnable>(tagId)?.let(::removeCallbacks)
    clearTagValue(tagId)
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
    showCenterControls: Boolean,
    texts: PlayerControlTexts,
    onToggleSubscription: () -> Unit,
    onSelectGroup: (String, VideoVariant?) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onBack: () -> Unit,
) {
    findViewById<TextView>(R.id.yummy_player_title)?.text = animeTitle.ifBlank { texts.title }
    findViewById<TextView>(R.id.yummy_player_subtitle)?.text = currentVideo.playbackSubtitle(texts)
    findViewById<TextView>(R.id.yummy_player_info)?.text = currentVideo.playbackSourceLabel(false)
    findViewById<TextView>(Media3R.id.exo_position)?.text = context.getString(R.string.player_zero_time)
    findViewById<TextView>(Media3R.id.exo_duration)?.text = context.getString(R.string.player_zero_time)

    findViewById<View>(Media3R.id.exo_settings)?.visibility = View.GONE
    findViewById<View>(R.id.yummy_skip_controls)?.visibility = View.GONE
    findViewById<View>(Media3R.id.exo_play_pause)?.visibility = View.GONE
    findViewById<View>(R.id.yummy_player_back)?.setOnClickListener { onBack() }
    findViewById<View>(R.id.yummy_player_episode_controls)?.visibility = if (showCenterControls) {
        View.VISIBLE
    } else {
        View.GONE
    }

    findViewById<View>(R.id.yummy_episode_previous)?.apply {
        visibility = if (showCenterControls && previousVideo != null) View.VISIBLE else View.GONE
        setOnClickListener { previousVideo?.let(onPlayVideo) }
    }
    findViewById<View>(R.id.yummy_episode_next)?.apply {
        visibility = if (showCenterControls && nextVideo != null) View.VISIBLE else View.GONE
        setOnClickListener { nextVideo?.let(onPlayVideo) }
    }

    findViewById<TextView>(R.id.yummy_player_voice)?.apply {
        text = texts.voice
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
                texts = texts,
                onSelectGroup = onSelectGroup,
            )
        }
    }

    findViewById<TextView>(R.id.yummy_player_quality)?.apply {
        text = texts.quality
        visibility = View.VISIBLE
        setPlayerControlEnabled(false)
    }
    findViewById<TextView>(R.id.yummy_player_subtitles)?.apply {
        text = texts.subtitles
        visibility = View.GONE
        setPlayerControlEnabled(false)
    }
    findViewById<TextView>(R.id.yummy_player_subscription)?.apply {
        text = if (subscriptionActive) texts.subscribed else texts.subscription
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
        text = context.getString(R.string.player_pip)
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
        compareBy<VideoVariant> { it.episodeOrderValue() ?: Double.MAX_VALUE }
            .thenBy { it.index.takeIf { index -> index > 0 } ?: Int.MAX_VALUE }
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
    return groupBy { it.matchingEpisodeKey }
        .values
        .mapNotNull { variants ->
            variants.minWithOrNull(
                compareBy<VideoVariant> { if (it.isOfflineAvailable) 0 else 1 }
                    .thenBy { if (it.groupKey == preferredGroupKey) 0 else 1 }
                    .thenBy { sourceProviderRank(it.player) }
                    .thenBy { it.index }
                    .thenBy { it.id },
            )
        }
        .sortedForPlayer()
}

private fun List<VideoSubscription>.isVideoVoiceSubscribed(video: VideoVariant): Boolean {
    return isSubscribedTo(video)
}

private fun VideoVariant.playbackSourceLabel(isLocalPlayback: Boolean = localPlaybackUrl.isNotBlank()): String {
    return if (isLocalPlayback) {
        "Local"
    } else {
        player.cleanVideoSourceLabel().ifBlank { player }.ifBlank { "HLS" }
    }
}

private fun ResolvedSubtitleTrack.toMedia3SubtitleConfiguration(): MediaItem.SubtitleConfiguration? {
    val cleanUri = uri.takeIf { it.isNotBlank() } ?: return null
    val resolvedMimeType = subtitleMimeTypeForMedia3(cleanUri, mimeType)
        ?.takeIf { it.isSideLoadedSubtitleMimeType() }
        ?: return null
    return MediaItem.SubtitleConfiguration.Builder(cleanUri.toUri()).apply {
        setMimeType(resolvedMimeType)
        language?.takeIf { it.isNotBlank() }?.let(::setLanguage)
        label.takeIf { it.isNotBlank() }?.let(::setLabel)
        setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
    }.build()
}

private fun subtitleMimeTypeForMedia3(uri: String, mimeType: String?): String? {
    val source = mimeType?.takeIf { it.isNotBlank() } ?: uri
    val lower = source.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
    return when {
        "mpegurl" in lower || lower.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
        "subrip" in lower || lower.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
        "text/vtt" in lower || lower.endsWith(".vtt") -> MimeTypes.TEXT_VTT
        "text/x-ssa" in lower || lower.endsWith(".ass") || lower.endsWith(".ssa") -> MimeTypes.TEXT_SSA
        "ttml" in lower || lower.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
        else -> null
    }
}

private fun String.isSideLoadedSubtitleMimeType(): Boolean {
    return this == MimeTypes.TEXT_VTT ||
        this == MimeTypes.APPLICATION_SUBRIP ||
        this == MimeTypes.TEXT_SSA ||
        this == MimeTypes.APPLICATION_TTML
}

@OptIn(UnstableApi::class)
private fun createVideoPlayer(
    context: Context,
    stream: ResolvedVideoStream,
    startPositionMs: Long,
    httpClient: OkHttpClient,
    renderersFactory: DefaultRenderersFactory,
    loadControl: DefaultLoadControl,
): ExoPlayer {
    val userAgent = stream.headers["User-Agent"] ?: APP_USER_AGENT
    val trackSelector = DefaultTrackSelector(context).apply {
        parameters = buildUponParameters()
            .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            .setMaxVideoBitrate(Int.MAX_VALUE)
            .build()
    }
    val httpDataSourceFactory = OkHttpDataSource.Factory(httpClient)
        .setUserAgent(userAgent)
        .setDefaultRequestProperties(stream.headers)
    val dataSourceFactory: DataSource.Factory = if (stream.url.startsWith("file:", ignoreCase = true)) {
        DefaultDataSource.Factory(context)
    } else {
        DefaultDataSource.Factory(context, httpDataSourceFactory)
    }
    val mediaItemBuilder = MediaItem.Builder().setUri(stream.url)
    stream.mimeType?.let { mediaItemBuilder.setMimeType(it) }
    val subtitleConfigurations = stream.subtitles.mapNotNull { it.toMedia3SubtitleConfiguration() }
    if (subtitleConfigurations.isNotEmpty()) {
        mediaItemBuilder.setSubtitleConfigurations(subtitleConfigurations)
    }

    return ExoPlayer.Builder(context, renderersFactory)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .setTrackSelector(trackSelector)
        .setLoadControl(loadControl)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()
        .apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            setMediaItem(mediaItemBuilder.build(), startPositionMs.coerceAtLeast(0L))
            playWhenReady = false
            prepare()
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

private fun List<VideoVariant>.sourceQualityOptionsFor(currentVideo: VideoVariant): List<QualityOption> {
    val qualities = filter { it.isSameEpisodeAs(currentVideo) && it.matchingVoiceKey == currentVideo.matchingVoiceKey }
        .flatMap { it.sourceQualities }
    return qualities.sourceQualityOptions()
}

private fun List<SourceQuality>.sourceQualityOptions(): List<QualityOption> {
    return bestSourceQualityPerHeight().mapNotNull { quality ->
        val preferredQuality = PreferredQuality.fromHeight(quality.height) ?: return@mapNotNull null
        val label = quality.title.takeIf { it.isNotBlank() } ?: preferredQuality.title
        QualityOption(
            group = null,
            trackIndex = -1,
            label = label,
            height = quality.height ?: 0,
            bitrate = quality.bitrate,
            key = "source:${quality.height}:${quality.bitrate}",
            preferredQuality = preferredQuality,
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

private fun VideoVariant.withoutLocalPlayback(): VideoVariant {
    return copy(
        localPlaybackUrl = "",
        localMimeType = null,
        localBytes = 0L,
        localFiles = emptyList(),
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

private fun SubtitleOption.subtitleOptionIdentity(): String {
    return listOf(
        language.orEmpty().lowercase(Locale.ROOT),
        label.lowercase(Locale.ROOT),
        key.lowercase(Locale.ROOT),
    ).joinToString(":").replace(Regex("""\s+"""), "")
}

private fun SubtitleOption.matchesSelectedSubtitleKey(selectedSubtitleKey: String?): Boolean {
    val selected = selectedSubtitleKey?.takeIf { it.isNotBlank() } ?: return false
    return key == selected || subtitleOptionIdentity() == selected
}

private fun VideoVariant.playbackSubtitle(texts: PlayerControlTexts): String {
    val voice = dubbing.cleanVideoSourceLabel()
    return listOf(voice, localizedEpisodeTitle(texts.episode, texts.episodeFallback))
        .filterNot { it.isNullOrBlank() }
        .joinToString(" • ")
}

private fun findAdjacentPlayerVideo(
    currentVideo: VideoVariant,
    allVideos: List<VideoVariant>,
    selectedGroup: String?,
    forward: Boolean,
): VideoVariant? {
    val videos = allVideos.ifEmpty { listOf(currentVideo) }
    val preferredVoiceKey = selectedGroup
        ?.let { groupKey -> videos.firstOrNull { it.groupKey == groupKey }?.matchingVoiceKey }
        ?: currentVideo.matchingVoiceKey
    val preferredGroupKey = selectedGroup?.takeIf { groupKey -> videos.any { it.groupKey == groupKey } }
        ?: currentVideo.groupKey

    val episodeVideos = videos
        .groupBy { it.matchingEpisodeKey }
        .values
        .mapNotNull { variants ->
            variants.minWithOrNull(
                compareBy<VideoVariant> { if (it.matchingVoiceKey == preferredVoiceKey) 0 else 1 }
                    .thenBy { if (it.groupKey == preferredGroupKey) 0 else 1 }
                    .thenBy { if (it.isOfflineAvailable) 0 else 1 }
                    .thenBy { sourceProviderRank(it.player) }
                    .thenBy { it.index }
                    .thenBy { it.id },
            )
        }
        .sortedForPlayer()

    val currentIndex = episodeVideos.indexOfFirst { it.isSameEpisodeAs(currentVideo) }
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
    if (previousVideo.matchingVoiceKey == nextVideo.matchingVoiceKey) return
    Toast.makeText(
        context,
        "Озвучка «${previousVideo.matchingVoiceTitle}» недоступна для ${nextVideo.episodeTitle}. Включена «${nextVideo.matchingVoiceTitle}».",
        Toast.LENGTH_LONG,
    ).show()
}

private data class PlayerControlTexts(
    val title: String,
    val watch: String,
    val voice: String,
    val quality: String,
    val subtitles: String,
    val subtitlesOff: String,
    val subscription: String,
    val subscribed: String,
    val skip: String,
    val episode: String,
    val episodeFallback: String,
    val downloaded: String,
)

private val defaultPlayerControlTexts = PlayerControlTexts(
    title = "Просмотр",
    watch = "Смотреть",
    voice = "Озвучка",
    quality = "Качество",
    subtitles = "Субтитры",
    subtitlesOff = "Выкл.",
    subscription = "Подписка",
    subscribed = "Подписан",
    skip = "Пропустить",
    episode = "Серия",
    episodeFallback = "Эпизод",
    downloaded = "скачано",
)

@Composable
private fun rememberPlayerControlTexts(): PlayerControlTexts {
    return PlayerControlTexts(
        title = uiText("Просмотр"),
        watch = uiText("Смотреть"),
        voice = uiText("Озвучка"),
        quality = uiText("Качество"),
        subtitles = uiText("Субтитры"),
        subtitlesOff = uiText("Выкл."),
        subscription = uiText("Подписка"),
        subscribed = uiText("Подписан"),
        skip = uiText("Пропустить"),
        episode = uiText("Серия"),
        episodeFallback = uiText("Эпизод"),
        downloaded = uiText("Скачано").lowercase(Locale.ROOT),
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun NativeVideoPlayer(
    stream: ResolvedVideoStream,
    animeTitle: String,
    currentVideo: VideoVariant,
    settings: AppSettings,
    startPositionMs: Long,
    playbackPreferredQuality: PreferredQuality,
    pendingPlaybackRecovery: PlaybackRecoveryCandidate?,
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
    onPlayVideoAtQuality: (VideoVariant, Long, PreferredQuality) -> Unit,
    onPlaybackFailed: (VideoVariant, Long) -> Unit,
    onPrepareFallbackSource: (VideoVariant) -> Unit,
    onSwitchToPreparedFallbackSource: (VideoVariant, Long) -> Boolean,
    onRecoveryPrebufferReady: (Long, Long) -> Boolean,
    onRecoveryPrebufferFailed: (Long) -> Unit,
    onPlaybackStarted: (VideoVariant) -> Unit,
    onPlaybackEnded: (VideoVariant) -> Unit,
    onPlaybackProgress: (VideoVariant, Long, Long) -> Unit,
    canUsePictureInPicture: Boolean,
    isInPictureInPicture: Boolean,
    onEnterPictureInPicture: () -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
    onRegisterPlayerInputActionHandler: (((InputActionEvent) -> Boolean)?) -> Unit,
    offlineMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val activity = remember(context) { context.findActivity() }
    val fallbackScope = rememberCoroutineScope()
    val playerControlTexts = rememberPlayerControlTexts()
    val currentSettings by rememberUpdatedState(settings)
    val currentProgressCallback by rememberUpdatedState(onPlaybackProgress)
    val currentProgressVideo by rememberUpdatedState(currentVideo)
    val latestCurrentVideo by rememberUpdatedState(currentVideo)
    val latestPreviousVideo by rememberUpdatedState(previousVideo)
    val latestNextVideo by rememberUpdatedState(nextVideo)
    val latestPlayVideoAt by rememberUpdatedState(onPlayVideoAt)
    val latestPrepareFallbackSource by rememberUpdatedState(onPrepareFallbackSource)
    val latestSwitchToPreparedFallbackSource by rememberUpdatedState(onSwitchToPreparedFallbackSource)
    val latestRecoveryPrebufferReady by rememberUpdatedState(onRecoveryPrebufferReady)
    val latestRecoveryPrebufferFailed by rememberUpdatedState(onRecoveryPrebufferFailed)
    var fallbackSuppressedUntilMs by remember(stream.url, currentVideo.id) {
        mutableLongStateOf(SystemClock.elapsedRealtime() + PLAYBACK_SEEK_BUFFER_GRACE_MS)
    }
    var bufferResetSignal by remember(stream.url, currentVideo.id) { mutableIntStateOf(0) }
    val httpClient = remember { defaultVideoResolveClient() }
    val renderersFactory = remember(context, settings.decoderMode) {
        DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(settings.decoderMode.mediaCodecSelector())
    }
    val player = remember(
        stream.url,
        stream.headers,
        stream.subtitles,
        startPositionMs,
        httpClient,
        renderersFactory,
        settings.playerBufferPreset,
    ) {
        createVideoPlayer(
            context = context,
            stream = stream,
            startPositionMs = startPositionMs,
            httpClient = httpClient,
            renderersFactory = renderersFactory,
            loadControl = settings.playerBufferPreset.toLoadControl(),
        )
    }
    DisposableEffect(
        pendingPlaybackRecovery?.id,
        pendingPlaybackRecovery?.stream?.url,
        player,
        settings.playerBufferPreset,
        httpClient,
        renderersFactory,
    ) {
        val recovery = pendingPlaybackRecovery
        if (
            recovery == null ||
            recovery.stream.url.isBlank() ||
            recovery.stream.url == stream.url ||
            stream.url.startsWith("file:", ignoreCase = true) ||
            stream.url.startsWith("content:", ignoreCase = true) ||
            recovery.stream.url.startsWith("file:", ignoreCase = true) ||
            recovery.stream.url.startsWith("content:", ignoreCase = true)
        ) {
            onDispose {}
        } else {
            val targetBufferMs = settings.playerBufferPreset.recoveryPrebufferTargetMs()
            val probeStartPositionMs = recovery.positionMs.coerceAtLeast(0L)
            var finished = false
            val probePlayer = runCatching {
                createVideoPlayer(
                    context = context,
                    stream = recovery.stream,
                    startPositionMs = probeStartPositionMs,
                    httpClient = httpClient,
                    renderersFactory = renderersFactory,
                    loadControl = settings.playerBufferPreset.toRecoveryPrebufferLoadControl(),
                ).apply {
                    volume = 0f
                    playWhenReady = false
                }
            }.getOrElse { throwable ->
                AppLog.w("YummyDroidPlayer", "Recovery prebuffer failed to start", throwable)
                latestRecoveryPrebufferFailed(recovery.id)
                null
            }

            if (probePlayer == null) {
                onDispose {}
            } else {
                fun failRecovery(throwable: Throwable? = null) {
                    if (finished) return
                    finished = true
                    if (throwable != null) {
                        AppLog.w("YummyDroidPlayer", "Recovery prebuffer failed", throwable)
                    }
                    latestRecoveryPrebufferFailed(recovery.id)
                }

                fun bufferedAheadMs(): Long {
                    val bufferedPosition = probePlayer.bufferedPosition.takeIf { it != C.TIME_UNSET } ?: 0L
                    return (bufferedPosition - probeStartPositionMs).coerceAtLeast(0L)
                }

                fun maybeSwitchAfterBuffer(): Boolean {
                    if (finished) return true
                    if (probePlayer.playbackState != Player.STATE_READY) return false
                    if (bufferedAheadMs() < targetBufferMs) return false
                    finished = true
                    latestRecoveryPrebufferReady(
                        recovery.id,
                        player.currentPosition.coerceAtLeast(0L),
                    )
                    return true
                }

                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        maybeSwitchAfterBuffer()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        failRecovery(error)
                    }
                }
                probePlayer.addListener(listener)
                val prebufferJob = fallbackScope.launch {
                    val startedAtMs = SystemClock.elapsedRealtime()
                    while (!finished) {
                        delay(PLAYBACK_RECOVERY_PREBUFFER_POLL_MS)
                        if (maybeSwitchAfterBuffer()) break
                        if (SystemClock.elapsedRealtime() - startedAtMs >= PLAYBACK_RECOVERY_PREBUFFER_TIMEOUT_MS) {
                            failRecovery()
                        }
                    }
                }

                onDispose {
                    prebufferJob.cancel()
                    probePlayer.removeListener(listener)
                    probePlayer.release()
                }
            }
        }
    }
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    val onlineQualityOptions = remember(tracks) { tracks.videoQualityOptions() }
    val subtitleOptions = remember(tracks, playerControlTexts) { tracks.subtitleOptions(playerControlTexts) }
    val sourceQualityOptions = remember(
        groups,
        selectedKey,
        currentVideo.matchingEpisodeKey,
        currentVideo.matchingVoiceKey,
    ) {
        val sourceVideos = groups[selectedKey].orEmpty().ifEmpty { groups[currentVideo.matchingVoiceKey].orEmpty() }
        sourceVideos.sourceQualityOptionsFor(currentVideo)
    }
    val streamQualityOptions = remember(stream.availableQualities) {
        stream.availableQualities.sourceQualityOptions()
    }
    val localQualityOptions = remember(
        currentVideo.matchingEpisodeKey,
        currentVideo.matchingVoiceKey,
        currentVideo.localPlaybackUrl,
        currentVideo.localFiles,
    ) {
        currentVideo.localQualityOptions()
    }
    val qualityOptions = remember(onlineQualityOptions, sourceQualityOptions, streamQualityOptions, localQualityOptions, offlineMode) {
        mergeVideoQualityOptions(
            onlineOptions = onlineQualityOptions + sourceQualityOptions + streamQualityOptions,
            localOptions = localQualityOptions,
            offlineMode = offlineMode,
        )
    }
    val streamSelectedQualityKey = remember(currentVideo.id, stream.url, stream.selectedVideoHeight) {
        stream.selectedVideoHeight
            ?.takeIf { it > 0 }
            ?.let { "height:$it" }
    }
    val latestQualityOptions by rememberUpdatedState(qualityOptions)
    val latestPlaybackPreferredQuality by rememberUpdatedState(playbackPreferredQuality)
    val latestStreamSelectedQualityKey by rememberUpdatedState(streamSelectedQualityKey)
    var selectedQualityKey by remember(currentVideo.id, stream.url, stream.selectedVideoHeight) {
        val preferredOption = qualityOptions.preferredOption(
            playbackPreferredQuality.takeUnless { it == PreferredQuality.Auto } ?: settings.defaultQuality,
        )
        mutableStateOf(
            currentVideo.selectedLocalQualityKey(stream.url)
                ?: streamSelectedQualityKey?.takeIf { key -> qualityOptions.any { it.matchesSelectedQualityKey(key) } }
                ?: preferredOption?.qualityOptionIdentity(),
        )
    }
    var selectedSubtitleKey by remember(currentVideo.id, stream.url) {
        mutableStateOf(SUBTITLE_OFF_KEY)
    }
    var subtitleSelectionTouched by remember(currentVideo.id, stream.url) {
        mutableStateOf(false)
    }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    DisposableEffect(player, isInPictureInPicture) {
        onRegisterPlayerInputActionHandler { event ->
            val view = playerView
            if (view == null || isInPictureInPicture) {
                false
            } else {
                view.handleRemoteInputAction(event)
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
        val currentKey = selectedQualityKey
        if (currentKey != null && qualityOptions.none { it.matchesSelectedQualityKey(currentKey) }) {
            val preferredQuality = playbackPreferredQuality.takeUnless { it == PreferredQuality.Auto } ?: settings.defaultQuality
            selectedQualityKey = streamSelectedQualityKey
                ?.takeIf { key -> qualityOptions.any { it.matchesSelectedQualityKey(key) } }
                ?: qualityOptions.preferredOption(preferredQuality)?.qualityOptionIdentity()
        }
    }

    LaunchedEffect(qualityOptions, playbackPreferredQuality, settings.defaultQuality, stream.url, streamSelectedQualityKey) {
        if (selectedQualityKey != null && qualityOptions.any { it.matchesSelectedQualityKey(selectedQualityKey) }) {
            return@LaunchedEffect
        }
        val resolvedSourceKey = streamSelectedQualityKey
            ?.takeIf { key -> qualityOptions.any { it.matchesSelectedQualityKey(key) } }
        val preferredOption = qualityOptions.preferredOption(
            playbackPreferredQuality.takeUnless { it == PreferredQuality.Auto } ?: settings.defaultQuality,
        )
        val preferredKey = resolvedSourceKey ?: preferredOption?.qualityOptionIdentity()
        if (preferredKey != null && selectedQualityKey != preferredKey) {
            preferredOption?.takeIf { it.group != null }?.let(player::selectQuality)
            selectedQualityKey = preferredKey
            playerView?.findViewById<TextView>(R.id.yummy_player_quality)
                ?.setTag(R.id.yummy_player_quality, preferredKey)
        }
    }

    LaunchedEffect(player, qualityOptions, playbackPreferredQuality, settings.defaultQuality, stream.url) {
        val preferredQuality = playbackPreferredQuality.takeUnless { it == PreferredQuality.Auto }
            ?: settings.defaultQuality.takeUnless { it == PreferredQuality.Auto }
        val preferredOption = preferredQuality?.let { qualityOptions.preferredOption(it) }
        if (preferredOption?.group != null) {
            player.selectQuality(preferredOption)
        }
    }

    LaunchedEffect(player, subtitleOptions, selectedSubtitleKey, subtitleSelectionTouched) {
        if (!subtitleSelectionTouched && selectedSubtitleKey == SUBTITLE_OFF_KEY) {
            val defaultOption = subtitleOptions.defaultSubtitleOption() ?: return@LaunchedEffect
            player.selectSubtitle(defaultOption)
            val stableKey = defaultOption.subtitleOptionIdentity()
            selectedSubtitleKey = stableKey
            playerView?.findViewById<TextView>(R.id.yummy_player_subtitles)
                ?.setTag(R.id.yummy_player_subtitles, stableKey)
            return@LaunchedEffect
        }
        if (selectedSubtitleKey == SUBTITLE_OFF_KEY) return@LaunchedEffect
        if (subtitleOptions.none { it.matchesSelectedSubtitleKey(selectedSubtitleKey) }) {
            selectedSubtitleKey = SUBTITLE_OFF_KEY
            player.disableSubtitles()
            playerView?.findViewById<TextView>(R.id.yummy_player_subtitles)
                ?.setTag(R.id.yummy_player_subtitles, SUBTITLE_OFF_KEY)
        }
    }

    LaunchedEffect(player, settings.playerSpeed) {
        player.setPlaybackSpeed(settings.playerSpeed.value)
    }

    LaunchedEffect(player) {
        while (player.playbackState != Player.STATE_READY && player.playbackState != Player.STATE_ENDED) {
            delay(24)
        }
        if (player.playbackState == Player.STATE_READY) {
            playerView?.hideController()
            player.play()
        }
    }

    LaunchedEffect(player, settings.matchDisplayModeToVideo, tracks) {
        activity?.applyVideoDisplayMode(
            enabled = settings.matchDisplayModeToVideo,
            video = player.currentVideoDisplayInfo(),
        )
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

    LaunchedEffect(player, currentVideo.id, stream.url, settings.playerBufferPreset, bufferResetSignal) {
        if (
            stream.url.startsWith("file:", ignoreCase = true) ||
            currentVideo.localPlaybackUrl.isNotBlank()
        ) {
            return@LaunchedEffect
        }

        var lastBufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
        var stagnantSinceMs: Long? = null
        var prepareRequested = false
        while (true) {
            delay(PLAYBACK_BUFFER_STALL_POLL_MS)
            val nowMs = SystemClock.elapsedRealtime()
            val positionMs = player.currentPosition.coerceAtLeast(0L)
            val durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            val bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
            val bufferAheadMs = (bufferedPositionMs - positionMs).coerceAtLeast(0L)
            val bufferIsGrowing = bufferedPositionMs > lastBufferedPositionMs + PLAYBACK_BUFFER_GROWTH_EPSILON_MS
            val nearPlaybackEnd = durationMs
                ?.minus(positionMs)
                ?.coerceAtLeast(0L)
                ?.let { remainingMs ->
                    remainingMs <= maxOf(
                        PLAYBACK_BUFFER_END_IGNORE_MS,
                        settings.playerBufferPreset.switchFallbackThresholdMs * 2,
                    )
                } == true
            val bufferedToEnd = durationMs
                ?.let { bufferedPositionMs >= it - PLAYBACK_BUFFER_END_EPSILON_MS } == true
            val canInspectBuffer = nowMs >= fallbackSuppressedUntilMs &&
                player.playbackState == Player.STATE_READY &&
                (player.isPlaying || player.playWhenReady) &&
                !nearPlaybackEnd &&
                !bufferedToEnd

            if (
                canInspectBuffer &&
                !bufferIsGrowing &&
                bufferAheadMs <= settings.playerBufferPreset.prepareFallbackThresholdMs
            ) {
                val stagnantFromMs = stagnantSinceMs ?: nowMs.also { stagnantSinceMs = it }
                val stagnantForMs = nowMs - stagnantFromMs
                if (!prepareRequested && stagnantForMs >= PLAYBACK_BUFFER_STALL_CONFIRM_MS) {
                    prepareRequested = true
                    latestPrepareFallbackSource(currentVideo)
                }
                if (
                    bufferAheadMs <= settings.playerBufferPreset.switchFallbackThresholdMs &&
                    stagnantForMs >= PLAYBACK_BUFFER_STALL_SWITCH_MS &&
                    latestSwitchToPreparedFallbackSource(currentVideo, positionMs)
                ) {
                    return@LaunchedEffect
                }
            } else {
                stagnantSinceMs = null
                if (
                    bufferIsGrowing ||
                    bufferAheadMs > settings.playerBufferPreset.prepareFallbackThresholdMs * 2
                ) {
                    prepareRequested = false
                }
            }
            lastBufferedPositionMs = maxOf(lastBufferedPositionMs, bufferedPositionMs)
        }
    }

    DisposableEffect(player) {
        var fallbackReported = false
        var autoAdvanceReported = false
        var playbackStartedReported = false
        var playbackEndedReported = false
        var bufferingFallbackJob: Job? = null
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
                selectedSubtitleKey = currentTracks.currentSubtitleKey() ?: SUBTITLE_OFF_KEY
                playerView?.findViewById<TextView>(R.id.yummy_player_subtitles)
                    ?.setTag(R.id.yummy_player_subtitles, selectedSubtitleKey)
                val resolvedSourceKey = latestStreamSelectedQualityKey
                if (resolvedSourceKey != null && latestQualityOptions.any { it.matchesSelectedQualityKey(resolvedSourceKey) }) {
                    selectedQualityKey = resolvedSourceKey
                    playerView?.findViewById<TextView>(R.id.yummy_player_quality)
                        ?.setTag(R.id.yummy_player_quality, resolvedSourceKey)
                    return
                }
                val explicitPreferredQuality = latestPlaybackPreferredQuality.takeUnless { it == PreferredQuality.Auto }
                    ?: currentSettings.defaultQuality.takeUnless { it == PreferredQuality.Auto }
                val preferredOption = explicitPreferredQuality
                    ?.let { latestQualityOptions.preferredOption(it) }
                if (preferredOption != null) {
                    val preferredKey = preferredOption.qualityOptionIdentity()
                    selectedQualityKey = preferredKey
                    playerView?.findViewById<TextView>(R.id.yummy_player_quality)
                        ?.setTag(R.id.yummy_player_quality, preferredKey)
                    return
                }
                val actualQualityKey = player.currentQualityKey()
                selectedQualityKey = currentTracks.videoQualityOptions()
                    .firstOrNull { it.matchesSelectedQualityKey(actualQualityKey) }
                    ?.qualityOptionIdentity()
                    ?: actualQualityKey?.qualityIdentityFromLabel()
                    ?: actualQualityKey
                playerView?.findViewById<TextView>(R.id.yummy_player_quality)
                    ?.setTag(R.id.yummy_player_quality, selectedQualityKey)
                activity?.applyVideoDisplayMode(
                    enabled = currentSettings.matchDisplayModeToVideo,
                    video = player.currentVideoDisplayInfo(),
                )
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                activity?.applyVideoDisplayMode(
                    enabled = currentSettings.matchDisplayModeToVideo,
                    video = player.currentVideoDisplayInfo() ?: videoSize.toVideoDisplayInfo(),
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING && playbackStartedReported && !fallbackReported) {
                    bufferingFallbackJob?.cancel()
                    bufferingFallbackJob = fallbackScope.launch {
                        val delayMs = maxOf(
                            PLAYBACK_BUFFERING_FALLBACK_DELAY_MS,
                            fallbackSuppressedUntilMs - SystemClock.elapsedRealtime(),
                        )
                        delay(delayMs.coerceAtLeast(0L))
                        if (
                            SystemClock.elapsedRealtime() >= fallbackSuppressedUntilMs &&
                            player.playbackState == Player.STATE_BUFFERING &&
                            !fallbackReported
                        ) {
                            fallbackReported = true
                            onPlaybackFailed(currentVideo, player.currentPosition.coerceAtLeast(0L))
                        }
                    }
                } else if (playbackState != Player.STATE_BUFFERING) {
                    bufferingFallbackJob?.cancel()
                    bufferingFallbackJob = null
                }

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
                        currentProgressCallback(next, 1_000L, 0L)
                        playerView?.hideController()
                        onPlayVideoAt(next, 0L)
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                fallbackSuppressedUntilMs = SystemClock.elapsedRealtime() + PLAYBACK_SEEK_BUFFER_GRACE_MS
                bufferResetSignal += 1
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
                    bufferingFallbackJob?.cancel()
                    bufferingFallbackJob = null
                    fallbackReported = true
                    onPlaybackFailed(currentVideo, player.currentPosition.coerceAtLeast(0L))
                }
            }
        }
        player.addListener(listener)
        onDispose {
            bufferingFallbackJob?.cancel()
            currentProgressCallback(
                currentProgressVideo,
                player.currentPosition.coerceAtLeast(0L),
                player.duration.normalizedDurationMs(),
            )
            player.removeListener(listener)
            PlayerPipController.unregisterPlayer(pipPlayerHandle)
            playerView?.clearTimelineScrubState()
            playerView?.unbindSkipControls()
            activity?.clearPreferredDisplayMode()
            player.release()
        }
    }

    key(
        configuration.orientation,
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        configuration.smallestScreenWidthDp,
    ) {
        AndroidView(
            factory = { viewContext ->
                val parent = FrameLayout(viewContext)
                LayoutInflater.from(viewContext).inflate(R.layout.yummy_player_view, parent, false) as PlayerView
            },
            update = { view ->
                playerView = view
                view.player = player
                view.controllerAutoShow = false
                view.setControllerAnimationEnabled(false)
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                view.installVideoZoomGestures(token = "${currentVideo.id}:${stream.url}")
                view.keepScreenOn = true
                view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                view.requestFocus()
                val previousPictureInPictureMode = view.tagValue<Boolean>(R.id.yummy_player_view)
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
                        subtitleOptions = subtitleOptions,
                        selectedSubtitleKey = selectedSubtitleKey,
                        onSelectedSubtitleKeyChange = {
                            subtitleSelectionTouched = true
                            selectedSubtitleKey = it
                        },
                        onSelectLocalQuality = { localFile ->
                            val positionMs = player.currentPosition.coerceAtLeast(0L)
                            player.pause()
                            onPlayVideoAt(currentVideo.withOfflineFile(localFile), positionMs)
                        },
                        onSelectPreferredQuality = { preferredQuality ->
                            val positionMs = player.currentPosition.coerceAtLeast(0L)
                            player.pause()
                            onPlayVideoAtQuality(currentVideo.withoutLocalPlayback(), positionMs, preferredQuality)
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
}

@OptIn(UnstableApi::class)
private fun PlayerView.installVideoZoomGestures(token: String) {
    val currentToken = tagValue<String>(R.id.yummy_video_zoom_token_tag)
    val currentState = tagValue<VideoZoomGestureState>(R.id.yummy_video_zoom_state_tag)
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
    tagValue<VideoZoomGestureState>(R.id.yummy_video_zoom_state_tag)?.let { state ->
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
private fun PlayerView.handleRemoteInputAction(event: InputActionEvent): Boolean {
    val action = event.action
    if (!useController) return false
    if (isSkipOnlyControllerMode()) {
        val skipButton = findViewById<View>(R.id.yummy_skip_skip)
        val watchButton = findViewById<View>(R.id.yummy_skip_watch)
        val timeBar = findViewById<View>(Media3R.id.exo_progress)
        if (action == InputAction.Confirm && skipButton?.hasFocus() == true) {
            skipButton.performClick()
            return true
        }
        if (action == InputAction.Confirm && watchButton?.hasFocus() == true) {
            watchButton.performClick()
            return true
        }
        val movedInsideSkipPrompt = when {
            action == InputAction.Right && skipButton?.hasFocus() == true -> watchButton?.requestFocus() == true
            action == InputAction.Left && watchButton?.hasFocus() == true -> skipButton.requestFocus()
            else -> false
        }
        cancelSkipAutoCountdown()
        if (movedInsideSkipPrompt) {
            return true
        }
        setSkipOnlyControllerMode(false)
        showController()
        post {
            val focused = when {
                action == InputAction.Down && (skipButton?.hasFocus() == true || watchButton?.hasFocus() == true) -> timeBar?.requestFocus() == true
                else -> findViewById<View>(Media3R.id.exo_play_pause)?.requestFocus() == true
            }
            if (!focused) requestFocus()
        }
        return true
    }
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
                seekTimelineIfFocused(
                    forward = action == InputAction.Right,
                    repeatedInput = event.isRepeated,
                )
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

private fun PlayerView.isSkipOnlyControllerMode(): Boolean {
    return tagValue<Boolean>(R.id.yummy_player_skip_only_mode) == true
}

@OptIn(UnstableApi::class)
private fun PlayerView.setSkipOnlyControllerMode(enabled: Boolean) {
    setTag(R.id.yummy_player_skip_only_mode, enabled)
    setControllerShowTimeoutMs(if (enabled) 0 else PLAYER_CONTROLS_AUTO_HIDE_MS.toInt())
    findViewById<View>(Media3R.id.exo_controls_background)?.visibility = if (enabled) View.GONE else View.VISIBLE
    findViewById<View>(R.id.yummy_player_top_bar)?.visibility = if (enabled) View.GONE else View.VISIBLE
    findViewById<View>(R.id.yummy_player_episode_controls)?.visibility = if (enabled) View.GONE else View.VISIBLE
    findViewById<View>(Media3R.id.exo_bottom_bar)?.visibility = if (enabled) View.GONE else View.VISIBLE
}

@OptIn(UnstableApi::class)
private fun PlayerView.seekTimelineIfFocused(
    forward: Boolean,
    repeatedInput: Boolean,
): Boolean {
    val timeBarView = findViewById<View>(Media3R.id.exo_progress) ?: return false
    if (!timeBarView.hasFocus()) return false

    val currentPlayer = player ?: return false
    val duration = currentPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: return false
    val now = SystemClock.uptimeMillis()
    val direction = if (forward) 1 else -1
    val state = tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)
        ?: TimelineScrubState(pendingPositionMs = currentPlayer.currentPosition.coerceIn(0L, duration))
    state.clearRunnable?.let(::removeCallbacks)
    state.clearRunnable = null

    val keepsHoldingSameDirection = repeatedInput && state.lastDirection == direction
    state.repeatedInputCount = if (keepsHoldingSameDirection) state.repeatedInputCount + 1 else 1
    state.lastDirection = direction
    state.lastInputAtMs = now
    state.generation += 1
    state.pendingPositionMs = (state.pendingPositionMs + direction.toLong() * state.stepMs(duration)).coerceIn(0L, duration)
    setTag(R.id.yummy_player_timeline_manual_until, now + PLAYER_TIMELINE_MANUAL_FREEZE_MS)

    state.commitRunnable?.let(::removeCallbacks)
    val commitGeneration = state.generation
    val commitRunnable = object : Runnable {
        override fun run() {
            val latestState = tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)
                ?: return
            if (latestState.generation != commitGeneration) return

            val elapsedSinceInputMs = SystemClock.uptimeMillis() - latestState.lastInputAtMs
            if (elapsedSinceInputMs < PLAYER_TIMELINE_SCRUB_COMMIT_DELAY_MS) {
                postDelayed(this, PLAYER_TIMELINE_SCRUB_COMMIT_DELAY_MS - elapsedSinceInputMs)
                return
            }

            val targetPositionMs = latestState.pendingPositionMs.coerceIn(0L, duration)
            currentPlayer.seekTo(targetPositionMs)
            latestState.pendingPositionMs = targetPositionMs
            latestState.repeatedInputCount = 0
            latestState.commitRunnable = null
            renderTimelineScrubPosition(latestState)
            val freezeUntil = SystemClock.uptimeMillis() + PLAYER_TIMELINE_MANUAL_FREEZE_MS
            setTag(R.id.yummy_player_timeline_manual_until, freezeUntil)
            val clearRunnable = object : Runnable {
                override fun run() {
                    val currentState = tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)
                    if (currentState !== latestState) return
                    if (isTimelineManuallyControlled()) {
                        postDelayed(this, 50L)
                        return
                    }
                    clearTimelineScrubState()
                }
            }
            latestState.clearRunnable = clearRunnable
            postDelayed(clearRunnable, PLAYER_TIMELINE_MANUAL_FREEZE_MS)
        }
    }
    state.commitRunnable = commitRunnable
    setTag(R.id.yummy_player_timeline_scrub_state, state)
    renderTimelineScrubPosition(state)
    post {
        val latestState = tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)
        if (latestState?.generation == commitGeneration) {
            renderTimelineScrubPosition(latestState)
        }
    }
    holdTimelineScrubPosition()
    postDelayed(commitRunnable, PLAYER_TIMELINE_SCRUB_COMMIT_DELAY_MS)
    return true
}

@OptIn(UnstableApi::class)
private fun PlayerView.renderTimelineScrubPosition(state: TimelineScrubState) {
    (findViewById<View>(Media3R.id.exo_progress) as? TimeBar)?.setPosition(state.pendingPositionMs)
    findViewById<TextView>(Media3R.id.exo_position)?.text = formatPlaybackTime(state.pendingPositionMs)
}

private fun PlayerView.isTimelineManuallyControlled(): Boolean {
    val until = tagValue<Long>(R.id.yummy_player_timeline_manual_until) ?: return false
    return SystemClock.uptimeMillis() < until
}

@OptIn(UnstableApi::class)
private fun PlayerView.holdTimelineScrubPosition() {
    if (tagValue<Runnable>(R.id.yummy_player_timeline_hold_runnable) != null) return
    val runnable = object : Runnable {
        override fun run() {
            val latestState = tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)
            if (latestState == null || !isTimelineManuallyControlled()) {
                clearTagValue(R.id.yummy_player_timeline_hold_runnable)
                return
            }
            renderTimelineScrubPosition(latestState)
            postOnAnimation(this)
        }
    }
    setTag(R.id.yummy_player_timeline_hold_runnable, runnable)
    postOnAnimation(runnable)
}

private fun PlayerView.clearTimelineScrubState() {
    tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)?.commitRunnable?.let(::removeCallbacks)
    tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)?.clearRunnable?.let(::removeCallbacks)
    removeTaggedRunnable(R.id.yummy_player_timeline_hold_runnable)
    clearTagValue(R.id.yummy_player_timeline_scrub_state)
    clearTagValue(R.id.yummy_player_timeline_manual_until)
}

private data class TimelineScrubState(
    var pendingPositionMs: Long,
    var repeatedInputCount: Int = 0,
    var lastDirection: Int = 0,
    var generation: Int = 0,
    var lastInputAtMs: Long = 0L,
    var commitRunnable: Runnable? = null,
    var clearRunnable: Runnable? = null,
) {
    fun stepMs(durationMs: Long): Long {
        val requestedStep = when {
            repeatedInputCount <= 3 -> PLAYER_TIMELINE_BASE_STEP_MS
            repeatedInputCount <= 7 -> 10_000L
            repeatedInputCount <= 13 -> 30_000L
            else -> 60_000L
        }
        val maxStep = (durationMs / PLAYER_TIMELINE_MAX_STEP_DIVISOR).coerceAtLeast(1_000L)
        return requestedStep.coerceAtMost(maxStep)
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
    subtitleOptions: List<SubtitleOption>,
    selectedSubtitleKey: String,
    onSelectedSubtitleKeyChange: (String) -> Unit,
    onSelectLocalQuality: (OfflineVideoFile) -> Unit,
    onSelectPreferredQuality: (PreferredQuality) -> Unit,
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
        currentVideo.playbackSubtitle(texts)
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
                texts = texts,
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
                onSelectPreferredQuality = onSelectPreferredQuality,
            )
        }
    }

    findViewById<TextView>(R.id.yummy_player_subtitles)?.apply {
        text = texts.subtitles
        visibility = if (subtitleOptions.isNotEmpty()) View.VISIBLE else View.GONE
        setPlayerControlEnabled(subtitleOptions.isNotEmpty())
        applyPlayerToggleState(selectedSubtitleKey != SUBTITLE_OFF_KEY && subtitleOptions.isNotEmpty())
        setOnClickListener {
            showController()
            showSubtitlePopup(
                anchor = this,
                player = player,
                options = subtitleOptions,
                selectedSubtitleKey = selectedSubtitleKey,
                texts = texts,
                onSelectedSubtitleKeyChange = onSelectedSubtitleKeyChange,
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
        text = context.getString(R.string.player_pip)
        visibility = if (canUsePictureInPicture) View.VISIBLE else View.GONE
        setOnClickListener {
            hideController()
            postDelayed({ onEnterPictureInPicture() }, PIP_ENTER_DELAY_MS)
        }
    }

    if (settings.skipOpeningsAndEndings) {
        bindSkipControls(player = player, currentVideo = currentVideo, texts = texts)
    } else {
        unbindSkipControls()
    }
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
        findViewById<View>(R.id.yummy_player_subtitles)?.takeIf { it.isVisible },
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
        setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) {
                return@setOnKeyListener false
            }
            if (event.action == KeyEvent.ACTION_DOWN) {
                seekTimelineIfFocused(
                    forward = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT,
                    repeatedInput = event.repeatCount > 0,
                )
            }
            true
        }
        applyPlayerTimelineFocusColors()
    }

    bottomControls.forEachIndexed { index, view ->
        view.nextFocusUpId = timeBar?.id ?: Media3R.id.exo_play_pause
        view.nextFocusDownId = view.id
        view.nextFocusLeftId = bottomControls.getOrNull(index - 1)?.id ?: view.id
        view.nextFocusRightId = bottomControls.getOrNull(index + 1)?.id ?: view.id
    }

    configureSkipFocusNavigation(findViewById<View>(R.id.yummy_skip_controls)?.isVisible == true)
}

@OptIn(UnstableApi::class)
private fun View.applyPlayerTimelineFocusColors() {
    val timeBar = this as? DefaultTimeBar ?: return
    timeBar.defaultFocusHighlightEnabled = false
    fun update(focused: Boolean) {
        val accent = if (focused) PLAYER_ACCENT_COLOR else android.graphics.Color.WHITE
        timeBar.setScrubberColor(accent)
        timeBar.setPlayedColor(accent)
    }
    update(hasFocus())
    setOnFocusChangeListener { _, focused -> update(focused) }
}

private fun PlayerView.configureSkipFocusNavigation(active: Boolean) {
    val timeBar = findViewById<View>(Media3R.id.exo_progress)
    val skipButton = findViewById<View>(R.id.yummy_skip_skip)
    val watchButton = findViewById<View>(R.id.yummy_skip_watch)
    if (active && skipButton != null && watchButton != null) {
        timeBar?.nextFocusUpId = R.id.yummy_skip_skip
        skipButton.nextFocusLeftId = R.id.yummy_skip_skip
        skipButton.nextFocusRightId = R.id.yummy_skip_watch
        skipButton.nextFocusUpId = Media3R.id.exo_play_pause
        skipButton.nextFocusDownId = timeBar?.id ?: R.id.yummy_skip_skip
        watchButton.nextFocusLeftId = R.id.yummy_skip_skip
        watchButton.nextFocusRightId = R.id.yummy_skip_watch
        watchButton.nextFocusUpId = Media3R.id.exo_play_pause
        watchButton.nextFocusDownId = timeBar?.id ?: R.id.yummy_skip_watch
    } else if (timeBar?.nextFocusUpId == R.id.yummy_skip_skip) {
        timeBar.nextFocusUpId = Media3R.id.exo_play_pause
    }
}

private fun TextView.applyPlayerSubscriptionState(active: Boolean) {
    applyPlayerToggleState(active)
}

private fun TextView.applyPlayerToggleState(active: Boolean) {
    backgroundTintList = null
    setBackgroundResource(if (active) R.drawable.player_control_chip_active else R.drawable.player_control_chip)
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
    if (tagValue<Long>(R.id.yummy_player_skip_video_id) != currentVideo.id) {
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
    removeTaggedRunnable(R.id.yummy_player_skip_poll_runnable)

    fun dismissedKeys(): MutableSet<String> {
        @Suppress("UNCHECKED_CAST")
        return tagValue<MutableSet<String>>(R.id.yummy_player_skip_dismissed_keys)
            ?: mutableSetOf<String>().also { setTag(R.id.yummy_player_skip_dismissed_keys, it) }
    }

    fun clearActivePrompt() {
        removeTaggedRunnable(R.id.yummy_player_skip_countdown_runnable)
        clearTagValue(R.id.yummy_player_active_skip_key)
        clearTagValue(R.id.yummy_player_active_skip_segment)
        clearTagValue(R.id.yummy_player_skip_auto_cancelled)
        container.visibility = View.GONE
        configureSkipFocusNavigation(active = false)
        if (isSkipOnlyControllerMode()) {
            setSkipOnlyControllerMode(false)
            hideController()
        }
    }

    fun dismissActivePrompt() {
        val prompt = tagValue<ActiveSkipPrompt>(R.id.yummy_player_active_skip_segment)
        if (prompt != null) {
            dismissedKeys().add(prompt.key)
        }
        clearActivePrompt()
    }

    fun skipActivePrompt() {
        val prompt = tagValue<ActiveSkipPrompt>(R.id.yummy_player_active_skip_segment) ?: return
        dismissedKeys().add(prompt.key)
        clearActivePrompt()
        if (player.currentPosition.coerceAtLeast(0L) < prompt.segment.endMs) {
            player.seekTo(prompt.segment.endMs)
        }
    }

    fun updateSkipButtonText(state: SkipCountdownState, nowMs: Long = SystemClock.elapsedRealtime()) {
        val remainingSeconds = (((state.deadlineMs - nowMs).coerceAtLeast(0L) + 999L) / 1_000L)
            .toInt()
            .coerceIn(0, SKIP_PROMPT_COUNTDOWN_SECONDS)
        skipButton.text = if (state.autoSkipEnabled) {
            context.getString(R.string.player_skip_countdown, texts.skip, remainingSeconds)
        } else {
            texts.skip
        }
    }

    fun scheduleCountdown(prompt: ActiveSkipPrompt) {
        val startedAtMs = SystemClock.elapsedRealtime()
        val state = SkipCountdownState(
            startedAtMs = startedAtMs,
            deadlineMs = startedAtMs + SKIP_PROMPT_COUNTDOWN_SECONDS * 1_000L,
            autoSkipEnabled = true,
        )
        setTag(R.id.yummy_player_skip_auto_cancelled, state)
        updateSkipButtonText(state)

        fun tick() {
            val activeKey = tagValue<String>(R.id.yummy_player_active_skip_key)
            if (activeKey != prompt.key || !state.autoSkipEnabled) return
            val nowMs = SystemClock.elapsedRealtime()
            val remainingMs = state.deadlineMs - nowMs
            if (remainingMs <= 0L) {
                updateSkipButtonText(state, state.deadlineMs)
                val finishCountdown = Runnable {
                    val currentKey = tagValue<String>(R.id.yummy_player_active_skip_key)
                    if (currentKey == prompt.key && state.autoSkipEnabled) {
                        skipActivePrompt()
                    }
                }
                setTag(R.id.yummy_player_skip_countdown_runnable, finishCountdown)
                postDelayed(finishCountdown, SKIP_PROMPT_ZERO_DISPLAY_MS)
            } else {
                updateSkipButtonText(state, nowMs)
                val nextTick = Runnable { tick() }
                setTag(R.id.yummy_player_skip_countdown_runnable, nextTick)
                val elapsedMs = (nowMs - state.startedAtMs).coerceAtLeast(0L)
                val nextSecondMs = ((elapsedMs / 1_000L) + 1L) * 1_000L
                val delayMs = (nextSecondMs - elapsedMs).coerceIn(16L, remainingMs)
                postDelayed(nextTick, delayMs)
            }
        }

        val firstTick = Runnable { tick() }
        setTag(R.id.yummy_player_skip_countdown_runnable, firstTick)
        postDelayed(firstTick, 1_000L)
    }

    fun showPrompt(segment: VideoSkipSegment) {
        val key = segment.key
        if (tagValue<String>(R.id.yummy_player_active_skip_key) == key) return
        val prompt = ActiveSkipPrompt(key = key, segment = segment)
        setTag(R.id.yummy_player_active_skip_key, key)
        setTag(R.id.yummy_player_active_skip_segment, prompt)
        container.visibility = View.VISIBLE
        showController()
        setSkipOnlyControllerMode(true)
        skipButton.setOnClickListener { skipActivePrompt() }
        watchButton.setOnClickListener { dismissActivePrompt() }
        configureSkipFocusNavigation(active = true)
        scheduleCountdown(prompt)
        post { skipButton.requestFocus() }
    }

    val pollRunnable = object : Runnable {
        override fun run() {
            val position = player.currentPosition.coerceAtLeast(0L)
            val activePrompt = tagValue<ActiveSkipPrompt>(R.id.yummy_player_active_skip_segment)
            val countdownState = tagValue<SkipCountdownState>(R.id.yummy_player_skip_auto_cancelled)
            if (
                activePrompt != null &&
                countdownState?.autoSkipEnabled != true &&
                !activePrompt.segment.isActive(position)
            ) {
                dismissedKeys().add(activePrompt.key)
                clearActivePrompt()
            }
            if (container.visibility != View.VISIBLE) {
                val segment = currentVideo.skipSegments.firstOrNull { segment ->
                    segment.key !in dismissedKeys() &&
                        position >= segment.startMs &&
                        segment.isActive(position)
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
    val state = tagValue<SkipCountdownState>(R.id.yummy_player_skip_auto_cancelled) ?: return
    if (!state.autoSkipEnabled) return
    state.autoSkipEnabled = false
    val skipText = tagValue<String>(R.id.yummy_player_skip_text_tag) ?: defaultPlayerControlTexts.skip
    findViewById<TextView>(R.id.yummy_skip_skip)?.text = skipText
    removeTaggedRunnable(R.id.yummy_player_skip_countdown_runnable)
}

private fun PlayerView.unbindSkipControls() {
    removeTaggedRunnable(R.id.yummy_player_skip_poll_runnable)
    removeTaggedRunnable(R.id.yummy_player_skip_countdown_runnable)
    clearTagValue(R.id.yummy_player_active_skip_key)
    clearTagValue(R.id.yummy_player_active_skip_segment)
    clearTagValue(R.id.yummy_player_skip_auto_cancelled)
    setSkipOnlyControllerMode(false)
    findViewById<View>(R.id.yummy_skip_controls)?.visibility = View.GONE
    configureSkipFocusNavigation(active = false)
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
    texts: PlayerControlTexts,
    onSelectGroup: (String, VideoVariant?) -> Unit,
) {
    val entries = groups.entries.sortedBy { it.value.firstOrNull()?.matchingVoiceTitle.orEmpty() }
    val totalEpisodeCount = groups.values
        .flatten()
        .map { it.matchingEpisodeKey }
        .distinct()
        .size
        .coerceAtLeast(1)
    PopupMenu(anchor.context, anchor).apply {
        entries.forEachIndexed { index, entry ->
            val voiceTitle = entry.value.firstOrNull()?.matchingVoiceTitle.orEmpty().ifBlank { "${texts.voice} ${index + 1}" }
            val availableEpisodes = entry.value.map { it.matchingEpisodeKey }.distinct().size
            val downloadedEpisodes = entry.value
                .asSequence()
                .filter { it.isOfflineAvailable }
                .map { it.matchingEpisodeKey }
                .distinct()
                .count()
            val downloadedSuffix = if (downloadedEpisodes > 0) " • ${texts.downloaded}: $downloadedEpisodes" else ""
            val title = "$voiceTitle  $availableEpisodes / $totalEpisodeCount$downloadedSuffix"
            menu.add(VOICE_MENU_GROUP_ID, index, index, title).apply {
                isCheckable = true
                isChecked = entry.key == selectedKey
            }
        }
        menu.setGroupCheckable(VOICE_MENU_GROUP_ID, true, true)
        setOnMenuItemClickListener { item ->
            val entry = entries.getOrNull(item.itemId) ?: return@setOnMenuItemClickListener false
            val sortedVideos = entry.value.sortedForPlayer(preferredGroupKey)
            val replacement = sortedVideos.firstOrNull { it.isSameEpisodeAs(currentVideo) }
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
    onSelectPreferredQuality: (PreferredQuality) -> Unit,
) {
    PopupMenu(anchor.context, anchor).apply {
        val effectiveSelectedQualityKey = anchor.tagValue<String>(R.id.yummy_player_quality)
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
            } ?: option.preferredQuality?.let { preferredQuality ->
                anchor.post { onSelectPreferredQuality(preferredQuality) }
            } ?: player.selectQuality(option)
            val stableKey = option.qualityOptionIdentity()
            anchor.setTag(R.id.yummy_player_quality, stableKey)
            onSelectedQualityKeyChange(stableKey)
            true
        }
        show()
    }
}

@OptIn(UnstableApi::class)
private fun showSubtitlePopup(
    anchor: View,
    player: ExoPlayer,
    options: List<SubtitleOption>,
    selectedSubtitleKey: String,
    texts: PlayerControlTexts,
    onSelectedSubtitleKeyChange: (String) -> Unit,
) {
    PopupMenu(anchor.context, anchor).apply {
        val effectiveSelectedSubtitleKey = anchor.tagValue<String>(R.id.yummy_player_subtitles)
            ?: selectedSubtitleKey
        menu.add(SUBTITLE_MENU_GROUP_ID, 0, 0, texts.subtitlesOff).apply {
            isCheckable = true
            isChecked = effectiveSelectedSubtitleKey == SUBTITLE_OFF_KEY
        }
        options.forEachIndexed { index, option ->
            menu.add(SUBTITLE_MENU_GROUP_ID, index + 1, index + 1, option.label).apply {
                isCheckable = true
                isChecked = option.matchesSelectedSubtitleKey(effectiveSelectedSubtitleKey)
            }
        }
        menu.setGroupCheckable(SUBTITLE_MENU_GROUP_ID, true, true)
        setOnMenuItemClickListener { item ->
            if (item.itemId == 0) {
                player.disableSubtitles()
                anchor.setTag(R.id.yummy_player_subtitles, SUBTITLE_OFF_KEY)
                onSelectedSubtitleKeyChange(SUBTITLE_OFF_KEY)
                return@setOnMenuItemClickListener true
            }
            val option = options.getOrNull(item.itemId - 1) ?: return@setOnMenuItemClickListener false
            player.selectSubtitle(option)
            val stableKey = option.subtitleOptionIdentity()
            anchor.setTag(R.id.yummy_player_subtitles, stableKey)
            onSelectedSubtitleKeyChange(stableKey)
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

private data class VideoDisplayInfo(
    val width: Int,
    val height: Int,
    val frameRate: Float,
)

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private fun Context.supportsDisplayModeMatching(): Boolean {
    val uiModeManager = getSystemService(UiModeManager::class.java)
    val isTelevision = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    if (isTelevision) return true

    val displayManager = getSystemService(DisplayManager::class.java)
    return displayManager
        ?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        ?.isNotEmpty() == true
}

private fun Activity.applyVideoDisplayMode(enabled: Boolean, video: VideoDisplayInfo?) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || !supportsDisplayModeMatching()) return
    if (!enabled || video == null || video.width <= 0 || video.height <= 0) {
        clearPreferredDisplayMode()
        return
    }

    @Suppress("DEPRECATION")
    val display = windowManager.defaultDisplay ?: return
    val targetMode = display.supportedModes
        .filter { mode -> mode.physicalWidth > 0 && mode.physicalHeight > 0 }
        .minByOrNull { mode -> mode.displayModeScore(video) }

    val targetModeId = targetMode?.modeId ?: 0
    if (window.attributes.preferredDisplayModeId == targetModeId) return
    window.attributes = window.attributes.apply {
        preferredDisplayModeId = targetModeId
    }
}

private fun Activity.clearPreferredDisplayMode() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    if (window.attributes.preferredDisplayModeId == 0) return
    window.attributes = window.attributes.apply {
        preferredDisplayModeId = 0
    }
}

private fun android.view.Display.Mode.displayModeScore(video: VideoDisplayInfo): Float {
    val modeLongSide = maxOf(physicalWidth, physicalHeight)
    val modeShortSide = minOf(physicalWidth, physicalHeight)
    val videoLongSide = maxOf(video.width, video.height)
    val videoShortSide = minOf(video.width, video.height)
    val resolutionPenalty = when {
        modeLongSide >= videoLongSide && modeShortSide >= videoShortSide ->
            (modeLongSide - videoLongSide) + (modeShortSide - videoShortSide)
        else ->
            100_000 + abs(modeLongSide - videoLongSide) + abs(modeShortSide - videoShortSide)
    }
    return resolutionPenalty + refreshRatePenalty(refreshRate, video.frameRate)
}

private fun refreshRatePenalty(refreshRate: Float, frameRate: Float): Float {
    if (refreshRate <= 0f || frameRate <= 0f) return 0f
    val candidates = listOf(frameRate, frameRate * 2f, frameRate * 3f, frameRate / 2f)
    return candidates.minOf { abs(refreshRate - it) } * 100f
}

@OptIn(UnstableApi::class)
private fun Player.currentVideoDisplayInfo(): VideoDisplayInfo? {
    (this as? ExoPlayer)?.videoFormat
        ?.takeIf { format -> format.width > 0 || format.height > 0 }
        ?.let { format ->
            return VideoDisplayInfo(
                width = format.width,
                height = format.height,
                frameRate = format.frameRate,
            )
        }

    return currentTracks.groups
        .asSequence()
        .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
        .flatMap { group ->
            (0 until group.length)
                .asSequence()
                .filter { trackIndex -> group.isTrackSelected(trackIndex) }
                .map { trackIndex -> group.getTrackFormat(trackIndex) }
        }
        .firstOrNull { format -> format.width > 0 || format.height > 0 }
        ?.let { format ->
            VideoDisplayInfo(
                width = format.width,
                height = format.height,
                frameRate = format.frameRate,
            )
        }
}

private fun VideoSize.toVideoDisplayInfo(): VideoDisplayInfo? {
    if (width <= 0 || height <= 0) return null
    return VideoDisplayInfo(width = width, height = height, frameRate = 0f)
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

@OptIn(UnstableApi::class)
private fun ExoPlayer.selectSubtitle(option: SubtitleOption) {
    trackSelectionParameters = trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .addOverride(TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndex))
        .build()
}

@OptIn(UnstableApi::class)
private fun ExoPlayer.disableSubtitles() {
    trackSelectionParameters = trackSelectionParameters
        .buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        .build()
}

private fun List<QualityOption>.preferredOption(preferredQuality: PreferredQuality): QualityOption? {
    val preferredHeight = preferredQuality.height ?: return null
    return minWithOrNull(
        compareBy<QualityOption> { option ->
            when {
                option.height <= 0 -> 2
                option.height <= preferredHeight -> 0
                else -> 1
            }
        }.thenBy { option ->
            when {
                option.height <= 0 -> Int.MAX_VALUE
                option.height <= preferredHeight -> preferredHeight - option.height
                else -> option.height - preferredHeight
            }
        }.thenByDescending { it.bitrate },
    )
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
private fun PlayerBufferPreset.toLoadControl(): DefaultLoadControl {
    return DefaultLoadControl.Builder()
        .setBufferDurationsMs(minBufferMs, maxBufferMs, playbackBufferMs, rebufferMs)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()
}

@OptIn(UnstableApi::class)
private fun PlayerBufferPreset.toRecoveryPrebufferLoadControl(): DefaultLoadControl {
    val targetBufferMs = recoveryPrebufferTargetMs().toInt()
    val resolvedMinBufferMs = maxOf(minBufferMs, targetBufferMs)
    val resolvedMaxBufferMs = maxOf(maxBufferMs, resolvedMinBufferMs)
    return DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            resolvedMinBufferMs,
            resolvedMaxBufferMs,
            targetBufferMs,
            maxOf(rebufferMs, targetBufferMs),
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()
}

private fun PlayerBufferPreset.recoveryPrebufferTargetMs(): Long {
    return maxOf(PLAYBACK_RECOVERY_PREBUFFER_MIN_MS, switchFallbackThresholdMs)
}

@OptIn(UnstableApi::class)
private fun Player.currentQualityKey(): String? {
    (this as? ExoPlayer)?.videoFormat
        ?.takeIf { format -> format.width > 0 || format.height > 0 }
        ?.let { format ->
            return "${format.height}:${format.bitrate}:${format.qualityLabel()}"
        }

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
    val preferredQuality: PreferredQuality? = null,
)

private data class SubtitleOption(
    val group: Tracks.Group,
    val trackIndex: Int,
    val label: String,
    val language: String?,
    val selectionFlags: Int,
    val key: String,
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
                        preferredQuality = PreferredQuality.fromHeight(format.height),
                    )
                }
        }
        .sortedWith(
            compareByDescending<QualityOption> { it.height.takeIf { height -> height > 0 } ?: 0 }
                .thenByDescending { it.bitrate.takeIf { bitrate -> bitrate > 0 } ?: 0 }
                .thenBy { it.label },
        )
        .distinctBy { it.qualityOptionIdentity() }
}

@OptIn(UnstableApi::class)
private fun Tracks.subtitleOptions(texts: PlayerControlTexts): List<SubtitleOption> {
    return groups
        .filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
        .flatMap { group ->
            (0 until group.length)
                .filter { trackIndex -> group.isTrackSupported(trackIndex) }
                .map { trackIndex ->
                    val format = group.getTrackFormat(trackIndex)
                    SubtitleOption(
                        group = group,
                        trackIndex = trackIndex,
                        label = format.subtitleLabel(texts, trackIndex),
                        language = format.language,
                        selectionFlags = format.selectionFlags,
                        key = "${format.id.orEmpty()}:${format.language.orEmpty()}:${format.label.orEmpty()}:$trackIndex",
                    )
                }
        }
        .distinctBy { it.subtitleOptionIdentity() }
}

private fun List<SubtitleOption>.defaultSubtitleOption(): SubtitleOption? {
    return firstOrNull { option -> (option.selectionFlags and C.SELECTION_FLAG_DEFAULT) != 0 }
        ?: firstOrNull()
}

@OptIn(UnstableApi::class)
private fun Tracks.currentSubtitleKey(): String? {
    return groups
        .asSequence()
        .filter { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
        .flatMap { group ->
            (0 until group.length)
                .asSequence()
                .filter { trackIndex -> group.isTrackSelected(trackIndex) }
                .map { trackIndex ->
                    val format = group.getTrackFormat(trackIndex)
                    "${format.id.orEmpty()}:${format.language.orEmpty()}:${format.label.orEmpty()}:$trackIndex"
                }
        }
        .firstOrNull()
}

@OptIn(UnstableApi::class)
private fun androidx.media3.common.Format.subtitleLabel(
    texts: PlayerControlTexts,
    trackIndex: Int,
): String {
    val explicitLabel = label?.takeIf { it.isNotBlank() }
    val languageLabel = language
        ?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }
        ?.let { languageTag ->
            runCatching { Locale.forLanguageTag(languageTag).getDisplayLanguage(Locale.getDefault()) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        }
    return explicitLabel
        ?: languageLabel
        ?: "${texts.subtitles} ${trackIndex + 1}"
}

@OptIn(UnstableApi::class)
private fun androidx.media3.common.Format.qualityLabel(): String {
    return when {
        height > 0 -> "${height}p"
        width > 0 -> "${width}px"
        else -> "Видео"
    }
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
private fun InlineErrorMessage(
    message: String,
    modifier: Modifier = Modifier,
) {
    if (message.isBlank()) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.34f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = YummyRadii.smallShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = YummySpacing.sm, vertical = YummySpacing.sm),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                overflow = TextOverflow.Visible,
            )
        }
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
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
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
        shape = YummyRadii.smallShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = YummySpacing.sm, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.xs),
        ) {
            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(YummySizes.badgeIcon))
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
        shape = YummyRadii.smallShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = YummyAlpha.badgeSurface),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = YummySpacing.sm, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.xs),
        ) {
            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(YummySizes.badgeIcon))
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
