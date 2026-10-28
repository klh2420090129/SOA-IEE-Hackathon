package org.soa8.freight.common.model;

public class CompartmentDto {
    private String compartmentId;
    private String name;
    private int lengthMm;
    private int widthMm;
    private int heightMm;
    private double maxPayloadKg;
    // Optional authoritative Centre of Gravity (CoG) bounding limits in millimetres.
    // If null, CoG is evaluated as informational without asserting compliance against unconfigured limits.
    private Double minCogXMm;
    private Double maxCogXMm;
    private Double minCogYMm;
    private Double maxCogYMm;
    private Double minCogZMm;
    private Double maxCogZMm;

    public CompartmentDto() {
    }

    public CompartmentDto(String compartmentId, String name, int lengthMm, int widthMm, int heightMm, double maxPayloadKg) {
        this.compartmentId = compartmentId;
        this.name = name;
        this.lengthMm = lengthMm;
        this.widthMm = widthMm;
        this.heightMm = heightMm;
        this.maxPayloadKg = maxPayloadKg;
    }

    public String getCompartmentId() { return compartmentId; }
    public void setCompartmentId(String compartmentId) { this.compartmentId = compartmentId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getLengthMm() { return lengthMm; }
    public void setLengthMm(int lengthMm) { this.lengthMm = lengthMm; }

    public int getWidthMm() { return widthMm; }
    public void setWidthMm(int widthMm) { this.widthMm = widthMm; }

    public int getHeightMm() { return heightMm; }
    public void setHeightMm(int heightMm) { this.heightMm = heightMm; }

    public double getMaxPayloadKg() { return maxPayloadKg; }
    public void setMaxPayloadKg(double maxPayloadKg) { this.maxPayloadKg = maxPayloadKg; }

    public Double getMinCogXMm() { return minCogXMm; }
    public void setMinCogXMm(Double minCogXMm) { this.minCogXMm = minCogXMm; }

    public Double getMaxCogXMm() { return maxCogXMm; }
    public void setMaxCogXMm(Double maxCogXMm) { this.maxCogXMm = maxCogXMm; }

    public Double getMinCogYMm() { return minCogYMm; }
    public void setMinCogYMm(Double minCogYMm) { this.minCogYMm = minCogYMm; }

    public Double getMaxCogYMm() { return maxCogYMm; }
    public void setMaxCogYMm(Double maxCogYMm) { this.maxCogYMm = maxCogYMm; }

    public Double getMinCogZMm() { return minCogZMm; }
    public void setMinCogZMm(Double minCogZMm) { this.minCogZMm = minCogZMm; }

    public Double getMaxCogZMm() { return maxCogZMm; }
    public void setMaxCogZMm(Double maxCogZMm) { this.maxCogZMm = maxCogZMm; }
}
