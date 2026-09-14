package dev.ngb.backend;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Enforces the module boundaries described in {@code docs/modules/} and {@code
 * docs/architecture/modular-monolith.md}, and renders them as a diagram.
 *
 * <p>This is not a business-logic test, and {@code docs/conventions/07-workflow-and-commits.md}'s
 * "do not create tests solely to satisfy a checklist" does not apply to it: without this class the
 * module boundaries drawn across 22 {@code package-info.java} files are comments a reviewer has to
 * trust, not a property the build checks. {@code ApplicationModules.of(...)} is computed once and
 * shared by both methods below — importing all ~1,900 classes through ArchUnit takes real time, and
 * there is no reason to pay it twice.</p>
 *
 * <p>{@link #verifiesModularStructure()} calls bare {@code verify()}, not the ratchet form
 * ({@code detectViolations().filter(...)}) a mid-migration commit would need — every module's
 * {@code allowedDependencies} was set from the actual, audited cross-module import graph (see
 * {@code docs/architecture/modular-monolith.md}), so there is nothing left to allow-list.</p>
 */
class ModularityTests {

    private static final ApplicationModules MODULES =
            ApplicationModules.of(RoomBookingBackendApplication.class);

    /**
     * Prints the detected module structure so an accidental 23rd module — a stray top-level package
     * that should have lived inside an existing one — shows up in the build log before it shows up
     * as a review comment.
     */
    @Test
    void printsApplicationModules() {
        MODULES.forEach(System.out::println);
    }

    /**
     * Fails the build the moment a module reaches into another module's {@code internal} package,
     * a cycle forms between modules, or a dependency is used that a module's {@code
     * allowedDependencies} does not list.
     */
    @Test
    void verifiesModularStructure() {
        MODULES.verify();
    }

    /**
     * Renders the verified module graph as PlantUML/C4 diagrams under {@code
     * build/spring-modulith-docs}, so the picture cannot drift from the code the way a hand-drawn
     * one would. Never fails on its own; it depends on {@link #verifiesModularStructure()} having
     * passed for the diagram to mean anything.
     *
     * @throws Exception propagated from {@link Documenter} if rendering itself fails
     */
    @Test
    void writesDocumentationSnippets() throws Exception {
        new Documenter(MODULES).writeDocumentation();
    }
}
