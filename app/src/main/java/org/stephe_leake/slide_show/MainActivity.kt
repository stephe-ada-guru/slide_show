package org.stephe_leake.slide_show

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2

import java.io.File

class MainActivity : AppCompatActivity()
{
   private val globalDirectory : String = "/storage/emulated/0/Images" // Just a starting point for picking a directory.

   private lateinit var slideshow: ViewPager2
   private lateinit var imageSlideshowAdapter: ImageSlideshowAdapter
   private val slideshowHandler = Handler(Looper.getMainLooper()) // Handler for slideshow transitions
   private var slideshowRunnable: Runnable? = null

   private fun alert(message : String)
   {
      AlertDialog.Builder(this)
         .setMessage(message)
         .setPositiveButton(R.string.Ok, null).show()
   }
   
   private fun checkPermission() : Boolean
   // Returns true if permissions are already granted, false if
   // they are now requested.
   //
   // If false, caller must return and wait for user to restart the
   // activity after granting permission
   {
      var result = true

      // Request MANAGE_EXTERNAL_STORAGE to read global image directories
      if (!Environment.isExternalStorageManager())
         {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.addCategory("android.intent.category.DEFAULT")
            intent.data = Uri.parse("package:${applicationContext.packageName}")
            startActivity(intent)
            result = false
         }

      return result
   }

   private fun startSlideshowTimer()
   {
      val prefs = PreferenceManager.getDefaultSharedPreferences(this)

      val slideDurationString : String = prefs.getString (
         this.getString(R.string.slide_duration_key), this.getString(R.string.slide_duration_default))!!
      var slideDuration = this.getString(R.string.slide_duration_default).toLong()

      try
      {
         slideDuration = slideDurationString.toLong()
      }
      catch (_ : java.lang.NumberFormatException)
      {
         alert("invalid value '${slideDurationString}' for preference" +
               "${this.getString(R.string.slide_duration_title)}; must be an integer.")
      }

      slideshowRunnable = Runnable {
         var currentItem = slideshow.currentItem
         currentItem++
         if (currentItem < imageSlideshowAdapter.itemCount) {
            slideshow.setCurrentItem(currentItem, true) // Use true for smooth scroll
            slideshowHandler.postDelayed(slideshowRunnable!!, slideDuration)
         }
      }
      slideshowHandler.postDelayed(slideshowRunnable!!, slideDuration)
   }

   private fun stopSlideshowTimer()
   {
      slideshowRunnable?.let {
         slideshowHandler.removeCallbacks(it)
         slideshowRunnable = null
      }
   }
     
   fun getDirectoryFromGhostCommanderUri(fileUri : Uri): String?
   {
      // like  content://com.ghostsq.commander.FileProvider/FS/L3N0b3JhZ2UvZW11bGF0ZWQvMC9QaWN0dXJlcy93YWxscGFwZXI/003_0.JPG
      val path = fileUri.path

      if (path != null && path.startsWith("/FS/"))
         {
            val encodedPartAndFile = path.substring("/FS/".length)
            val lastSlashIndex = encodedPartAndFile.lastIndexOf('/')

         if (lastSlashIndex != -1)
            {
               val encodedDirPath = encodedPartAndFile.substring(0, lastSlashIndex)
            try
            {
               val decodedPathBytes = Base64.decode(encodedDirPath, Base64.DEFAULT)
               val decodedDirectoryPath = String(decodedPathBytes, Charsets.UTF_8)

               if (decodedDirectoryPath.startsWith("/"))
                  {
                     Log.d("DirectoryExtract", "Decoded directory path: $decodedDirectoryPath")
                     return decodedDirectoryPath
                  }

            } catch (e: IllegalArgumentException)
            {
               // not Base64
            }
         }
      }
      return null
   } // getDirectoryFromGhostCommanderUri

   val imageExtensions = arrayOf("jpg", "jpeg", "png", "webp", "bmp")

   private fun getImages(absPath: File): List<Uri>
   {
      val files = absPath.listFiles{
         file ->
            !file.isDirectory &&
         imageExtensions.any {ext -> file.name.endsWith(".$ext", ignoreCase = true)}}

      if (files == null) // How can this happen!? Should just be an empty array
         return emptyList()
      else
         return files.mapNotNull{
            file ->
               try {Uri.fromFile(file)}
            catch (_ : Exception) {null}}
   } // getImages(String)

   private fun getImages(directoryUri: Uri): List<Uri>
   {
      val imageFiles = mutableListOf<Uri>()
      val documentId = DocumentsContract.getTreeDocumentId(directoryUri)
      val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, documentId)

      var cursor: Cursor? = null
      try {
         cursor = contentResolver.query(
            childrenUri,
            arrayOf( // Projection
                     DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                     DocumentsContract.Document.COLUMN_MIME_TYPE),
            null, // Selection
            null, // SelectionArgs
            null  // Sort order
         )

         cursor?.use { 
                       val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                       val mimeTypeColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

                       while (it.moveToNext())
                          {
                             val docId = it.getString(idColumn)
                             val mimeType = it.getString(mimeTypeColumn)

                             if (mimeType != null && mimeType.startsWith("image/"))
                                {
                                   val documentUri = DocumentsContract.buildDocumentUriUsingTree(directoryUri, docId)
                                   imageFiles.add(documentUri)
                                }
                          }
         }
      } catch (e: Exception) {
         Log.e("SAF_FILES", "Error querying SAF directory", e)
      }
      return imageFiles
   }  // getImages(Uri)

   private fun startShow (images : List<Uri>)
   {
      imageSlideshowAdapter.updateImages(images)
      startSlideshowTimer()
   }

   private val pickDirectoryLauncher =
      registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
         result ->
            if (result.resultCode == Activity.RESULT_OK)
            {
               result.data?.data?.also {
                  directoryUri ->
                     val images = getImages(directoryUri) 
                  if (images.size > 0)
                     {
                        startShow(images)
                     }
                  else
                     {
                        alert("No images found in ${directoryUri}.")
                     }
               }
            } else {
               Log.w("DirectoryPicker", "Directory selection cancelled or failed.")
            }
      }

   private fun isTreeUri(uri: Uri): Boolean
   {
      val paths = uri.pathSegments
      return paths.isNotEmpty() &&
      paths[0] == "tree" &&
      paths.size >= 2
   }

   private fun handleIntent (intent : Intent)
   {
      var images = listOf<Uri>()
      var absPath : String? = null
      var badIntent = false
      
      if (intent.action == Intent.ACTION_VIEW && intent.data != null)
         {
            // Probably from GhostCommander 'open with ..'
            absPath = getDirectoryFromGhostCommanderUri(intent.data!!)
         }
      else if (intent.action == Intent.ACTION_SEND)
         {
            // Probably from GhostCommander 'send to ...'
            val uri : Uri? = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM, Uri::class.java)
            
            if (uri != null)
               {
                  absPath = getDirectoryFromGhostCommanderUri(uri)
               }
            else
               {
                  badIntent = true
               }
         }
      else
         badIntent = true
      
      if (absPath != null)
         {
            val dir = File(absPath)
            if (dir.exists() && dir.isDirectory)
               {
                  if (checkPermission())
                     {
                        images = getImages(dir)
                     }
                  else
                     {
                        return
                     }
               }
            else
               badIntent = true
         }

      if (badIntent)
         {
            alert("intent not recognized: ${intent.toString()}")
            return
         }
      
      if (images.size > 0)
         {
            startShow(images)
         }
      else
         {
            alert("No images found in ${absPath}.")
         }
   }
   
   override fun onCreate(savedInstanceState: Bundle?)
   {
      super.onCreate(savedInstanceState)
      setContentView(R.layout.mainactivity)

      slideshow = findViewById(R.id.image_slideshow)
      imageSlideshowAdapter = ImageSlideshowAdapter(emptyList())
      slideshow.adapter = imageSlideshowAdapter

      if (this.intent != null && intent.action != Intent.ACTION_MAIN)
         {
            handleIntent(this.intent)
         }
   }

   override fun onNewIntent(intent : Intent)
   {
      super.onNewIntent(intent)
      handleIntent(intent)
   }
   
   override fun onCreateOptionsMenu(menu: Menu): Boolean
   {
      val Inf: MenuInflater = getMenuInflater()
      Inf.inflate(R.menu.main_menu, menu)
      return true // display menu
   }

   override fun onOptionsItemSelected(item: MenuItem): Boolean
   {
      when (item.getItemId())
      {
         // Alphabetical order

         R.id.menu_open ->
            {
               val initialUri = DocumentsContract.buildRootsUri("com.android.externalstorage.documents")
               val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
               intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
               pickDirectoryLauncher.launch(intent)
            }
         
         R.id.menu_preferences ->
            {
               // We don't need a result
               this.startActivity(Intent(this, PrefActivity::class.java))
            }
         
         else ->
            {
               Log.e("stephes_slide_show", "activity.onOptionsItemSelected: unknown MenuItemId " + item.getItemId())
            }
      }
      return false // continue menu processing
   } // onOptionsItemSelected
}

