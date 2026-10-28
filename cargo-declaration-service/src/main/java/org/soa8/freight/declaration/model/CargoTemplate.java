package org.soa8.freight.declaration.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cargo_templates")
public class CargoTemplate {

    @Id
    private String id;

    @Column(nullable = false)
    private String shipperId;

    @Column(nullable = false)
    private String name;

    private String description;

    private int version;

    private Instant createdAt;
    private Instant updatedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "template_id")
    private List<CargoTemplateItemEntity> items = new ArrayList<>();

    public CargoTemplate() {}

    public CargoTemplate(String id, String shipperId, String name, String description) {
        this.id = id;
        this.shipperId = shipperId;
        this.name = name;
        this.description = description;
        this.version = 1;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getShipperId() { return shipperId; }
    public void setShipperId(String shipperId) { this.shipperId = shipperId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public List<CargoTemplateItemEntity> getItems() { return items; }
    public void setItems(List<CargoTemplateItemEntity> items) { this.items = items; }
}
