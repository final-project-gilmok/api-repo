package kr.gilmok.api.policy.vo;

public record BlockRules(
        String ipPattern,
        String userAgentPattern,
        String rateLimitKey
) {
    public static BlockRules empty() {
        return new BlockRules(null, null, null);
    }
}