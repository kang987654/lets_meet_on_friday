[한국어](USER_GUIDE.md) | **English**

# KOSMOS User Guide
> Based on app 0.38.9 · 2026-10-29. Kept in sync with the Korean guide — if they differ, the Korean guide is authoritative.

> **Note — the app is Korean-only for now.** The screens, the assistant's instructions and the read-aloud voice are all Korean.
> An English version of the app is planned. Until then, this guide is mainly for understanding, building and running the project.

KOSMOS is an AI assistant that runs entirely on your phone, with no server. Ask in plain language and it creates calendar events,
reminders and notes, and answers by searching your past conversations and notes. It always asks for your confirmation before saving.

## 1. Requirements
| Item | Details |
|---|---|
| Device | Android 8.0 (API 26)+, 64-bit (arm64). **About 12 GB of RAM recommended** — it uses about 5 GB while running. Verified only on a Galaxy S25 Ultra |
| Storage | The model is about 3.6 GB — keep about 4 GB free before downloading |
| Network | Only for the first model download (Wi-Fi). Conversations work offline afterwards |
| Language | Korean only (English planned) |

## 2. Install
There is no prebuilt APK yet. Build from source:

```bash
git clone https://github.com/kang987654/lets_meet_on_friday.git
cd lets_meet_on_friday
./gradlew installDebug
```

You can also open the project in Android Studio (JDK 17+) and run it.

## 3. First launch — getting the model
- On the first screen, tap the download button (모델 내려받기) to fetch about 3.6 GB over Wi-Fi. It resumes after interruptions and keeps going in the
  background. You can download again later from Settings › AI model (설정 › AI 모델).
- To install it manually, copy `gemma-4-E4B-it.litertlm` to `Android/data/com.kosmos.app/files/models/`.
- Waking the model takes about 10 seconds (splash screen). The app unloads the model when you leave it, to free memory, and wakes it again when you return.

## 4. Permissions
Requested only when needed. Everything else keeps working if you decline.

| Permission | Used for |
|---|---|
| Microphone | Voice input |
| Calendar | Adding events to the device calendar and showing device events |
| Notifications · Alarms & reminders | Ringing reminders on time, restoring them after a reboot |

## 5. Using it
**One continuous conversation** — there is no "new chat" button. After 30 minutes of inactivity, the earlier part is summarized into a searchable memory by topic.

| Say (in Korean) | What happens |
|---|---|
| "다음 주 월요일 10시에 팀 회의 잡아줘" (team meeting next Monday at 10) | A confirmation sheet → tap Add to save it to the app and the device calendar |
| "30분 뒤에 물 마시라고 알려줘" (remind me to drink water in 30 min) | A reminder confirmation sheet → it rings on time |
| "내 자전거 비밀번호 4821이야 기억해줘" (remember my bike code is 4821) | A note confirmation sheet |
| "내 사물함 번호는 214번이야" (stating a fact, no request to save) | It does not save; it asks "메모해 둘까요?" (shall I note it?) → reply "응" (yes) to get the confirmation sheet |
| "자전거 비밀번호 뭐였지?" (what was my bike code?) | It searches notes and past conversations and shows the source as a card |

- **Confirmation sheets** cancel themselves after one minute. If cancelled, it briefly says nothing was saved.
- **Voice**: tap the microphone in the input bar and speak (up to 30 seconds). The model transcribes, then answers.
- **Attachments**: the paperclip attaches an image or a document. Only the beginning of a document goes into the conversation.
- **Web search**: Wikipedia is searched only while the globe toggle in the input bar is on. Off by default.
- **Read aloud**: the speaker icon under an answer.

**Other screens** — open the menu (☰) at the top left.
- **Past conversations (지난 대화)**: memories by topic — search, edit or delete summaries, jump back to the original messages.
- **Schedule (일정)**: today and upcoming events, a month calendar with Korean holidays, and to-dos.
- **Documents (문서)**: opens PDF, Excel, CSV, Word and Hangul (hwpx) files without the AI (Excel keeps its original formatting). "채팅으로" sends the document to the chat to ask about it.
- **Profile (프로필)**: what the assistant knows about you; pick suggestions drawn from conversations to save.
- **Settings (설정)**: theme, font, chat text size, splash scene, morning briefing time, memory cleanup (weekly review and merging duplicate notes),
  activity log, data reset. Backup export/import is in the menu of the notes screen.
- **Home widget and Quick Settings tile**: today's events and to-dos; the tile starts voice input directly.

## 6. Privacy
- Conversations, notes, events and voice **never leave the device**. The only network use is the model download and Wikipedia search when you turn it on.
- Everything the assistant did (adding events, saving notes, searching) is recorded in Settings › Activity log (활동 기록).
- Data lives in the app's private storage (other apps cannot read it) and is not separately encrypted. Keep backup files somewhere safe.

## 7. Good to know
- **Short conversational memory** — it directly remembers only the last few turns and finds the rest by searching past conversations and notes. Ask it to remember important things.
- **It can be wrong** — it is a small model. A confirmation sheet always appears before saving, so check the content.
- **When the device is hot** (battery around 43°C or more) background work such as memory cleanup and briefings is postponed.
- **Some turns are slow** — right after the model wakes, or after a long conversation is condensed, the first answer can take 10+ seconds.

## 8. Running on the emulator (developers)
- Start an x86_64 AVD with **`-gpu swiftshader_indirect`** (with the default GPU mode, generation fails). Copy the model file to the path in section 3.
- With runtime 0.18.0, answer generation on the emulator is currently blocked — only non-inference features (screens, navigation, storage) can be checked.
  A fix is planned ([roadmap, Korean](../plans/0.39.x-roadmap.md)).

## 9. Troubleshooting
| Symptom | Try |
|---|---|
| Model missing / failed to load | Download again from Settings › AI model |
| Download stuck | Check Wi-Fi — it does not download over mobile data |
| Reminder did not ring | Check the Notifications and "Alarms & reminders" permissions |
| Event missing from the device calendar | Check the Calendar permission (the event is still saved in the app) |

Please report questions and bugs as repository issues.
