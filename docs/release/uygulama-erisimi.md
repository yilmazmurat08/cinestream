# App access instructions (for Google Play reviewers)

Paste the text below into Play Console > App content > App access > "All or some functionality is restricted" > Add instructions. The field allows 500 characters; this text is about 430.

```
No login needed. The app plays the user's own playlist. To test:
1. Open the app, choose "Phone" (or "TV").
2. Paste this link into "M3U Playlist or Stream Link":
https://raw.githubusercontent.com/yilmazmurat08/cinestream/main/play-store/demo-playlist.m3u
3. Leave the Gemini API key empty and tap "Start the AI-Powered Experience".
All features are free; PRO only removes the 60 min/day viewing limit. QR login on TV is optional.
```

## Notes

- The demo playlist contains only freely licensed test streams (Apple and Mux HLS test streams) and Blender Foundation open movies (Big Buck Bunny, Tears of Steel, Elephants Dream; Creative Commons). It is served from `main`, so this branch must be merged before review.
- AI features need the user's own Gemini API key. **Optional:** create a separate Gemini key with a low quota in Google AI Studio and add it to the instructions as `Gemini API key (optional, rate-limited): <key>`. The key is only for reviewers; delete it after review. Do not reuse your personal key.
