package me.yummydroid.app

import android.view.Menu
import androidx.annotation.OptIn
import androidx.media3.cast.MediaRouteButtonFactory
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.framework.media.widget.ExpandedControllerActivity

@OptIn(UnstableApi::class)
class YummyCastExpandedControlsActivity : ExpandedControllerActivity() {
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.cast_expanded_controller, menu)
        MediaRouteButtonFactory.setUpMediaRouteButton(this, menu, R.id.cast_media_route)
        return true
    }
}
