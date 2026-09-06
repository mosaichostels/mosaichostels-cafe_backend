---
title: Mosaic Hostels Cafe Backend
emoji: ☕
colorFrom: yellow
colorTo: blue
sdk: docker
pinned: false
---

# Mosaic Hostels Cafe Backend

This is the high-performance backend for the Mosaic Hostels cafe ordering system, optimized for **Hugging Face Spaces** with 16GB of RAM.

## 🚀 Deployment Features

- **SDK**: Docker
- **RAM**: 16 GB (CPU Basic)
- **Port**: 7860
- **Sleep**: the Space sleeps without inbound traffic. The internal heartbeat and the
  Docker HEALTHCHECK are liveness markers only - neither keeps it awake. Use an external
  uptime pinger against the public URL if 24/7 warmth is needed.

## 🔗 How to Use

Once the build is finished, your API endpoint will be:
`https://huggingface.co/spaces/mosaichostels/cafe_backend`
