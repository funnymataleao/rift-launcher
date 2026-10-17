package com.limelight.binding.video;

/**
 * Decoder settings used by {@link MediaCodecDecoderRenderer}.
 *
 * Rift: replaces Artemis' UI-bound PreferenceConfiguration inside the core module.
 * Field names and constants mirror PreferenceConfiguration so the renderer code stays unchanged.
 */
public class DecoderConfiguration {
    public enum FormatOption {
        AUTO,
        FORCE_AV1,
        FORCE_HEVC,
        FORCE_H264,
    }

    public static final int FRAME_PACING_MIN_LATENCY = 0;
    public static final int FRAME_PACING_BALANCED = 1;
    public static final int FRAME_PACING_CAP_FPS = 2;
    public static final int FRAME_PACING_MAX_SMOOTHNESS = 3;

    public float fps = 60;
    public int bitrate = 20000; // Kbps, only reported in perf stats
    public FormatOption videoFormat = FormatOption.AUTO;
    public boolean fullRange;
    public boolean enableUltraLowLatency;
    public int framePacing = FRAME_PACING_BALANCED;
    public boolean enablePerfOverlay;
    public boolean enablePerfLogging;
    public boolean enablePerfOverlayLite;
}
