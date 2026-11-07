# FBSkills ⚡

**FBSkills** is an enterprise-grade, multi-platform Skills and Progression plugin designed specifically for modern Minecraft SMP networks running **Paper**, **Purpur**, and **Folia** (Minecraft **1.21 through 26.3+**).

---

## 🌟 Key Features

- **Multi-Platform Architecture:** Built from the ground up with native Folia threaded-region support, utilizing `RegionScheduler`, `EntityScheduler`, and `AsyncScheduler` alongside standard Paper compatibility.
- **11 Core Progression Skills:**
  - 🌾 **Farming:** Crop maturation checks, double crop yields, berry harvesting.
  - ⛏️ **Mining:** Anti-exploit block tracker, double ore drops, silk-touch loop protection.
  - 🏺 **Excavation:** Archaeological discoveries, treasure extraction from soils.
  - 🎣 **Fishing:** Master angler double catch, custom junk vs treasure XP tables.
  - 🏹 **Archery:** Folia-safe distance tracking via projectile PDC metadata.
  - ⚔️ **Fighting:** Dynamic damage ratios, custom mob kill rewards.
  - 🛡️ **Defense:** Shield-blocking rewards, rate-limited damage absorption, permanent heart perks.
  - 🏃 **Agility:** Sprint burst tracking, horizontal displacement anti-AFK safeguards, kinetic fall damage dampening.
  - 🔨 **Forging:** Finished gear whitelists (no ingot/block loops), anvil and netherite smithing perks.
  - 🔮 **Enchanting:** Level-cost multiplier rewards, verified grindstone disenchanting.
  - ⚗️ **Alchemy:** `BrewEvent` PDC tagging preventing potion re-insertion spam.
- **Enterprise Storage & Concurrency:**
  - **HikariCP** connection pooling for SQLite and MySQL with automatic table migration.
  - Monotonic revision-based auto-saving and immutable snapshotting to prevent dirty-flag race conditions.
  - Preloaded asynchronous profile caching during `AsyncPlayerPreLoginEvent`.
- **Modern User Experience:**
  - Full **MiniMessage / Adventure** text and gradient support.
  - Live Actionbar XP notifications with visual progress bars.
  - Interactive GUI menus with detailed perk milestone viewers.
- **Ecosystem & Developer Integration:**
  - **Vault Economy:** Direct monetary milestone deposits.
  - **PlaceholderAPI:** Full expansion (`%fbskills_*%`).
  - **Custom Bukkit Events:** `PlayerSkillXpGainEvent` (cancellable & mutable) and `PlayerSkillLevelUpEvent`.
  - **Permission Multipliers:** Configurable booster ranks (`fbskills.multiplier.vip`, etc.).

---

## 🎮 Commands & Permissions

### Commands
- `/skills` - Open the main skills progression overview GUI.
- `/skills <skill>` - Open the detailed milestone & perk viewer for a specific skill.
- `/skills top <skill>` - View the top 10 player leaderboard for a skill.
- `/skills givexp <player> <skill> <amount>` - *(Admin)* Grant skill experience.
- `/skills setlevel <player> <skill> <level>` - *(Admin)* Set a player's skill level.
- `/skills reload` - *(Admin)* Hot-reload configurations and invalidate caches.

### Permissions
- `fbskills.use` (default: true) - Allows viewing the skills GUI and leaderboards.
- `fbskills.admin` (default: op) - Grants administrative XP management and reload permissions.
- `fbskills.multiplier.<group>` - Grants configured XP multipliers (e.g., `fbskills.multiplier.vip`).

---

## 🛠️ Building from Source

Requirements:
- **JDK 21** or higher
- Gradle (included wrapper)

```bash
git clone https://github.com/MSNGAMING/FBSkills.git
cd FBSkills
./gradlew shadowJar
```

Compiled jar artifact will be located at:
`build/libs/FBSkills-1.0.0.jar`

---

## 📄 License
All rights reserved © 2026 FrostByte (Sriram).
