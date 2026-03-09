package kr.gilmok.api.reservation.scheduler;

import kr.gilmok.api.reservation.entity.Reservation;
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
        reservation.cancel();
        try {
            seatLockRedisRepository.unlockAndRestore(
                reservation.getEvent().getId(),
                reservation.getSeat().getId(),
                reservation.getUserId()
            );
        } catch (Exception e) {
            log.warn("Redis restore failed: code={}", reservation.getReservationCode(), e);
        }
        log.info("HOLDING expired → CANCELLED: code={}, userId={}",
            reservation.getReservationCode(), reservation.getUserId());
    }
}
