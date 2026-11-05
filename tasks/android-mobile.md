# Application Android mobile (téléphone et tablette) pour TubeTamer

Statut : à faire. Ce document est prévu pour être confié à un modèle moins coûteux (Sonnet). Il est autonome : tout ce qu'il faut savoir est ici ou dans les fichiers cités.

## Objectif

Une app Android native pour **téléphone et tablette**, avec les mêmes fonctions que l'app TV (`android-tv/app`). L'enfant indique l'adresse du serveur TubeTamer, choisit son profil, entre son PIN, parcourt le catalogue approuvé, cherche et demande des vidéos, puis les regarde en streaming depuis le serveur.

L'app TV reste inchangée pour l'utilisateur. On extrait son code non-UI dans un module partagé `:core`, puis on écrit une UI tactile (Material 3) dans un nouveau module `:mobile`.

## Décisions prises (validées par l'utilisateur, ne pas rediscuter)

1. **Module `:core` partagé.** `api/`, `data/`, les ViewModels, `AppContainer`, `Locale.kt`, les helpers testés, les traductions et deux images partagées passent dans `:core`. `:app` (TV) et `:mobile` en dépendent.
2. **Pas de nouveau dossier.** Le projet Gradle reste `android-tv/`, qui contiendra `app/` (TV), `core/` et `mobile/`.
3. **Distribution par le serveur.** Une nouvelle route `/app/tubetamer-mobile.apk`, calquée sur `/app/tubetamer.apk`, avec la config `web.mobile_apk` (`BRG_MOBILE_APK`, défaut `db/tubetamer-mobile.apk`).

## Principes non négociables

1. **Le serveur reste la source de vérité.** Budgets, horaires, approbations et blocages viennent de `/api/v1`. L'app n'applique aucune règle elle-même. Le contrat d'API est décrit dans `openwiki/api-v1.md` et `web/routers/api_v1.py`. **Aucune modification de `/api/v1`.**
2. **La TV ne régresse pas.** Après la phase 1, l'APK TV doit se construire et tous ses tests unitaires doivent passer. Le comportement TV ne change pas.
3. **Packages Kotlin inchangés.** Le code déplacé dans `:core` garde son package (`com.sirterrific.tubetamer.api`, `.data`, `.ui`, racine). Aucun `import` Kotlin ne change, sauf celui de `R` (voir phase 1).
4. **Lecture uniquement depuis le serveur.** On reprend le `PlayerViewModel` partagé et ses heartbeats. Pas d'iframe YouTube, pas de lien vers l'app YouTube, pas de partage (intent `ACTION_SEND`), pas de Picture-in-Picture : le PiP permettrait de continuer à regarder hors de l'app.
5. **Le token reste protégé.** Il est stocké par `SessionStore` et `TokenCipher` (Keystore, `AES-GCM`), qui sont dans `:core`. Le manifeste mobile met `android:allowBackup="false"`.
6. **Trois langues.** Toute nouvelle chaîne est ajoutée en anglais (`values/`), français (`values-fr/`) et norvégien (`values-nb/`).
7. **Rien de cassé côté serveur.** `pytest` reste vert.

## État actuel (relevé dans le code le 2026-11-04)

- Versions (dans `android-tv/gradle/libs.versions.toml`) : AGP 9.4.1, Kotlin 2.4.20, Compose BOM 2026.09.00, `tv-material` 1.1.0, Media3 1.11.1, Coil 3.6.3, OkHttp 5.5.0. AGP 9 compile Kotlin lui-même : il n'y a **pas** de plugin `kotlin-android`.
- `app/build.gradle.kts` : `namespace` et `applicationId` = `com.sirterrific.tubetamer`, `compileSdk`/`targetSdk` 37, `minSdk` 26, Java 17, `buildConfig = true`. La signature release lit `keystore.properties` (non commité). Le build release est minifié.
- Code TV (`app/src/main/java/com/sirterrific/tubetamer/`) :
  - **Sans dépendance TV, à déplacer dans `:core`** :
    - `api/ApiResult.kt`, `api/Models.kt`, `api/ServerUrl.kt`, `api/TubeTamerApi.kt`
    - `data/AuthRepository.kt`, `data/SessionStore.kt`, `data/TokenCipher.kt`
    - `AppContainer.kt`
    - `ui/AppViewModel.kt` (contient `Screen` et `UiError`), `ui/HomeViewModel.kt`, `ui/SearchViewModel.kt`, `ui/PlayerViewModel.kt`
    - `ui/Locale.kt` (`ServerLanguage`, `appLanguage`, Compose seulement)
  - **UI TV, reste dans `:app`** : `MainActivity.kt`, `TubeTamerApp.kt`, `ui/Screens.kt`, `ui/HomeScreens.kt`, `ui/SearchScreens.kt`, `ui/PlayerScreen.kt`, `ui/Components.kt`, `ui/Theme.kt` (`LocalTvLayout`, `tvLayout`), `ui/Icons.kt`.
  - **Helpers purs dans `ui/HomeScreens.kt`, à déplacer dans `:core`** (nouveau fichier `core/.../ui/Format.kt`) : `formatDuration`, `progressFraction` et `resolve(baseUrl, path)`. Les tests `HomeHelpersTest.kt` les utilisent.
- Ressources :
  - Partagées, à déplacer dans `:core` : `values/strings.xml`, `values-fr/strings.xml`, `values-nb/strings.xml` (75 usages de `R.string`), `drawable-nodpi/bg_doodle.png`, `drawable-nodpi/tubetamer_logo.png` (utilisées dans `Components.kt`).
  - Propres à la TV, restent dans `:app` : `drawable-*/banner.png`, `mipmap-*`, `values/themes.xml`, `xml/network_security_config.xml`. La couleur `tt_icon_background` est définie dans `values/themes.xml`, qui reste dans `:app`.
- Tests :
  - `app/src/test/...` : `ServerUrlTest`, `TubeTamerApiTest` (avec `mockwebserver3`), `HomeHelpersTest`, `LocaleTest`, `PlayerHelpersTest`, `SearchHelpersTest`. Ils testent tous du code qui part dans `:core` et partent donc avec lui.
  - `app/src/androidTest/.../ScreensUiTest.kt` teste l'UI TV et reste dans `:app`.
- Déclarations `internal` : elles cessent d'être visibles d'un module à l'autre. Les connues :
  - `Models.kt:48 ErrorBody`
  - `AppContainer.kt:23 Credentials.Value.url`
  - `Locale.kt:19 appLanguage`
  - `HomeViewModel.kt:147 appended`
  - `SearchViewModel.kt:160 withStatuses`
  - `PlayerViewModel.kt:228 resumeMs`

  Règle : si un module (`:app` ou `:mobile`) ne compile plus à cause d'un `internal` de `:core`, passer **cette** déclaration en `public`, rien d'autre. Les tests de `:core` voient ses `internal` sans changement.
- Lecteur TV (`PlayerScreen.kt`) : `PlayerView` dans `AndroidView` avec `keepScreenOn = true`. Il met en pause sur `ON_STOP`, appelle `vm.stop()` dans `onDispose` et utilise `BackHandler`. **Reprendre ces trois comportements à l'identique en mobile** : ils évitent de compter du temps de visionnage app fermée.
- Navigation TV : `AppViewModel.screen` (sealed `Screen` : `Loading`, `ServerSetup`, `Profiles`, `Pin`, `Home`, `Offline`, liste complète). Dans `Home`, un `enum HomePage { HOME, SEARCH, REQUESTS }` local et `BackHandler`.
- Serveur :
  - `web/routers/pwa.py` : `TV_APK_PATH = "/app/tubetamer.apk"` et la route `tv_apk`.
  - `web/middleware.py` : `_ROOT_AUTH_EXEMPT` contient `"/app/tubetamer.apk"`.
  - `config.py` : `WebConfig.tv_apk` (lignes ~63-69 et ~185), lu dans `BRG_TV_APK`.
  - Tests : `tests/test_tv_apk.py`.
- CI `.github/workflows/android-tv.yaml`, déclenchée sur `android-tv/**` :
  - job `unit` : `sh gradlew testDebugUnitTest assembleDebug --console=plain`. La commande s'applique à tous les modules, donc `:core` et `:mobile` sont couverts automatiquement.
  - job émulateur : `api-level: 30`, `target: android-tv`, `arch: x86`, `profile: tv_1080p`, `sh gradlew connectedDebugAndroidTest`.

## Environnement de build (Windows, PC de dev)

- **Piège :** le `JAVA_HOME` système pointe sur le JDK 8 et Gradle échoue avec. Toujours lancer Gradle avec le JBR d'Android Studio :
  ```bash
  cd android-tv
  JAVA_HOME="C:\Program Files\Android\Android Studio\jbr" ./gradlew.bat <tâches> --console=plain
  ```
- SDK : `C:/Users/jcarbel/AppData/Local/Android/Sdk` (dans `android-tv/local.properties`, non commité).
- AVD : `TubeTamer-TV` (TV) et `kuboo35` (Pixel 6, API 35), qui sert pour les tests UI mobile en local.
- Python : `python` n'est pas dans le PATH (alias Microsoft Store). Il faut un venv : l'ancien `$TEMP/ttvenv` a disparu, le recréer avec un Python installé (`py -3 -m venv`, sinon chercher `python.exe`), puis `pip install -r requirements.txt pytest pytest-asyncio`. Lancer `pytest` depuis la racine du dépôt.
- Une sortie longue (Gradle, pytest) se filtre avec `| tail -n 30`. Une erreur se garde en entier.

## Phase 1 : extraire `:core` (sans changement de comportement)

1. `libs.versions.toml` : ajouter le plugin `android-library = { id = "com.android.library", version.ref = "agp" }`. Dans le `build.gradle.kts` racine : `alias(libs.plugins.android.library) apply false`.
2. `settings.gradle.kts` : `include(":app", ":core", ":mobile")`. N'inclure `:mobile` qu'en phase 2 si cela simplifie.
3. Créer `android-tv/core/build.gradle.kts` :
   - plugins `android-library`, `kotlin-compose`, `kotlin-serialization`
   - `namespace = "com.sirterrific.tubetamer.core"`, `compileSdk = 37`, `minSdk = 26`, Java 17, `buildFeatures { compose = true }`
   - dépendances en `api(...)` pour ce que les apps utilisent directement :
     - BOM Compose (`api(platform(...))`), `compose.ui`, `compose.foundation`
     - `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`
     - `datastore-preferences`, `okhttp`, `kotlinx-serialization-json`, `coroutines-android`
     - `coil-compose`, `coil-network-okhttp`
   - `testImplementation` : `junit`, `okhttp-mockwebserver`
   - Aucun code Kotlin n'utilise `BuildConfig` (vérifié le 2026-11-04). `buildConfig` n'est donc pas nécessaire dans `:core`.
4. Créer `core/src/main/AndroidManifest.xml` minimal, avec `<uses-permission android:name="android.permission.INTERNET"/>`.
5. `git mv` les fichiers listés dans « à déplacer dans `:core` » vers `core/src/main/java/com/sirterrific/tubetamer/...`, au **même chemin de package**. Les tests unitaires vont dans `core/src/test/...`. Les `strings.xml` (3 langues) et les deux PNG `drawable-nodpi` vont dans `core/src/main/res/...`.
6. Extraire `formatDuration`, `progressFraction` et `resolve` de `HomeScreens.kt` vers `core/src/main/java/com/sirterrific/tubetamer/ui/Format.kt`, sans changer leur code.
7. Dans `:app` :
   - ajouter `implementation(project(":core"))` et retirer les dépendances désormais fournies par `:core`. Garder `tv-material`, `activity-compose`, `media3-*`, `core-ktx` et les dépendances de test UI.
   - remplacer `import com.sirterrific.tubetamer.R` par `import com.sirterrific.tubetamer.core.R` dans les fichiers qui n'utilisent que des chaînes ou les deux PNG partagés. Un fichier qui utilise aussi une ressource propre à la TV importe les deux, avec `import com.sirterrific.tubetamer.core.R as CoreR`.
   - corriger les `internal` selon la règle plus haut.
8. **Vérification (bloquante)** :
   ```bash
   JAVA_HOME="C:\Program Files\Android\Android Studio\jbr" ./gradlew.bat :core:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --console=plain
   ```
   - Tout doit être vert. Le nombre de tests unitaires doit être **identique** à avant l'extraction : compter avant de commencer, depuis les rapports `app/build/test-results/testDebugUnitTest/*.xml`.
   - `assembleRelease` vérifie que la minification (R8) passe toujours : sans keystore, l'APK n'est simplement pas signé.
   - Si l'émulateur TV est disponible : `:app:connectedDebugAndroidTest` sur `TubeTamer-TV`.
   - Commit séparé : `refactor(android): extract shared :core module`.

## Phase 2 : module `:mobile`

### Build

- `mobile/build.gradle.kts` : plugins `android-application` et `kotlin-compose`.
  - `namespace = "com.sirterrific.tubetamer.mobile"`, `applicationId = "com.sirterrific.tubetamer.mobile"`. L'ID est différent de celui de la TV, pour que les deux apps puissent cohabiter.
  - `versionCode = 1`, `versionName = "0.1.0"`
  - même bloc de signature que `:app` : lecture de `rootProject.file("keystore.properties")`, même clé, même `buildTypes`, `proguard-rules.pro` copié.
- Dépendances :
  - `project(":core")`, `activity-compose`, `core-ktx`
  - Compose **Material 3** (`androidx.compose.material3:material3`, sans version grâce au BOM) et `material-icons-extended` si besoin d'icônes
  - `media3-exoplayer`, `media3-ui`
  - Ajouter les entrées manquantes dans `libs.versions.toml`.
  - **Pas de `tv-material`.**
- Tests : `junit` ; `androidTest` avec `compose-ui-test-junit4`, `androidx-test-runner`, `androidx-test-junit` (copier la config de `:app`).

### Manifeste

- `INTERNET` (hérité de `:core`), `android:allowBackup="false"`, `android:supportsRtl="true"`
- `networkSecurityConfig` : copie de celui de la TV, pour autoriser le HTTP en clair vers un serveur LAN.
- Une seule activité, `MainActivity`, avec `MAIN` + `LAUNCHER`. **Pas** de `LEANBACK_LAUNCHER`, pas de `screenOrientation` fixe.
- `android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden"` : la rotation ne recrée pas l'activité, donc le lecteur ne redémarre pas.
- `android:windowSoftInputMode="adjustResize"`
- Pas de `supportsPictureInPicture` (absent veut dire non).
- Icône : copier `mipmap-*` et `mipmap-anydpi-v26/ic_launcher.xml` depuis `:app`, et définir la couleur `tt_icon_background` dans `mobile/src/main/res/values/colors.xml`, avec la même valeur que dans `app/src/main/res/values/themes.xml`. Ne pas copier `banner.png`.

### Code

Package `com.sirterrific.tubetamer.mobile`.

- `MobileApp : Application, SingletonImageLoader.Factory` : copie de `TubeTamerApp`.
- `MainActivity` : même schéma que la TV.
  - `enableEdgeToEdge()`
  - `AppViewModel.factory(container)`
  - `ServerLanguage(locale)` autour de `AppRoot`
- `ui/MobileTheme.kt` : `MaterialTheme` Material 3, sombre par défaut, couleurs reprises de `TtColors` (TV `Theme.kt`) pour garder l'identité visuelle.
- Écrans Material 3, mêmes états que la TV, chacun branché sur le ViewModel partagé correspondant :

  | `Screen` / page | Mobile |
  |---|---|
  | `Loading` | Logo + `CircularProgressIndicator` |
  | `ServerSetup` | `OutlinedTextField` (clavier `KeyboardType.Uri`, action `Done` qui appelle `vm.connect`), erreur selon `UiError` (reprendre les mêmes chaînes que la TV), bouton Connecter |
  | `Profiles` | Grille adaptative d'avatars (`LazyVerticalGrid(GridCells.Adaptive(140.dp))`) + action « Changer de serveur » (`vm.editServer`) |
  | `Pin` | Champ PIN numérique masqué (`KeyboardType.NumberPassword`), retour vers `backToProfiles` |
  | `Home` | `Scaffold` avec `NavigationBar` en bas (Accueil / Recherche / Demandes) sur téléphone, `NavigationRail` à gauche si largeur ≥ 600dp. Le changement de profil et la déconnexion sont dans le menu de la barre du haut. |
  | Accueil | Lignes du `HomeViewModel` : `LazyColumn` de sections, chacune en `LazyRow` de cartes (miniature 16:9, titre 2 lignes, durée `formatDuration`, barre de progression `progressFraction`). Bouton « Plus » ou chargement en fin de ligne via `loadMore`. Vue chaîne (`openChannel` / `closeChannel` + `BackHandler`). Tirer pour rafraîchir (`PullToRefreshBox`) → `refresh()`. |
  | Recherche | Champ de recherche (action `Search` → `vm.search`), résultats en grille adaptative, bouton Demander → `vm.request`, badges de statut, `startPolling()` / `stopPolling()` liés au cycle de vie de l'écran (`DisposableEffect`) |
  | Demandes | Liste des demandes avec statut |
  | `Offline` | Message + bouton Réessayer |

  Lire `Screens.kt`, `HomeScreens.kt` et `SearchScreens.kt` pour chaque état et chaque appel au ViewModel, et **reproduire exactement les mêmes appels**. Seule la présentation change.
- Les miniatures se chargent avec Coil en passant par le `ImageLoader` de `AppContainer` (il ajoute le bearer token). Les URL se résolvent avec `resolve(baseUrl, path)`.
- **Lecteur** (`ui/PlayerScreen.kt`) :
  - `PlayerViewModel.factory(container)`, mêmes états `PlayerState` et blocages (`Block.*`) que la TV, avec les mêmes messages.
  - `PlayerView` avec `useController = true` (contrôles tactiles), `keepScreenOn = true`, pause sur `ON_STOP`, `vm.stop()` dans `onDispose`, `BackHandler` (copier la TV).
  - Plein écran : en entrant, `activity.requestedOrientation = SCREEN_ORIENTATION_SENSOR_LANDSCAPE` et masquage des barres système (`WindowInsetsControllerCompat.hide(systemBars())`, comportement `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`). À la sortie (`onDispose`) : `SCREEN_ORIENTATION_UNSPECIFIED` et réaffichage des barres.
  - Afficher le temps restant (`vm.remaining`) et l'écran « temps écoulé » (`vm.expired`) comme la TV.
- Tablettes : aucune mise en page figée en dp de TV. Les grilles et lignes s'adaptent à la largeur, et le paysage comme le portrait fonctionnent partout, sauf dans le lecteur qui force le paysage.
- Nouvelles chaînes : celles qui ne servent qu'au mobile (ex. libellés de navigation s'ils manquent) vont dans `mobile/src/main/res/values{,-fr,-nb}/strings.xml`. Réutiliser d'abord les chaînes de `:core`.

### Tests

- `mobile/src/androidTest/.../MobileScreensUiTest.kt`, sur le modèle de `ScreensUiTest.kt` : rendu de `ServerSetup` (saisie + bouton), `Profiles` (affiche les noms), `Pin` et l'état `Offline`, sans réseau, avec des états construits à la main.
- Logique partagée : déjà couverte par les tests de `:core`. Pas de doublon.

### Vérification (bloquante)

```bash
JAVA_HOME="C:\Program Files\Android\Android Studio\jbr" ./gradlew.bat testDebugUnitTest assembleDebug :mobile:assembleRelease --console=plain
```

Ensuite, émulateur `kuboo35` (Pixel 6) démarré :

```bash
JAVA_HOME="C:\Program Files\Android\Android Studio\jbr" ./gradlew.bat :mobile:connectedDebugAndroidTest --console=plain
```

Test manuel sur `kuboo35`, avec un serveur TubeTamer local (`python main.py`, `local_playback.enabled: true`) joignable depuis l'émulateur à `http://10.0.2.2:8080` :

- [ ] Saisie de l'adresse, profils, PIN faux puis bon
- [ ] Accueil, défilement, vue chaîne, retour
- [ ] Recherche, demande, approbation via Telegram, le statut change
- [ ] Lecture, rotation, retour, reprise à la position
- [ ] Mise en arrière-plan pendant la lecture : la vidéo est en pause et les heartbeats s'arrêtent (logs serveur)
- [ ] Budget épuisé : le lecteur bloque avec le bon message
- [ ] Langue du serveur `fr` puis `nb` : l'UI suit
- [ ] Changement de profil, déconnexion
- [ ] Taille tablette : `adb shell wm size 2560x1600` puis `adb shell wm size reset`. Navigation rail et grilles plus larges.

Commit : `feat(android): add phone and tablet app (:mobile)`.

## Phase 3 : serveur (Python)

1. `config.py` : ajouter `mobile_apk: str = ""` dans `WebConfig`, avec le même schéma que `tv_apk` : défaut `BRG_MOBILE_APK` sinon `"db/tubetamer-mobile.apk"`, aux deux endroits où `tv_apk` est lu.
2. `web/routers/pwa.py` :
   - ajouter `MOBILE_APK_PATH = "/app/tubetamer-mobile.apk"` et une route GET/HEAD calquée sur `tv_apk`, `filename="tubetamer-mobile.apk"`, 404 `"Mobile app not installed on this server."`
   - factoriser le corps commun aux deux routes dans un helper (`_serve_apk(path, filename, missing_msg)`).
3. `web/middleware.py` : ajouter `"/app/tubetamer-mobile.apk"` à `_ROOT_AUTH_EXEMPT`, avec un commentaire.
4. `config.example.yaml` et `docs/configuration.md` : documenter `web.mobile_apk` / `BRG_MOBILE_APK` à côté de `tv_apk`.
5. Tests : `tests/test_mobile_apk.py`, copie adaptée de `tests/test_tv_apk.py` (404 si absent, 200 sans login avec bon content-type et content-disposition, HEAD).
6. Vérification : `pytest` complet vert, avec le même nombre de tests qu'avant plus les nouveaux.

Commit : `feat(web): serve the mobile app at /app/tubetamer-mobile.apk`.

## Phase 4 : CI, docs, livraison

1. `.github/workflows/android-tv.yaml` :
   - le job `unit` couvre déjà les trois modules (tâches racine). Vérifier dans les logs que `:core:testDebugUnitTest` et `:mobile:assembleDebug` tournent.
   - job émulateur : limiter l'existant à `sh gradlew :app:connectedDebugAndroidTest`, et ajouter un job (ou une entrée de matrice) téléphone : `api-level: 30`, `target: google_apis`, `arch: x86_64`, `profile: pixel_6`, `sh gradlew :mobile:connectedDebugAndroidTest`.
   - garder le nom du workflow `Android TV Tests`, car le badge README en dépend. Sinon, mettre le badge à jour.
2. Docs :
   - `docs/android-mobile.md` + `docs/android-mobile.fr.md`, sur le modèle de `docs/android-tv.md` : build, installation depuis `http://<serveur>:8080/app/tubetamer-mobile.apk` (autoriser les sources inconnues dans le navigateur), premier lancement, dépannage.
   - Conseil parent : épingler l'app (« Épinglage d'écran ») ou utiliser Family Link pour que l'enfant ne sorte pas de l'app. L'app ne peut pas l'imposer elle-même.
   - Mettre à jour `android-tv/README.md` (trois modules, commandes) et `docs/android-tv.md` (la commande `docker cp` vaut aussi pour `tubetamer-mobile.apk`).
   - README EN/FR : une ligne qui renvoie vers la doc mobile.
   - `CHANGELOG.md` : entrée dans `Unreleased`.
3. Ne **pas** éditer `openwiki/` à la main. Il se régénère (`openwiki --update`).
4. Vérification finale :
   - `pytest`
   - `./gradlew.bat testDebugUnitTest assembleDebug`
   - push, puis surveiller les deux workflows (`gh run list`, `gh run watch <id> --exit-status`) jusqu'à ce qu'ils soient verts.

## Hors périmètre

- Toute modification de `/api/v1` ou de la logique serveur hors de la route APK.
- iOS, PWA, mode hors ligne, téléchargement de vidéos sur l'appareil, Chromecast, PiP, notifications push.
- Renommer `android-tv/` ou changer l'`applicationId` TV.
- Changer l'UI TV, en dehors des imports de `R` et des `internal` passés en `public`.

## Risques et points à surveiller

- **`R` non transitif (AGP 9)** : les chaînes déplacées sont dans `com.sirterrific.tubetamer.core.R`. Une erreur `Unresolved reference: string` vient d'un import de `R` oublié.
- **R8 et kotlinx.serialization** : les règles de release restent dans les apps. Si `:mobile:assembleRelease` échoue ou plante au démarrage, comparer avec `app/proguard-rules.pro`. Ajouter `consumerProguardFiles` dans `:core` seulement si nécessaire.
- **Temps de visionnage** : un lecteur qui continue en arrière-plan ou en PiP compterait du temps app fermée, ou en dehors de tout contrôle. Pause sur `ON_STOP` obligatoire, et vérifier dans les logs serveur que les heartbeats s'arrêtent.
- **Rotation** : sans `configChanges`, l'activité est recréée. Les ViewModels survivent, mais `PlayerView` et `ExoPlayer` doivent être libérés et recréés proprement. Avec `configChanges`, le risque disparaît.
- **Émulateur et serveur local** : depuis l'émulateur, le PC hôte est `10.0.2.2`, pas `localhost`.

## Ordre de travail

1. Phase 1, puis vérification et commit. **S'arrêter et signaler** si les tests TV ne sont pas identiques.
2. Phase 2, puis vérification, test manuel et commit.
3. Phase 3, puis `pytest` et commit.
4. Phase 4, puis CI verte.

Ne pas pousser sur `main` sans demande explicite de l'utilisateur.
