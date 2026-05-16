#ifndef SIMPLE_VAD_H
#define SIMPLE_VAD_H

#ifdef __cplusplus
extern "C" {
#endif

// Initialize the VAD. aggressiveness is a tuning parameter (0-3), higher = more aggressive
void vad_init(int aggressiveness);

// Process a PCM16 buffer. Returns 1 if speech is detected, 0 otherwise.
int vad_process(const short* pcm, int length);

// Release any resources.
void vad_release();

#ifdef __cplusplus
}
#endif

#endif // SIMPLE_VAD_H
