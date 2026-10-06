package info.unterrainer.htl.dtos;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder(toBuilder = true)
public class Level {
    @Builder.Default
    private String aspect = "9:16";
    // Server time (epoch millis) at which this level was built
    private long serverTime;
    private List<Flower> flowers;
    private List<Cloud> clouds;
    private List<WindKeyframe> wind;
    private List<Bee> bees;
    private String yourBeeId;
}
