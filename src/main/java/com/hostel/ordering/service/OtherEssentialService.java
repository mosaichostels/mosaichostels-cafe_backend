package com.hostel.ordering.service;

import com.hostel.ordering.model.OtherEssential;
import com.hostel.ordering.repository.OtherEssentialRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OtherEssentialService {

    private final OtherEssentialRepository otherEssentialRepository;

    public OtherEssentialService(OtherEssentialRepository otherEssentialRepository) {
        this.otherEssentialRepository = otherEssentialRepository;
    }

    public OtherEssential createOtherEssential(OtherEssential otherEssential) {
        otherEssential.setCreatedAt(System.currentTimeMillis());
        otherEssential.setUpdatedAt(System.currentTimeMillis());
        return otherEssentialRepository.save(otherEssential);
    }

    public OtherEssential getOtherEssential(String id) {
        return otherEssentialRepository.findById(id).orElse(null);
    }

    public List<OtherEssential> getAllOtherEssentials() {
        return otherEssentialRepository.findAll();
    }

    public List<OtherEssential> getAvailableOtherEssentials() {
        return otherEssentialRepository.findByAvailableTrueOrderByNameAsc();
    }

    public List<OtherEssential> searchOtherEssentials(String query, boolean availableOnly) {
        if (availableOnly) {
            return otherEssentialRepository.findByNameContainingIgnoreCaseAndAvailableTrueOrderByNameAsc(query);
        }
        return otherEssentialRepository.findByNameContainingIgnoreCaseOrderByNameAsc(query);
    }

    public OtherEssential updateOtherEssential(String id, OtherEssential otherEssential) {
        return otherEssentialRepository.findById(id)
                .map(existing -> {
                    if (otherEssential.getName() != null) existing.setName(otherEssential.getName());
                    if (otherEssential.getDescription() != null) existing.setDescription(otherEssential.getDescription());
                    if (otherEssential.getPrice() != null) existing.setPrice(otherEssential.getPrice());
                    if (otherEssential.getCategory() != null) existing.setCategory(otherEssential.getCategory());
                    if (otherEssential.getAvailable() != null) existing.setAvailable(otherEssential.getAvailable());
                    existing.setUpdatedAt(System.currentTimeMillis());
                    return otherEssentialRepository.save(existing);
                })
                .orElse(null);
    }

    public void deleteOtherEssential(String id) {
        otherEssentialRepository.deleteById(id);
    }
}
