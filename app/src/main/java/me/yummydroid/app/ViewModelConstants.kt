package me.yummydroid.app

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.AnimeComment
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AnimeRatingSummary
import me.yummydroid.app.data.AnimeRatingStateStorage
import me.yummydroid.app.data.AnimeSort
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.data.AppUpdateInfo
import me.yummydroid.app.data.AnimeTrailer
import me.yummydroid.app.data.AuthStorage
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.CaptchaRequiredException
import me.yummydroid.app.data.DEFAULT_SITE_BASE_URL
import me.yummydroid.app.data.FilterCatalog
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.GitHubUpdateChecker
import me.yummydroid.app.data.HistoryAnimeCacheStorage
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PlaybackProgressStorage
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedPlayback
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.SiteDomainResolver
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.UserProfile
import me.yummydroid.app.data.ScheduleAnime
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoSubscriptionHint
import me.yummydroid.app.data.VideoSubscriptionHintStorage
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.YummyAnimeRepository
import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.episodeOrderValue
import me.yummydroid.app.data.hasSubscriptionForVoice
import me.yummydroid.app.data.hasSameVoiceAs
import me.yummydroid.app.data.isNewerThanVersion
import me.yummydroid.app.data.isSameEpisodeAs
import me.yummydroid.app.data.isFullyReleased
import me.yummydroid.app.data.matchingDubbingKey
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingDubbingTitle
import me.yummydroid.app.data.matchingSourceKey
import me.yummydroid.app.data.matchingPlayerKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.matchingVoiceTitle
import me.yummydroid.app.data.matchesAnimeVoice
import me.yummydroid.app.data.matchesVideoPlayer
import me.yummydroid.app.data.isUnauthorizedApiError
import me.yummydroid.app.data.normalized
import me.yummydroid.app.data.progressSyncKey
import me.yummydroid.app.data.withVoiceSubscriptionState

internal const val MAX_NAVIGATION_STACK = 40
internal const val AUTH_REQUIRED_ERROR_KEY = "auth_required"
internal const val SUBSCRIPTION_ENABLE_FAILED_KEY = "subscription_enable_failed"
internal const val SUBSCRIPTION_DISABLE_FAILED_KEY = "subscription_disable_failed"
internal const val SUBSCRIPTION_TARGET_NOT_FOUND_KEY = "subscription_target_not_found"
internal const val WATCH_HISTORY_MAX_OFFSET = 100_000
internal const val PLAYBACK_SOURCE_RECOVERY_INTERVAL_MS = 45_000L
internal const val PLAYBACK_SOURCE_RECOVERY_MIN_HEIGHT_GAIN = 120
internal const val PLAYBACK_FAILED_SOURCE_RETRY_COOLDOWN_MS = 5L * 60L * 1000L
internal const val PLAYBACK_STANDBY_TTL_MS = 2L * 60L * 1000L
internal const val BROWSE_REMOTE_REFRESH_INTERVAL_MS = 60_000L
