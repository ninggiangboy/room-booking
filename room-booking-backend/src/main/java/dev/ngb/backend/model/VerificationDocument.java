package dev.ngb.backend.model;

import java.time.Instant;
import java.time.LocalDate;
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
 * A document submitted as verification evidence, held by reference rather than by value.
 *
 * <p>The bytes live in encrypted object storage behind the secret boundary; this row carries a
 * location, a digest, and the key version that protects it. A database dump is therefore not a
 * collection of passport scans.</p>
 *
 * <p>Every document carries a deletion deadline from the moment it is uploaded, because a document
 * with no retention instant is one nobody ever deletes. Nothing may open a document until its
 * {@link #scanState} is clean: uploads are attacker-controlled input, and a reviewer opening one is
 * exactly the path a malicious file is aiming for.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("verification_documents")
public class VerificationDocument {

    /** Primary key of the document record. */
    @Id
    private @Nullable UUID id;
    /** Verification case the document is evidence for. */
    private UUID verificationCaseId;
    /** Kind of document submitted. */
    private VerificationDocumentType documentType;
    /** ISO 3166-1 alpha-2 country that issued it. */
    private @Nullable String issuingCountry;
    /** Location of the encrypted bytes in object storage. */
    private String storageReference;
    /** SHA-256 digest of the content, proving the bytes have not changed. */
    private String contentDigest;
    /** Version of the key the bytes are encrypted under. */
    private short encryptionKeyVersion;
    /** Malware-scan result; nothing may read the document until this is clean. */
    private DocumentScanState scanState;
    /** Civil date the document itself expires, where it carries one. */
    private @Nullable LocalDate expiresOn;
    /** UTC instant the document was uploaded. */
    private Instant uploadedAt;
    /** UTC instant by which the document must be erased. */
    private Instant retainUntil;
    /** UTC instant the bytes were actually erased. */
    private @Nullable Instant deletedAt;
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
     * Reports whether the document may be opened by a reviewer or a provider adapter.
     *
     * @return {@code true} only when the malware scan came back clean and the bytes still exist
     */
    public boolean isReadable() {
        return scanState == DocumentScanState.CLEAN && deletedAt == null;
    }
}
