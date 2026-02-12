package kr.gilmok.api.queue.dto;

import kr.gilmok.api.queue.QueueStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class QueueStatusResponse {
    private final QueueStatus status;
    private final long position;
    private final long total;
    private final long etaSeconds;
    private final long pollAfterMs;
}
