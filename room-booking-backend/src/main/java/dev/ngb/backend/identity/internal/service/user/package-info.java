/**
 * Centralizes "load this account and decide what an absent or disabled one means" so every service
 * that needs a user stops repeating {@code findById(...).orElseThrow(...)} plus an activation check.
 */
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.identity.internal.service.user;
