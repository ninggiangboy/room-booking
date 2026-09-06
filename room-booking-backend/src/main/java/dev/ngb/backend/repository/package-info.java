/**
 * Database-access abstractions implemented at runtime by Spring Data JDBC.
 *
 * <p>Extending {@code ListCrudRepository} provides common CRUD methods. Spring derives SQL from
 * method names for simple queries, while {@code @Query} supplies explicit SQL for projections.</p>
 */
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.repository;
