package com.hostel.ordering.service;

import com.hostel.ordering.model.User;
import com.hostel.ordering.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceDeleteGuardTest {

    @Mock
    UserRepository userRepository;

    @Mock
    AuditService auditService;

    @InjectMocks
    UserService userService;

    private User user(String id, String name, String... roles) {
        User u = new User(name, "hash", Set.of(roles));
        u.setId(id);
        return u;
    }

    @Test
    void refusesToDeleteTheOnlyAdmin() {
        User onlyAdmin = user("1", "admin", "ROLE_ADMIN");
        when(userRepository.findById("1")).thenReturn(Optional.of(onlyAdmin));
        when(userRepository.findAll()).thenReturn(List.of(onlyAdmin));

        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> userService.deleteUser("1"));

        assertEquals("Cannot delete the last administrator. Create another admin first.", e.getMessage());
        verify(userRepository, never()).delete(any());
    }

    @Test
    void allowsDeletingAnAdminWhenAnotherRemains() {
        User first = user("1", "admin", "ROLE_ADMIN");
        User second = user("2", "naveen", "ROLE_ADMIN");
        when(userRepository.findById("1")).thenReturn(Optional.of(first));
        when(userRepository.findAll()).thenReturn(List.of(first, second));

        userService.deleteUser("1");

        verify(userRepository, times(1)).delete(first);
    }

    @Test
    void allowsDeletingStaffWhileOnlyOneAdminExists() {
        User admin = user("1", "admin", "ROLE_ADMIN");
        User staff = user("2", "kitchen", "ROLE_STAFF");
        when(userRepository.findById("2")).thenReturn(Optional.of(staff));

        userService.deleteUser("2");

        verify(userRepository, times(1)).delete(staff);
    }
}
