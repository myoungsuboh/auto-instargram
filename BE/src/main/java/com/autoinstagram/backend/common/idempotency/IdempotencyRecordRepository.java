package com.autoinstagram.backend.common.idempotency;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    /** 같은 키 + 같은 엔드포인트의 기존 기록. DB 의 partial unique index 와 같은 조건이다. */
    Optional<IdempotencyRecord> findByIdempotencyKeyAndRequestMethodAndRequestPathAndDeletedAtIsNull(
            String idempotencyKey, String requestMethod, String requestPath);
}
