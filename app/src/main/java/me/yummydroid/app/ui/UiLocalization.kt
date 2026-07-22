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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.cancelAndJoin
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
import me.yummydroid.app.AppBackAction
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.BuildConfig
import me.yummydroid.app.canHandleRootHomeBackToTop
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
import me.yummydroid.app.resolveAppBackAction
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
import me.yummydroid.app.data.selectForPreferredQuality
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
internal fun uiText(ru: String): String {
    val language = LocalUiLanguage.current
    return remember(language, ru) { translateUiText(ru, language) }
}
internal fun translateUiText(ru: String, language: ContentLanguage): String {
    if (language == ContentLanguage.Russian) return ru
    val dictionary = when (language) {
        ContentLanguage.English -> englishUiDictionary
        ContentLanguage.Ukrainian -> ukrainianUiDictionary
        ContentLanguage.Russian -> emptyMap()
    }
    return dictionary[ru] ?: ru
}

@Composable
internal fun localizedPluralWord(
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
internal fun localizedEpisodesWord(count: Int): String {
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
internal fun localizedVotesWord(count: Long): String {
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
internal fun BrowseSection.localizedTitle(): String = uiText(
    when (this) {
        BrowseSection.Catalog -> UiStringKey.BrowseCatalog
        BrowseSection.Schedule -> UiStringKey.BrowseSchedule
        BrowseSection.History -> UiStringKey.BrowseHistory
        BrowseSection.Downloads -> UiStringKey.BrowseDownloads
    },
)

internal fun visibleBrowseSections(isAuthorized: Boolean): List<BrowseSection> {
    return if (isAuthorized) {
        listOf(BrowseSection.Catalog, BrowseSection.Schedule, BrowseSection.History)
    } else {
        listOf(BrowseSection.Catalog, BrowseSection.Schedule)
    }
}

@Composable
internal fun PreferredQuality.localizedTitle(): String = when (this) {
    PreferredQuality.Auto -> uiText("Авто")
    else -> title
}

@Composable
internal fun PlayerDecoderMode.localizedTitle(): String = when (this) {
    PlayerDecoderMode.Auto -> uiText("Авто")
    PlayerDecoderMode.Hardware -> uiText("Аппаратный")
    PlayerDecoderMode.Software -> uiText("Программный")
}

@Composable
internal fun PlayerBufferPreset.localizedTitle(): String = uiText(title)

@Composable
internal fun PosterCardSize.localizedTitle(): String = when (this) {
    PosterCardSize.Compact -> uiText("Компактные")
    PosterCardSize.Standard -> uiText("Стандартные")
    PosterCardSize.Large -> uiText("Крупные")
}

internal fun PosterCardSize.resolveCatalogColumns(screenWidthDp: Int): Int {
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
internal fun ContentLanguage.localizedTitle(): String = when (this) {
    ContentLanguage.Russian -> "Русский"
    ContentLanguage.English -> "English"
    ContentLanguage.Ukrainian -> "Українська"
}

@Composable
internal fun AnimeSort.localizedTitle(): String = uiText(
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
internal fun FilterOption.localizedTitle(): String = when (value) {
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

internal fun ContentLanguage.voiceRecognizerTag(): String = when (this) {
    ContentLanguage.Russian -> "ru-RU"
    ContentLanguage.English -> "en-US"
    ContentLanguage.Ukrainian -> "uk-UA"
}

internal val englishUiDictionary = mapOf(
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

internal val ukrainianUiDictionary = mapOf(
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
