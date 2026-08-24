package com.musiccollector.controller.v1.implementation;

import com.musiccollector.controller.v1.schema.AuthApi;
import com.musiccollector.model.action.LoginRequest;
import com.musiccollector.model.action.RegisterRequest;
import com.musiccollector.model.core.SessionDto;
import com.musiccollector.model.core.UserDto;
import com.musiccollector.security.CurrentUser;
import com.musiccollector.services.auth.AuthService;
import com.musiccollector.services.auth.RefreshCookieFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService authService;
    private final RefreshCookieFactory refreshCookieFactory;
    private final CurrentUser currentUser;

    @Override
    public ResponseEntity<SessionDto> register(RegisterRequest request) {
        return withRefreshCookie(authService.register(request));
    }

    @Override
    public ResponseEntity<SessionDto> login(LoginRequest request) {
        return withRefreshCookie(authService.login(request));
    }

    @Override
    public ResponseEntity<SessionDto> refresh(String refreshToken) {
        // Reissued on every refresh, so an active session's cookie keeps sliding forward
        // rather than expiring mid-use.
        return withRefreshCookie(authService.refresh(refreshToken));
    }

    @Override
    public ResponseEntity<Void> logout() {
        authService.signOutEverywhere(currentUser.require());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.clear().toString())
                .build();
    }

    @Override
    public ResponseEntity<UserDto> me() {
        return ResponseEntity.ok(AuthService.toDto(currentUser.require()));
    }

    private ResponseEntity<SessionDto> withRefreshCookie(AuthService.Session session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.create(session.refreshToken()).toString())
                .body(session.body());
    }
}
