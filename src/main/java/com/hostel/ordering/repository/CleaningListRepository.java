package com.hostel.ordering.repository;

import com.hostel.ordering.model.CleaningListEntry;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CleaningListRepository extends MongoRepository<CleaningListEntry, String> {

    Optional<CleaningListEntry> findByRoomIdAndUnitId(String roomId, String unitId);
}
