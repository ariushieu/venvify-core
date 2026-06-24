package com.venvify.venvifycore.event.mapper;

import com.venvify.venvifycore.event.dto.CreateEventRequest;
import com.venvify.venvifycore.event.dto.EventResponse;
import com.venvify.venvifycore.event.dto.UpdateEventRequest;
import com.venvify.venvifycore.event.entity.Event;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EventMapper {

    @Mapping(target = "hostPublicId", source = "host.publicId")
    @Mapping(target = "hostHandle", source = "host.hostHandle")
    EventResponse toResponse(Event event);

    /** host, slug, status, claimedSlots do service set — không map từ request. */
    Event toEntity(CreateEventRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(UpdateEventRequest request, @MappingTarget Event event);
}
