package com.hostel.ordering.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "app_version")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppVersion {

    public static final String LATEST_ID = "latest";

    @Id
    private String id;
    private int versionCode;
    private String versionName;
    private String downloadUrl;
    private String releaseNotes;
    private long publishedAt;
}
