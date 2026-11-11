# Altered Beast (Commodore 64 Native Android Port)

Porting penuh dan penerjemahan 1:1 seluruh basis kode asli *Altered Beast* versi Commodore 64 (karya Michael J Archer, 1989) ke platform Android native (`minSdk 24` / Android 7.0 hingga versi terbaru) dengan identitas aplikasi **Altered Beast** dan paket `com.alteredbeast.port`, mempertahankan 100% logika permainan asli serta aset biner tanpa kompresi.

### User Review & Critical Decisions

> [!IMPORTANT]
> Seluruh preferensi yang Anda konfirmasikan pada tahap klarifikasi telah dikunci sebagai spesifikasi utama pembangunan aplikasi:

- **Tampilan & Rasio Layar (Confirmed)**: Mode **Landscape layar penuh** mengikuti rasio layar perangkat, merender framebuffer C64 (320x200 multicolor bitmap/character + hardware sprite multiplexer + panel status) secara penuh ke seluruh permukaan layar.
- **Gaya Perenderan Visual (Confirmed)**: **Piksel tajam Nearest-Neighbor murni** (`FilterQuality.None` / tanpa interpolasi bilinear) sesuai resolusi dan palet 16-warna VIC-II C64 asli, dengan seluruh aset asli (`.SPR`, `.CHR`, `.COL`, `.BLK`, `.MAP`, `.FAC`, `.EXP`, `.PIC`, `.DAT`) dimuat mentah (*uncompressed*) byte-demi-byte.
- **Skema Kontrol Sentuh SVG Melayang (Confirmed)**: Disesuaikan secara presisi berdasarkan pemetaan register input `CIA1` (`$DC00`/`$DC01`) dan `NMI` di dalam source code asli (`WHATPLAYER`, `JOYGET`, `FIREBUTTON`, dan `NMI`):
  - **8-Arah D-Pad + Tombol FIRE**: Memetakan bit 0–4 register joystick C64 (`UP`, `DOWN`, `LEFT`, `RIGHT`, `FIRE`) yang digunakan oleh `JOYGET` untuk pergerakan pemain, lompat, pukulan (*punch*), tendangan (*kick*), serta serangan *Altered Beast*.
  - **Tombol F1 (1 Player) & F7 (2 Player)**: Memetakan matriks keyboard `CIA1` (`$FE` -> `$EF` dan `$F7`) yang dibaca oleh rutin `WHATPLAYER` dan `NOPE` pada layar tabel High Score / Title.
  - **Tombol PAUSE (Shift Lock) & RESTORE**: Memetakan pemeriksaan matriks `SHIFTLOCK` (`CIA1` `$FD` -> `$7F`) pada variabel zero-page `PAUSE` di rutin `JOYGET`, serta sinyal `RESTORE` (`NMI` / variabel `QUIT`) untuk kembali ke urutan awal permainan.
  - **Toggle 2P / Cheat Chord Opsional**: Mendukung input Port 1 & Port 2 serta kombinasi tombol asli di `WHATPLAYER`.
- **Standar Kualitas Kode (Confirmed)**: Arsitektur modular yang bersih, terstruktur secara profesional, **tanpa spaghetti code**, serta **tanpa komentar dan tanpa emoji** di dalam seluruh file source code.

---

### 1. Overview & Core Concept

- **What It Does**: Menjalankan seluruh alur permainan *Altered Beast* C64 secara native di Android mulai dari sekuens `INTRO` (efek *bitmap roller* `STONE.PIC` dan `ALTERED.PIC`, animasi sprite pembuka, dan musik SID intro), layar High Score & pemilihan 1/2 Pemain, hingga kelima level permainan (`LEVEL1` s.d. `LEVEL5`), pertarungan bos tiap akhir level (`ENDSTART`, `UPDATEEND`, `ENDSEQUENC`), layar intermission (`INTER1` s.d. `INTER5`), dan sekuens tamat (`END1` s.d. `END8`).
- **Target Audience / Persona**: Penggemar game retro dan pelestari arsip klasik yang menginginkan pengalaman *Altered Beast* C64 yang 100% autentik secara mekanik dan visual di perangkat Android modern.
- **Key Value**: Eksekusi native berkecepatan tinggi dengan penyesuaian *frame rate cap* otomatis mengikuti *refresh rate* layar perangkat pengguna (`Display.getSupportedModes()` / `Choreographer`), menjaga kecepatan simulasi logika asli (50Hz PAL `SYNC`) tetap konsisten tanpa sedikit pun perubahan pada perilaku musuh, fisika lompatan, *hit detection*, maupun *scrolling*.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Peluncuran & Urutan Intro (`INTRO/X0..X7`)**: Aplikasi langsung masuk ke mode *immersive full-screen landscape*. Mesin mengeksekusi modul `INTRO` yang menampilkan efek *raster roller* pada `STONE.PIC` dan `ALTERED.PIC` beserta panel bawah `PANNEL.CHR` dan pemutaran `INTRO/MUSIC.DAT`. Menekan tombol **FIRE** memicu transisi `STARTGAME` menuju modul permainan utama.
  2. **Layar High Score & Pemilihan Pemain (`GAME/X0.DIS` - `DISPLAYHIGH` & `WHATPLAYER`)**: Menampilkan daftar peringkat skor tertinggi (`RANK SCORE RD. NAME`) dan instruksi `F1 1 PLAYER F7 2 PLAYER`. Pengguna dapat menekan tombol SVG melayang **F1**, **F7**, atau **FIRE** untuk memulai permainan.
  3. **Gameplay Utama Level 1–5 (`GAME/X1..X5`, `LEVEL1..5.PDS`, `ENEMY1..5.PDS`)**: Layar menampilkan area bermain *smooth-scrolling* 4-arah/horizontal, *multiplexed sprites* untuk pemain, serigala (*wolf*), bola *power-up* (*bonus balls*), penyihir (*wizard*), nisan/ular/musuh spesifik tiap level, serta panel status di bagian bawah (skor, nyawa, indikator kepala *Altered Beast*, dan *health bar*).
  4. **Transisi Bos, Intermission & End Sequence**: Saat `ENDSCROLL` tercapai, bos level muncul (`LEVEL1..5.FAC` & `LEVEL1..5EN.SPR`). Setelah dikalahkan (`KILLED != 0`), layar menampilkan sekuens wajah dan layar cerita intermission sebelum memuat level berikutnya hingga tamat di Level 5.
- **Visual Identity & Theme**:
  - *Aesthetic Direction*: **Authentic C64 VIC-II Hardware Precision** dengan overlay kontrol SVG bergaya *minimalist tactical arcade HUD* yang semi-transparan agar tidak menghalangi piksel permainan.
  - *Color Palette & Mood*: Menggunakan palet 16-warna autentik chip VIC-II Commodore 64 (`#000000` Black, `#FFFFFF` White, `#880000` Red, `#AAFFEE` Cyan, `#CC44CC` Purple, `#00CC55` Green, `#0000AA` Blue, `#EEEE77` Yellow, `#DD8855` Orange, `#664400` Brown, `#FF7777` Light Red, `#333333` Dark Grey, `#777777` Medium Grey, `#AAFF66` Light Green, `#0088FF` Light Blue, `#BBBBBB` Light Grey). Overlay kontrol menggunakan aksen emas/amber tipis (`#F59E0B` dengan opasitas rendah) dan permukaan gelap transparan (`#111827` pada alfa 35%).
  - *Typography & Hierarchy*: Seluruh teks di dalam permainan dirender langsung dari bitmap karakter 8x8 asli (`PANNEL.CHR` dan `LEVEL1..5.CHR`).
  - *Component Styling & Layout*: Kanvas utama dirender penuh (`fillMaxSize()`) dengan pemfilteran piksel `FilterQuality.None`. Kontrol sentuh SVG ditempatkan secara ergonomis di sisi kiri bawah (8-Way D-Pad SVG), sisi kanan bawah (Tombol **FIRE** utama berukuran responsif), dan bar atas tipis melayang (Tombol **F1**, **F7**, **PAUSE**, dan **RESTORE**).
- **Interactive Feedback & Motion**:
  - Input multi-sentuh (*multi-touch pointer tracking*) berlatensi nol sehingga pemain dapat menahan arah diagonal pada D-Pad sambil menekan tombol **FIRE** secara bersamaan untuk melakukan *mid-air punch*, *kick*, atau serangan *Altered Beast* persis seperti pada perangkat keras C64.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Penerjemahan 1:1 Berbasis Mesin Status & Memori Terpetakan C64 (*Zero Logic Alteration*)**
  - *Chosen Approach*: Menerjemahkan seluruh kode rakitan PDS 6502 (`INTRO/X0..X7`, `GAME/X0..X5`, `LEVEL1..5.PDS`, `ENEMY1..5.PDS`, `SCOPY2.PDS`, `LOADER.PDS`, `TLOADER.PDS`, `SPRITES/LEVEL4..5.PDS`) bersama ruang memori 64KB, tabel Zero-Page (`ZP`), pembalik bit sprite (`REVERSETABLE` di `SCOPY2.PDS`), *raster IRQ/NMI multiplexer* (`X1.DIS`), dan synthesizer audio SID (`$D400` / `MUSIC.DAT`) secara langsung di Kotlin.
  - *Why*: Kode asli *Altered Beast* C64 sangat bergantung pada tabel *lookup* alamat memori, modifikasi kode mandiri (*self-modifying code* seperti `MAINIRQL+1`, `SECONDT+1`, `LASTDIR+1`, `PLACEPLUS+2`), dan tata letak memori `.SPR`/`.CHR`/`.MAP`/`.BLK`. Pendekatan ini menjamin **nol perbedaan gameplay** dari versi aslinya.
  - *Alternatives Considered*: Menulis ulang fisika dan AI menggunakan engine game umum (ditolak karena pasti mengubah *frame timing*, *hitbox*, dan perilaku asli game).
- **Decision 2: Penyimpanan Aset Tanpa Kompresi (*Zero Asset Compression*)**
  - *Chosen Approach*: Menyimpan seluruh file aset asli dari `GAME/` dan `INTRO/` secara mentah tanpa kompresi dan mengonfigurasi `androidResources { noCompress += ... }` di Gradle agar APK pun tidak mengompresi byte aset tersebut.
  - *Why*: Memenuhi instruksi ketat untuk tidak mengompresi satu pun aset game dan memungkinkan pembacaan *zero-copy* langsung ke memori runtime.
- **Decision 3: Penyesuaian Otomatis FPS Cap Mengikuti Layar Perangkat Pengguna**
  - *Chosen Approach*: Mendeteksi *refresh rate* tertinggi/aktif dari layar perangkat pengguna (`Display` API & `Choreographer` frame callback, misalnya 60Hz, 90Hz, 120Hz, atau 144Hz) dan menyinkronkan loop perenderan native ke FPS cap perangkat tersebut, sementara detak logika internal `SYNC` (50Hz PAL C64) dijalankan dengan akumulator waktu berpresisi nanodetik.
  - *Why*: Memastikan aplikasi berjalan sangat mulus sesuai batas FPS perangkat pengguna tanpa mempercepat atau memperlambat kecepatan gameplay asli.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌────────────────────────────────────────────────────────────────────────────┐
│                   ANDROID NATIVE LAYER (Jetpack Compose)                   │
│                                                                            │
│  ┌──────────────────────────────────┐  ┌────────────────────────────────┐  │
│  │   Full-Screen Nearest-Neighbor   │  │   Floating SVG Touch Controls  │  │
│  │       VIC-II Frame Canvas        │  │  (Multi-Touch D-Pad, FIRE, F1, │  │
│  │        (320x200 -> Screen)       │  │      F7, PAUSE, RESTORE)       │  │
│  └─────────────────▲────────────────┘  └───────────────┬────────────────┘  │
│                    │                                   │                   │
│  ┌─────────────────┴────────────────┐                  │                   │
│  │   Adaptive Frame Rate Pacer      │                  ▼                   │
│  │ (Device Hz Detector & VSync Cap) │  ┌────────────────────────────────┐  │
│  └─────────────────▲────────────────┘  │   CIA1 ($DC00/$DC01) & NMI     │  │
│                    │                   │      Input Matrix Bridge       │  │
└────────────────────┼───────────────────┴───────────────┬───────────────────┘
                     │                                   │
┌────────────────────┼───────────────────────────────────▼───────────────────┐
│                    │         CORE C64 ENGINE & GAME LOGIC                  │
│                    │                                                       │
│  ┌─────────────────┴────────────────┐  ┌────────────────────────────────┐  │
│  │   VIC-II Raster & Multiplexer    │  │   SID 6581 Audio Synthesizer   │  │
│  │  - Playfield Scroll (XSCROLL)    │  │  - 3-Voice Waveform Generator  │  │
│  │  - Multicolor Char & Bitmap      │  │  - ADSR Envelope & Ring/Sync   │  │
│  │  - 15-Zone IRQ Sprite Multiplex  │  │  - GAME & INTRO MUSIC.DAT SFX  │  │
│  └─────────────────▲────────────────┘  └───────────────▲────────────────┘  │
│                    │                                   │                   │
│  ┌─────────────────┴───────────────────────────────────┴────────────────┐  │
│  │             COMPLETE TRANSLATED C64 ALTERED BEAST MODULES            │  │
│  │  - INTRO Module (X0..X7: Bitmap Roller, Title Sprites, Music Driver) │  │
│  │  - GAME Core (X0..X5: High Score, IRQ/NMI, Scroll, Player/Hit Logic) │  │
│  │  - Sprite Mirroring & Copy Engine (SCOPY2.PDS: WACKER/SECONDT/RVSED) │  │
│  │  - Level & Boss Logic (LEVEL1..5.PDS, SPRITES/LEVEL4..5.PDS)         │  │
│  │  - Enemy AI & Spawn Tables (ENEMY1..5.PDS: Zombies, Dragon, etc.)    │  │
│  │  - Virtual Disk/Memory Loader (LOADER.PDS & TLOADER.PDS -> Assets)   │  │
│  └──────────────────────────────────▲───────────────────────────────────┘  │
│                                     │                                      │
│  ┌──────────────────────────────────┴───────────────────────────────────┐  │
│  │           UNCOMPRESSED NATIVE ASSET REGISTRY (RAW BYTES)             │  │
│  │   LEVEL1..5 (.BLK, .CHR, .COL, .MAP, IN.CHR, EN.CHR, EN.COL)         │  │
│  │   SPRITES (.SPR, .FAC, .EXP), GRAPHICS (.PIC, PANNEL.CHR), MUSIC.DAT │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────┘
```

- **Data Model & State**:
  - **Zero-Page & Game State Registers (`X0.DIS`)**: Menyimpan seluruh variabel status asli pada alamat yang identik (`SCREENENABLE`, `SCREENDI`, `XSCROLL`, `SCROLL`, `ENDSCROLL`, `ALTERED`, `BALLS`, `FACEFLASH`, `HITS`, `DIR`, `UP`, `DOWN`, `LEFT`, `RIGHT`, `FIRE`, `PAUSE`, `QUIT`, `PLAYERS`, `LIVES`, `LEVEL`, `SCORELO/MI/HI`, `KILLED`, `GRAVEX/Y/B`, `SNAKEX/Y/B/O`, `BALLINDEX`, `SORTTABLE`).
  - **Uncompressed Asset Loader (`LOADER.PDS`)**: Memetakan seluruh tabel file `LEV1..5`, `INT1..5`, dan `ENDST1..8` langsung ke byte mentah dari `SPRITES/`, `LEVEL1..5/`, `GRAPHICS/`, dan `INTRO/` di memori tanpa kehilangan satu byte pun.
- **Interactive Component & State Mapping**:
  - **Multi-Touch D-Pad SVG Overlay**: Menggunakan `pointerInput` untuk melacak posisi jari secara simultan dan memperbarui bit 0–3 (`UP`, `DOWN`, `LEFT`, `RIGHT`) pada register `CIA1` (`$DC00`/`$DC01`).
  - **Tombol FIRE SVG Overlay**: Memperbarui bit 4 (`FIRE`) pada `CIA1`, yang langsung dibaca oleh `FIREBUTTON` (saat Intro), `WHATPLAYER` (saat layar High Score), `TOPL` (`PRESS BUTTON TO START`), dan `JOYGET` (saat pertarungan).
  - **Tombol F1 / F7 / PAUSE / RESTORE SVG Overlay**: Mengatur matriks baris/kolom `CIA1` untuk memulai permainan 1 Pemain (`F1`), 2 Pemain (`F7`), menahan jalannya permainan (`PAUSE` / Shift Lock), atau memicu interrupt `NMI` (`RESTORE`) untuk mereset sesi permainan.
  - **Indikator & Adaptasi FPS Cap**: Membaca `Display.Mode.getRefreshRate()` secara dinamis saat inisialisasi dan saat konfigurasi layar berubah, mengatur *frame pacing* ke FPS maksimal layar perangkat sementara timer 50Hz PAL menjalankan siklus IRQ/NMI dan logika frame secara presisi.
