package com.musiccollector.security;

import com.musiccollector.entity.UserEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.List;

/** The authenticated principal: the resolved user, not just the token subject. */
public class AuthenticationToken extends AbstractAuthenticationToken {

    private final UserEntity user;

    public AuthenticationToken(UserEntity user) {
        super(List.of());
        this.user = user;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public UserEntity getPrincipal() {
        return user;
    }
}
