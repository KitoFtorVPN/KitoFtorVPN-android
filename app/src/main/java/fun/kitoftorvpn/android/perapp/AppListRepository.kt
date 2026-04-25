package `fun`.kitoftorvpn.android.perapp

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Данные об установленном приложении для экрана выбора исключений.
 * icon загружается отдельно в UI (через rememberDrawablePainter или тяжёлый path).
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

object AppListRepository {

    /**
     * Возвращает список пользовательских (не системных) приложений с INTERNET.
     * Отсортирован по label. Без самого KitoFtorVPN — себя исключать бессмысленно.
     */
    suspend fun loadUserApps(context: Context): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val ownPkg = context.packageName

            // Получаем список пакетов, у которых есть launcher activity
            // (т.е. они видны в ящике приложений). Это самый надёжный способ
            // отсеять системные сервисы — Samsung помечает свои стоковые приложения
            // как "обновлённые системные", флаги тут не помогают.
            val launcherIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            }
            val launchable = pm.queryIntentActivities(launcherIntent, 0)
                .map { it.activityInfo.packageName }
                .toSet()

            val out = ArrayList<InstalledApp>(launchable.size)
            for (pkg in launchable) {
                if (pkg == ownPkg) continue
                val ai = try { pm.getApplicationInfo(pkg, 0) } catch (_: Exception) { continue }

                // Должно иметь INTERNET — иначе исключать бесполезно.
                val hasInternet = try {
                    pm.checkPermission(android.Manifest.permission.INTERNET, pkg) ==
                            PackageManager.PERMISSION_GRANTED
                } catch (_: Exception) { true }
                if (!hasInternet) continue

                val label = try { pm.getApplicationLabel(ai).toString() } catch (_: Exception) { ai.packageName }
                val icon = try { pm.getApplicationIcon(ai) } catch (_: Exception) { null }
                out.add(InstalledApp(ai.packageName, label, icon))
            }
            out.sortedBy { it.label.lowercase() }
        }
}
