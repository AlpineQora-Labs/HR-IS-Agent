package com.taportal.domain.messaging;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CandidateMessageRepository extends JpaRepository<CandidateMessage, UUID> {

    /** A person's messages: those of every candidate row they have, and those of their number. */
    @Query("""
            select m from CandidateMessage m
            where m.candidateId in :candidateIds or (m.channel = 'SMS' and m.address = :phone)
            order by m.createdAt asc, m.id asc""")
    List<CandidateMessage> timeline(@Param("candidateIds") Collection<UUID> candidateIds, @Param("phone") String phone);

    /**
     * The messages of one of several people who gave the same number: their
     * own, and those of the number that are nobody's in particular — a text
     * that came from it, and what was answered. What was sent to the others
     * is theirs.
     */
    @Query("""
            select m from CandidateMessage m
            where m.candidateId in :candidateIds
               or (m.channel = 'SMS' and m.address = :phone and m.candidateId is null)
            order by m.createdAt asc, m.id asc""")
    List<CandidateMessage> timelineOnSharedNumber(
            @Param("candidateIds") Collection<UUID> candidateIds, @Param("phone") String phone);

    boolean existsByDedupeKey(String dedupeKey);

    List<CandidateMessage> findByDedupeKeyIn(Collection<String> dedupeKeys);

    Optional<CandidateMessage> findByProviderAndProviderMessageId(String provider, String providerMessageId);

    /** The offers still open for a number, newest first. */
    @Query("""
            select m from CandidateMessage m
            where m.address = :address and m.direction = 'OUTBOUND' and m.channel = 'SMS'
              and m.offer is not null and m.offerClosedAt is null
              and m.status in ('QUEUED', 'SENDING', 'SENT')
            order by m.createdAt desc, m.id desc""")
    List<CandidateMessage> openOffersTo(@Param("address") String address);

    /** The offers still open for an interview, whoever they went to. */
    @Query("""
            select m from CandidateMessage m
            where m.interviewId = :interviewId and m.offer is not null and m.offerClosedAt is null""")
    List<CandidateMessage> openOffersFor(@Param("interviewId") UUID interviewId);

    /**
     * Take an offer, so that it can be acted on once. Two replies arriving
     * together both ask; only one is answered yes.
     *
     * @return 1 when the offer was open and is now taken, 0 when someone else took it
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CandidateMessage m set m.offerClosedAt = :now where m.id = :id and m.offerClosedAt is null")
    int claimOffer(@Param("id") UUID id, @Param("now") OffsetDateTime now);

    /** Take a queued message to send it. Two dispatchers both ask; only one sends. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CandidateMessage m set m.status = 'SENDING' where m.id = :id and m.status = 'QUEUED'")
    int claimForSending(@Param("id") UUID id);

    List<CandidateMessage> findByStatusAndCreatedAtBefore(String status, OffsetDateTime before);

    /** How many answers of these kinds a number has been given since a moment — to stop two machines talking. */
    @Query("""
            select count(m) from CandidateMessage m
            where m.address = :address and m.direction = 'OUTBOUND' and m.point in :points
              and m.createdAt > :since and m.status <> 'SUPPRESSED'""")
    long countAnswers(@Param("address") String address, @Param("points") Collection<String> points,
            @Param("since") OffsetDateTime since);

    /** Whether anything has ever been texted to this number. */
    @Query("""
            select count(m) > 0 from CandidateMessage m
            where m.address = :address and m.direction = 'OUTBOUND' and m.channel = 'SMS'
              and m.status in ('QUEUED', 'SENDING', 'SENT')""")
    boolean hasBeenTexted(@Param("address") String address);

    /**
     * Interviews whose candidate was told times are being arranged, and for
     * whom nothing has been written since that offers any. Whatever was
     * written — a text with times, an email, a link, or a text held back and
     * recorded as such — the promise has been acted on, and is not acted on
     * again every minute.
     */
    @Query("""
            select distinct m.interviewId from CandidateMessage m
            where m.point = 'NO_TIMES_AVAILABLE' and m.interviewId is not null and m.createdAt > :since
              and m.status in ('QUEUED', 'SENDING', 'SENT')
              and not exists (
                select 1 from CandidateMessage later
                where later.interviewId = m.interviewId and later.createdAt > m.createdAt
                  and later.direction = 'OUTBOUND'
                  and (later.offer is not null or later.point in :offering))""")
    List<UUID> interviewsWaitingForTimes(
            @Param("since") OffsetDateTime since, @Param("offering") Collection<String> offering);

    /** What was written for a number from a moment on: the answers to a text that arrived then. */
    @Query("""
            select m from CandidateMessage m
            where m.direction = 'OUTBOUND' and m.createdAt >= :since
              and (m.address = :address or m.candidateId in :candidateIds)
            order by m.createdAt asc, m.id asc""")
    List<CandidateMessage> writtenSince(@Param("address") String address,
            @Param("candidateIds") Collection<UUID> candidateIds, @Param("since") OffsetDateTime since);
}
