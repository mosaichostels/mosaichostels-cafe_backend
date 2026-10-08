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
import java.util.concurrent.*;
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

    @Test
    void twoAdminsCannotDemoteEachOtherAtTheSameTime() throws Exception {
        User a = user("1", "alice", "ROLE_ADMIN");
        User b = user("2", "bob", "ROLE_ADMIN");
        when(userRepository.findById("1")).thenReturn(Optional.of(a));
        when(userRepository.findById("2")).thenReturn(Optional.of(b));
        // Hold each thread inside the "is there another admin?" read until its rival arrives (or
        // 300ms passes). Unserialised, both pass the check; serialised, the second sees the first's save.
        CyclicBarrier meet = new CyclicBarrier(2);
        when(userRepository.findAll()).thenAnswer(inv -> {
            // A real repository hands back fresh copies as of the read, not the live objects.
            List<User> snapshot = List.of(user("1", "alice", a.getRoles().toArray(new String[0])),
                    user("2", "bob", b.getRoles().toArray(new String[0])));
            try { meet.await(300, TimeUnit.MILLISECONDS); } catch (Exception ignored) { }
            return snapshot;
        });
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Boolean> demoteA = pool.submit(() -> tryDemote("1", "alice"));
        Future<Boolean> demoteB = pool.submit(() -> tryDemote("2", "bob"));
        boolean okA = demoteA.get(5, TimeUnit.SECONDS);
        boolean okB = demoteB.get(5, TimeUnit.SECONDS);
        pool.shutdown();

        assertTrue(okA ^ okB, "exactly one demotion must win (A=" + okA + ", B=" + okB + ")");
        assertTrue(a.getRoles().contains("ROLE_ADMIN") || b.getRoles().contains("ROLE_ADMIN"));
    }

    private boolean tryDemote(String id, String name) {
        try {
            userService.updateUser(id, name, null, Set.of("ROLE_STAFF"));
            return true;
        } catch (IllegalArgumentException e) {
            if (!e.getMessage().contains("last administrator")) throw e;
            return false;
        }
    }
}
