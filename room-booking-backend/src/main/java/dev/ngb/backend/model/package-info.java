/**
 * Domain enums and Spring Data JDBC persistence models.
 *
 * <p>Unlike JPA entities, Spring Data JDBC aggregates do not use lazy-loading proxies or a
 * persistence session. Lombok generates mechanical constructors, builders, getters, and setters;
 * Spring Data annotations define table identity and optimistic locking.</p>
 */
package dev.ngb.backend.model;
