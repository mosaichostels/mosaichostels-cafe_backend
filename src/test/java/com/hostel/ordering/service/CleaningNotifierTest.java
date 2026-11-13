package com.hostel.ordering.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.hostel.ordering.model.User;
import com.hostel.ordering.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CleaningNotifierTest {

    UserRepository users;
    FCMNotificationService fcm;
    CleaningNotifier notifier;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        fcm = mock(FCMNotificationService.class);
        notifier = new CleaningNotifier(users, fcm);
    }

    private static User user(String id, String token, String... roles) {
        User u = new User();
        u.setId(id);
        u.setUsername(id);
        u.setFcmToken(token);
        u.setRoles(Set.of(roles));
        return u;
    }

    @Test
    void listSubmittedPushesHousekeepingWithTokenOnly() {
        when(users.findByRolesContaining("ROLE_HOUSEKEEPING")).thenReturn(
                List.of(user("a", "tokA", "ROLE_HOUSEKEEPING"), user("b", "", "ROLE_HOUSEKEEPING"),
                        user("c", null, "ROLE_HOUSEKEEPING")));
        notifier.listSubmitted(3);
        ArgumentCaptor<Map<String, String>> data = ArgumentCaptor.forClass(Map.class);
        verify(fcm, times(1)).sendNotificationToToken(eq("tokA"), eq("Cleaning list"), eq("3 bed(s) to clean"), data.capture());
        assertEquals(Map.of("type", "CLEANING_LIST"), data.getValue());
    }

    private void todayBody(int added, int tasks, String expected) {
        when(users.findByRolesContaining("ROLE_HOUSEKEEPING")).thenReturn(List.of(user("a", "tokA", "ROLE_HOUSEKEEPING")));
        notifier.todaySubmitted(added, tasks);
        verify(fcm).sendNotificationToToken(eq("tokA"), eq("Cleaning list"), eq(expected), eq(Map.of("type", "CLEANING_LIST")));
    }

    @Test
    void todayWordingBedsAndTasks() { todayBody(3, 12, "Today's cleaning: 3 bed(s), 12 task(s)"); }

    @Test
    void todayWordingBedsOnly() { todayBody(3, 0, "Today's cleaning: 3 bed(s)"); }

    @Test
    void todayWordingTasksOnly() { todayBody(0, 12, "Today's cleaning: 12 task(s)"); }

    @Test
    void todayWordingNeither() { todayBody(0, 0, "Today's cleaning is ready"); }

    @Test
    void listSubmittedZeroSendsNothing() {
        notifier.listSubmitted(0);
        verifyNoInteractions(fcm, users);
    }

    @Test
    void cleanedNotifiesAdminAndSuperadminOnceEach() {
        User both = user("x", "tokX", "ROLE_ADMIN", "ROLE_SUPERADMIN");
        when(users.findByRolesContaining("ROLE_ADMIN")).thenReturn(List.of(both, user("y", "", "ROLE_ADMIN")));
        when(users.findByRolesContaining("ROLE_SUPERADMIN")).thenReturn(List.of(both, user("z", "tokZ", "ROLE_SUPERADMIN")));
        notifier.cleaned("Dorm 1 Bed 2", "hk1", "CONFIRMED");
        ArgumentCaptor<Map<String, String>> data = ArgumentCaptor.forClass(Map.class);
        verify(fcm, times(1)).sendNotificationToToken(eq("tokX"), eq("Bed cleaned"),
                eq("Dorm 1 Bed 2 cleaned by hk1. eZee updated."), data.capture());
        verify(fcm, times(1)).sendNotificationToToken(eq("tokZ"), any(), any(), any());
        verifyNoMoreInteractions(fcm);
        assertEquals(Map.of("type", "CLEANING_DONE"), data.getValue());
    }

    @Test
    void cleanedBodyForOtherOutcomes() {
        when(users.findByRolesContaining("ROLE_ADMIN")).thenReturn(List.of(user("x", "t", "ROLE_ADMIN")));
        when(users.findByRolesContaining("ROLE_SUPERADMIN")).thenReturn(List.of());
        for (String o : List.of("NOT_CONFIRMED", "OFF", "FAILED")) {
            notifier.cleaned("R", "u", o);
        }
        verify(fcm, times(3)).sendNotificationToToken(eq("t"), eq("Bed cleaned"),
                eq("R cleaned by u. eZee not updated: set it Clean in eZee."), any());
    }

    @Test
    void throwingFcmDoesNotPropagateAndOthersStillSent() {
        when(users.findByRolesContaining("ROLE_HOUSEKEEPING")).thenReturn(
                List.of(user("a", "t1", "ROLE_HOUSEKEEPING"), user("b", "t2", "ROLE_HOUSEKEEPING")));
        doThrow(new RuntimeException("down")).when(fcm).sendNotificationToToken(eq("t1"), any(), any(), any());
        assertDoesNotThrow(() -> notifier.listSubmitted(1));
        verify(fcm).sendNotificationToToken(eq("t2"), any(), any(), any());
    }

    @Test
    void throwingRepositoryDoesNotPropagate() {
        when(users.findByRolesContaining(anyString())).thenThrow(new RuntimeException("db"));
        assertDoesNotThrow(() -> notifier.cleaned("R", "u", "OFF"));
        assertDoesNotThrow(() -> notifier.listSubmitted(2));
    }
}
