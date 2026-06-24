package com.venvify.venvifycore.user.mapper;

import com.venvify.venvifycore.user.dto.CreateUserRequest;
import com.venvify.venvifycore.user.dto.UpdateUserRequest;
import com.venvify.venvifycore.user.dto.UserResponse;
import com.venvify.venvifycore.user.entity.User;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * Map DTO ↔ User (CLAUDE.md §4 — MapStruct).
 * Lưu ý: password KHÔNG map trực tiếp — service tự hash rồi set passwordHash.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    UserResponse toResponse(User user);

    /** Chỉ map các field hồ sơ; field null trong request thì giữ nguyên giá trị cũ. */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(UpdateUserRequest request, @MappingTarget User user);

    /** Map field cơ bản từ request đăng ký; passwordHash/roles/status do service set. */
    User toEntity(CreateUserRequest request);
}
