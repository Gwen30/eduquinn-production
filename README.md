# EduQuinn mobile interface preview

A static, clickable HTML preview of the EduQuinn native app's screens,
styled to match www.eduquinn.co.zw's own interface exactly (no redesign).
Includes a bottom navigation bar (Catalog / Live / Messages / More) so it
can be clicked through like the real app.

This is a design preview only — the actual mobile app being shipped to
the Play Store is a separate native Android (Kotlin/Jetpack Compose)
project, built to match this same interface.

Deploy: push this repo to GitHub, then import it on vercel.com as a
static site (zero build config needed — it's a single index.html).
