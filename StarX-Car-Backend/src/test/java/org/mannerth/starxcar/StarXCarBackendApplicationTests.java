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
import org.mannerth.starxcar.infra.image.ImageService;
import org.mannerth.starxcar.infra.image.response.ImageUploadResponse;
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
    private ImageService imageService;

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

    private String driverToken() {
        return jwtService.createToken("1", Map.of("role", "driver", "token_type", "access"));
    }

}
