package com.hostel.ordering.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hostel.ordering.model.CleaningListEntry;
import com.hostel.ordering.pms.PmsFeatureDisabledException;
import com.hostel.ordering.pms.PmsNotEnabledException;
import com.hostel.ordering.pms.PmsRoomStatusWriteService;
import com.hostel.ordering.pms.PmsWriteResult;
import com.hostel.ordering.pms.dto.RoomStatusWriteRequest;
import com.hostel.ordering.pms.dto.RoomStatusWriteResult;
import com.hostel.ordering.repository.CleaningListRepository;
import com.hostel.ordering.service.CleaningListService.BedInput;
import com.hostel.ordering.service.CleaningListService.DoneResult;
import com.hostel.ordering.service.CleaningListService.SubmitResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

class CleaningListServiceTest {

    CleaningListRepository repo;
    MongoTemplate mongo;
    PmsRoomStatusWriteService ezee;
    CleaningNotifier notifier;
    CleaningListService service;

    @BeforeEach
    void setUp() {
        repo = mock(CleaningListRepository.class);
        mongo = mock(MongoTemplate.class);
        ezee = mock(PmsRoomStatusWriteService.class);
        notifier = mock(CleaningNotifier.class);
        service = new CleaningListService(repo, mongo, ezee, notifier);
    }

    private static CleaningListEntry entry(String id, String roomId, String unitId, String name, long at) {
        CleaningListEntry e = new CleaningListEntry();
        e.setId(id);
        e.setRoomId(roomId);
        e.setUnitId(unitId);
        e.setRoomName(name);
        e.setAddedAt(at);
        return e;
    }

    private void present(String id) {
        when(mongo.findAndRemove(any(Query.class), eq(CleaningListEntry.class)))
                .thenReturn(entry(id, "10", "20", "Dorm 2 Bed 1", 1L));
    }

    private static PmsWriteResult<RoomStatusWriteResult> result(boolean confirmed) {
        return new PmsWriteResult<>(new RoomStatusWriteResult("10", "20", "CLEAN", "Clean", confirmed), confirmed);
    }

    @Test
    void submitAddsNewAndSkipsDuplicates() {
        when(repo.findByRoomIdAndUnitId("1", "2")).thenReturn(Optional.empty());
        when(repo.findByRoomIdAndUnitId("3", "4")).thenReturn(Optional.of(entry("x", "3", "4", "B", 1)));
        SubmitResult r = service.submit(List.of(new BedInput("1", "2", "A"), new BedInput("3", "4", "B")), "admin");
        assertEquals(1, r.added());
        assertEquals(1, r.alreadyListed());
        ArgumentCaptor<CleaningListEntry> c = ArgumentCaptor.forClass(CleaningListEntry.class);
        verify(repo, times(1)).save(c.capture());
        assertEquals("A", c.getValue().getRoomName());
        assertEquals("admin", c.getValue().getAddedBy());
        assertTrue(c.getValue().getAddedAt() > 0);
    }

    @Test
    void submitRejectsBadInputAndSavesNothing() {
        BedInput ok = new BedInput("1", "2", "A");
        List<List<BedInput>> bad = new ArrayList<>();
        bad.add(List.of(ok, new BedInput("12a", "2", "A")));
        bad.add(List.of(ok, new BedInput("1", "", "A")));
        bad.add(List.of(ok, new BedInput("1", "2", "  ")));
        bad.add(List.of(ok, new BedInput("1", "2", "x".repeat(81))));
        bad.add(List.of(ok, new BedInput("1", "2", "a\nb")));
        bad.add(List.of());
        List<BedInput> many = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            many.add(new BedInput("1", String.valueOf(i), "A"));
        }
        bad.add(many);
        for (List<BedInput> beds : bad) {
            assertThrows(IllegalArgumentException.class, () -> service.submit(beds, "admin"));
        }
        assertThrows(IllegalArgumentException.class, () -> service.submit(null, "admin"));
        verify(repo, never()).save(any());
    }

    @Test
    void submitAccepts80CharNameAnd200Beds() {
        List<BedInput> beds = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            beds.add(new BedInput("1", String.valueOf(i), "x".repeat(80)));
        }
        assertEquals(200, service.submit(beds, "admin").added());
    }

    @Test
    void listSortsNaturallyThenByAddedAt() {
        when(repo.findAll()).thenReturn(List.of(
                entry("a", "1", "1", "Dorm 10", 1), entry("b", "1", "2", "Dorm 2", 5),
                entry("c", "1", "3", "Dorm 2", 3)));
        List<String> ids = service.list().stream().map(CleaningListEntry::getId).toList();
        assertEquals(List.of("c", "b", "a"), ids);
    }

    @Test
    void removeReportsPresence() {
        when(mongo.findAndRemove(any(Query.class), eq(CleaningListEntry.class)))
                .thenReturn(entry("a", "1", "1", "A", 1)).thenReturn(null);
        assertTrue(service.remove("a"));
        assertFalse(service.remove("a"));
    }

    @Test
    void doneWritesEzeeOnceWithExpectedArgs() {
        present("e1");
        when(ezee.setStatus(any(), any(), any())).thenReturn(result(true));
        DoneResult r = service.done("e1", "hk1");
        assertFalse(r.alreadyDone());
        assertEquals("CONFIRMED", r.ezee());
        assertEquals("Dorm 2 Bed 1", r.roomName());
        ArgumentCaptor<RoomStatusWriteRequest> req = ArgumentCaptor.forClass(RoomStatusWriteRequest.class);
        verify(ezee, times(1)).setStatus(eq("10"), req.capture(), eq("cleaning-done-e1"));
        assertEquals(new RoomStatusWriteRequest("20", "CLEAN", "Cleaned by hk1"), req.getValue());
        verify(notifier, times(1)).cleaned("Dorm 2 Bed 1", "hk1", "CONFIRMED");
    }

    @Test
    void doneMapsOutcomes() {
        present("e1");
        when(ezee.setStatus(any(), any(), any())).thenReturn(result(false));
        assertEquals("NOT_CONFIRMED", service.done("e1", "u").ezee());

        when(ezee.setStatus(any(), any(), any())).thenThrow(new PmsFeatureDisabledException("off"));
        assertEquals("OFF", service.done("e1", "u").ezee());

        doThrow(new PmsNotEnabledException("off")).when(ezee).setStatus(any(), any(), any());
        assertEquals("OFF", service.done("e1", "u").ezee());

        doThrow(new IllegalStateException("boom")).when(ezee).setStatus(any(), any(), any());
        assertEquals("FAILED", service.done("e1", "u").ezee());
        // removal happened before every ezee call
        verify(mongo, times(4)).findAndRemove(any(Query.class), eq(CleaningListEntry.class));
    }

    @Test
    void doneOnAbsentIdIsAlreadyDone() {
        when(mongo.findAndRemove(any(Query.class), eq(CleaningListEntry.class))).thenReturn(null);
        DoneResult r = service.done("gone", "u");
        assertTrue(r.alreadyDone());
        assertNull(r.ezee());
        verifyNoInteractions(ezee, notifier);
    }

    @Test
    void notifierFailureDoesNotChangeResult() {
        present("e1");
        when(ezee.setStatus(any(), any(), any())).thenReturn(result(true));
        doThrow(new RuntimeException("fcm down")).when(notifier).cleaned(any(), any(), any());
        assertEquals("CONFIRMED", service.done("e1", "u").ezee());
        verify(notifier, times(1)).cleaned("Dorm 2 Bed 1", "u", "CONFIRMED");
    }
}
