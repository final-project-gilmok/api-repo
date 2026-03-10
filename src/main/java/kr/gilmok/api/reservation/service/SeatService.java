package kr.gilmok.api.reservation.service;

import kr.gilmok.api.event.entity.Event;
import kr.gilmok.api.event.repository.EventRepository;
import kr.gilmok.api.reservation.dto.*;
import kr.gilmok.api.reservation.entity.Seat;
import kr.gilmok.api.reservation.exception.ReservationErrorCode;
import kr.gilmok.api.reservation.repository.SeatLockRedisRepository;
import kr.gilmok.api.reservation.repository.SeatRepository;
import kr.gilmok.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatService {

    private final SeatRepository seatRepository;
    private final EventRepository eventRepository;
    private final SeatLockRedisRepository seatLockRedisRepository;

    public List<SeatResponse> getSeatsByEvent(Long eventId) {
        List<Seat> seats = seatRepository.findByEventId(eventId);
        return seats.stream()
                .map(seat -> {
                    int redisAvailable = seatLockRedisRepository.getAvailable(eventId, seat.getId());
                    return SeatResponse.of(seat, redisAvailable);
                })
                .toList();
    }

    @Transactional
    public SeatResponse createSeat(Long eventId, SeatCreateRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new CustomException(ReservationErrorCode.EVENT_NOT_FOUND));

        Seat seat = Seat.builder()
                .event(event)
                .section(request.section())
                .totalCount(request.totalCount())
                .price(request.price())
                .build();

        Seat saved = seatRepository.save(seat);
        return SeatResponse.from(saved);
    }

    @Transactional
    public SeatResponse updateSeat(Long eventId, Long seatId, SeatUpdateRequest request) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new CustomException(ReservationErrorCode.SEAT_NOT_FOUND));
        if (!seat.getEvent().getId().equals(eventId)) {
        throw new CustomException(ReservationErrorCode.SEAT_NOT_FOUND);
        }

        seat.update(request.section(), request.totalCount(), request.price());
        return SeatResponse.from(seat);
    }

    @Transactional
    public void deleteSeat(Long eventId, Long seatId) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new CustomException(ReservationErrorCode.SEAT_NOT_FOUND));
        if (!seat.getEvent().getId().equals(eventId)) {
            throw new CustomException(ReservationErrorCode.SEAT_NOT_FOUND);
        }

        seatLockRedisRepository.deleteAvailable(eventId, seatId);
        seatRepository.delete(seat);
    }

    public void initRedisAvailable(Long eventId) {
        List<Seat> seats = seatRepository.findByEventId(eventId);
        for (Seat seat : seats) {
            seatLockRedisRepository.initAvailable(eventId, seat.getId(), seat.getAvailableCount());
        }
    }

    public List<SeatStatsResponse> getSeatStats(Long eventId) {
        List<Seat> seats = seatRepository.findByEventId(eventId);
        return seats.stream()
                .map(seat -> new SeatStatsResponse(
                        seat.getId(),
                        seat.getSection(),
                        seat.getTotalCount(),
                        seat.getReservedCount(),
                        seat.getAvailableCount(),
                        seat.getPrice()
                ))
                .toList();
    }
}
