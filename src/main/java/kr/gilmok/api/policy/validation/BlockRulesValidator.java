package kr.gilmok.api.policy.validation;

import kr.gilmok.api.policy.exception.PolicyErrorCode;
import kr.gilmok.api.policy.vo.BlockRules;
import kr.gilmok.common.exception.CustomException;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class BlockRulesValidator {

    private static final Set<String> ALLOWED_RATE_LIMIT_KEYS = Set.of("ip", "userId", "sessionId");

    private BlockRulesValidator() {
    }

    /**
     * BlockRules 유효성 검사. 실패 시 CustomException(INVALID_BLOCK_RULES) 발생.
     * - rateLimitKey: null 허용, 아니면 ip, userId, sessionId 중 하나
     * - ipPattern, userAgentPattern: null/빈 문자열 허용, 값이 있으면 유효한 정규식이어야 함
     */
    public static void validate(BlockRules blockRules) {
        if (blockRules == null) {
            return;
        }
        if (blockRules.rateLimitKey() != null && !blockRules.rateLimitKey().isBlank()) {
            if (!ALLOWED_RATE_LIMIT_KEYS.contains(blockRules.rateLimitKey())) {
                throw new CustomException(PolicyErrorCode.INVALID_BLOCK_RULES);
            }
        }
        validateRegexPattern("ipPattern", blockRules.ipPattern());
        validateRegexPattern("userAgentPattern", blockRules.userAgentPattern());
    }

    private static void validateRegexPattern(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            Pattern.compile(value);
        } catch (PatternSyntaxException e) {
            throw new CustomException(PolicyErrorCode.INVALID_BLOCK_RULES);
        }
    }
}
