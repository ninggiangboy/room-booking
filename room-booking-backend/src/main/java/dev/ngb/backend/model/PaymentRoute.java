package dev.ngb.backend.model;

import java.time.Instant;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * What it takes to send a payment to one merchant account.
 *
 * <p>Kept apart from the provider account itself so that disabling new submissions during an
 * outage never touches the account's identity, credentials, or ownership of operations already in
 * flight. The capability flags answer the routing question the platform actually asks: can this
 * account take this method, in this currency, and do the things this obligation needs?</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("payment_routes")
public class PaymentRoute {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Merchant account this route configures. */
    private UUID providerAccountId;
    /** Instrument family the route serves. */
    private PaymentMethodFamily methodFamily;
    /** ISO 4217 code the route collects in. */
    private String currency;
    /** Market the route is restricted to, when it is. */
    private @Nullable String marketCode;
    /** Whether the account can reserve funds without collecting. */
    private boolean supportsAuthorize;
    /** Whether it can convert a reservation into collected funds. */
    private boolean supportsCapture;
    /** Whether it can authorise and capture as one action. */
    private boolean supportsSale;
    /** Whether it can release an unused reservation. */
    private boolean supportsVoid;
    /** Whether it can return captured funds. */
    private boolean supportsRefund;
    /** Whether it can return part of a capture. */
    private boolean supportsPartialRefund;
    /** Whether it answers server-to-server status retrieval. */
    private boolean supportsQuery;
    /** Whether it can drive a redirect or authentication step. */
    private boolean supportsCustomerAction;
    /** Whether it can vault an instrument for reuse. */
    private boolean supportsTokenization;
    /** Smallest amount in minor units the route accepts. */
    private @Nullable Long minAmountMinor;
    /** Largest amount in minor units the route accepts. */
    private @Nullable Long maxAmountMinor;
    /** Deterministic preference order; lower is preferred. */
    private short routingPriority;
    /** Share of eligible traffic within one priority band. */
    private short routingWeight;
    /** Version of the routing policy this configuration belongs to. */
    private String routingPolicyVersion;
    /**
     * Whether new collection may be submitted through this route.
     *
     * <p>Separate from {@link #refundsEnabled} on purpose. An outage that stops new collection must
     * still let refunds, webhooks and status queries through, or the platform loses the ability to
     * resolve money it has already taken.</p>
     */
    private boolean submissionsEnabled;
    /** Whether refunds may be submitted through this route. */
    private boolean refundsEnabled;
    /** Breaker state for this route. */
    private CircuitState circuitState;
    /** UTC instant the breaker last moved. */
    private @Nullable Instant circuitChangedAt;
    /** Why the breaker moved, for the incident record. */
    private @Nullable String circuitReason;
    /** UTC instant this configuration starts applying. */
    private Instant effectiveFrom;
    /** UTC instant it stops applying; open-ended when absent. */
    private @Nullable Instant effectiveUntil;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;
}
