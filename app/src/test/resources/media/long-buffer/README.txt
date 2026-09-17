Synthetic 120-second 440 Hz sine wave, AAC mono 32 kbit/s, for Standard 35s/70s
buffer tests. No provider/site content, credentials or third-party media.
MP4 plus HLS copies total approximately 1 MB. Created solely for this repository.

From this directory, regenerate with local FFmpeg:
ffmpeg -f lavfi -i sine=frequency=440:sample_rate=44100:duration=120 -c:a aac -b:a 32k -movflags +faststart -fflags +bitexact -flags:a +bitexact -map_metadata -1 audio.m4a
ffmpeg -i audio.m4a -c:a copy -f hls -hls_time 10 -hls_playlist_type vod -hls_segment_type fmp4 -hls_segment_filename segment%03d.m4s -fflags +bitexact -map_metadata -1 index.m3u8
