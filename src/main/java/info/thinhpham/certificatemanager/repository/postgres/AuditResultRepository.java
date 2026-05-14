package info.thinhpham.certificatemanager.repository.postgres;

import info.thinhpham.certificatemanager.model.postgres.AuditResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditResultRepository extends JpaRepository<AuditResult, Long> {
    List<AuditResult> findTop100ByOrderByCheckedAtDesc();
}
