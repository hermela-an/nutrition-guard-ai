-- Email verification support (users.is_verified + verification tokens)
-- Safe to run multiple times.

ALTER TABLE public.users
  ADD COLUMN IF NOT EXISTS is_verified BOOLEAN NOT NULL DEFAULT true;

CREATE TABLE IF NOT EXISTS public.email_verification_tokens (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  token_hash TEXT UNIQUE NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_email_verification_tokens_hash_unique
  ON public.email_verification_tokens (token_hash);

CREATE INDEX IF NOT EXISTS idx_email_verification_tokens_user_id
  ON public.email_verification_tokens (user_id);

CREATE INDEX IF NOT EXISTS idx_email_verification_tokens_expires_at
  ON public.email_verification_tokens (expires_at);

