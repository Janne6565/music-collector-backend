-- V2 originally stored WCAG relative luminance. That is linear light, so mid-grey sits at
-- 0.22 and the design's "darker than 55% picks dark chrome" rule classed almost every
-- sleeve as dark -- a pale scanned sleeve measured 0.459 and got dark chrome.
--
-- The value is now perceptual CIE L*, normalised to 0..1, where 0.55 means what a person
-- looking at the cover would call half-way.
ALTER TABLE releases RENAME COLUMN luminance TO lightness;

-- Existing values are on the old scale and cannot be converted reliably (the stored number
-- has already lost the original RGB), so they are cleared. A null palette makes the next
-- detail lookup resample the cover, which is cheap and self-healing.
UPDATE releases SET lightness = NULL, dominant_color = NULL, accent_color = NULL;
