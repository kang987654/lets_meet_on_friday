[한국어](USER_GUIDE.md) | **English**

# KOSMOS User Guide
> Based on app 0.41.10 · 2026-11-10. Kept in sync with the Korean guide — if they differ, the Korean guide is authoritative.

KOSMOS is an AI assistant that runs entirely on your phone, with no server. Ask in plain language (Korean or English) and it creates calendar events,
reminders and notes, and answers by searching your past conversations, notes and the documents you registered. It always asks for your confirmation before saving.

## 1. Requirements
| Item | Details |
|---|---|
| Device | Android 8.0 (API 26)+, 64-bit (arm64). **About 12 GB of RAM recommended** — it uses about 6.5–7 GB while running. Verified only on a Galaxy S25 Ultra |
| Storage | The model is about 3.6 GB — keep about 4 GB free before downloading. The optional meaning search model adds about 485 MB |
| Network | Only for the first model download (Wi-Fi). Conversations work offline afterwards |
| Language | **Korean & English** (switch in **Settings › Display › Language** or Android system per-app language settings) |

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
- Waking the model takes about 10 seconds (splash screen). After a break, the chat opens once it has tidied up your last conversation, prepared today's briefing and readied the conversation — the splash screen shows what it is doing.
- The app unloads the model when you leave it, to free memory, and wakes it again when you return — meanwhile you can read the conversation, but **the input box is locked** and it and the status pill show text such as "Waking up AI engine…" (typing during this stutters the screen).

## 4. Permissions
Requested only when needed. Everything else keeps working if you decline.

| Permission | Used for |
|---|---|
| Microphone | Voice input |
| Calendar | Adding events to the device calendar and showing device events |
| Notifications · Alarms & reminders | Ringing reminders on time, restoring them after a reboot |

## 5. Using it
**One continuous conversation** — there is no "new chat" button. After 30 minutes of inactivity, the earlier part is summarized into a searchable memory by topic, and the assistant remembers it through that summary — if you come back and ask it to continue its previous answer exactly, it may ask you once to clarify.

| Say | What happens |
|---|---|
| "Schedule a team meeting next Monday at 10 AM" / "다음 주 월요일 10시에 팀 회의 잡아줘" | A confirmation sheet → tap **Add** to save it to the app and the device calendar |
| "Remind me in 30 minutes to drink water" / "30분 뒤에 물 마시라고 알려줘" | A reminder confirmation sheet → it rings on time |
| "Remember that my bike PIN is 4821" / "내 자전거 비밀번호 4821이야 기억해줘" | A note confirmation sheet |
| "My locker number is 214" (stating a fact, no request to save) | It does not save; it asks "Would you like me to save this as a note?" → reply "Yes" to get the confirmation sheet; say anything else and nothing is saved |
| "What was my bike PIN?" / "자전거 비밀번호 뭐였지?" | It searches notes and past conversations and shows the source as a card. A new value you just said in this conversation wins over a saved note |
| "What's on my schedule today?" / "오늘 일정 뭐 있어?" | Shows both app events and device calendar events |
| (with document search on) "돈을 빌려주고 오래 안 받으면 언제 못 받게 돼?" | Searches your library documents, answers, and attaches the document and article it found as a **source card**. Tap the card to open the document at that spot |

- **Confirmation sheets** cancel themselves after one minute. If cancelled, it briefly says nothing was saved.
- **Voice**: tap the microphone in the input bar and speak (up to 30 seconds). The model transcribes, then answers.
- **Attachments**: the paperclip attaches an image or a document. Only the beginning of a document goes into the conversation.
- **Search pop-up**: tap the magnifying glass in the input bar to see two switches. The glass turns blue while either is on. Both are off by default and stay as you set them.
  - **Web search**: Wikipedia is searched only while it is on.
  - **Document search**: answers from the documents you added to the **library** on the Documents screen, only while it is on. It can't be turned on while the library is empty.
- **Read aloud**: the speaker icon under an answer.
- **Header star**: the star next to KOSMOS shows the AI engine state (blue ready · grey starting · yellow hot · red error). When it is yellow or red, the box on the right says "발열 주의" (running hot), "엔진 오류" (engine error) or "모델 없음" (no model); tap it for the reason and **Retry**.

**Other screens** — open the menu (☰) at the top left.
- **Past conversations (지난 대화)**: memories by topic — search, edit or delete summaries, jump back to the original messages.
- **Schedule (일정)**: today and upcoming events, a month calendar with Korean holidays, and to-dos.
- **Documents (문서)**: opens PDF, Excel, CSV, Word and Hangul (hwpx) files without the AI (Excel keeps its original formatting). "채팅으로" sends the document to the chat to ask about it.
  Add documents to the **library (보관함)** and the chat's document search looks inside them (for long documents such as rules, laws and textbooks; the original file stays where it is). With the meaning search model installed, **Prepare meaning search** lets it find passages worded differently.
- **Profile (프로필)**: what the assistant knows about you; pick suggestions drawn from conversations to save.
- **Settings (설정)**: language (System / 한국어 / English), theme, font, chat text size, splash scene, morning briefing time, memory cleanup (weekly review and merging duplicate notes), **meaning search model (optional)** download and delete,
  activity log, data reset. Backup export/import is in the menu of the notes screen.
- **Home widget and Quick Settings tile**: today's events and to-dos; the tile starts voice input directly.

## 6. Privacy
- Conversations, notes, events and voice **never leave the device**. The only network use is the model downloads (chat model and meaning search model) and Wikipedia search when you turn it on. Document search runs on the device only.
- Everything the assistant did (adding events, saving notes, searching) is recorded in Settings › Activity log (활동 기록).
  Each record is chained to the one before it, so if someone deletes or edits a record, a warning appears at the top of the screen (nothing appears when all is well). Write the **chain head** at the bottom of the list (number and first/last 8 characters) somewhere off the phone (paper, another device) to later catch even a log that was rebuilt from scratch. Records from before this update are shown as "before the chain" and are not checked. Resetting conversation history also clears the activity log, and "Data reset" becomes the first record of the new chain.
- Data lives in the app's private storage (other apps cannot read it) and is not separately encrypted. Keep backup files somewhere safe.

## 7. Good to know
- **Short conversational memory** — it directly remembers only the last few turns and finds the rest by searching past conversations and notes. Ask it to remember important things.
- **Memory search matches the same words by default** — without the meaning search model, Korean notes are found with Korean queries and English notes with the same English words. Download it in **Settings › Meaning search model** and tap "Prepare meaning search for memories" once: notes and past chats are then found even when worded differently or asked in another language. New memories are prepared automatically shortly after each reply. Version 0.41.7 rebuilds what was prepared to make meaning search more accurate, so after updating tap "Prepare meaning search for memories" and **Prepare meaning search** in the library once more.
- **Right after switching languages, a few replies may follow the previous language** — if you switch from Korean to English mid-conversation, replies or event titles can stay in Korean until the earlier turns drop out of the recent history. Keep talking in the new language and it settles.
- **It can be wrong** — it is a small model. A confirmation sheet always appears before saving, so check the content.
- **When the device is hot** (battery around 43°C or more) background work such as memory cleanup and briefings is postponed.
- **Sending a chat message during memory cleanup cancels the cleanup** — so your answer does not wait behind it, cleanup stops if it is still preparing its results (an already open review screen stays). Start it again from Settings.
- **Some turns are slow** — after the model wakes and after each answer, the assistant prepares the next conversation once you have stopped typing for 5 seconds (about 5–10 seconds; it waits so typing stays smooth). If you send before that, or when a turn has to rebuild the conversation on the spot, the first answer can take around 10 seconds.

- **Document search cites sources but can be wrong** — the first source on the card is not always the best-matching article; check the other sources below it too. Rephrasing with the document's own terms helps. Without the meaning search model it needs a matching word.
- **The first answer right after turning on document search or changing library documents is slow** — the assistant re-reads the conversation. Scanned PDFs (image-only pages) can't be searched.
- **Preparing meaning search pauses while you chat** and resumes shortly after the answer. It stops when you leave the app; tap **Prepare meaning search** again to continue from where it left off.

## 8. Running on the emulator (developers)
- Copy the model file to the path in section 3 on an x86_64 AVD. The app detects the emulator and runs the model **on CPU only** (the GPU path looks for OpenCL, which emulators lack, and fails at generation).
- Answers work — tool calls and confirmation sheets were verified. It is slow, though (tens of seconds for a short answer, plus a few minutes on first launch to build the CPU cache). Use it to check flows; judge speed and heat on a real device.

## 9. Troubleshooting
| Symptom | Try |
|---|---|
| Model missing / failed to load | Download from Settings › AI model — if it is already installed you see "Installed" and **Download again**, which asks once more (fetches about 3.6GB from the start) |
| Download stuck | Check Wi-Fi — it does not download over mobile data |
| Reminder did not ring | Check the Notifications and "Alarms & reminders" permissions |
| Event missing from the device calendar | Check the Calendar permission (the event is still saved in the app) |

Please report questions and bugs as repository issues.
