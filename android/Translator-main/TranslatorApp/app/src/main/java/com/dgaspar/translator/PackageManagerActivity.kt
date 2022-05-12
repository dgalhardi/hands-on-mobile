package com.dgaspar.translator

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.LinearLayout.LayoutParams
import androidx.appcompat.app.AppCompatActivity
import org.apertium.utils.IOUtils
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URL
import java.util.*


class PackageManagerActivity : AppCompatActivity() {

    /** language pairs titles => "Spanish → Portuguese (BR)" */
    public var lpTitles : ArrayList<String> = arrayListOf<String>()

    /** <key, value> = <"Spanish → Portuguese (BR)", "es-pt_BR"> */
    public var titleToPackage = HashMap<String, String>()

    /** <key, value> = <"es-pt_BR", "apertium-es-pt_BR"> */
    public var titleToURL = HashMap<String, String>()

    /*******************************************************************************************/

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_package_manager)

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

        /////////////////////////////////////////////////////////////////////////////////////

        /**
         * READ LANGUAGE PAIRS
        */

        /** open file*/
        var inputStream : InputStream = resources.openRawResource(R.raw.language_pairs)
        var fileReader : BufferedReader = BufferedReader(InputStreamReader(inputStream))
        var line : String
        var i : Int = 0

        /** cycle through files */
        try {
            // line 1
            line = fileReader.readLine()

            // next lines
            while (line != null){
                println(line)

                // parse line
                var token : List<String> = line.split("\t")

                // title
                var title = LanguageTitles.getTitle(token[3])
                lpTitles.add(title)

                // package
                titleToPackage[title] = token[0]

                // url
                titleToURL[title] = token[1]

                line = fileReader.readLine()
                i++
            }

        } catch (e: Exception){
            println("Reading TSV Error!")
            e.printStackTrace()
        } finally {
            try {
                fileReader?.close()
            } catch (e: Exception) {
                println("closing TSV Error!")
                e.printStackTrace()
            }
        }

        /////////////////////////////////////////////////////////////////////////////////////

        /** LAYOUT - CREATE ITEMS */

        var mainLayout : LinearLayout = findViewById(R.id.mainLayout)
        var installedPackages = apertium.getInstalledPackages()

        for (i in 0 until lpTitles.size){
            var title = lpTitles[i]
            var pkg = titleToPackage[title]

            createPackageItem(
                    apertium,
                    mainLayout,
                    i,
                    title.replace(", ", "\n"),
                    (pkg in installedPackages)
            )

            // create horizontal line
            createHorizontalLine(
                    mainLayout,
                    color = "#000000"
            )
        }
    }

    /*******************************************************************************************/

    private fun createPackageItem(
            apertium: Apertium,
            layout: LinearLayout,
            id: Int,
            title: String,
            installed: Boolean = false
    ){
        // item layout params
        var layoutParams : LayoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        )
        layoutParams.setMargins(0, 50, 0, 50)

        // item layout
        var itemLayout : RelativeLayout = RelativeLayout(this)
        itemLayout.layoutParams = layoutParams
        itemLayout.gravity = Gravity.CENTER_VERTICAL

        // textView
        var nameTextView : TextView =  createTextView(
                itemLayout,
                title,
                15f
        )

        // button
        var buttonText : String = if (installed) "Remover" else "Instalar"
        var buttonColor : String = if (installed) "#c0c0c0" else "#9ecae1"
        var button : Button = createButton(
                itemLayout,
                id,
                buttonText,
                15f,
                buttonColor
        )
        button.setOnClickListener {
            buttonListener(apertium, button)
        }

        layout.addView(itemLayout)
    }

    /*******************************************************************************************/

    private fun createTextView(
            layout: RelativeLayout,
            text: String,
            textSize: Float = 20f,
            textColor: String = "#000000"
    ) : TextView {
        // layout params
        var params : RelativeLayout.LayoutParams = RelativeLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
        )
        params.addRule(RelativeLayout.ALIGN_PARENT_LEFT)

        // create text
        var textView : TextView = TextView(this)
        textView.layoutParams = params
        textView.textSize = textSize
        textView.text = text
        textView.setTextColor(Color.parseColor(textColor))
        layout.addView(textView)

        return textView
    }

    /*******************************************************************************************/

    private fun createButton(
            layout: RelativeLayout,
            id: Int,
            text: String,
            textSize: Float = 20f,
            color: String = "#c0c0c0"
    ) : Button {
        // layout params
        var params : RelativeLayout.LayoutParams = RelativeLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
        )
        params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)

        // create button
        var button : Button = Button(this)
        button.layoutParams = params
        button.textSize = textSize
        button.text = text
        button.setBackgroundColor(Color.parseColor(color))
        button.setPadding(10, 10, 10, 10)
        button.id = id
        layout.addView(button)

        return button
    }

    private fun buttonListener(apertium: Apertium, button: Button){
        var installedPackages = apertium.getInstalledPackages()

        var title = lpTitles[button.id]
        var pkg = titleToPackage[title].toString()

        if (pkg in installedPackages){

            /** REMOVE PACKAGE */
            apertium.uninstallPackage(pkg)

            button.text = "Instalar"
            button.setBackgroundColor(Color.parseColor("#9ecae1"))

            Toast.makeText(
                    this,
                    "$title removido!",
                    Toast.LENGTH_LONG
            ).show()

        } else {

            //enableProgressBar()

            /** INSTALL PACKAGE */
            var url = URL(titleToURL[title].toString())
            apertium.installPackage(this, pkg, url, button)

            button.text = "Instalando"
            button.setBackgroundColor(Color.parseColor("#c0c0c0"))

            Toast.makeText(
                    this,
                    "Instalando!",
                    Toast.LENGTH_LONG
            ).show()

        }
    }

    /*******************************************************************************************/

    private fun createHorizontalLine(
            layout: LinearLayout,
            height: Int = 2,
            color: String = "#c0c0c0"
    ){
        var horLine : View = View(this)
        horLine.layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                height
        )
        horLine.setBackgroundColor(Color.parseColor(color))

        // add horizontal line
        layout.addView(horLine)
    }

    /*******************************************************************************************/

    fun enableProgressBar(){
        Log.e("PackageManagerActivity", "Ativando barra de progresso")
        var llProgressBar : LinearLayout = findViewById(R.id.llProgressBar)
        llProgressBar.visibility = View.VISIBLE
    }

    /**TODO Achar local pra desabilitar**/
    fun disableProgressBar(){
        Log.e("PackageManagerActivity", "Desativando barra de progresso")
        var llProgressBar : LinearLayout = findViewById(R.id.llProgressBar)
        llProgressBar.visibility = View.GONE
    }
}