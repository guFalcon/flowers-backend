package info.unterrainer.htl.dtos;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class Level {
    private String aspect = "9:16";
    private List<Flower> flowers;
}
