package kr.gilmok.api.ai.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record BlockRules(
        @JsonPropertyDescription("차단할 IP 대역 목록 (예: ['10.0.0.0/16'])")
        List<String> ipRanges,

        @JsonPropertyDescription("차단할 User-Agent 패턴 (예: 'Python-requests')")
        String userAgentPatterns
) {}