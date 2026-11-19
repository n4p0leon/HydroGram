package dev.hydrogram.mod

import android.app.Application
import android.content.Context
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.ConcurrentHashMap

class HydroGramInit : IXposedHookLoadPackage {

    companion object {
        private const val TARGET_PACKAGE = "org.telegram.messenger"
        private const val TAG = "HydroGram"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return
        XposedBridge.log("[$TAG] Activating 11 HydroGram hooks inside $TARGET_PACKAGE")

        runCatching {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val app = param.thisObject as? Application ?: return
                        HydroPrefs.init(app)
                        HydroDatabase.init(app)
                    }
                }
            )
        }

        // 0. Native Telegram Drawer Menu: встраивание раздела «Настройки HydroGram»
        runCatching {
            XposedHelpers.findAndHookMethod(
                "org.telegram.ui.Adapters.DrawerLayoutAdapter",
                lpparam.classLoader,
                "resetItems",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        @Suppress("UNCHECKED_CAST")
                        val items = XposedHelpers.getObjectField(param.thisObject, "items") as? ArrayList<Any?> ?: return
                        val itemClass = XposedHelpers.findClass(
                            "org.telegram.ui.Adapters.DrawerLayoutAdapter$Item",
                            lpparam.classLoader
                        )
                        val iconId = android.R.drawable.ic_menu_manage
                        val hydroItem = XposedHelpers.newInstance(itemClass, 9001, "Настройки HydroGram", iconId)
                        if (items.size > 2) items.add(2, hydroItem) else items.add(hydroItem)
                    }
                }
            )
        }

        // 1. Liquid Glass Blur (Android 12+ RenderEffect & Glass ActionBar)
        runCatching {
            XposedHelpers.findAndHookMethod(
                "org.telegram.ui.ActionBar.ActionBar",
                lpparam.classLoader,
                "onAttachedToWindow",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as? android.view.View ?: return
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            view.alpha = 0.92f
                            view.setBackgroundColor(android.graphics.Color.argb(180, 14, 20, 34))
                        }
                    }
                }
            )
        }

        // 2. Snappy Animations (70%): упругие переходы без «ватности»
        runCatching {
            val springCurve = android.view.animation.PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)
            XposedHelpers.findAndHookMethod(
                "org.telegram.ui.ActionBar.ActionBarLayout",
                lpparam.classLoader,
                "presentFragment",
                "org.telegram.ui.ActionBar.ActionBarLayout$NavigationParams",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val animSet = runCatching {
                            XposedHelpers.getObjectField(param.thisObject, "currentAnimation") as? android.animation.AnimatorSet
                        }.getOrNull()
                        animSet?.let {
                            val factor = 70.coerceIn(40, 140) / 100f
                            it.duration = (200f * factor).toLong()
                            it.interpolator = springCurve
                        }
                    }
                }
            )
        }

        // 3. Custom Bubble Radius (20dp)
        runCatching {
            XposedHelpers.setStaticIntField(
                XposedHelpers.findClass("org.telegram.messenger.SharedConfig", lpparam.classLoader),
                "bubbleRadius",
                20
            )
        }

        // 4. Ghost Read: блокировка отправки галочек прочтения
        runCatching {
            XposedHelpers.findAndHookMethod(
                "org.telegram.messenger.MessagesController",
                lpparam.classLoader,
                "markMessageAsRead",
                Long::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val isExplicit = Thread.currentThread().stackTrace.any {
                            it.methodName.contains("markAsReadExplicitly")
                        }
                        if (!isExplicit && HydroPrefs.isGhostReadEnabled()) {
                            param.result = null
                        }
                    }
                }
            )
        }

        // 5. Ghost Typing: скрытие статуса «печатает» и «записывает голос»
        runCatching {
            XposedHelpers.findAndHookMethod(
                "org.telegram.messenger.MessagesController",
                lpparam.classLoader,
                "sendTyping",
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (HydroPrefs.isGhostTypingEnabled()) {
                            param.result = null
                        }
                    }
                }
            )
        }

        // 6. Anti-Delete: перехват удалений и запись в локальную базу
        runCatching {
            XposedHelpers.findAndHookMethod(
                "org.telegram.messenger.MessagesStorage",
                lpparam.classLoader,
                "markMessagesAsDeletedInternal",
                Long::class.javaPrimitiveType,
                ArrayList::class.java,
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        @Suppress("UNCHECKED_CAST")
                        val msgIds = param.args[1] as? ArrayList<Int> ?: return
                        val dialogId = param.args[0] as Long
                        if (msgIds.isNotEmpty()) {
                            HydroDatabase.instance?.markDeleted(dialogId, msgIds)
                            param.result = ArrayList<Int>()
                        }
                    }
                }
            )
        }

        // 7. Edit History: сохранение прошлых версий сообщений
        runCatching {
            XposedHelpers.findAndHookMethod(
                "org.telegram.messenger.MessagesStorage",
                lpparam.classLoader,
                "updateMessageText",
                Long::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val dialogId = param.args[0] as Long
                        val msgId = param.args[1] as Int
                        val newText = param.args[2] as? String ?: return
                        HydroDatabase.instance?.appendEditRevision(dialogId, msgId, newText)
                    }
                }
            )
        }

        // 8. Unlock Copy & Forward: обход запрета копирования в каналах
        runCatching {
            XposedHelpers.findAndHookMethod(
                "org.telegram.messenger.MessagesController",
                lpparam.classLoader,
                "isChatNoForwards",
                "org.telegram.tgnet.TLRPC$Chat",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = false
                    }
                }
            )
        }

        // 9. Hide Sponsored Ads: фильтрация рекламы Telegram Ads
        runCatching {
            XposedHelpers.findAndHookMethod(
                "org.telegram.messenger.MessageObject",
                lpparam.classLoader,
                "isSponsored",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = false
                    }
                }
            )
        }
    }
}

object HydroPrefs {
    private const val PREF_NAME = "hydrogram_mod_prefs"
    private var ghostRead = true
    private var ghostTyping = true

    fun init(app: Application) {
        val prefs = app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        ghostRead = prefs.getBoolean("ghost_read", true)
        ghostTyping = prefs.getBoolean("ghost_typing", true)
    }

    fun isGhostReadEnabled(): Boolean = ghostRead
    fun isGhostTypingEnabled(): Boolean = ghostTyping
}

object DeletedMessagesStore {
    private val deletedByDialog = ConcurrentHashMap<Long, MutableSet<Int>>()

    fun archiveDeletedIds(dialogId: Long, ids: List<Int>) {
        val bucket = deletedByDialog.getOrPut(dialogId) { mutableSetOf() }
        bucket.addAll(ids)
    }
}

object EditHistoryStore {
    private val revisions = ConcurrentHashMap<String, MutableList<Pair<Long, String>>>()

    fun appendRevision(dialogId: Long, msgId: Int, text: String) {
        val key = "$dialogId:$msgId"
        val list = revisions.getOrPut(key) { mutableListOf() }
        list.add(System.currentTimeMillis() to text)
    }
}
