package rw.gov.ptc.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.notification.entity.PushSubscription;

import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {
}
