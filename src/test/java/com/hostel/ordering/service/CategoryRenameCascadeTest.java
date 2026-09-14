package com.hostel.ordering.service;

import com.hostel.ordering.model.Category;
import com.hostel.ordering.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Items reference their category by name, so a rename must carry across to them. Without the
 * cascade the section keeps rendering under the old heading in both clients, and loses its
 * configured position because no config name matches it any more.
 */
@ExtendWith(MockitoExtension.class)
class CategoryRenameCascadeTest {

    @Mock CategoryRepository categoryRepository;
    @Mock AuditService auditService;
    @Mock MongoTemplate mongoTemplate;

    @InjectMocks CategoryService categoryService;

    private Category stored(String name, String type) {
        Category c = new Category();
        c.setId("c1");
        c.setName(name);
        c.setType(type);
        c.setShowOrder(1);
        return c;
    }

    private Category edit(String name, int showOrder) {
        Category c = new Category();
        c.setName(name);
        c.setShowOrder(showOrder);
        return c;
    }

    private void givenStored(Category category) {
        when(categoryRepository.findById("c1")).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void renamingAMenuCategoryRewritesItsItems() {
        givenStored(stored("Parathas", Category.TYPE_MENU));

        categoryService.updateCategory("c1", edit("Stuffed Parathas", 1));

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateMulti(query.capture(), update.capture(), eq("menu_items"));

        assertTrue(query.getValue().getQueryObject().toString().contains("Parathas"),
                query.getValue().toString());
        assertTrue(update.getValue().getUpdateObject().toString().contains("Stuffed Parathas"),
                update.getValue().toString());
    }

    @Test
    void renamingAnEssentialCategoryRewritesTheEssentialsCollection() {
        givenStored(stored("Toiletries", Category.TYPE_ESSENTIAL));

        categoryService.updateCategory("c1", edit("Bathroom", 1));

        verify(mongoTemplate).updateMulti(any(Query.class), any(UpdateDefinition.class),
                eq("other_essentials"));
        verify(mongoTemplate, never()).updateMulti(any(Query.class), any(UpdateDefinition.class),
                eq("menu_items"));
    }

    @Test
    void reorderingWithoutRenamingLeavesItemsAlone() {
        givenStored(stored("Parathas", Category.TYPE_MENU));

        categoryService.updateCategory("c1", edit("Parathas", 3));

        verify(mongoTemplate, never()).updateMulti(any(Query.class), any(UpdateDefinition.class),
                anyString());
    }
}
