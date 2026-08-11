package me.yummydroid.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.data.AnimeDetails

@Composable
internal fun DetailsHeroHeading(
    details: AnimeDetails,
    compact: Boolean,
    isWide: Boolean,
    detailsExtras: LoadState<AnimeDetailsExtras>,
    auth: AuthUiState,
    showHeroRating: Boolean,
    onSetAnimeRating: (Int?) -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    heroFocusGridState: VisualFocusGridState?,
) {
    val compactHeading = compact || !isWide
    Text(
        text = details.title,
        style = when {
            !isWide -> MaterialTheme.typography.headlineSmall
            compact -> MaterialTheme.typography.headlineMedium
            else -> MaterialTheme.typography.displaySmall
        },
        fontWeight = FontWeight.Black,
    )
    DetailsHeroAlternateTitles(details = details, compact = compactHeading)
    DetailsHeroRatingAndStats(
        details = details,
        detailsExtras = detailsExtras,
        auth = auth,
        showHeroRating = showHeroRating,
        onSetAnimeRating = onSetAnimeRating,
        onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
        heroFocusGridState = heroFocusGridState,
        compact = compactHeading,
    )
}
