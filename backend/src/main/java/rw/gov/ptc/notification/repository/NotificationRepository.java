package rw.gov.ptc.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.notification.entity.Notification;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
}
