package kr.gilmok.api.reservation.scheduler;

import kr.gilmok.api.reservation.entity.Reservation;
import kr.gilmok.api.reservation.entity.ReservationStatus;
import kr.gilmok.api.reservation.repository.ReservationRepository;
import kr.gilmok.api.reservation.repository.SeatLockRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HoldingExpiryScheduler {

    private final ReservationRepository reservationRepository;
    private final SeatLockRedisRepository seatLockRedisRepository;

    @Value("${reservation.seat-lock-ttl-seconds:300}")
    private int seatLockTtlSeconds;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void expireHoldingReservations() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusSeconds(seatLockTtlSeconds);
            List<Reservation> expired = reservationRepository.findByStatusAndCreatedAtBefore(
                    ReservationStatus.HOLDING, cutoff
            );

            for (Reservation reservation : expired) {
                reservation.cancel();

                // Redis 잔여석 복구
                try {
                    seatLockRedisRepository.unlockAndRestore(
                            reservation.getEvent().getId(),
                            reservation.getSeat().getId(),
                            reservation.getUserId()
                              );
                } catch (Exception e) {
                    log.warn("Failed to restore Redis seat lock: code={}, error={}",
                    reservation.getReservationCode(), e.getMessage());
                }

                log.info("HOLDING expired → CANCELLED: code={}, userId={}",
                        reservation.getReservationCode(), reservation.getUserId());
            }

            if (!expired.isEmpty()) {
                log.info("Expired {} HOLDING reservations", expired.size());
            }
        } catch (Exception e) {
            log.error("HoldingExpiryScheduler failed", e);
        }
    }
}
