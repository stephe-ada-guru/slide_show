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

import android.view.View.GONE
import android.view.View.VISIBLE
class MainActivity : AppCompatActivity()
{
   private val CHECK_PERM_OPEN_DIR = 101

   val globalDirectory : String = "/storage/emulated/0/Images" // Just a starting point for picking a directory.

   private lateinit var slideshow: ViewPager2
   private lateinit var imageSlideshowAdapter: ImageSlideshowAdapter
   private val slideshowHandler = Handler(Looper.getMainLooper()) // Handler for slideshow transitions
   private var slideshowRunnable: Runnable? = null

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
         AlertDialog.Builder(this)
            .setMessage("invalid value '${slideDurationString}' for preference" +
                        "${this.getString(R.string.slide_duration_title)}; must be an integer.")
            .setPositiveButton(R.string.Ok, null).show()
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
     
   private fun checkPermission(code : Int) : Boolean
   // Returns true if permissions are already granted, false if
   // they are now requested.
   //
   // If false, caller must return and wait for
   // MainActivity.onRequestPermissionsResult to restart the
   // activity indicated by 'code'.
   {
      val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES)

      val permissionsToRequest = mutableListOf<String>()
      
      var result = true

      // Request MANAGE_EXTERNAL_STORAGE to read global images.
      if (!Environment.isExternalStorageManager())
         {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.addCategory("android.intent.category.DEFAULT")
            intent.data = Uri.parse("package:${applicationContext.packageName}")
            startActivity(intent)
            result = false
         }

      for (permission in REQUIRED_PERMISSIONS)
         {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
               {
                  permissionsToRequest.add(permission)
               }
         }
      
      if (permissionsToRequest.isNotEmpty())
         {
            var rationaleRequired = false
            
            for (permission in REQUIRED_PERMISSIONS)
               {
                  if (this.shouldShowRequestPermissionRationale(permission))
                     {
                        // See // https://developer.android.com/training/permissions/requesting#explain
                        rationaleRequired = true
                     }
               }

            if (rationaleRequired)
               { AlertDialog.Builder(this)
                    .setMessage("We allow viewing images from anywhere in storage, " +
                    "so we need file read/write permission.")
                    .setPositiveButton(R.string.Ok, null).show()
               }
            
               ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), code)

            result = false
         }
      return result
   }

   val imageExtensions = arrayOf("jpg", "jpeg", "png", "webp", "bmp")

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
      } finally {
         cursor?.close() // Ensure cursor is closed (though 'use' handles this)
      }
      return imageFiles
   }

   private val pickDirectoryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
           result ->
              if (result.resultCode == Activity.RESULT_OK)
              {
                 result.data?.data?.also {
                    directoryUri ->
                       imageSlideshowAdapter.updateImages(getImages(directoryUri))
                    startSlideshowTimer()
                 }
            } else {
                Log.w("DirectoryPicker", "Directory selection cancelled or failed.")
            }
        }

   fun onClickOpenDir(v : View)
   {
   }
   
   override fun onCreate(savedInstanceState: Bundle?)
   {
      super.onCreate(savedInstanceState)
      setContentView(R.layout.mainactivity)

      slideshow = findViewById(R.id.image_slideshow)
      imageSlideshowAdapter = ImageSlideshowAdapter(emptyList())
      slideshow.adapter = imageSlideshowAdapter
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
               if (checkPermission(CHECK_PERM_OPEN_DIR))
                  {
                     val initialUri = DocumentsContract.buildRootsUri("com.android.externalstorage.documents")
                     val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                     intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
                     pickDirectoryLauncher.launch(intent)
                  }
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

