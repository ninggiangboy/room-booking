package dev.ngb.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts the Room Booking backend and anchors component scanning at {@code dev.ngb.backend}.
 *
 * <p>{@code @SpringBootApplication} combines configuration registration, sensible Spring Boot
 * auto-configuration, and component scanning of this package and all descendants.</p>
 */
@SpringBootApplication
public class RoomBookingBackendApplication {

    /**
     * Launches the application with the configuration selected from command-line arguments.
     *
     * @param args Spring Boot command-line arguments
     */
    static void main(String[] args) {
        SpringApplication.run(RoomBookingBackendApplication.class, args);
    }

}
