package fi.aikapolku.app.link

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import fi.aikapolku.app.MainActivity
import fi.aikapolku.app.R
import java.net.ServerSocket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Ohjaajan puhelimen vastaanotto (V5a): kuuntelee samassa verkossa ja ilmoittaa itsestään
 * (DNS-SD), ottaa vastaan liitettyjen asiakaslaitteiden salatut viestit ja näyttää ne ilmoituksena
 * ja etusivulla. Etualan palvelu, jotta viestit tulevat perille myös näytön ollessa pimeänä.
 */
class LinkReceiverService : Service() {
    private var server: ServerSocket? = null
    private var pool: ExecutorService? = null
    private var registration: NsdManager.RegistrationListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        LinkNotify.channels(this)
        val notification = LinkNotify.ongoing(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(LinkNotify.ONGOING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(LinkNotify.ONGOING_ID, notification)
        }
        if (server == null) startServer()
        return START_STICKY
    }

    private fun startServer() {
        val app = applicationContext
        val store = LinkStore(app)
        val host = object : LinkProtocol.Host {
            override val identity: Identity get() = store.identity
            override fun sender(id: String) = store.peers().firstOrNull { it.id == id && !it.receiver }
            override fun addSender(peer: Peer) = store.addPeer(peer)
            override fun confirmPair(name: String, code: String): Boolean {
                LinkNotify.pairRequest(app, name)
                return LinkPairing.request(name, code, LinkProtocol.PAIR_TIMEOUT_MS - 10_000L).also { LinkNotify.cancelPair(app) }
            }
            override fun onMessage(message: LinkMessage) {
                LinkInbox.add(app, message)
                LinkNotify.message(app, message)
            }
        }
        val socket = runCatching { ServerSocket(0) }.getOrNull() ?: return
        server = socket
        val executor = Executors.newCachedThreadPool().also { pool = it }
        thread(name = "aikapolku-link", isDaemon = true) {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                runCatching { executor.execute { runCatching { LinkProtocol.serve(client, host) } } }
            }
        }
        register(socket.localPort, store.identity)
    }

    private fun register(port: Int, me: Identity) {
        val nsd = getSystemService(NsdManager::class.java) ?: return
        val info = NsdServiceInfo().apply {
            serviceName = "AikaPolku ${me.name}".take(60)
            serviceType = LinkProtocol.SERVICE_TYPE
            setPort(port)
            setAttribute("id", me.id)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        registration = listener
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    override fun onDestroy() {
        registration?.let { l -> runCatching { getSystemService(NsdManager::class.java)?.unregisterService(l) } }
        registration = null
        runCatching { server?.close() }
        server = null
        pool?.shutdownNow()
        pool = null
        super.onDestroy()
    }

    companion object {
        private const val ACTION_STOP = "fi.aikapolku.app.link.STOP"

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, LinkReceiverService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, LinkReceiverService::class.java)) }
        }

        /** Sovelluksen käynnistyessä: vastaanotto päälle, jos se on valittu tässä puhelimessa. */
        fun ensure(context: Context) {
            if (LinkStore(context).receiverEnabled) start(context)
        }
    }
}

internal object LinkNotify {
    private const val CHANNEL_MESSAGES = "link_messages"
    private const val CHANNEL_ONGOING = "link_ongoing"
    const val ONGOING_ID = 7301
    private const val PAIR_ID = 7302

    fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, context.getString(R.string.link_channel_messages), NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, context.getString(R.string.link_channel_ongoing), NotificationManager.IMPORTANCE_MIN)
        )
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun ongoing(context: Context): Notification = NotificationCompat.Builder(context, CHANNEL_ONGOING)
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setContentTitle(context.getString(R.string.link_ongoing_title))
        .setContentText(context.getString(R.string.link_ongoing_text))
        .setContentIntent(openApp(context))
        .setOngoing(true)
        .build()

    fun message(context: Context, message: LinkMessage) {
        channels(context)
        val title = message.who.ifBlank { message.fromName }
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify((message.at % 100_000).toInt() + 8000, notification) }
    }

    fun pairRequest(context: Context, name: String) {
        channels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.link_pair_request_title))
            .setContentText(context.getString(R.string.link_pair_request_notify, name))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(PAIR_ID, notification) }
    }

    fun cancelPair(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(PAIR_ID) }
    }
}
