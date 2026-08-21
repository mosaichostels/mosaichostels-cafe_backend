package com.hostel.ordering.repository;

import com.hostel.ordering.model.AppVersion;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AppVersionRepository extends MongoRepository<AppVersion, String> {
}
