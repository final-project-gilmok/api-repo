package kr.gilmok.api.queue.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class QueueRegisterResponse {
    private final String queueKey;
    private final long position;
    private final long etaSeconds;
}
