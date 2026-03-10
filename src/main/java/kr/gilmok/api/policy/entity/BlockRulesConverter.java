package kr.gilmok.api.policy.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import kr.gilmok.api.policy.vo.BlockRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Converter
@RequiredArgsConstructor
public class BlockRulesConverter implements AttributeConverter<BlockRules, String> {

    private final ObjectMapper objectMapper;

    @Override
    public String convertToDatabaseColumn(BlockRules attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.error("Error converting BlockRules to JSON", e);
            throw new RuntimeException("JSON conversion error", e);
        }
    }


    @Override
    public BlockRules convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return BlockRules.empty(); // 데이터가 없으면 빈 규칙 반환
        }
        try {
            return objectMapper.readValue(dbData, BlockRules.class);
        } catch (JsonProcessingException e) {
            log.error("Error converting JSON to BlockRules", e);
            return BlockRules.empty(); // 에러 발생 시 안전하게 빈 규칙 반환
        }
    }
}