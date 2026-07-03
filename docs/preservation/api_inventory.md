# API Inventory (Phase 0)

This document contains a catalog of all API endpoints exposed by the Django backend and Go Gateway.

---

## 1. Authentication Endpoints
- **POST `/api/agent/auth/login/`**
  - *Payload:* `{"username": "...", "password": "...", "device_signature": "..."}`
  - *Response:* Login token pair and user data.
  - *Authentication:* None.

## 2. Configuration & Registration
- **GET `/api/agent/config/`**
  - *Response:* Agent config settings, offline permissions, and server time.
  - *Authentication:* Bearer JWT.
- **POST `/api/agent/device/register/`**
  - *Payload:* `{"device_name": "..."}`
  - *Response:* Generated `device_id` and `device_secret` key.
  - *Authentication:* Bearer JWT.

## 3. Transactional & Tickets
- **POST `/api/agent/ticket/create/`**
  - *Payload:* `{"draw_ids": [int], "lines": [{"jeu": "...", "valeur": "...", "mise": double}]}`
  - *Response:* Created ticket details.
  - *Authentication:* Bearer JWT.
- **POST `/api/agent/ticket/create-multi/`**
  - *Payload:* `{"tirage_ids": [int], "entries": [...], "session_key": "..."}`
  - *Headers:* `X-DEVICE-ID` and `X-PAYLOAD-SIGN` (optional for online, required for offline sync).
  - *Response:* Created tickets batch.
  - *Authentication:* Bearer JWT.

## 4. Search and Cashbox
- **GET `/api/agent/tickets/search/?q=...`**
  - *Response:* Match result of ticket number or ticket ID.
  - *Authentication:* Bearer JWT.
- **GET `/api/agent/caisse/`**
  - *Response:* Caisse balance and last transactions.
  - *Authentication:* Bearer JWT.
