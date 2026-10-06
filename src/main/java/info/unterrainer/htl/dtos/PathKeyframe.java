package info.unterrainer.htl.dtos;

/**
 * A point of a bee's flight path: the bee is at (x, y) at time {@code t} (epoch millis) and moves in a
 * straight line at constant speed to the next keyframe.
 */
public record PathKeyframe(long t, double x, double y) {
}
