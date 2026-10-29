Read when: investigating a production error, slow request or failed deploy.
Verified: 2026-10-29 @ 9e39a6f
Covers: src/main/resources/application-prod.yml

| Signal | Where | Notes |
| --- | --- | --- |
| Backend logs | Hugging Face Space "Logs" tab | Only source (owner answer 2026-10-29). Container stdout. |
| Log levels | `logging.level` in `application-prod.yml:24` | root INFO, `com.hostel.ordering` INFO, mongodb/google/grpc WARN |
| Metrics | none | No separate metrics system |
| Alerts | none | No alerting configured |
| Deploy status | GitHub Actions run of `.github/workflows/sync_to_hf.yml` | Then the Space build log on Hugging Face |
| Audit trail | `GET /audit/**` (SuperAdmin) | Business events, not an ops log |

- Logs are not retained beyond what the Space shows; do not rely on history.
