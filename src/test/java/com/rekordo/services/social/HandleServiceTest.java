package com.rekordo.services.social;

import com.rekordo.entity.HandleChangeEntity;
import com.rekordo.entity.UserEntity;
import com.rekordo.model.core.HandleAvailabilityDto;
import com.rekordo.model.exception.HandleChangeLimitException;
import com.rekordo.model.exception.HandleUnavailableException;
import com.rekordo.repository.HandleChangeRepository;
import com.rekordo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HandleServiceTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock private UserRepository userRepository;
    @Mock private HandleChangeRepository handleChangeRepository;

    @InjectMocks private HandleService service;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(USER);
        when(userRepository.findById(USER)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(handleChangeRepository.findRecentClaimsByOthers(anyString(), any(), any())).thenReturn(List.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", ".jonas", "jonas.", "jo..nas", "jonas meyer", "jonas_meyer", "jonas!"})
    void refusesHandlesThatAreNotLettersNumbersAndSingleDots(String handle) {
        assertThat(service.check(USER, handle).reason()).isEqualTo(HandleAvailabilityDto.Reason.MALFORMED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"jonasmeyer", "friedrich.k", "a1b", "j.o.n.a.s"})
    void acceptsTheShapesTheClaimScreenPromises(String handle) {
        assertThat(service.check(USER, handle).available()).isTrue();
    }

    @Test
    void refusesWordsTheAppNeedsForItsOwnPaths() {
        // The public profile is /@handle and its wishlist /@handle/wishlist, so a collector
        // called @wishlist would shadow a route.
        assertThat(service.check(USER, "wishlist").reason()).isEqualTo(HandleAvailabilityDto.Reason.RESERVED);
        assertThat(service.check(USER, "admin").reason()).isEqualTo(HandleAvailabilityDto.Reason.RESERVED);
    }

    @Test
    void treatsAHandleWithDifferentCaseAsTaken() {
        when(userRepository.existsByHandleIgnoreCase("anna")).thenReturn(true);

        // @Anna beside an existing @anna would be a convincing impersonation of it.
        assertThat(service.check(USER, "Anna").reason()).isEqualTo(HandleAvailabilityDto.Reason.TAKEN);
    }

    @Test
    void keepsAHandleSomebodyGaveUpOutOfCirculationForAWhile() {
        HandleChangeEntity previous = new HandleChangeEntity();
        previous.setHandle("jonasmeyer");
        when(handleChangeRepository.findRecentClaimsByOthers(any(), any(), any())).thenReturn(List.of(previous));

        // Otherwise the next claimant inherits every link and pending request aimed at it.
        assertThat(service.check(USER, "jonasmeyer").reason()).isEqualTo(HandleAvailabilityDto.Reason.TAKEN);
    }

    @Test
    void storesTheHandleLowercasedAndWithoutItsAt() {
        service.claim(USER, "@JonasMeyer");

        assertThat(user.getHandle()).isEqualTo("jonasmeyer");
    }

    @Test
    void recordsTheClaimSoTheLimitHasSomethingToCountFrom() {
        service.claim(USER, "jonasmeyer");

        verify(handleChangeRepository).save(any(HandleChangeEntity.class));
    }

    @Test
    void savingTheHandleYouAlreadyHaveSpendsNothing() {
        user.setHandle("jonasmeyer");

        service.claim(USER, "@JonasMeyer");

        // Not a change, so it must not be recorded as one — otherwise re-saving the Sharing
        // screen twice would use up the year's allowance.
        verify(handleChangeRepository, never()).save(any());
    }

    @Test
    void refusesAThirdChangeInsideTheYear() {
        user.setHandle("jonasmeyer");
        // The first claim plus two changes.
        when(handleChangeRepository.countByUserIdAndChangedAtAfter(any(), any())).thenReturn(3L);

        assertThatThrownBy(() -> service.claim(USER, "somethingelse"))
                .isInstanceOf(HandleChangeLimitException.class);
    }

    @Test
    void doesNotCountTheFirstClaimAgainstTheChangeAllowance() {
        when(handleChangeRepository.countByUserIdAndChangedAtAfter(any(), any())).thenReturn(1L);

        assertThat(service.changesRemaining(USER)).isEqualTo(2);
    }

    @Test
    void refusesAHandleSomebodyElseHolds() {
        when(userRepository.existsByHandleIgnoreCase("jonasmeyer")).thenReturn(true);

        assertThatThrownBy(() -> service.claim(USER, "jonasmeyer"))
                .isInstanceOf(HandleUnavailableException.class)
                .satisfies(thrown -> assertThat(((HandleUnavailableException) thrown).getReason())
                        .isEqualTo(HandleAvailabilityDto.Reason.TAKEN));
    }

    @Test
    void reportsTheFirstClaimAsHavingTheFullAllowanceLeft() {
        when(handleChangeRepository.countByUserIdAndChangedAtAfter(any(), any())).thenReturn(0L);

        assertThat(service.changesRemaining(USER)).isEqualTo(2);
    }

    @Test
    void stampsTheChangeWithWhenItHappened() {
        Instant before = Instant.now().minusSeconds(1);

        service.claim(USER, "jonasmeyer");

        verify(handleChangeRepository).save(org.mockito.ArgumentMatchers.argThat(
                change -> change.getChangedAt().isAfter(before) && change.getUserId().equals(USER)));
    }
}
