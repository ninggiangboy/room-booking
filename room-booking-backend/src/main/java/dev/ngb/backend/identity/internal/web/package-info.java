/**
 * REST controllers and their request/response records for authentication and account management.
 *
 * <p>{@code @RestController} combines Spring's controller registration with automatic JSON response
 * serialization. Controllers intentionally contain little business logic: validation, transactions,
 * and persistence belong to services in {@link dev.ngb.backend.identity.internal.service}. The
 * request/response types are Java {@code record}s; Bean Validation annotations on request
 * components reject invalid input before a controller invokes its service.</p>
 */
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.identity.internal.web;
