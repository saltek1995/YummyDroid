package me.yummydroid.app

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.graphics.Rect
import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Rational
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.ui.theme.YummyDroidTheme
import me.yummydroid.app.ui.YummyDroidApp

class MainActivity : ComponentActivity() {
    private var inputActionHandler: ((InputActionEvent) -> Boolean)? = null
    private var lastMotionNavigationAt = 0L
    private var isPlayerRoute = false
    private var isPlayerPictureInPicture by mutableStateOf(false)
    private var pendingSystemSearchQuery by mutableStateOf<String?>(null)
    private val pipPlaybackStateListener: (Boolean) -> Unit = {
        updatePictureInPictureParams()
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode != KeyEvent.KEYCODE_BACK) {
            val action = event.toInputAction()
            if (action != null && inputActionHandler?.invoke(InputActionEvent(action, event.repeatCount)) == true) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val action = event.toInputAction()
        if (action != null && inputActionHandler?.invoke(InputActionEvent(action)) == true) {
            return true
        }

        return super.dispatchGenericMotionEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindowForCutouts()
        requestNotificationPermissionIfNeeded()
        DownloadCenter.initialize(applicationContext)
        PlayerPipController.addPlaybackStateListener(pipPlaybackStateListener)
        window.decorView.isFocusable = true
        window.decorView.isFocusableInTouchMode = true
        window.decorView.requestFocus()
        pendingSystemSearchQuery = intent.searchQueryExtra()

        setContent {
            val viewModel: YummyDroidViewModel = viewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val initialAnimeId = intent.extras.animeIdExtra()
            val initialVideo = intent.extras.videoExtra()
            val initialAnimeTitle = intent.extras.animeTitleExtra()
            val systemSearchQuery = pendingSystemSearchQuery

            LaunchedEffect(initialAnimeId, initialVideo) {
                if (initialVideo != null) {
                    viewModel.playVideo(initialVideo, initialAnimeTitle)
                } else if (initialAnimeId > 0L) {
                    viewModel.openAnime(initialAnimeId)
                }
            }

            LaunchedEffect(systemSearchQuery) {
                val query = systemSearchQuery?.trim().orEmpty()
                if (query.isNotBlank()) {
                    viewModel.selectBrowseSection(BrowseSection.Catalog)
                    viewModel.updateSearchQuery(query)
                    pendingSystemSearchQuery = null
                }
            }

            LaunchedEffect(state.route) {
                val playerRoute = state.route is AppRoute.Player
                isPlayerRoute = playerRoute
                setPlayerFullscreen(playerRoute)
                if (playerRoute) {
                    updatePictureInPictureParams()
                } else {
                    PlayerPipController.setPictureInPictureMode(false)
                }
            }

            LaunchedEffect(state.auth.profile?.id, state.settings.notificationsEnabled) {
                SubscriptionNotificationScheduler.configure(
                    context = this@MainActivity,
                    enabled = state.settings.notificationsEnabled && state.auth.profile != null,
                )
            }

            YummyDroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    YummyDroidApp(
                        state = state,
                        isInPictureInPicture = isPlayerPictureInPicture,
                        onQueryChange = viewModel::updateSearchQuery,
                        onRefresh = viewModel::refresh,
                        onLoadMoreAnime = viewModel::loadMoreAnime,
                        onBrowseSectionChange = viewModel::selectBrowseSection,
                        onFiltersChange = viewModel::updateFilters,
                        onResetFilters = viewModel::resetFilters,
                        onSettingsChange = viewModel::updateSettings,
                        onOpenAnime = viewModel::openAnime,
                        onFilterByGenre = viewModel::filterByGenre,
                        onFilterByYear = viewModel::filterByYear,
                        onFilterByStudio = viewModel::filterByStudio,
                        onFilterByCreator = viewModel::filterByCreator,
                        onSelectVideoGroup = viewModel::selectVideoGroup,
                        onPlayVideo = viewModel::playVideo,
                        onPlayVideoAt = viewModel::playVideoAt,
                        onPlayVideoAtQuality = viewModel::playVideoAtQuality,
                        onRetryVideo = viewModel::retryVideo,
                        onPlaybackFailed = viewModel::fallbackPlaybackSource,
                        onPrepareFallbackSource = viewModel::prepareFallbackPlaybackSource,
                        onSwitchToPreparedFallbackSource = viewModel::switchToPreparedFallbackSource,
                        onRecoveryPrebufferReady = viewModel::switchToBufferedRecoverySource,
                        onRecoveryPrebufferFailed = viewModel::discardBufferedRecoverySource,
                        onPlaybackStarted = viewModel::confirmPlaybackSource,
                        onPlaybackEnded = viewModel::handlePlaybackEnded,
                        onPlaybackProgress = viewModel::savePlaybackProgress,
                        onResetAnimeWatchProgress = viewModel::resetAnimeWatchProgress,
                        canUsePictureInPicture = supportsPlayerPictureInPicture(),
                        onEnterPictureInPicture = ::enterPlayerPictureInPicture,
                        onLogin = viewModel::login,
                        onCaptchaSolved = viewModel::submitCaptchaResponse,
                        onCaptchaCanceled = viewModel::cancelCaptchaChallenge,
                        onLogout = viewModel::logout,
                        onOpenLibraryFilter = viewModel::openLibraryFilter,
                        onSelectAnimeListMark = viewModel::selectAnimeListMark,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onSetAnimeRating = viewModel::setAnimeRating,
                        onAddAnimeComment = viewModel::addAnimeComment,
                        onLoadMoreAnimeComments = viewModel::loadMoreAnimeComments,
                        onToggleVideoSubscription = viewModel::toggleVideoSubscription,
                        onUnsubscribeVideoSubscription = viewModel::unsubscribeVideoSubscription,
                        onRefreshVideoSubscriptions = viewModel::refreshVideoSubscriptions,
                        onResolveDownloadQualities = viewModel::resolveAvailableDownloadQualities,
                        onDownloadVideo = viewModel::downloadVideoForOffline,
                        onDownloadAllVideos = viewModel::downloadAllVideosForOffline,
                        onDeleteOfflineVideo = viewModel::deleteOfflineVideo,
                        onDeleteOfflineAnime = viewModel::deleteOfflineAnime,
                        onClearAppContentCache = viewModel::clearAppContentCache,
                        onClearDownloadHistory = viewModel::clearDownloadHistory,
                        onCancelDownload = viewModel::cancelDownload,
                        onPauseDownload = viewModel::pauseDownload,
                        onResumeDownload = viewModel::resumeDownload,
                        onCheckForUpdates = viewModel::checkForUpdates,
                        onBack = viewModel::navigateBack,
                        registerInputActionHandler = { handler -> inputActionHandler = handler },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        PlayerPipController.removePlaybackStateListener(pipPlaybackStateListener)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingSystemSearchQuery = intent.searchQueryExtra()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPlayerRoute && PlayerPipController.hasPlayer) {
            enterPlayerPictureInPicture(showMessage = false)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPlayerPictureInPicture = isInPictureInPictureMode
        PlayerPipController.setPictureInPictureMode(isInPictureInPictureMode)
        if (isPlayerRoute) {
            if (isInPictureInPictureMode) {
                setPlayerFullscreen(false)
            } else {
                window.decorView.post {
                    setPlayerFullscreen(true)
                    window.decorView.requestLayout()
                }
            }
            updatePictureInPictureParams()
        }
    }

    private fun setPlayerFullscreen(enabled: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setCutoutMode(enabled)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (enabled) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            if (isTelevisionDevice) {
                controller.hide(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun configureWindowForCutouts() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.BLACK
        setCutoutMode(false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.show(WindowInsetsCompat.Type.systemBars())
        if (isTelevisionDevice) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    @Suppress("DEPRECATION")
    private fun setCutoutMode(fullscreen: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    if (fullscreen) {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    } else {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    }
            }
        }
    }

    private val isTelevisionDevice: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
    }

    private fun supportsPlayerPictureInPicture(): Boolean {
        return packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    private fun enterPlayerPictureInPicture(showMessage: Boolean = true) {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            if (showMessage) {
                Toast.makeText(this, getString(R.string.pip_not_supported_device), Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (!isPlayerRoute || isInPictureInPictureMode || !PlayerPipController.hasPlayer) return

        runCatching {
            PlayerPipController.setPictureInPictureMode(true)
            enterPictureInPictureMode(buildPlayerPictureInPictureParams())
        }.onSuccess { entered ->
            if (!entered) {
                PlayerPipController.setPictureInPictureMode(false)
            }
            if (!entered && showMessage) {
                Toast.makeText(this, getString(R.string.pip_rejected), Toast.LENGTH_SHORT).show()
            }
        }.onFailure { throwable ->
            PlayerPipController.setPictureInPictureMode(false)
            AppLog.w("YummyDroidPiP", "Failed to enter picture-in-picture", throwable)
            if (showMessage) {
                Toast.makeText(this, getString(R.string.pip_open_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updatePictureInPictureParams() {
        if (!supportsPlayerPictureInPicture() || !isPlayerRoute) return
        runCatching {
            setPictureInPictureParams(buildPlayerPictureInPictureParams())
        }.onFailure { throwable ->
            AppLog.w("YummyDroidPiP", "Failed to update picture-in-picture params", throwable)
        }
    }

    private fun buildPlayerPictureInPictureParams(): PictureInPictureParams {
        val actions = buildList {
            if (PlayerPipController.canPlayPreviousEpisode) {
                add(
                    buildPipAction(
                        action = PipActionReceiver.ACTION_PREVIOUS_EPISODE,
                        iconRes = R.drawable.ic_pip_previous,
                        label = getString(R.string.player_previous),
                        requestCode = PIP_PREVIOUS_REQUEST_CODE,
                    ),
                )
            }
            add(buildPlayPauseAction())
            if (PlayerPipController.canPlayNextEpisode) {
                add(
                    buildPipAction(
                        action = PipActionReceiver.ACTION_NEXT_EPISODE,
                        iconRes = R.drawable.ic_pip_next,
                        label = getString(R.string.player_next),
                        requestCode = PIP_NEXT_REQUEST_CODE,
                    ),
                )
            }
        }
        val paramsBuilder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(actions)
        val sourceRectHint = Rect()
        if (window.decorView.getGlobalVisibleRect(sourceRectHint) && !sourceRectHint.isEmpty) {
            paramsBuilder.setSourceRectHint(sourceRectHint)
        }
        return paramsBuilder.build()
    }

    private fun buildPlayPauseAction(): RemoteAction {
        val isPlaying = PlayerPipController.isPlaying
        val iconRes = if (isPlaying) R.drawable.ic_pip_pause else R.drawable.ic_pip_play
        val label = getString(if (isPlaying) R.string.pip_pause else R.string.pip_play)
        return buildPipAction(
            action = PipActionReceiver.ACTION_TOGGLE_PLAY_PAUSE,
            iconRes = iconRes,
            label = label,
            requestCode = PIP_PLAY_PAUSE_REQUEST_CODE,
        )
    }

    private fun buildPipAction(
        action: String,
        iconRes: Int,
        label: String,
        requestCode: Int,
    ): RemoteAction {
        val intent = Intent(this, PipActionReceiver::class.java).setAction(action)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return RemoteAction(
            Icon.createWithResource(this, iconRes),
            label,
            label,
            pendingIntent,
        )
    }

    private fun MotionEvent.toInputAction(): InputAction? {
        if (action != MotionEvent.ACTION_MOVE) return null
        if ((source and InputDevice.SOURCE_CLASS_JOYSTICK) == 0 &&
            (source and InputDevice.SOURCE_DPAD) == 0 &&
            (source and InputDevice.SOURCE_GAMEPAD) == 0
        ) {
            return null
        }

        val now = SystemClock.uptimeMillis()
        if (now - lastMotionNavigationAt < 180L) return null

        val hatX = getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = getAxisValue(MotionEvent.AXIS_HAT_Y)
        val x = getAxisValue(MotionEvent.AXIS_X)
        val y = getAxisValue(MotionEvent.AXIS_Y)
        val inputAction = when {
            hatX <= -0.5f || x <= -0.65f -> InputAction.Left
            hatX >= 0.5f || x >= 0.65f -> InputAction.Right
            hatY <= -0.5f || y <= -0.65f -> InputAction.Up
            hatY >= 0.5f || y >= 0.65f -> InputAction.Down
            else -> null
        }

        if (inputAction != null) {
            lastMotionNavigationAt = now
        }

        return inputAction
    }

    private fun KeyEvent.toInputAction(): InputAction? {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP -> InputAction.Up
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN -> InputAction.Down
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT,
            KeyEvent.KEYCODE_NAVIGATE_PREVIOUS -> InputAction.Left
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT,
            KeyEvent.KEYCODE_NAVIGATE_NEXT -> InputAction.Right
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
            KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_BUTTON_L1 -> InputAction.PreviousEpisode
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_BUTTON_R1 -> InputAction.NextEpisode
            KeyEvent.KEYCODE_MEDIA_PLAY -> InputAction.Play
            KeyEvent.KEYCODE_MEDIA_PAUSE -> InputAction.Pause
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> InputAction.PlayPause
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_NAVIGATE_IN -> InputAction.Confirm
            KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_NAVIGATE_OUT -> InputAction.Back
            else -> null
        }
    }

    private fun Intent.searchQueryExtra(): String? {
        return if (action == Intent.ACTION_SEARCH) {
            getStringExtra(SearchManager.QUERY)?.trim()?.takeIf { it.isNotBlank() }
        } else {
            null
        }
    }

    private fun Bundle?.animeIdExtra(): Long {
        return this?.getLong(EXTRA_ANIME_ID, 0L)?.takeIf { it > 0L } ?: 0L
    }

    private fun Bundle?.animeTitleExtra(): String {
        return this?.getString(EXTRA_ANIME_TITLE)?.trim().orEmpty()
    }

    private fun Bundle?.videoExtra(): VideoVariant? {
        val extras = this ?: return null
        val url = extras.getString(EXTRA_VIDEO_URL)?.takeIf { it.isNotBlank() } ?: return null
        return VideoVariant(
            id = extras.getLong(EXTRA_VIDEO_ID, 0L),
            animeId = extras.getLong(EXTRA_VIDEO_ANIME_ID, 0L),
            player = extras.getString(EXTRA_VIDEO_PLAYER)?.takeIf { it.isNotBlank() } ?: "External",
            dubbing = extras.getString(EXTRA_VIDEO_DUBBING)?.takeIf { it.isNotBlank() } ?: "Video",
            episode = extras.getString(EXTRA_VIDEO_EPISODE)?.takeIf { it.isNotBlank() } ?: "1",
            url = url,
            index = extras.getInt(EXTRA_VIDEO_INDEX, 1),
            durationSeconds = null,
            views = 0L,
        )
    }

    private companion object {
        const val EXTRA_ANIME_ID = "anime_id"
        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_VIDEO_ANIME_ID = "video_anime_id"
        const val EXTRA_VIDEO_INDEX = "video_index"
        const val EXTRA_VIDEO_URL = "video_url"
        const val EXTRA_VIDEO_PLAYER = "video_player"
        const val EXTRA_VIDEO_DUBBING = "video_dubbing"
        const val EXTRA_VIDEO_EPISODE = "video_episode"
        const val EXTRA_ANIME_TITLE = "anime_title"
        const val PIP_PLAY_PAUSE_REQUEST_CODE = 1001
        const val PIP_PREVIOUS_REQUEST_CODE = 1002
        const val PIP_NEXT_REQUEST_CODE = 1003
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 9105
    }
}
