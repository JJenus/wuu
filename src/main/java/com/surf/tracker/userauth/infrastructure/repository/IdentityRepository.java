package com.surf.tracker.userauth.infrastructure.repository;

import com.surf.tracker.userauth.domain.entity.Identity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdentityRepository extends JpaRepository<Identity, Long> {
    Optional<Identity> findByProviderAndProviderUid(String provider, String providerUid);
}
