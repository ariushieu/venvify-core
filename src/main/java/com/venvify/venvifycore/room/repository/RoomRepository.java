package com.venvify.venvifycore.room.repository;

import com.venvify.venvifycore.room.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    Optional<Room> findByPublicId(String publicId);

    Optional<Room> findByEventId(Long eventId);
}
