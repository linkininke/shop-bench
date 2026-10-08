package com.shopbench.media;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** 16-bit 小端 PCM WAV。演示解析头、响度、增益、淡入淡出和混音。 */
public final class WavProcessor {
    private WavProcessor() {}

    public static byte[] sine(double freqHz, double seconds, double amplitude) {
        int rate = 16000;
        int n = Math.max(1, (int) Math.round(rate * seconds));
        byte[] pcm = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            double sample = amplitude * Math.sin(2 * Math.PI * freqHz * i / rate);
            writeSample(pcm, i, sample);
        }
        return wrap(pcm, rate);
    }

    public static WavInfo analyze(byte[] wav) {
        Pcm pcm = decode(wav);
        double sum = 0;
        int peak = 0;
        for (int i = 0; i < pcm.samples.length; i++) {
            int v = pcm.samples[i];
            sum += (double) v * v;
            peak = Math.max(peak, Math.abs(v));
        }
        double rms = Math.sqrt(sum / pcm.samples.length) / 32768.0;
        double db = rms <= 1e-9 ? -120 : 20 * Math.log10(rms);
        double duration = pcm.samples.length / (double) pcm.sampleRate;
        return new WavInfo(pcm.sampleRate, 1, 16, duration, round(db), round(peak / 32768.0));
    }

    public static byte[] applyGain(byte[] wav, double gain) {
        Pcm pcm = decode(wav);
        byte[] out = new byte[pcm.samples.length * 2];
        for (int i = 0; i < pcm.samples.length; i++) {
            writeSample(out, i, (pcm.samples[i] / 32768.0) * gain);
        }
        return wrap(out, pcm.sampleRate);
    }

    public static byte[] fade(byte[] wav, double fadeInSec, double fadeOutSec) {
        Pcm pcm = decode(wav);
        int inN = Math.min(pcm.samples.length, (int) Math.round(fadeInSec * pcm.sampleRate));
        int outN = Math.min(pcm.samples.length, (int) Math.round(fadeOutSec * pcm.sampleRate));
        byte[] raw = new byte[pcm.samples.length * 2];
        for (int i = 0; i < pcm.samples.length; i++) {
            double env = 1;
            if (inN > 0 && i < inN) {
                env *= (double) i / inN;
            }
            int fromEnd = pcm.samples.length - 1 - i;
            if (outN > 0 && fromEnd < outN) {
                env *= (double) fromEnd / outN;
            }
            writeSample(raw, i, (pcm.samples[i] / 32768.0) * env);
        }
        return wrap(raw, pcm.sampleRate);
    }

    public static byte[] trim(byte[] wav, double startSec, double durationSec) {
        Pcm pcm = decode(wav);
        int from = Math.max(0, (int) Math.round(startSec * pcm.sampleRate));
        int length = Math.max(1, (int) Math.round(durationSec * pcm.sampleRate));
        if (from >= pcm.samples.length) {
            throw new IllegalArgumentException("裁剪起点已经超过音频结尾");
        }
        int to = Math.min(pcm.samples.length, from + length);
        byte[] raw = new byte[(to - from) * 2];
        for (int i = from; i < to; i++) {
            writeSample(raw, i - from, pcm.samples[i] / 32768.0);
        }
        return wrap(raw, pcm.sampleRate);
    }

    public static byte[] mix(byte[] a, byte[] b, double gainB) {
        Pcm left = decode(a);
        Pcm right = decode(b);
        if (left.sampleRate != right.sampleRate) {
            throw new IllegalArgumentException("两条音频采样率不同，生产环境要先重采样");
        }
        int n = Math.max(left.samples.length, right.samples.length);
        byte[] raw = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            double s = sampleAt(left, i) + sampleAt(right, i) * gainB;
            writeSample(raw, i, s);
        }
        return wrap(raw, left.sampleRate);
    }

    private static double sampleAt(Pcm pcm, int i) {
        return i < pcm.samples.length ? pcm.samples[i] / 32768.0 : 0;
    }

    private static void writeSample(byte[] pcm, int index, double unit) {
        int v = (int) Math.round(Math.max(-1, Math.min(1, unit)) * 32767);
        pcm[index * 2] = (byte) (v & 0xff);
        pcm[index * 2 + 1] = (byte) ((v >> 8) & 0xff);
    }

    private static byte[] wrap(byte[] pcm, int rate) {
        int dataLen = pcm.length;
        ByteBuffer buf = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(36 + dataLen);
        buf.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buf.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(16);
        buf.putShort((short) 1);
        buf.putShort((short) 1);
        buf.putInt(rate);
        buf.putInt(rate * 2);
        buf.putShort((short) 2);
        buf.putShort((short) 16);
        buf.put("data".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(dataLen);
        buf.put(pcm);
        return buf.array();
    }

    static Pcm decode(byte[] wav) {
        if (wav == null || wav.length < 44) {
            throw new IllegalArgumentException("不是有效的 WAV");
        }
        if (!Arrays.equals(wav, 0, 4, "RIFF".getBytes(StandardCharsets.US_ASCII), 0, 4)
                || !Arrays.equals(wav, 8, 12, "WAVE".getBytes(StandardCharsets.US_ASCII), 0, 4)) {
            throw new IllegalArgumentException("只接受 PCM WAV");
        }
        int pos = 12;
        Integer rate = null;
        byte[] data = null;
        while (pos + 8 <= wav.length) {
            String id = new String(wav, pos, 4, StandardCharsets.US_ASCII);
            int size = ByteBuffer.wrap(wav, pos + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            int start = pos + 8;
            if (start + size > wav.length) {
                throw new IllegalArgumentException("WAV 块长度不合法");
            }
            if ("fmt ".equals(id)) {
                int format = ByteBuffer.wrap(wav, start, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xffff;
                int channels = ByteBuffer.wrap(wav, start + 2, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
                rate = ByteBuffer.wrap(wav, start + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                int bits = ByteBuffer.wrap(wav, start + 14, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
                if (format != 1 || channels != 1 || bits != 16) {
                    throw new IllegalArgumentException("演示版只处理 16-bit 单声道 PCM");
                }
            } else if ("data".equals(id)) {
                data = Arrays.copyOfRange(wav, start, start + size);
            }
            pos = start + size + (size % 2);
        }
        if (rate == null || data == null) {
            throw new IllegalArgumentException("WAV 缺少 fmt 或 data");
        }
        int n = data.length / 2;
        int[] samples = new int[n];
        ByteBuffer pcm = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < n; i++) {
            samples[i] = pcm.getShort();
        }
        return new Pcm(rate, samples);
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public record WavInfo(int sampleRate, int channels, int bits, double durationSec, double rmsDb, double peak) {}

    record Pcm(int sampleRate, int[] samples) {}
}
