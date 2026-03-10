package kr.gilmok.api.event.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EventStatus {
    DRAFT("임시 저장"),
    OPEN("모집 중"),
    CLOSED("종료");

    private final String description;
}