package kr.gilmok.api.reservation.entity;

import jakarta.persistence.*;
import kr.gilmok.api.event.entity.Event;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 10)
    private String section;

    @Column(nullable = false)
    private int totalCount;

    @Column(nullable = false)
    private int reservedCount;

    @Column(nullable = false)
    private int price;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder
    public Seat(Event event, String section, int totalCount, int price) {
        this.event = event;
        this.section = section;
        this.totalCount = totalCount;
        this.reservedCount = 0;
        this.price = price;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public int getAvailableCount() {
        return totalCount - reservedCount;
    }

    public void reserve(int count) {
        if (reservedCount + count > totalCount) {
            throw new IllegalStateException("잔여석이 부족합니다.");
        }
        this.reservedCount += count;
        this.updatedAt = LocalDateTime.now();
    }

    public void cancelReservation(int count) {
        this.reservedCount = Math.max(0, this.reservedCount - count);
        this.updatedAt = LocalDateTime.now();
    }

    public void update(String section, int totalCount, int price) {
        this.section = section;
        this.totalCount = totalCount;
        this.price = price;
        this.updatedAt = LocalDateTime.now();
    }
}
