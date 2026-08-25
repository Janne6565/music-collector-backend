package com.musiccollector.security;

import com.musiccollector.entity.UserEntity;
import com.musiccollector.model.exception.NotAuthenticatedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Reads the authenticated user out of the security context. */
@Component
public class CurrentUser {

    public UserEntity require() {
        return optional().orElseThrow(NotAuthenticatedException::new);
    }

    /**
     * The signed-in user, or empty.
     *
     * <p>Some endpoints are open but answer differently once they know who is asking: a
     * public profile renders for a stranger and gains an "Ask to be friends" button for
     * somebody with an account. Those read the viewer this way rather than being split into
     * an anonymous and an authenticated version of the same page.
     */
    public Optional<UserEntity> optional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof AuthenticationToken token)) {
            return Optional.empty();
        }
        return Optional.ofNullable(token.getPrincipal());
    }

    public Optional<UUID> optionalId() {
        return optional().map(UserEntity::getId);
    }
}
