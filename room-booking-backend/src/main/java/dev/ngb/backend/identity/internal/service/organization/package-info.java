/**
 * Organization creation, membership, and co-host delegation of an organization's own capability
 * authority.
 *
 * <p>{@code OrganizationService} is {@code public} because {@code internal.web.OrganizationController}
 * calls it directly. {@code OrganizationFactory} stays package-private: nothing outside this package
 * constructs an organization's account holder or its founding membership row directly.</p>
 */
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.identity.internal.service.organization;
