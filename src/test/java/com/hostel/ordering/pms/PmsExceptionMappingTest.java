package com.hostel.ordering.pms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hostel.ordering.exception.GlobalExceptionHandler;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.ResponseEntity;

class PmsExceptionMappingTest {

    private static final String SECRET = "eZee said <Error>token=abc123 for booking 77</Error>";

    static Stream<Arguments> cases() {
        return Stream.of(
                args(PmsAuthException::new, 502, "PMS_AUTH"),
                args(PmsUpstreamException::new, 502, "PMS_UPSTREAM"),
                args(PmsBusyException::new, 503, "PMS_BUSY"),
                args(PmsNotEnabledException::new, 501, "PMS_NOT_ENABLED"),
                args(PmsFeatureDisabledException::new, 403, "PMS_FEATURE_DISABLED"),
                args(PmsUnconfirmedException::new, 409, "PMS_UNCONFIRMED"),
                args(PmsInProgressException::new, 409, "PMS_IN_PROGRESS"),
                args(PmsTargetBusyException::new, 409, "PMS_TARGET_BUSY"),
                args(PmsIdempotencyMismatchException::new, 409, "IDEMPOTENCY_MISMATCH"));
    }

    private static Arguments args(Function<String, PmsException> make, int status, String code) {
        return Arguments.of(make.apply(SECRET), status, code);
    }

    @ParameterizedTest
    @MethodSource("cases")
    void mapsStatusAndCodeWithoutLeakingMessage(PmsException ex, int status, String code) {
        ResponseEntity<Map<String, String>> res = new GlobalExceptionHandler().handlePms(ex);

        assertEquals(status, res.getStatusCode().value());
        assertEquals(code, ex.code());
        assertEquals(code, res.getBody().get("errorCode"));
        assertNotNull(res.getBody().get("message"));
        assertFalse(res.getBody().toString().contains("abc123"));
        assertFalse(res.getBody().toString().contains("eZee said"));
    }
}
