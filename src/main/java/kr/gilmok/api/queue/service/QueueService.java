package kr.gilmok.api.queue.service;

import kr.gilmok.api.queue.QueueStatus;
import kr.gilmok.api.queue.dto.QueueRegisterRequest;
import kr.gilmok.api.queue.dto.QueueRegisterResponse;
import kr.gilmok.api.queue.dto.QueueStatusResponse;
import kr.gilmok.api.queue.repository.QueueRedisRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    private final QueueRedisRepository queueRedisRepository;

    @Value("${queue.admission-rps:10}")
    private int admissionRps;

    @Value("${queue.admitted-ttl-seconds:300}")
    private int admittedTtlSeconds;

    @PostConstruct
    void validateConfig() {
        if (admissionRps <= 0) {
            throw new IllegalStateException("queue.admission-rps must be positive, but was: " + admissionRps);
        }
        if (admittedTtlSeconds <= 0) {
            throw new IllegalStateException("queue.admitted-ttl-seconds must be positive, but was: " + admittedTtlSeconds);
        }
    }

    public QueueRegisterResponse register(QueueRegisterRequest request) {
        String eventId = request.getEventId();
        String sessionKey = request.getSessionKey();

        // Check if session already has a queueKey (re-entry)
        String existingQueueKey = queueRedisRepository.findQueueKeyBySession(eventId, sessionKey);
        if (existingQueueKey != null) {
            Long rank = queueRedisRepository.getRank(eventId, existingQueueKey);
            if (rank != null) {
                long position = rank + 1;
                long etaSeconds = position / admissionRps;
                return new QueueRegisterResponse(existingQueueKey, position, etaSeconds);
            }
            // Already admitted — return position 0
            if (queueRedisRepository.isAdmitted(eventId, existingQueueKey)) {
                return new QueueRegisterResponse(existingQueueKey, 0, 0);
            }
        }

        // New registration
        String queueKey = UUID.randomUUID().toString();
        double score = System.currentTimeMillis();
        queueRedisRepository.register(eventId, sessionKey, queueKey, score);

        Long rank = queueRedisRepository.getRank(eventId, queueKey);
        long position = rank != null ? rank + 1 : 1;
        long etaSeconds = position / admissionRps;

        log.info("Queue registered: eventId={}, sessionKey={}, queueKey={}, position={}",
                eventId, sessionKey, queueKey, position);

        return new QueueRegisterResponse(queueKey, position, etaSeconds);
    }

    public QueueStatusResponse getStatus(String eventId, String queueKey) {
        // Check if admitted
        if (queueRedisRepository.isAdmitted(eventId, queueKey)) {
            return new QueueStatusResponse(QueueStatus.ADMITTABLE, 0, 0, 0);
        }

        // Check if still waiting
        Long rank = queueRedisRepository.getRank(eventId, queueKey);
        if (rank != null) {
            long position = rank + 1;
            long etaSeconds = position / admissionRps;
            return new QueueStatusResponse(QueueStatus.WAITING, position, etaSeconds, 3000);
        }

        // Neither in queue nor admitted → expired
        return new QueueStatusResponse(QueueStatus.EXPIRED, 0, 0, 0);
    }

    public void expireAdmitted(String eventId) {
        long cutoffMs = System.currentTimeMillis() - (admittedTtlSeconds * 1000L);
        long removed = queueRedisRepository.removeExpiredAdmitted(eventId, cutoffMs);
        if (removed > 0) {
            log.info("Expired {} admitted users: eventId={}", removed, eventId);
        }
    }

    public void processAdmission(String eventId) {
        long queueSize = queueRedisRepository.getQueueSize(eventId);
        if (queueSize == 0) {
            return;
        }

        long tokensAvailable = queueRedisRepository.consumeTokens(eventId, admissionRps, queueSize);
        if (tokensAvailable > 0) {
            queueRedisRepository.admitFromHead(eventId, tokensAvailable);
            log.info("Admitted {} users from queue: eventId={}", tokensAvailable, eventId);
        }
    }
}
