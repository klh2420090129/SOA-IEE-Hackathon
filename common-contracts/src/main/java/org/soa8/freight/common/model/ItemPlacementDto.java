package org.soa8.freight.common.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ItemPlacementDto {
    private String placementId;
    private String cargoItemId;
    private int instanceIndex;
    private String hazardClass;
    private String unNumber;
    @JsonProperty("xMm")
    @JsonAlias({"xmm", "xMm"})
    private int xMm;
    @JsonProperty("yMm")
    @JsonAlias({"ymm", "yMm"})
    private int yMm;
    @JsonProperty("zMm")
    @JsonAlias({"zmm", "zMm"})
    private int zMm;
    private int lengthMm;
    private int widthMm;
    private int heightMm;
    private double weightKg;
    private String orientation;
    private Boolean stackable;
    private Double maxStackWeightKg;

    public ItemPlacementDto() {
    }

    /**
     * @deprecated Legacy constructor without explicit stacking metadata. Defaults stackable to false for safety.
     * Use {@link #ItemPlacementDto(String, String, int, String, String, int, int, int, int, int, int, double, String, Boolean, Double)} instead.
     */
    @Deprecated
    public ItemPlacementDto(String placementId, String cargoItemId, int instanceIndex, String hazardClass,
                            String unNumber, int xMm, int yMm, int zMm, int lengthMm, int widthMm,
                            int heightMm, double weightKg, String orientation) {
        this(placementId, cargoItemId, instanceIndex, hazardClass, unNumber, xMm, yMm, zMm, lengthMm, widthMm, heightMm, weightKg, orientation, false, null);
    }

    public ItemPlacementDto(String placementId, String cargoItemId, int instanceIndex, String hazardClass,
                            String unNumber, int xMm, int yMm, int zMm, int lengthMm, int widthMm,
                            int heightMm, double weightKg, String orientation, Boolean stackable, Double maxStackWeightKg) {
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
        this.stackable = stackable;
        this.maxStackWeightKg = maxStackWeightKg;
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

    public Boolean getStackable() { return stackable; }
    public boolean isStackable() { return Boolean.TRUE.equals(stackable); }
    public void setStackable(Boolean stackable) { this.stackable = stackable; }

    public Double getMaxStackWeightKg() { return maxStackWeightKg; }
    public void setMaxStackWeightKg(Double maxStackWeightKg) { this.maxStackWeightKg = maxStackWeightKg; }
}
