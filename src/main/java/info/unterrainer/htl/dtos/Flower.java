package info.unterrainer.htl.dtos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Flower {
    private String id;
    private double x;
    private double y;
    private double size;
    private int petals;
    private String color;
    private double fill;
}