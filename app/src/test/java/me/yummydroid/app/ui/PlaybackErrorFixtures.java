package me.yummydroid.app.ui;

import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo;
import java.io.IOException;
import java.util.Collections;

/** Policy-only fixtures: event metadata is never accessed and needs no Android runtime. */
final class PlaybackErrorFixtures {
    static LoadErrorInfo info(IOException error, int count) {
        return new LoadErrorInfo(null, null, error, count);
    }

    static HttpDataSource.InvalidResponseCodeException httpError(int code) {
        return new HttpDataSource.InvalidResponseCodeException(
                code, "test", null, Collections.emptyMap(), null, new byte[0]);
    }
}
