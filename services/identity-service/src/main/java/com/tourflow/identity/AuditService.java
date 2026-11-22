package com.tourflow.identity;

import com.tourflow.identity.model.AuditLog;
import com.tourflow.identity.repository.AuditLogRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditLogRepository repo;
    public AuditService(AuditLogRepository repo) { this.repo = repo; }

    public void log(UUID actor, String action, String target, String details) {
        AuditLog a = new AuditLog();
        a.setActor(actor); a.setAction(action); a.setTarget(target); a.setDetails(details);
        repo.save(a);
    }
}

