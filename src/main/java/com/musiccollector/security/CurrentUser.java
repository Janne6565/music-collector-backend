package com.musiccollector.security;

import com.musiccollector.entity.UserEntity;
import com.musiccollector.model.exception.NotAuthenticatedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Reads the authenticated user out of the security context. */
@Component
public class CurrentUser {

    public UserEntity require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof AuthenticationToken token)) {
            throw new NotAuthenticatedException();
        }
        return token.getPrincipal();
    }
}
