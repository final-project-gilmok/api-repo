package kr.gilmok.api.reservation.scheduler;

import kr.gilmok.api.reservation.entity.Reservation;
import kr.gilmok.api.reservation.entity.ReservationStatus;
import kr.gilmok.api.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HoldingExpiryScheduler {

    private final ReservationRepository reservationRepository;
    private final HoldingExpiryProcessor processor;

    @Value("${reservation.seat-lock-ttl-seconds:300}")
    private int seatLockTtlSeconds;

    @Scheduled(fixedDelay = 60000)
    public void expireHoldingReservations() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusSeconds(seatLockTtlSeconds);
            List<Reservation> expired = reservationRepository.findExpiredHolding(
                ReservationStatus.HOLDING, cutoff
            );
            expired.forEach(processor::processSingleExpiry);

            if (!expired.isEmpty()) {
                log.info("Expired {} HOLDING reservations", expired.size());
            }
        } catch (Exception e) {
            log.error("HoldingExpiryScheduler failed", e);
        }
    }
}
