# Overview

Spring Boot REST API for the Mosaic Hostels cafe ordering system (`com.hostel:ordering-system`, pom.xml). Consumers: a static web frontend (Vercel) and a staff/admin Android app, in sibling repos (workspace `../AGENTS.md`). Persists to MongoDB, posts order charges to the eZee PMS, sends FCM push notifications.

Deploy: Docker image on a Hugging Face Space (`mosaichostels/cafe_backend`), port 7860 (README.md, Dockerfile). Push to `main` runs `.github/workflows/sync_to_hf.yml`: `mvn -B verify`, then a force-push to the Space.
