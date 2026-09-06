package com.hostel.ordering.service;

import com.hostel.ordering.model.Category;
import com.hostel.ordering.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryDeleteGuardTest {

    @Mock CategoryRepository categoryRepository;
    @Mock AuditService auditService;
    @Mock MongoTemplate mongoTemplate;

    @InjectMocks CategoryService categoryService;

    private Category category(String name, String type) {
        Category c = new Category();
        c.setId("c1");
        c.setName(name);
        c.setType(type);
        c.setShowOrder(1);
        return c;
    }

    @Test
    void refusesToDeleteACategoryThatItemsStillUse() {
        when(categoryRepository.findById("c1"))
                .thenReturn(Optional.of(category("Parathas", Category.TYPE_MENU)));
        when(mongoTemplate.count(any(Query.class), eq("menu_items"))).thenReturn(5L);

        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> categoryService.deleteCategory("c1"));

        assertTrue(e.getMessage().contains("Parathas"), e.getMessage());
        assertTrue(e.getMessage().contains("5"), e.getMessage());
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void deletesAnUnusedCategory() {
        when(categoryRepository.findById("c1"))
                .thenReturn(Optional.of(category("Unused", Category.TYPE_MENU)));
        when(mongoTemplate.count(any(Query.class), eq("menu_items"))).thenReturn(0L);

        categoryService.deleteCategory("c1");

        verify(categoryRepository, times(1)).delete(any());
    }

    @Test
    void checksTheEssentialsCollectionForAnEssentialCategory() {
        when(categoryRepository.findById("c1"))
                .thenReturn(Optional.of(category("Beverages", Category.TYPE_ESSENTIAL)));
        when(mongoTemplate.count(any(Query.class), eq("other_essentials"))).thenReturn(2L);

        assertThrows(IllegalArgumentException.class, () -> categoryService.deleteCategory("c1"));

        verify(mongoTemplate, never()).count(any(Query.class), eq("menu_items"));
    }
}
