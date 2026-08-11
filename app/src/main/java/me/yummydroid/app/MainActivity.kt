package me.yummydroid.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hcaptcha.sdk.HCaptcha
import com.hcaptcha.sdk.HCaptchaConfig
import com.hcaptcha.sdk.HCaptchaException
import com.hcaptcha.sdk.HCaptchaSize
import com.hcaptcha.sdk.HCaptchaTheme
import com.hcaptcha.sdk.HCaptchaTokenResponse
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.ui.YummyDroidApp
import me.yummydroid.app.ui.YummyDroidAppActions
import me.yummydroid.app.ui.theme.YummyDroidTheme

// HCaptchaActivity
class HCaptchaActivity : FragmentActivity() {
    private val hCaptcha by lazy { HCaptcha.getClient(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = HCaptchaConfig.builder()
            .siteKey(SITE_KEY)
            .size(HCaptchaSize.NORMAL)
            .theme(HCaptchaTheme.DARK)
            .locale("ru")
            .build()

        hCaptcha.addOnSuccessListener { response: HCaptchaTokenResponse ->
            response.markUsed()
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(EXTRA_CAPTCHA_TOKEN, response.tokenResult),
            )
            finish()
        }
        hCaptcha.addOnFailureListener { error: HCaptchaException ->
            setResult(
                Activity.RESULT_CANCELED,
                Intent().putExtra(EXTRA_CAPTCHA_ERROR, error.message),
            )
            finish()
        }
        hCaptcha.setup(config).verifyWithHCaptcha()
    }

    override fun onDestroy() {
        hCaptcha.removeAllListeners()
        hCaptcha.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CAPTCHA_TOKEN = "captcha_token"
        const val EXTRA_CAPTCHA_ERROR = "captcha_error"
        private const val SITE_KEY = "b1847961-208e-4a90-9671-1e6bba9e0b36"
    }
}

// MainActivity
class MainActivity : MainActivityRuntime()

// MainActivityContent
@Composable
internal fun MainActivityContent(
    initialRequest: MainActivityRequest,
    systemSearchQuery: String?,
    profileNotificationsOpenRequest: Long,
    isInPictureInPicture: Boolean,
    canUsePictureInPicture: Boolean,
    onViewModelAvailable: (YummyDroidViewModel) -> Unit,
    onSystemSearchConsumed: () -> Unit,
    onPlayerRouteChanged: (Boolean) -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
    onEnterPictureInPicture: () -> Unit,
    onExitApp: () -> Unit,
    onProfileNotificationsRequestConsumed: () -> Unit,
    registerInputActionHandler: (((InputActionEvent) -> Boolean)?) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: YummyDroidViewModel = viewModel()
    onViewModelAvailable(viewModel)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(
        initialRequest.animeId,
        initialRequest.video,
        profileNotificationsOpenRequest,
    ) {
        when {
            profileNotificationsOpenRequest > 0L -> Unit
            initialRequest.video != null -> {
                viewModel.playVideo(initialRequest.video, initialRequest.animeTitle)
            }
            initialRequest.animeId > 0L -> viewModel.openAnime(initialRequest.animeId)
        }
    }

    LaunchedEffect(systemSearchQuery) {
        val query = systemSearchQuery?.trim().orEmpty()
        if (query.isNotBlank()) {
            viewModel.selectBrowseSection(BrowseSection.Catalog)
            viewModel.updateSearchQuery(query)
            onSystemSearchConsumed()
        }
    }

    LaunchedEffect(state.route) {
        onPlayerRouteChanged(state.route is AppRoute.Player)
    }

    LaunchedEffect(state.auth.profile?.id, state.settings.notificationsEnabled) {
        SubscriptionNotificationScheduler.configureAsync(
            context = context,
            enabled = state.settings.notificationsEnabled && state.auth.profile != null,
        )
    }

    val appActions = remember(viewModel) {
        createMainActivityAppActions(
            viewModel = viewModel,
            onSettingsChange = onSettingsChange,
            onEnterPictureInPicture = onEnterPictureInPicture,
            onExitApp = onExitApp,
            onProfileNotificationsRequestConsumed = onProfileNotificationsRequestConsumed,
            registerInputActionHandler = registerInputActionHandler,
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
                isInPictureInPicture = isInPictureInPicture,
                canUsePictureInPicture = canUsePictureInPicture,
                openProfileNotificationsRequest = profileNotificationsOpenRequest,
                actions = appActions,
            )
        }
    }
}

private fun createMainActivityAppActions(
    viewModel: YummyDroidViewModel,
    onSettingsChange: (AppSettings) -> Unit,
    onEnterPictureInPicture: () -> Unit,
    onExitApp: () -> Unit,
    onProfileNotificationsRequestConsumed: () -> Unit,
    registerInputActionHandler: (((InputActionEvent) -> Boolean)?) -> Unit,
): YummyDroidAppActions {
    return YummyDroidAppActions(
        onQueryChange = viewModel::updateSearchQuery,
        onSearchSubmitted = viewModel::submitSearchQuery,
        onSearchHistorySelected = viewModel::selectSearchHistoryQuery,
        onRefresh = viewModel::refresh,
        onLoadMoreAnime = viewModel::loadMoreAnime,
        onBrowseSectionChange = viewModel::selectBrowseSection,
        onFiltersChange = viewModel::updateFilters,
        onResetFilters = viewModel::resetFilters,
        onSettingsChange = onSettingsChange,
        onOpenAnime = viewModel::openAnime,
        onFilterByGenre = viewModel::filterByGenre,
        onFilterByYear = viewModel::filterByYear,
        onFilterByStudio = viewModel::filterByStudio,
        onFilterByCreator = viewModel::filterByCreator,
        onSelectVideoGroup = viewModel::selectVideoGroup,
        onPlayVideo = viewModel::playVideo,
        onPlayVideoWithResumeChoice = viewModel::playVideoWithResumeChoice,
        onPlayVideoAt = viewModel::playVideoAt,
        onPlayVideoAtQuality = viewModel::playVideoAtQuality,
        onSelectPlaybackSource = viewModel::selectPlaybackSource,
        onChoosePlayerResumePosition = viewModel::choosePlayerResumePosition,
        onRetryVideo = viewModel::retryVideo,
        onPlaybackFailed = viewModel::fallbackPlaybackSource,
        onPlaybackStarted = viewModel::confirmPlaybackSource,
        onPlaybackEnded = viewModel::handlePlaybackEnded,
        onPlaybackProgress = viewModel::savePlaybackProgress,
        onResetAnimeWatchProgress = viewModel::resetAnimeWatchProgress,
        onEnterPictureInPicture = onEnterPictureInPicture,
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
        onTogglePlayerVideoSubscription = viewModel::togglePlayerVideoSubscription,
        onUnsubscribeVideoSubscription = viewModel::unsubscribeVideoSubscription,
        onRefreshVideoSubscriptions = viewModel::refreshVideoSubscriptions,
        onRefreshProfileNotifications = viewModel::refreshProfileNotifications,
        onMarkProfileNotificationRead = viewModel::markProfileNotificationRead,
        onMarkAllProfileNotificationsRead = viewModel::markAllProfileNotificationsRead,
        onDeleteProfileNotification = viewModel::deleteProfileNotification,
        onResolveSampledDownloadQualities = viewModel::resolveSampledDownloadQualities,
        onDownloadAllVideos = viewModel::downloadAllVideosForOffline,
        onDeleteOfflineVideo = viewModel::deleteOfflineVideo,
        onDeleteOfflineAnime = viewModel::deleteOfflineAnime,
        onClearAppContentCache = viewModel::clearAppContentCache,
        onRefreshAppContentCacheSize = viewModel::refreshAppContentCacheSize,
        onClearDownloadHistory = viewModel::clearDownloadHistory,
        onCancelDownload = viewModel::cancelDownload,
        onPauseDownload = viewModel::pauseDownload,
        onResumeDownload = viewModel::resumeDownload,
        onCheckForUpdates = viewModel::checkForUpdates,
        onConsumePlayerNotice = viewModel::consumePlayerNotice,
        onBack = viewModel::navigateBack,
        onExitApp = onExitApp,
        onProfileNotificationsRequestConsumed = onProfileNotificationsRequestConsumed,
        registerInputActionHandler = registerInputActionHandler,
    )
}

// MainActivityInputRouter
internal class MainActivityInputRouter(
    private val uptimeMillis: () -> Long = SystemClock::uptimeMillis,
) {
    private var handler: ((InputActionEvent) -> Boolean)? = null
    private var lastMotionNavigationAt = 0L
    private var hadPointerInputSinceNavigation = false
    private var handledBackKeyDown = false

    fun setHandler(updatedHandler: ((InputActionEvent) -> Boolean)?) {
        handler = updatedHandler
    }

    fun interceptKeyEvent(event: KeyEvent): Boolean? {
        val action = inputActionForKeyCode(event.keyCode)
        return when {
            action == InputAction.Back -> interceptBackEvent(event)
            event.action == KeyEvent.ACTION_DOWN && action != null -> interceptActionEvent(event, action)
            else -> null
        }
    }

    fun recoverAfterSystemDispatch(event: KeyEvent, handledBySystem: Boolean): Boolean {
        if (handledBySystem || event.action != KeyEvent.ACTION_DOWN) return handledBySystem
        val action = inputActionForKeyCode(event.keyCode) ?: return false
        if (!MainActivityInputPolicy.usesDpadFocusRecovery(action)) return false
        return handler?.invoke(
            InputActionEvent(
                action = action,
                repeatCount = event.repeatCount,
                focusRecovery = true,
            ),
        ) == true
    }

    fun recordTouchEvent(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            hadPointerInputSinceNavigation = true
        }
    }

    fun consumeGenericMotionEvent(event: MotionEvent): Boolean {
        val action = motionAction(event) ?: return false
        return handler?.invoke(InputActionEvent(action)) == true
    }

    fun handleBackPressed() {
        if (!handledBackKeyDown) {
            handler?.invoke(InputActionEvent(InputAction.Back))
        }
    }

    private fun interceptBackEvent(event: KeyEvent): Boolean? {
        if (event.action == KeyEvent.ACTION_UP && handledBackKeyDown) {
            handledBackKeyDown = false
            return true
        }
        if (event.action != KeyEvent.ACTION_DOWN) return null
        val handled = dispatchAction(event, InputAction.Back)
        handledBackKeyDown = handled
        return true.takeIf { handled }
    }

    private fun interceptActionEvent(event: KeyEvent, action: InputAction): Boolean? {
        val handled = dispatchAction(event, action)
        if (MainActivityInputPolicy.resetsPointerInputNavigation(action)) {
            hadPointerInputSinceNavigation = false
        }
        return true.takeIf { handled }
    }

    private fun dispatchAction(event: KeyEvent, action: InputAction): Boolean {
        return handler?.invoke(
            InputActionEvent(
                action = action,
                repeatCount = event.repeatCount,
                followsPointerInput = hadPointerInputSinceNavigation,
            ),
        ) == true
    }

    private fun motionAction(event: MotionEvent): InputAction? {
        if (event.action != MotionEvent.ACTION_MOVE || !event.hasNavigationSource()) return null
        val now = uptimeMillis()
        if (now - lastMotionNavigationAt < MOTION_NAVIGATION_THROTTLE_MILLIS) return null
        val inputAction = MainActivityInputPolicy.actionForAxes(
            hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X),
            hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y),
            x = event.getAxisValue(MotionEvent.AXIS_X),
            y = event.getAxisValue(MotionEvent.AXIS_Y),
        )
        if (inputAction != null) {
            lastMotionNavigationAt = now
        }
        return inputAction
    }

    private fun MotionEvent.hasNavigationSource(): Boolean {
        return (source and InputDevice.SOURCE_CLASS_JOYSTICK) != 0 ||
            (source and InputDevice.SOURCE_DPAD) != 0 ||
            (source and InputDevice.SOURCE_GAMEPAD) != 0
    }
}

internal object MainActivityInputPolicy {
    fun actionForAxes(
        hatX: Float,
        hatY: Float,
        x: Float,
        y: Float,
    ): InputAction? {
        return when {
            hatX <= -HAT_AXIS_THRESHOLD || x <= -STICK_AXIS_THRESHOLD -> InputAction.Left
            hatX >= HAT_AXIS_THRESHOLD || x >= STICK_AXIS_THRESHOLD -> InputAction.Right
            hatY <= -HAT_AXIS_THRESHOLD || y <= -STICK_AXIS_THRESHOLD -> InputAction.Up
            hatY >= HAT_AXIS_THRESHOLD || y >= STICK_AXIS_THRESHOLD -> InputAction.Down
            else -> null
        }
    }

    fun resetsPointerInputNavigation(action: InputAction): Boolean {
        return action in pointerResetActions
    }

    fun usesDpadFocusRecovery(action: InputAction?): Boolean {
        return action in focusRecoveryActions
    }

    private val pointerResetActions = setOf(
        InputAction.Up,
        InputAction.Down,
        InputAction.Left,
        InputAction.Right,
        InputAction.Confirm,
    )
    private val focusRecoveryActions = setOf(
        InputAction.Up,
        InputAction.Down,
        InputAction.Left,
        InputAction.Right,
    )
}

private const val MOTION_NAVIGATION_THROTTLE_MILLIS = 180L
private const val HAT_AXIS_THRESHOLD = 0.5f
private const val STICK_AXIS_THRESHOLD = 0.65f

// MainActivityRequest
internal data class MainActivityRequest(
    val searchQuery: String? = null,
    val openProfileNotifications: Boolean = false,
    val animeId: Long = 0L,
    val animeTitle: String = "",
    val video: VideoVariant? = null,
)

internal fun Intent.toMainActivityRequest(): MainActivityRequest {
    return MainActivityRequest(
        searchQuery = searchQueryExtra(),
        openProfileNotifications = requestsProfileNotifications(),
        animeId = extras.animeIdExtra(),
        animeTitle = extras.animeTitleExtra(),
        video = extras.videoExtra(),
    )
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

private const val EXTRA_ANIME_ID = "anime_id"
private const val EXTRA_VIDEO_ID = "video_id"
private const val EXTRA_VIDEO_ANIME_ID = "video_anime_id"
private const val EXTRA_VIDEO_INDEX = "video_index"
private const val EXTRA_VIDEO_URL = "video_url"
private const val EXTRA_VIDEO_PLAYER = "video_player"
private const val EXTRA_VIDEO_DUBBING = "video_dubbing"
private const val EXTRA_VIDEO_EPISODE = "video_episode"
private const val EXTRA_ANIME_TITLE = "anime_title"

// MainActivityRuntime
abstract class MainActivityRuntime : ComponentActivity() {
    private val inputRouter = MainActivityInputRouter()
    private val windowController by lazy(LazyThreadSafetyMode.NONE) {
        MainActivityWindowController(this)
    }
    private val pictureInPictureController by lazy(LazyThreadSafetyMode.NONE) {
        MainActivityPictureInPictureController(this) { isPlayerRoute }
    }
    private var viewModelRef: YummyDroidViewModel? = null
    private var isPlayerRoute = false
    private var isPlayerPictureInPicture by mutableStateOf(false)
    private var pendingSystemSearchQuery by mutableStateOf<String?>(null)
    private var pendingProfileNotificationsOpenRequest by mutableLongStateOf(0L)

    override fun attachBaseContext(newBase: Context) {
        val interfaceScale = AppSettingsStorage(newBase).readInterfaceScale()
        super.attachBaseContext(newBase.withAppUiConfiguration(interfaceScale))
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        inputRouter.interceptKeyEvent(event)?.let { return it }
        return inputRouter.recoverAfterSystemDispatch(event, super.dispatchKeyEvent(event))
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        inputRouter.recordTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        return inputRouter.consumeGenericMotionEvent(event) || super.dispatchGenericMotionEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        windowController.captureSystemBarColors()
        windowController.configureForAppContent()
        requestNotificationPermissionIfNeeded()
        DownloadCenter.initialize(applicationContext)
        pictureInPictureController.start()
        configureDecorFocus()
        registerBackHandler()

        val initialRequest = intent.toMainActivityRequest()
        pendingSystemSearchQuery = initialRequest.searchQuery
        if (initialRequest.openProfileNotifications) {
            requestOpenProfileNotifications()
        }

        setContent {
            MainActivityContent(
                initialRequest = initialRequest,
                systemSearchQuery = pendingSystemSearchQuery,
                profileNotificationsOpenRequest = pendingProfileNotificationsOpenRequest,
                isInPictureInPicture = isPlayerPictureInPicture,
                canUsePictureInPicture = pictureInPictureController.isSupported(),
                onViewModelAvailable = { viewModelRef = it },
                onSystemSearchConsumed = { pendingSystemSearchQuery = null },
                onPlayerRouteChanged = ::handlePlayerRouteChanged,
                onSettingsChange = ::handleSettingsChange,
                onEnterPictureInPicture = pictureInPictureController::enter,
                onExitApp = ::finish,
                onProfileNotificationsRequestConsumed = {
                    pendingProfileNotificationsOpenRequest = 0L
                },
                registerInputActionHandler = inputRouter::setHandler,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        window.decorView.post(::applyCurrentWindowMode)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.post(::applyCurrentWindowMode)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        window.decorView.post(::applyCurrentWindowMode)
    }

    override fun onDestroy() {
        pictureInPictureController.stop()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val request = intent.toMainActivityRequest()
        pendingSystemSearchQuery = request.searchQuery
        if (request.openProfileNotifications) {
            requestOpenProfileNotifications()
            return
        }
        request.video?.let { video ->
            viewModelRef?.playVideo(video, request.animeTitle)
            return
        }
        if (request.animeId > 0L) {
            viewModelRef?.openAnime(request.animeId)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pictureInPictureController.shouldEnterOnUserLeaveHint(Build.VERSION.SDK_INT)) {
            pictureInPictureController.enter(showMessage = false)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPlayerPictureInPicture = isInPictureInPictureMode
        PlayerPipController.setPictureInPictureMode(isInPictureInPictureMode)
        if (!isPlayerRoute) return

        if (isInPictureInPictureMode) {
            windowController.configureForAppContent()
        } else {
            window.decorView.post {
                applyCurrentWindowMode()
                window.decorView.requestLayout()
            }
        }
        pictureInPictureController.updateParams()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (
            requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            val unreadCount = viewModelRef?.uiState?.value?.auth?.profile?.unreadNotifications ?: 0
            SubscriptionNotificationBadge.update(this, unreadCount)
            SubscriptionNotificationScheduler.configureFromStoredStateAsync(
                context = this,
                runImmediately = true,
            )
        }
    }

    private fun configureDecorFocus() {
        window.decorView.isFocusable = true
        window.decorView.isFocusableInTouchMode = true
        window.decorView.requestFocus()
    }

    private fun registerBackHandler() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = inputRouter.handleBackPressed()
            },
        )
    }

    private fun handlePlayerRouteChanged(playerRoute: Boolean) {
        isPlayerRoute = playerRoute
        applyCurrentWindowMode()
        pictureInPictureController.updateParams()
        if (!playerRoute) {
            PlayerPipController.setPictureInPictureMode(false)
        }
    }

    private fun handleSettingsChange(updatedSettings: AppSettings) {
        val settingsStorage = AppSettingsStorage(this)
        val previousInterfaceScale = settingsStorage.readInterfaceScale()
        val interfaceScaleChanged = previousInterfaceScale != updatedSettings.interfaceScale
        if (interfaceScaleChanged) {
            settingsStorage.saveInterfaceScale(updatedSettings.interfaceScale)
        }
        viewModelRef?.updateSettings(updatedSettings)
        if (interfaceScaleChanged) {
            window.decorView.post(::recreate)
        }
    }

    private fun applyCurrentWindowMode() {
        windowController.applyCurrentMode(
            isPlayerRoute = isPlayerRoute,
            isInPictureInPictureMode = isInPictureInPictureMode,
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
    }

    private fun requestOpenProfileNotifications() {
        pendingProfileNotificationsOpenRequest = SystemClock.uptimeMillis()
            .coerceAtLeast(pendingProfileNotificationsOpenRequest + 1L)
    }

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 9105
    }
}

// MainActivityWindowController
internal class MainActivityWindowController(
    private val activity: ComponentActivity,
) {
    private var appStatusBarColor = Color.BLACK
    private var appNavigationBarColor = Color.BLACK

    val isTelevisionDevice: Boolean
        get() = (activity.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION

    @Suppress("DEPRECATION")
    fun captureSystemBarColors() {
        appStatusBarColor = activity.window.statusBarColor
        appNavigationBarColor = activity.window.navigationBarColor
    }

    fun applyCurrentMode(
        isPlayerRoute: Boolean,
        isInPictureInPictureMode: Boolean,
    ) {
        if (isPlayerRoute && !isInPictureInPictureMode) {
            setPlayerFullscreen()
        } else {
            configureForAppContent()
        }
    }

    @Suppress("DEPRECATION")
    fun configureForAppContent() {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, !isTelevisionDevice)
        applyPreferredAppFrameRate()
        window.statusBarColor = appStatusBarColor
        window.navigationBarColor = appNavigationBarColor
        setCutoutMode(fullscreen = false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.show(WindowInsetsCompat.Type.systemBars())
        if (shouldHideAppStatusBar) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    @Suppress("DEPRECATION")
    private fun setPlayerFullscreen() {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setPreferredWindowRefreshRate(0f)
        setCutoutMode(fullscreen = true)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.BLACK
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    @Suppress("DEPRECATION")
    private fun applyPreferredAppFrameRate() {
        if (isTelevisionDevice) {
            setPreferredWindowRefreshRate(APP_CONTENT_FRAME_RATE)
        }
    }

    @Suppress("DEPRECATION")
    private fun setPreferredWindowRefreshRate(refreshRate: Float) {
        activity.window.attributes = activity.window.attributes.apply {
            preferredRefreshRate = refreshRate
        }
    }

    @Suppress("DEPRECATION")
    private fun setCutoutMode(fullscreen: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        activity.window.attributes = activity.window.attributes.apply {
            layoutInDisplayCutoutMode = if (fullscreen) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
        }
    }

    private val shouldHideAppStatusBar: Boolean
        get() = isTelevisionDevice ||
            activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}

private const val APP_CONTENT_FRAME_RATE = 60f
