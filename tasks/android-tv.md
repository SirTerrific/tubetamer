# Application Android TV native pour TubeTamer

Statut : à faire. Document prévu pour être confié à Opus quand on sera prêt.

## Objectif

Une app Android TV native (Kotlin) qui sert d'interface TubeTamer sur la télévision. L'enfant choisit son profil, entre son PIN, parcourt le catalogue approuvé, demande des vidéos et les regarde.

Précision : l'app ne remplace pas YouTube et n'en est pas un client. C'est une app séparée, qui s'appelle **TubeTamer** (nom affiché, bannière, icône), installée à côté des autres apps de la TV. L'enfant y voit uniquement les vidéos approuvées par le parent, téléchargées et diffusées par le serveur.

Appareil cible : **NVIDIA Shield** (Android TV, 2017 ou 2019). Identifiant de paquet proposé : `com.sirterrific.tubetamer`.

## Principes non négociables

1. **Lecture 100 % depuis le serveur.** Aucun téléchargement ni cache de fichier vidéo sur la TV. Le lecteur lit `GET /api/stream/{video_id}` (HTTP Range) en streaming.
2. **Mémoire maîtrisée sur la TV.** Buffer ExoPlayer plafonné (`DefaultLoadControl`, quelques dizaines de Mo au plus), pas de cache disque vidéo, miniatures via un cache d'images borné.
3. **Lecture locale uniquement.** Pas d'iframe YouTube. Une vidéo n'est lisible que si son statut de téléchargement est `ready`. Il faut `local_playback.enabled: true` côté serveur.
4. **Le serveur reste la source de vérité.** Horaires, limites quotidiennes, catégories, blocages : tout reste appliqué côté serveur. L'app n'applique aucune règle elle-même.
5. **Ne rien casser.** Le site web et le bot Telegram doivent continuer de fonctionner à l'identique, et la suite `pytest` doit rester verte.

## État actuel du serveur (relevé dans le code)

| Besoin | Existant | Remarque |
|---|---|---|
| Choix profil + PIN | `GET/POST /login`, `GET /switch-profile` | HTML, cookie de session signé |
| Catalogue | `GET /api/catalog`, `GET /api/catalog/status` | JSON |
| Recherche | `GET /search` | HTML seulement |
| Demande de vidéo | `POST /request` | Formulaire, jeton CSRF |
| Statut d'une demande | `GET /api/status/{video_id}` | JSON, exempté d'auth |
| Lecture | `GET /api/stream/{video_id}` | Range supporté |
| Statut téléchargement | `GET /api/download-status/{video_id}` | JSON |
| Sous-titres | `GET /api/subs/{video_id}/{lang}` | WebVTT |
| Heartbeat temps de visionnage | `POST /api/watch-heartbeat` | JSON, applique les limites |
| Historique | `GET /api/history` | JSON |
| Activité, demandes en attente | `/activity`, `/requests` | HTML |
| Langue | `POST /api/locale` | JSON |

## Partie A : serveur (Python)

Objectif : une API JSON stable sous `/api/v1/`, sans toucher aux routes HTML existantes.

- [x] **A1. Auth par jeton pour clients natifs.**
  - `POST /api/v1/auth/login` : `{profile_id, pin}` → `{token, expires_at, profile}`.
  - Jeton aléatoire stocké **haché** en base (nouvelle table `device_tokens` : profil, appareil, créé le, dernier usage, révoqué).
  - Middleware : accepter `Authorization: Bearer <jeton>` en plus du cookie. Pas de CSRF pour le Bearer (pas de cookie, pas de risque CSRF).
  - Rate limiting sur le login (anti force brute du PIN), comme le reste de l'app (slowapi).
  - `POST /api/v1/auth/logout` : révoque le jeton.
- [x] **A2. `GET /api/v1/profiles`** : liste des profils (id, nom, avatar), sans PIN. Utilisable avant authentification.
- [x] **A3. `GET /api/v1/home`** : rangées de l'accueil (récents, par catégorie, chaînes, Shorts si activés), avec URLs de miniatures, durée, statut de téléchargement.
- [x] **A4. `GET /api/v1/search?q=`** : JSON. Réutiliser la logique de `web/routers/search.py` (filtres de mots, chaînes bloquées, Shorts, historique de recherche) en la factorisant, pas en la dupliquant.
- [x] **A5. `POST /api/v1/requests`** : demander une vidéo (déclenche la notification Telegram). Réutiliser la logique de `POST /request`.
- [x] **A6. `GET /api/v1/requests`** : demandes du profil avec leur statut (pending, approved, denied).
- [x] **A7. Chaînes :** liste des chaînes autorisées et vidéos d'une chaîne (équivalent des pages chaîne du catalogue web).
- [x] **A8. `GET /api/v1/videos/{id}`** : métadonnées, statut de téléchargement, URL de flux, sous-titres disponibles, position de reprise. Couvert par `POST /api/v1/videos/{id}/play` (même contenu), pas de route séparée.
- [x] **A9. Statut du temps restant :** `GET /api/v1/time` → temps restant par catégorie, fenêtre horaire, raison d'un blocage. L'app l'affiche et se bloque proprement.
- [x] **A10. Flux :** vérifier que `/api/stream/{id}` accepte le Bearer et reste compatible Range (206, `Accept-Ranges`, `Content-Range`) avec ExoPlayer. Ne pas casser le comportement navigateur.
- [x] **A11. Heartbeat :** accepter le Bearer sur `/api/watch-heartbeat`. Réponse claire quand le budget est épuisé (l'app arrête la lecture).
- [x] **A12. Miniatures :** servies par le serveur (`/thumb/...`). Vérifier l'accès avec le Bearer.
- [x] **A13. Parent :** commande Telegram (ou option) pour lister et révoquer les appareils connectés.
- [x] **A14. Tests pytest** pour chaque endpoint v1 : auth, mauvais PIN, jeton révoqué, profil isolé, limites de temps. Mettre à jour `docs/` et les pages OpenWiki concernées.
- [x] **A15. Sécurité :** les jetons ne sont jamais journalisés. Les endpoints v1 respectent le profil du jeton (un enfant ne lit jamais les données d'un autre). Message clair sur l'usage en réseau local (HTTP en clair).

## Partie B : application Android TV

Pile : Kotlin, Jetpack Compose for TV (`androidx.tv`), Media3/ExoPlayer, Retrofit ou Ktor + kotlinx.serialization, Coil (images), Hilt (injection). Cible : NVIDIA Shield (Android TV 11, API 30). `minSdk` 26, `targetSdk` 34 ou plus récent. La Shield décode le H.264 en matériel, donc les MP4 du serveur (360p à 1080p) se lisent sans transcodage.

- [x] **B1. Projet.** Nouveau dossier `android-tv/` dans le dépôt (ou dépôt séparé, à décider). Manifest Leanback : `android.software.leanback`, `LEANBACK_LAUNCHER`, bannière 320x180, `touchscreen` non requis.
- [x] **B2. Configuration serveur.** Écran de premier lancement : saisie de l'adresse du serveur (ex. `http://192.168.x.x:8080`), test de connexion. Enregistrée en local. Autoriser le HTTP en clair uniquement vers le réseau local (`network_security_config`).
- [x] **B3. Choix du profil + PIN.** Grille de profils avec avatars, pavé PIN adapté à la télécommande (D-pad). Jeton stocké dans DataStore, chiffré par une clé AES-GCM du Keystore Android (EncryptedSharedPreferences est déprécié). Déconnexion simple, qui révoque le jeton côté serveur.
- [x] **B4. Accueil.** Rangées horizontales (catalogue par catégorie, chaînes, Shorts si activés, reprise de lecture), focus et navigation D-pad soignés, miniatures via Coil avec cache mémoire/disque **borné**.
- [x] **B5. Recherche.** Champ avec clavier à l'écran et saisie vocale. Résultats avec bouton **Demander**. Écran de confirmation, puis état « en attente d'approbation » avec polling de `/api/v1/requests`.
- [x] **B6. Mes demandes.** Liste avec statuts, mise à jour automatique.
- [x] **B7. Lecteur.**
  - ExoPlayer avec `ProgressiveMediaSource` sur l'URL de flux et l'en-tête `Authorization`.
  - `DefaultLoadControl` : buffer borné (ex. 15 à 30 s), pas de cache vidéo sur disque.
  - Contrôles télécommande : lecture/pause, ±10 s, barre de progression, sous-titres (WebVTT via `/api/subs`), reprise à la dernière position.
  - Libérer le lecteur à la sortie de l'écran (`onStop`/`DisposableEffect`), pas de fuite.
  - Gérer : vidéo pas encore téléchargée (écran d'attente, polling `download-status`), erreur réseau, 404/403.
- [x] **B8. Heartbeat et temps restant.** Envoyer le heartbeat toutes les ~30 s pendant la lecture. À la réponse « budget épuisé » ou « hors horaire » : arrêter la lecture et afficher l'écran correspondant. Afficher le temps restant avant la fin du budget.
- [x] **B9. Écrans d'état :** hors horaires, limite atteinte, vidéo refusée, serveur injoignable (avec réessai), jeton expiré ou révoqué (retour au choix du profil).
- [x] **B10. Localisation :** anglais, français, norvégien, alignés sur la langue du profil ou du serveur.
- [x] **B11. Thème sombre**, polices lisibles à 3 m, zones de focus visibles.
- [ ] **B12. Robustesse mémoire :** profil mémoire (Android Studio Profiler) sur 1 h de lecture et 20 vidéos de suite. Pas de croissance continue. Tester sur une TV bas de gamme (1 Go de RAM) si possible.
- [x] **B13. Distribution sans ADB.** APK signé (clé `release` hors dépôt, `*.jks` et `keystore.properties` dans `.gitignore`). Installation sur la Shield, au choix :
  1. **Depuis le serveur TubeTamer (recommandé) :** le serveur sert l'APK sur une URL fixe (ex. `/app/tubetamer.apk`, sans authentification, l'APK ne contient aucun secret). Sur la Shield, installer l'app **Downloader** (Play Store), saisir l'URL du serveur, autoriser « Installer des apps inconnues » pour Downloader, puis ouvrir l'APK.
  2. **Clé USB :** copier l'APK, brancher la clé sur la Shield, l'ouvrir avec un gestionnaire de fichiers.
  3. **« Send Files to TV » :** envoi direct du PC ou du téléphone vers la Shield.
  Option : mise à jour dans l'app (vérifier un `version.json` sur le serveur, télécharger l'APK, lancer `PackageInstaller`, avec confirmation de l'utilisateur). Le nom affiché reste « TubeTamer ». ADB reste utile pour le débogage, mais n'est pas requis pour installer.

## Environnement de build (PC de développement)

Relevé sur le PC :

| Élément | État |
|---|---|
| Android SDK | `%LOCALAPPDATA%\Android\Sdk`, présent |
| Plateformes | android-28, android-34, android-35 |
| Build-tools | 28.0.3, 34.0.0, 35.0.0 |
| JDK d'Android Studio (JBR) | 21, `C:\Program Files\Android\Android Studio\jbr` |
| `JAVA_HOME` | **JDK 8 (Adoptium)** : trop ancien pour Gradle/AGP actuels |
| `ANDROID_HOME`, `gradle` sur le PATH | absents |
| `adb` | présent dans `platform-tools`, hors PATH |
| Émulateur Android TV | à vérifier (images système 28 et 35 présentes, type à confirmer) |

Conséquences pour le projet :
- Lancer Gradle avec le JBR 21 : `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"` pour les commandes de build, sans modifier le `JAVA_HOME` global du PC. Ou définir `org.gradle.java.home` dans `gradle.properties` local.
- Créer `android-tv/local.properties` (non commité) avec `sdk.dir=C\:\\Users\\jcarbel\\AppData\\Local\\Android\\Sdk`.
- Utiliser le **Gradle wrapper** (`gradlew.bat`) généré par Android Studio : pas besoin d'installer Gradle.
- Compiler avec `compileSdk` 35 (plateforme présente). Build : `gradlew.bat assembleDebug` puis `assembleRelease`. APK dans `app/build/outputs/apk/`.
- Prévoir l'installation de l'image système « Android TV » (API 34) via le SDK Manager pour tester sans la Shield.
- Les tests D-pad et la mémoire se valident sur la Shield réelle, installée sans ADB (voir B13).
- [x] **B14. Tests :** tests unitaires (clients API, modèles), tests UI Compose basiques, scénario manuel complet décrit dans `docs/android-tv.md`.

## Partie C : documentation et livraison

- [x] **C1.** `docs/android-tv.md` : installation de l'APK, configuration du serveur, dépannage.
- [ ] **C2.** Mise à jour de `README.md` et `README.fr.md` (section Android TV) et du `CHANGELOG.md`.
- [ ] **C3.** Release avec bump de version (suivre la procédure de `build-test-release`).

## Hors périmètre (à ne pas faire)

- Pas de lecture directe depuis YouTube, ni d'iframe, ni d'appel à l'API YouTube.
- Pas de téléchargement des vidéos sur la TV.
- Pas de compte Google, pas de Play Services requis pour le fonctionnement.
- Pas de console parent dans l'app : l'administration reste dans Telegram.

## Risques et points à surveiller

- **L'app YouTube de la Shield :** TubeTamer ne la remplace pas, elle reste installée et accessible. Si tu veux que l'enfant n'ait que TubeTamer, il faut bloquer `youtube.com` et `googlevideo.com` par DNS pour la Shield (le serveur TubeTamer, lui, reste autorisé). C'est un choix à faire de ton côté, hors du code de l'app.
- **Mémoire de la Shield :** 2 Go (Shield TV) ou 3 Go (Shield TV Pro), partagés avec Android TV. D'où le buffer borné et le streaming sans cache vidéo.
- **PIN à 4 chiffres :** faible face à la force brute. Limiter les essais côté serveur (A1), avec un verrouillage temporaire.
- **HTTP en clair sur le LAN :** le jeton circule sans chiffrement. Acceptable sur un réseau domestique de confiance, à documenter. Option future : HTTPS via un reverse proxy.
- **Codecs :** les vidéos sont des MP4 (H.264/AAC) via ffmpeg. À vérifier quand même sur la Shield pour chaque qualité.
- **Matériel réel :** impossible de valider la télécommande, les performances et la mémoire sans la Shield. Prévoir des allers-retours de test (installation via `adb`, journaux via `adb logcat`).

## Ordre de travail recommandé

1. A1 à A2, puis B1 à B3 (connexion de bout en bout).
2. A3, A8, A10, A11 puis B4, B7, B8 (voir et lire une vidéo avec contrôle du temps).
3. A4 à A6 puis B5 et B6 (recherche et demandes).
4. A7, A9, puis B9 à B12 (finitions, états d'erreur, mémoire).
5. A13 à A15, B13 et B14, partie C.
