# Setting up the MyBooks community (Supabase)

The community features (accounts, friends, requests, block and report) run on a free
[Supabase](https://supabase.com) project. Until it's configured, the app hides them and behaves
exactly as before.

All the rules (who can see what, who can accept a request, blocking, account deletion) are in
`supabase/migrations/`. They're tested automatically by the **Supabase** GitHub workflow
(`supabase/tests/`).

## 1. Create the project

1. Sign up at <https://supabase.com> and create a **New project**:
   - Name: `mybooks`
   - Region: **West EU (Frankfurt)**, or the one nearest to your users
   - Database password: generate one and keep it in a password manager (you need it in step 3)
2. When it's ready, open **Project Settings → API** and note:
   - **Project URL** (e.g. `https://abcdefgh.supabase.co`)
   - **anon public** key (a long `eyJ…` string). It's meant to be built into apps; the database
     rules protect the data.
   - Never use or share the **service_role** key.

## 2. Turn on sign-in with an email code

1. **Authentication → Sign In / Providers → Email**: keep **Email** enabled and turn **Confirm email** on.
2. **Authentication → Emails → Templates → Magic Link**: replace the body so it shows the code,
   for example:

   ```html
   <h2>Your MyBooks code</h2>
   <p>Enter this code in the app: <strong>{{ .Token }}</strong></p>
   <p>It expires in one hour. If you didn't ask for it, ignore this email.</p>
   ```

   Do the same for the **Confirm signup** template, which is used the first time someone signs in.
3. **Authentication → URL Configuration**: the Site URL can stay as it is; the app doesn't use links.

## 3. Create the tables and rules

From the MyBooks folder on your PC (Node.js is already in `C:\Users\mcosta\devtools`):

```powershell
$env:Path = "C:\Users\mcosta\devtools\node-v18.20.8-win-x64;$env:Path"
npx supabase login                         # opens the browser once
npx supabase link --project-ref abcdefgh   # the part of your Project URL before .supabase.co
npx supabase db push                       # asks for the database password from step 1
```

`db push` applies everything in `supabase/migrations/`. Run it again whenever a new migration is
added (for example when social part 2 arrives).

## 4. Give the apps the URL and key

- **Android** (your PC): add to `local.properties`, then rebuild:

  ```properties
  SUPABASE_URL=https://abcdefgh.supabase.co
  SUPABASE_ANON_KEY=eyJ...
  ```

- **iPhone** (cloud build): on GitHub → *Settings → Secrets and variables → Actions*, add the
  secrets `SUPABASE_URL` and `SUPABASE_ANON_KEY` with the same values.

After that, the **Friends** icon appears in the library's top bar, and **Settings** shows
*Join the MyBooks community*.

## 5. Before opening it to everyone

- **Email sending**: Supabase's built-in email sends only a few messages per hour, which is fine
  for testing with friends. For a public launch, add your own SMTP sender in
  **Authentication → Emails → SMTP Settings** (Resend, Brevo and Postmark all have free tiers).
- **Privacy policy**: both app stores require a public page saying what you store (email,
  username, name, photo, friendships, reports) and how to delete it (Settings → Account →
  Delete account in the app).
- **Reports**: read them in **Table Editor → reports**. To act on one, block or delete the user
  from **Authentication → Users**.
