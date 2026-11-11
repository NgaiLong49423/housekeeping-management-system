package com.housekeeping.management.dto.entity;

import com.housekeeping.management.entity.StaffProfile;

public record StaffProfileDTO(Long profileId, Long staffId, String avatarUrl, String bio) {
    public static StaffProfileDTO convertEntityToDTO(StaffProfile entity) {
        if (entity == null) return null;
        return new StaffProfileDTO(entity.getProfileId(), entity.getStaffId(), entity.getAvatarUrl(), entity.getBio());
    }

    public StaffProfile convertDTOToEntity() {
        StaffProfile entity = new StaffProfile();
        entity.setProfileId(profileId);
        entity.setStaffId(staffId);
        entity.setAvatarUrl(avatarUrl);
        entity.setBio(bio);
        return entity;
    }
}
