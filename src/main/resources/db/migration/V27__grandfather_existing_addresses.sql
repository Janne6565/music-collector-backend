-- Accounts that existed before there was such a thing as confirming an address.
--
-- A password reset is about to require a confirmed address (design 21f), and every account
-- made before V26 has `email_verified_at IS NULL` simply because nobody was ever asked.
-- Leaving them there would silently take away a reset that worked yesterday, and it would
-- hit hardest the person who cannot get a link -- exactly the failure 21f rejects.
--
-- So this is a grandfather clause, not a proof: it records that these addresses are accepted
-- as given, because demanding retroactive proof of something we never asked for would strand
-- people rather than protect them. Every account made from here on has to confirm.
--
-- Placeholder addresses are left alone. Apple and Google may withhold an address, and there
-- is nothing to accept as given about a mailbox that does not exist.
UPDATE users
SET email_verified_at = created_at
WHERE email_verified_at IS NULL
  AND email NOT LIKE '%@no-email.invalid';
