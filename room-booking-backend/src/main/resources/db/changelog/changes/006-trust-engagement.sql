--liquibase formatted sql

--changeset ninggiangboy:006-01-reviews
CREATE TABLE reviews (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id        UUID NOT NULL,
    listing_id        UUID NOT NULL,
    reviewer_id       UUID NOT NULL,
    reviewee_id       UUID,
    review_type       VARCHAR(24) NOT NULL,
    rating            SMALLINT NOT NULL,
    cleanliness       SMALLINT,
    accuracy          SMALLINT,
    communication     SMALLINT,
    location          SMALLINT,
    value             SMALLINT,
    comment           TEXT NOT NULL,
    published_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_reviews_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_reviews_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT fk_reviews_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id),
    CONSTRAINT fk_reviews_reviewee FOREIGN KEY (reviewee_id) REFERENCES users (id),
    CONSTRAINT uk_reviews_booking_type UNIQUE (booking_id, review_type),
    CONSTRAINT ck_reviews_type CHECK (review_type IN ('GUEST_TO_LISTING', 'HOST_TO_GUEST')),
    CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_reviews_category_ratings CHECK (
        (cleanliness IS NULL OR cleanliness BETWEEN 1 AND 5)
        AND (accuracy IS NULL OR accuracy BETWEEN 1 AND 5)
        AND (communication IS NULL OR communication BETWEEN 1 AND 5)
        AND (location IS NULL OR location BETWEEN 1 AND 5)
        AND (value IS NULL OR value BETWEEN 1 AND 5)
    ),
    CONSTRAINT ck_reviews_type_shape CHECK (
        (review_type = 'GUEST_TO_LISTING' AND reviewee_id IS NULL)
        OR
        (
            review_type = 'HOST_TO_GUEST'
            AND reviewee_id IS NOT NULL
            AND cleanliness IS NULL
            AND accuracy IS NULL
            AND location IS NULL
            AND value IS NULL
        )
    ),
    CONSTRAINT ck_reviews_comment CHECK (length(trim(comment)) > 0),
    CONSTRAINT ck_reviews_version CHECK (version >= 0)
);

CREATE INDEX idx_reviews_listing_published ON reviews (listing_id, published_at DESC);
CREATE INDEX idx_reviews_reviewee_published ON reviews (reviewee_id, published_at DESC);
--rollback DROP TABLE reviews;

--changeset ninggiangboy:006-02-favorites
CREATE TABLE favorites (
    user_id       UUID NOT NULL,
    listing_id    UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_favorites PRIMARY KEY (user_id, listing_id),
    CONSTRAINT fk_favorites_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_favorites_listing FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE CASCADE
);

CREATE INDEX idx_favorites_listing ON favorites (listing_id, created_at DESC);
--rollback DROP TABLE favorites;
