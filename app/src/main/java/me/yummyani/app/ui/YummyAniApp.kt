package me.yummyani.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.Collator
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import me.yummyani.app.AppLog
import me.yummyani.app.AppRoute
import me.yummyani.app.AuthUiState
import me.yummyani.app.BuildConfig
import me.yummyani.app.HCaptchaActivity
import me.yummyani.app.InputAction
import me.yummyani.app.LoadState
import me.yummyani.app.PagingUiState
import me.yummyani.app.PipPlayerHandle
import me.yummyani.app.PlayerPipController
import me.yummyani.app.R
import me.yummyani.app.YummyAniUiState
import me.yummyani.app.data.Anime
import me.yummyani.app.data.AnimeDetails
import me.yummyani.app.data.AnimeGenreFilter
import me.yummyani.app.data.AnimeSort
import me.yummyani.app.data.AnimeStatusFilter
import me.yummyani.app.data.AppSettings
import me.yummyani.app.data.BrowseFilters
import me.yummyani.app.data.FilterCatalog
import me.yummyani.app.data.FilterOption
import me.yummyani.app.data.PlayerDecoderMode
import me.yummyani.app.data.PreferredQuality
import me.yummyani.app.data.RelatedAnime
import me.yummyani.app.data.RatingDetails
import me.yummyani.app.data.ResolvedVideoStream
import me.yummyani.app.data.SiteDomainResolver
import me.yummyani.app.data.UserAnimeListMark
import me.yummyani.app.data.UserAnimeMark
import me.yummyani.app.data.UserProfile
import me.yummyani.app.data.VideoVariant
import me.yummyani.app.data.ageRatingFilterOptions
import me.yummyani.app.data.seasonFilterOptions
import me.yummyani.app.data.statusFilterOptions
import me.yummyani.app.data.translateFilterOptions
import me.yummyani.app.data.userMarkFilterOptions
import me.yummyani.app.data.normalizeSiteBaseUrl
import me.yummyani.app.data.normalizedSiteBaseUrls
import me.yummyani.app.ui.components.dpadClickable
import me.yummyani.app.ui.components.focusRing
import okhttp3.OkHttpClient

@Composable
fun YummyAniApp(
    state: YummyAniUiState,
    isInPictureInPicture: Boolean,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMoreAnime: () -> Unit,
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
    canUsePictureInPicture: Boolean,
    onEnterPictureInPicture: () -> Unit,
    onLogin: (String, String, String?) -> Unit,
    onLogout: () -> Unit,
    onSelectAnimeListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    registerInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var loginDialogOpen by remember { mutableStateOf(false) }
    var profileDialogOpen by remember { mutableStateOf(false) }
    var settingsDialogOpen by remember { mutableStateOf(false) }
    var playerInputActionHandler by remember { mutableStateOf<((InputAction) -> Boolean)?>(null) }
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
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
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
                onQueryChange = onQueryChange,
                onRefresh = onRefresh,
                onLoadMoreAnime = onLoadMoreAnime,
                onFiltersChange = onFiltersChange,
                onResetFilters = onResetFilters,
                onOpenSettings = { settingsDialogOpen = true },
                onOpenLogin = { loginDialogOpen = true },
                onOpenProfile = { profileDialogOpen = true },
                onOpenAnime = onOpenAnime,
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
                onSelectAnimeListMark = onSelectAnimeListMark,
                onToggleFavorite = onToggleFavorite,
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
                onSelectGroup = onSelectVideoGroup,
                onPlayVideo = onPlayVideo,
                onPlayVideoAt = onPlayVideoAt,
                onRetry = onRetryVideo,
                onPlaybackFailed = onPlaybackFailed,
                onPlaybackStarted = onPlaybackStarted,
                canUsePictureInPicture = canUsePictureInPicture,
                onEnterPictureInPicture = onEnterPictureInPicture,
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
                onOpenLogin = {
                    profileDialogOpen = false
                    loginDialogOpen = true
                },
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
                onSettingsChange = onSettingsChange,
                onDismiss = { settingsDialogOpen = false },
            )
        }
    }
}

@Composable
private fun BrowseScreen(
    state: YummyAniUiState,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMoreAnime: () -> Unit,
    onFiltersChange: (BrowseFilters) -> Unit,
    onResetFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAnime: (Long) -> Unit,
) {
    val isSearching = state.searchQuery.isNotBlank()
    val contentState = if (isSearching) state.searchResults else state.featured
    val pagingState = if (isSearching) state.searchPaging else state.featuredPaging
    val configuration = LocalConfiguration.current
    val isWide = configuration.screenWidthDp >= 720
    val gridState = rememberLazyGridState()
    var searchDialogOpen by remember { mutableStateOf(false) }
    var filtersDialogOpen by remember { mutableStateOf(false) }

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
            auth = state.auth,
            activeFilters = state.filters.activeCount,
            onOpenLogin = onOpenLogin,
            onOpenProfile = onOpenProfile,
            isWide = isWide,
        )

        Box(modifier = Modifier.weight(1f)) {
            AnimeListStateContent(
                state = contentState,
                onRetry = onRefresh,
                emptyMessage = if (isSearching) "Ничего не найдено" else "Каталог пуст",
            ) { animes ->
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = if (isWide) 178.dp else 142.dp),
                    state = gridState,
                    contentPadding = PaddingValues(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(animes, key = { _, anime -> anime.id }) { _, anime ->
                        AnimeCard(
                            anime = anime,
                            onClick = { onOpenAnime(anime.id) },
                        )
                    }

                    if (pagingState.isLoadingMore || pagingState.canLoadMore || pagingState.error != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            PagingGridFooter(
                                paging = pagingState,
                                onLoadMore = onLoadMoreAnime,
                            )
                        }
                    }
                }
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
            onApply = onFiltersChange,
            onReset = onResetFilters,
            onDismiss = { filtersDialogOpen = false },
        )
    }
}

@Composable
private fun BrowseTopBarModern(
    onRefresh: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    auth: AuthUiState,
    activeFilters: Int,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    isWide: Boolean,
) {
    val horizontalPadding = if (isWide) 32.dp else 16.dp
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val stackActions = !isWide && screenWidthDp < 360

    if (isWide) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .padding(horizontal = horizontalPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "YummyAnime",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            BrowseTopBarActions(
                onRefresh = onRefresh,
                onOpenSearch = onOpenSearch,
                onOpenFilters = onOpenFilters,
                onOpenSettings = onOpenSettings,
                auth = auth,
                activeFilters = activeFilters,
                onOpenLogin = onOpenLogin,
                onOpenProfile = onOpenProfile,
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
            Text(
                text = "YummyAnime",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            BrowseTopBarActions(
                onRefresh = onRefresh,
                onOpenSearch = onOpenSearch,
                onOpenFilters = onOpenFilters,
                onOpenSettings = onOpenSettings,
                auth = auth,
                activeFilters = activeFilters,
                onOpenLogin = onOpenLogin,
                onOpenProfile = onOpenProfile,
                modifier = Modifier.fillMaxWidth(),
                spreadActions = !stackActions,
                stackActions = stackActions,
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
    auth: AuthUiState,
    activeFilters: Int,
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
                ProfileActionButton(auth, onOpenLogin, onOpenProfile)
                SearchActionButton(onOpenSearch)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                FiltersActionButton(activeFilters, onOpenFilters)
                SettingsActionButton(onOpenSettings)
                RefreshActionButton(onRefresh)
            }
        }
        return
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (spreadActions) Arrangement.SpaceBetween else Arrangement.spacedBy(10.dp),
    ) {
        ProfileActionButton(auth, onOpenLogin, onOpenProfile)
        SearchActionButton(onOpenSearch)
        FiltersActionButton(activeFilters, onOpenFilters)
        SettingsActionButton(onOpenSettings)
        RefreshActionButton(onRefresh)
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
            contentDescription = if (auth.profile == null) "Войти" else "Профиль",
        )
    }
}

@Composable
private fun SearchActionButton(onOpenSearch: () -> Unit) {
    IconButton(
        onClick = onOpenSearch,
        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
    ) {
        Icon(Icons.Default.Search, contentDescription = "Поиск")
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
            Icon(Icons.Default.Tune, contentDescription = "Фильтры")
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
        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
    }
}

@Composable
private fun SearchDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
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

    LaunchedEffect(Unit) {
        delay(120)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Поиск") },
        text = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                )
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Что найти?")
                            }
                            runCatching {
                                keyboardController?.hide()
                                voiceSearchLauncher.launch(intent)
                            }.onFailure { throwable ->
                                if (throwable is ActivityNotFoundException) {
                                    Toast.makeText(
                                        context,
                                        "Голосовой поиск недоступен на этом устройстве",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    throw throwable
                                }
                            }
                        },
                        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Голосовой поиск")
                    }
                },
                placeholder = { Text("Найти аниме") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(2.dp)
                    .focusRequester(focusRequester)
                    .focusRing(RoundedCornerShape(8.dp)),
            )
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(
                    text = "Готово",
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
    onApply: (BrowseFilters) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isAuthorized = auth.profile != null
    var draft by remember(filters, isAuthorized) {
        mutableStateOf(if (isAuthorized) filters else filters.copy(userMarks = emptySet()))
    }
    var expandedSection by remember { mutableStateOf("") }
    val catalog = (catalogState as? LoadState.Ready)?.data ?: FilterCatalog.Empty
    val containerScrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Фильтры") },
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
                )

                if (isAuthorized) {
                    FilterAccordionSection(
                        id = "user_marks",
                        title = "Метки",
                        options = userMarkFilterOptions,
                        selected = draft.userMarks,
                        expandedSection = expandedSection,
                        onExpandedChange = { expandedSection = it },
                        onToggle = { value -> draft = draft.copy(userMarks = draft.userMarks.toggle(value)) },
                    )
                }

                RangeAccordionSection(
                    id = "years",
                    title = "Год",
                    summary = rangeSummary(draft.fromYear, draft.toYear),
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    startLabel = "От",
                    endLabel = "До",
                    startText = draft.fromYear?.toString().orEmpty(),
                    endText = draft.toYear?.toString().orEmpty(),
                    keyboardType = KeyboardType.Number,
                    sanitizeInput = ::integerInput,
                    onStartChange = { value -> draft = draft.copy(fromYear = value.yearFilterValue()) },
                    onEndChange = { value -> draft = draft.copy(toYear = value.yearFilterValue()) },
                )

                RangeAccordionSection(
                    id = "rating_range",
                    title = "Рейтинг",
                    summary = rangeSummary(draft.minRating, draft.maxRating),
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    startLabel = "От",
                    endLabel = "До",
                    startText = draft.minRating.filterText(),
                    endText = draft.maxRating.filterText(),
                    keyboardType = KeyboardType.Decimal,
                    sanitizeInput = ::decimalInput,
                    onStartChange = { value -> draft = draft.copy(minRating = value.ratingFilterValue()) },
                    onEndChange = { value -> draft = draft.copy(maxRating = value.ratingFilterValue()) },
                )

                RangeAccordionSection(
                    id = "episodes",
                    title = "Серии",
                    summary = rangeSummary(draft.episodeFrom, draft.episodeTo),
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    startLabel = "От",
                    endLabel = "До",
                    startText = draft.episodeFrom?.toString().orEmpty(),
                    endText = draft.episodeTo?.toString().orEmpty(),
                    keyboardType = KeyboardType.Number,
                    sanitizeInput = ::integerInput,
                    onStartChange = { value -> draft = draft.copy(episodeFrom = value.episodeFilterValue()) },
                    onEndChange = { value -> draft = draft.copy(episodeTo = value.episodeFilterValue()) },
                )

                FilterAccordionSection(
                    id = "status",
                    title = "Статус",
                    options = statusFilterOptions,
                    selected = draft.statuses,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(statuses = draft.statuses.toggle(value)) },
                )
                FilterAccordionSection(
                    id = "genres",
                    title = "Жанры",
                    options = catalog.genres,
                    selected = draft.genres,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(genres = draft.genres.toggle(value)) },
                )
                FilterAccordionSection(
                    id = "excluded_genres",
                    title = "Исключить жанры",
                    options = catalog.genres,
                    selected = draft.excludedGenres,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(excludedGenres = draft.excludedGenres.toggle(value)) },
                )
                FilterAccordionSection(
                    id = "types",
                    title = "Тип",
                    options = catalog.types,
                    selected = draft.types,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(types = draft.types.toggle(value)) },
                )
                FilterAccordionSection(
                    id = "seasons",
                    title = "Сезон",
                    options = seasonFilterOptions,
                    selected = draft.seasons,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(seasons = draft.seasons.toggle(value)) },
                )
                FilterAccordionSection(
                    id = "translates",
                    title = "Озвучка",
                    options = translateFilterOptions,
                    selected = draft.translates,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(translates = draft.translates.toggle(value)) },
                )
                FilterAccordionSection(
                    id = "age",
                    title = "Возраст",
                    options = ageRatingFilterOptions,
                    selected = draft.ageRatings,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(ageRatings = draft.ageRatings.toggle(value)) },
                )

                if (catalogState is LoadState.Error) {
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
                    text = "Сбросить",
                    onClick = {
                        draft = BrowseFilters()
                        onReset()
                        onDismiss()
                    },
                )
                DialogActionButton(
                    text = "Отмена",
                    onClick = onDismiss,
                )
                DialogActionButton(
                    text = "Применить",
                    primary = true,
                    onClick = {
                        onApply(if (isAuthorized) draft else draft.copy(userMarks = emptySet()))
                        onDismiss()
                    },
                )
            }
        },
    )
}

@Composable
private fun SortAccordionSection(
    expanded: Boolean,
    selected: AnimeSort,
    onToggleExpanded: () -> Unit,
    onSelected: (AnimeSort) -> Unit,
) {
    AccordionHeader(
        title = "Сортировка",
        summary = selected.title,
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
                    title = sort.title,
                    selected = selected == sort,
                    onClick = { onSelected(sort) },
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
            items(sortedOptions, key = { it.value }) { option ->
                SelectableFilterRow(
                    title = option.title,
                    selected = option.value in selected,
                    onClick = { onToggle(option.value) },
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
                    .focusRing(RoundedCornerShape(8.dp)),
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
                    .focusRing(RoundedCornerShape(8.dp)),
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
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

private fun rangeSummary(from: Any?, to: Any?): String {
    val start = from.filterText()
    val end = to.filterText()
    return when {
        start.isBlank() && end.isBlank() -> "Все"
        start.isNotBlank() && end.isNotBlank() -> "$start - $end"
        start.isNotBlank() -> "от $start"
        else -> "до $end"
    }
}

private fun Any?.filterText(): String {
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

private fun selectedFilterSummary(
    options: List<FilterOption>,
    selected: Set<String>,
): String {
    if (selected.isEmpty()) return "Все"

    val titles = options
        .filter { it.value in selected }
        .map { it.title }

    return when {
        titles.isEmpty() -> "${selected.size} выбрано"
        titles.size <= 2 -> titles.joinToString(", ")
        else -> titles.take(2).joinToString(", ") + " +${titles.size - 2}"
    }
}

@Composable
private fun FiltersDialogModern(
    filters: BrowseFilters,
    catalogState: LoadState<FilterCatalog>,
    onApply: (BrowseFilters) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(filters) { mutableStateOf(filters) }
    val catalog = (catalogState as? LoadState.Ready)?.data ?: FilterCatalog.Empty

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Фильтры") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                SortSection(
                    selected = draft.sort,
                    onSelected = { draft = draft.copy(sort = it) },
                )
                MultiSelectSection(
                    title = "Статус",
                    options = statusFilterOptions,
                    selected = draft.statuses,
                    onToggle = { value -> draft = draft.copy(statuses = draft.statuses.toggle(value)) },
                )
                MultiSelectSection(
                    title = "Жанры",
                    options = catalog.genres,
                    selected = draft.genres,
                    onToggle = { value -> draft = draft.copy(genres = draft.genres.toggle(value)) },
                )
                MultiSelectSection(
                    title = "Исключить жанры",
                    options = catalog.genres,
                    selected = draft.excludedGenres,
                    onToggle = { value -> draft = draft.copy(excludedGenres = draft.excludedGenres.toggle(value)) },
                )
                MultiSelectSection(
                    title = "Тип",
                    options = catalog.types,
                    selected = draft.types,
                    onToggle = { value -> draft = draft.copy(types = draft.types.toggle(value)) },
                )
                MultiSelectSection(
                    title = "Сезон",
                    options = seasonFilterOptions,
                    selected = draft.seasons,
                    onToggle = { value -> draft = draft.copy(seasons = draft.seasons.toggle(value)) },
                )
                MultiSelectSection(
                    title = "Озвучка",
                    options = translateFilterOptions,
                    selected = draft.translates,
                    onToggle = { value -> draft = draft.copy(translates = draft.translates.toggle(value)) },
                )
                MultiSelectSection(
                    title = "Возраст",
                    options = ageRatingFilterOptions,
                    selected = draft.ageRatings,
                    onToggle = { value -> draft = draft.copy(ageRatings = draft.ageRatings.toggle(value)) },
                )

                if (catalogState is LoadState.Error) {
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
                    text = "Сбросить",
                    onClick = {
                        draft = BrowseFilters()
                        onReset()
                        onDismiss()
                    },
                )
                DialogActionButton(
                    text = "Отмена",
                    onClick = onDismiss,
                )
                DialogActionButton(
                    text = "Применить",
                    primary = true,
                    onClick = {
                        onApply(draft)
                        onDismiss()
                    },
                )
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SortSection(
    selected: AnimeSort,
    onSelected: (AnimeSort) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Сортировка",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimeSort.entries.forEach { sort ->
                FilterChip(
                    selected = selected == sort,
                    onClick = { onSelected(sort) },
                    label = { Text(sort.title) },
                    modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiSelectSection(
    title: String,
    options: List<FilterOption>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    if (options.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option.value in selected,
                    onClick = { onToggle(option.value) },
                    label = {
                        Text(
                            text = option.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier
                        .widthIn(max = 240.dp)
                        .focusRing(RoundedCornerShape(8.dp)),
                )
            }
        }
    }
}

private fun Set<String>.toggle(value: String): Set<String> {
    return if (value in this) this - value else this + value
}

@Composable
private fun FiltersDialog(
    filters: BrowseFilters,
    onSortSelected: (AnimeSort) -> Unit,
    onStatusSelected: (AnimeStatusFilter) -> Unit,
    onGenreSelected: (AnimeGenreFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Фильтры") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilterChoiceRow(
                    title = "Сортировка",
                    filterItems = AnimeSort.entries,
                    selected = filters.sort,
                    itemLabel = { it.title },
                    onSelected = onSortSelected,
                )
                FilterChoiceRow(
                    title = "Статус",
                    filterItems = AnimeStatusFilter.entries,
                    selected = filters.status,
                    itemLabel = { it.title },
                    onSelected = onStatusSelected,
                )
                FilterChoiceRow(
                    title = "Жанр",
                    filterItems = AnimeGenreFilter.entries,
                    selected = filters.genre,
                    itemLabel = { it.title },
                    onSelected = onGenreSelected,
                )
            }
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(
                    text = "Готово",
                    primary = true,
                    onClick = onDismiss,
                )
            }
        },
    )
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
        title = { Text("Вход") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                    label = { Text("Email") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRing(RoundedCornerShape(8.dp)),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("Пароль") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRing(RoundedCornerShape(8.dp)),
                )
                auth.error?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                DialogActionRow {
                    DialogActionButton(
                        text = "Регистрация",
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(sitePageUrl(siteBaseUrl, "register"))),
                            )
                        },
                    )
                    DialogActionButton(
                        text = "Забыл пароль",
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(sitePageUrl(siteBaseUrl, "login/reset-password"))),
                            )
                        },
                    )
                }
            }
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(
                    text = "Отмена",
                    onClick = onDismiss,
                )
                DialogActionButton(
                    text = "Войти",
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
    onOpenLogin: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
) {
    val profile = auth.profile
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (profile == null) "Аккаунт" else "Профиль") },
        text = {
            if (profile == null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Вы не авторизованы.",
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
                                text = profile.nickname.ifBlank { "Пользователь" },
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
                            text = "Аккаунт заблокирован на сайте.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    if (profile.about.isNotBlank()) {
                        ProfileProperty(label = "О себе", value = profile.about)
                    }

                    ProfileProperty(
                        label = "Роли",
                        value = profile.roles.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Нет",
                    )
                    ProfileProperty(
                        label = "Уведомления",
                        value = profile.unreadNotifications.toString(),
                    )
                    ProfileProperty(
                        label = "Сообщения",
                        value = profile.unreadMessages.toString(),
                    )
                }
            }
        },
        confirmButton = {
            if (profile == null) {
                DialogActionRow {
                    DialogActionButton(
                        text = "Закрыть",
                        onClick = onDismiss,
                    )
                    DialogActionButton(
                        text = "Войти",
                        primary = true,
                        onClick = onOpenLogin,
                    )
                }
            } else {
                DialogActionRow {
                    DialogActionButton(
                        text = "ЛК",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(profile.siteProfileUrl(siteBaseUrl)),
                                    ),
                                )
                            }.onFailure {
                                Toast.makeText(context, "Не удалось открыть сайт", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    DialogActionButton(
                        text = "Закрыть",
                        onClick = onDismiss,
                    )
                    DialogActionButton(
                        text = "Выйти",
                        primary = true,
                        onClick = onLogout,
                    )
                }
            }
        },
    )
}

@Composable
private fun SettingsActionButton(onOpenSettings: () -> Unit) {
    IconButton(
        onClick = onOpenSettings,
        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
    ) {
        Icon(Icons.Default.Settings, contentDescription = "Настройки")
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
private fun SettingsDialog(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var qualityPickerOpen by remember { mutableStateOf(false) }
    var decoderPickerOpen by remember { mutableStateOf(false) }
    var domainsDialogOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SettingsSectionTitle("Плеер")
                SettingsActionRow(
                    title = "Качество по умолчанию",
                    value = settings.defaultQuality.title,
                    onClick = { qualityPickerOpen = true },
                )
                SettingsActionRow(
                    title = "Декодер",
                    value = settings.decoderMode.title,
                    onClick = { decoderPickerOpen = true },
                )
                SettingsSwitchRow(
                    title = "Автовоспроизведение следующей серии",
                    checked = settings.autoplayNextEpisode,
                    onCheckedChange = { onSettingsChange(settings.copy(autoplayNextEpisode = it)) },
                )

                SettingsActionRow(
                    title = "Домены сайта",
                    value = "${settings.siteDomains.size} доменов",
                    onClick = { domainsDialogOpen = true },
                )

                SettingsSectionTitle("О программе")
                ProfileProperty(
                    label = "Версия",
                    value = "${BuildConfig.VERSION_NAME} ${BuildConfig.BUILD_TYPE} (${BuildConfig.VERSION_CODE})",
                )
            }
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(
                    text = "Готово",
                    primary = true,
                    onClick = onDismiss,
                )
            }
        },
    )

    if (qualityPickerOpen) {
        SettingsPickerDialog(
            title = "Качество по умолчанию",
            options = PreferredQuality.entries,
            selected = settings.defaultQuality,
            optionTitle = { it.title },
            onSelected = {
                onSettingsChange(settings.copy(defaultQuality = it))
                qualityPickerOpen = false
            },
            onDismiss = { qualityPickerOpen = false },
        )
    }

    if (decoderPickerOpen) {
        SettingsPickerDialog(
            title = "Декодер",
            options = PlayerDecoderMode.entries,
            selected = settings.decoderMode,
            optionTitle = { it.title },
            onSelected = {
                onSettingsChange(settings.copy(decoderMode = it))
                decoderPickerOpen = false
            },
            onDismiss = { decoderPickerOpen = false },
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
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
    }
}

@Composable
private fun <T> SettingsPickerDialog(
    title: String,
    options: List<T>,
    selected: T,
    optionTitle: (T) -> String,
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
                    text = "Закрыть",
                    primary = true,
                    onClick = onDismiss,
                )
            }
        },
    )
}

@Composable
private fun SettingsDomainsDialog(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var newDomain by remember(settings.siteDomains) { mutableStateOf("") }
    var domainError by remember(settings.siteDomains) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Домены сайта (${settings.siteDomains.size})") },
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
                                    Icon(Icons.Default.Close, contentDescription = "Удалить домен")
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
                    label = { Text("Новый домен") },
                    isError = domainError != null,
                    supportingText = domainError?.let { message -> { Text(message) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRing(RoundedCornerShape(8.dp)),
                )
            }
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(
                    text = "Сбросить",
                    onClick = {
                        newDomain = ""
                        domainError = null
                        onSettingsChange(settings.copy(siteDomains = SiteDomainResolver.DEFAULT_SITE_DOMAINS))
                    },
                )
                DialogActionButton(
                    text = "Закрыть",
                    onClick = onDismiss,
                )
                DialogActionButton(
                    text = "Добавить",
                    primary = true,
                    onClick = {
                        val normalized = normalizeSiteBaseUrl(newDomain)
                        when {
                            normalized == null -> domainError = "Некорректный домен"
                            settings.siteDomains.any { it.trimEnd('/').equals(normalized.trimEnd('/'), ignoreCase = true) } ->
                                domainError = "Домен уже в списке"
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
private fun BrowseFilterBar(
    filters: BrowseFilters,
    onSortSelected: (AnimeSort) -> Unit,
    onStatusSelected: (AnimeStatusFilter) -> Unit,
    onGenreSelected: (AnimeGenreFilter) -> Unit,
    isWide: Boolean,
) {
    val horizontalPadding = if (isWide) 32.dp else 16.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = horizontalPadding, end = horizontalPadding, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChoiceRow(
            title = "Сортировка",
            filterItems = AnimeSort.entries,
            selected = filters.sort,
            itemLabel = { it.title },
            onSelected = onSortSelected,
        )
        FilterChoiceRow(
            title = "Статус",
            filterItems = AnimeStatusFilter.entries,
            selected = filters.status,
            itemLabel = { it.title },
            onSelected = onStatusSelected,
        )
        FilterChoiceRow(
            title = "Жанр",
            filterItems = AnimeGenreFilter.entries,
            selected = filters.genre,
            itemLabel = { it.title },
            onSelected = onGenreSelected,
        )
    }
}

@Composable
private fun <T> FilterChoiceRow(
    title: String,
    filterItems: List<T>,
    selected: T,
    itemLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 74.dp),
        )

        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 8.dp),
        ) {
            items(filterItems, key = { it.toString() }) { item ->
                FilterChip(
                    selected = item == selected,
                    onClick = { onSelected(item) },
                    label = {
                        Text(
                            text = itemLabel(item),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier
                        .widthIn(max = 190.dp)
                        .focusRing(RoundedCornerShape(8.dp)),
                )
            }
        }
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
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = anime.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.heightIn(min = 44.dp),
            )

            if (anime.meta.isNotBlank()) {
                Text(
                    text = anime.meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (anime.description.isNotBlank()) {
                Text(
                    text = anime.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DetailsScreenModern(
    state: YummyAniUiState,
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
    onSelectAnimeListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        DetailsStateContent(
            state = state.details,
            onRetry = onRefresh,
            emptyMessage = "Карточка не найдена",
        ) { details ->
            DetailsContentModern(
                details = details,
                videos = state.videos,
                selectedGroup = state.selectedVideoGroup,
                auth = state.auth,
                animeMark = state.animeMark,
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
                onSelectAnimeListMark = onSelectAnimeListMark,
                onToggleFavorite = onToggleFavorite,
                onRetry = onRefresh,
            )
        }
    }
}

@Composable
private fun DetailsContentModern(
    details: AnimeDetails,
    videos: LoadState<List<VideoVariant>>,
    selectedGroup: String?,
    auth: AuthUiState,
    animeMark: LoadState<UserAnimeMark?>,
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
    onSelectAnimeListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    onRetry: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isWide = configuration.screenWidthDp >= 900
    val useThreeColumnHero = configuration.screenWidthDp >= 1180
    val heroHeight = if (useThreeColumnHero) {
        (configuration.screenHeightDp * 0.50f).dp.coerceIn(340.dp, 430.dp)
    } else if (isWide) {
        (configuration.screenHeightDp * 0.54f).dp.coerceIn(360.dp, 500.dp)
    } else {
        if (configuration.screenWidthDp < 420) 620.dp else 560.dp
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        DetailsHeroModern(
            details = details,
            isWide = isWide,
            useThreeColumnHero = useThreeColumnHero,
            onBack = onBack,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight),
        )

        AnimeMarkPanelModern(
            auth = auth,
            animeMark = animeMark,
            onOpenLogin = onOpenLogin,
            onOpenProfile = onOpenProfile,
            onSelectListMark = onSelectAnimeListMark,
            onToggleFavorite = onToggleFavorite,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .fillMaxWidth(),
        )
        DetailsFactsSection(
            details = details,
            onGenreClick = onGenreFilterSelected,
            onYearClick = onYearFilterSelected,
            onStudioClick = onStudioFilterSelected,
            onCreatorClick = onCreatorFilterSelected,
        )
        DetailsDescriptionSection(details)
        DetailsScreenshotsSection(details.screenshots)
        DetailsRelatedAnimeSection(
            relatedAnime = details.relatedAnime,
            onOpenAnime = onOpenAnime,
        )

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
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DetailsHeroModern(
    details: AnimeDetails,
    isWide: Boolean,
    useThreeColumnHero: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
        details.backdropUrl?.let { backdrop ->
            PosterImage(
                url = backdrop,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.20f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FloatingHeroButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            FloatingHeroButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Обновить")
            }
        }

        if (useThreeColumnHero) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 32.dp, top = 76.dp, end = 32.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                DetailsPoster(
                    posterUrl = details.posterUrl,
                    title = details.title,
                    modifier = Modifier.width(168.dp),
                )
                DetailsHeroText(
                    details = details,
                    compact = false,
                    showGenres = false,
                    modifier = Modifier.weight(1f),
                )
            }
        } else if (isWide) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 32.dp, top = 76.dp, end = 32.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    DetailsPoster(
                        posterUrl = details.posterUrl,
                        title = details.title,
                        modifier = Modifier.width(190.dp),
                    )
                    DetailsHeroText(
                        details = details,
                        compact = false,
                        showGenres = false,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 64.dp),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 18.dp, top = 76.dp, end = 18.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = details.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    maxLines = 7,
                    overflow = TextOverflow.Clip,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    DetailsPoster(
                        posterUrl = details.posterUrl,
                        title = details.title,
                        modifier = Modifier.width(124.dp),
                    )
                    DetailsHeroMeta(
                        details = details,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingHeroButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(8.dp),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
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

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(22.dp))
                Text(
                    text = profile?.nickname ?: "Войти в аккаунт",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                if (profile == null) {
                    DialogActionButton(
                        text = "Войти",
                        primary = true,
                        onClick = onOpenLogin,
                        enabled = !auth.loading,
                    )
                } else {
                    DialogActionButton(
                        text = "Профиль",
                        onClick = onOpenProfile,
                    )
                }
            }

            if (profile != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    UserAnimeListMark.displayOrder.forEach { listMark ->
                        FilterChip(
                            selected = mark.list == listMark,
                            onClick = { onSelectListMark(listMark) },
                            leadingIcon = {
                                Icon(
                                    imageVector = listMark.icon(),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            label = {
                                Text(
                                    text = listMark.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            modifier = Modifier
                                .widthIn(max = 170.dp)
                                .focusRing(RoundedCornerShape(8.dp)),
                        )
                    }
                    FilterChip(
                        selected = mark.isFavorite,
                        onClick = onToggleFavorite,
                        leadingIcon = {
                            Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        label = {
                            Text(
                                text = "Любимые",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier
                            .widthIn(max = 170.dp)
                            .focusRing(RoundedCornerShape(8.dp)),
                    )
                }

                when (animeMark) {
                    LoadState.Loading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Синхронизация",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is LoadState.Error -> Text(
                        text = animeMark.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    is LoadState.Ready -> Unit
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnimeMarkPanel(
    auth: AuthUiState,
    animeMark: LoadState<UserAnimeMark?>,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    onSelectListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(22.dp))
                Text(
                    text = auth.profile?.nickname ?: "Войти в аккаунт",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                if (auth.profile == null) {
                    DialogActionButton(
                        text = "Войти",
                        primary = true,
                        onClick = onOpenLogin,
                        enabled = !auth.loading,
                    )
                } else {
                    DialogActionButton(
                        text = "Выйти",
                        onClick = onOpenProfile,
                    )
                }
            }

            if (auth.profile != null) {
                when (animeMark) {
                    LoadState.Loading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Синхронизация",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is LoadState.Error -> Text(
                        text = animeMark.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    is LoadState.Ready -> {
                        val mark = animeMark.data ?: UserAnimeMark()
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            UserAnimeListMark.displayOrder.forEach { listMark ->
                                FilterChip(
                                    selected = mark.list == listMark,
                                    onClick = { onSelectListMark(listMark) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = listMark.icon(),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = listMark.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    modifier = Modifier
                                        .widthIn(max = 170.dp)
                                        .focusRing(RoundedCornerShape(8.dp)),
                                )
                            }
                            FilterChip(
                                selected = mark.isFavorite,
                                onClick = onToggleFavorite,
                                leadingIcon = {
                                    Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                label = {
                                    Text(
                                        text = "Любимые",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                modifier = Modifier
                                    .widthIn(max = 170.dp)
                                    .focusRing(RoundedCornerShape(8.dp)),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun UserProfile.siteProfileUrl(siteBaseUrl: String): String {
    val base = siteBaseUrl.trim().ifBlank { "https://old.yummyani.me/" }.trimEnd('/')
    return "$base/users/id$id"
}

private fun sitePageUrl(siteBaseUrl: String, path: String): String {
    val base = siteBaseUrl.trim().ifBlank { "https://old.yummyani.me/" }.trimEnd('/')
    return "$base/${path.trim().trimStart('/')}"
}

@Composable
private fun DetailsHeroMeta(
    details: AnimeDetails,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (details.meta.isNotBlank()) {
            Text(
                text = details.meta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (compact) 4 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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

        if (details.episodeSummary.isNotBlank()) {
            Text(
                text = details.episodeSummary,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun DetailsHeroText(
    details: AnimeDetails,
    compact: Boolean,
    modifier: Modifier = Modifier,
    showGenres: Boolean = true,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = details.title,
            style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            maxLines = if (compact) 6 else 5,
            overflow = TextOverflow.Clip,
        )

        if (details.meta.isNotBlank()) {
            Text(
                text = details.meta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        if (details.episodeSummary.isNotBlank()) {
            Text(
                text = details.episodeSummary,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
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
    val facts = buildList<Pair<String, String>> {
        add("Тип" to details.type)
        add("Возрастной рейтинг" to details.minAge)
        add("Статус" to details.status)
        details.year?.let { add("Год выхода" to it.toString()) }
        if (details.otherTitles.isNotEmpty()) add("Другие названия" to details.otherTitles.take(4).joinToString(" | "))
        details.original.takeIf { it.isNotBlank() }?.let { add("Первоисточник" to it) }
        if (details.studios.isNotEmpty()) add("Студия" to details.studios.joinToString { it.title })
        if (details.creators.isNotEmpty()) add("Режиссёр" to details.creators.joinToString { it.title })
        if (details.genreTags.isNotEmpty()) add("Жанры" to details.genreTags.joinToString { it.title })
        details.nextEpisodeText.takeIf { it.isNotBlank() }?.let { add("До выхода серии" to it) }
        details.durationSeconds.takeIf { it > 0 }?.let { seconds ->
            formatDuration(seconds)?.let { add("Длительность" to it) }
        }
        details.commentsCount.takeIf { it > 0L }?.let { add("Комментарии" to formatViews(it)) }
        details.listsCount.takeIf { it > 0L }?.let { add("В списках" to formatViews(it)) }
    }.filter { (_, value) -> value.isNotBlank() }

    if (facts.isEmpty() && details.ratingDetails == RatingDetails()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DetailsRatingStrip(details.ratingDetails, details.views)

        facts.forEach { (label, value) ->
            DetailsFactRow(label = label) {
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
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsRatingStrip(
    ratingDetails: RatingDetails,
    views: Long,
) {
    val entries = buildList {
        ratingDetails.average?.let { add("★" to formatRating(it)) }
        if (views > 0L) add("Просм." to formatViews(views))
        if (ratingDetails.counters > 0L) add("Оценок" to formatViews(ratingDetails.counters))
        ratingDetails.myAnimeList?.let { add("MAL" to formatRating(it)) }
        ratingDetails.shikimori?.let { add("Шики" to formatRating(it)) }
        ratingDetails.kinopoisk?.let { add("КП" to formatRating(it)) }
        ratingDetails.worldArt?.let { add("WA" to formatRating(it)) }
        ratingDetails.aniDub?.let { add("AniDUB" to formatRating(it)) }
    }
    if (entries.isEmpty()) return

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(entries) { (label, value) ->
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        text = "$label $value",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun DetailsFactRow(
    label: String,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = 128.dp, max = 220.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            AssistChip(
                onClick = { onClick(option) },
                label = {
                    Text(
                        text = option.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
            )
        }
    }
}

@Composable
private fun DetailsDescriptionSection(details: AnimeDetails) {
    val description = details.description.ifBlank { "Описание пока не добавлено." }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Описание",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DetailsScreenshotsSection(screenshots: List<String>) {
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
            text = "Кадры",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(visibleScreenshots, key = { it }) { screenshot ->
                val index = visibleScreenshots.indexOf(screenshot)
                val shape = RoundedCornerShape(8.dp)
                PosterImage(
                    url = screenshot,
                    contentDescription = null,
                    modifier = Modifier
                        .width(320.dp)
                        .aspectRatio(16f / 9f)
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
        )
    }
}

@Composable
private fun ScreenshotViewerDialog(
    screenshots: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    if (screenshots.isEmpty()) return
    var currentIndex by remember(screenshots, initialIndex) {
        mutableIntStateOf(initialIndex.coerceIn(0, screenshots.lastIndex))
    }
    var scale by remember(currentIndex) { mutableFloatStateOf(1f) }
    var offset by remember(currentIndex) { mutableStateOf(Offset.Zero) }
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .navigationBarsPadding(),
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
                    .pointerInput(currentIndex) {
                        detectTransformGestures { _, pan, zoom, _ ->
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
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${currentIndex + 1} / ${screenshots.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.56f), RoundedCornerShape(50))
                        .focusRing(RoundedCornerShape(50)),
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (currentIndex > 0) {
                    Button(
                        onClick = { currentIndex -= 1 },
                        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
                    ) {
                        Text("Пред.")
                    }
                }
                if (currentIndex < screenshots.lastIndex) {
                    Button(
                        onClick = { currentIndex += 1 },
                        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
                    ) {
                        Text("След.")
                    }
                }
            }
        }
    }
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
                    text = "Порядок просмотра",
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

private fun UserAnimeListMark.icon() = when (this) {
    UserAnimeListMark.Watching -> Icons.Default.RemoveRedEye
    UserAnimeListMark.Planned -> Icons.Default.Cloud
    UserAnimeListMark.Watched -> Icons.Default.Flag
    UserAnimeListMark.Postponed -> Icons.Default.Schedule
    UserAnimeListMark.Dropped -> Icons.Default.VisibilityOff
}

@Composable
private fun VideoPickerModern(
    videos: List<VideoVariant>,
    selectedGroup: String?,
    onSelectGroup: (String) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (videos.isEmpty()) {
        EmptyPane(
            message = "Видео для этого аниме пока нет",
            modifier = modifier.heightIn(min = 180.dp),
        )
        return
    }

    val configuration = LocalConfiguration.current
    val columns = when {
        configuration.screenWidthDp >= 1180 -> 4
        configuration.screenWidthDp >= 760 -> 3
        configuration.screenWidthDp >= 560 -> 2
        else -> 1
    }
    val groups = videos.groupBy { it.groupKey }
    val selectedKey = selectedGroup?.takeIf(groups::containsKey) ?: groups.keys.first()
    val selectedVideos = groups.getValue(selectedKey).sortedWith(
        compareBy<VideoVariant> { it.index }.thenBy { it.episode.toDoubleOrNull() ?: 0.0 },
    )

    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        selectedVideos.chunked(columns).forEach { rowVideos ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                rowVideos.forEach { video ->
                    EpisodeCard(
                        video = video,
                        onClick = { onPlayVideo(video) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - rowVideos.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DetailsScreen(
    state: YummyAniUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectVideoGroup: (String) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        DetailsTopBar(
            title = (state.details as? LoadState.Ready)?.data?.title ?: "Аниме",
            onBack = onBack,
            onRefresh = onRefresh,
        )

        Box(modifier = Modifier.weight(1f)) {
            DetailsStateContent(
                state = state.details,
                onRetry = onRefresh,
                emptyMessage = "Карточка не найдена",
            ) { details ->
                DetailsContent(
                    details = details,
                    videos = state.videos,
                    selectedGroup = state.selectedVideoGroup,
                    onSelectVideoGroup = onSelectVideoGroup,
                    onPlayVideo = onPlayVideo,
                    onRetry = onRefresh,
                )
            }
        }
    }
}

@Composable
private fun DetailsTopBar(
    title: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        IconButton(
            onClick = onRefresh,
            modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Обновить")
        }
    }
}

@Composable
private fun DetailsContent(
    details: AnimeDetails,
    videos: LoadState<List<VideoVariant>>,
    selectedGroup: String?,
    onSelectVideoGroup: (String) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onRetry: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isWide = configuration.screenWidthDp >= 840
    val heroHeight = if (isWide) {
        (configuration.screenHeightDp * 0.5f).dp.coerceIn(240.dp, 300.dp)
    } else {
        300.dp
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        DetailsHero(
            details = details,
            isWide = isWide,
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight),
        )

        when (videos) {
            LoadState.Loading -> LoadingPane(Modifier.weight(1f))
            is LoadState.Error -> ErrorPane(
                message = videos.message,
                onRetry = onRetry,
                modifier = Modifier.weight(1f),
            )
            is LoadState.Ready -> VideoPicker(
                videos = videos.data,
                selectedGroup = selectedGroup,
                onSelectGroup = onSelectVideoGroup,
                onPlayVideo = onPlayVideo,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DetailsHero(
    details: AnimeDetails,
    isWide: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background),
    ) {
        details.backdropUrl?.let { backdrop ->
            PosterImage(
                url = backdrop,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.42f),
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            )
        }

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                DetailsPoster(
                    posterUrl = details.posterUrl,
                    title = details.title,
                    modifier = Modifier.width(150.dp),
                )
                DetailsText(details = details, modifier = Modifier.weight(1f))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    DetailsPoster(details.posterUrl, details.title, modifier = Modifier.width(124.dp))
                    DetailsText(details = details, modifier = Modifier.weight(1f), compact = true)
                }
            }
        }
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
private fun DetailsText(
    details: AnimeDetails,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = details.title,
            style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            maxLines = if (compact) 3 else 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (details.meta.isNotBlank()) {
            Text(
                text = details.meta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        if (!compact && details.genres.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(details.genres.take(10)) { genre ->
                    AssistChip(onClick = {}, label = { Text(genre) })
                }
            }
        }

        if (details.episodeSummary.isNotBlank()) {
            Text(
                text = details.episodeSummary,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        Text(
            text = details.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (compact) 4 else 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.verticalScroll(rememberScrollState()),
        )
    }
}

@Composable
private fun VideoPicker(
    videos: List<VideoVariant>,
    selectedGroup: String?,
    onSelectGroup: (String) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (videos.isEmpty()) {
        EmptyPane(
            message = "Видео для этого аниме пока нет",
            modifier = modifier,
        )
        return
    }

    val groups = videos.groupBy { it.groupKey }
    val groupEntries = groups.entries.toList()
    val selectedKey = selectedGroup?.takeIf(groups::containsKey) ?: groups.keys.first()
    val selectedVideos = groups.getValue(selectedKey).sortedWith(
        compareBy<VideoVariant> { it.index }.thenBy { it.episode.toDoubleOrNull() ?: 0.0 },
    )
    val episodeGridState = rememberLazyGridState()

    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            items(groupEntries, key = { it.key }) { entry ->
                val representative = entry.value.first()
                FilterChip(
                    selected = entry.key == selectedKey,
                    onClick = { onSelectGroup(entry.key) },
                    label = {
                        Text(
                            text = representative.groupTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .focusRing(RoundedCornerShape(8.dp)),
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 178.dp),
            state = episodeGridState,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(selectedVideos, key = { _, video -> video.id }) { _, video ->
                EpisodeCard(
                    video = video,
                    onClick = { onPlayVideo(video) },
                )
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    video: VideoVariant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .dpadClickable(RoundedCornerShape(8.dp), onClick),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(22.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
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
                    text = listOfNotNull(formatDuration(video.durationSeconds), formatViews(video.views))
                        .joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val PLAYER_CONTROLS_AUTO_HIDE_MS = 4_000L
private const val VOICE_MENU_GROUP_ID = 19
private const val QUALITY_MENU_GROUP_ID = 20
private const val PIP_ENTER_DELAY_MS = 120L

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
    onSelectGroup: (String) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    onRetry: () -> Unit,
    onPlaybackFailed: (VideoVariant, Long) -> Unit,
    onPlaybackStarted: (VideoVariant) -> Unit,
    canUsePictureInPicture: Boolean,
    onEnterPictureInPicture: () -> Unit,
    onBack: () -> Unit,
    onRegisterPlayerInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
) {
    val videos = allVideos.ifEmpty { listOf(video) }
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
            LoadState.Loading -> LoadingPane(Modifier.fillMaxSize())
            is LoadState.Error -> PlayerErrorPane(
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
                onSelectGroup = { groupKey, replacement, positionMs ->
                    onSelectGroup(groupKey)
                    replacement?.let { onPlayVideoAt(it, positionMs) }
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
                canUsePictureInPicture = canUsePictureInPicture,
                isInPictureInPicture = isInPictureInPicture,
                onEnterPictureInPicture = onEnterPictureInPicture,
                onBack = onBack,
                onRegisterPlayerInputActionHandler = onRegisterPlayerInputActionHandler,
                modifier = Modifier.fillMaxSize(),
            )
        }

    }
}

@Composable
private fun PlayerControlsSurface(
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    currentVideo: VideoVariant,
    previousVideo: VideoVariant?,
    nextVideo: VideoVariant?,
    onSelectGroup: (String, VideoVariant?) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    modifier: Modifier = Modifier,
) {
    var voiceDialogOpen by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PlayerEpisodeButton(
                    text = "Пред.",
                    enabled = previousVideo != null,
                    onClick = { previousVideo?.let(onPlayVideo) },
                )
                PlayerEpisodeButton(
                    text = "След.",
                    enabled = nextVideo != null,
                    onClick = { nextVideo?.let(onPlayVideo) },
                )
                if (groups.size > 1) {
                    Button(
                        onClick = { voiceDialogOpen = true },
                        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
                    ) {
                        Text("Озвучка")
                    }
                }
                Text(
                    text = currentVideo.episodeTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = selectedKey?.let { groups[it]?.firstOrNull()?.groupTitle }.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (voiceDialogOpen) {
        PlayerVoiceDialog(
            groups = groups,
            selectedKey = selectedKey,
            currentVideo = currentVideo,
            onSelectGroup = { groupKey, replacement ->
                voiceDialogOpen = false
                onSelectGroup(groupKey, replacement)
            },
            onDismiss = { voiceDialogOpen = false },
        )
    }
}

@Composable
private fun PlayerEpisodeButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
    ) {
        Text(text)
    }
}

@Composable
private fun PlayerVoiceDialog(
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    currentVideo: VideoVariant,
    onSelectGroup: (String, VideoVariant?) -> Unit,
    onDismiss: () -> Unit,
) {
    if (groups.size <= 1) return

    val entries = remember(groups) {
        groups.entries.sortedBy { it.value.firstOrNull()?.groupTitle.orEmpty() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Озвучка и плеер") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(entries, key = { it.key }) { entry ->
                    val sortedVideos = entry.value.sortedForPlayer()
                    val replacement = sortedVideos.firstOrNull { it.sameEpisodeSlot(currentVideo) }
                        ?: sortedVideos.firstOrNull()
                    val representative = entry.value.first()
                    SelectableFilterRow(
                        title = representative.groupTitle,
                        selected = entry.key == selectedKey,
                        onClick = { onSelectGroup(entry.key, replacement) },
                    )
                }
            }
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(
                    text = "Закрыть",
                    primary = true,
                    onClick = onDismiss,
                )
            }
        },
    )
}

private fun List<VideoVariant>.sortedForPlayer(): List<VideoVariant> {
    return sortedWith(
        compareBy<VideoVariant> { it.index }
            .thenBy { it.episode.toDoubleOrNull() ?: 0.0 }
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
                Text("Еще раз")
            }
        }
    }
}

private fun List<VideoVariant>.sortedForPlayer(preferredGroupKey: String?): List<VideoVariant> {
    return groupBy { it.episodeSlotKey }
        .values
        .mapNotNull { variants ->
            variants.minWithOrNull(
                compareBy<VideoVariant> { if (it.groupKey == preferredGroupKey) 0 else 1 }
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
    get() = dubbing.cleanVideoLabel("Озвучка").ifBlank { player.cleanVideoLabel("Плеер") }.ifBlank { "Озвучка" }

private val VideoVariant.voiceKey: String
    get() = voiceTitle.trim().lowercase(Locale.ROOT)

private val VideoVariant.episodeSlotKey: String
    get() = episode.takeIf { it.isNotBlank() } ?: "index:$index"

private fun VideoVariant.playbackSubtitle(): String {
    val voice = dubbing.cleanVideoLabel("Озвучка")
    return listOf(voice, episodeTitle)
        .filterNot { it.isNullOrBlank() }
        .joinToString(" • ")
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

@Composable
private fun PlayerErrorPane(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogActionButton(text = "Назад", onClick = onBack)
                DialogActionButton(text = "Повторить", primary = true, onClick = onRetry)
            }
        }
    }
}

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
    onSelectGroup: (String, VideoVariant?, Long) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    onPlaybackFailed: (VideoVariant, Long) -> Unit,
    onPlaybackStarted: (VideoVariant) -> Unit,
    canUsePictureInPicture: Boolean,
    isInPictureInPicture: Boolean,
    onEnterPictureInPicture: () -> Unit,
    onBack: () -> Unit,
    onRegisterPlayerInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var desiredVolume by remember { mutableFloatStateOf(1f) }
    val currentSettings by rememberUpdatedState(settings)
    val httpClient = remember {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
    val trackSelector = remember(context) {
        DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                .setMaxVideoBitrate(Int.MAX_VALUE)
                .build()
        }
    }
    val renderersFactory = remember(context, settings.decoderMode) {
        DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(settings.decoderMode.mediaCodecSelector())
    }
    val player = remember(stream.url, stream.headers, startPositionMs, httpClient, trackSelector, renderersFactory) {
        AppLog.w("YummyAniPlayer", "Stream headers=${stream.headers.keys.sorted()}")
        val userAgent = stream.headers["User-Agent"] ?: "YummyAnime Android TV"
        val dataSourceFactory = OkHttpDataSource.Factory(httpClient)
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(stream.headers)
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
                volume = desiredVolume
                setMediaItem(mediaItemBuilder.build(), startPositionMs.coerceAtLeast(0L))
                playWhenReady = true
                prepare()
            }
    }
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    val qualityOptions = remember(tracks) { tracks.videoQualityOptions() }
    var selectedQualityKey by remember(stream.url) { mutableStateOf<String?>(null) }
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

            override fun play() {
                player.play()
            }

            override fun pause() {
                player.pause()
            }

            override fun setPictureInPictureMode(enabled: Boolean) {
                playerView?.applyPictureInPictureControllerMode(enabled)
            }

            override fun hideAppControls() {
                playerView?.hideController()
            }
        }
    }

    LaunchedEffect(qualityOptions) {
        if (selectedQualityKey != null && qualityOptions.none { it.key == selectedQualityKey }) {
            selectedQualityKey = null
        }
    }

    LaunchedEffect(qualityOptions, settings.defaultQuality, stream.url) {
        val preferredOption = qualityOptions.preferredOption(settings.defaultQuality)
        if (preferredOption != null && selectedQualityKey != preferredOption.key) {
            player.selectQuality(preferredOption)
            selectedQualityKey = preferredOption.key
            playerView?.findViewById<TextView>(R.id.yummy_player_quality)
                ?.setTag(R.id.yummy_player_quality, preferredOption.key)
        }
    }

    DisposableEffect(player) {
        var fallbackReported = false
        var autoAdvanceReported = false
        var playbackStartedReported = false
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
                    "YummyAniPlayer",
                    "Available qualities=$qualityLabels, sourceMax=${stream.maxVideoHeight ?: 0}, " +
                        "source=${currentVideo.groupTitle}",
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
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
                        "YummyAniPlayer",
                        "Playback HTTP ${httpError.responseCode}: host=${uri.host}, file=${uri.lastPathSegment}, headers=${httpError.headerFields.keys}",
                    )
                } else {
                    AppLog.w("YummyAniPlayer", "Playback failed: ${error.errorCodeName}", error)
                }
                if (!fallbackReported) {
                    fallbackReported = true
                    onPlaybackFailed(currentVideo, player.currentPosition.coerceAtLeast(0L))
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            PlayerPipController.unregisterPlayer(pipPlayerHandle)
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
                    groups = groups,
                    selectedKey = selectedKey,
                    previousVideo = previousVideo,
                    nextVideo = nextVideo,
                    qualityOptions = qualityOptions,
                    selectedQualityKey = selectedQualityKey,
                    onSelectedQualityKeyChange = { selectedQualityKey = it },
                    onSelectGroup = onSelectGroup,
                    onPlayVideo = onPlayVideo,
                    onPlayVideoAt = onPlayVideoAt,
                    onVolumeChange = { desiredVolume = it },
                    canUsePictureInPicture = canUsePictureInPicture,
                    onEnterPictureInPicture = onEnterPictureInPicture,
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
        InputAction.Left,
        InputAction.Right,
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
private fun PlayerView.bindYummyController(
    player: ExoPlayer,
    animeTitle: String,
    currentVideo: VideoVariant,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    previousVideo: VideoVariant?,
    nextVideo: VideoVariant?,
    qualityOptions: List<QualityOption>,
    selectedQualityKey: String?,
    onSelectedQualityKeyChange: (String) -> Unit,
    onSelectGroup: (String, VideoVariant?, Long) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    canUsePictureInPicture: Boolean,
    onEnterPictureInPicture: () -> Unit,
    onBack: () -> Unit,
) {
    findViewById<TextView>(R.id.yummy_player_title)?.text = animeTitle.ifBlank { "Просмотр" }
    findViewById<TextView>(R.id.yummy_player_subtitle)?.text =
        currentVideo.playbackSubtitle()
    findViewById<TextView>(R.id.yummy_player_info)?.text =
        currentVideo.player.cleanVideoLabel("Плеер")

    findViewById<View>(Media3R.id.exo_settings)?.visibility = View.GONE
    findViewById<View>(R.id.yummy_player_back)?.setOnClickListener { onBack() }

    findViewById<TextView>(R.id.yummy_episode_previous)?.apply {
        visibility = if (previousVideo != null) View.VISIBLE else View.GONE
        setOnClickListener {
            previousVideo?.let {
                showVoiceFallbackToast(context, currentVideo, it)
                player.pause()
                onPlayVideoAt(it, 0L)
            }
        }
    }

    findViewById<TextView>(R.id.yummy_episode_next)?.apply {
        visibility = if (nextVideo != null) View.VISIBLE else View.GONE
        setOnClickListener {
            nextVideo?.let {
                showVoiceFallbackToast(context, currentVideo, it)
                player.pause()
                onPlayVideoAt(it, 0L)
            }
        }
    }
    configurePlayerFocusNavigation(previousVideo != null, nextVideo != null)

    findViewById<TextView>(R.id.yummy_player_voice)?.apply {
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
        visibility = if (qualityOptions.isNotEmpty()) View.VISIBLE else View.GONE
        setOnClickListener {
            showController()
            showQualityPopup(
                anchor = this,
                player = player,
                options = qualityOptions,
                selectedQualityKey = selectedQualityKey,
                onSelectedQualityKeyChange = onSelectedQualityKeyChange,
            )
        }
    }

    findViewById<TextView>(R.id.yummy_player_volume)?.apply {
        setOnClickListener {
            showController()
            showVolumePopup(
                anchor = this,
                player = player,
                onVolumeChange = onVolumeChange,
            )
        }
    }

    findViewById<TextView>(R.id.yummy_player_pip)?.apply {
        visibility = if (canUsePictureInPicture) View.VISIBLE else View.GONE
        setOnClickListener {
            hideController()
            postDelayed({ onEnterPictureInPicture() }, PIP_ENTER_DELAY_MS)
        }
    }
}

private fun PlayerView.configurePlayerFocusNavigation(
    hasPreviousVideo: Boolean,
    hasNextVideo: Boolean,
) {
    val back = findViewById<View>(R.id.yummy_player_back)
    val previous = findViewById<View>(R.id.yummy_episode_previous)
    val playPause = findViewById<View>(Media3R.id.exo_play_pause)
    val next = findViewById<View>(R.id.yummy_episode_next)
    val bottomControls = listOfNotNull(
        findViewById<View>(R.id.yummy_player_voice)?.takeIf { it.visibility == View.VISIBLE },
        findViewById<View>(R.id.yummy_player_quality)?.takeIf { it.visibility == View.VISIBLE },
        findViewById<View>(R.id.yummy_player_volume)?.takeIf { it.visibility == View.VISIBLE },
        findViewById<View>(R.id.yummy_player_pip)?.takeIf { it.visibility == View.VISIBLE },
    )
    val firstBottomControl = bottomControls.firstOrNull()
    val lastBottomControl = bottomControls.lastOrNull()

    playPause?.apply {
        nextFocusLeftId = if (hasPreviousVideo) R.id.yummy_episode_previous else id
        nextFocusRightId = if (hasNextVideo) R.id.yummy_episode_next else id
        nextFocusUpId = R.id.yummy_player_back
        nextFocusDownId = firstBottomControl?.id ?: id
    }

    previous?.apply {
        nextFocusLeftId = id
        nextFocusRightId = Media3R.id.exo_play_pause
        nextFocusUpId = R.id.yummy_player_back
        nextFocusDownId = firstBottomControl?.id ?: Media3R.id.exo_play_pause
    }

    next?.apply {
        nextFocusLeftId = Media3R.id.exo_play_pause
        nextFocusRightId = id
        nextFocusUpId = R.id.yummy_player_back
        nextFocusDownId = lastBottomControl?.id ?: Media3R.id.exo_play_pause
    }

    back?.nextFocusDownId = Media3R.id.exo_play_pause

    bottomControls.forEachIndexed { index, view ->
        view.nextFocusUpId = Media3R.id.exo_play_pause
        view.nextFocusLeftId = bottomControls.getOrNull(index - 1)?.id ?: Media3R.id.exo_play_pause
        view.nextFocusRightId = bottomControls.getOrNull(index + 1)?.id ?: view.id
    }
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
            onSelectGroup(replacement?.groupKey ?: entry.value.firstOrNull()?.groupKey ?: entry.key, replacement)
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
) {
    PopupMenu(anchor.context, anchor).apply {
        val effectiveSelectedQualityKey = anchor.getTag(R.id.yummy_player_quality) as? String
            ?: selectedQualityKey
            ?: player.currentQualityKey()
        options.forEachIndexed { index, option ->
            menu.add(QUALITY_MENU_GROUP_ID, index, index, option.label).apply {
                isCheckable = true
                isChecked = option.key == effectiveSelectedQualityKey
            }
        }
        menu.setGroupCheckable(QUALITY_MENU_GROUP_ID, true, true)
        setOnMenuItemClickListener { item ->
            val option = options.getOrNull(item.itemId) ?: return@setOnMenuItemClickListener false
            player.selectQuality(option)
            anchor.setTag(R.id.yummy_player_quality, option.key)
            onSelectedQualityKeyChange(option.key)
            true
        }
        show()
    }
}

@OptIn(UnstableApi::class)
private fun ExoPlayer.selectQuality(option: QualityOption) {
    trackSelectionParameters = trackSelectionParameters
        .buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
        .setMaxVideoBitrate(Int.MAX_VALUE)
        .addOverride(TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndex))
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

private fun showVolumePopup(
    anchor: View,
    player: ExoPlayer,
    onVolumeChange: (Float) -> Unit,
) {
    val context = anchor.context
    val density = context.resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).roundToInt()

    val label = TextView(context).apply {
        setTextColor(android.graphics.Color.WHITE)
        textSize = 16f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val seekBar = SeekBar(context).apply {
        max = 100
        progress = (player.volume * 100f).roundToInt().coerceIn(0, 100)
        isFocusable = true
    }

    fun applyProgress(progress: Int) {
        val safeProgress = progress.coerceIn(0, 100)
        val volume = safeProgress / 100f
        label.text = context.getString(R.string.volume_percent, safeProgress)
        player.volume = volume
        onVolumeChange(volume)
    }

    applyProgress(seekBar.progress)
    seekBar.setOnSeekBarChangeListener(
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(view: SeekBar?, progress: Int, fromUser: Boolean) {
                applyProgress(progress)
            }

            override fun onStartTrackingTouch(view: SeekBar?) = Unit

            override fun onStopTrackingTouch(view: SeekBar?) = Unit
        },
    )

    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(14), dp(18), dp(14))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(8).toFloat()
            setColor(android.graphics.Color.rgb(45, 45, 45))
        }
        addView(
            label,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        addView(
            seekBar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(10)
            },
        )
    }

    PopupWindow(container, dp(320), ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
        isOutsideTouchable = true
        setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        elevation = dp(8).toFloat()
        showAtLocation(anchor.rootView, Gravity.BOTTOM or Gravity.END, dp(18), dp(88))
    }
}

private data class QualityOption(
    val group: Tracks.Group,
    val trackIndex: Int,
    val label: String,
    val height: Int,
    val bitrate: Int,
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
                Text("Повторить")
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

private fun formatViews(views: Long): String {
    return when {
        views >= 10_000_000 -> String.format(Locale.US, "%.0f млн", views / 1_000_000.0)
        views >= 1_000_000 -> String.format(Locale.US, "%.1f млн", views / 1_000_000.0)
        views >= 100_000 -> String.format(Locale.US, "%.0f тыс", views / 1_000.0)
        views >= 1_000 -> String.format(Locale.US, "%.1f тыс", views / 1_000.0)
        else -> "$views"
    }
}

