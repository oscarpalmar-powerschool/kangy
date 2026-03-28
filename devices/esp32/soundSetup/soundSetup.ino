#include <driver/i2s.h>
#include "sound_data.h"  // place this file next to the sketch

#define I2S_BCLK  26
#define I2S_LRCLK 25
#define I2S_DOUT  22

#define WAV_HEADER_SIZE 44
#define BUFFER_SIZE 512

void playWav() {
  const uint8_t* audio = wav_data + WAV_HEADER_SIZE;
  int audioLen = wav_len - WAV_HEADER_SIZE;

  size_t written;
  int offset = 0;

  while (offset < audioLen) {
    int chunk = min(BUFFER_SIZE, audioLen - offset);
    i2s_write(I2S_NUM_0, audio + offset, chunk, &written, portMAX_DELAY);
    offset += chunk;
  }
}

void setup() {
  Serial.begin(115200);

  i2s_config_t config = {
    .mode = (i2s_mode_t)(I2S_MODE_MASTER | I2S_MODE_TX),
    .sample_rate = 16000,  // change to match your WAV file's sample rate
    .bits_per_sample = I2S_BITS_PER_SAMPLE_16BIT,
    .channel_format = I2S_CHANNEL_FMT_ONLY_LEFT,
    .communication_format = I2S_COMM_FORMAT_I2S,
    .intr_alloc_flags = 0,
    .dma_buf_count = 8,
    .dma_buf_len = 512,
    .use_apll = false
  };

  i2s_pin_config_t pins = {
    .bck_io_num = I2S_BCLK,
    .ws_io_num  = I2S_LRCLK,
    .data_out_num = I2S_DOUT,
    .data_in_num  = -1
  };

  i2s_driver_install(I2S_NUM_0, &config, 0, NULL);
  i2s_set_pin(I2S_NUM_0, &pins);

  playWav();
}

void loop() {}