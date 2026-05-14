package info.thinhpham.certificatemanager.repository.oracle;

import info.thinhpham.certificatemanager.model.oracle.ServiceInventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceInventoryRepository extends JpaRepository<ServiceInventory, Long> {
    List<ServiceInventory> findByActive(Integer active);
}
