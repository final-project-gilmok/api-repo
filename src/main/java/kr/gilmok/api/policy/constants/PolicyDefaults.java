package kr.gilmok.api.policy.constants;

import kr.gilmok.api.policy.vo.BlockRules;

/**
 * 정책 기본값 단일 정의. Policy 생성·수정 시 미지정 시 여기서 참조한다.
 */
public final class PolicyDefaults {

    private PolicyDefaults() {
    }

    public static final int ADMISSION_RPS = 0;
    public static final int ADMISSION_CONCURRENCY = 0;
    public static final long TOKEN_TTL_SECONDS = 300L;
    public static final int MAX_REQUESTS_PER_SECOND = 100;
    public static final int BLOCK_DURATION_MINUTES = 10;
    public static final String GATE_MODE = "ROUTING_ENABLED";

    public static BlockRules blockRules() {
        return BlockRules.empty();
    }
}
