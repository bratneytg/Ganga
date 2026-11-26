package com.bratneytg.ganga.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bratneytg.ganga.R
import com.bratneytg.ganga.core.Countries
import com.bratneytg.ganga.core.Fmt
import com.bratneytg.ganga.core.Item
import com.bratneytg.ganga.core.Offer

/** Notificaciones del móvil cuando un artículo baja de tu objetivo o llega a su mínimo histórico. */
object DealNotifier {
    private const val CHANNEL = "ofertas"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL, "Ofertas", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "Avisos cuando un artículo baja de tu precio objetivo o llega a su mínimo histórico"
        nm.createNotificationChannel(channel)
    }

    fun message(item: Item, best: Offer, reasons: List<String>): Pair<String, String> {
        val flags = ArrayList<String>()
        if (reasons.contains("target") && item.targetPrice != null) flags.add("por debajo de tu objetivo (${Fmt.eur(item.targetPrice)})")
        if (reasons.contains("lowest")) flags.add("mínimo histórico")
        val where = best.store + if (Countries.get(best.country) != null) " (${Countries.name(best.country)})" else ""
        val detail = "${Fmt.eur(best.priceEur)} + ${Fmt.eur(best.shippingEur)} de envío" +
            if (best.importEur > 0) " + ${Fmt.eur(best.importEur)} de importación" else ""
        val short = "${Fmt.eur(best.landedEur)} en total en $where: ${flags.joinToString(" y ")}."
        return short to "$short\n$detail"
    }

    fun notifyDeal(ctx: Context, item: Item, best: Offer, reasons: List<String>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val open = Intent(Intent.ACTION_VIEW, Uri.parse(best.url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        val pending = PendingIntent.getActivity(
            ctx,
            item.id.hashCode(),
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val (short, long) = message(item, best, reasons)
        val notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_tag)
            .setContentTitle("Ganga: ${item.name}")
            .setContentText(short)
            .setStyle(NotificationCompat.BigTextStyle().bigText(long))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(item.id.hashCode(), notification)
        } catch (e: SecurityException) {
            // sin permiso de notificaciones
        }
    }

    /** Aviso de prueba desde Ajustes, para comprobar que las notificaciones llegan. */
    fun notifyTest(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)
        val notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_tag)
            .setContentTitle("Ganga: aviso de prueba")
            .setContentText("Así te avisaré cuando un artículo baje de tu precio objetivo.")
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(1, notification)
        } catch (e: SecurityException) {
            // sin permiso de notificaciones
        }
    }
}
