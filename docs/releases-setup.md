# Setting up "New in Portugal" (monthly new releases)

The app's **New in Portugal** section reads monthly lists of new books published in Portugal, by genre. A nightly
GitHub Actions job (`.github/workflows/releases.yml`, code in `releases/`) builds them from the National Library's
National Bibliography (legal deposit, harvested through its OAI-PMH service) and publishes them on GitHub Pages:

- `https://marioruidacosta.github.io/MyBooks/releases/index.json`: the available months, with book counts per genre
- `https://marioruidacosta.github.io/MyBooks/releases/<YYYY-MM>.json`: one month's books

A month lists the books that first appeared in the National Bibliography that month. That is usually one to three
months after they reach the shops: the Library records only the publication year, and catalogues in batches.

## Once

1. On GitHub: **Settings → Pages → Build and deployment → Source: GitHub Actions**.
2. **Actions → Releases job → Run workflow**. The first run fills in the current month and the six before it
   (about 80 requests to the Library, two seconds apart: a few minutes), creates the
   `releases-data` branch and publishes the files.
3. Check that `https://marioruidacosta.github.io/MyBooks/releases/index.json` lists seven months.

After that it runs by itself every night at 03:00 UTC.

## When something goes wrong

- **A red Releases job run:** the Library was unavailable, or its data changed shape. Nothing published is lost,
  and the next run catches up from the last good day. The run's annotations show the error without signing in.
- **To rebuild from scratch** (for example after the job learns to publish a new field): Actions → Releases job →
  Run workflow → tick **Rebuild all seven months from scratch**. The old lists are replaced in a normal commit; the
  `releases-data` branch keeps its history.

## Running it on this PC

```powershell
cd C:\Users\mcosta\GIT\MyBooks
$env:JAVA_TOOL_OPTIONS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"   # trust the company proxy's certificates
.\gradlew :releases:run --args="--data-dir C:\temp\releases-data"
```
