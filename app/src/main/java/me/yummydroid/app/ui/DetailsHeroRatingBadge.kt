package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.yummydroid.app.formatRating
import me.yummydroid.app.ui.components.dpadClickable

@Composable
internal fun HeroRatingBadge(
    rating: Double,
    enabled: Boolean,
    onClick: () -> Unit,
    heroFocusGridState: VisualFocusGridState?,
) {
    val shape = RoundedCornerShape(8.dp)
    val ratingColor = ratingColorForSiteScale(rating)
    val textStyle = MaterialTheme.typography.headlineSmall.withoutFontPadding()
    val focusModifier = if (enabled && heroFocusGridState != null) {
        Modifier.visualFocusGridItem(
            state = heroFocusGridState,
            index = DetailsHeroFocusIndex.RatingBadge,
            horizontal = true,
            vertical = true,
            blockKey = DetailsFocusBlockKey.HeroStats,
            blockEntryIndex = DetailsHeroFocusIndex.RatingBadge,
        )
    } else {
        Modifier
    }
    Surface(
        modifier = Modifier
            .then(focusModifier)
            .then(if (enabled) Modifier.dpadClickable(shape, onClick) else Modifier),
        shape = shape,
        color = Color.Transparent,
        contentColor = ratingColor,
    ) {
        Row(
            modifier = Modifier.height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(25.dp))
            Text(
                text = formatRating(rating),
                style = textStyle,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

internal fun ratingColorForSiteScale(rating: Double): Color = when {
    rating < 5.0 -> Color(0xFFFF6666)
    rating < 7.0 -> Color(0xFFF2B800)
    else -> Color(0xFF3CCE7B)
}
