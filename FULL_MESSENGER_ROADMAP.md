# NeuroGram — full messenger implementation plan

This branch starts the shared data foundation for the complete feature set. It is not a claim that Telegram parity is already finished.

## Included in this foundation
- Typed Kotlin models for profiles, channels, channel posts, typing state, call invitations and sticker packs.
- Firebase repository entry points for public channel discovery, channel creation/join/leave, channel publishing, typing indicators, media upload and call invitations.
- Existing application ID, Firebase project and signing workflow are intentionally left unchanged.

## Remaining integration and verification
- Connect the new repository to Compose navigation and add channel/profile/contact/settings screens.
- Add and deploy matching Firestore and Storage security rules for channels, calls, typing and sticker packs.
- Implement contact discovery, group administration UI, attachment picker, voice-message recording/playback, message actions and push delivery.
- Add a vetted end-to-end encrypted secret-chat protocol; do not invent cryptography or store private keys in Firestore.
- Add WebRTC signaling, permissions, foreground call service and TURN infrastructure for audio/video calls.
- Add bot API/runtime with scoped tokens, rate limits and abuse controls.
- Add sticker/GIF catalog and caching.
- Add automated unit, emulator and Firebase Rules tests; build and install-test the APK before release.

## Important
The repository currently uses Firestore/Storage rules that do not yet authorize all new collections. Do not deploy the new UI against production until matching least-privilege rules are reviewed and published. The call invitation record is signaling metadata only; it does not itself establish an audio/video connection.
