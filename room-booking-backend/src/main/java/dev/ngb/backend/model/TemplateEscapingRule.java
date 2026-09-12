package dev.ngb.backend.model;

/**
 * How rendered variables must be escaped for a channel.
 */
public enum TemplateEscapingRule {
    /** No markup; variables are inserted literally. */
    PLAIN_TEXT,
    /** HTML-escaped. */
    HTML,
    /** Markdown-escaped. */
    MARKDOWN
}
