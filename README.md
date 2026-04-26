# NewGrokChat

A beautiful Android chat application powered by x.ai Grok API with a native Grok-inspired dark theme.

## Features

- 🤖 Chat with Grok AI models (grok-4.20-beta, grok-3, grok-2-latest)
- 🌙 Native dark theme with blue/purple gradient accents
- 📱 Material Design 3 UI
- ⚡ Streaming response support
- 💾 Conversation history persistence
- 🔧 Multiple API endpoints support

## Screenshots

The app features a sleek black background with:
- Grok logo and model selector in the toolbar
- Beautiful message bubbles with gradient effects
- Smooth chat experience

## Tech Stack

- **Language**: Kotlin
- **UI**: Material Design 3, ViewBinding
- **Architecture**: MVVM
- **Async**: Coroutines + StateFlow
- **Network**: OkHttp + SSE
- **Storage**: SharedPreferences + Gson
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)

## API Configuration

This app uses the apiyi API service with the following endpoints:

| Endpoint | Description |
|----------|-------------|
| http://vip.apiyi.com:16888 | US Optimized |
| http://api-cf.apiyi.com:16888 | Cloudflare CDN |
| http://api.apiyi.com:16888 | China Optimized 1 |
| http://b.apiyi.com:16888 | China Optimized 2 |

## Getting Started

1. Clone the repository
2. Open in Android Studio
3. Configure your API key in Settings
4. Build and run

## Build

```bash
./gradlew assembleDebug
```

APK will be generated at `app/build/outputs/apk/debug/`

## License

MIT License
