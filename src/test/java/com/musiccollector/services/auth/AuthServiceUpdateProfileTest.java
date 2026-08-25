package com.musiccollector.services.auth;

import com.musiccollector.entity.UserEntity;
import com.musiccollector.model.core.UserDto;
import com.musiccollector.repository.PhotoRepository;
import com.musiccollector.repository.UserRepository;
import com.musiccollector.services.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Renaming an account -- the one detail of it the app lets you change in place. */
@ExtendWith(MockitoExtension.class)
class AuthServiceUpdateProfileTest {

    @Mock private UserRepository userRepository;
    @Mock private JwtService jwtService;
    @Mock private PhotoRepository photoRepository;
    @Mock private StorageService storageService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(
                userRepository, new BCryptPasswordEncoder(), jwtService, photoRepository, storageService);
    }

    private UserEntity user(String displayName) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("jonas@example.test");
        user.setPasswordHash("hash");
        user.setDisplayName(displayName);
        user.setTokenVersion(2);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return user;
    }

    @Test
    void storesTheNewNameTrimmed() {
        UserEntity user = user("Jonas");

        UserDto dto = service.updateProfile(user, "  Jonas Weber  ");

        assertThat(user.getDisplayName()).isEqualTo("Jonas Weber");
        assertThat(dto.displayName()).isEqualTo("Jonas Weber");
    }

    @Test
    void aBlankNameClearsIt() {
        UserEntity user = user("Jonas");

        UserDto dto = service.updateProfile(user, "   ");

        assertThat(user.getDisplayName()).isNull();
        assertThat(dto.displayName()).isNull();
    }

    @Test
    void leavesTheSessionAlone() {
        UserEntity user = user("Jonas");
        int before = user.getTokenVersion();

        service.updateProfile(user, "Jonas Weber");

        // A rename is not a security event: bumping the version here would sign the person
        // out of every device for changing a label.
        assertThat(user.getTokenVersion()).isEqualTo(before);
    }
}
