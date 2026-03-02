package kr.gilmok.api.queue.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class QueueRegisterRequest {

    @NotBlank(message = "eventId는 필수입니다.")
    private String eventId;
}
