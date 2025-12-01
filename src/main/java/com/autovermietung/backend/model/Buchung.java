package com.autovermietung.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "buchung")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Buchung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "buchungs_nummer", unique = true, length = 32)
    private String buchungsNummer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fahrzeug_id", nullable = false)
    private Fahrzeug fahrzeug;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String kundeName;
    private String kundeEmail;
    private String kundePhone;

    @Column(nullable = false)
    private LocalDateTime startDatum;

    @Column(nullable = false)
    private LocalDateTime endDatum;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BuchungsStatus status = BuchungsStatus.RESERVIERT;

    @Builder.Default
    @Column(nullable = false)
    private boolean bringService = false;

    // ⏱ bis wann der Slot reserviert ist (z.B. jetzt + 5 Minuten)
    private LocalDateTime reserviertBis;

    // in Buchung.java

    @Column(name = "stripe_session_id", length = 255)
    private String stripeSessionId;

    @Column(name = "stripe_payment_intent_id", length = 255)
    private String stripePaymentIntentId;

    @Column(name = "refund_amount")
    private BigDecimal refundAmount;

    @Column(name = "storniert_am")
    private LocalDateTime storniertAm;


    @Column(precision = 10, scale = 2)
    private BigDecimal gesamtPreis;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;



    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;

        // Falls jemand es beim Bauen nicht gesetzt hat → Default: +5 Minuten
        if (this.status == BuchungsStatus.RESERVIERT && this.reserviertBis == null) {
            this.reserviertBis = this.createdAt.plusMinutes(5);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

