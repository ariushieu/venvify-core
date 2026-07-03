package com.venvify.venvifycore.booking.mapper;

import com.venvify.venvifycore.booking.dto.TransferResponse;
import com.venvify.venvifycore.booking.entity.TicketTransfer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TransferMapper {

    @Mapping(target = "bookingPublicId", source = "booking.publicId")
    @Mapping(target = "eventPublicId", source = "booking.event.publicId")
    @Mapping(target = "eventTitle", source = "booking.event.title")
    @Mapping(target = "fromUserPublicId", source = "fromUser.publicId")
    @Mapping(target = "fromUserName", source = "fromUser.fullName")
    @Mapping(target = "toUserPublicId", source = "toUser.publicId")
    @Mapping(target = "toUserName", source = "toUser.fullName")
    @Mapping(target = "transactionRef", source = "transaction.transactionRef")
    TransferResponse toResponse(TicketTransfer transfer);
}
