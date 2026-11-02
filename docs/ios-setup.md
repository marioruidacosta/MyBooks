# Getting MyBooks onto your iPhone

You don't need a Mac. GitHub builds the iPhone app on a cloud Mac and uploads it to
**TestFlight**, Apple's app for installing test builds. You do these steps once; after that,
every build appears in TestFlight on its own.

## 1. Join the Apple Developer Program

1. Go to <https://developer.apple.com/programs/enroll/> and enrol with your Apple ID ($99/year).
2. Wait for the "Welcome to the Apple Developer Program" email. It can take up to 48 hours.
3. Find your **Team ID** at <https://developer.apple.com/account> → *Membership details*. It's a
   10-character code such as `AB12CD34EF`.

## 2. Create the app in App Store Connect

1. Open <https://appstoreconnect.apple.com> → *Apps* → **+** → *New App*.
2. Fill in:
   - Platform: **iOS**
   - Name: **MyBooks** (if the name is taken, add something, e.g. "MyBooks Mario")
   - Primary language: Portuguese (Portugal) or English
   - Bundle ID: choose **com.mcosta.mybooks**. If it isn't in the list, register it first at
     <https://developer.apple.com/account/resources/identifiers/list> → **+** → *App IDs* → *App*,
     with the explicit Bundle ID `com.mcosta.mybooks`, then come back.
   - SKU: `mybooks`
   - User access: Full access
3. The app never has to be published on the App Store. TestFlight is enough.

## 3. Create an App Store Connect API key

This key lets the cloud build sign and upload the app for you.

1. App Store Connect → *Users and Access* → *Integrations* → *App Store Connect API* →
   *Team Keys* → **+**.
2. Name: `GitHub MyBooks`, Access: **App Manager** → *Generate*.
3. Write down the **Key ID** and the **Issuer ID** (shown above the list).
4. Click **Download API Key**. You get a file `AuthKey_XXXXXXXXXX.p8`, and Apple lets you
   download it **only once**. Keep it somewhere safe.

## 4. Add the secrets to GitHub

1. Open <https://github.com/marioruidacosta/MyBooks> → *Settings* → *Secrets and variables* →
   *Actions* → **New repository secret**.
2. Add these five secrets:

   | Name | Value |
   |---|---|
   | `APPLE_TEAM_ID` | your Team ID from step 1 |
   | `ASC_KEY_ID` | the Key ID from step 3 |
   | `ASC_ISSUER_ID` | the Issuer ID from step 3 |
   | `ASC_KEY_P8` | the `.p8` file encoded as base64 (see below) |
   | `GOOGLE_BOOKS_API_KEY` | the same Google Books key you put in `local.properties` |

   To get the base64 text of the `.p8` file on Windows, run this in PowerShell, then paste the
   copied text as the secret's value:

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\Downloads\AuthKey_XXXXXXXXXX.p8")) | Set-Clipboard
   ```

GitHub secrets stay hidden even though the repository is public.

## 5. Build and install

1. On GitHub, open *Actions* → **iOS** → *Run workflow*. Builds also run on their own when code
   is pushed to `main`, or when a version tag such as `v2.0.0` is pushed.
2. The run takes about 20–30 minutes. Afterwards, App Store Connect needs another 5–15 minutes
   to process the build.
3. In App Store Connect → your app → *TestFlight*:
   - Add yourself under *Internal Testing* (create a group, add your Apple ID).
   - If the build shows *Missing Compliance*, answer "None of the algorithms mentioned above"
     (the app only uses standard HTTPS).
4. On the iPhone, install **TestFlight** from the App Store, sign in with the same Apple ID,
   then install MyBooks from it.

Each TestFlight build works for 90 days. New builds replace the old one and keep your library.

## Moving your library from Android

1. On Android: *Settings* → **Export library**, and save the `.zip` file to Google Drive or
   send it to yourself by email.
2. On the iPhone: save the file to *Files*, then in MyBooks go to *Settings* →
   **Import library** and pick it. Books and cover photos are both restored.

## If a build fails

Open the failed run in *Actions* and look at the step marked with a red cross:

- **Shared tests on the iOS simulator / Simulator build**: a code problem. Tell Claude which step
  failed and paste its log.
- **Archive and upload**: usually a signing setup issue. Check the four Apple secrets, that the
  API key has App Manager access, and that the bundle ID `com.mcosta.mybooks` exists.
