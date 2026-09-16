Synthetic test-only audio; no provider/site media or credentials.

Input: ../cvh-20s-aac.m4a, a locally generated 20-second 440 Hz sine wave.
See CvhBufferingIntegrationTest for the deterministic input generation command.

Regenerate with FFmpeg from the repository root:
ffmpeg -i app/src/test/resources/media/cvh-20s-aac.m4a -c:a copy -f hls -hls_time 2 -hls_playlist_type vod -hls_segment_type fmp4 -hls_segment_filename app/src/test/resources/media/provider-hls/segment%03d.m4s -fflags +bitexact -map_metadata -1 app/src/test/resources/media/provider-hls/index.m3u8

The playlist, initialization fragment and segments are synthetic fixtures created
for this repository and carry no third-party media/content licensing restriction.
