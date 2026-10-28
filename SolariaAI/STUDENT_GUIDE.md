# Solaria: Student Guide

Use this guide on your student computer. Your teacher handles Docker, the AI service, and approvals. You do **not** need the teacher's token, token file, Docker commands, or teacher web console.

> **Current limits:** the default classroom service uses MOCK predictions, which normally say Earth regardless of the drawing. That is a connection demonstration, not real recognition. Scanner submission and review code exists, but live classroom camera/LAN behaviour still needs validation. Approval does not yet guarantee planet generation or travel.

## 1. Get ready

- Use the Minecraft installation and matching Solaria mod setup supplied by your teacher. Join the world/server they specify; ask them for connection details.
- The bundled camera backend currently supports **Windows 10/11**. Connect a webcam and close other apps using it.
- Prepare a clear drawing on paper. Keep your face, names, school details, and other personal information out of the frame.
- Use good light, hold the page flat, and let the drawing fill most of the view.
- Wait until your teacher says the host and service are ready. Students connect through Minecraft, not directly to the teacher's localhost API.

## 2. Open the scanner

Type this in Minecraft chat:

```text
/solariascan
```

Alternatively, right-click the **Solaria Scanner** item your teacher provides. The scanner has a dim background, rounded panels, and a preview that fits the available space. If text or controls are clipped, enlarge the window or ask your teacher to adjust GUI Scale.

Opening this screen does **not** turn on the camera.

## 3. Preview and capture

1. Press **Open camera**. Camera capture is video only; no microphone is used.
2. If camera access is blocked, use **Camera settings**. Windows may require **Camera access** and **Let desktop apps access your camera**. Ask your teacher or administrator if settings are locked; do not bypass school policy.
3. Line up the drawing in the local preview.
4. Press **Capture**. This freezes the photo and turns off the camera.
5. Check the photo. **Retake** discards it so you can try again; press **Open camera** again when ready.

The preview and captured photo stay local until you explicitly submit. **Capture alone does not upload anything.**

## 4. Submit and review the result

1. Press **Submit drawing** only when you are ready to share the frozen photo with the teacher's service. It is relayed through the trusted Minecraft host.
2. Wait for the result. Do not repeatedly submit while a request is running.
3. Check the suggested object. Confidence is not proof that the answer is correct.
4. If it matches your drawing, press **Yes, this is correct**. This records your agreement; it does not replace teacher approval.
5. If it is wrong, ask your teacher to review/correct it, or use **Retry** to make a new scan.
6. Keep the scanner open while waiting for approval. A changed label may require you to agree again. Follow the latest result and your teacher's instructions.

**Close or Esc cancels the active scan.** It also releases the camera. Closing does not delete a photo already submitted from the teacher's in-memory review feed. If you cancel or disconnect, ask your teacher whether a fresh submission is needed.

Even when the host reports both approvals recorded, generation and travel remain unfinished. Do not treat a success message, a button mentioning building, or a teacher decision as proof that a planet was generated or that you will be teleported.

## 5. If something goes wrong

| What you see | What to do |
| --- | --- |
| Camera will not open | Connect it, close other camera apps, check Windows camera permissions, then Retry. Ask your teacher if it still fails |
| Unsupported camera backend | The bundled backend supports Windows 10/11 only; ask your teacher for a supported setup |
| Blurry, dark, or rejected photo | Improve lighting, focus, contrast, and framing, then take a new photo |
| `SERVICE_TOKEN_MISSING` or `UNAUTHORIZED` | Tell your teacher the code. This is a host credential problem, not a student camera permission problem. Never request or enter the teacher's token |
| Service unavailable, timeout, or queue full | Tell your teacher, wait for their instruction, and retry once they say the service is ready |
| Waiting for teacher approval | Leave the scanner open and ask your teacher to review your scan |
| Scan disappeared after a restart | Reviews are temporary. Ask your teacher before submitting again |
| Result always says Earth | Ask whether MOCK mode is enabled. Mock results do not recognise your drawing |

Report the displayed reason code and what step failed. Do not share credentials or screenshots containing private teacher information.

## 6. Privacy and finishing

- No audio is captured. Local preview/capture does not save image files.
- Explicit submission sends the photo to the teacher's service, which holds bounded temporary reviews/thumbnails in memory, not a permanent drawing collection.
- Runtime submissions are not automatically saved as a training dataset. Any separate dataset collection needs its own approved consent process.
- Close the scanner when finished and confirm the camera is off. Tell your teacher if its indicator remains on.
- Follow your teacher's instructions for leaving the world. Automatic return to your original location is not promised by this scan workflow.

**Quick checklist:** join the teacher's world → `/solariascan` → Open camera → Capture → inspect → Submit drawing → check the result → agree if correct → wait for teacher approval.
