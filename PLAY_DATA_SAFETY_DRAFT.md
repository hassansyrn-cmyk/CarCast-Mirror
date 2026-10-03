# Google Play Data Safety draft

This is a draft for Play Console and must be checked against the final shipped binary and every third-party SDK.

## High-level answers to validate

- **Does the app collect or share user data?** No remote collection by CarCast. The app transfers screen pixels, eligible device playback audio, local receiver metadata, and connection diagnostics between user-controlled devices on the local network to perform casting.
- **Is data encrypted in transit?** Yes. Native signaling/media uses TLS 1.3 plus WebRTC DTLS-SRTP; Browser mode uses WebRTC DTLS-SRTP and local-only signaling.
- **Is data processed ephemerally?** Media is streamed for the active session and is not intentionally stored by CarCast. Session diagnostics may be stored in app-private preferences until cleared.
- **Is data shared with third parties?** No CarCast cloud relay, advertising network, analytics provider, or account service. A receiver selected and approved by the user is the intended endpoint.

## Data categories to review in Play Console

| Category | Collection | Sharing | Purpose | Retention |
|---|---|---|---|---|
| App activity / diagnostics | App-private session state only | No remote provider | App functionality and troubleshooting | Until user clears diagnostics or app data is removed |
| Audio | User-initiated eligible playback capture | Approved local receiver only | Screen casting | Ephemeral during session |
| App interactions / device metadata | Local receiver name, browser user agent, connection state | Approved local receiver/phone peer only | Establish and diagnose a session | Ephemeral; selected diagnostics may persist locally |
| Location | No intentional collection | None | Not applicable |
| Contacts, photos, files, advertising ID | No | None | Not applicable |

## Permission disclosures

- `RECORD_AUDIO` is requested only when the user enables device-audio sharing. Android playback capture is used; the microphone is not the intended source.
- MediaProjection consent is requested per screen-share session.
- `INTERNET` and local discovery are used for direct LAN transport; there is no cloud relay.
- Notifications support active foreground-service status and Stop actions.

## Play Console follow-up

Complete the final form only after reviewing WebRTC, Bouncy Castle, ZXing, and Java-WebSocket SDK behavior and the signed release artifact. Link the final hosted privacy policy and ensure the store listing does not claim cloud storage, universal audio capture, or Android Auto screen injection.
