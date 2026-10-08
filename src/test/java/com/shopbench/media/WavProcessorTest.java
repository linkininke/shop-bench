package com.shopbench.media;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WavProcessorTest {
    @Test
    void sineHasExpectedDurationAndLoudness() {
        byte[] wav = WavProcessor.sine(440, 1.0, 0.5);
        WavProcessor.WavInfo info = WavProcessor.analyze(wav);
        assertEquals(16000, info.sampleRate());
        assertEquals(1.0, info.durationSec(), 0.02);
        assertTrue(info.rmsDb() > -10 && info.rmsDb() < -8);
    }

    @Test
    void gainLowersLoudness() {
        byte[] wav = WavProcessor.sine(440, 0.5, 0.5);
        double before = WavProcessor.analyze(wav).rmsDb();
        double after = WavProcessor.analyze(WavProcessor.applyGain(wav, 0.5)).rmsDb();
        assertTrue(after < before - 5);
    }

    @Test
    void trimShortensDuration() {
        byte[] wav = WavProcessor.sine(440, 1.0, 0.5);
        double duration = WavProcessor.analyze(WavProcessor.trim(wav, 0.2, 0.4)).durationSec();
        assertEquals(0.4, duration, 0.02);
    }

    @Test
    void recipeExtractsPcmWav() {
        var cmd = FfmpegRecipes.extractWav("ad.mp4", "voice.wav");
        assertTrue(cmd.contains("pcm_s16le"));
        assertTrue(cmd.contains("ffmpeg"));
    }
}
