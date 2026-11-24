# Privacy Policy for Paperly

**Effective Date:** November 24, 2026  
**Developer:** JO Production  
**Contact Email:** [joproduction6@gmail.com](mailto:joproduction6@gmail.com)

JO Production ("we", "our", or "us") built **Paperly** as a privacy-first mobile utility for PDF and image processing. This Privacy Policy explains how information is handled when you use the Paperly Android application ("the App").

By using Paperly, you agree to the practices described in this Privacy Policy.

---

## 1. Overview & Data Controller

Paperly does not require user accounts, registration, or sign-in. Most PDF and image tools execute **100% locally on your device**. Optional cloud-powered AI tools transmit data over encrypted HTTPS only when you explicitly initiate those features, and third-party services (Google AdMob and Google Play Billing) operate as described below.

---

## 2. Local On-Device PDF & Image Processing

All core document and image utilities run strictly on your device without uploading your files to JO Production servers:

- **PDF Tools:** Merge PDF, Split PDF, Compress PDF (preset and exact KB/MB targets), Scan to PDF (using CameraX and Google ML Kit Document Scanner), E-Sign PDF, Lock PDF (128-bit password encryption), and the built-in PDF Viewer (including password-protected PDF unlocking).
- **On-Device Image Tools:** Exact-KB Image Compressor, Crop & Pixel Resizer, Image to Text (on-device Google ML Kit Text Recognition / OCR), Background Remover (on-device Google ML Kit Subject Segmentation), Passport Photo Sheet Maker, and AI Image Upscaler.

Files you create or import are stored locally in Paperly's private app storage on your device (along with local metadata such as file names, sizes, and page counts in an on-device Room database) until you export, share, delete individual files, clear local files in **Settings**, or uninstall the App.

---

## 3. Optional Cloud AI Features (HTTPS Processing)

Paperly offers two optional internet-connected AI features that are **only triggered when you explicitly tap to use them**:

### a. AI Summarize (Gemini AI)
When you choose to summarize a PDF:
- Paperly extracts text from the selected document on your device (up to 18,000 characters, or up to 3 rendered preview page images for image-only scanned PDFs).
- That extracted payload is sent over an **encrypted HTTPS connection** through our secure API proxy (`paperly-ai.dark2242v2.workers.dev`) to Google's Gemini API to generate your summary.
- JO Production does **not** store, log, or sell your document text, images, or generated summaries on our servers.

### b. AI Text to Image
When you choose to generate an image from a text prompt:
- Your text prompt, selected style, and aspect ratio are transmitted over **encrypted HTTPS** to Pollinations (`image.pollinations.ai`) to generate and return the requested image.
- Please avoid entering sensitive personal, confidential, or financial details into AI text prompts.

---

## 4. Permissions & Device Access

Paperly adheres to Google Play's least-privilege permission principles:

- **Camera (`android.permission.CAMERA`):** Requested only at runtime when you choose to capture document pages or snap photos for Scan to PDF or Image-to-Text OCR. Camera frames are processed locally on your device.
- **Photos & Document Selection (Zero Broad Storage Permissions):** Paperly does **not** request broad storage permissions (`READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, or `READ_MEDIA_VIDEO`). Instead, it uses Android's system **Photo Picker** and **Storage Access Framework** so the App can only access the specific photos or PDFs you explicitly select, or locations where you choose to save exported files.
- **Network Access (`INTERNET`, `ACCESS_NETWORK_STATE`):** Used solely for optional cloud AI requests, Google Play Billing verification, and loading advertisements for free-tier users.

---

## 5. Advertising (Google AdMob)

Free-tier users of Paperly see advertisements provided by **Google AdMob** (including adaptive banners, native ads, and optional rewarded video ads used to unlock temporary credits for AI or Pro-gated tools).

- Google AdMob may collect and process device identifiers (such as the Android Advertising ID), IP address, coarse location derived from IP, ad interaction events, and crash/diagnostic telemetry to deliver, cap, and measure advertisements in accordance with [Google's Privacy Policy](https://policies.google.com/privacy) and [How Google uses information from sites or apps that use our services](https://policies.google.com/technologies/partner-sites).
- Paperly has explicitly opted out of Android Privacy Sandbox AdServices attribution/topics permissions (`ACCESS_ADSERVICES_ATTRIBUTION`, `ACCESS_ADSERVICES_TOPICS`, `ACCESS_ADSERVICES_AD_ID`) in the application manifest.
- **Ad-Free Experience:** Users with an active **Paperly Pro** subscription do not receive any AdMob banner, native, or rewarded advertisements, and ad requests are suppressed while Pro is active.

You can reset or limit ad personalization anytime through your Android device's **Settings → Privacy → Ads** (or **Google → Ads**).

---

## 6. Subscriptions & Payments (Google Play Billing)

Paperly offers optional auto-renewing subscriptions (**Paperly Pro Monthly** and **Paperly Pro Yearly**, including eligible 3-day free trials) processed exclusively through **Google Play Billing** (`com.android.vending.BILLING`).

- All payment methods, billing details, taxes, trial eligibility, renewals, and cancellations are handled securely by Google Play.
- JO Production **never** receives or stores your credit card numbers, bank details, or Google account passwords. We only receive anonymized purchase tokens and subscription entitlement status from Google Play to unlock Pro features and restore purchases across devices.
- You can manage or cancel your subscription at any time in the **Google Play Store → Payments & subscriptions**.

---

## 7. Data Retention & User Control

Because JO Production does not maintain user accounts or cloud storage for your files:
- You have full control over your local PDFs and images at all times.
- You can delete individual items from the **Files** tab, remove all locally stored Paperly files at once via **Settings → Clear Local Files**, or uninstall the App to permanently erase all local app data and preferences.

---

## 8. Children's Privacy (Under 13)

Paperly is a general-audience productivity application and is **not directed to children under the age of 13** (or the equivalent minimum age in applicable jurisdictions). We do not knowingly collect personal information from children under 13. If you are a parent or guardian and believe that a child under 13 has provided personal information through the App or our support channels, please contact us at [joproduction6@gmail.com](mailto:joproduction6@gmail.com) so we can take appropriate action.

---

## 9. Security

We take reasonable technical safeguards to protect your data in transit and on device:
- Optional cloud AI calls use encrypted **HTTPS/TLS** transport.
- Optional PDF password locking applies **128-bit PDF encryption** locally on your device.
- Release builds apply code obfuscation and Google Play release integrity checks.

---

## 10. Changes to This Privacy Policy

We may update this Privacy Policy from time to time to reflect new features, legal requirements, or third-party SDK updates. Any changes will be posted in this document with an updated **Effective Date**.

---

## 11. Contact Us

If you have any questions, requests, or privacy concerns regarding **Paperly**, please contact **JO Production**:

- **Developer:** JO Production  
- **Email:** [joproduction6@gmail.com](mailto:joproduction6@gmail.com)
