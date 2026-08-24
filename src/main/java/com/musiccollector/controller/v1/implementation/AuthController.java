package com.musiccollector.controller.v1.implementation;

import com.musiccollector.controller.v1.schema.AuthApi;
import com.musiccollector.model.action.ForgotPasswordRequest;
import com.musiccollector.model.action.LoginRequest;
import com.musiccollector.model.action.RegisterRequest;
import com.musiccollector.model.action.ResetPasswordRequest;
import com.musiccollector.model.core.SessionDto;
import com.musiccollector.model.core.TokenMode;
import com.musiccollector.model.core.UserDto;
import com.musiccollector.security.CurrentUser;
import com.musiccollector.services.auth.AuthService;
import com.musiccollector.services.auth.PasswordResetService;
import com.musiccollector.services.auth.RefreshCookieFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final RefreshCookieFactory refreshCookieFactory;
    private final CurrentUser currentUser;

    @Override
    public ResponseEntity<SessionDto> register(RegisterRequest request, String tokenMode) {
        return deliver(authService.register(request), TokenMode.fromHeader(tokenMode));
    }

    @Override
    public ResponseEntity<SessionDto> login(LoginRequest request, String tokenMode) {
        return deliver(authService.login(request), TokenMode.fromHeader(tokenMode));
    }

    @Override
    public ResponseEntity<SessionDto> refresh(String cookieToken, String bodyToken, String tokenMode) {
        TokenMode mode = TokenMode.fromHeader(tokenMode);
        // A native client has no cookie, so it sends the token it stored instead.
        String presented = mode == TokenMode.DIRECT ? bodyToken : cookieToken;
        // A cookie that arrived with no Max-Age is a session cookie, so this sign-in chose
        // not to be remembered; reissuing it as durable would quietly override that.
        boolean remember = mode == TokenMode.DIRECT || cookieToken != null;
        return deliver(authService.refresh(presented, remember), mode);
    }

    @Override
    public ResponseEntity<Void> forgotPassword(ForgotPasswordRequest request) {
        passwordResetService.request(request.email());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<SessionDto> resetPassword(ResetPasswordRequest request, String tokenMode) {
        return deliver(
                authService.issueFor(passwordResetService.redeem(request.token(), request.password())),
                TokenMode.fromHeader(tokenMode));
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

    @Override
    public ResponseEntity<Void> deleteAccount() {
        authService.deleteAccount(currentUser.require());
        // The refresh cookie now points at an account that no longer exists, so it is
        // cleared here rather than left to expire on its own.
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.clear().toString())
                .build();
    }

    private ResponseEntity<SessionDto> deliver(AuthService.Session session, TokenMode mode) {
        if (mode == TokenMode.DIRECT) {
            SessionDto body = session.body();
            return ResponseEntity.ok(new SessionDto(body.accessToken(), session.refreshToken(), body.user()));
        }
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshCookieFactory.create(session.refreshToken(), session.remember()).toString())
                .body(session.body());
    }
}
