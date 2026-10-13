package org.graphiks.kadre.platform.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

/**
 * Le seam SDK du service foreground `mediaProjection` — la responsabilité KADRE (ruling
 * P8-0, roadmap :246) : le service appartient à la bibliothèque pendant une session
 * HostChoice active, démarré AVANT `createVirtualDisplay` et arrêté au terminal. Le host
 * n'a RIEN à déclarer — le manifest de la bibliothèque merge le service et ses permissions
 * ; la conséquence documentée est la notification de capture visible pendant les sessions
 * HostChoice (exigence Android, capabilities/PR).
 */
internal interface AndroidCaptureForeground : AutoCloseable {
    /**
     * Démarre le FGS `mediaProjection` pour [token] (`startForegroundService` + la notification
     * minimale de la bibliothèque) et suspend JUSQU'À ce que `startForeground(
     * FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)` ait tourné — l'ordre que la plateforme
     * exige avant `createVirtualDisplay` (fact sheet §5a) et que le test hôte
     * `serviceStartedBeforeVirtualDisplayAndStoppedAtTerminal` épinglé. Un démarrage refusé
     * (hôte en arrière-plan, service absent) lève [AndroidCaptureForegroundRefused].
     */
    public suspend fun start(token: String)

    /** Arrête le service — le chemin du terminal de session, quel qu'il soit. */
    public fun stop()

    override public fun close()
}

/** Le refus d'un démarrage de FGS — le port le traduit en `Temporary(retryable = true)`. */
internal class AndroidCaptureForegroundRefused(public val code: String) : Exception(code)

/** La clef de l'extra du jeton de session dans l'intent du service. */
internal const val ANDROID_CAPTURE_SERVICE_TOKEN = "kadre-capture-token"

/**
 * Le registre process-wide de préparation du FGS — le service y signale que
 * `startForeground` a tourné pour son jeton, le contrôle de production y attend ce signal
 * (la sérialisation `startForeground` < `createVirtualDisplay`, sans aucune course : le
 * onStartCommand du service est asynchrone, l'attente suspend la réservation).
 */
internal object AndroidCaptureForegroundRegistry {
    private val lock = Any()
    private val armed = HashMap<String, CompletableDeferred<Unit>>()

    /** Arme l'attente pour [token] — avant `startForegroundService`, aucune course possible. */
    public fun arm(token: String): CompletableDeferred<Unit> = synchronized(lock) {
        armed.getOrPut(token) { CompletableDeferred(Unit) }
    }

    /** Le signal du service : `startForeground(type = mediaProjection)` a tourné pour [token]. */
    public fun ready(token: String) {
        synchronized(lock) { armed[token] }?.complete(Unit)
    }

    /** Oublie l'attente de [token] — le chemin du refus, sans fuite. */
    public fun forget(token: String) {
        synchronized(lock) { armed.remove(token) }
    }
}

/**
 * LE service foreground de capture Kadre — `mediaProjection`, déclaré dans le manifest de la
 * bibliothèque (`exported = false`, `foregroundServiceType = "mediaProjection"`, les
 * permissions `FOREGROUND_SERVICE_MEDIA_PROJECTION` et `POST_NOTIFICATIONS` mergées). La
 * notification minimale est créée par la bibliothèque (le ruling P8-0 : le host ne déclare
 * RIEN) ; le service signale [AndroidCaptureForegroundRegistry] dès que `startForeground` a
 * tourné, puis ne fait plus rien — la capture elle-même vit dans la réservation du port.
 */
public class AndroidCaptureService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithType()
        intent?.getStringExtra(ANDROID_CAPTURE_SERVICE_TOKEN)?.let(AndroidCaptureForegroundRegistry::ready)
        return START_NOT_STICKY
    }

    /** La notification minimale de la bibliothèque, puis le `startForeground` de la bonne forme d'API. */
    private fun startForegroundWithType() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW),
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(NOTIFICATION_TITLE)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            // La forme trois-arguments que la fact sheet §5a exige AVANT createVirtualDisplay ;
            // le type `mediaProjection` n'existe qu'à partir de l'API 29.
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        const val CHANNEL_ID = "kadre-capture"
        const val CHANNEL_NAME = "Screen capture"
        const val NOTIFICATION_ID = 0x4b444354 // "KCAP" — l'id stable de la bibliothèque
        const val NOTIFICATION_TITLE = "Screen capture"
    }
}

/**
 * Le contrôle de production du FGS : `startForegroundService` vers [AndroidCaptureService],
 * l'attente bornée du signal `startForeground`, et le `stopService` du terminal. Le jeton de
 * session voyage dans l'extra — le service le rend au registre au retour de `onStartCommand`.
 */
internal class AndroidServiceCaptureForeground(
    private val context: Context,
) : AndroidCaptureForeground {

    override suspend fun start(token: String) {
        val deferred = AndroidCaptureForegroundRegistry.arm(token)
        val intent = Intent(context, AndroidCaptureService::class.java)
            .putExtra(ANDROID_CAPTURE_SERVICE_TOKEN, token)
        try {
            context.startForegroundService(intent)
        } catch (refused: Throwable) {
            AndroidCaptureForegroundRegistry.forget(token)
            throw AndroidCaptureForegroundRefused("foreground-start-refused")
        }
        try {
            withTimeout(FGS_START_TIMEOUT) { deferred.await() }
        } catch (timeout: TimeoutCancellationException) {
            // Un timeout n'est pas une annulation du port : si le port lui-même est annulé,
            // ensureActive relance l'annulation vraie ; sinon le FGS n'a jamais annoncé.
            AndroidCaptureForegroundRegistry.forget(token)
            currentCoroutineContext().ensureActive()
            throw AndroidCaptureForegroundRefused("foreground-start-timeout")
        }
    }

    override fun stop() {
        context.stopService(Intent(context, AndroidCaptureService::class.java))
    }

    override fun close() = stop()

    private companion object {
        val FGS_START_TIMEOUT = kotlin.time.Duration.parse("5s")
    }
}
