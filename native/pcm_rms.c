#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

/* 读取 16-bit 小端单声道 PCM WAV，打印时长和 RMS 响度。
   和 Java 的 WavProcessor 是同一套算法，方便面试对照。 */

static int read_u16(FILE *fp, uint16_t *out) {
    unsigned char b[2];
    if (fread(b, 1, 2, fp) != 2) return 0;
    *out = (uint16_t)(b[0] | (b[1] << 8));
    return 1;
}

static int read_u32(FILE *fp, uint32_t *out) {
    unsigned char b[4];
    if (fread(b, 1, 4, fp) != 4) return 0;
    *out = (uint32_t)b[0] | ((uint32_t)b[1] << 8) | ((uint32_t)b[2] << 16) | ((uint32_t)b[3] << 24);
    return 1;
}

int main(int argc, char **argv) {
    if (argc != 2) {
        fprintf(stderr, "用法: pcm_rms <file.wav>\n");
        return 1;
    }
    FILE *fp = fopen(argv[1], "rb");
    if (!fp) {
        perror("fopen");
        return 1;
    }
    char id[4];
    uint32_t chunk_size = 0;
    if (fread(id, 1, 4, fp) != 4 || memcmp(id, "RIFF", 4) != 0) {
        fprintf(stderr, "不是 RIFF\n");
        return 1;
    }
    if (!read_u32(fp, &chunk_size) || fread(id, 1, 4, fp) != 4 || memcmp(id, "WAVE", 4) != 0) {
        fprintf(stderr, "不是 WAVE\n");
        return 1;
    }

    uint16_t format = 0, channels = 0, bits = 0;
    uint32_t rate = 0, data_size = 0;
    int got_fmt = 0, got_data = 0;
    long data_pos = 0;
    while (fread(id, 1, 4, fp) == 4) {
        if (!read_u32(fp, &chunk_size)) break;
        if (memcmp(id, "fmt ", 4) == 0) {
            if (!read_u16(fp, &format) || !read_u16(fp, &channels) || !read_u32(fp, &rate)) return 1;
            fseek(fp, 6, SEEK_CUR);
            if (!read_u16(fp, &bits)) return 1;
            if (chunk_size > 16) fseek(fp, (long)chunk_size - 16, SEEK_CUR);
            got_fmt = 1;
        } else if (memcmp(id, "data", 4) == 0) {
            data_pos = ftell(fp);
            data_size = chunk_size;
            fseek(fp, (long)chunk_size, SEEK_CUR);
            got_data = 1;
        } else {
            fseek(fp, (long)chunk_size, SEEK_CUR);
        }
        if (chunk_size % 2) fseek(fp, 1, SEEK_CUR);
    }
    if (!got_fmt || !got_data || format != 1 || channels != 1 || bits != 16 || rate == 0) {
        fprintf(stderr, "只支持 16-bit 单声道 PCM\n");
        return 1;
    }

    uint32_t samples = data_size / 2;
    double sum = 0;
    fseek(fp, data_pos, SEEK_SET);
    for (uint32_t i = 0; i < samples; i++) {
        uint16_t raw;
        if (!read_u16(fp, &raw)) break;
        int16_t sample = (int16_t)raw;
        sum += (double)sample * (double)sample;
    }
    fclose(fp);
    double rms = sqrt(sum / samples) / 32768.0;
    double db = rms <= 1e-9 ? -120.0 : 20.0 * log10(rms);
    printf("sample_rate=%u\n", rate);
    printf("duration_sec=%.3f\n", (double)samples / rate);
    printf("rms_db=%.2f\n", db);
    return 0;
}
