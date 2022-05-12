package com.dgaspar.translator

import android.Manifest.permission.RECORD_AUDIO
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.apertium.Translator
import org.apertium.utils.IOUtils
import java.io.File
import java.util.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        onCreateOnRestart()
    }

    override fun onRestart() {
        super.onRestart()

        onCreateOnRestart()
    }

    /*******************************************************************************************/

    private fun onCreateOnRestart(){
        requestPermissions()

        if(checkPermissions() != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(
                    this,
                    "Permissão negada para acesso ao microfone.",
                    Toast.LENGTH_LONG
            ).show()
        }

        // set background color
        var mainLayout : LinearLayout = findViewById(R.id.mainLayout)
        mainLayout.setBackgroundColor(Color.parseColor("#deebf7"))

        // editTexts
        var inputEditText : EditText = findViewById(R.id.inputText)
        var outputEditText : EditText = findViewById(R.id.outputText)

        // disable inputEditText
        inputEditText.isEnabled = false
        inputEditText.isFocusable = false
        inputEditText.isFocusableInTouchMode = false

        /////////////////////////////////////////////////////////////////////////////////////

        /**
         * APERTIUM TRANSLATOR - KOTLIN
         * https://wiki.apertium.org/wiki/Apertium_Android
         */

        /** generate temp dirs */
        var packagesDir : File = File(filesDir, "packages") // where packages data are installed
        var bytecodeDir : File = File(filesDir, "bytecode") // where packages bytecode are installed. Must be private
        var bytecodeCacheDir : File = File(filesDir, "bytecodecache") // where bytecode cache is kept. Must be private
        IOUtils.cacheDir = File(cacheDir, "apertium-index-cache") // where cached transducer indexes are kept

        /** initialize apertium */
        var apertium : Apertium = Apertium(packagesDir, bytecodeDir, bytecodeCacheDir)
        apertium.rescanForPackages()
        println(".\nINSTALLED PACKAGES:\n" + apertium.titleToMode.keys + "\n" + apertium.titleToMode.values)

        /** INSTALL es-pt PACKAGE (TEST) */
        /** COMMENT ON FINAL VERSION */
        //var url : URL = URL("https://svn.code.sf.net/p/apertium/svn/builds/apertium-es-pt/apertium-es-pt.jar")
        //var pkgAux : String = "apertium-es-pt"
        //apertium.installPackage(pkgAux, url)

        /** add installed package titles to dropDown menu */
        var dropdownMenu : Spinner = findViewById(R.id.dropdown_menu)
        setContentOnDropDownView(
                dropdownMenu,
                apertium.titleToMode.keys.toTypedArray()
        )

        /** active editText if there is any package installed (dropdown_menu != null) */
        if (dropdownMenu != null && dropdownMenu.adapter.count > 0){ // not empty

            // dropDown selected variables
            var title : String = dropdownMenu.selectedItem.toString()
            var mode = apertium.titleToMode[title].toString()
            var pkg = apertium.modeToPackage[mode].toString()

            // dropDown menu
            dropdownMenu.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
                override fun onNothingSelected(parent: AdapterView<*>?) {

                }

                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    title = dropdownMenu.selectedItem.toString()
                    mode = apertium.titleToMode[title].toString()
                    pkg = apertium.modeToPackage[mode].toString()
                    println("dropdownMenu: $title $mode $pkg")
                }
            }

            // enable inputEditText
            inputEditText.isEnabled = true
            inputEditText.isFocusable = true
            inputEditText.isFocusableInTouchMode = true

            // input and output text
            var inputText : String = ""
            var outputText : String = ""

            // text watcher
            inputEditText.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    /** get input text */
                    inputText = inputEditText.text.toString()

                    /** translate */
                    if (inputText.isNotEmpty()) {
                        Translator.setBase(apertium.getBasedirForPackage(pkg), apertium.getClassLoaderForPackage(pkg))
                        Translator.setMode(mode)
                        outputText = Translator.translate(inputText)
                    } else {
                        outputText = ""
                    }

                    /** set outputText */
                    println(inputEditText.text)
                    outputEditText.setText(outputText)
                }
            })
        }
    }

    /*******************************************************************************************/

    /** microphone */

    fun openMicrophoneToSpeak(view: View){
        // Criando intent
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        // Iniciando intent
        if(intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, SPEECH_REQUEST_CODE)
        }
        else {
            Toast.makeText(this, "Erro ao obter fala", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        var inputEditText : EditText = findViewById(R.id.inputText)
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val spokenText: String? =
                    data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).let { results ->
                        results?.get(0)
                    }
            inputEditText.setText(spokenText)

        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    companion object {
        private const val SPEECH_REQUEST_CODE = 0
    }

    /*******************************************************************************************/

    /** configuration changed */

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        var layout = findViewById<LinearLayout>(R.id.inputOutputLayout)
        var separatorLine : View = findViewById(R.id.separatorLine)

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            layout.orientation = LinearLayout.HORIZONTAL;
            var separatorLineParams : LinearLayout.LayoutParams = LinearLayout.LayoutParams(
                    2,
                    LinearLayout.LayoutParams.MATCH_PARENT
            )
            separatorLineParams.setMargins(10, 0, 10, 0)
            separatorLine.layoutParams = separatorLineParams

            Log.e("MainActivity", "Alterando orientação para horizontal");

        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            layout.orientation = LinearLayout.VERTICAL;
            var separatorLineParams : LinearLayout.LayoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2
            )
            separatorLineParams.setMargins(0, 10, 0, 10)
            separatorLine.layoutParams = separatorLineParams

            Log.e("MainActivity", "Alterando orientação para vertical");

        }
    }

    /*******************************************************************************************/

    /** drop down */

    fun setContentOnDropDownView(
            spinner: Spinner,
            items: Array<String>
    ){
        var adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                items
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        spinner.adapter = adapter
    }

    /*******************************************************************************************/

    /** package manager link */

    // open package manager activity
    fun openPackageManagerActivity(view: View){
        var intent = Intent(this, PackageManagerActivity::class.java)
        startActivity(intent)
    }

    /*******************************************************************************************/

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val n = cm.activeNetwork
            if (n != null) {
                val nc = cm.getNetworkCapabilities(n)
                //It will check for both wifi and cellular network
                return nc!!.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            }
            return false
        } else {
            val netInfo = cm.activeNetworkInfo
            return netInfo != null && netInfo.isConnectedOrConnecting
        }
    }

    private fun checkPermissions(): Int {
        return ContextCompat.checkSelfPermission(applicationContext, RECORD_AUDIO)
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
                this@MainActivity, arrayOf(RECORD_AUDIO), 1)
    }
}


