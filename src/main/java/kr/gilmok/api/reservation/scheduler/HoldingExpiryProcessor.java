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
    }
}
