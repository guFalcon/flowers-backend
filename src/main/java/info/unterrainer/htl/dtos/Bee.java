package info.unterrainer.htl.dtos;

import java.util.List;

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
    // Harvested honey in microlitres
    private long honey;
    private String name;
    // Current flight, sorted by t; immutable, so copies of the bee may share it
    private List<PathKeyframe> path;

    public record Position(double x, double y) {
    }

    public void setPath(List<PathKeyframe> path) {
        this.path = List.copyOf(path);
    }

    /**
     * Position at the given time (epoch millis), interpolated linearly between the path's keyframes;
     * the first keyframe before the flight, the last one after it.
     */
    public Position positionAt(long now) {
        PathKeyframe first = path.getFirst();
        if (now <= first.t())
            return new Position(first.x(), first.y());
        for (int i = 1; i < path.size(); i++) {
            PathKeyframe from = path.get(i - 1);
            PathKeyframe to = path.get(i);
            if (now < to.t()) {
                double f = (double) (now - from.t()) / (to.t() - from.t());
                return new Position(from.x() + (to.x() - from.x()) * f, from.y() + (to.y() - from.y()) * f);
            }
        }
        PathKeyframe last = path.getLast();
        return new Position(last.x(), last.y());
    }

    /**
     * End of the current flight (epoch millis): the time of the path's last keyframe.
     */
    @JsonIgnore
    public long arrivalTime() {
        return path.getLast().t();
    }
}
