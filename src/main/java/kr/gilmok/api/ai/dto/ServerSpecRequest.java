package kr.gilmok.api.ai.dto;

public record ServerSpecRequest(
        Integer cpuCores,
        Integer memoryGb,
        String instanceType,
        Integer replicaCount
) {
    public boolean hasAnySpec() {
        return cpuCores != null || memoryGb != null
                || (instanceType != null && !instanceType.isBlank())
                || replicaCount != null;
    }
}
