package com.dgaspar.translator

import android.content.Context
import android.util.Log
import android.widget.Button
import android.widget.Toast
import dalvik.system.DexClassLoader
import kotlinx.coroutines.*
import org.apertium.Translator
import org.apertium.utils.IOUtils.cacheDir
import java.io.*
import java.net.URL
import java.net.URLConnection
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.net.ssl.HttpsURLConnection
import kotlin.collections.ArrayList

/**
 * @author Daniel Gaspar Goncalves - 17-02-2021
*/
class Apertium (packagesDir : File, bytecodeDir : File, bytecodeCacheDir : File) {

    /** This is where the packages are installed */
    private var packagesDir : File = packagesDir

    /** This is where bytecode will be put */
    private var bytecodeDir : File = bytecodeDir

    /** This is where optimized bytecode will be put */
    private var bytecodeCacheDir : File = bytecodeCacheDir

    /** <key, value> = <"Spanish → Portuguese (BR)", "es-pt_BR"> */
    public var titleToMode = HashMap<String, String>()

    /** <key, value> = <"es-pt_BR", "apertium-es-pt_BR"> */
    public var modeToPackage = HashMap<String, String>()

    init {
        packagesDir.mkdirs()
        bytecodeDir.mkdirs()
        bytecodeCacheDir.mkdirs()
    }

    /*******************************************************************************************/

    fun rescanForPackages(){
        titleToMode.clear()
        modeToPackage.clear()

        /** list packages */
        var installedPackages : ArrayList<String> = getInstalledPackages()
        //var installedPackages : ArrayList<String> = packagesDir.list().toCollection(ArrayList())

        /** cycle through installed packages list */
        for (pkg in installedPackages){

            /** filename filter */
            if(pkg.matches("apertium-[a-z][a-z][a-z]?-[a-z][a-z][a-z]?".toRegex())) {

                /** check if is a valid package */
                var baseDir : String = "$packagesDir/$pkg"
                try {
                    Translator.setBase(baseDir, getClassLoaderForPackage(pkg))

                    for (mode in Translator.getAvailableModes()){
                        var title : String = LanguageTitles.getTitle(mode)
                        titleToMode[title] = mode
                        modeToPackage[mode] = pkg
                    }
                } catch (e : Throwable){
                    e.printStackTrace()
                    Log.e("", baseDir, e)
                }
            }
        }
    }

    /*******************************************************************************************/

    fun getInstalledPackages() : ArrayList<String>{
        return packagesDir.list().toCollection(ArrayList())
    }

    /*******************************************************************************************/

    /**
     * INSTALL PACKAGE
    */

    fun installPackage(context: Context, pkg : String, url : URL, button : Button){

        /** install */
       CoroutineScope(Dispatchers.IO).launch {
            installPackageAsync(pkg, url)

            /** throw a toast when the installation is completed */
            withContext(Dispatchers.Main) {
                button.text = "Remover"

                Toast.makeText(
                        context,
                        "Pacote instalado!",
                        Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun installPackageAsync(
            pkg : String,
            url : URL
    ){
        var connection : URLConnection = url.openConnection() as HttpsURLConnection

        //var lastModified = connection.lastModified
        //var contentLength = connection.contentLength

        var tmpjarfile : File = File(cacheDir, "$pkg.jar")

        // create input and output stream
        var inStream = BufferedInputStream(connection.getInputStream())

        tmpjarfile.parentFile.mkdirs()
        tmpjarfile.createNewFile()

        var fos : FileOutputStream = FileOutputStream(tmpjarfile)

        // download data
        var data = ByteArray(8192)
        var count: Int = 0
        var total : Int = 0
        while (inStream.read(data, 0, 1024).also({ count = it }) != -1){
            fos.write(data, 0, count)
            total += count
        }

        // close files
        fos.close()
        inStream.close()

        // install jar
        installJar(tmpjarfile, pkg)

        // delete temp file
        tmpjarfile.delete()

        // rescan - update installed packages
        rescanForPackages()
    }

    @Throws(IOException::class)
    fun installJar(tmpjarfile : File, pkg: String) {
        val dir = File(packagesDir, pkg)
        FileUtils.unzip(tmpjarfile.path, dir.path) { dir, filename ->
            /**
             * @param dir the directory in which the filename was found.
             * @param filename the name of the file in dir to test.
            */
            !filename.endsWith(".class")
        }
        dir.setLastModified(tmpjarfile.lastModified())
        val classesDex = File(dir, "classes.dex")
        val installedjarfile = File(bytecodeDir, "$pkg.jar")
        if (!classesDex.exists()) {
            tmpjarfile.renameTo(installedjarfile) // resolve to renaming and hope for the best!
        } else {
            val zos = ZipOutputStream(BufferedOutputStream(FileOutputStream(installedjarfile)))
            try {
                val entry = ZipEntry(classesDex.name)
                zos.putNextEntry(entry)
                val inStream = FileInputStream(classesDex)
                val buffer = ByteArray(1024)
                var read: Int
                while (inStream.read(buffer).also { read = it } != -1) {
                    zos.write(buffer, 0, read)
                }
                inStream.close()
                classesDex.delete()
                zos.closeEntry()
            } finally {
                zos.close()
            }
            installedjarfile.setLastModified(tmpjarfile.lastModified())
        }
    }

    /*******************************************************************************************/

    /**
     * REMOVE PACKAGE
    */

    fun uninstallPackage(pkg: String) {
        FileUtils.remove(File(bytecodeDir, "$pkg.jar"))
        FileUtils.remove(File(packagesDir, pkg))
        FileUtils.remove(File(bytecodeCacheDir, "$pkg.dex"))

        // rescan - update installed packages
        rescanForPackages()
    }

    /*******************************************************************************************/

    /** auxiliar functions */

    fun getClassLoaderForPackage(pkg: String): DexClassLoader? {
        if (!bytecodeCacheDir.exists()) {
            bytecodeCacheDir.mkdirs()
        }
        return DexClassLoader(
                "$bytecodeDir/$pkg.jar",
                bytecodeCacheDir.absolutePath, //bytecodeCacheDir.getAbsolutePath(),
                null, this.javaClass.classLoader)
    }

    fun getBasedirForPackage(pkg: String): String? {
        return "$packagesDir/$pkg"
    }
}