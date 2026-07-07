# Plan de Correction: Synchronisation des Tickets (Local-First - V2.9.7) - COMPLÉTÉ

## Résumé des Corrections

### ✅ 1. GaboomAgentApp - Initialisation au démarrage
- `OfflineLimitEnforcer` injecté et initialisé au démarrage (`recompute()` après 1s)
- `SyncManager` injecté directement (plus via `Provider`) pour initialisation immédiate
- Sync déclenché au démarrage si des tickets sont en attente
- Boucle périodique de recompute toutes les 2 minutes

### ✅ 2. OfflineLimitEnforcer - Temps de grâce
- Ajout de `GRACE_PERIOD_MS = 2 minutes` après le démarrage de l'app
- Si `lastContact == 0` mais app démarrée depuis < 2 min, ne pas bloquer
- `getAppStartTime()` / `setAppStartTime()` ajouté à `AgentConfigDataStore`
- `recordServerContact()` appelle automatiquement `recompute()`

### ✅ 3. SyncManager - Sync périodique
- Boucle périodique toutes les 5 minutes pour relancer le sync si tickets en attente
- Même mécanisme d'auto-sync au retour réseau préservé

### ✅ 4. HeartbeatWorker - Recompute après chaque appel
- `offlineLimitEnforcer.recompute()` appelé dans tous les cas (succès, 401, erreur, exception)

### ✅ 5. Numéro de version
- Version bump: 2.9.6 → 2.9.7 (versionCode 35 → 36)
- Landing page mise à jour (v2.9.7)
- APK en cours de build...

### 🔄 En cours
- Build APK (assembleRelease) - ~82%
- Copie APK vers static/
- Git commit

## Fichiers modifiés
1. `android_app/app/src/main/kotlin/com/gaboom/agent/GaboomAgentApp.kt` - Initialisation complète
2. `android_app/app/src/main/kotlin/com/gaboom/agent/data/sync/OfflineLimitEnforcer.kt` - Grace period
3. `android_app/app/src/main/kotlin/com/gaboom/agent/data/config/AgentConfigDataStore.kt` - App start time
4. `android_app/app/src/main/kotlin/com/gaboom/agent/data/sync/SyncManager.kt` - Periodic sync
5. `android_app/app/src/main/kotlin/com/gaboom/agent/data/sync/HeartbeatWorker.kt` - Recompute on all paths
6. `android_app/app/build.gradle.kts` - Version bump
7. `templates/landing/index.html` - Version display update