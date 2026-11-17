package com.hostel.ordering.service;

import com.hostel.ordering.model.MenuItem;
import com.hostel.ordering.model.OtherEssential;
import com.hostel.ordering.repository.MenuItemRepository;
import com.hostel.ordering.repository.OtherEssentialRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogValidationTest {

    @Test
    void menuCreateRejectsBlankNameAndNonFinitePrice() {
        MenuItemRepository repository = mock(MenuItemRepository.class);
        MenuItemService service = new MenuItemService(repository, mock(AuditService.class));
        MenuItem item = validMenu();
        item.setName(" ");
        assertThrows(IllegalArgumentException.class, () -> service.createMenuItem(item));
        item.setName("Coffee");
        item.setPrice(Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> service.createMenuItem(item));
        verify(repository, never()).save(item);
    }

    @Test
    void essentialCreateRejectsMissingAvailabilityAndOversizedCategory() {
        OtherEssentialRepository repository = mock(OtherEssentialRepository.class);
        OtherEssentialService service = new OtherEssentialService(repository, mock(AuditService.class));
        OtherEssential item = validEssential();
        item.setAvailable(null);
        assertThrows(IllegalArgumentException.class, () -> service.createOtherEssential(item));
        item.setAvailable(true);
        item.setCategory("x".repeat(101));
        assertThrows(IllegalArgumentException.class, () -> service.createOtherEssential(item));
        verify(repository, never()).save(item);
    }

    @Test
    void mergedPartialUpdateIsValidatedBeforeSave() {
        MenuItemRepository repository = mock(MenuItemRepository.class);
        MenuItemService service = new MenuItemService(repository, mock(AuditService.class));
        MenuItem existing = validMenu();
        when(repository.findById("m1")).thenReturn(Optional.of(existing));
        MenuItem patch = new MenuItem();
        patch.setPrice(-1.0);

        assertThrows(IllegalArgumentException.class, () -> service.updateMenuItem("m1", patch));
        verify(repository, never()).save(existing);
    }

    private static MenuItem validMenu() {
        MenuItem item = new MenuItem();
        item.setName("Coffee");
        item.setCategory("Drinks");
        item.setPrice(50.0);
        item.setAvailable(true);
        return item;
    }

    private static OtherEssential validEssential() {
        OtherEssential item = new OtherEssential();
        item.setName("Towel");
        item.setCategory("Essentials");
        item.setPrice(100.0);
        item.setAvailable(true);
        return item;
    }
}
