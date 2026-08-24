package com.musiccollector.controller.v1.implementation;

import com.musiccollector.configuration.OAuthProperties;
import com.musiccollector.controller.v1.schema.OAuthApi;
import com.musiccollector.entity.UserEntity;
import com.musiccollector.model.core.AuthProviderDto;
import com.musiccollector.services.auth.AuthService;
import com.musiccollector.services.auth.RefreshCookieFactory;
import com.musiccollector.services.auth.oauth.OAuthService;
import com.musiccollector.services.auth.oauth.OAuthUserResolver;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class OAuthController implements OAuthApi {

    private static final Logger log = LoggerFactory.getLogger(OAuthController.class);

    private final OAuthService oauthService;
    private final OAuthUserResolver userResolver;
    private final AuthService authService;
    private final RefreshCookieFactory refreshCookieFactory;
    private final OAuthProperties properties;

    @Override
    public ResponseEntity<List<AuthProviderDto>> providers() {
        return ResponseEntity.ok(oauthService.available());
    }

    @Override
    public ResponseEntity<Void> authorize(String provider) {
        return ResponseEntity.status(302)
                .location(URI.create(oauthService.authorizeUrl(provider)))
                .build();
    }

    @Override
    public ResponseEntity<Void> callback(String provider, String code, String state, String error) {
        if (error != null || code == null) {
            return redirect("/signin?oauthError=true");
        }
        try {
            oauthService.consumeState(provider, state);
            UserEntity user = userResolver.resolve(provider, oauthService.exchange(provider, code));
            AuthService.Session session = authService.issueFor(user);

            // The token goes in a cookie, never in the URL — a redirect target ends up in
            // browser history, server logs and the Referer header.
            return ResponseEntity.status(302)
                    .header(
                            HttpHeaders.SET_COOKIE,
                            refreshCookieFactory.create(session.refreshToken(), true).toString())
                    .location(URI.create(appUrl("/?signedIn=1")))
                    .build();
        } catch (RuntimeException e) {
            log.warn("External sign-in with {} failed", provider, e);
            return redirect("/signin?oauthError=true");
        }
    }

    private ResponseEntity<Void> redirect(String path) {
        return ResponseEntity.status(302).location(URI.create(appUrl(path))).build();
    }

    private String appUrl(String path) {
        String base = properties.publicBaseUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path;
    }
}
