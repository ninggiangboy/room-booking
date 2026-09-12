# Migration 016 — Supply catalog in the target vocabulary

## Goal

Replace the listing-centric model with the one the target release actually needs: a listing is no
longer the thing that is sold.

- **`property`** — a physical, operational location.
- **`accommodation_type`** — the sellable category at that property.
- **`physical_unit`** — an optionally assigned specific room or apartment.
- **`listing`** — the public presentation of an accommodation type, *not* inventory authority.
- **`rate_plan`** — the conditions an offer is sold under.

That separation is what lets one hotel sell forty identical rooms from a pooled count while a single
apartment sells itself once, without the two being different codebases.

## Why the historical tables were retired rather than altered

Migrations `002`–`006` cannot be reshaped with `ALTER`, because the *grain* of the tables is wrong:
`availability_days` is keyed by listing, and `bookings` references a listing as inventory. Neither is
true in the target model.

Changeset `016-01` retires the whole historical stack in one step because foreign keys make it one
unit. `listings` roots a cluster containing `listing_images`, `listing_amenities`,
`availability_days`, `bookings`, `booking_nights`, `payment_attempts`, `refunds`, `reviews`, and
`favorites`; dropping it piecemeal across later migrations is not possible. No Java code read any of
them and no environment held data in them.

**This changeset is not reversible.** `--rollback empty` is honest rather than convenient: the
definitions live in applied changesets `002`–`006`, which must never be edited, so restoring the
historical shape means restoring a database snapshot taken before this migration. Target replacements
arrive in `018` (inventory), `020` (booking), `021` (payment), `026` (reviews), `029` (saved
listings).

## Design rules

- **`inventory_mode` is the decision that shapes everything downstream.** A `UNIQUE_RENTAL` is sold
  once and defended by an overlap exclusion constraint; a `QUANTITY_POOL` is sold from a per-date
  count defended by a quantity check. Migration `018` builds both on this one column, and
  `ck_accommodation_types_unique_quantity` stops a unique rental claiming a quantity above one.
- **The property owns its own IANA zone, not the market's.** A market can span zones, and every stay
  date, check-in time, and deadline for this property resolves in *its* zone. Aliases like `UTC` are
  rejected because they carry no DST history.
- **`ENTIRE_PLACE` cannot be shared.** `ck_accommodation_types_sharing_consistency` refuses the
  contradiction, because letting the two disagree is how a guest books what they believe is a private
  home and finds strangers in the kitchen.
- **The obfuscated public position is stored, not computed per request.** Every viewer then sees the
  same circle; recomputing it per request leaks the true point by triangulation.
- **The amenity vocabulary is versioned.** Renaming "Wifi" to "Wi-Fi" must not silently change what a
  guest booked last year, and retiring a term must not orphan the listings that claimed it. Labels
  live in `amenity_translations` so search and storage use the stable key while presentation uses the
  reader's language.
- **Exactly one listing content locale is the source.** Without
  `uk_listing_contents_one_source` there is no answer to "which wording is authoritative" when two
  translations disagree.
- **Media has three independent gates** — `scan_state`, `moderation_state`, `processing_state` —
  because they fail independently: a file can be malware-free but depict something prohibited, or be
  clean and permitted but not yet resized for delivery. The gallery index requires all three.
  `idx_listing_media_digest` exists because the same photograph uploaded against two properties is a
  strong duplicate-listing signal.
- **Safety absence is recorded explicitly.** "This property has no smoke alarm" is information a
  guest is entitled to; "nobody asked" is a different statement, and a missing row cannot distinguish
  them.
- **Accessibility claims carry their unit and their evidence.** A guest may rely on one to decide
  whether they can physically enter the property, so `32` never stands alone, and a verified claim is
  a different promise from an unverified one.
- **`listing_change_history` exists for disputes.** A guest arguing "the listing said there was air
  conditioning" needs the listing as it was on the day they booked, not as it is now.
- **Collaboration is not authority.** `property_collaborators` records that someone works on a
  property; `capability_grant_id` points at the property-scoped grant that says what they may do, so
  ending a collaboration and revoking its authority stay one linked act instead of two that drift.

## Exit criteria

- A hotel room type and a single villa are both expressible, and the database refuses the incoherent
  combinations of mode, quantity, room type, and sharing.
- A published listing always resolves to exactly one accommodation type, and an accommodation type
  has at most one live listing.
- A listing's wording, amenities, and media on any past date are reconstructible.
- A property's stay times resolve in the property's own zone.
