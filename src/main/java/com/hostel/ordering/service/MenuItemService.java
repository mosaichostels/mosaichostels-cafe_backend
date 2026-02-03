package com.hostel.ordering.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.hostel.ordering.model.MenuItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
public class MenuItemService {

    private static final String COLLECTION_NAME = "menuItems";

    @Autowired
    private Firestore firestore;

    public MenuItem createMenuItem(MenuItem menuItem) throws ExecutionException, InterruptedException {
        menuItem.setCreatedAt(System.currentTimeMillis());
        menuItem.setUpdatedAt(System.currentTimeMillis());

        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document();
        menuItem.setId(docRef.getId());

        ApiFuture<WriteResult> result = docRef.set(menuItem);
        result.get();

        return menuItem;
    }

    public MenuItem getMenuItem(String id) throws ExecutionException, InterruptedException {
        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(id);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();

        if (document.exists()) {
            return document.toObject(MenuItem.class);
        }
        return null;
    }

    public List<MenuItem> getAllMenuItems() throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION_NAME).get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();

        List<MenuItem> menuItems = new ArrayList<>();
        for (QueryDocumentSnapshot document : documents) {
            menuItems.add(document.toObject(MenuItem.class));
        }
        return menuItems;
    }

    public List<MenuItem> getAvailableMenuItems() throws ExecutionException, InterruptedException {
        ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION_NAME)
                .whereEqualTo("available", true)
                .get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();

        List<MenuItem> menuItems = new ArrayList<>();
        for (QueryDocumentSnapshot document : documents) {
            menuItems.add(document.toObject(MenuItem.class));
        }
        return menuItems;
    }

    public MenuItem updateMenuItem(String id, MenuItem menuItem) throws ExecutionException, InterruptedException {
        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(id);

        Map<String, Object> updates = new HashMap<>();
        if (menuItem.getName() != null) updates.put("name", menuItem.getName());
        if (menuItem.getDescription() != null) updates.put("description", menuItem.getDescription());
        if (menuItem.getPrice() != null) updates.put("price", menuItem.getPrice());
        if (menuItem.getCategory() != null) updates.put("category", menuItem.getCategory());
        if (menuItem.getAvailable() != null) updates.put("available", menuItem.getAvailable());
        updates.put("updatedAt", System.currentTimeMillis());

        ApiFuture<WriteResult> result = docRef.update(updates);
        result.get();

        return getMenuItem(id);
    }

    public void deleteMenuItem(String id) throws ExecutionException, InterruptedException {
        ApiFuture<WriteResult> result = firestore.collection(COLLECTION_NAME).document(id).delete();
        result.get();
    }
}
