# OTP Hopper 🚀

**OTP Hopper** is a privacy-focused Android utility designed to synchronize and forward incoming SMS verification codes (OTPs) to secondary devices or shared groups. It solves the challenge of authenticating shared services (like OTT platforms) when users are physically separated.



## ✨ Features
* **Automated Forwarding:** Seamlessly routes incoming SMS to designated Telegram groups or secondary phone numbers.
* **Smart Keyword Routing:** Configure dynamic rules to forward messages based on specific keywords (e.g., "Netflix", "Hotstar", "Bank").
* **Background Reliability:** Built with a **Foreground Service** to ensure the app remains active and responsive, even on aggressive battery-optimizing devices like Motorola.
* **Local Persistence:** Uses **Jetpack DataStore** with **Kotlin Serialization** for high-performance, type-safe storage of your forwarding rules.
* **Secure & Private:** No cloud storage; all message processing and rule matching happen locally on the device.

## 🛠️ Tech Stack
* **Language:** Kotlin
* **UI Framework:** Jetpack Compose
* **Data Persistence:** Jetpack DataStore
* **Serialization:** Kotlinx Serialization
* **Background Processing:** Android Foreground Services
* **IDE:** Android Studio Ladybug/Otter

## 🚀 Getting Started

### Prerequisites
* Android device running API 24 (Nougat) or higher.
* A Telegram Bot Token and Chat ID.

### Installation & Setup
1.  **Clone the Repo:**
    ```bash
    git clone [https://github.com/ArunElanthamil/OTP-Hopper.git](https://github.com/ArunElanthamil/OTP-Hopper.git)
    ```
2.  **Open in Android Studio:** Import the project and sync Gradle.
3.  **Enable Permissions:** Grant `RECEIVE_SMS` and `READ_SMS` permissions when prompted.
4.  **Configure Rules:** * Open the app and navigate to **Settings**.
    * Add a new rule with a keyword (e.g., "OTP") and your Telegram Bot details.

## 🔒 Security Disclosure
This app requires sensitive SMS permissions to function. It does **not** store your messages on any external server. Data is only transmitted to the destination (Telegram) via encrypted HTTPS requests initiated directly from your device.

---

### UI Philosophy
As a **Senior Front-End Developer**, this project implements a modern **MVVM architecture**. The UI is built entirely with **Jetpack Compose**, focusing on:
* **Unidirectional Data Flow (UDF):** Ensuring state consistency across the pager and rule-setting screens.
* **Performance:** Optimized recomposition for dynamic lists using keys in LazyColumns.
* **UX:** A seamless 3-step onboarding process with custom SVG backgrounds.

---

**Would you like me to add a "Troubleshooting" section specifically for battery optimization on different Android skins?** This is often a common issue for background-heavy apps.
