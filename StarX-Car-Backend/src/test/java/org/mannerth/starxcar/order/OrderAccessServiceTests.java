package org.mannerth.starxcar.order;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class OrderAccessServiceTests {

    @Test
    void hidesOrdersThatDoNotBelongToThePassengerOrDriver() {
        RideOrderMapper mapper = mock(RideOrderMapper.class);
        RideOrder order = new RideOrder();
        order.setOrderNo("O1");
        order.setPassengerId(1L);
        order.setDriverId(2L);
        given(mapper.selectList(any())).willReturn(List.of(order));
        OrderAccessService accessService = new OrderAccessService(mapper);

        assertThat(accessService.requirePassengerOrder("O1", 1L)).isSameAs(order);
        assertThatThrownBy(() -> accessService.requirePassengerOrder("O1", 3L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> accessService.requireDriverOrder("O1", 4L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
