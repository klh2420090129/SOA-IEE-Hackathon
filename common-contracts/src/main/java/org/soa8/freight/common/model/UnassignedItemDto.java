package org.soa8.freight.common.model;

public class UnassignedItemDto {
    private String cargoItemId;
    private int instanceIndex;
    private String reasonCode;
    private String reasonDescription;

    public UnassignedItemDto() {
    }

    public UnassignedItemDto(String cargoItemId, int instanceIndex, String reasonCode, String reasonDescription) {
        this.cargoItemId = cargoItemId;
        this.instanceIndex = instanceIndex;
        this.reasonCode = reasonCode;
        this.reasonDescription = reasonDescription;
    }

    public String getCargoItemId() { return cargoItemId; }
    public void setCargoItemId(String cargoItemId) { this.cargoItemId = cargoItemId; }

    public int getInstanceIndex() { return instanceIndex; }
    public void setInstanceIndex(int instanceIndex) { this.instanceIndex = instanceIndex; }

    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

    public String getReasonDescription() { return reasonDescription; }
    public void setReasonDescription(String reasonDescription) { this.reasonDescription = reasonDescription; }
}
