package com.housekeeping.management.dto.entity;

import com.housekeeping.management.domain.UserRole;
import com.housekeeping.management.domain.UserStatus;
import com.housekeeping.management.entity.User;

/** User data safe for transfer; password hashes are supplied only to the entity converter. */
public record UserDTO(Long userId, String fullName, String email, String phone, UserRole role, UserStatus status) {
    public static UserDTO convertEntityToDTO(User entity) {
        if (entity == null) return null;
        return new UserDTO(entity.getUserId(), entity.getFullName(), entity.getEmail(), entity.getPhone(),
                entity.getRole(), entity.getStatus());
    }

    public User convertDTOToEntity(String encodedPasswordHash) {
        User entity = new User();
        entity.setUserId(userId);
        entity.setFullName(fullName);
        entity.setEmail(email);
        entity.setPhone(phone);
        entity.setRole(role);
        entity.setStatus(status);
        entity.setPasswordHash(encodedPasswordHash);
        return entity;
    }
}
