package dev.ngb.backend.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Connects the {@code OTEL} appender declared in {@code logback-spring.xml} to the application's
 * {@link OpenTelemetry} SDK instance.
 *
 * <p>Boot's {@code spring-boot-starter-opentelemetry} configures OTLP log export but does not
 * install a Logback appender on its own; the appender exists once
 * {@code opentelemetry-logback-appender-1.0} is on the classpath and referenced from
 * {@code logback-spring.xml}, but it stays inert until {@link OpenTelemetryAppender#install} gives
 * it something to export through. Without this component, every log record the appender receives
 * is silently dropped rather than reaching {@code room-booking-infra}'s LGTM stack -- confirmed
 * empirically before this class existed.</p>
 */
@Component
class OpenTelemetryLogbackAppenderInstaller implements InitializingBean {

    private final OpenTelemetry openTelemetry;

    OpenTelemetryLogbackAppenderInstaller(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public void afterPropertiesSet() {
        OpenTelemetryAppender.install(openTelemetry);
    }

}
