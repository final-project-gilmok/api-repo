package kr.gilmok.api.token.interceptor;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import kr.gilmok.api.token.exception.AdmissionTokenErrorCode;
import kr.gilmok.api.token.repository.AdmissionTokenBlocklistRepository;
import kr.gilmok.api.token.service.JwtProvider;
import kr.gilmok.common.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdmissionTokenInterceptor 단위 테스트")
class AdmissionTokenInterceptorTest {

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private AdmissionTokenBlocklistRepository admissionTokenBlocklistRepository;

    @InjectMocks
    private AdmissionTokenInterceptor interceptor;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private static final String TOKEN = "valid.jwt.token";
    private static final String COOKIE_NAME = "admissionToken_TEST-CODE-001";

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setRequestURI("/reservations/TEST-CODE-001/confirm");
        request.setCookies(new Cookie(COOKIE_NAME, TOKEN));
    }

    @Test
    @DisplayName("유효하고 미사용 토큰이면 통과한다")
    void preHandle_validToken_passes() throws Exception {
        // given
        Claims claims = buildClaims("test-jti", "ADMITTED");
        given(jwtProvider.validateToken(TOKEN)).willReturn(true);
        given(jwtProvider.getClaims(TOKEN)).willReturn(claims);
        given(admissionTokenBlocklistRepository.isUsed("test-jti")).willReturn(false);

        // when
        boolean result = interceptor.preHandle(request, response, new Object());

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("이미 사용된 토큰(blacklist)이면 ALREADY_USED_ADMISSION_TOKEN 예외가 발생한다")
    void preHandle_alreadyUsed_throwsException() {
        // given
        Claims claims = buildClaims("used-jti", "ADMITTED");
        given(jwtProvider.validateToken(TOKEN)).willReturn(true);
        given(jwtProvider.getClaims(TOKEN)).willReturn(claims);
        given(admissionTokenBlocklistRepository.isUsed("used-jti")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(AdmissionTokenErrorCode.ALREADY_USED_ADMISSION_TOKEN));
    }

    @Test
    @DisplayName("토큰이 없으면 MISSING_ADMISSION_TOKEN 예외가 발생한다")
    void preHandle_missingToken_throwsException() {
        // given
        request.setCookies(); // 쿠키 없음

        // when & then
        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(AdmissionTokenErrorCode.MISSING_ADMISSION_TOKEN));
    }

    @Test
    @DisplayName("만료되거나 유효하지 않은 토큰이면 INVALID_ADMISSION_TOKEN 예외가 발생한다")
    void preHandle_invalidToken_throwsException() {
        // given
        given(jwtProvider.validateToken(TOKEN)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(AdmissionTokenErrorCode.INVALID_ADMISSION_TOKEN));
    }

    @Test
    @DisplayName("토큰에 jti가 누락되었으면 INVALID_ADMISSION_TOKEN 예외가 발생한다")
    void preHandle_missingJti_throwsException() {
        // given
        Claims claims = buildClaims(null, "ADMITTED"); // jti is null
        given(jwtProvider.validateToken(TOKEN)).willReturn(true);
        given(jwtProvider.getClaims(TOKEN)).willReturn(claims);

        // when & then
        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(AdmissionTokenErrorCode.INVALID_ADMISSION_TOKEN));
    }

    @Test
    @DisplayName("ADMITTED가 아닌 status를 가진 토큰이면 NOT_ADMITTED_STATUS 예외가 발생한다")
    void preHandle_wrongStatus_throwsException() {
        // given
        Claims claims = buildClaims("some-jti", "WAITING");
        given(jwtProvider.validateToken(TOKEN)).willReturn(true);
        given(jwtProvider.getClaims(TOKEN)).willReturn(claims);

        // when & then
        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(AdmissionTokenErrorCode.NOT_ADMITTED_STATUS));
    }

    private Claims buildClaims(String jti, String status) {
        Claims claims = mock(Claims.class);
        given(claims.getId()).willReturn(jti);
        given(claims.get("status", String.class)).willReturn(status);
        given(claims.get("evt")).willReturn("1");
        given(claims.get("sub")).willReturn("testuser");
        return claims;
    }
}
