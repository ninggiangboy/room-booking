/**
 * Application-wide Spring configuration and HTTP security infrastructure.
 *
 * <p>Classes annotated with {@code @Configuration} declare beans, while classes annotated with
 * {@code @Component} are discovered automatically. The JWT filter authenticates requests before
 * controllers run, and the two security handlers keep authentication errors in the same JSON
 * format as normal API errors.</p>
 */
package dev.ngb.backend.config;
