package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.yummydroid.app.R
import me.yummydroid.app.data.RatingDetails
import me.yummydroid.app.formatRating

internal data class ExternalRatingDisplay(
    val iconResId: Int,
    val iconSize: Dp,
    val title: String,
    val value: String,
)

internal fun detailsHeroExternalRatingDisplays(ratingDetails: RatingDetails): List<ExternalRatingDisplay> {
    return buildList {
        ratingDetails.worldArt?.let {
            add(ExternalRatingDisplay(R.drawable.ic_rating_world_art, 26.dp, "World Art", formatRating(it)))
        }
        ratingDetails.kinopoisk?.let {
            add(ExternalRatingDisplay(R.drawable.ic_rating_kinopoisk, 18.dp, "Kinopoisk", formatRating(it)))
        }
        ratingDetails.shikimori?.let {
            add(ExternalRatingDisplay(R.drawable.ic_rating_shikimori, 18.dp, "Shikimori", formatRating(it)))
        }
        ratingDetails.myAnimeList?.let {
            add(ExternalRatingDisplay(R.drawable.ic_rating_mal, 33.dp, "MyAnimeList", formatRating(it)))
        }
        ratingDetails.aniDub?.let {
            add(ExternalRatingDisplay(R.drawable.ic_rating_anilibria, 20.dp, "Anilibria", formatRating(it)))
        }
    }
}

@Composable
internal fun HeroExternalRatingBadges(ratingDetails: RatingDetails) {
    detailsHeroExternalRatingDisplays(ratingDetails).forEach { entry ->
        HeroExternalRatingItem(entry = entry)
    }
}

@Composable
private fun HeroExternalRatingItem(entry: ExternalRatingDisplay) {
    val textStyle = MaterialTheme.typography.titleMedium.withoutFontPadding()
    Row(
        modifier = Modifier.height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            painter = painterResource(id = entry.iconResId),
            contentDescription = entry.title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(entry.iconSize),
        )
        Text(
            text = entry.value,
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

internal fun RatingDetails.hasExternalRatings(): Boolean {
    return detailsHeroExternalRatingDisplays(this).isNotEmpty()
}
