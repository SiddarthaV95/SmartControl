# IoT-Based Smart Switch System using Blynk

This project implements a smart switch system that enables real-time control and monitoring of an electrical appliance through both a physical switch and the Blynk IoT platform (mobile and web dashboard). The system ensures seamless synchronization between the physical switch and the digital interfaces, reflecting accurate appliance status and allowing control from anywhere.

## 🔧 Features

- Real-time appliance state monitoring on Blynk web and mobile dashboard.
- Control appliance using:
  - Physical switch
  - Blynk mobile app
  - Blynk web dashboard
- Sync logic handles both toggled positions of the physical switch.
- Immediate state update from physical switch to dashboard.
- Control input from dashboard takes effect with minimal delay (~0.5s).
- Works without local HTTP server – fully cloud-based via Blynk.

## 🛠️ Hardware Used

- ESP32 microcontroller
- Relay module
- Physical switch (two-way)
- AC appliance (demo/tested with a bulb)
- Internet connection (WiFi)

## 🌐 Platform

- [Blynk IoT Platform](https://blynk.io)
  - Blynk Web Dashboard
  - Blynk Mobile App (iOS/Android)

## ⚙️ How It Works

1. ESP32 reads the state of the physical switch and controls the relay.
2. The relay toggles the appliance ON/OFF.
3. The current appliance state is sent to Blynk dashboard in real time.
4. User can send control commands from app or web dashboard.
5. ESP32 updates the relay state and syncs with the switch logic.

## 📸 Demo

Images of the working prototype have been included in the `demo/` folder and shown in the project presentation.

## ✅ Observations

- Appliance state changes via physical switch are instantly reflected on the Blynk dashboard.
- Control from dashboard incurs a small delay (~0.5 seconds) before execution.
- The system successfully handles state sync between all control points and accurately reflects the ON/OFF state of the appliance.
