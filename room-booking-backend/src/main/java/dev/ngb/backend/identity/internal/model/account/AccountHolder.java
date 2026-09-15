package dev.ngb.backend.identity.internal.model.account;

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
 * The account holder: the principal that signs in, owns supply, enters contracts, and is settled.
 *
 * <p>Since migration {@code 037} retired the legacy {@code users} table, this is the single
 * principal root identity code is built on — the identifier a JWT names in its subject claim, and
 * the row every capability grant, session, credential, and contact channel points at. It coincides
 * with an individual host and diverges for a company, whose supply and payouts belong to the
 * organization rather than to whichever employee happens to manage them.</p>
 *
 * <p>Holders are never deleted — a closed holder still has to explain the bookings it contracted and
 * the money it was paid — so closure is a {@link AccountHolderStatus}, not a row removal.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("account_holders")
public class AccountHolder {

    /** Primary key of the holder, and the identifier every JWT subject claim now names. */
    @Id
    private @Nullable UUID id;
    /** Whether the holder is a person or an organization. */
    private AccountHolderType holderType;
    /** Name the holder is presented and contracted under. */
    private String displayName;
    /** Optional location of the holder's avatar; the one profile field with no other home. */
    private @Nullable String avatarUrl;
    /** Whether the holder may own supply, contract, and be settled. */
    private AccountHolderStatus status;
    /** Market the holder operates in; {@code null} only while unresolved. */
    private @Nullable String marketCode;
    /** Whether the market is known or has never been resolved. */
    private MarketContextState contextState;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic-lock value checked and incremented by Spring Data. */
    @Version
    private @Nullable Long version;

    /**
     * Reports whether this holder may take part in a consequential workflow.
     *
     * <p>Both conditions matter. An unresolved holder has no market, so there is no approved tax
     * treatment, cancellation ladder, or contracting entity for it — publishing or booking under it
     * would form a contract under rules nobody approved.</p>
     *
     * @return {@code true} when the holder is active and its market context is resolved
     */
    public boolean canTransact() {
        return status == AccountHolderStatus.ACTIVE
                && contextState == MarketContextState.RESOLVED;
    }
}
