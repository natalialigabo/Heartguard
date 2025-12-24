package com.example.heartguard.presentation.theme


import android.content.res.AssetFileDescriptor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp()
        }
    }

    @Composable
    fun WearApp() {
        // Estados da Interface (O que muda na tela)
        var statusText by remember { mutableStateOf("Pronto para Monitorar") }
        var statusColor by remember { mutableStateOf(Color.Gray) }
        var isRunning by remember { mutableStateOf(false) }

        // Escopo para rodar a simulação em segundo plano (Corrotina)
        val scope = rememberCoroutineScope()

        MaterialTheme {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Texto Principal de Status
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                // Botão de Iniciar
                Button(
                    onClick = {
                        if (!isRunning) {
                            isRunning = true
                            statusText = "Iniciando IA..."
                            // Lança a simulação
                            scope.launch {
                                runSimulation(
                                    updateUI = { text, color ->
                                        statusText = text
                                        statusColor = color
                                    },
                                    onFinish = {
                                        isRunning = false
                                        statusText = "Monitoramento Finalizado"
                                        statusColor = Color.Gray
                                    }
                                )
                            }
                        }
                    }
                ) {
                    Text(text = if (isRunning) "Rodando..." else "Iniciar")
                }
            }
        }
    }

    // --- LÓGICA DA INTELIGÊNCIA ARTIFICIAL ---

    private suspend fun runSimulation(updateUI: (String, Color) -> Unit, onFinish: () -> Unit) {
        try {
            // 1. Carregar o Modelo TFLite
            val interpreter = Interpreter(loadModelFile("model_quantizado.tflite"))

            // 2. Ler o arquivo de dados simulados (TXT)
            val ecgData = loadEcgData("dados_ecg_simulados.txt")

            // Tamanho da janela que o modelo espera (360 pontos)
            val inputSize = 360
            var currentIndex = 0

            // 3. Loop de Simulação (Batimento a Batimento)
            while (currentIndex + inputSize < ecgData.size) {

                // A. Prepara o Input: Pega 360 pontos do array gigante
                // O modelo espera formato: [1, 360, 1] -> FloatBuffer ou Array 3D
                val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * 1 * 4) // 4 bytes por float
                inputBuffer.order(ByteOrder.nativeOrder())

                for (i in 0 until inputSize) {
                    inputBuffer.putFloat(ecgData[currentIndex + i])
                }

                // B. Prepara o Output: Onde a IA vai escrever a resposta (5 classes)
                // Formato: [1, 5]
                val outputArray = Array(1) { FloatArray(5) }

                // C. RODA A IA (Inferência)
                interpreter.run(inputBuffer, outputArray)

                // D. Interpreta o Resultado
                // outputArray[0] tem as probabilidades: [Normal%, S%, V%, F%, Q%]
                // Vamos pegar o índice da maior probabilidade
                val probabilities = outputArray[0]
                val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1
                val confidence = probabilities[maxIndex]

                // E. Atualiza a Tela com base na Classe
                // 0=Normal, 1=S, 2=V (Perigo), 3=F, 4=Q
                when (maxIndex) {
                    0 -> updateUI("Ritmo Normal\n(Sinusal)", Color.Green)
                    1 -> updateUI("Alerta: Arritmia\nSupraventricular", Color.Yellow)
                    2 -> updateUI("PERIGO: Arritmia\nVentricular Detectada!", Color.Red) // <--- O MOMENTO CHAVE DO TCC
                    else -> updateUI("Sinal Inconclusivo", Color.Gray)
                }

                // Avança para o próximo batimento (pula 360 pontos)
                currentIndex += inputSize

                // Espera 1 segundo para parecer um relógio de verdade
                delay(1000)
            }

            interpreter.close()
            onFinish()

        } catch (e: Exception) {
            e.printStackTrace()
            updateUI("Erro: ${e.message}", Color.Red)
        }
    }

    // Função auxiliar para carregar o arquivo .tflite da pasta assets
    private fun loadModelFile(filename: String): MappedByteBuffer {
        val fileDescriptor: AssetFileDescriptor = assets.openFd(filename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    // Função auxiliar para ler o TXT e transformar em lista de números
    private fun loadEcgData(filename: String): List<Float> {
        val dataList = mutableListOf<Float>()
        val reader = BufferedReader(InputStreamReader(assets.open(filename)))
        reader.forEachLine { line ->
            if (line.isNotEmpty()) {
                try {
                    dataList.add(line.trim().toFloat())
                } catch (e: NumberFormatException) {
                    // Ignora linhas ruins
                }
            }
        }
        return dataList
    }
}