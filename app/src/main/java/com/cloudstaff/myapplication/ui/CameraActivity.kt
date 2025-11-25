package com.cloudstaff.myapplication.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cloudstaff.myapplication.databinding.ActivityCameraBinding
import com.cloudstaff.myapplication.utils.retrofit.UploadFileResponse
import com.cloudstaff.myapplication.utils.retrofit.WorkflowInputs
import com.cloudstaff.myapplication.utils.retrofit.WorkflowPayload
import com.cloudstaff.myapplication.utils.retrofit.WorkflowPicture
import com.cloudstaff.myapplication.utils.retrofit.api
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

import android.util.Log
import com.cloudstaff.myapplication.utils.retrofit.token
import com.cloudstaff.myapplication.utils.retrofit.workflowToken
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private var imageCapture: ImageCapture? = null
    private var capturedImageFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
        }

        binding.btnCapture.setOnClickListener { takePhoto() }
        binding.btnSubmit.setOnClickListener { submitPhoto() }
        binding.btnRetake.setOnClickListener { retakePhoto() }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val photoFile = File(externalMediaDirs.first(), "captured_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    capturedImageFile = photoFile
                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)

                    // Show captured image
                    binding.capturedImage.setImageBitmap(bitmap)
                    binding.capturedImage.visibility = View.VISIBLE

                    // Show Submit & Retake
                    binding.btnSubmit.visibility = View.VISIBLE
                    binding.btnRetake.visibility = View.VISIBLE

                    // Hide Capture button & preview
                    binding.btnCapture.visibility = View.GONE
                    binding.previewView.visibility = View.GONE
                }

                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(applicationContext, "Error capturing image", Toast.LENGTH_SHORT).show()
                    exc.printStackTrace()
                }
            }
        )
    }

    private fun retakePhoto() {
        // Hide captured preview & buttons
        binding.capturedImage.visibility = View.GONE
        binding.btnSubmit.visibility = View.GONE
        binding.btnRetake.visibility = View.GONE

        // Show camera preview & capture button
        binding.previewView.visibility = View.VISIBLE
        binding.btnCapture.visibility = View.VISIBLE
    }

    private fun displayOutput() {
        // Hide captured preview & buttons
        binding.btnSubmit.visibility = View.GONE
        binding.btnRetake.visibility = View.GONE

        binding.tvWorkflowResult.visibility = View.VISIBLE
    }

    private fun submitPhoto() {
        binding.btnSubmit.visibility = View.GONE
        binding.btnRetake.visibility = View.GONE

        capturedImageFile?.let { file ->
            Toast.makeText(this, "Photo submitted: ${file.name}", Toast.LENGTH_SHORT).show()

            lifecycleScope.launch {
                uploadFile(file)
            }

        }
    }

    private suspend fun uploadFile(photoFile: File) {
        binding.loading.visibility = View.VISIBLE
        try {

            val requestFile = photoFile.asRequestBody("image/png".toMediaTypeOrNull()) // adjust to "image/jpeg" if needed
            val multipart = MultipartBody.Part.createFormData(
                "file",
                photoFile.name,
                requestFile
            )

            // Call the API
            val response = api.uploadFile("Bearer $token", multipart)



            binding.loading.visibility = View.INVISIBLE

            lifecycleScope.launch {
                runWorkflowForPhoto(response.id)
            }

            // Show success in a Toast
            Log.e("uploadFile", response.toString())
            Toast.makeText(
                this@CameraActivity, // or requireContext() in Fragment
                "Uploaded photo: ${response.name}\nURL: ${response.source_url}",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("TAG", "${e.localizedMessage}")
            binding.loading.visibility = View.INVISIBLE
            Toast.makeText(
                this@CameraActivity, // or requireContext() in Fragment
                "Photo upload failed: ${e.localizedMessage}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private suspend fun runWorkflowForPhoto(workflowId: String) {
        binding.loading.visibility = View.VISIBLE
        try {
            val payload = WorkflowPayload(
                inputs = WorkflowInputs(
                    picture = WorkflowPicture(
                        upload_file_id = workflowId
                    )
                )
            )

            Log.d("PAYLOAD", payload.toString())
            val response = api.runWorkflow("Bearer $workflowToken", payload)

            println("Workflow executed successfully!")
            println("Output area: ${response.data.outputs.result}")
            binding.loading.visibility = View.INVISIBLE

            val resultText = response.data.outputs.result

            // Update the TextView on the main thread using binding
            withContext(Dispatchers.Main) {
                binding.tvWorkflowResult.text = resultText
            }

            displayOutput()
            
            Toast.makeText(
                this@CameraActivity, // or requireContext() if in Fragment
                "Workflow executed successfully!",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            e.printStackTrace()
            println("Output area: ${e.localizedMessage}")
            
            Log.e("runWorkflow", workflowId)
            Log.e("runWorkflow", e.localizedMessage)

            binding.loading.visibility = View.INVISIBLE
            Toast.makeText(
                this@CameraActivity,
                "Workflow failed: ${e.localizedMessage}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun Context.getFileFromUri(uri: Uri, fileName: String): MultipartBody.Part {
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw Exception("Unable to open URI")
        val tempFile = File(cacheDir, fileName)
        inputStream.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        val requestFile = tempFile.asRequestBody(contentResolver.getType(uri)?.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("file", tempFile.name, requestFile)
    }
}
