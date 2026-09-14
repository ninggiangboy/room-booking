/**
 * The abstract, HTTP-status-bearing exception hierarchy every domain failure extends.
 *
 * <p>{@link dev.ngb.backend.platform.exception.base.DomainException} is the root; each direct
 * subtype ({@code BadRequestException}, {@code ConflictException}, {@code ForbiddenException},
 * {@code NotFoundException}, {@code TooManyRequestsException}, {@code UnauthorizedException}) fixes
 * one HTTP status so a concrete exception never has to choose one itself — see
 * {@code docs/conventions/05-api-dto-and-errors.md}. Lives under {@code platform} because the global
 * exception handler in {@code config} maps against these base types only, never a concrete
 * module-owned exception, which is what lets {@code config} stay a dependency-free translation layer
 * instead of importing every module's own exception types.</p>
 */
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.platform.exception.base;
