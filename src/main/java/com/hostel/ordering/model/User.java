package com.hostel.ordering.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

@Document(collection = "users")
public class User {
    @Id
    private String id;

    @NotBlank(message = "Username cannot be blank")
    private String username;

    @NotNull(message = "Password cannot be null")
    @JsonIgnore
    private String password;

    @NotEmpty(message = "Roles cannot be empty")
    private Set<String> roles;

    private String fcmToken;

    // Epoch millis. Tokens issued before this instant are revoked (set on explicit logout).
    // Survives a restart, unlike the in-memory token blacklist.
    private Long tokensValidFrom;

    public User() {}

    public User(String username, String password, Set<String> roles) {
        this.username = username;
        this.password = password;
        this.roles = roles;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }

    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }

    public Long getTokensValidFrom() { return tokensValidFrom; }
    public void setTokensValidFrom(Long tokensValidFrom) { this.tokensValidFrom = tokensValidFrom; }
}