package info.unterrainer.htl.dtos;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder(toBuilder = true)
public class Level {
    @Builder.Default
    private String aspect = "9:16";
    private List<Flower> flowers;
    private List<Bee> bees;
    private String yourBeeId;
}
