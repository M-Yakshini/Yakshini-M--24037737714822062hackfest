# WitnessSketch AI — Forensic Composite Sketch Generator & Review Platform

**Hackfest Submission by Yakshini M**  
**Repository:** [Yakshini-M--24037737714822062hackfest](https://github.com/M-Yakshini/Yakshini-M--24037737714822062hackfest)

---

## 📌 Project Overview

**WitnessSketch AI** is a full-stack forensic application engineered to assist law enforcement, investigative officers, and forensic artists in constructing, refining, and cataloging witness composite sketches. 

Integrating anatomical landmark calibration with dataset-backed forensic sketch retrieval and generative illustration candidates, the platform bridges witness recollection and actionable criminal composite records.

---

## 🌟 Key Features

### 1. 🎨 Forensic Sketch Editor
- **8 Anatomical Feature Extractors**: Parametric controls for *Face Shape, Hairstyle, Eyebrows, Eye Shape, Nose Shape, Facial Hair, Jawline, and Facial Expression*.
- **Dataset Integration**: Directly connected to the `original_sketch` dataset repository (1,190+ specimens) with calibrated 3-point landmark geometry (`sketch_points`).
- **Multi-Candidate Variations**: Generates 3 visually diverse candidate illustrations for comparative review.
- **Interactive Version Selection & High-Resolution Save**: Select between variations, preview instant composite renders, and commit directly to case files.

### 2. 📊 Investigation Dashboard & Case History
- **Case Tracking**: Auto-associates sketches with official case numbers (e.g., `FIR-2026-00142`), timestamps, and facial attribute summaries.
- **Download & Session Analytics**: Track downloaded sketches, saved cases, and feedback submissions.
- **Forensic Backup Suite**:
  - Export case metadata as structured **JSON**.
  - Export tabular case logs as **CSV**.
  - One-click bulk download of all saved forensic sketches.

### 3. 🌐 Dual-Language Localization (English & தமிழ்)
- Seamless bilingual toggle between **English** and **Tamil (தமிழ்)** across all interface labels, tabs, instructions, dashboard tables, and notifications.

### 4. 💬 Feedback & Forensic Help Centre
- **Knowledge Base & FAQs**: Interactive accordion answering common forensic, formatting, and technical queries.
- **Priority Support Ticketing**: Built-in ticket submission module categorized by bug report, feature request, or technical difficulty.
- **Keyboard Shortcuts**: Rapid investigation hotkeys (`E` for Editor, `D` for Dashboard, `?` for Help Centre, `L` for Language toggle).

---

## 🛠️ Technology Stack

- **Backend**: Java 21, Spring Boot 3.4.x, Spring Security, Spring Data JPA
- **Database**: H2 In-Memory & Persistent Database Engine
- **Frontend**: Responsive Single-Page Application (HTML5, Vanilla CSS3 Cyber-Glassmorphism, Vanilla ES6+ JavaScript)
- **Dataset**: CUHK Face Sketch Database (`original_sketch`) + 3-point facial coordinate landmarks

---

## 🚀 Getting Started

### Prerequisites
- **Java JDK 21** or higher
- **Apache Maven 3.8+**
- Git

### Running Locally

1. **Clone the repository**:
   ```bash
   git clone https://github.com/M-Yakshini/Yakshini-M--24037737714822062hackfest.git
   cd Yakshini-M--24037737714822062hackfest
   ```

2. **Run the Spring Boot application**:
   ```bash
   mvn clean spring-boot:run
   ```

3. **Access the application**:
   Open your browser and navigate to:
   ```
   http://localhost:8080
   ```

4. **Default Authentication**:
   - **Email**: `test@test.com`
   - **Password**: `testpassword123`

---

## ⚖️ Responsible Use & Ethics Notice

> **Forensic Advisory**: Composite sketches produced by WitnessSketch AI are investigative approximations intended solely to assist witness recall and visual lead generation. Sketches do not constitute conclusive identification or legal evidence of identity. All case outputs require verified forensic artist review and judicial oversight.

---

## 👩‍💻 Author

- **Yakshini M**
- GitHub: [@M-Yakshini](https://github.com/M-Yakshini)
- Email: `yakshinim14@gmail.com`
