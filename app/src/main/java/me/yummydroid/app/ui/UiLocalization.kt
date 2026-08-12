package me.yummydroid.app.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.R
import me.yummydroid.app.data.AnimeSort
import me.yummydroid.app.data.ContentLanguage
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.PlayerBufferPreset
import me.yummydroid.app.data.PlayerDecoderMode
import me.yummydroid.app.data.PosterCardSize
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.formatByteSize
import me.yummydroid.app.formatCompactCount
import me.yummydroid.app.localizedString

// UiLocalization
internal val LocalUiLanguage = staticCompositionLocalOf { ContentLanguage.Russian }

// UiNumberLocalization
private data class CompactCountSuffixes(
    val thousand: String,
    val million: String,
)

private val CompactCountSuffixCache = mutableMapOf<ContentLanguage, CompactCountSuffixes>()

@Composable
internal fun localizedPluralWord(
    count: Long,
    one: UiStringKey,
    few: UiStringKey,
    many: UiStringKey,
): String {
    val normalized = kotlin.math.abs(count)
    val mod100 = normalized % 100
    val mod10 = normalized % 10
    val key = when {
        mod100 in 11..14 -> many
        mod10 == 1L -> one
        mod10 in 2L..4L -> few
        else -> many
    }
    return uiText(key)
}

@Composable
internal fun localizedEpisodesWord(count: Int): String {
    return localizedPluralWord(
        count = count.toLong(),
        one = UiStringKey.EpisodeOne,
        few = UiStringKey.EpisodeFew,
        many = UiStringKey.EpisodeMany,
    )
}

@Composable
internal fun localizedVotesWord(count: Long): String {
    return localizedPluralWord(
        count = count,
        one = UiStringKey.VoteOne,
        few = UiStringKey.VoteFew,
        many = UiStringKey.VoteMany,
    )
}

@Composable
internal fun localizedViews(value: Long): String {
    val context = LocalContext.current
    val language = LocalUiLanguage.current
    val suffixes = remember(context, language) {
        compactCountSuffixes(context, language)
    }
    return remember(language, value, suffixes) {
        formatCompactCount(
            value = value,
            thousandSuffix = suffixes.thousand,
            millionSuffix = suffixes.million,
        )
    }
}

private fun compactCountSuffixes(
    context: android.content.Context,
    language: ContentLanguage,
): CompactCountSuffixes {
    synchronized(CompactCountSuffixCache) {
        CompactCountSuffixCache[language]?.let { return it }
    }
    val created = CompactCountSuffixes(
        thousand = context.localizedString(R.string.ui_number_thousand_suffix, language),
        million = context.localizedString(R.string.ui_number_million_suffix, language),
    )
    synchronized(CompactCountSuffixCache) {
        return CompactCountSuffixCache.getOrPut(language) { created }
    }
}

@Composable
internal fun localizedByteSize(bytes: Long): String {
    val context = LocalContext.current
    val language = LocalUiLanguage.current
    return remember(context, language, bytes) {
        formatByteSize(
            bytes = bytes,
            byteUnit = context.localizedString(R.string.ui_unit_byte, language),
            kilobyteUnit = context.localizedString(R.string.ui_unit_kilobyte, language),
            megabyteUnit = context.localizedString(R.string.ui_unit_megabyte, language),
            gigabyteUnit = context.localizedString(R.string.ui_unit_gigabyte, language),
        )
    }
}

// UiOptionLocalization
@Composable
internal fun BrowseSection.localizedTitle(): String = uiText(
    when (this) {
        BrowseSection.Catalog -> UiStringKey.BrowseCatalog
        BrowseSection.Schedule -> UiStringKey.BrowseSchedule
        BrowseSection.History -> UiStringKey.BrowseHistory
        BrowseSection.Downloads -> UiStringKey.BrowseDownloads
    },
)

@Composable
internal fun PreferredQuality.localizedTitle(): String = when (this) {
    PreferredQuality.Auto -> uiText(UiStringKey.Auto)
    else -> title
}

@Composable
internal fun PlayerDecoderMode.localizedTitle(): String = when (this) {
    PlayerDecoderMode.Auto -> uiText(UiStringKey.Auto)
    PlayerDecoderMode.Hardware -> uiText(UiStringKey.Hardware)
    PlayerDecoderMode.Software -> uiText(UiStringKey.Software)
}

@Composable
internal fun PlayerBufferPreset.localizedTitle(): String = when (this) {
    PlayerBufferPreset.Compact -> uiText(UiStringKey.Compact)
    PlayerBufferPreset.Standard -> uiText(UiStringKey.Standard)
    PlayerBufferPreset.Large -> uiText(UiStringKey.Large)
    PlayerBufferPreset.Maximum -> uiText(UiStringKey.Maximum)
}

@Composable
internal fun PosterCardSize.localizedTitle(): String = when (this) {
    PosterCardSize.Compact -> uiText(UiStringKey.Compact)
    PosterCardSize.Standard -> uiText(UiStringKey.Standard)
    PosterCardSize.Large -> uiText(UiStringKey.Large)
}

@Composable
internal fun ContentLanguage.localizedTitle(): String = when (this) {
    ContentLanguage.Russian -> uiText(UiStringKey.LanguageRussian)
    ContentLanguage.English -> uiText(UiStringKey.LanguageEnglish)
    ContentLanguage.Ukrainian -> uiText(UiStringKey.LanguageUkrainian)
}

internal fun UserAnimeListMark.localizedTitleKey(): UiStringKey = when (this) {
    UserAnimeListMark.Watching -> UiStringKey.Watching
    UserAnimeListMark.Planned -> UiStringKey.Planned
    UserAnimeListMark.Watched -> UiStringKey.Watched
    UserAnimeListMark.Postponed -> UiStringKey.Postponed
    UserAnimeListMark.Dropped -> UiStringKey.Dropped
}

@Composable
internal fun UserAnimeListMark.localizedTitle(): String = uiText(localizedTitleKey())

@Composable
internal fun AnimeSort.localizedTitle(): String = uiText(
    when (this) {
        AnimeSort.Rating -> UiStringKey.Rating5709e2
        AnimeSort.RatingCounters -> UiStringKey.Votes
        AnimeSort.Views -> UiStringKey.Views
        AnimeSort.Year -> UiStringKey.New
        AnimeSort.Top -> UiStringKey.Top
        AnimeSort.Title -> UiStringKey.AZ
        AnimeSort.Id -> UiStringKey.RecentlyAdded
        AnimeSort.Random -> UiStringKey.Random
    },
)

@Composable
internal fun FilterOption.localizedTitle(): String = when (value) {
    "released" -> uiText(UiStringKey.Released)
    "ongoing" -> uiText(UiStringKey.Ongoing)
    "announcement" -> uiText(UiStringKey.Announcements)
    "winter" -> uiText(UiStringKey.Winter)
    "spring" -> uiText(UiStringKey.Spring)
    "summer" -> uiText(UiStringKey.Summer)
    "fall" -> uiText(UiStringKey.Fall)
    "dubbing" -> uiText(UiStringKey.FullDubbing)
    "multivoice" -> uiText(UiStringKey.MultiVoice)
    "duet" -> uiText(UiStringKey.TwoVoice)
    "onevoice" -> uiText(UiStringKey.SingleVoice)
    "subtitles" -> uiText(UiStringKey.Subtitles)
    "0" -> uiText(UiStringKey.Watching)
    "1" -> uiText(UiStringKey.Planned)
    "2" -> uiText(UiStringKey.Watched)
    "3" -> uiText(UiStringKey.Dropped)
    "4" -> uiText(UiStringKey.Favorites)
    "5" -> uiText(UiStringKey.Postponed)
    else -> title
}

internal fun ContentLanguage.voiceRecognizerTag(): String = when (this) {
    ContentLanguage.Russian -> "ru-RU"
    ContentLanguage.English -> "en-US"
    ContentLanguage.Ukrainian -> "uk-UA"
}

internal fun ContentLanguage.uiLocale(): Locale {
    return locale
}

// UiStringKeys
internal enum class UiStringKey(
    @param:StringRes val resId: Int,
) {
    About(R.string.ui_about),
    About312416(R.string.ui_about_312416),
    Account(R.string.ui_account),
    ActiveCount(R.string.ui_active_count),
    Add(R.string.ui_add),
    Added(R.string.ui_added),
    AdvancedMode(R.string.ui_advanced_mode),
    Age(R.string.ui_age),
    All(R.string.ui_all),
    AllDownloadedVariants(R.string.ui_all_downloaded_variants),
    AllEpisodes(R.string.ui_all_episodes),
    AllEf8ff2(R.string.ui_all_ef8ff2),
    AllAvailableEpisodesAreAlreadyDownloaded(R.string.ui_all_available_episodes_are_already_downloaded),
    AllSelectedEpisodesAreAlreadyDownloaded(R.string.ui_all_selected_episodes_are_already_downloaded),
    AlreadyDownloaded(R.string.ui_already_downloaded),
    AlreadyDownloadedEpisodesWithTheSameQualityWillBeSkipped(R.string.ui_already_downloaded_episodes_with_the_same_quality_will_be_skipped),
    Anime(R.string.ui_anime),
    AnimeCardNotFound(R.string.ui_anime_card_not_found),
    AnimeReleaseOrder(R.string.ui_anime_release_order),
    Announcements(R.string.ui_announcements),
    AppAndContentLanguage(R.string.ui_app_and_content_language),
    AppNotifications(R.string.ui_app_notifications),
    Apply(R.string.ui_apply),
    Auto(R.string.ui_auto),
    AutomaticMarks(R.string.ui_automatic_marks),
    AutoplayNextEpisode(R.string.ui_autoplay_next_episode),
    AvailableOffline(R.string.ui_available_offline),
    Back(R.string.ui_back),
    AgeRating(R.string.ui_age_rating),
    BrowseCatalog(R.string.ui_browse_catalog),
    BrowseDownloads(R.string.ui_browse_downloads),
    BrowseHistory(R.string.ui_browse_history),
    BrowseSchedule(R.string.ui_browse_schedule),
    BufferSize(R.string.ui_buffer_size),
    Cancel(R.string.ui_cancel),
    CancelDownload(R.string.ui_cancel_download),
    Cancelled(R.string.ui_cancelled),
    CacheSize(R.string.ui_cache_size),
    CardSize(R.string.ui_card_size),
    InterfaceScale(R.string.ui_interface_scale),
    CatalogAndAppearance(R.string.ui_catalog_and_appearance),
    CatalogIsEmpty(R.string.ui_catalog_is_empty),
    Check(R.string.ui_check),
    CheckUpdatesOnStartup(R.string.ui_check_updates_on_startup),
    CheckingAvailableQuality(R.string.ui_checking_available_quality),
    CheckingQuality(R.string.ui_checking_quality),
    ChooseVoice(R.string.ui_choose_voice),
    Clear(R.string.ui_clear),
    ClearCache(R.string.ui_clear_cache),
    Close(R.string.ui_close),
    CollectingVoicesAndRanges(R.string.ui_collecting_voices_and_ranges),
    Comment(R.string.ui_comment),
    Comments(R.string.ui_comments),
    Compact(R.string.ui_compact),
    Continue(R.string.ui_continue),
    ContinueWatching(R.string.ui_continue_watching),
    CouldNotOpenTheSite(R.string.ui_could_not_open_the_site),
    Decoder(R.string.ui_decoder),
    DefaultQuality(R.string.ui_default_quality),
    Delete(R.string.ui_delete),
    DeleteAnime(R.string.ui_delete_anime),
    DeleteDownloadedEpisode(R.string.ui_delete_downloaded_episode),
    DeleteEpisode(R.string.ui_delete_episode),
    DeleteWatchProgressForAllEpisodesOfThisAnime(R.string.ui_delete_watch_progress_for_all_episodes_of_this_anime),
    Director(R.string.ui_director),
    Disable(R.string.ui_disable),
    DomainIsAlreadyInTheList(R.string.ui_domain_is_already_in_the_list),
    Domains(R.string.ui_domains),
    Done(R.string.ui_done),
    Download(R.string.ui_download),
    DownloadChannelDescription(R.string.ui_download_channel_description),
    DownloadChannelName(R.string.ui_download_channel_name),
    DownloadEpisode(R.string.ui_download_episode),
    DownloadMissingEpisodesOnly(R.string.ui_download_missing_episodes_only),
    DownloadNetworkWaiting(R.string.ui_download_network_waiting),
    DownloadNetworkWaitingUnmetered(R.string.ui_download_network_waiting_unmetered),
    DownloadNotificationIdleText(R.string.ui_download_notification_idle_text),
    DownloadNotificationIdleTitle(R.string.ui_download_notification_idle_title),
    DownloadNotificationProgress(R.string.ui_download_notification_progress),
    DownloadNotificationTitle(R.string.ui_download_notification_title),
    DownloadOverMobileData(R.string.ui_download_over_mobile_data),
    DownloadPlan(R.string.ui_download_plan),
    DownloadPlanCompleted(R.string.ui_download_plan_completed),
    DownloadPlanCompletedWithErrors(R.string.ui_download_plan_completed_with_errors),
    DownloadPlanFailed(R.string.ui_download_plan_failed),
    DownloadPlanLoading(R.string.ui_download_plan_loading),
    DownloadPlans(R.string.ui_download_plans),
    DownloadQueue(R.string.ui_download_queue),
    DownloadRetryMessage(R.string.ui_download_retry_message),
    DownloadSpeedLimit(R.string.ui_download_speed_limit),
    DownloadSpeedLimitWarning(R.string.ui_download_speed_limit_warning),
    DownloadSpeedMegabytesPerSecond(R.string.ui_download_speed_megabytes_per_second),
    DownloadStartFailed(R.string.ui_download_start_failed),
    DownloadStopped(R.string.ui_download_stopped),
    DownloadThreads(R.string.ui_download_threads),
    Downloaded(R.string.ui_downloaded),
    DownloadedBc4f6a(R.string.ui_downloaded_bc4f6a),
    DownloadedEpisodes(R.string.ui_downloaded_episodes),
    DownloadedEpisodesCachedAnimeCardsAndLocalPlaybackProgressWillBeDeletedAccountAn(R.string.ui_downloaded_episodes_cached_anime_cards_and_local_playback_progress_wil),
    DownloadedFae287(R.string.ui_downloaded_fae287),
    Downloads(R.string.ui_downloads),
    Dropped(R.string.ui_dropped),
    Duration(R.string.ui_duration),
    Email(R.string.ui_email),
    Empty(R.string.ui_empty),
    Episode(R.string.ui_episode),
    Episode4da919(R.string.ui_episode_4da919),
    EpisodeAlreadyDownloaded(R.string.ui_episode_already_downloaded),
    EpisodeFew(R.string.ui_episode_few),
    EpisodeCount(R.string.ui_episode_count),
    EpisodeNumberInvalid(R.string.ui_episode_number_invalid),
    EpisodeRangeInvalid(R.string.ui_episode_range_invalid),
    EpisodeIsAlreadyOut(R.string.ui_episode_is_already_out),
    EpisodeMany(R.string.ui_episode_many),
    EpisodeOne(R.string.ui_episode_one),
    Episodes(R.string.ui_episodes),
    Error(R.string.ui_error),
    ExcludeGenres(R.string.ui_exclude_genres),
    ExcludeMarks(R.string.ui_exclude_marks),
    ExcludedByRanges(R.string.ui_excluded_by_ranges),
    Fall(R.string.ui_fall),
    Favorites(R.string.ui_favorites),
    Filters(R.string.ui_filters),
    FindAnime(R.string.ui_find_anime),
    FixEpisodeRanges(R.string.ui_fix_episode_ranges),
    ForgotPassword(R.string.ui_forgot_password),
    From(R.string.ui_from),
    FromDba126(R.string.ui_from_dba126),
    FromStart(R.string.ui_from_start),
    FullDubbing(R.string.ui_full_dubbing),
    Genres(R.string.ui_genres),
    HasSubtitles(R.string.ui_has_subtitles),
    Hardware(R.string.ui_hardware),
    HistoryIsEmpty(R.string.ui_history_is_empty),
    InvalidDomain(R.string.ui_invalid_domain),
    LanguageEnglish(R.string.ui_language_english),
    LanguageRussian(R.string.ui_language_russian),
    LanguageUkrainian(R.string.ui_language_ukrainian),
    Large(R.string.ui_large),
    Library(R.string.ui_library),
    Loaded(R.string.ui_loaded),
    Loading(R.string.ui_loading),
    MarkAsWatchedAfterFinalEpisode(R.string.ui_mark_as_watched_after_final_episode),
    MarkAsWatchingOnPlayback(R.string.ui_mark_as_watching_on_playback),
    MarkAllRead(R.string.ui_mark_all_read),
    MarkRead(R.string.ui_mark_read),
    Marks(R.string.ui_marks),
    MatchDisplayToVideo(R.string.ui_match_display_to_video),
    Maximum(R.string.ui_maximum),
    Messages(R.string.ui_messages),
    MoveDown(R.string.ui_move_down),
    MoveUp(R.string.ui_move_up),
    MultiVoice(R.string.ui_multi_voice),
    Network(R.string.ui_network),
    NewDomain(R.string.ui_new_domain),
    New(R.string.ui_sort_new),
    Next(R.string.ui_next),
    Next6ff11d(R.string.ui_next_6ff11d),
    No(R.string.ui_no),
    NoAvailableQualityFoundForSelectedVoices(R.string.ui_no_available_quality_found_for_selected_voices),
    NoDownloadedEpisodesYet(R.string.ui_no_downloaded_episodes_yet),
    NoItemsMatchTheSelectedFilters(R.string.ui_no_items_match_the_selected_filters),
    NoNotifications(R.string.ui_no_notifications),
    NoQualitiesAreAvailableForTheSelectedVoice(R.string.ui_no_qualities_are_available_for_the_selected_voice),
    NoReleaseNotesYet(R.string.ui_no_release_notes_yet),
    NoSubscriptions(R.string.ui_no_subscriptions),
    NoUpcomingReleasesYet(R.string.ui_no_upcoming_releases_yet),
    NoVideosForThisAnimeYet(R.string.ui_no_videos_for_this_anime_yet),
    NoEpisodes(R.string.ui_no_episodes),
    NoEpisodesToDownload(R.string.ui_no_episodes_to_download),
    NoVoicesAreAvailableForDownload(R.string.ui_no_voices_are_available_for_download),
    NotAvailableInSelectedVoices(R.string.ui_not_available_in_selected_voices),
    NothingFound(R.string.ui_nothing_found),
    Notifications(R.string.ui_notifications),
    Of(R.string.ui_of),
    Off(R.string.ui_off),
    Offline(R.string.ui_offline),
    OfflineOnlyDownloadedAnimeAreShown(R.string.ui_offline_only_downloaded_anime_are_shown),
    Ongoing(R.string.ui_ongoing),
    OngoingSchedule(R.string.ui_ongoing_schedule),
    Password(R.string.ui_password),
    Pause(R.string.ui_pause),
    Paused(R.string.ui_paused),
    Planned(R.string.ui_planned),
    Playback(R.string.ui_playback),
    Postponed(R.string.ui_postponed),
    PreparingDownloadPlan(R.string.ui_preparing_download_plan),
    Previous(R.string.ui_previous),
    Profile(R.string.ui_profile),
    ProfileEb0b9b(R.string.ui_profile_eb0b9b),
    Quality(R.string.ui_quality),
    QualityCheckFailed(R.string.ui_quality_check_failed),
    QualityNotFound(R.string.ui_quality_not_found),
    Queued(R.string.ui_queued),
    RateAnime(R.string.ui_rate_anime),
    Rating(R.string.ui_rating),
    Rating5709e2(R.string.ui_rating_5709e2),
    Random(R.string.ui_sort_random),
    RecentlyAdded(R.string.ui_sort_recently_added),
    Refresh(R.string.ui_refresh),
    Released(R.string.ui_released),
    RemoveDomain(R.string.ui_remove_domain),
    Reset(R.string.ui_reset),
    ResetWatchProgress(R.string.ui_reset_watch_progress),
    ResumeDownload(R.string.ui_resume_download),
    Retry(R.string.ui_retry),
    Roles(R.string.ui_roles),
    S(R.string.ui_s),
    SavedPosition(R.string.ui_saved_position),
    ScheduleIsEmpty(R.string.ui_schedule_is_empty),
    Search(R.string.ui_search),
    SearchingQualityOptions(R.string.ui_searching_quality_options),
    Season(R.string.ui_season),
    Selected(R.string.ui_selected),
    SelectedQualityIsUnavailable(R.string.ui_selected_quality_is_unavailable),
    Send(R.string.ui_send),
    Settings(R.string.ui_settings),
    SettingsDownloadsAndStorage(R.string.ui_settings_downloads_and_storage),
    SettingsInterfaceAndCatalog(R.string.ui_settings_interface_and_catalog),
    SettingsNetworkAndUpdates(R.string.ui_settings_network_and_updates),
    SettingsPlayer(R.string.ui_settings_player),
    SettingsViewingStatuses(R.string.ui_settings_viewing_statuses),
    SignIn(R.string.ui_sign_in),
    SignIn07205a(R.string.ui_sign_in_07205a),
    SignOut(R.string.ui_sign_out),
    SignUp(R.string.ui_sign_up),
    Similar(R.string.ui_similar),
    SingleVoice(R.string.ui_single_voice),
    SiteDomains(R.string.ui_site_domains),
    Skip(R.string.ui_skip),
    SkipOPED(R.string.ui_skip_op_ed),
    Software(R.string.ui_software),
    SomeSourcesDidNotRespond(R.string.ui_some_sources_did_not_respond),
    Sorting(R.string.ui_sorting),
    AZ(R.string.ui_sort_a_z),
    Top(R.string.ui_sort_top),
    Views(R.string.ui_sort_views),
    Votes(R.string.ui_sort_votes),
    Source(R.string.ui_source),
    Spring(R.string.ui_spring),
    Standard(R.string.ui_standard),
    Status(R.string.ui_status),
    Storage(R.string.ui_storage),
    Studio(R.string.ui_studio),
    Subscribed(R.string.ui_subscribed),
    Subscription(R.string.ui_subscription),
    Subscriptions(R.string.ui_subscriptions),
    Subtitles(R.string.ui_subtitles),
    Summer(R.string.ui_summer),
    TheAccountIsBlockedOnTheSite(R.string.ui_the_account_is_blocked_on_the_site),
    TheLatestVersionIsInstalled(R.string.ui_the_latest_version_is_installed),
    TheUpdateCheckHasNotBeenRunYet(R.string.ui_the_update_check_has_not_been_run_yet),
    To(R.string.ui_to),
    To7618b0(R.string.ui_to_7618b0),
    ToQueue(R.string.ui_to_queue),
    TotalEpisodes(R.string.ui_total_episodes),
    TryAgain(R.string.ui_try_again),
    TwoVoice(R.string.ui_two_voice),
    Type(R.string.ui_type),
    Updates(R.string.ui_updates),
    User(R.string.ui_user),
    Version(R.string.ui_version),
    VideosCardsAndProgress(R.string.ui_videos_cards_and_progress),
    Voice(R.string.ui_voice),
    VoiceHasNoEpisodes(R.string.ui_voice_has_no_episodes),
    VoiceSearch(R.string.ui_voice_search),
    VoiceSearchIsNotAvailableOnThisDevice(R.string.ui_voice_search_is_not_available_on_this_device),
    VoicesAndPriority(R.string.ui_voices_and_priority),
    VoteFew(R.string.ui_vote_few),
    VoteMany(R.string.ui_vote_many),
    VoteOne(R.string.ui_vote_one),
    Watch(R.string.ui_watch),
    Watch5af041(R.string.ui_watch_5af041),
    Watched(R.string.ui_watched),
    Watching(R.string.ui_watching),
    WhatShouldIFind(R.string.ui_what_should_i_find),
    Winter(R.string.ui_winter),
    Year(R.string.ui_year),
    Year92264e(R.string.ui_year_92264e),
    YouAreNotSignedIn(R.string.ui_you_are_not_signed_in);
}

// UiText
@Composable
internal fun uiText(key: UiStringKey): String {
    val context = LocalContext.current
    val language = LocalUiLanguage.current
    return remember(context, language, key) {
        context.localizedString(key.resId, language)
    }
}

@Composable
internal fun uiText(key: UiStringKey, vararg formatArgs: Any): String {
    val context = LocalContext.current
    val language = LocalUiLanguage.current
    return remember(context, language, key, formatArgs.contentHashCode()) {
        context.localizedString(key.resId, language, *formatArgs)
    }
}
