package net.gameocean.core.database;

import java.sql.Timestamp;
import java.util.UUID;

public class PlayerProfile {

    private final UUID uuid;
    private String username;
    private boolean termsAccepted;
    private int level;
    private String currentApartment;
    private boolean apartmentIntroSeen;
    private boolean apartmentIsPublic;
    private boolean apartmentOfflineAccess;
    private boolean friendAnnouncements;
    private boolean friendPopupRequests;
    private Timestamp lastLogin;
    private final Timestamp createdAt;

    public PlayerProfile(UUID uuid, String username, boolean termsAccepted, int level, String currentApartment, boolean apartmentIntroSeen, boolean apartmentIsPublic, boolean apartmentOfflineAccess, boolean friendAnnouncements, boolean friendPopupRequests, Timestamp lastLogin, Timestamp createdAt) {
        this.uuid = uuid;
        this.username = username;
        this.termsAccepted = termsAccepted;
        this.level = level;
        this.currentApartment = currentApartment;
        this.apartmentIntroSeen = apartmentIntroSeen;
        this.apartmentIsPublic = apartmentIsPublic;
        this.apartmentOfflineAccess = apartmentOfflineAccess;
        this.friendAnnouncements = friendAnnouncements;
        this.friendPopupRequests = friendPopupRequests;
        this.lastLogin = lastLogin;
        this.createdAt = createdAt;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean hasAcceptedTerms() {
        return termsAccepted;
    }

    public void setTermsAccepted(boolean termsAccepted) {
        this.termsAccepted = termsAccepted;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getCurrentApartment() {
        return currentApartment;
    }

    public void setCurrentApartment(String currentApartment) {
        this.currentApartment = currentApartment;
    }

    public boolean hasSeenApartmentIntro() {
        return apartmentIntroSeen;
    }

    public void setApartmentIntroSeen(boolean apartmentIntroSeen) {
        this.apartmentIntroSeen = apartmentIntroSeen;
    }

    public boolean isApartmentPublic() {
        return apartmentIsPublic;
    }

    public void setApartmentIsPublic(boolean apartmentIsPublic) {
        this.apartmentIsPublic = apartmentIsPublic;
    }

    public boolean hasOfflineAccess() {
        return apartmentOfflineAccess;
    }

    public void setOfflineAccess(boolean apartmentOfflineAccess) {
        this.apartmentOfflineAccess = apartmentOfflineAccess;
    }

    public boolean hasFriendAnnouncements() {
        return friendAnnouncements;
    }

    public void setFriendAnnouncements(boolean friendAnnouncements) {
        this.friendAnnouncements = friendAnnouncements;
    }

    public boolean hasFriendPopupRequests() {
        return friendPopupRequests;
    }

    public void setFriendPopupRequests(boolean friendPopupRequests) {
        this.friendPopupRequests = friendPopupRequests;
    }

    public Timestamp getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(Timestamp lastLogin) {
        this.lastLogin = lastLogin;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "PlayerProfile{" +
                "uuid=" + uuid +
                ", username='" + username + '\'' +
                ", termsAccepted=" + termsAccepted +
                ", level=" + level +
                ", currentApartment='" + currentApartment + '\'' +
                ", apartmentIntroSeen=" + apartmentIntroSeen +
                ", apartmentIsPublic=" + apartmentIsPublic +
                ", apartmentOfflineAccess=" + apartmentOfflineAccess +
                ", friendAnnouncements=" + friendAnnouncements +
                ", friendPopupRequests=" + friendPopupRequests +
                ", lastLogin=" + lastLogin +
                ", createdAt=" + createdAt +
                '}';
    }
}
