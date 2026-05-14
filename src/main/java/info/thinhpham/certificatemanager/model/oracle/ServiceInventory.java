package info.thinhpham.certificatemanager.model.oracle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "SERVICE_INVENTORY")
public class ServiceInventory {

    @Id
    @Column(name = "ID")
    private Long id;

    @Column(name = "SERVICE_NAME")
    private String serviceName;

    @Column(name = "URL")
    private String url;

    @Column(name = "ACTIVE")
    private Integer active;

    public Long getId() { return id; }
    public String getServiceName() { return serviceName; }
    public String getUrl() { return url; }
    public Integer getActive() { return active; }
}
