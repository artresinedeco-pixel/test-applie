package com.wyl.obd

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

// Valeurs affichées. NaN = pas de réponse de la voiture.
data class Mesures(
    val rpm: Float = Float.NaN,
    val vitesse: Float = Float.NaN,
    val eau: Float = Float.NaN,
    val air: Float = Float.NaN,
    val batt: Float = Float.NaN,
    val papillon: Float = Float.NaN,
    val avance: Float = Float.NaN,
    val essence: Float = Float.NaN,
    val boost: Float = Float.NaN
)

// Liaison avec l'adaptateur ELM327 en Wi-Fi (même logique que le programme ESP32).
// Le téléphone doit être connecté au Wi-Fi "WiFi_OBDII" dans ses réglages.
class ObdClient(private val ctx: Context) {

    @Volatile var connecte = false
    @Volatile var statut = "Non connecté"

    private var socket: Socket? = null
    private var entree: InputStream? = null
    private var sortie: OutputStream? = null
    private var gestionnaire: ConnectivityManager? = null
    private var rappelReseau: ConnectivityManager.NetworkCallback? = null
    private var baro = 101.3f
    private var groupe = 0

    // Ouvre la liaison : demande le Wi-Fi sans internet, puis ouvre la connexion TCP dessus
    @Synchronized
    fun connecter(): Boolean {
        deconnecter()
        statut = "Recherche du Wi-Fi OBD..."
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        gestionnaire = cm
        val requete = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val attente = CountDownLatch(1)
        val trouve = AtomicReference<Network?>(null)
        val rappel = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trouve.set(network)
                attente.countDown()
            }
        }
        cm.requestNetwork(requete, rappel)
        rappelReseau = rappel
        attente.await(15, TimeUnit.SECONDS)

        val reseau = trouve.get()
        if (reseau == null) {
            statut = "Wi-Fi OBD introuvable"
            return false
        }
        statut = "Connexion à l'ELM327..."
        return try {
            val s = reseau.socketFactory.createSocket()
            s.connect(InetSocketAddress("192.168.0.10", 35000), 5000)
            s.soTimeout = 1000
            socket = s
            entree = s.getInputStream()
            sortie = s.getOutputStream()

            val init = send("ATZ", 2500)
            if (!init.contains("ELM")) {
                deconnecter()
                statut = "ELM327 muet"
                return false
            }
            send("ATE0")
            send("ATL0")
            send("ATS0")
            send("ATSP0")
            send("0100", 8000)   // déclenche la recherche du protocole
            connecte = true
            statut = "OBD connecté"
            true
        } catch (e: Exception) {
            deconnecter()
            statut = "ELM327 injoignable"
            false
        }
    }

    @Synchronized
    fun deconnecter() {
        connecte = false
        rappelReseau?.let { gestionnaire?.unregisterNetworkCallback(it) }
        rappelReseau = null
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        entree = null
        sortie = null
    }

    // Envoie une commande et lit la réponse jusqu'au caractère '>'
    private fun send(cmd: String, delaiMs: Long = 800): String {
        val out = sortie ?: return ""
        val inp = entree ?: return ""
        try {
            while (inp.available() > 0) inp.read()
            out.write((cmd + "\r").toByteArray())
            out.flush()
            val sb = StringBuilder()
            val fin = System.currentTimeMillis() + delaiMs
            while (System.currentTimeMillis() < fin) {
                while (inp.available() > 0) {
                    val c = inp.read().toChar()
                    if (c == '>') return sb.toString()
                    sb.append(c)
                }
                Thread.sleep(5)
            }
            return sb.toString()
        } catch (e: Exception) {
            deconnecter()
            statut = "Liaison perdue"
            return ""
        }
    }

    // Octets de données d'un PID : "41 0C 1A F8" -> [0x1A, 0xF8]
    private fun octets(pid: String, n: Int): IntArray? {
        val propre = send("01$pid").uppercase().filter { it in '0'..'9' || it in 'A'..'F' }
        val p = propre.indexOf("41$pid")
        if (p < 0 || propre.length < p + 4 + 2 * n) return null
        return IntArray(n) { propre.substring(p + 4 + 2 * it, p + 6 + 2 * it).toInt(16) }
    }

    // Une passe de lecture : régime à chaque passe, les autres capteurs à tour de rôle
    @Synchronized
    fun cycle(precedent: Mesures): Mesures? {
        if (!connecte) return null
        var m = precedent.copy(
            rpm = octets("0C", 2)?.let { (it[0] * 256 + it[1]) / 4f } ?: Float.NaN
        )
        groupe = (groupe + 1) % 4
        when (groupe) {
            0 -> {
                octets("33", 1)?.let { baro = it[0].toFloat() }
                val map = octets("0B", 1)?.get(0)?.toFloat()
                m = m.copy(
                    boost = if (map != null) (map - baro) / 100f else Float.NaN,
                    vitesse = octets("0D", 1)?.get(0)?.toFloat() ?: Float.NaN
                )
            }
            1 -> m = m.copy(
                eau = octets("05", 1)?.let { (it[0] - 40).toFloat() } ?: Float.NaN,
                air = octets("0F", 1)?.let { (it[0] - 40).toFloat() } ?: Float.NaN
            )
            2 -> m = m.copy(
                batt = octets("42", 2)?.let { (it[0] * 256 + it[1]) / 1000f } ?: Float.NaN,
                papillon = octets("11", 1)?.let { it[0] * 100f / 255f } ?: Float.NaN
            )
            else -> m = m.copy(
                avance = octets("0E", 1)?.let { it[0] / 2f - 64f } ?: Float.NaN,
                essence = octets("0A", 1)?.let { it[0] * 3f } ?: Float.NaN
            )
        }
        return m
    }

    // Lecture des codes défaut (mode 03)
    @Synchronized
    fun lireDtc(): List<String> {
        val rep = send("03", 3000).uppercase().filter { it in '0'..'9' || it in 'A'..'F' }
        val idx = rep.indexOf("43")
        if (idx < 0) return emptyList()
        val lettres = "PCBU"
        val codes = mutableListOf<String>()
        var p = idx + 2
        while (p + 4 <= rep.length && codes.size < 12) {
            val b1 = rep.substring(p, p + 2).toInt(16)
            val b2 = rep.substring(p + 2, p + 4).toInt(16)
            if (!(b1 == 0 && b2 == 0)) {
                codes.add(
                    "${lettres[(b1 shr 6) and 3]}${(b1 shr 4) and 3}" +
                        "%X%X%X".format(b1 and 0xF, b2 shr 4, b2 and 0xF)
                )
            }
            p += 4
        }
        return codes
    }

    // Effacement des codes défaut (mode 04) : ne doit être appelé qu'après confirmation
    @Synchronized
    fun effacerDtc(): Boolean {
        val rep = send("04", 3000).uppercase().filter { it in '0'..'9' || it in 'A'..'F' }
        return rep.contains("44")
    }
}
