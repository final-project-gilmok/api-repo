package kr.gilmok.api.ai.dto;

import kr.gilmok.api.ai.entity.AiErrorCode;
import kr.gilmok.common.exception.CustomException;

import java.util.stream.Stream;

public record ServerSpecRequest(
        Integer cpuCores,
        Integer memoryGb,
        String instanceType,
        Integer replicaCount
) {
    // ✅ 이슈3: 4개 모두 있어야 true (기존 any → all)
    public boolean hasAnySpec() {
        return cpuCores != null
                && memoryGb != null
                && (instanceType != null && !instanceType.isBlank())
                && replicaCount != null;
    }

    // ✅ 이슈3: all-or-none + 양수 불변식 검증
    public void validate() {
        int filledCount = Stream.of(
                cpuCores != null ? 1 : 0,
                memoryGb != null ? 1 : 0,
                (instanceType != null && !instanceType.isBlank()) ? 1 : 0,
                replicaCount != null ? 1 : 0
        ).mapToInt(Integer::intValue).sum();

        if (filledCount > 0 && filledCount < 4) {
            throw new CustomException(AiErrorCode.AI_INVALID_SERVER_SPEC_PARTIAL);
        }
        if (filledCount == 4) {
            if (cpuCores <= 0 || memoryGb <= 0 || replicaCount <= 0) {
                throw new CustomException(AiErrorCode.AI_INVALID_SERVER_SPEC_VALUE);
            }
        }
    }
}