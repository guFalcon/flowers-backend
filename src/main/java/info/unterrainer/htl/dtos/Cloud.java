package info.unterrainer.htl.dtos;

import lombok.Builder;
import lombok.Data;

/**
 * A cloud drifting with the wind. (x, y) is its position at time {@code t} (epoch millis); size
 * (diameter) and speed are in play-area heights (per second), drift is its fixed offset to the wind
 * direction in radians.
 */
@Data
@Builder(toBuilder = true)
public class Cloud {
    private String id;
    private double x;
    private double y;
    private long t;
    private double size;
    private double speed;
    private double drift;
}
