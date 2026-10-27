package org.soa8.freight.common.model;

import java.util.List;

public class ValidationFindingDto {
    private String severity; // CRITICAL, WARNING, INFO
    private String code;     // OVERLAP_DETECTED, OUT_OF_BOUNDS, HAZARD_SEGREGATION_VIOLATION, CAPACITY_EXCEEDED
    private String message;
    private List<String> involvedItemIds;

    public ValidationFindingDto() {
    }

    public ValidationFindingDto(String severity, String code, String message, List<String> involvedItemIds) {
        this.severity = severity;
        this.code = code;
        this.message = message;
        this.involvedItemIds = involvedItemIds;
    }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<String> getInvolvedItemIds() { return involvedItemIds; }
    public void setInvolvedItemIds(List<String> involvedItemIds) { this.involvedItemIds = involvedItemIds; }
}
