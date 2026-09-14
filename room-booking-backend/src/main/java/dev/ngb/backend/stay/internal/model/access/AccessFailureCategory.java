package dev.ngb.backend.stay.internal.model.access;

/**
 * Why an access operation failed, in terms the retry policy can use.
 */
public enum AccessFailureCategory {
    /** The provider refused the request outright. */
    PROVIDER_REJECTED,
    /** The lock could not be reached. */
    DEVICE_UNREACHABLE,
    /** The device reported insufficient power. */
    DEVICE_BATTERY,
    /** Platform credentials for the provider were refused. */
    AUTHENTICATION,
    /** The provider asked us to slow down. */
    RATE_LIMITED,
    /** No response arrived in time. */
    TIMEOUT,
    /** The request was malformed or unsupported. */
    INVALID_REQUEST,
    /** The platform failed before or after the call. */
    INTERNAL
}
