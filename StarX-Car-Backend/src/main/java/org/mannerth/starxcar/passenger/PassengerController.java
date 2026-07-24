package org.mannerth.starxcar.passenger;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.mannerth.starxcar.auth.UserMapper;
import org.mannerth.starxcar.security.CurrentUser;
import org.mannerth.starxcar.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/passengers/me")
@PreAuthorize("hasRole('PASSENGER')")
@Tag(name = "乘客资料", description = "仅限当前乘客操作自己的乘客资料")
@SecurityRequirement(name = "bearerAuth")
public class PassengerController {
    private final PassengerMapper passengerMapper;
    private final UserMapper userMapper;

    public PassengerController(PassengerMapper passengerMapper, UserMapper userMapper) {
        this.passengerMapper = passengerMapper;
        this.userMapper = userMapper;
    }

    @GetMapping
    @Operation(summary = "获取我的乘客资料", description = "仅限 passenger 角色；手机号展示值由数据库触发器生成。")
    public Passenger get(@AuthenticationPrincipal String principal) {
        return requirePassenger(CurrentUser.id(principal));
    }

    @PutMapping
    @Operation(summary = "更新我的手机号", description = "仅限 passenger 角色；修改手机号后由数据库触发器更新尾号和脱敏展示号码。")
    public Passenger update(@AuthenticationPrincipal String principal, @Valid @RequestBody PassengerUpdateRequest request) {
        Passenger passenger = requirePassenger(CurrentUser.id(principal));
        passenger.setPhoneNumber(request.phoneNumber());
        passengerMapper.updateById(passenger);
        return requirePassenger(passenger.getId());
    }

    @DeleteMapping
    @Operation(summary = "注销乘客账号", description = "删除当前乘客资料及对应用户账号，此操作不可恢复。")
    @Transactional
    public ApiResponse<Void> delete(@AuthenticationPrincipal String principal) {
        Long userId = CurrentUser.id(principal);
        passengerMapper.deleteById(userId);
        userMapper.deleteById(userId);
        return ApiResponse.success(null);
    }

    private Passenger requirePassenger(Long userId) {
        Passenger passenger = passengerMapper.selectById(userId);
        if (passenger == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "乘客不存在");
        }
        return passenger;
    }
}
