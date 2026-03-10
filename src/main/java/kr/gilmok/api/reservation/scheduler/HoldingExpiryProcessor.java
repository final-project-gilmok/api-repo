package kr.gilmok.api.reservation.scheduler;

import kr.gilmok.api.reservation.entity.Reservation;
import kr.gilmok.api.reservation.entity.ReservationStatus;
import kr.gilmok.api.reservation.repository.ReservationRepository;
import kr.gilmok.api.reservation.repository.SeatLockRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldingExpiryProcessor {

    private final ReservationRepository reservationRepository;
    private final SeatLockRedisRepository seatLockRedisRepository;

    @Transactional
    public void processSingleExpiry(Reservation reservation) {
        Reservation managed = reservationRepository.findById(reservation.getId()).orElse(null);
        if (managed == null || managed.getStatus() != ReservationStatus.HOLDING) {
            return;
        }

        managed.cancel();
        try {
            seatLockRedisRepository.unlockAndRestore(
                managed.getEvent().getId(),
                managed.getSeat().getId(),
                managed.getUserId()
            );
        } catch (Exception e) {
            log.error("Redis restore failed: code={}", managed.getReservationCode(), e);
        }
        log.info("HOLDING expired → CANCELLED: code={}, userId={}",
            managed.getReservationCode(), managed.getUserId());
    }
}
