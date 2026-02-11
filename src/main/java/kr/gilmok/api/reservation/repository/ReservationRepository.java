package kr.gilmok.api.reservation.repository;

import kr.gilmok.api.reservation.entity.Reservation;
import kr.gilmok.api.reservation.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByReservationCode(String reservationCode);

    List<Reservation> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Reservation> findByEventIdOrderByCreatedAtDesc(Long eventId);

    List<Reservation> findByStatusAndCreatedAtBefore(ReservationStatus status, LocalDateTime cutoff);

    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.event.id = :eventId AND r.status = :status")
    long countByEventIdAndStatus(@Param("eventId") Long eventId, @Param("status") ReservationStatus status);

    @Query("SELECT COALESCE(SUM(r.quantity), 0) FROM Reservation r WHERE r.event.id = :eventId AND r.status = :status")
    long sumQuantityByEventIdAndStatus(@Param("eventId") Long eventId, @Param("status") ReservationStatus status);
}
