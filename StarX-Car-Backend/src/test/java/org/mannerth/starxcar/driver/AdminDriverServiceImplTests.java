package org.mannerth.starxcar.driver;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mannerth.starxcar.auth.User;
import org.mannerth.starxcar.auth.UserMapper;
import org.mannerth.starxcar.car.CarMapper;
import org.mannerth.starxcar.constant.UserType;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminDriverServiceImplTests {
    private DriverMapper driverMapper;
    private UserMapper userMapper;
    private CarMapper carMapper;
    private AdminDriverServiceImpl service;

    @BeforeEach
    void setUp() {
        driverMapper = mock(DriverMapper.class);
        userMapper = mock(UserMapper.class);
        carMapper = mock(CarMapper.class);
        service = new AdminDriverServiceImpl(driverMapper, userMapper, carMapper);
    }

    @Test
    void listsDriversWithAssociatedUserData() {
        Driver driver = driver(7L, "pending");
        User user = driverUser(7L, "active");
        Page<Driver> result = new Page<>(1, 20, 1);
        result.setRecords(List.of(driver));
        given(driverMapper.selectPage(any(), any())).willReturn(result);
        given(userMapper.selectBatchIds(any())).willReturn(List.of(user));

        var response = service.list(1, 20, null, "pending");

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.records()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(7L);
            assertThat(item.username()).isEqualTo("driver7");
            assertThat(item.approvalStatus()).isEqualTo("pending");
        });
    }

    @Test
    void reviewsPendingActiveDriver() {
        Driver driver = driver(7L, "pending");
        User user = driverUser(7L, "active");
        given(driverMapper.selectById(7L)).willReturn(driver);
        given(userMapper.selectById(7L)).willReturn(user);

        var response = service.review(7L, DriverApprovalDecision.approved);

        assertThat(response.approvalStatus()).isEqualTo("approved");
        assertThat(response.workStatus()).isEqualTo("offline");
        verify(driverMapper).updateById(driver);
    }

    @Test
    void rejectsSecondReview() {
        given(driverMapper.selectById(7L)).willReturn(driver(7L, "approved"));

        assertThatThrownBy(() -> service.review(7L, DriverApprovalDecision.rejected))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
    }

    private Driver driver(Long id, String approvalStatus) {
        Driver driver = new Driver();
        driver.setId(id);
        driver.setPhoneNumber("13800000007");
        driver.setLicenseNumber("DL-7");
        driver.setApprovalStatus(approvalStatus);
        driver.setWorkStatus("offline");
        return driver;
    }

    private User driverUser(Long id, String status) {
        User user = new User();
        user.setId(id);
        user.setUsername("driver7");
        user.setType(UserType.driver);
        user.setStatus(status);
        return user;
    }
}
