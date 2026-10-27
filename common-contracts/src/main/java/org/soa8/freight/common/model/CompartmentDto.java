package org.soa8.freight.common.model;

public class CompartmentDto {
    private String compartmentId;
    private String name;
    private int lengthMm;
    private int widthMm;
    private int heightMm;
    private double maxPayloadKg;

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
}
