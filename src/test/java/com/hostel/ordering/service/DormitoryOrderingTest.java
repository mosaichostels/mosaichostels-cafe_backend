package com.hostel.ordering.service;

import com.hostel.ordering.model.Dormitory;
import com.hostel.ordering.repository.DormitoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * showOrder is what /config sorts the dormitory names by, so it decides the order of the
 * checkout picker in both clients. These cover the three ways it changes.
 */
@ExtendWith(MockitoExtension.class)
class DormitoryOrderingTest {

    @Mock DormitoryRepository repository;
    @Mock AuditService auditService;

    @InjectMocks DormitoryService dormitoryService;

    private Dormitory dorm(String id, String name, int showOrder) {
        Dormitory d = new Dormitory(name);
        d.setId(id);
        d.setShowOrder(showOrder);
        return d;
    }

    @SuppressWarnings("unchecked")
    private List<Dormitory> capturedSave() {
        ArgumentCaptor<List<Dormitory>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saved.capture());
        return saved.getValue();
    }

    private String orders(List<Dormitory> dorms) {
        return dorms.stream()
                .sorted((a, b) -> a.getShowOrder() - b.getShowOrder())
                .map(d -> d.getName() + "=" + d.getShowOrder())
                .collect(Collectors.joining(","));
    }

    @Test
    void movingOneUpSlidesItsNeighbourDown() {
        Dormitory a = dorm("a", "Alpha", 1);
        Dormitory b = dorm("b", "Bravo", 2);
        Dormitory c = dorm("c", "Charlie", 3);
        when(repository.findById("c")).thenReturn(Optional.of(c));
        when(repository.save(any(Dormitory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findAllByOrderByShowOrderAsc())
                .thenReturn(new ArrayList<>(Arrays.asList(a, b, c)));

        dormitoryService.updateDormitory("c", dorm(null, "Charlie", 2));

        assertEquals("Charlie=2,Bravo=3", orders(capturedSave()));
        assertEquals(1, a.getShowOrder(), "the row above the move must not shift");
    }

    @Test
    void addingWithoutAPositionGoesLast() {
        Dormitory a = dorm("a", "Alpha", 1);
        Dormitory b = dorm("b", "Bravo", 2);
        when(repository.save(any(Dormitory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findAllByOrderByShowOrderAsc())
                .thenReturn(new ArrayList<>(Arrays.asList(a, b)));

        Dormitory added = dormitoryService.addDormitory(new Dormitory("Charlie"));

        assertEquals(3, added.getShowOrder());
    }

    @Test
    void rowsPredatingShowOrderAreNumberedOnFirstRead() {
        Dormitory a = dorm("a", "Alpha", 0);
        Dormitory b = dorm("b", "Bravo", 0);
        when(repository.findAllByOrderByShowOrderAsc())
                .thenReturn(new ArrayList<>(Arrays.asList(a, b)));

        dormitoryService.getAllDormitories();

        assertEquals("Alpha=1,Bravo=2", orders(capturedSave()));
    }

    @Test
    void readingAnAlreadyNumberedListWritesNothing() {
        when(repository.findAllByOrderByShowOrderAsc())
                .thenReturn(new ArrayList<>(Arrays.asList(dorm("a", "Alpha", 1), dorm("b", "Bravo", 2))));

        dormitoryService.getAllDormitories();

        verify(repository, never()).saveAll(anyList());
    }
}
