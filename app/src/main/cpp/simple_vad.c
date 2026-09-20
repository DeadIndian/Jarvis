#include "simple_vad.h"
#include <math.h>
#include <stdlib.h>

typedef struct {
    double noise_level;
    double alpha; // smoothing for noise estimate
    int hangover; // frames of speech to keep after end
    int hangover_left;
    double threshold_ratio;
} VadState;

static VadState g_state = {0};

void vad_init(int aggressiveness) {
    // aggressiveness 0..3 -> threshold ratio and hangover tuning
    if (aggressiveness < 0) aggressiveness = 0;
    if (aggressiveness > 3) aggressiveness = 3;
    g_state.alpha = 0.95; // noise smoothing
    g_state.noise_level = 1e-8;
    g_state.hangover =  (aggressiveness == 0) ? 10 : (aggressiveness == 1) ? 7 : (aggressiveness == 2) ? 5 : 3;
    g_state.hangover_left = 0;
    g_state.threshold_ratio = (aggressiveness == 0) ? 3.0 : (aggressiveness == 1) ? 4.0 : (aggressiveness == 2) ? 6.0 : 8.0;
}

static double compute_rms(const short* pcm, int length) {
    if (length <= 0) return 0.0;
    double sum = 0.0;
    for (int i = 0; i < length; ++i) {
        double s = (double)pcm[i];
        sum += s * s;
    }
    double mean = sum / length;
    return sqrt(mean);
}

int vad_process(const short* pcm, int length) {
    // PCM is 16-bit mono, length samples, typical frames 160@16kHz (10ms) or larger
    double rms = compute_rms(pcm, length);

    // Update noise estimate when quiet
    // If rms is significantly lower than current noise, slowly adapt down;
    // else adapt up faster
    if (rms < g_state.noise_level) {
        g_state.noise_level = g_state.alpha * g_state.noise_level + (1.0 - g_state.alpha) * rms;
    } else {
        // adapt up faster
        g_state.noise_level = 0.7 * g_state.noise_level + 0.3 * rms;
    }

    double ratio = (g_state.noise_level > 1e-9) ? (rms / g_state.noise_level) : 0.0;
    int is_speech = (ratio > g_state.threshold_ratio) ? 1 : 0;

    if (is_speech) {
        g_state.hangover_left = g_state.hangover; // reset hangover
        return 1;
    }

    if (g_state.hangover_left > 0) {
        g_state.hangover_left--;
        return 1;
    }

    return 0;
}

void vad_release() {
    // nothing to free in this simple impl
    g_state.noise_level = 1e-8;
    g_state.hangover_left = 0;
}
