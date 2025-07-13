# 📱 IoT-Based Smart Switch System using Blynk (Smartphone + USB-TTL Based)

This project demonstrates a low-cost smart switch system using an **old Android smartphone**, the **Blynk IoT platform**, and a **relay module** controlled via a **USB to TTL converter**. It allows real-time control and monitoring of electrical appliances through both a physical switch and Blynk (web/mobile dashboard), with seamless synchronization.

---

## 🔧 Features

- Real-time appliance control and monitoring via:
  - Physical switch
  - Blynk mobile app
  - Blynk web dashboard
- Synchronization logic ensures:
  - Changes from physical switch reflect on Blynk
  - Remote Blynk commands update the appliance and stay in sync
- Minimal delay (~0.5s) for cloud-based operations
- No microcontroller used — replaces ESP32 with an **Android phone + USB-TTL**

---

## 🛠️ Hardware Used

- Old Android smartphone (USB OTG supported)
- **USB to TTL converter** (e.g., CP2102/FTDI)
- **Relay module** (5V)
- Two-way physical switch
- AC appliance (bulb used for demo)
- Internet connection (via phone’s WiFi or hotspot)
- USB OTG cable (to connect TTL converter to phone)

---

## 🌐 Platform

- **Blynk IoT Platform**
- Blynk Web Dashboard
- Blynk Mobile App (Android/iOS)

---

## ⚙️ How It Works

1. The **Android phone runs a Blynk-connected script/app** (e.g., via Termux or a custom Java app).
2. The phone communicates with the relay module using a **USB to TTL converter** via serial communication.
3. The **physical switch** changes state and is detected either by GPIO input logic or manual sync.
4. Any command (from switch or Blynk) updates the relay, toggling the appliance ON/OFF.
5. The current state is sent to Blynk for remote monitoring.

---

## ✅ Observations

- System works reliably without a microcontroller.
- Delays are minimal due to direct serial control via the phone.
- Sync logic prevents desync between physical and digital inputs.
- Useful for **repurposing old phones into smart IoT controllers**.

---
