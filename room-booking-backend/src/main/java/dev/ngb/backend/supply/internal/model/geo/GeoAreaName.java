package dev.ngb.backend.supply.internal.model.geo;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;


/**
 * One of the names a destination is known by, in one language.
 *
 * <p>Kept apart from the destination itself so that a place can be found by a name nobody would
 * choose to display. Da Nang is searched for under several spellings and an old administrative name;
 * folding those into the destination row would force a choice between matching a guest's query and
 * showing a name the guest recognises.</p>
 *
 * <p>There is no optimistic-lock version and no update timestamp: a name is inserted or removed by
 * the catalog import, never edited in place. Correcting a name means the key changes, which is a
 * different row.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("geo_area_names")
public class GeoAreaName {

    /** Composite destination, language, and normalized-name primary key. */
    @Id
    private GeoAreaNameId id;
    /** Name as presented to a reader in this language. */
    private String name;
    /** How this name relates to what the destination is actually called. */
    private GeoAreaNameType nameType;
    /** UTC instant the row was created. */
    @CreatedDate
    private Instant createdAt;
}
