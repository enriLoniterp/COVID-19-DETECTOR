package com.example.covid_detect

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.text.Html
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.TensorProcessor
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.*
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


private const val REQUEST_PERMISSIONS_REQUEST_CODE = 1
private const val PICKFILE_REQUEST_CODE = 2

class MainActivity : AppCompatActivity() {

    private var ok : Boolean = false
    private val REQUEST_IMAGE_CAPTURE = 1
    private lateinit var currentPhotoPath: String
    lateinit var photoURI : Uri

    private var permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val button = findViewById<Button>(R.id.btn_get_slice)
        val button2 = findViewById<Button>(R.id.button)

        val colorDrawable = ColorDrawable(Color.parseColor("#000080"))
        supportActionBar?.setBackgroundDrawable(colorDrawable);
        supportActionBar?.title = Html.fromHtml("<font color='#FFFFFF'>CT Covid-19 Detector</font>");
        val welcome : String = "Welcome to CT Covid-19 Detector App!\n\nWith this app you can check if a CT slice has signs of the virus SARS-CoV-19\n\nYou can take a photo of the slice or load the image from memory and also see the history of the scans made\n"
        findViewById<TextView>(R.id.textView3).text = welcome
        requestPermissionsIfNecessary(permissions)
        //supportActionBar?.elevation=0F
        button.setOnClickListener{
            requestPermissionsIfNecessary(permissions)
            dispatchTakePictureIntent()

        }

        button2.setOnClickListener{
            requestPermissionsIfNecessary(permissions)
            dispatchLoadPictureIntent()

        }
    }



    @SuppressLint("SimpleDateFormat")
    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File = getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        return File.createTempFile(
                "JPEG_${timeStamp}_", /* prefix */
                ".jpg", /* suffix */
                storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }


    private fun saveScan(a: String, toWrite: String) {

        val file = File(this.applicationContext!!.filesDir, "History.txt")
        if(!file.exists()){
            file.createNewFile()
        }else{checkEqual(" " + a, toWrite)}


        val f = PrintWriter(FileOutputStream(file, true))

        f.println(toWrite)
        f.flush()
        f.close()

    }

    private fun checkEqual(p: String, s: String){
        val file2 = File(this.applicationContext!!.filesDir, "History.txt")

        var st : StringTokenizer
        var riga : String?
        var found = false
        val reader = BufferedReader(FileReader(file2))
        riga = reader.readLine()
        var i = 0

        while (riga != null && !found){
            st = StringTokenizer(riga, "*")
            var sl = st.nextToken()
            if (sl.equals(p)){
                found = true
            }
            if(!found){i++}
            riga = reader.readLine()
        }

        reader.close()
        if(found){
            removeLine(i)
        }
    }

    private fun removeLine(pos: Int){

        val file = File(this.applicationContext!!.filesDir, "temp.txt")
        file.createNewFile()
        val f = PrintWriter(FileOutputStream(file, true))


        val file2 = File(this.applicationContext!!.filesDir, "History.txt")


        var riga : String?
        val reader = BufferedReader(FileReader(file2))
        riga = reader.readLine()
        var i = 0

        while (riga != null){
            if(i != pos){
                f.println(riga)
            }
            i++
            riga = reader.readLine()
        }

        reader.close()
        f.close()

        file2.delete()
        file.renameTo(File(this.applicationContext!!.filesDir, "History.txt"))



    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val imageView = findViewById<ImageView>(R.id.myView)
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            this.grabImage(imageView, 0);
        }else if(requestCode == PICKFILE_REQUEST_CODE && resultCode == RESULT_OK){
            if(data == null){
                return
            }
            photoURI = data.data!!
            this.grabImage(imageView, 1)
        }

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        //method used when clicking on option of the menu/action bar
        when (item.itemId) {
            R.id.MENU_1 -> {
                val i = Intent(this, HistoryActivity::class.java)
                startActivity(i)
            }

        }
        return false
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        //method used for the menu/action bar
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.main, menu)
        return true
    }

    private fun grabImage(imageView: ImageView, i: Int) {
        findViewById<TextView>(R.id.textView3).visibility=View.GONE
        this.contentResolver.notifyChange(photoURI, null)
        val cr = this.contentResolver
        var bitmap: Bitmap
        val adjustedBitmap : Bitmap
        val matrix = Matrix()
        val a : Boolean
        if (i==0) {
            a = false
            val exif = ExifInterface(contentResolver.openFileDescriptor(photoURI, "r")?.fileDescriptor)
            val rotation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val rotationInDegrees: Int = exifToDegrees(rotation)
            if (rotation != 0) {
                matrix.preRotate(rotationInDegrees.toFloat())
            }
        }else{a = true}
        try {
            bitmap = MediaStore.Images.Media.getBitmap(cr, photoURI)
            var height = bitmap.height
            var width = bitmap.width
            if(i==0) {
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
            }
            //val bitmap2 : Bitmap = BitmapFactory.decodeResource(this.resources, R.drawable.noncovid1096)
            val imageProcessor = ImageProcessor.Builder()
                    .add(ResizeOp(240, 240, ResizeOp.ResizeMethod.BILINEAR))
                    .add(NormalizeOp(0.0F, 1.0F))
                    .build()


            var tImage = TensorImage(DataType.FLOAT32)
            var p = String()
            if(a){ p= this.getRealPathFromURI_API19(this.applicationContext, photoURI).toString()}
            tImage.load(bitmap)
            tImage = imageProcessor.process(tImage)


            useNet(tImage.buffer, a, p)
            /*
            if(i==0) {
                adjustedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
            }else{
                adjustedBitmap = bitmap
            }

             */
            imageView.setImageBitmap(bitmap)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load", Toast.LENGTH_SHORT).show()
            println(e.stackTraceToString())
        }
    }

    fun getRealPathFromURI_API19(context: Context?, uri: Uri): String? {
        val isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":").toTypedArray()
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                }

                // TODO handle non-primary volumes
            } else if (isDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id))
                return context?.let { getDataColumn(it, contentUri, null, null) }
            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":").toTypedArray()
                val type = split[0]
                var contentUri: Uri? = null
                if ("image" == type) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                } else if ("video" == type) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else if ("audio" == type) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val selection = "_id=?"
                val selectionArgs = arrayOf(
                        split[1]
                )
                return context?.let { getDataColumn(it, contentUri, selection, selectionArgs) }
            }
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {

            // Return the remote address
            return if (isGooglePhotosUri(uri)) uri.lastPathSegment else context?.let { getDataColumn(it, uri, null, null) }
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }
        return null
    }

    fun getDataColumn(context: Context, uri: Uri?, selection: String?,
                      selectionArgs: Array<String>?): String? {
        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(
                column
        )
        try {
            cursor = uri?.let {
                context.getContentResolver().query(it, projection, selection, selectionArgs,
                        null)
            }
            if (cursor != null && cursor.moveToFirst()) {
                val index: Int = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(index)
            }
        } finally {
            if (cursor != null) cursor.close()
        }
        return null
    }

    fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

    fun isGooglePhotosUri(uri: Uri): Boolean {
        return "com.google.android.apps.photos.content" == uri.authority
    }


    private fun exifToDegrees(exifOrientation: Int): Int {
        if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
            return 90
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
            return 180
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
            return 270
        }
        return 0
    }

    private fun useNet(im: ByteBuffer, arc: Boolean, pa: String){

        val probabilityBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 2), DataType.FLOAT32)

        val tfliteModel = FileUtil.loadMappedFile(this.baseContext,
                "converted_model.tflite")
        val tflite : Interpreter = Interpreter(tfliteModel)
        println(tfliteModel.toString())
        val probabilityProcessor = TensorProcessor.Builder().add(NormalizeOp(0.0F, 255.0F)).build()

        tflite.run(im, probabilityBuffer.buffer)

        val arr = probabilityProcessor.process(probabilityBuffer).floatArray
        var max : Float = 0F
        var indM : Int = 0
        var ind : Int = 0
        for (element in arr){
            print(element.toString())
            if (element > max){
                max  = element
                indM = ind
            }
            ind++
        }

        var s : String
        var toSave  = String()
        val image : Bitmap
        val imView = findViewById<ImageView>(R.id.visurIcon)
        if ( indM == 0){
            s = "Found signs of Covid-19 in this slice!"
            if(arc){

                toSave = " "+ pa + "*    COVID"
            }
            image = BitmapFactory.decodeResource(this.resources, R.drawable.coronavirus)
        }else{
            s = "No signs of Covid-19 were found in this slice"
            if(arc){
                toSave = " "+ pa + "*    NON COVID"
            }
            image = BitmapFactory.decodeResource(this.resources, R.drawable.no_virus)
        }
        val result = findViewById<TextView>(R.id.textView)
        result.text =  s
        imView.setImageBitmap(image)

        if (arc){
            saveScan(pa, toSave)
        }
        /*
        // Releases model resources if no longer used.
        model.close()

         */
    }


    private fun dispatchTakePictureIntent() {

        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            // Ensure that there's a camera activity to handle the intent
            takePictureIntent.resolveActivity(packageManager)?.also {
                // Create the File where the photo should go
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    null
                }
                // Continue only if the File was successfully created
                photoFile?.also {
                    photoURI = FileProvider.getUriForFile(
                            this,
                            "com.example.covid_detect",
                            it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            }
        }
    }


    private fun dispatchLoadPictureIntent() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        startActivityForResult(intent, PICKFILE_REQUEST_CODE)
    }




    private fun requestPermissionsIfNecessary(permissions: Array<String>) {
        //method to check and request permission
        val permissionsToRequest: ArrayList<String> = ArrayList()
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED
            ) {
                // Permission is not granted
                permissionsToRequest.add(permission)
            }
        }
        if (permissionsToRequest.size > 0) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(arrayOfNulls(0)),
                    REQUEST_PERMISSIONS_REQUEST_CODE
            )
        }
    }

}