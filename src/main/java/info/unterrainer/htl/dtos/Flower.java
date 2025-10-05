package info.unterrainer.htl.dtos;

import java.util.List;

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
    private List<String> petalColors;
    private String stampColor;
    private double fill;
    private double rate;
}