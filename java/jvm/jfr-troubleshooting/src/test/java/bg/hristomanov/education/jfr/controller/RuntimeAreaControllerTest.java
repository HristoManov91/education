package bg.hristomanov.education.jfr.controller;

import bg.hristomanov.education.jfr.service.RuntimeAreaService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeAreaControllerTest {

    private final RuntimeAreaController controller = new RuntimeAreaController(new RuntimeAreaService());

    @Test
    void delegatesValidWorkloadToService() {
        assertThat(controller.calculate(2, 7).evaluatedShapes()).isEqualTo(14L);
    }

    @Test
    void invalidWorkloadReturnsClientErrorRatherThanServerError() {
        assertThatThrownBy(() -> controller.calculate(0, 1))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
