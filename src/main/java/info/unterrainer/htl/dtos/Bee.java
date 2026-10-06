package info.unterrainer.htl.dtos;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class Bee {
    private String id;
    private double x;
    private double y;
    private double targetX;
    private double targetY;
    private String color;
    private long lastActive;
    private long honey;
    private String name;

    // Current flight: a straight line from (fromX, fromY) to the target during [flightStart, flightEnd]
    @JsonIgnore
    private double fromX;
    @JsonIgnore
    private double fromY;
    @JsonIgnore
    private long flightStart;
    @JsonIgnore
    private long flightEnd;

    public record Position(double x, double y) {
    }

    /**
     * Position at the given time (epoch millis), interpolated linearly along the current flight; the
     * target once the bee has arrived.
     */
    public Position positionAt(long now) {
        if (now >= flightEnd)
            return new Position(targetX, targetY);
        if (now <= flightStart)
            return new Position(fromX, fromY);
        double t = (double) (now - flightStart) / (flightEnd - flightStart);
        return new Position(fromX + (targetX - fromX) * t, fromY + (targetY - fromY) * t);
    }
}
