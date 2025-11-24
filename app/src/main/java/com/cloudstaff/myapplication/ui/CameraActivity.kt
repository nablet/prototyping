package com.cloudstaff.myapplication.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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
import com.cloudstaff.myapplication.databinding.ActivityCameraBinding
import java.io.File

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

    private fun submitPhoto() {
        capturedImageFile?.let { file ->
            Toast.makeText(this, "Photo submitted: ${file.name}", Toast.LENGTH_SHORT).show()
            // TODO: Upload file or send to server

            // Optionally reset to camera preview
            retakePhoto()
        }
    }
}
