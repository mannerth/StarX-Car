package org.mannerth.starxcar.auth;

import org.mannerth.starxcar.auth.request.DriverRegistrationRequest;
import org.mannerth.starxcar.auth.request.LoginRequest;
import org.mannerth.starxcar.auth.request.PassengerRegistrationRequest;
import org.mannerth.starxcar.auth.request.RefreshTokenRequest;
import org.mannerth.starxcar.auth.response.TokenResponse;

public interface AuthService {
    TokenResponse login(LoginRequest request);

    TokenResponse refresh(RefreshTokenRequest request);

    TokenResponse registerPassenger(PassengerRegistrationRequest request);

    TokenResponse registerDriver(DriverRegistrationRequest request);
}
