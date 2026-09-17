Synthetic 180-second testsrc2 H264 640x360/25fps video plus 440Hz mono AAC audio.
Created locally for this repository; no provider content, credentials or third-party media.
Size approximately 7.94 MB. JVM real-time tests consume encoded samples with fake outputs;
Android instrumentation tests package this same asset and decode its video/audio normally.

Regenerate using FFmpeg:
ffmpeg -f lavfi -i testsrc2=size=640x360:rate=25:duration=180 -f lavfi -i sine=frequency=440:sample_rate=48000:duration=180 -c:v libx264 -preset ultrafast -b:v 300k -maxrate 300k -bufsize 600k -g 50 -pix_fmt yuv420p -c:a aac -b:a 48k -movflags +faststart -map_metadata -1 av-180s.mp4

Maximum-preset fixture: 540 seconds, 320x180/25fps H264 100 kbit/s plus AAC48k.
10,326,132 bytes, 13,500 video frames and 25,314 AAC frames. Same synthetic provenance.
The reduced video bitrate keeps the self-contained test asset under 10 MiB.
ffmpeg -f lavfi -i testsrc2=size=320x180:rate=25:duration=540 -f lavfi -i sine=frequency=440:sample_rate=48000:duration=540 -c:v libx264 -preset ultrafast -b:v 100k -maxrate 100k -bufsize 200k -g 50 -pix_fmt yuv420p -c:a aac -b:a 48k -movflags +faststart -map_metadata -1 av-540s.mp4
