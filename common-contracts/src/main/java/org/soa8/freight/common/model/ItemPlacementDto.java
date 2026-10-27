package org.soa8.freight.common.model;

public class ItemPlacementDto {
    private String placementId;
    private String cargoItemId;
    private int instanceIndex;
    private String hazardClass;
    private String unNumber;
    private int xMm;
    private int yMm;
    private int zMm;
    private int lengthMm;
    private int widthMm;
    private int heightMm;
    private double weightKg;
    private String orientation;

    public ItemPlacementDto() {
    }

    public ItemPlacementDto(String placementId, String cargoItemId, int instanceIndex, String hazardClass,
                            String unNumber, int xMm, int yMm, int zMm, int lengthMm, int widthMm,
                            int heightMm, double weightKg, String orientation) {
        this.placementId = placementId;
        this.cargoItemId = cargoItemId;
        this.instanceIndex = instanceIndex;
        this.hazardClass = hazardClass;
        this.unNumber = unNumber;
        this.xMm = xMm;
        this.yMm = yMm;
        this.zMm = zMm;
        this.lengthMm = lengthMm;
        this.widthMm = widthMm;
        this.heightMm = heightMm;
        this.weightKg = weightKg;
        this.orientation = orientation;
    }

    public String getPlacementId() { return placementId; }
    public void setPlacementId(String placementId) { this.placementId = placementId; }

    public String getCargoItemId() { return cargoItemId; }
    public void setCargoItemId(String cargoItemId) { this.cargoItemId = cargoItemId; }

    public int getInstanceIndex() { return instanceIndex; }
    public void setInstanceIndex(int instanceIndex) { this.instanceIndex = instanceIndex; }

    public String getHazardClass() { return hazardClass; }
    public void setHazardClass(String hazardClass) { this.hazardClass = hazardClass; }

    public String getUnNumber() { return unNumber; }
    public void setUnNumber(String unNumber) { this.unNumber = unNumber; }

    public int getXMm() { return xMm; }
    public void setXMm(int xMm) { this.xMm = xMm; }

    public int getYMm() { return yMm; }
    public void setYMm(int yMm) { this.yMm = yMm; }

    public int getZMm() { return zMm; }
    public void setZMm(int zMm) { this.zMm = zMm; }

    public int getLengthMm() { return lengthMm; }
    public void setLengthMm(int lengthMm) { this.lengthMm = lengthMm; }

    public int getWidthMm() { return widthMm; }
    public void setWidthMm(int widthMm) { this.widthMm = widthMm; }

    public int getHeightMm() { return heightMm; }
    public void setHeightMm(int heightMm) { this.heightMm = heightMm; }

    public double getWeightKg() { return weightKg; }
    public void setWeightKg(double weightKg) { this.weightKg = weightKg; }

    public String getOrientation() { return orientation; }
    public void setOrientation(String orientation) { this.orientation = orientation; }
}
