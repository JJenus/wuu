package com.surf.tracker.notification.infrastructure.repository;

import com.surf.tracker.notification.domain.entity.Device;
import com.surf.tracker.notification.domain.enums.DevicePlatform;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByDeviceId(String deviceId);

    List<Device> findByUserId(String userId);

    List<Device> findByUserIdAndPlatform(String userId, DevicePlatform platform);

    List<Device> findByUserIdAndIsValidTrue(String userId);

    Optional<Device> findByPushToken(String pushToken);

    Page<Device> findByUserId(String userId, Pageable pageable);

    boolean existsByUserIdAndPlatform(String userId, DevicePlatform platform);
}
