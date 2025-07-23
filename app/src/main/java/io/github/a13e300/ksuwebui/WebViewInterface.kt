package io.github.a13e300.ksuwebui

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Window
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class WebViewInterface(
    val context: Context,
    private val webView: WebView,
    private val modDir: String,
    private val scope: CoroutineScope
) {

    private fun buildCommand(cmd: String, options: String?): String {
        val opts = options?.let { JSONObject(it) } ?: JSONObject()
        val cwd = opts.optString("cwd")
        val env = opts.optJSONObject("env")
        val command = StringBuilder()
        if (cwd.isNotEmpty()) {
            command.append("cd ").append(cwd).append("; ")
        }
        env?.keys()?.forEach { key ->
            command.append("export ").append(key).append("=").append(env.getString(key)).append("; ")
        }
        command.append(cmd)
        return command.toString()
    }

    @JavascriptInterface
    fun exec(cmd: String, options: String?, callbackFunc: String?) {
        scope.launch(Dispatchers.IO) {
            val finalCommand = buildCommand(cmd, options)
            val result = withNewRootShell(true) {
                newJob().add(finalCommand).to(ArrayList(), ArrayList()).exec()
            }
            val stdout = result.out.joinToString(separator = "\n")
            val stderr = result.err.joinToString(separator = "\n")
            if (callbackFunc != null) {
                val jsCode =
                    "(function() { try { ${callbackFunc}(${result.code}, ${JSONObject.quote(stdout)}, ${JSONObject.quote(stderr)}); } catch(e) { console.error(e); } })();"
                webView.post {
                    webView.evaluateJavascript(jsCode, null)
                }
            }
        }
    }

    @JavascriptInterface
    fun exec(cmd: String, callbackFunc: String) {
        exec(cmd, null, callbackFunc)
    }

    @JavascriptInterface
    fun exec(cmd: String): String {
        return withNewRootShell(true) { ShellUtils.fastCmd(this, buildCommand(cmd, null)) }
    }


    @JavascriptInterface
    fun spawn(command: String, args: String, options: String?, callbackFunc: String) {
        scope.launch(Dispatchers.IO) {
            val finalCommand = buildCommand(command, options)
            val shell = createRootShell(true)
            val emitData = fun(name: String, data: String) {
                val jsCode =
                    "(function() { try { ${callbackFunc}.${name}.emit('data', ${JSONObject.quote(data)}); } catch(e) { console.error('emitData', e); } })();"
                webView.post {
                    webView.evaluateJavascript(jsCode, null)
                }
            }

            val stdout = object : CallbackList<String>() {
                override fun onAddElement(s: String) {
                    emitData("stdout", s)
                }
            }

            val stderr = object : CallbackList<String>() {
                override fun onAddElement(s: String) {
                    emitData("stderr", s)
                }
            }
            val job = shell.newJob().add(finalCommand).to(stdout, stderr)
            val result = job.exec()

            val emitExitCode =
                "(function() { try { ${callbackFunc}.emit('exit', ${result.code}); } catch(e) { console.error(`emitExit error: \${e}`); } })();"
            webView.post {
                webView.evaluateJavascript(emitExitCode, null)
            }

            if (result.code != 0) {
                val emitErrCode =
                    "(function() { try { var err = new Error(); err.exitCode = ${result.code}; err.message = ${JSONObject.quote(result.err.joinToString("\n"))};${callbackFunc}.emit('error', err); } catch(e) { console.error('emitErr', e); } })();"
                webView.post {
                    webView.evaluateJavascript(emitErrCode, null)
                }
            }
            shell.close()
        }
    }

    @JavascriptInterface
    fun toast(msg: String) {
        webView.post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun fullScreen(enable: Boolean) {
        if (context is Activity) {
            Handler(Looper.getMainLooper()).post {
                if (enable) {
                    hideSystemUI(context.window)
                } else {
                    showSystemUI(context.window)
                }
            }
        }
    }

    @JavascriptInterface
    fun moduleInfo(): String {
        val currentModuleInfo = JSONObject()
        currentModuleInfo.put("moduleDir", modDir)
        val moduleId = File(modDir).name
        currentModuleInfo.put("id", moduleId)
        // TODO: more
        return currentModuleInfo.toString()
    }
}

fun hideSystemUI(window: Window) =
    WindowInsetsControllerCompat(window, window.decorView).let { controller ->
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

fun showSystemUI(window: Window) =
    WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())