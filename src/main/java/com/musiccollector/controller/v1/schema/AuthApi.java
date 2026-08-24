package com.musiccollector.controller.v1.schema;

import com.musiccollector.model.action.ForgotPasswordRequest;
import com.musiccollector.model.action.LoginRequest;
import com.musiccollector.model.action.RegisterRequest;
import com.musiccollector.model.action.ResetPasswordRequest;
import com.musiccollector.model.core.SessionDto;
import com.musiccollector.model.core.UserDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;

/**
 * Accounts, which are entirely optional.
 *
 * The app works with no account at all — everything lives on the device. Signing in adds
 * cross-device sync and nothing else, so none of these endpoints gate any feature.
 */
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
public interface AuthApi {

    @PostMapping("/register")
    @Operation(summary = "Create an account", description = "Also sets the refresh cookie.")
    @ApiResponse(responseCode = "200", description = "Account created and signed in")
    @ApiResponse(responseCode = "409", description = "That e-mail is already registered")
    @ApiResponse(responseCode = "429", description = "Too many attempts")
    ResponseEntity<SessionDto> register(
            @Valid @RequestBody RegisterRequest request,
            @RequestHeader(name = "X-Token-Mode", required = false) String tokenMode);

    @PostMapping("/login")
    @Operation(summary = "Sign in", description = "Also sets the refresh cookie.")
    @ApiResponse(responseCode = "200", description = "Signed in")
    @ApiResponse(responseCode = "401", description = "Wrong e-mail or password")
    @ApiResponse(responseCode = "429", description = "Too many attempts")
    ResponseEntity<SessionDto> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(name = "X-Token-Mode", required = false) String tokenMode);

    @PostMapping("/refresh")
    @Operation(summary = "Exchange the refresh cookie for a new access token")
    @ApiResponse(responseCode = "200", description = "A fresh access token")
    @ApiResponse(responseCode = "401", description = "No valid refresh cookie")
    ResponseEntity<SessionDto> refresh(
            @CookieValue(name = "mc_refresh", required = false) String cookieToken,
            @RequestHeader(name = "X-Refresh-Token", required = false) String bodyToken,
            @RequestHeader(name = "X-Token-Mode", required = false) String tokenMode);

    @PostMapping("/logout")
    @Operation(
            summary = "Sign out on every device",
            description = "Clears the refresh cookie and invalidates all outstanding refresh tokens.")
    @ApiResponse(responseCode = "204", description = "Signed out")
    ResponseEntity<Void> logout();

    @PostMapping("/forgot-password")
    @Operation(
            summary = "Send a password reset link",
            description = "Always answers 204, whether or not the address has an account — "
                    + "a different answer would turn this into a way to find out who is registered.")
    @ApiResponse(responseCode = "204", description = "Handled")
    @ApiResponse(responseCode = "429", description = "Too many attempts")
    ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request);

    @PostMapping("/reset-password")
    @Operation(
            summary = "Redeem a reset link and sign in",
            description = "Revokes every other session, since a reset may be locking somebody out.")
    @ApiResponse(responseCode = "200", description = "Password changed and signed in")
    @ApiResponse(responseCode = "400", description = "The link is expired, used, or not valid")
    ResponseEntity<SessionDto> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            @RequestHeader(name = "X-Token-Mode", required = false) String tokenMode);

    @GetMapping("/me")
    @Operation(summary = "The signed-in account")
    @ApiResponse(responseCode = "200", description = "The account")
    @ApiResponse(responseCode = "401", description = "Not signed in")
    ResponseEntity<UserDto> me();

    @DeleteMapping("/me")
    @Operation(
            summary = "Delete the account and everything synced to it",
            description = "Removes the server-side copy of the collection and every uploaded photo. "
                    + "The client's local collection is untouched -- it belongs to the device, not "
                    + "the account, and the app goes on working without one.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "401", description = "Not signed in")
    ResponseEntity<Void> deleteAccount();
}
