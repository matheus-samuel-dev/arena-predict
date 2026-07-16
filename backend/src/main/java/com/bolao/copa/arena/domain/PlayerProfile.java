package com.bolao.copa.arena.domain;

import com.bolao.copa.entity.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "arena_player_profiles")
public class PlayerProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", unique = true)
    private User user;
    @Column(length = 300) private String avatarUrl;
    @Column(length = 500) private String bio;
    @Column(length = 600) private String favoriteSports;
    @Column(nullable = false, length = 20) private String theme = "dark";
    @Column(nullable = false, length = 12) private String language = "pt-BR";
    @Column(nullable = false) private boolean notificationsEnabled = true;
    @Column(nullable = false) private boolean publicProfile = true;
    @Column(nullable = false) private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getFavoriteSports() { return favoriteSports; }
    public void setFavoriteSports(String favoriteSports) { this.favoriteSports = favoriteSports; }
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean notificationsEnabled) { this.notificationsEnabled = notificationsEnabled; }
    public boolean isPublicProfile() { return publicProfile; }
    public void setPublicProfile(boolean publicProfile) { this.publicProfile = publicProfile; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touch() { updatedAt = Instant.now(); }
}
