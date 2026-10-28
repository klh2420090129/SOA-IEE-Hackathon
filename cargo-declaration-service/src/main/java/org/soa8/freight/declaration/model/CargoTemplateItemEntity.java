package org.soa8.freight.declaration.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "cargo_template_items")
public class CargoTemplateItemEntity {

    @Id
    private String id;
    private String cargoItemId;
    private String description;
    private String hazardClass;
    private String unNumber;
    private int lengthMm;
    private int widthMm;
    private int heightMm;
    private double weightKg;
    private boolean stackable;
    private double maxStackWeightKg;
    private int quantity;

    public CargoTemplateItemEntity() {}

    public CargoTemplateItemEntity(String id, String cargoItemId, String description, String hazardClass,
                                   String unNumber, int lengthMm, int widthMm, int heightMm,
                                   double weightKg, boolean stackable, double maxStackWeightKg, int quantity) {
        this.id = id;
        this.cargoItemId = cargoItemId;
        this.description = description;
        this.hazardClass = hazardClass;
        this.unNumber = unNumber;
        this.lengthMm = lengthMm;
        this.widthMm = widthMm;
        this.heightMm = heightMm;
        this.weightKg = weightKg;
        this.stackable = stackable;
        this.maxStackWeightKg = maxStackWeightKg;
        this.quantity = quantity;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCargoItemId() { return cargoItemId; }
    public void setCargoItemId(String cargoItemId) { this.cargoItemId = cargoItemId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getHazardClass() { return hazardClass; }
    public void setHazardClass(String hazardClass) { this.hazardClass = hazardClass; }

    public String getUnNumber() { return unNumber; }
    public void setUnNumber(String unNumber) { this.unNumber = unNumber; }

    public int getLengthMm() { return lengthMm; }
    public void setLengthMm(int lengthMm) { this.lengthMm = lengthMm; }

    public int getWidthMm() { return widthMm; }
    public void setWidthMm(int widthMm) { this.widthMm = widthMm; }

    public int getHeightMm() { return heightMm; }
    public void setHeightMm(int heightMm) { this.heightMm = heightMm; }

    public double getWeightKg() { return weightKg; }
    public void setWeightKg(double weightKg) { this.weightKg = weightKg; }

    public boolean isStackable() { return stackable; }
    public void setStackable(boolean stackable) { this.stackable = stackable; }

    public double getMaxStackWeightKg() { return maxStackWeightKg; }
    public void setMaxStackWeightKg(double maxStackWeightKg) { this.maxStackWeightKg = maxStackWeightKg; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
