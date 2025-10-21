package info.unterrainer.htl.dtos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Bee {
    private String id;
    private double x;
    private double y;
    private double targetX;
    private double targetY;
    private String color;
    private long lastActive;
}