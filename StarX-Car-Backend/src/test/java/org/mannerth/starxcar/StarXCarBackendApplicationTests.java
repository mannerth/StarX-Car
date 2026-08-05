package org.mannerth.starxcar;

import org.junit.jupiter.api.Test;
import org.mannerth.starxcar.auth.UserMapper;
import org.mannerth.starxcar.auth.User;
import org.mannerth.starxcar.car.CarMapper;
import org.mannerth.starxcar.car.Car;
import org.mannerth.starxcar.constant.UserType;
import org.mannerth.starxcar.driver.Driver;
import org.mannerth.starxcar.driver.DriverCarBinding;
import org.mannerth.starxcar.driver.DriverMapper;
import org.mannerth.starxcar.driver.DriverCarBindingMapper;
import org.mannerth.starxcar.driver.DriverWorkService;
import org.mannerth.starxcar.infra.image.ImageService;
import org.mannerth.starxcar.infra.image.response.ImageUploadResponse;
import org.mannerth.starxcar.order.pricing.PricingRuleMapper;
import org.mannerth.starxcar.order.pricing.PricingRuleTierMapper;
import org.mannerth.starxcar.order.pricing.AdminPricingRuleService;
import org.mannerth.starxcar.order.OrderService;
import org.mannerth.starxcar.order.OrderQueryService;
import org.mannerth.starxcar.order.RideOrderMapper;
import org.mannerth.starxcar.order.DriverLocationMapper;
import org.mannerth.starxcar.order.DriverDispatchService;
import org.mannerth.starxcar.order.OrderLifecycleService;
import org.mannerth.starxcar.order.OrderCompletionService;
import org.mannerth.starxcar.order.PassengerTripLocationService;
import org.mannerth.starxcar.order.OrderStatus;
import org.mannerth.starxcar.order.response.FareQuoteResponse;
import org.mannerth.starxcar.order.response.OrderCreatedResponse;
import org.mannerth.starxcar.order.response.OrderQuoteResponse;
import org.mannerth.starxcar.order.response.OrderPageResponse;
import org.mannerth.starxcar.order.response.OrderSummaryResponse;
import org.mannerth.starxcar.order.response.OrderAcceptedResponse;
import org.mannerth.starxcar.order.response.OrderStatusTransitionResponse;
import org.mannerth.starxcar.order.response.OrderCompletionResponse;
import org.mannerth.starxcar.order.response.DriverLocationResponse;
import org.mannerth.starxcar.order.response.RideRouteContextResponse;
import org.mannerth.starxcar.order.pricing.response.PricingRulePageResponse;
import org.mannerth.starxcar.security.JwtService;
import org.mannerth.starxcar.passenger.PassengerMapper;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class StarXCarBackendApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserMapper userMapper;

    @MockitoBean
    private PassengerMapper passengerMapper;

    @MockitoBean
    private DriverMapper driverMapper;

    @MockitoBean
    private DriverCarBindingMapper driverCarBindingMapper;

    @MockitoBean
    private CarMapper carMapper;

    @MockitoBean
    private PricingRuleMapper pricingRuleMapper;

    @MockitoBean
    private PricingRuleTierMapper pricingRuleTierMapper;

    @MockitoBean
    private ImageService imageService;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private OrderQueryService orderQueryService;

    @MockitoBean
    private RideOrderMapper rideOrderMapper;

    @MockitoBean
    private DriverLocationMapper driverLocationMapper;

    @MockitoBean
    private DriverWorkService driverWorkService;

    @MockitoBean
    private DriverDispatchService driverDispatchService;

    @MockitoBean
    private OrderLifecycleService orderLifecycleService;

    @MockitoBean
    private OrderCompletionService orderCompletionService;

    @MockitoBean
    private PassengerTripLocationService passengerTripLocationService;

    @MockitoBean
    private AdminPricingRuleService adminPricingRuleService;

    @Test
    void contextLoads() {
    }

    @Test
    void authenticationFailureUsesStandardResponseAndTraceId() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"missing\",\"password\":\"incorrect\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void openApiDocumentIsAvailable() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("StarX-Car API"));
    }

    @Test
    @WithMockUser
    void imageUploadReturnsStandardEnvelope() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", MediaType.IMAGE_PNG_VALUE,
                new byte[]{1, 2, 3});
        given(imageService.upload(org.mockito.ArgumentMatchers.any()))
                .willReturn(new ImageUploadResponse("image-key", "avatar.png", "https://image.example/avatar.png", null));

        mockMvc.perform(multipart("/api/image/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.key").value("image-key"))
                .andExpect(jsonPath("$.data.url").value("https://image.example/avatar.png"));
    }

    @Test
    @WithMockUser
    void imageUploadRequiresFilePart() throws Exception {
        mockMvc.perform(multipart("/api/image/upload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void multipartProfileUpdateUploadsAvatarBeforeSavingUser() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("passenger");
        user.setGender(org.mannerth.starxcar.constant.GenderEnum.male);
        user.setType(org.mannerth.starxcar.constant.UserType.passenger);
        user.setStatus("active");
        given(userMapper.selectById(1L)).willReturn(user);
        given(userMapper.exists(any())).willReturn(false);
        given(imageService.upload(any())).willReturn(new ImageUploadResponse(
                "avatar-key", "avatar.png", "https://image.example/avatar.png", null));
        String token = jwtService.createToken("1", Map.of("role", "passenger", "token_type", "access"));
        MockMultipartFile avatar = new MockMultipartFile("avatar", "avatar.png", MediaType.IMAGE_PNG_VALUE,
                new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/users/me").file(avatar)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .header("Authorization", "Bearer " + token)
                        .param("username", "passenger")
                        .param("gender", "male")
                        .param("realName", "乘客"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value("https://image.example/avatar.png"));

        verify(imageService).upload(any());
        verify(userMapper).updateById(user);
    }

    @Test
    void driverCanListAllActiveBoundCars() throws Exception {
        Driver driver = new Driver();
        driver.setId(1L);
        DriverCarBinding binding = new DriverCarBinding();
        binding.setDriverId(1L);
        binding.setCarId(7L);
        Car car = new Car();
        car.setId(7L);
        car.setCarNumber("粤A12345");
        given(driverMapper.selectById(1L)).willReturn(driver);
        given(driverCarBindingMapper.selectList(any())).willReturn(java.util.List.of(binding));
        given(carMapper.selectBatchIds(any())).willReturn(java.util.List.of(car));

        mockMvc.perform(get("/api/drivers/me/cars")
                        .header("Authorization", "Bearer " + driverToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(7));
    }

    @Test
    void driverCanSwitchToAnAlreadyBoundCar() throws Exception {
        Driver driver = new Driver();
        driver.setId(1L);
        driver.setCarId(3L);
        Car car = new Car();
        car.setId(7L);
        car.setVehicleStatus("active");
        given(driverMapper.selectById(1L)).willReturn(driver);
        given(carMapper.selectById(7L)).willReturn(car);
        given(driverCarBindingMapper.exists(any())).willReturn(true);

        mockMvc.perform(put("/api/drivers/me/current-car")
                        .header("Authorization", "Bearer " + driverToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carId\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.carId").value(7));

        verify(driverMapper).updateById(driver);
        verify(driverCarBindingMapper, never()).insert(any(DriverCarBinding.class));
    }

    @Test
    void driverCancellationKeepsBindingHistoryAndCancelsAccount() throws Exception {
        Driver driver = new Driver();
        driver.setId(1L);
        driver.setCarId(7L);
        User user = new User();
        user.setId(1L);
        user.setType(UserType.driver);
        user.setStatus("active");
        given(driverMapper.selectById(1L)).willReturn(driver);
        given(userMapper.selectById(1L)).willReturn(user);

        mockMvc.perform(delete("/api/drivers/me")
                        .header("Authorization", "Bearer " + driverToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(driverCarBindingMapper, never()).delete(any());
        verify(driverMapper, never()).deleteById(1L);
        verify(userMapper, never()).deleteById(1L);
        verify(driverMapper).updateById(driver);
        verify(userMapper).updateById(user);
        org.assertj.core.api.Assertions.assertThat(driver.getCarId()).isNull();
        org.assertj.core.api.Assertions.assertThat(user.getStatus()).isEqualTo("cancelled");
    }

    @Test
    void passengerCanQuoteAndCreateAnImmediateOrder() throws Exception {
        OrderQuoteResponse quote = new OrderQuoteResponse(1L, 1, 12_001, 1_801,
                new FareQuoteResponse(new java.math.BigDecimal("5.00"), new java.math.BigDecimal("31.20"),
                        new java.math.BigDecimal("6.50"), new java.math.BigDecimal("0.00"), new java.math.BigDecimal("42.70")));
        given(orderService.quote(any())).willReturn(quote);
        given(orderService.create(org.mockito.ArgumentMatchers.eq(1L), any()))
                .willReturn(new OrderCreatedResponse("O2026080420000000000000", OrderStatus.PENDING, quote));
        String body = """
                {"pickup":{"poiId":"pickup","name":"天安门","address":"北京市东城区","latitude":39.916527,"longitude":116.397128},
                 "destination":{"poiId":"destination","name":"外滩","address":"上海市黄浦区","latitude":31.230416,"longitude":121.473701}}
                """;

        mockMvc.perform(post("/api/orders/quote")
                        .header("Authorization", "Bearer " + passengerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.estimatedDistanceM").value(12001))
                .andExpect(jsonPath("$.data.fare.totalFare").value(42.70));

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + passengerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void orderQuoteRejectsDriverRoleAndOpenApiDocumentsOrderPaths() throws Exception {
        mockMvc.perform(post("/api/orders/quote")
                        .header("Authorization", "Bearer " + driverToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pickup":{"name":"天安门","latitude":39.916527,"longitude":116.397128},
                                 "destination":{"name":"外滩","latitude":31.230416,"longitude":121.473701}}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/orders/quote'].post").exists())
                .andExpect(jsonPath("$.paths['/api/orders'].post").exists());
    }

    @Test
    void passengerCanListOwnOrdersThroughStandardEnvelope() throws Exception {
        given(orderQueryService.listPassengerOrders(1L, 1, 20, null)).willReturn(new OrderPageResponse(1, 20, 1,
                java.util.List.of(new OrderSummaryResponse("O20260804001", OrderStatus.PENDING, "天安门", "外滩",
                        new java.math.BigDecimal("42.70"), java.time.LocalDateTime.of(2026, 8, 4, 20, 0), true))));

        mockMvc.perform(get("/api/passengers/me/orders")
                        .header("Authorization", "Bearer " + passengerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].orderNo").value("O20260804001"));
    }

    @Test
    void driverCanUpdateWorkStatusAndReportLocation() throws Exception {
        Driver driver = new Driver();
        driver.setId(1L);
        driver.setWorkStatus("online");
        given(driverWorkService.updateWorkStatus(1L, "online")).willReturn(driver);

        mockMvc.perform(put("/api/drivers/me/work-status")
                        .header("Authorization", "Bearer " + driverToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"online\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.workStatus").value("online"));

        mockMvc.perform(put("/api/drivers/me/location")
                        .header("Authorization", "Bearer " + driverToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude":39.916527,"longitude":116.397128,"accuracyM":8.5,
                                 "speedMps":12.3,"bearing":180,"recordedTime":"2026-08-05 10:00:00"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(driverWorkService).reportLocation(org.mockito.ArgumentMatchers.eq(1L), any());
    }

    @Test
    void driverWorkEndpointsRejectPassengerAndAreDocumented() throws Exception {
        mockMvc.perform(put("/api/drivers/me/work-status")
                        .header("Authorization", "Bearer " + passengerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"online\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/drivers/me/work-status'].put").exists())
                .andExpect(jsonPath("$.paths['/api/drivers/me/location'].put").exists());
    }

    @Test
    void driverCanListPendingOrdersAndAcceptOne() throws Exception {
        given(driverDispatchService.listPendingOrders(1L, 1, 20)).willReturn(new OrderPageResponse(1, 20, 1,
                java.util.List.of(new OrderSummaryResponse("O20260805001", OrderStatus.PENDING, "天安门", "外滩",
                        new java.math.BigDecimal("42.70"), java.time.LocalDateTime.of(2026, 8, 5, 10, 0), true))));
        given(driverDispatchService.acceptOrder(1L, "O20260805001")).willReturn(new OrderAcceptedResponse(
                "O20260805001", OrderStatus.ACCEPTED, 7L, java.time.LocalDateTime.of(2026, 8, 5, 10, 1)));

        mockMvc.perform(get("/api/drivers/me/orders/pending")
                        .header("Authorization", "Bearer " + driverToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].orderNo").value("O20260805001"));

        mockMvc.perform(post("/api/orders/O20260805001/accept")
                        .header("Authorization", "Bearer " + driverToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.carId").value(7));
    }

    @Test
    void driverDispatchEndpointsRejectPassengerAndAreDocumented() throws Exception {
        mockMvc.perform(get("/api/drivers/me/orders/pending")
                        .header("Authorization", "Bearer " + passengerToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/drivers/me/orders/pending'].get").exists())
                .andExpect(jsonPath("$.paths['/api/orders/{orderNo}/accept'].post").exists());
    }

    @Test
    void passengerCanCancelAndDriverCanArriveAndStartService() throws Exception {
        User passenger = new User();
        passenger.setId(1L);
        passenger.setType(UserType.passenger);
        passenger.setStatus("active");
        given(userMapper.selectById(1L)).willReturn(passenger);
        given(orderLifecycleService.cancelByPassenger(1L, "O20260805001", "行程变更"))
                .willReturn(new OrderStatusTransitionResponse("O20260805001", OrderStatus.CANCELLED,
                        java.time.LocalDateTime.of(2026, 8, 5, 10, 2), null));

        mockMvc.perform(post("/api/orders/O20260805001/cancel")
                        .header("Authorization", "Bearer " + passengerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"行程变更\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mockMvc.perform(post("/api/orders/O20260805001/cancel")
                        .header("Authorization", "Bearer " + passengerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + "a".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        User driver = new User();
        driver.setId(1L);
        driver.setType(UserType.driver);
        driver.setStatus("active");
        given(userMapper.selectById(1L)).willReturn(driver);
        given(orderLifecycleService.markDriverArrived(1L, "O20260805002"))
                .willReturn(new OrderStatusTransitionResponse("O20260805002", OrderStatus.DRIVER_ARRIVED,
                        java.time.LocalDateTime.of(2026, 8, 5, 10, 3), null));
        given(orderLifecycleService.startService(1L, "O20260805002"))
                .willReturn(new OrderStatusTransitionResponse("O20260805002", OrderStatus.IN_SERVICE,
                        java.time.LocalDateTime.of(2026, 8, 5, 10, 8), 300L));

        mockMvc.perform(post("/api/orders/O20260805002/arrive")
                        .header("Authorization", "Bearer " + driverToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRIVER_ARRIVED"));

        mockMvc.perform(post("/api/orders/O20260805002/start-service")
                        .header("Authorization", "Bearer " + driverToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_SERVICE"))
                .andExpect(jsonPath("$.data.waitingDurationS").value(300));
    }

    @Test
    void lifecycleEndpointsRejectWrongRoleAndAreDocumented() throws Exception {
        mockMvc.perform(post("/api/orders/O20260805001/arrive")
                        .header("Authorization", "Bearer " + passengerToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/orders/{orderNo}/cancel'].post").exists())
                .andExpect(jsonPath("$.paths['/api/orders/{orderNo}/arrive'].post").exists())
                .andExpect(jsonPath("$.paths['/api/orders/{orderNo}/start-service'].post").exists());
    }

    @Test
    void driverCanCompleteAnInServiceOrder() throws Exception {
        User driver = new User();
        driver.setId(1L);
        driver.setType(UserType.driver);
        driver.setStatus("active");
        given(userMapper.selectById(1L)).willReturn(driver);
        given(orderCompletionService.complete(1L, "O20260805002")).willReturn(new OrderCompletionResponse(
                "O20260805002", OrderStatus.COMPLETED, java.time.LocalDateTime.of(2026, 8, 5, 10, 30),
                1801L, 301L, new FareQuoteResponse(new java.math.BigDecimal("5.00"),
                new java.math.BigDecimal("31.20"), new java.math.BigDecimal("6.50"),
                new java.math.BigDecimal("0.50"), new java.math.BigDecimal("43.20"))));

        mockMvc.perform(post("/api/orders/O20260805002/complete")
                        .header("Authorization", "Bearer " + driverToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.actualDurationS").value(1801))
                .andExpect(jsonPath("$.data.fare.totalFare").value(43.20));
    }

    @Test
    void completionEndpointRejectsPassengerAndIsDocumented() throws Exception {
        mockMvc.perform(post("/api/orders/O20260805002/complete")
                        .header("Authorization", "Bearer " + passengerToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/orders/{orderNo}/complete'].post").exists());
    }

    @Test
    void passengerCanReadDriverLocationAndInServiceRouteContext() throws Exception {
        User passenger = new User();
        passenger.setId(1L);
        passenger.setType(UserType.passenger);
        passenger.setStatus("active");
        given(userMapper.selectById(1L)).willReturn(passenger);
        DriverLocationResponse location = new DriverLocationResponse(new java.math.BigDecimal("39.916527"),
                new java.math.BigDecimal("116.397128"), new java.math.BigDecimal("8.50"),
                new java.math.BigDecimal("12.30"), new java.math.BigDecimal("180.00"),
                java.time.LocalDateTime.of(2026, 8, 5, 10, 0), java.time.LocalDateTime.of(2026, 8, 5, 10, 0, 3), false);
        given(passengerTripLocationService.getDriverLocation(1L, "O20260805002")).willReturn(location);
        given(passengerTripLocationService.getRouteContext(1L, "O20260805002")).willReturn(
                new RideRouteContextResponse(location, "destination", "北京首都国际机场", "北京市顺义区",
                        new java.math.BigDecimal("40.079311"), new java.math.BigDecimal("116.603112"), 100, 60));

        mockMvc.perform(get("/api/orders/O20260805002/driver-location")
                        .header("Authorization", "Bearer " + passengerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latitude").value(39.916527))
                .andExpect(jsonPath("$.data.stale").value(false));
        mockMvc.perform(get("/api/orders/O20260805002/route-context")
                        .header("Authorization", "Bearer " + passengerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.destinationName").value("北京首都国际机场"))
                .andExpect(jsonPath("$.data.recommendedRefreshSeconds").value(60));
    }

    @Test
    void passengerTripLocationEndpointsRejectDriverAndAreDocumented() throws Exception {
        mockMvc.perform(get("/api/orders/O20260805002/driver-location")
                        .header("Authorization", "Bearer " + driverToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/orders/{orderNo}/driver-location'].get").exists())
                .andExpect(jsonPath("$.paths['/api/orders/{orderNo}/route-context'].get").exists());
    }

    @Test
    void adminCanListPricingRulesAndPricingRulePathsAreDocumented() throws Exception {
        User admin = new User();
        admin.setId(1L);
        admin.setType(UserType.admin);
        admin.setStatus("active");
        given(userMapper.selectById(1L)).willReturn(admin);
        given(adminPricingRuleService.list(1, 20, null)).willReturn(new PricingRulePageResponse(1, 20, 0,
                java.util.List.of()));

        mockMvc.perform(get("/api/admin/pricing-rules")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/api/admin/pricing-rules")
                        .header("Authorization", "Bearer " + passengerToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/admin/pricing-rules'].get").exists())
                .andExpect(jsonPath("$.paths['/api/admin/pricing-rules'].post").exists())
                .andExpect(jsonPath("$.paths['/api/admin/pricing-rules/{ruleId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/admin/pricing-rules/{ruleId}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/admin/pricing-rules/{ruleId}/activate'].post").exists())
                .andExpect(jsonPath("$.paths['/api/admin/pricing-rules/{ruleId}/archive'].post").exists());
    }

    @Test
    void orderApiFailuresUseStandardEnvelopeAcrossExpectedStatusCodes() throws Exception {
        given(orderService.quote(any())).willThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "计价规则配置不可用"));
        String quoteBody = """
                {"pickup":{"name":"天安门","latitude":39.916527,"longitude":116.397128},
                 "destination":{"name":"外滩","latitude":31.230416,"longitude":121.473701}}
                """;
        mockMvc.perform(post("/api/orders/quote")
                        .header("Authorization", "Bearer " + passengerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());

        User driver = new User();
        driver.setId(1L);
        driver.setType(UserType.driver);
        driver.setStatus("active");
        given(userMapper.selectById(1L)).willReturn(driver);
        given(orderCompletionService.complete(1L, "O409"))
                .willThrow(new ResponseStatusException(HttpStatus.CONFLICT, "订单状态已变化"));
        mockMvc.perform(post("/api/orders/O409/complete")
                        .header("Authorization", "Bearer " + driverToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.data").doesNotExist());

        User passenger = new User();
        passenger.setId(1L);
        passenger.setType(UserType.passenger);
        passenger.setStatus("active");
        given(userMapper.selectById(1L)).willReturn(passenger);
        given(passengerTripLocationService.getDriverLocation(1L, "O404"))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "订单暂无司机位置"));
        mockMvc.perform(get("/api/orders/O404/driver-location")
                        .header("Authorization", "Bearer " + passengerToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void openApiDocumentsBearerSecurityAndOrderRequestSchemas() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/orders/quote'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/admin/pricing-rules'].post.requestBody").exists())
                .andExpect(jsonPath("$.paths['/api/orders/{orderNo}/driver-location'].get.responses['200']").exists());
    }

    private String driverToken() {
        return jwtService.createToken("1", Map.of("role", "driver", "token_type", "access"));
    }

    private String passengerToken() {
        return jwtService.createToken("1", Map.of("role", "passenger", "token_type", "access"));
    }

    private String adminToken() {
        return jwtService.createToken("1", Map.of("role", "admin", "token_type", "access"));
    }

}
