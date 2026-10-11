# Google sign-in and email setup

Service Providers can sign in with Google or email and password. Both methods use the same account and permissions.

## Private configuration

- Create your own Google OAuth web client and set `PUBLIC_FUND_GOOGLE_CLIENT_ID`.
- Register the exact frontend origins you use, such as `http://localhost:5173` and `http://127.0.0.1:5173`.
- Configure the consent audience and any required test users in your Google project. No Gmail or Drive OAuth permissions are requested. The client secret is not used by the Google Identity Services ID-token flow.
- Integration settings belong in the environment or ignored `config/local.properties`. Spring loads this file when started from the backend directory. Start from `config/local.properties.example`.
- For Gmail, use your own sender/account with SMTP host `smtp.gmail.com`, port 587, and STARTTLS. Keep the app password private.

## One private step to enable email

1. Open your Google Account's App Passwords page while signed into the sender account. If Google asks you to authenticate, do that yourself.
2. Create a replacement app password named **Public Fund System Mail**. Do not use your ordinary Google password and do not paste any password into chat.
3. Stop an existing backend with Ctrl+C in its own terminal to free port 8080.
4. In the VS Code PowerShell terminal, run:

```powershell
cd C:\Users\Admin\Desktop\codex_project\Public-Fund-System\backend
.\Start-Backend.ps1 -ConfigureMail
```

5. Paste the app password only into the script's hidden prompt and press Enter. It saves a Windows DPAPI-encrypted credential in `.local/mail-password.xml`, bound to your Windows user and computer. This directory is excluded from Git. Keep the file private; encryption does not protect against malicious code running as your Windows user.
6. Subsequent backend starts use `.\Start-Backend.ps1`. The script decrypts the credential only for the launched process environment; it does not print it or write a plaintext credential file.

If PowerShell blocks scripts, follow your administrator's approved script-signing/execution policy; do not disable system protections.

In a separate terminal:

```powershell
cd C:\Users\Admin\Desktop\codex_project\Public-Fund-System\frontend
npm run dev
```

Open `http://127.0.0.1:5173/login` or `http://localhost:5173/login`. For localhost development the frontend automatically uses the matching backend hostname, so the nonce cookie stays same-site. MySQL must be running. Password reset links currently open `http://localhost:5173`.

## How the account flows work

- A new verified Gmail/Google Workspace identity can create only a Service Provider account.
- Existing Service Providers must confirm their current Public Fund password before first linking Google. Linking keeps their user ID, projects and password.
- Google identities using a non-Google-hosted email must first register/verify through the existing email flow, then link with their password.
- Admins and Government Officers continue using their existing email/password login. Google does not provision staff or grant staff access.
- Disabled, locked, unverified and must-change-password accounts cannot bypass their restrictions through Google.
- Google ID tokens are checked for signature, issuer, audience, expiry, verified email and browser-bound nonce. Challenges expire in five minutes and are consumed once. The stable Google subject is stored, not an OAuth access token or client secret.
- The Google token stays in frontend memory during linking. The successful session uses the existing application JWT.
- Public forgot-password sends an eligible Service Provider a 30-minute single-use reset link. No development token is displayed. Only the reset-token digest is stored. Opening the link removes its token from the URL and keeps it in page memory; refreshing requires reopening the email link.
- Registration verification continues using the existing 10-minute OTP flow and resend/retry limits. Email failure does not claim successful delivery.

## Configuration for another environment

Set `PUBLIC_FUND_GOOGLE_CLIENT_ID` and `PUBLIC_FUND_FRONTEND_URL`, plus:

```text
PUBLIC_FUND_MAIL_HOST
PUBLIC_FUND_MAIL_PORT
PUBLIC_FUND_MAIL_USERNAME
PUBLIC_FUND_MAIL_PASSWORD
PUBLIC_FUND_MAIL_FROM
PUBLIC_FUND_MAIL_TLS
```

Never commit secrets. Production needs an HTTPS domain, matching authorized Google origins and backend CORS origins, approved Google branding/audience configuration, and correct proxy HTTPS handling for secure cookies. `VITE_API_URL` can configure the frontend API URL. This local setup is not a production deployment. The current nonce store/per-IP limits are process-local: use a shared store/rate limiter or an appropriate single-instance deployment. Use same-site frontend/backend hosting for the Strict nonce cookie.

Google's consent project remains in Testing; public rollout is not completed. Finish any required test-user/branding configuration in Google Cloud before broader testing or publication. Do not request unrelated OAuth scopes.

## Database

`database/migrations/003_google_sign_in.sql` adds the nullable, unique `users.google_subject` column. Back up the database and check whether the column exists before applying the migration.

## Testing

Automated tests cover Google linking, staff/disabled/unverified account restrictions, nonce replay, CORS, reset-token hashing, expiry, single use, mail failures, and existing workflows. Frontend Google/recovery browser tests use mocked Google/API responses, not real accounts. Run `frontend/scripts/google-recovery-test.mjs` with Playwright available and the local frontend running.

Real Google login and real inbox delivery still require a final user-driven check. After private mail setup, register a test Service Provider or request recovery for a provider you own, check inbox/spam, and verify the code/link. Do not change or reset someone else's account for testing. The SMTP app password is separate from the OAuth client and cannot be replaced by its client ID/secret.
