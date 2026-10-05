package com.ecommerce.common.dlq;

import com.ecommerce.common.constants.ApiConstants;
import com.ecommerce.common.dto.PagedResponse;
import com.ecommerce.common.events.DlqPublisher;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeadLetterService {

    private final DeadLetterRepository repository;
    private final DlqPublisher publisher;

    @Transactional(readOnly = true)
    public PagedResponse<DeadLetterResponse> list(String status, int page, int size) {
        int pageSize = Math.min(Math.max(size, 1), ApiConstants.MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), pageSize, Sort.by("id").descending());
        Page<DeadLetter> result = status == null || status.isBlank()
                ? repository.findAll(pageable)
                : repository.findByStatus(status.toUpperCase(), pageable);
        return PagedResponse.of(result.getContent().stream().map(DeadLetterResponse::of).toList(),
                page, pageSize, result.getTotalElements());
    }

    /** Sends a parked message back to its original topic and marks it replayed. */
    @Transactional
    public DeadLetterResponse replay(Long id) {
        DeadLetter deadLetter = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DeadLetter", id));
        if (!DeadLetter.PARKED.equals(deadLetter.getStatus())) {
            throw new BusinessException("Dead letter " + id + " was already replayed", "DEAD_LETTER_ALREADY_REPLAYED");
        }
        publisher.replay(deadLetter);
        deadLetter.setStatus(DeadLetter.REPLAYED);
        deadLetter.setReplayedAt(Instant.now());
        return DeadLetterResponse.of(repository.save(deadLetter));
    }
}
