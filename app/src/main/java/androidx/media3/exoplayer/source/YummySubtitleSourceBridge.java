package androidx.media3.exoplayer.source;

import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;

/** Access to Media3 1.10.1's lazy single-track setup, which has no public factory API. */
@UnstableApi
public final class YummySubtitleSourceBridge {
  private YummySubtitleSourceBridge() {}

  public static ProgressiveMediaSource.Factory enableLazyTrack(
      ProgressiveMediaSource.Factory factory, int trackId, Format format) {
    return factory.enableLazyLoadingWithSingleTrack(trackId, format);
  }
}
