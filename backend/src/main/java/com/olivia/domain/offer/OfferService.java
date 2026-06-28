package com.olivia.domain.offer;

import com.olivia.api.OfferDtos.CreateOfferRequest;
import com.olivia.api.OfferDtos.OfferResponse;
import jakarta.persistence.EntityNotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OfferService {

    private final OfferRepository repository;

    public OfferService(OfferRepository repository) {
        this.repository = repository;
    }

    public List<OfferResponse> list() {
        return repository.findAll().stream().map(OfferService::toResponse).toList();
    }

    public OfferResponse get(UUID id) {
        return repository.findById(id).map(OfferService::toResponse)
                .orElseThrow(() -> new EntityNotFoundException("Offer not found: " + id));
    }

    @Transactional
    public OfferResponse create(CreateOfferRequest request) {
        Offer offer = new Offer();
        offer.setApplicationId(request.applicationId());
        offer.setTitle(request.title());
        offer.setCompBase(request.compBase());
        offer.setCompPeriod(request.compPeriod());
        offer.setCompBonus(request.compBonus());
        offer.setEquity(request.equity());
        offer.setStartDate(request.startDate());
        offer.setLetterBody(request.letterBody());
        offer.setStatus("DRAFT");
        return toResponse(repository.save(offer));
    }

    @Transactional
    public OfferResponse send(UUID id) {
        Offer offer = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Offer not found: " + id));
        offer.setStatus("SENT");
        offer.setSentAt(OffsetDateTime.now());
        return toResponse(repository.save(offer));
    }

    static OfferResponse toResponse(Offer o) {
        return new OfferResponse(
                o.getId(),
                o.getApplicationId(),
                null,
                null,
                o.getTitle(),
                o.getCompBase(),
                o.getCompPeriod(),
                o.getCompBonus(),
                o.getEquity(),
                o.getStartDate(),
                o.getStatus(),
                o.getLetterBody(),
                o.getSentAt());
    }
}
