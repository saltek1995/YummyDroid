package me.yummydroid.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class YummyRadiiTest {
    @Test
    fun radiusTokensAndDerivedShapesStayAligned() {
        assertEquals(8.dp, YummyRadii.small)
        assertEquals(12.dp, YummyRadii.medium)
        assertEquals(50.dp, YummyRadii.pill)
        assertEquals(RoundedCornerShape(8.dp), YummyRadii.smallShape)
        assertEquals(RoundedCornerShape(12.dp), YummyRadii.mediumShape)
        assertEquals(RoundedCornerShape(50.dp), YummyRadii.pillShape)
    }
}
