package com.venvify.venvifycore.booking.mapper;

import com.venvify.venvifycore.booking.dto.BookingResponse;
import com.venvify.venvifycore.booking.entity.Booking;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BookingMapper {

    @Mapping(target = "eventPublicId", source = "event.publicId")
    @Mapping(target = "eventTitle", source = "event.title")
    BookingResponse toResponse(Booking booking);
}
