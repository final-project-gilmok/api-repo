package kr.gilmok.api.event.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EventStatus status;

    @Column(length = 500)
    private String demoUrl;

    private LocalDateTime startsAt;
    private LocalDateTime endsAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder
    public Event(String name, String description, LocalDateTime startsAt,
                 LocalDateTime endsAt, String demoUrl) {
        this.name = name;
        this.description = description;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.demoUrl = demoUrl;
        this.status = EventStatus.DRAFT; // 생성 시 무조건 DRAFT
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void open() {
        this.status = EventStatus.OPEN;
        this.updatedAt = LocalDateTime.now();
    }

    public void close() {
        this.status = EventStatus.CLOSED;
        this.updatedAt = LocalDateTime.now();
    }
}