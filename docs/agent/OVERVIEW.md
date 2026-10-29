Read when: you need the purpose, consumers or deploy target of this service.
Verified: 2026-10-29 @ 9e39a6f
Covers: README.md, pom.xml, Dockerfile, src/main/java/**/*.java

# Overview

- What: Spring Boot REST API for the Mosaic Hostels cafe ordering system (`com.hostel:ordering-system:1.0.0`, pom.xml:11-12).
- Consumers: static web frontend (Vercel) and staff/admin Android app, in sibling repos (`../AGENTS.md`). Guests order unauthenticated (`POST /orders`); staff/admin use JWT.
- Persists: MongoDB. Posts order charges to eZee PMS (HTTP). Push: Firebase Cloud Messaging.
- Deploy: Docker image on Hugging Face Space `mosaichostels/cafe_backend`, port 7860 (README.md, Dockerfile:15).
- Pipeline: push to `main` -> `.github/workflows/sync_to_hf.yml`: job `build` (`mvn -B verify`, JDK 17 temurin) -> job `sync-to-hub` (`git push --force` to the Space, secret `HF_TOKEN`). Also `workflow_dispatch`.
- Root `README.md` is Hugging Face Space metadata (front matter) + deploy notes; do not remove the front matter.
