package com.wyl.obd

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Pages de l'application (étape 2 : toutes les pages de la maquette)
enum class Page { VEILLE, MENU, DASH, TURBO, OBD, DTC, PERF, MOTEUR, LOG, REGLAGES, JEUX, SERPENT, PONG }

object Etat {
    @Volatile var page = Page.VEILLE
    @Volatile var demo = false
    @Volatile var mesures = Mesures()
    @Volatile var histo = FloatArray(20) { Float.NaN }   // pression turbo, 1 valeur par seconde
    @Volatile var histoIdx = 0
    @Volatile var dtc: List<String> = emptyList()
    @Volatile var msgDtc = "Touchez LIRE"
    @Volatile var effacerArme = false
    @Volatile var effacerMs = 0L
    @Volatile var vitMax = 0f
    @Volatile var boostMax = 0f
    @Volatile var rpmMax = 0f
    @Volatile var chrono = false
    @Volatile var chronoDebut = 0L
    @Volatile var chronoCumul = 0L

    // Réglages
    @Volatile var unitesMph = false
    @Volatile var seuilTurbo = 1.1f
    @Volatile var seuilRpm = 6000f
    @Volatile var modeSport = false
    @Volatile var alertesEcran = true

    // Journal des sorties (le plus récent en premier)
    val log = ArrayList<String>()

    fun ajouterLog(s: String) {
        val heure = SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date())
        synchronized(log) {
            log.add(0, "$heure  $s")
            while (log.size > 50) log.removeAt(log.size - 1)
        }
    }
}

// Jeux : tournent seulement quand la voiture est à l'arrêt
object Jeux {
    const val COLS = 29
    const val ROWS = 20
    private val rnd = java.util.Random()

    // Serpent
    var serpent = ArrayList<IntArray>()
    var dir = 1            // 0 haut, 1 droite, 2 bas, 3 gauche
    var nourr = IntArray(2)
    var scoreS = 0
    var perduS = true

    fun nouveauSerpent() {
        serpent = arrayListOf(intArrayOf(5, 10), intArrayOf(4, 10), intArrayOf(3, 10))
        dir = 1
        scoreS = 0
        perduS = false
        placerNourriture()
    }

    private fun placerNourriture() {
        while (true) {
            val x = rnd.nextInt(COLS)
            val y = rnd.nextInt(ROWS)
            if (serpent.none { it[0] == x && it[1] == y }) {
                nourr = intArrayOf(x, y)
                return
            }
        }
    }

    fun tournerSerpent(d: Int) {
        if ((d + 2) % 4 != dir) dir = d   // pas de demi-tour direct
    }

    fun avancerSerpent() {
        if (perduS || serpent.isEmpty()) return
        val h = serpent[0]
        val nx = h[0] + when (dir) { 1 -> 1; 3 -> -1; else -> 0 }
        val ny = h[1] + when (dir) { 2 -> 1; 0 -> -1; else -> 0 }
        if (nx < 0 || ny < 0 || nx >= COLS || ny >= ROWS || serpent.any { it[0] == nx && it[1] == ny }) {
            perduS = true
            Etat.ajouterLog("Serpent terminé : score $scoreS")
            return
        }
        serpent.add(0, intArrayOf(nx, ny))
        if (nx == nourr[0] && ny == nourr[1]) {
            scoreS++
            placerNourriture()
        } else {
            serpent.removeAt(serpent.size - 1)
        }
    }

    // Ping-pong : positions en fraction de la zone de jeu (0 à 1)
    var raquette = 0.5f
    var balleX = 0.5f
    var balleY = 0.2f
    var vx = 0.03f
    var vy = 0.05f
    var scoreP = 0
    var ratesP = 0
    var pongActif = false

    fun pongPas() {
        if (!pongActif) return
        balleX += vx
        balleY += vy
        if (balleX < 0f) { balleX = 0f; vx = -vx }
        if (balleX > 1f) { balleX = 1f; vx = -vx }
        if (balleY < 0f) { balleY = 0f; vy = -vy }
        if (balleY >= 0.94f && vy > 0f) {
            if (abs(balleX - raquette) <= 0.12f) {
                vy = -abs(vy)
                balleY = 0.94f
                scoreP++
            } else if (balleY >= 1f) {
                ratesP++
                balleX = 0.5f
                balleY = 0.2f
                vy = abs(vy)
            }
        }
    }

    fun pongRaz() {
        raquette = 0.5f; balleX = 0.5f; balleY = 0.2f
        vx = 0.03f; vy = 0.05f; scoreP = 0; ratesP = 0; pongActif = true
    }
}

val NOIR = Color.BLACK
val BLANC = Color.rgb(242, 244, 247)
val GRIS = Color.rgb(90, 99, 112)
val GRIS_FOND = Color.rgb(31, 36, 44)
val CARTE = Color.rgb(20, 24, 30)
val VERT = Color.rgb(61, 220, 132)
val JAUNE = Color.rgb(255, 197, 61)
val ROUGE = Color.rgb(230, 58, 46)
val BLEU = Color.rgb(59, 155, 255)
val CYAN = Color.rgb(64, 214, 230)
val ORANGE = Color.rgb(255, 140, 40)

class MainActivity : Activity() {
    private lateinit var obd: ObdClient
    private lateinit var ecran: Tableau

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        obd = ObdClient(this)
        ecran = Tableau(this, obd)
        setContentView(ecran)
        Thread { boucle() }.apply { isDaemon = true }.start()
    }

    override fun onDestroy() {
        obd.deconnecter()
        super.onDestroy()
    }

    // Lecture, statistiques, journal et jeux, environ 5 fois par seconde
    private fun boucle() {
        var derniereHisto = 0L
        var derniereSerpent = 0L
        var turboHaut = false
        var rpmHaut = false
        while (true) {
            val now = System.currentTimeMillis()
            if (Etat.demo) {
                Etat.mesures = simuler(now / 1000.0)
            } else if (obd.connecte) {
                obd.cycle(Etat.mesures)?.let { Etat.mesures = it }
            }
            val m = Etat.mesures
            if (!m.vitesse.isNaN()) Etat.vitMax = maxOf(Etat.vitMax, m.vitesse)
            if (!m.boost.isNaN()) Etat.boostMax = maxOf(Etat.boostMax, m.boost)
            if (!m.rpm.isNaN()) Etat.rpmMax = maxOf(Etat.rpmMax, m.rpm)

            // Journal : une ligne à chaque franchissement de seuil
            if (!m.boost.isNaN()) {
                val haut = m.boost > Etat.seuilTurbo
                if (haut && !turboHaut) Etat.ajouterLog("Pression turbo ${String.format(Locale.FRANCE, "%.2f", m.boost)} bar")
                turboHaut = haut
            }
            if (!m.rpm.isNaN()) {
                val haut = m.rpm > Etat.seuilRpm
                if (haut && !rpmHaut) Etat.ajouterLog("Régime ${m.rpm.toInt()} tr/min")
                rpmHaut = haut
            }

            if (now - derniereHisto >= 1000) {
                derniereHisto = now
                Etat.histo[Etat.histoIdx] = m.boost
                Etat.histoIdx = (Etat.histoIdx + 1) % Etat.histo.size
            }

            // Jeux : seulement à l'arrêt (moins de 2 km/h)
            val arret = !(m.vitesse > 2f)
            if (arret) {
                if (Etat.page == Page.SERPENT && now - derniereSerpent >= 350) {
                    derniereSerpent = now
                    Jeux.avancerSerpent()
                }
                if (Etat.page == Page.PONG) Jeux.pongPas()
            }

            ecran.postInvalidate()
            Thread.sleep(200)
        }
    }

    private fun simuler(t: Double): Mesures = Mesures(
        rpm = (2000 + 4500 * (0.5 + 0.5 * sin(t * 0.7))).toFloat(),
        vitesse = (20 + 110 * (0.5 + 0.5 * sin(t * 0.3))).toFloat(),
        eau = (85 + 12 * sin(t * 0.2)).toFloat(),
        air = (30 + 8 * sin(t * 0.5)).toFloat(),
        batt = (13.8 + 0.6 * sin(t * 0.4)).toFloat(),
        papillon = (50 + 50 * sin(t * 0.8)).toFloat().coerceAtLeast(0f),
        avance = (12 + 14 * sin(t * 0.6)).toFloat(),
        essence = (380 + 60 * sin(t * 0.5)).toFloat(),
        boost = (1.2 * sin(t * 0.9) + 0.4).toFloat()
    )
}

// Dessin et tactile. Mise en page prévue en 800 x 480, mise à l'échelle de l'écran.
class Tableau(ctx: Context, private val obd: ObdClient) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val LARG = 800f
    private val HAUT = 480f
    private val heure = SimpleDateFormat("HH:mm", Locale.FRANCE)

    override fun onDraw(c: Canvas) {
        c.drawColor(NOIR)
        c.save()
        c.scale(width / LARG, height / HAUT)
        val m = Etat.mesures
        when (Etat.page) {
            Page.VEILLE -> veille(c)
            Page.MENU -> menu(c)
            Page.DASH -> tableauDeBord(c, m)
            Page.TURBO -> turbo(c, m)
            Page.OBD -> connexion(c)
            Page.DTC -> pannes(c)
            Page.PERF -> performances(c)
            Page.MOTEUR -> moteur(c, m)
            Page.LOG -> journal(c)
            Page.REGLAGES -> reglages(c)
            Page.JEUX -> jeuxMenu(c, m)
            Page.SERPENT -> serpent(c, m)
            Page.PONG -> pong(c, m)
        }
        c.restore()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_UP) {
            toucher(e.x * LARG / width, e.y * HAUT / height)
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    // ---------- Tactile ----------
    private fun dans(x: Float, y: Float, rx: Float, ry: Float, w: Float, h: Float) =
        x >= rx && x < rx + w && y >= ry && y < ry + h

    private fun toucher(x: Float, y: Float) {
        if (Etat.page == Page.VEILLE) {
            Etat.page = Page.MENU
            return
        }
        if (Etat.page != Page.MENU && dans(x, y, 620f, 12f, 160f, 40f)) {
            Etat.page = Page.MENU
            return
        }
        when (Etat.page) {
            Page.MENU -> menuTouch(x, y)
            Page.OBD -> connexionTouch(x, y)
            Page.DTC -> pannesTouch(x, y)
            Page.PERF -> perfTouch(x, y)
            Page.REGLAGES -> reglagesTouch(x, y)
            Page.JEUX -> jeuxTouch(x, y)
            Page.SERPENT -> serpentTouch(x, y)
            Page.PONG -> pongTouch(x, y)
            else -> {}
        }
    }

    // Tuiles du menu : 4 colonnes x 2 lignes
    private val tuiles = listOf(
        Triple("Tableau de bord", "Régime, vitesse, relevés", Page.DASH),
        Triple("Pression turbo", "Cadran de -1 à +2 bar", Page.TURBO),
        Triple("Pannes", "Recherche de pannes", Page.DTC),
        Triple("Jeux", "Serpent et ping-pong, à l'arrêt", Page.JEUX),
        Triple("Moteur", "Avance, essence, admission", Page.MOTEUR),
        Triple("Performances", "Pics de vitesse, pression", Page.PERF),
        Triple("Log", "Historique des sorties", Page.LOG),
        Triple("Réglages", "Unités, seuils, affichage", Page.REGLAGES)
    )

    private fun menuTouch(x: Float, y: Float) {
        for (i in tuiles.indices) {
            val col = i % 4
            val row = i / 4
            if (dans(x, y, 20f + col * 196f, 60f + row * 200f, 176f, 180f)) {
                Etat.page = tuiles[i].third
                return
            }
        }
    }

    private fun connexionTouch(x: Float, y: Float) {
        if (dans(x, y, 40f, 330f, 300f, 90f)) {
            Etat.ajouterLog("Connexion OBD demandée")
            Thread { obd.connecter() }.start()
        } else if (dans(x, y, 460f, 330f, 300f, 90f)) {
            Etat.demo = !Etat.demo
        }
    }

    private fun pannesTouch(x: Float, y: Float) {
        if (dans(x, y, 40f, 390f, 220f, 70f)) {
            Thread {
                if (!obd.connecte) {
                    Etat.msgDtc = "OBD non connecté"
                    return@Thread
                }
                val liste = obd.lireDtc()
                Etat.dtc = liste
                Etat.msgDtc = if (liste.isEmpty()) "Aucune panne enregistrée"
                else "${liste.size} panne(s) trouvée(s)"
                Etat.ajouterLog("Lecture des pannes : ${liste.size}")
            }.start()
        } else if (dans(x, y, 290f, 390f, 220f, 70f)) {
            val maintenant = System.currentTimeMillis()
            if (!Etat.effacerArme || maintenant - Etat.effacerMs > 8000) {
                Etat.effacerArme = true
                Etat.effacerMs = maintenant
                Etat.msgDtc = "Touchez EFFACER encore pour confirmer"
            } else {
                Etat.effacerArme = false
                Thread {
                    if (!obd.connecte) {
                        Etat.msgDtc = "OBD non connecté"
                        return@Thread
                    }
                    if (obd.effacerDtc()) {
                        Etat.dtc = emptyList()
                        Etat.msgDtc = "Pannes effacées (à relire pour vérifier)"
                        Etat.ajouterLog("Pannes effacées")
                    } else {
                        Etat.msgDtc = "Effacement non confirmé par la voiture"
                    }
                }.start()
            }
        }
    }

    private fun perfTouch(x: Float, y: Float) {
        if (dans(x, y, 440f, 12f, 170f, 40f)) {
            Etat.vitMax = 0f
            Etat.boostMax = 0f
            Etat.rpmMax = 0f
            Etat.chronoCumul = 0L
            Etat.chrono = false
        } else if (dans(x, y, 620f, 70f, 160f, 390f)) {
            if (Etat.chrono) {
                Etat.chronoCumul += System.currentTimeMillis() - Etat.chronoDebut
                Etat.chrono = false
            } else {
                Etat.chronoDebut = System.currentTimeMillis()
                Etat.chrono = true
            }
        }
    }

    // Réglages : 6 lignes de 60 px, espacées de 66 px, à partir de y = 60
    private fun reglagesTouch(x: Float, y: Float) {
        if (x < 20f || x > 780f || y < 60f) return
        val i = ((y - 60f) / 66f).toInt()
        if (i !in 0..5 || (y - 60f) - i * 66f > 60f) return
        when (i) {
            0 -> Etat.unitesMph = !Etat.unitesMph
            1 -> Etat.seuilTurbo = when (Etat.seuilTurbo) { 0.8f -> 1.0f; 1.0f -> 1.1f; 1.1f -> 1.3f; else -> 0.8f }
            2 -> Etat.seuilRpm = when (Etat.seuilRpm) { 5000f -> 5500f; 5500f -> 6000f; 6000f -> 6500f; else -> 5000f }
            3 -> Etat.modeSport = !Etat.modeSport
            4 -> Etat.alertesEcran = !Etat.alertesEcran
            5 -> Etat.page = Page.OBD
        }
    }

    // Jeux : deux grands boutons. Interdits en roulant.
    private fun jeuxTouch(x: Float, y: Float) {
        if (!arret()) return
        if (dans(x, y, 40f, 120f, 340f, 140f)) {
            Jeux.nouveauSerpent()
            Etat.page = Page.SERPENT
        } else if (dans(x, y, 420f, 120f, 340f, 140f)) {
            Jeux.pongRaz()
            Etat.page = Page.PONG
        }
    }

    private fun serpentTouch(x: Float, y: Float) {
        if (dans(x, y, 440f, 12f, 170f, 40f)) {
            Jeux.nouveauSerpent()
        } else if (arret()) {
            if (dans(x, y, 665f, 230f, 70f, 60f)) Jeux.tournerSerpent(0)
            else if (dans(x, y, 665f, 370f, 70f, 60f)) Jeux.tournerSerpent(2)
            else if (dans(x, y, 620f, 300f, 70f, 60f)) Jeux.tournerSerpent(3)
            else if (dans(x, y, 710f, 300f, 70f, 60f)) Jeux.tournerSerpent(1)
        }
    }

    private fun pongTouch(x: Float, y: Float) {
        if (dans(x, y, 440f, 12f, 170f, 40f)) {
            Jeux.pongRaz()
        } else if (arret() && dans(x, y, 20f, 60f, 580f, 400f)) {
            Jeux.raquette = ((x - 20f) / 580f).coerceIn(0.1f, 0.9f)
        }
    }

    private fun arret(): Boolean = !(Etat.mesures.vitesse > 2f)

    // ---------- Outils de dessin ----------
    private fun texte(c: Canvas, s: String, x: Float, y: Float, taille: Float, couleur: Int, centre: Boolean = false, gras: Boolean = false) {
        p.reset()
        p.isAntiAlias = true
        p.color = couleur
        p.textSize = taille
        p.isFakeBoldText = gras
        p.textAlign = if (centre) Paint.Align.CENTER else Paint.Align.LEFT
        c.drawText(s, x, y, p)
    }

    private fun fmt(v: Float, dec: Int): String =
        if (v.isNaN()) "--" else String.format(Locale.FRANCE, "%.${dec}f", v)

    // Carte arrondie sombre, bordure en tirets (style de la maquette)
    private fun carte(c: Canvas, x: Float, y: Float, w: Float, h: Float, bord: Int, traitille: Boolean) {
        p.reset()
        p.isAntiAlias = true
        p.style = Paint.Style.FILL
        p.color = CARTE
        c.drawRoundRect(RectF(x, y, x + w, y + h), 14f, 14f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f
        p.color = bord
        if (traitille) p.pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
        c.drawRoundRect(RectF(x, y, x + w, y + h), 14f, 14f, p)
        p.pathEffect = null
    }

    private fun bouton(c: Canvas, x: Float, y: Float, w: Float, h: Float, label: String, couleur: Int, taille: Float = 30f) {
        carte(c, x, y, w, h, couleur, false)
        texte(c, label, x + w / 2, y + h / 2 + taille / 3, taille, couleur, true, true)
    }

    private fun entete(c: Canvas, titre: String, retour: Boolean = true) {
        texte(c, titre, 20f, 40f, 26f, ROUGE, false, true)
        if (retour) bouton(c, 620f, 12f, 160f, 40f, "‹ Menu", ROUGE, 20f)
    }

    // Arc de jauge : f de 0 à 1 sur 270 degrés, départ en bas à gauche
    private fun arc(c: Canvas, cx: Float, cy: Float, r: Float, f: Float, couleur: Int, epaisseur: Float) {
        val ovale = RectF(cx - r, cy - r, cx + r, cy + r)
        p.reset()
        p.isAntiAlias = true
        p.style = Paint.Style.STROKE
        p.strokeWidth = epaisseur
        p.color = couleur
        c.drawArc(ovale, 135f, 270f * f.coerceIn(0f, 1f), false, p)
    }

    private fun aiguille(c: Canvas, cx: Float, cy: Float, r: Float, f: Float, couleur: Int) {
        val a = Math.toRadians(135.0 + 270.0 * f.coerceIn(0f, 1f))
        val x = cx + (r * cos(a)).toFloat()
        val y = cy + (r * sin(a)).toFloat()
        p.reset()
        p.isAntiAlias = true
        p.strokeWidth = 4f
        p.color = couleur
        c.drawLine(cx, cy, x, y, p)
        p.style = Paint.Style.FILL
        c.drawCircle(cx, cy, 8f, p)
    }

    private fun vitesseAff(v: Float): Float = if (Etat.unitesMph) v * 0.621371f else v

    // ---------- Pages ----------
    private fun veille(c: Canvas) {
        texte(c, heure.format(Date()), 400f, 200f, 110f, BLANC, true, true)
        texte(c, "FIAT 500 ABARTH", 400f, 280f, 40f, ROUGE, true, true)
        texte(c, "Bonjour Wylliam", 400f, 325f, 28f, BLANC, true)
        texte(c, "Démarrage du système de surveillance et performance", 400f, 360f, 20f, GRIS, true)
        texte(c, "touchez l'écran pour revenir au menu", 400f, 450f, 20f, GRIS, true)
    }

    private fun menu(c: Canvas) {
        entete(c, "MENU", false)
        texte(c, if (obd.connecte) "Contact : ON" else "Contact : OFF", 780f, 40f, 20f, GRIS, false)
        tuiles.forEachIndexed { i, t ->
            val col = i % 4
            val row = i / 4
            val x = 20f + col * 196f
            val y = 60f + row * 200f
            carte(c, x, y, 176f, 180f, ROUGE, true)
            texte(c, t.first, x + 16f, y + 90f, 24f, BLANC, false, true)
            texte(c, t.second, x + 16f, y + 150f, 16f, GRIS, false)
        }
    }

    private fun tableauDeBord(c: Canvas, m: Mesures) {
        entete(c, "TABLEAU DE BORD")
        if (Etat.modeSport) {
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.color = ROUGE
            c.drawRoundRect(RectF(330f, 18f, 420f, 46f), 6f, 6f, p)
            texte(c, "SPORT", 375f, 40f, 18f, NOIR, true, true)
        }
        val rpm = m.rpm

        // 25 LED de régime
        for (i in 0 until 25) {
            val seuil = (i + 1) / 25f * 8000f
            val couleur = when {
                seuil >= 5500f -> ROUGE
                seuil >= 4500f -> JAUNE
                else -> VERT
            }
            val allumee = !rpm.isNaN() && rpm >= seuil - 160f
            p.reset()
            p.isAntiAlias = true
            p.style = Paint.Style.FILL
            p.color = if (allumee) couleur else GRIS_FOND
            c.drawCircle(40f + i * 30f, 80f, 9f, p)
        }

        // Compte-tours
        val cx = 200f
        val cy = 245f
        val r = 115f
        val f = if (rpm.isNaN()) 0f else rpm.coerceIn(0f, 8000f) / 8000f
        arc(c, cx, cy, r, 1f, GRIS_FOND, 18f)
        arc(c, cx, cy, r, f, BLEU, 18f)
        for (k in 0..8) {
            val a = Math.toRadians(135.0 + 270.0 * k / 8.0)
            texte(c, k.toString(), cx + ((r + 30f) * cos(a)).toFloat(), cy + ((r + 30f) * sin(a)).toFloat() + 8f, 20f, BLANC, true)
        }
        aiguille(c, cx, cy, r - 20f, f, BLANC)
        texte(c, "RPM x1000", cx, cy + 60f, 14f, GRIS, true)
        texte(c, if (rpm.isNaN()) "--" else fmt(rpm / 1000f, 1), cx, cy + 100f, 40f, BLANC, true, true)

        // Vitesse
        val unite = if (Etat.unitesMph) "mph" else "km/h"
        texte(c, if (m.vitesse.isNaN()) "--" else vitesseAff(m.vitesse).toInt().toString(), 560f, 250f, 120f, BLANC, true, true)
        texte(c, unite, 560f, 300f, 26f, GRIS, true)

        // Petites cartes en bas
        val cartes = listOf(
            Triple("EAU", if (m.eau.isNaN()) "--" else "${m.eau.toInt()} °C", BLANC),
            Triple("PAPILLON", if (m.papillon.isNaN()) "--" else "${m.papillon.toInt()} %", BLEU),
            Triple("TURBO", if (m.boost.isNaN()) "--" else "${fmt(m.boost, 2)} bar", if (!m.boost.isNaN() && m.boost > Etat.seuilTurbo) ROUGE else BLANC),
            Triple("BATTERIE", if (m.batt.isNaN()) "--" else "${fmt(m.batt, 1)} V", BLEU)
        )
        cartes.forEachIndexed { i, t ->
            val x = 20f + i * 196f
            carte(c, x, 360f, 176f, 100f, GRIS, false)
            texte(c, t.first, x + 88f, 390f, 16f, GRIS, true)
            texte(c, t.second, x + 88f, 440f, 30f, t.third, true, true)
        }
    }

    private fun turbo(c: Canvas, m: Mesures) {
        entete(c, "PRESSION TURBO")
        val b = m.boost
        val cx = 230f
        val cy = 230f
        val r = 130f
        val f = if (b.isNaN()) 0f else (b + 1f) / 3f
        arc(c, cx, cy, r, 1f, GRIS_FOND, 18f)
        arc(c, cx, cy, r, f, if (!b.isNaN() && b > Etat.seuilTurbo) ROUGE else BLEU, 18f)
        for (k in listOf(-1f, -0.5f, 0f, 0.5f, 1f, 1.5f, 2f)) {
            val a = Math.toRadians(135.0 + 270.0 * ((k + 1f) / 3f))
            texte(c, fmt(k, 1), cx + ((r + 32f) * cos(a)).toFloat(), cy + ((r + 32f) * sin(a)).toFloat() + 6f, 18f, BLANC, true)
        }
        texte(c, fmt(b, 2), cx, cy + 40f, 70f, BLANC, true, true)
        texte(c, "bar", cx, cy + 75f, 22f, GRIS, true)

        texte(c, "Papillon", 460f, 160f, 22f, GRIS)
        texte(c, if (m.papillon.isNaN()) "--" else "${m.papillon.toInt()} %", 460f, 215f, 56f, BLANC, false, true)

        // Historique des 20 dernières secondes
        texte(c, "PRESSION · 20 DERNIÈRES SECONDES", 40f, 330f, 16f, GRIS)
        val x0 = 40f
        val y0 = 440f
        val h = 90f
        val largeur = 720f / 20f
        for (i in 0 until 20) {
            val v = Etat.histo[(Etat.histoIdx + i) % 20]
            if (v.isNaN()) continue
            val hauteur = (v / 2.5f * h).coerceIn(-h / 2, h)
            p.reset()
            p.style = Paint.Style.FILL
            p.color = if (v > Etat.seuilTurbo) ROUGE else BLEU
            val haut = minOf(y0, y0 - hauteur)
            val bas = maxOf(y0, y0 - hauteur)
            c.drawRect(RectF(x0 + i * largeur + 2, haut, x0 + i * largeur + largeur - 3, bas), p)
        }
        p.reset()
        p.strokeWidth = 2f
        p.color = GRIS
        c.drawLine(x0, y0, x0 + 720f, y0, p)
        val ligne = y0 - Etat.seuilTurbo / 2.5f * h
        p.color = ROUGE
        p.pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
        c.drawLine(x0, ligne, x0 + 720f, ligne, p)
        p.pathEffect = null
        texte(c, "${fmt(Etat.seuilTurbo, 1)} bar", 760f, ligne - 6f, 16f, ROUGE, false)
    }

    private fun moteur(c: Canvas, m: Mesures) {
        entete(c, "MOTEUR")
        val cartes = listOf(
            Triple("AVANCE", if (m.avance.isNaN()) "--" else "${fmt(m.avance, 1)} °", BLANC),
            Triple("PRESSION ESSENCE", if (m.essence.isNaN()) "--" else "${m.essence.toInt()} kPa", BLANC),
            Triple("AIR ADMISSION", if (m.air.isNaN()) "--" else "${m.air.toInt()} °C", BLANC),
            Triple("EAU", if (m.eau.isNaN()) "--" else "${m.eau.toInt()} °C", BLANC),
            Triple("BATTERIE", if (m.batt.isNaN()) "--" else "${fmt(m.batt, 1)} V", BLEU),
            Triple("PAPILLON", if (m.papillon.isNaN()) "--" else "${m.papillon.toInt()} %", BLEU)
        )
        cartes.forEachIndexed { i, t ->
            val x = 20f + (i % 3) * 260f
            val y = 70f + (i / 3) * 190f
            carte(c, x, y, 240f, 170f, GRIS, false)
            texte(c, t.first, x + 120f, y + 50f, 16f, GRIS, true)
            texte(c, t.second, x + 120f, y + 120f, 48f, t.third, true, true)
        }
    }

    private fun journal(c: Canvas) {
        entete(c, "JOURNAL DES SORTIES")
        val lignes = synchronized(Etat.log) { Etat.log.toList() }
        if (lignes.isEmpty()) {
            texte(c, "Aucun événement pour l'instant", 400f, 240f, 26f, GRIS, true)
        }
        lignes.take(14).forEachIndexed { i, s ->
            texte(c, s, 30f, 85f + i * 27f, 19f, BLANC)
        }
    }

    private fun reglages(c: Canvas) {
        entete(c, "RÉGLAGES")
        val lignes = listOf(
            Pair("Unité de vitesse", if (Etat.unitesMph) "mph" else "km/h"),
            Pair("Alerte turbo au-dessus de", "${fmt(Etat.seuilTurbo, 1)} bar"),
            Pair("Alerte régime au-dessus de", "${Etat.seuilRpm.toInt()} tr/min"),
            Pair("Mode Sport", if (Etat.modeSport) "ACTIVÉ" else "désactivé"),
            Pair("Alertes à l'écran", if (Etat.alertesEcran) "ACTIVÉES" else "désactivées"),
            Pair("Connexion OBD", if (obd.connecte) "connecté" else "ouvrir")
        )
        lignes.forEachIndexed { i, (nom, valeur) ->
            val y = 60f + i * 66f
            carte(c, 20f, y, 760f, 60f, GRIS, false)
            texte(c, nom, 40f, y + 38f, 22f, BLANC)
            texte(c, valeur, 760f, y + 38f, 22f, ROUGE, false, true)
        }
    }

    private fun jeuxMenu(c: Canvas, m: Mesures) {
        entete(c, "JEUX")
        if (!arret()) {
            texte(c, "Jeux disponibles à l'arrêt uniquement", 400f, 100f, 24f, JAUNE, true, true)
        } else {
            texte(c, "Touchez un jeu", 400f, 100f, 22f, GRIS, true)
        }
        bouton(c, 40f, 120f, 340f, 140f, "SERPENT", VERT, 30f)
        bouton(c, 420f, 120f, 340f, 140f, "PING-PONG", BLEU, 30f)
    }

    private fun serpent(c: Canvas, m: Mesures) {
        entete(c, "SERPENT · score ${Jeux.scoreS}")
        bouton(c, 440f, 12f, 170f, 40f, "Rejouer", VERT, 18f)
        // Plateau
        carte(c, 20f, 60f, 580f, 400f, GRIS, false)
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL
        p.color = ROUGE
        c.drawCircle(20f + Jeux.nourr[0] * 20f + 10f, 60f + Jeux.nourr[1] * 20f + 10f, 7f, p)
        p.color = VERT
        Jeux.serpent.forEach { cell ->
            c.drawRect(RectF(20f + cell[0] * 20f + 2f, 60f + cell[1] * 20f + 2f, 20f + cell[0] * 20f + 18f, 60f + cell[1] * 20f + 18f), p)
        }
        if (Jeux.perduS) {
            texte(c, "PERDU · touchez Rejouer", 310f, 265f, 26f, ROUGE, true, true)
        }
        if (!arret()) {
            texte(c, "En roulant : jeu en pause", 310f, 300f, 20f, JAUNE, true)
        }
        // Commandes
        bouton(c, 665f, 230f, 70f, 60f, "▲", BLANC, 26f)
        bouton(c, 620f, 300f, 70f, 60f, "◀", BLANC, 26f)
        bouton(c, 710f, 300f, 70f, 60f, "▶", BLANC, 26f)
        bouton(c, 665f, 370f, 70f, 60f, "▼", BLANC, 26f)
    }

    private fun pong(c: Canvas, m: Mesures) {
        entete(c, "PING-PONG · ${Jeux.scoreP} / ${Jeux.ratesP}")
        bouton(c, 440f, 12f, 170f, 40f, "Rejouer", VERT, 18f)
        carte(c, 20f, 60f, 580f, 400f, GRIS, false)
        // Balle
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL
        p.color = JAUNE
        c.drawCircle(20f + Jeux.balleX * 580f, 60f + Jeux.balleY * 400f, 9f, p)
        // Raquette
        p.color = BLEU
        val rx = 20f + (Jeux.raquette - 0.12f) * 580f
        val rw = 0.24f * 580f
        c.drawRoundRect(RectF(rx, 440f, rx + rw, 452f), 6f, 6f, p)
        if (!arret()) texte(c, "En roulant : jeu en pause", 310f, 260f, 20f, JAUNE, true)
        if (!Jeux.pongActif) texte(c, "Touchez Rejouer", 310f, 260f, 22f, GRIS, true)
        texte(c, "Touchez la zone pour placer la raquette", 310f, 470f, 16f, GRIS, true)
    }

    private fun connexion(c: Canvas) {
        entete(c, "CONNEXION OBD")
        val couleur = when {
            Etat.demo -> JAUNE
            obd.connecte -> VERT
            else -> ROUGE
        }
        texte(c, if (Etat.demo) "Démonstration : valeurs simulées" else obd.statut, 40f, 130f, 30f, couleur, false, true)
        texte(c, "Prise OBD2 · broches 6 et 14 · CAN 500 kbit/s", 40f, 180f, 20f, GRIS)
        texte(c, "Connectez le téléphone au Wi-Fi WiFi_OBDII avant de cliquer", 40f, 215f, 20f, GRIS)
        bouton(c, 40f, 330f, 300f, 90f, "CONNECTER", VERT, 26f)
        bouton(c, 460f, 330f, 300f, 90f, if (Etat.demo) "ARRÊTER DÉMO" else "DÉMONSTRATION", JAUNE, 22f)
    }

    private fun pannes(c: Canvas) {
        entete(c, "RECHERCHE DE PANNES")
        texte(c, Etat.msgDtc, 40f, 110f, 26f, BLANC)
        Etat.dtc.forEachIndexed { i, code ->
            texte(c, code, 60f + (i % 2) * 360f, 170f + (i / 2) * 45f, 34f, ROUGE, false, true)
        }
        bouton(c, 40f, 390f, 220f, 70f, "LIRE", VERT, 26f)
        bouton(c, 290f, 390f, 220f, 70f, if (Etat.effacerArme) "CONFIRMER ?" else "EFFACER", if (Etat.effacerArme) ROUGE else ORANGE, 22f)
    }

    private fun performances(c: Canvas) {
        entete(c, "PERFORMANCES")
        bouton(c, 440f, 12f, 170f, 40f, "Réinitialiser", ROUGE, 18f)
        val cartes = listOf(
            Pair("VITESSE MAX · " + (if (Etat.unitesMph) "mph" else "km/h"), if (Etat.vitMax == 0f) "--" else vitesseAff(Etat.vitMax).toInt().toString()),
            Pair("PRESSION TURBO MAX · bar", if (Etat.boostMax == 0f) "--" else fmt(Etat.boostMax, 2)),
            Pair("RÉGIME MAX · tr/min", if (Etat.rpmMax == 0f) "--" else Etat.rpmMax.toInt().toString()),
            Pair("TEMPS DE SESSION", chronoTexte())
        )
        cartes.forEachIndexed { i, t ->
            val x = 20f + (i % 2) * 290f
            val y = 70f + (i / 2) * 200f
            carte(c, x, y, 270f, 180f, ROUGE, true)
            texte(c, t.first, x + 135f, y + 40f, 16f, GRIS, true)
            texte(c, t.second, x + 135f, y + 120f, 56f, BLANC, true, true)
        }
        // Panneau de départ
        carte(c, 620f, 70f, 160f, 390f, GRIS, false)
        texte(c, "DÉPART", 700f, 110f, 18f, GRIS, true)
        p.reset()
        p.isAntiAlias = true
        p.style = Paint.Style.FILL
        p.color = if (Etat.chrono) VERT else ROUGE
        c.drawCircle(700f, 190f, 40f, p)
        p.color = GRIS
        c.drawCircle(700f, 320f, 40f, p)
        texte(c, if (Etat.chrono) "EN COURS" else "ARRÊT", 700f, 410f, 20f, BLANC, true, true)
    }

    private fun chronoTexte(): String {
        val ms = Etat.chronoCumul + if (Etat.chrono) System.currentTimeMillis() - Etat.chronoDebut else 0L
        val s = ms / 1000
        return String.format(Locale.FRANCE, "%02d:%02d", s / 60, s % 60)
    }
}
