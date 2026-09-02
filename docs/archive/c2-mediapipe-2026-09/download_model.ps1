$Url = "https://storage.googleapis.com/mediapipe-models/text_embedder/universal_sentence_encoder/float32/1/universal_sentence_encoder.tflite"
$OutDir = "app\src\main\assets\models"
$OutPath = "$OutDir\universal_sentence_encoder.tflite"

if (!(Test-Path -Path $OutDir)) {
    New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
}

Write-Host "Downloading Universal Sentence Encoder model..."
Invoke-WebRequest -Uri $Url -OutFile $OutPath
Write-Host "Download complete: $OutPath"
