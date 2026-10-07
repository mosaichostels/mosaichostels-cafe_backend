package com.hostel.ordering.service;

import com.hostel.ordering.model.MenuItem;
import com.hostel.ordering.model.OtherEssential;
import com.hostel.ordering.repository.MenuItemRepository;
import com.hostel.ordering.repository.OtherEssentialRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The web panel's Undo re-sends a deleted item; without clearing the flag it stayed hidden forever. */
class RestoreDeletedItemTest {

    @Test
    void updatingASoftDeletedMenuItemRestoresIt() {
        MenuItemRepository repo = mock(MenuItemRepository.class);
        MenuItem gone = new MenuItem();
        gone.setDeleted(true);
        when(repo.findById("m1")).thenReturn(Optional.of(gone));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        MenuItem restore = new MenuItem();
        restore.setAvailable(true);

        MenuItem result = new MenuItemService(repo, mock(AuditService.class)).updateMenuItem("m1", restore);

        assertFalse(result.isDeleted());
    }

    @Test
    void updatingASoftDeletedEssentialRestoresIt() {
        OtherEssentialRepository repo = mock(OtherEssentialRepository.class);
        OtherEssential gone = new OtherEssential();
        gone.setDeleted(true);
        when(repo.findById("e1")).thenReturn(Optional.of(gone));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        OtherEssential restore = new OtherEssential();
        restore.setAvailable(true);

        OtherEssential result = new OtherEssentialService(repo, mock(AuditService.class)).updateOtherEssential("e1", restore);

        assertFalse(result.isDeleted());
    }
}
