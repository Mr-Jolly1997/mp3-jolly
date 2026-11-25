# MP3 Jolly

Un lettore musicale per Android, semplice e senza fronzoli, per i file audio che hai sul telefono.

**Niente pubblicità, niente account, niente internet.** L'app non ha il permesso di connettersi a internet e non contiene librerie di terze parti: usa solo l'SDK di Android.

## Funzioni

- Schede Brani, Playlist, Cartelle, Album, Artisti e Generi, con copertine e ricerca in tutta la libreria
- Esclusione automatica degli audio di WhatsApp e Telegram, cartelle escludibili a scelta, filtro per i file brevi
- Riproduzione in background con comandi nelle notifiche e sulla schermata di blocco
- Coda (scorri un brano verso destra per aggiungerlo), casuale, ripeti, timer di spegnimento
- Velocità di riproduzione da 0,5× a 2× ed equalizzatore con bassi ed effetto spaziale
- Preferiti e playlist
- Tema chiaro/scuro/di sistema, cinque profili colore, ogni colore personalizzabile con anteprima

Requisiti: Android 8.0 (API 26) o superiore. L'interfaccia è in italiano.

## Permessi

| Permesso | A cosa serve |
|---|---|
| `READ_MEDIA_AUDIO` (Android 13+) / `READ_EXTERNAL_STORAGE` (fino ad Android 12) | Leggere i file audio |
| `POST_NOTIFICATIONS` | Mostrare i comandi del brano nella tendina (facoltativo) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK` | Continuare a suonare a schermo spento |
| `MODIFY_AUDIO_SETTINGS` | Equalizzatore ed effetti |

## Compilare

Serve il JDK 17, l'SDK di Android (piattaforma 36) e Gradle 8.14.3 (la versione è indicata in `gradle/wrapper/gradle-wrapper.properties`). In alternativa basta aprire la cartella con Android Studio.

```
gradle assembleRelease
```

L'APK non firmato si trova in `app/build/outputs/apk/release/`.

Segnalazioni e suggerimenti: [Issues](https://github.com/Mr-Jolly1997/mp3-jolly/issues).

## Sviluppo

L'app è stata sviluppata con l'assistenza di un modello di intelligenza artificiale (Claude, di Anthropic). Il codice è stato rivisto e l'app è stata provata su un telefono reale.

## Licenza

Copyright © 2026 Stefano

Questo programma è software libero: puoi ridistribuirlo e/o modificarlo secondo i termini della GNU General Public License, versione 3 o (a tua scelta) qualsiasi versione successiva, come pubblicata dalla Free Software Foundation. Vedi il file [LICENSE](LICENSE).

---

## English

A simple local music player for Android. No ads, no account, no internet permission, no third-party libraries. Licensed under the GNU GPL v3.0 or later. The user interface is in Italian.
