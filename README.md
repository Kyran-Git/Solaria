# Solaria

Solaria is a Minecraft-based astronomy learning tool designed for secondary school classrooms. It aims to bridge hands-on drawing with interactive 3D exploration: students draw an astronomy concept on paper, scan it with an in-game camera item, and have a local machine-learning service clean and classify the drawing. Once a result is accepted, students can travel to a dedicated dimension for that concept and fly around a full-scale voxel sphere of the celestial body, while a teacher oversees the classroom workflow and retains authority to review or override classifications.

## Repository Structure

This workspace contains two companion projects that work together:

| Folder | What it is | Where it runs |
|---|---|---|
| `SolariaAI/` | Standalone Python package for dataset training, ONNX export, drawing cleaning, quality checks, classification, and a local HTTP service with a teacher review console. | The teacher's machine, bound to loopback (`127.0.0.1`). |
| `SolariaMC/` | Fabric mod for Minecraft Java Edition 26.1.2 providing the camera item, scanning GUI, webcam capture and server relay, custom dimensions, spherical voxel generation, and teacher moderation tools. | The teacher's machine (hosting a LAN world) and student machines (joining as clients). |

## How Solaria Works

1. **Draw and capture:** A student sketches an astronomy concept on paper and scans it using the in-game camera interface on their Minecraft client.
2. **Relay and clean:** The student client sends the downsized capture to the teacher's Minecraft server, which relays the image over loopback HTTP to `SolariaAI`. The AI service runs perspective/illumination cleaning and quality checks.
3. **Classify with confidence gating:** The classifier predicts one of the supported astronomy classes. Predictions below the confidence threshold (default `0.60`) are reported as `unknown` so uncertain drawings do not trigger automatic generation.
4. **Teacher oversight:** Teachers oversee scans and hold final authority—a teacher's approval or override always takes precedence over the model's prediction.
5. **Explore in 3D:** Accepted concepts let students travel to the corresponding `solaria:<class>` dimension and fly around a full-size spherical voxel model centred at the origin.

## Core Design Goals & Principles

- **Strict AI boundary:** `SolariaMC` never embeds ML models or inference libraries. `SolariaAI` runs as an independent local process on the teacher's machine, authenticated with a startup bearer token over loopback HTTP.
- **Aligned class contract:** Ten lowercase astronomy classes—`sun`, `earth`, `moon`, `mars`, `jupiter`, `venus`, `saturn`, `uranus`, `neptune`, and `mercury`—are shared identically between the model metadata and the mod's dimensions (`solaria:<class>`).
- **Classroom privacy:** All processing stays on the local classroom network and the teacher's machine, with no cloud services or external uploads, and participant codes used instead of student usernames in logs.
- **Educational focus:** Students explore planetary and solar scale through Creative-mode flight in dedicated dimensions designed around each celestial body.

