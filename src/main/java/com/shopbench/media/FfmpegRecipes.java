package com.shopbench.media;

import java.util.List;

/** 视频转码在生产里交给 FFmpeg。这里保留面试要讲的命令，不在演示里解码 H.264。 */
public final class FfmpegRecipes {
    private FfmpegRecipes() {}

    public static List<String> transcode(String input, String output) {
        return List.of("ffmpeg", "-y", "-i", input, "-c:v", "libx264", "-c:a", "aac", "-movflags", "+faststart", output);
    }

    public static List<String> thumbnail(String input, String output) {
        return List.of("ffmpeg", "-y", "-ss", "1", "-i", input, "-frames:v", "1", output);
    }

    public static List<String> extractWav(String input, String output) {
        return List.of("ffmpeg", "-y", "-i", input, "-vn", "-ac", "1", "-ar", "16000", "-c:a", "pcm_s16le", output);
    }

    public static List<String> trim(String input, String start, String duration, String output) {
        return List.of("ffmpeg", "-y", "-ss", start, "-i", input, "-t", duration, "-c", "copy", output);
    }
}
