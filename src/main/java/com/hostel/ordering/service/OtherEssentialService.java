package com.hostel.ordering.service;

import com.hostel.ordering.model.OtherEssential;
import com.hostel.ordering.repository.OtherEssentialRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class OtherEssentialService {

    @Autowired
    private OtherEssentialRepository otherEssentialRepository;

    public OtherEssential createOtherEssential(OtherEssential otherEssential) {
        otherEssential.setCreatedAt(System.currentTimeMillis());
        otherEssential.setUpdatedAt(System.currentTimeMillis());

        return otherEssentialRepository.save(otherEssential);
    }

    public OtherEssential getOtherEssential(String id) {
        Optional<OtherEssential> otherEssential = otherEssentialRepository.findById(id);
        return otherEssential.orElse(null);
    }

    public List<OtherEssential> getAllOtherEssentials() {
        return otherEssentialRepository.findAll();
    }

    public List<OtherEssential> getAvailableOtherEssentials() {
        return otherEssentialRepository.findByAvailableTrueOrderByNameAsc();
    }

    public OtherEssential updateOtherEssential(String id, OtherEssential otherEssential) {
        Optional<OtherEssential> optionalOtherEssential = otherEssentialRepository.findById(id);
        
        if (optionalOtherEssential.isPresent()) {
            OtherEssential existingOtherEssential = optionalOtherEssential.get();
            
            if (otherEssential.getName() != null) existingOtherEssential.setName(otherEssential.getName());
            if (otherEssential.getDescription() != null) existingOtherEssential.setDescription(otherEssential.getDescription());
            if (otherEssential.getPrice() != null) existingOtherEssential.setPrice(otherEssential.getPrice());
            if (otherEssential.getCategory() != null) existingOtherEssential.setCategory(otherEssential.getCategory());
            if (otherEssential.getAvailable() != null) existingOtherEssential.setAvailable(otherEssential.getAvailable());
            
            existingOtherEssential.setUpdatedAt(System.currentTimeMillis());
            
            return otherEssentialRepository.save(existingOtherEssential);
        }
        
        return null;
    }

    public void deleteOtherEssential(String id) {
        otherEssentialRepository.deleteById(id);
    }
}
