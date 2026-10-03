# CarCast Mirror Privacy Policy — draft

**Effective date:** To be set before beta distribution  
**Operator/contact:** To be completed by the publisher

CarCast Mirror is a local screen-mirroring app. It does not require an account and does not operate a cloud relay, advertising network, or analytics service.

## Information handled

When you start a session, CarCast handles the phone display content and, only when you enable device audio and grant Android permission, eligible playback audio. It also handles temporary receiver names, browser user-agent text, local network endpoints, connection state, and technical session diagnostics.

## How it is used

This information is used only to discover a nearby receiver, authenticate the intended peer, establish an encrypted session, stream the user-requested media, adapt quality, and troubleshoot failures.

## Where it goes

Media and signaling are sent directly between the phone and the receiver selected or approved by the user over the local network. CarCast does not upload screen, audio, or diagnostics to an operator server. TLS/SAS and WebRTC security protect the session in transit.

## Storage and deletion

Media is not intentionally recorded or stored. Limited technical diagnostics may remain in app-private storage to help the user recover a failed session. Use the Diagnostics **CLEAR** action or clear app storage to remove them. Uninstalling the app removes app-private data.

## Permissions

Android MediaProjection is requested for each screen-share session. `RECORD_AUDIO` is requested only for the optional device-playback audio path; protected/private audio may be unavailable. Network permissions are used for direct local discovery and transport.

## Children and advertising

CarCast is not designed as a child-directed service and does not serve advertising.

## Changes and contact

The publisher will update this policy before public distribution and will provide a contact address for privacy questions and legally required requests. This draft is not the final store-linked policy until those details are completed.
