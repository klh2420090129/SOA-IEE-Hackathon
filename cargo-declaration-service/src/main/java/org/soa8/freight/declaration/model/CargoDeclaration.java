package org.soa8.freight.declaration.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cargo_declarations")
public class CargoDeclaration {
    @Id
    private String id;

    @Column(nullable = false)
    private String referenceNumber;

    @Column(nullable = false)
    private String shipperId;

    private String origin;
    private String destination;

    private int version;

    private String status; // SUBMITTED, IN_ASSESSMENT, APPROVED, REJECTED

    private Instant createdAt;
    private Instant updatedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "declaration_id")
    private List<CargoItemEntity> items = new ArrayList<>();

    public CargoDeclaration() {}

    public CargoDeclaration(String id, String referenceNumber, String shipperId, String origin, String destination) {
        this.id = id;
        this.referenceNumber = referenceNumber;
        this.shipperId = shipperId;
        this.origin = origin;
        this.destination = destination;
        this.version = 1;
        this.status = "SUBMITTED";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }

    public String getShipperId() { return shipperId; }
    public void setShipperId(String shipperId) { this.shipperId = shipperId; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public List<CargoItemEntity> getItems() { return items; }
    public void setItems(List<CargoItemEntity> items) { this.items = items; }
}
