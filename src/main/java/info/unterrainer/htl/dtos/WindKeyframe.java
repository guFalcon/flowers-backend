package info.unterrainer.htl.dtos;

/**
 * The wind direction from {@code t} (epoch millis) until the next keyframe. The angle is in radians,
 * 0 points towards growing x, π/2 towards growing y.
 */
public record WindKeyframe(long t, double angle) {
}
