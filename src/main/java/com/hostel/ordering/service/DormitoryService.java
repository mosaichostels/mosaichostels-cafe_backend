package com.hostel.ordering.service;

import com.hostel.ordering.model.Dormitory;
import com.hostel.ordering.repository.DormitoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DormitoryService {

    private final DormitoryRepository repository;

    public DormitoryService(DormitoryRepository repository) {
        this.repository = repository;
    }

    public List<Dormitory> getAllDormitories() {
        return repository.findAll();
    }

    public Dormitory addDormitory(Dormitory dormitory) {
        return repository.save(dormitory);
    }

    public void deleteDormitory(String id) {
        repository.deleteById(id);
    }
    
    public long count() {
        return repository.count();
    }
}
