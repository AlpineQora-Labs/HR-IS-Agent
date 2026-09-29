package com.taportal.domain.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A phone number that said STOP. Opting out belongs to the number, not to a
 * candidate record: one person may have several records, and a number that
 * said STOP before anyone applied from it has still said STOP.
 */
@Entity
@Table(name = "sms_opt_out")
@Getter
@NoArgsConstructor
public class SmsOptOut {

    @Id
    @Column(name = "phone_e164")
    private String phoneE164;

    @Column(name = "opted_out_at", nullable = false)
    private OffsetDateTime optedOutAt;

    public SmsOptOut(String phoneE164) {
        this.phoneE164 = phoneE164;
        this.optedOutAt = OffsetDateTime.now();
    }
}
