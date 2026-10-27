package org.soa8.freight.common.model;

import java.util.List;

public class CompatibilityRuleDto {
    private String ruleId;
    private String ruleCode;
    private String primaryHazardClass;
    private String secondaryHazardClass;
    private String segregationType; // FORBIDDEN, SEPARATED_BY_DISTANCE, BUFFER_REQUIRED
    private int minDistanceMm;
    private String description;
    private String jurisdiction;

    public CompatibilityRuleDto() {
    }

    public CompatibilityRuleDto(String ruleId, String ruleCode, String primaryHazardClass, String secondaryHazardClass,
                                String segregationType, int minDistanceMm, String description, String jurisdiction) {
        this.ruleId = ruleId;
        this.ruleCode = ruleCode;
        this.primaryHazardClass = primaryHazardClass;
        this.secondaryHazardClass = secondaryHazardClass;
        this.segregationType = segregationType;
        this.minDistanceMm = minDistanceMm;
        this.description = description;
        this.jurisdiction = jurisdiction;
    }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }

    public String getPrimaryHazardClass() { return primaryHazardClass; }
    public void setPrimaryHazardClass(String primaryHazardClass) { this.primaryHazardClass = primaryHazardClass; }

    public String getSecondaryHazardClass() { return secondaryHazardClass; }
    public void setSecondaryHazardClass(String secondaryHazardClass) { this.secondaryHazardClass = secondaryHazardClass; }

    public String getSegregationType() { return segregationType; }
    public void setSegregationType(String segregationType) { this.segregationType = segregationType; }

    public int getMinDistanceMm() { return minDistanceMm; }
    public void setMinDistanceMm(int minDistanceMm) { this.minDistanceMm = minDistanceMm; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }
}
