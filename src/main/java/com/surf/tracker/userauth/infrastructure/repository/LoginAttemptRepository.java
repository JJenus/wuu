package com.surf.tracker.userauth.infrastructure.repository;

import com.surf.tracker.userauth.domain.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    @Query("SELECT COUNT(la) FROM LoginAttempt la WHERE la.user.id = :userId AND la.success = false AND la.failureReason = 'INVALID_CREDENTIALS' AND la.attemptedAt > :since")
    long countByUserIdAndSuccessFalseAndAttemptedAtAfter(@Param("userId") Long userId,
                                                         @Param("since") Instant since);

    @Query("SELECT COUNT(la) FROM LoginAttempt la WHERE la.ipAddress = :ip AND la.success = false AND la.attemptedAt > :since")
    long countByIpAddressAndSuccessFalseAndAttemptedAtAfter(@Param("ip") String ip,
                                                            @Param("since") Instant since);
}
