package com.hostel.ordering.service;

import com.hostel.ordering.model.User;
import com.hostel.ordering.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceRulesTest {

    @Mock UserRepository userRepository;
    @Mock AuditService auditService;
    @Mock PasswordEncoder encoder;
    @InjectMocks UserService userService;

    private User user(String id, String name, String... roles) {
        User u = new User(name, "hash", Set.of(roles));
        u.setId(id);
        return u;
    }

    @Test
    void createUser_rejectsUnknownRoleShortPasswordAndMissingRoles() {
        assertThrows(IllegalArgumentException.class, () -> userService.createUser("a", "longenough", Set.of("ROLE_ROOT")));
        assertThrows(IllegalArgumentException.class, () -> userService.createUser("a", "short", Set.of("ROLE_STAFF")));
        assertThrows(IllegalArgumentException.class, () -> userService.createUser("a", null, Set.of("ROLE_STAFF")));
        assertThrows(IllegalArgumentException.class, () -> userService.createUser("a", "longenough", Set.of()));
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_acceptsBareRoleNamesAndStoresRolePrefix() {
        when(userRepository.findByUsername("kitchen")).thenReturn(Optional.empty());
        when(encoder.encode("longenough")).thenReturn("enc");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        User saved = userService.createUser("kitchen", "longenough", Set.of("STAFF"));

        assertEquals(Set.of("ROLE_STAFF"), saved.getRoles());
    }

    @Test
    void updateUser_refusesToDemoteTheLastAdmin() {
        User onlyAdmin = user("1", "admin", "ROLE_ADMIN");
        when(userRepository.findById("1")).thenReturn(Optional.of(onlyAdmin));
        when(userRepository.findAll()).thenReturn(List.of(onlyAdmin));

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateUser("1", "admin", null, Set.of("ROLE_STAFF")));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_passwordOrRoleChangeRevokesExistingTokens() {
        User staff = user("2", "kitchen", "ROLE_STAFF");
        when(userRepository.findById("2")).thenReturn(Optional.of(staff));
        when(encoder.encode("newpassword")).thenReturn("enc");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        long before = System.currentTimeMillis();

        User saved = userService.updateUser("2", "kitchen", "newpassword", Set.of("ROLE_STAFF"));

        assertNotNull(saved.getTokensValidFrom());
        assertTrue(saved.getTokensValidFrom() >= before);
    }

    @Test
    void updateUser_noCredentialChangeLeavesTokensAlone() {
        User staff = user("2", "kitchen", "ROLE_STAFF");
        when(userRepository.findById("2")).thenReturn(Optional.of(staff));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        User saved = userService.updateUser("2", "kitchen", null, Set.of("ROLE_STAFF"));

        assertNull(saved.getTokensValidFrom());
    }
}
