package com.example.heartguard.presentation

import android.content.res.AssetFileDescriptor
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

// --- 1. CONFIGURAÇÃO DE REDE (RETROFIT) ---

// ⚠️ ATENÇÃO: Verifique se este link do Ngrok ainda é válido!
const val BASE_URL = "https://drawlingly-precurricular-eugena.ngrok-free.dev/"

// Modelo de Dados para enviar
data class VitalSigns(
    val patient_name: String,
    val ecg_status: String,
    val bp_value: String,
    val spo2_value: String,
    val location_type: String,
    val timestamp: String
)

data class ApiResponse(
    val status: String,
    val medical_advice: String
)

interface ApiService {
    @POST("analyze")
    suspend fun sendVitals(@Body data: VitalSigns): ApiResponse
}

object RetrofitClient {
    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

// --- 2. PERFIL DO PACIENTE ---
enum class TipoRegiao { URBANA, RURAL_REMOTA }

object PatientProfile {
    const val NAME = "Sr. João (Ribeirinho)"
    const val HAS_PACEMAKER = true
    val REGIAO_ATUAL = TipoRegiao.RURAL_REMOTA
    const val TARGET_SYS = 130
    const val TARGET_DIA = 80
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp()
        }
    }

    @Composable
    fun WearApp() {
        var ecgStatus by remember { mutableStateOf("Aguardando...") }
        var bpValue by remember { mutableStateOf("--/--") }
        var spo2Value by remember { mutableStateOf("--%") }
        var statusColor by remember { mutableStateOf(Color.Gray) }
        var agentMessage by remember { mutableStateOf("") }
        var isRunning by remember { mutableStateOf(false) }

        val scope = rememberCoroutineScope()

        MaterialTheme {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(6.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Paciente: ${PatientProfile.NAME}", color = Color.DarkGray, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(8.dp))

                // Painel de Dados
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ECG", color = Color.LightGray, fontSize = 10.sp)
                        Text(ecgStatus, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("PA", color = Color.LightGray, fontSize = 10.sp)
                        Text(bpValue, color = Color.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (PatientProfile.HAS_PACEMAKER) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "SpO2: $spo2Value",
                        color = if (spo2Value.startsWith("8")) Color.Red else Color.Green,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Área de Mensagem da IA
                if (agentMessage.isNotEmpty()) {
                    Text(
                        text = "🤖 $agentMessage",
                        color = Color.White,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Spacer(modifier = Modifier.height(20.dp))
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Botão
                Button(
                    onClick = {
                        if (!isRunning) {
                            isRunning = true
                            ecgStatus = "Carregando IA..."
                            agentMessage = "Sincronizando..."
                            scope.launch {
                                runSimulation(
                                    updateUI = { ecg, bp, spo2, color, msg ->
                                        ecgStatus = ecg
                                        bpValue = bp
                                        spo2Value = spo2
                                        statusColor = color
                                        agentMessage = msg
                                    },
                                    onFinish = {
                                        isRunning = false
                                        ecgStatus = "Fim"
                                        statusColor = Color.Gray
                                        agentMessage = "Monitoramento encerrado."
                                    }
                                )
                            }
                        }
                    },
                    modifier = Modifier.height(30.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.DarkGray)
                ) {
                    Text(text = if (isRunning) "■ Parar" else "▶ Iniciar", fontSize = 10.sp)
                }
            }
        }
    }

    private suspend fun runSimulation(
        updateUI: (String, String, String, Color, String) -> Unit,
        onFinish: () -> Unit
    ) {
        // --- LOGS DE DEBUG ---
        Log.d("HeartGuardDebug", "1. Iniciando runSimulation...")

        try {
            // Tenta carregar o modelo
            Log.d("HeartGuardDebug", "2. Tentando carregar o modelo TFLite...")
            // ATENÇÃO: Verifique se o nome aqui é IGUAL ao da pasta assets
            val interpreter = Interpreter(loadModelFile("model_br_nativa.tflite"))
            Log.d("HeartGuardDebug", "3. Modelo carregado com sucesso!")

            // Tenta carregar os dados
            Log.d("HeartGuardDebug", "4. Tentando carregar dados_ecg_simulados.txt...")
            val ecgData = loadEcgData("dados_ecg_simulados.txt")
            Log.d("HeartGuardDebug", "5. Dados carregados! Tamanho: ${ecgData.size}")

            val inputSize = 360
            var currentIndex = 0

            Log.d("HeartGuardDebug", "6. Entrando no loop de simulação...")

            while (currentIndex + inputSize < ecgData.size) {
                // ... (O código do loop continua igual aqui dentro) ...

                // Só para garantir, vamos logar o primeiro ciclo
                if (currentIndex == 0) Log.d("HeartGuardDebug", "7. Primeiro ciclo rodando!")

                // 1. INFERÊNCIA LOCAL (EDGE AI)
                val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * 1 * 4)
                inputBuffer.order(ByteOrder.nativeOrder())
                for (i in 0 until inputSize) inputBuffer.putFloat(ecgData[currentIndex + i])

                val outputArray = Array(1) { FloatArray(2) } // Lembre-se: 2 saídas agora
                interpreter.run(inputBuffer, outputArray)
                val maxIndex = outputArray[0].indices.maxByOrNull { outputArray[0][it] } ?: -1

                // 2. SIMULAÇÃO CLÍNICA
                var labelECG = "Normal"
                var simulatedBP = "120/80"
                var simulatedSpO2 = "98%"
                var color = Color.Green

                when (maxIndex) {
                    0 -> { // Normal
                        val sys = Random.nextInt(125, 135); val dia = Random.nextInt(75, 85)
                        simulatedBP = "$sys/$dia"
                        simulatedSpO2 = "${Random.nextInt(96, 99)}%"
                        labelECG = "Normal (BR)"
                        color = Color.Green
                    }
                    1 -> { // Alterado
                        val sys = Random.nextInt(70, 90); val dia = Random.nextInt(40, 60)
                        simulatedBP = "$sys/$dia"
                        simulatedSpO2 = "${Random.nextInt(82, 89)}%"
                        labelECG = "ANORMALIDADE"
                        color = Color.Red
                    }
                }

                val acaoLogistica = if (PatientProfile.REGIAO_ATUAL == TipoRegiao.URBANA) "🚑 CHAMANDO 192..." else "📡 CONECTANDO TELEMEDICINA..."
                var iaResponseText = "Analisando..."

                // 3. ENVIO PARA API
                try {
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    val statusParaEnvio = if (maxIndex == 1) "PERIGO VENTRICULAR" else labelECG

                    val dadosParaEnvio = VitalSigns(
                        patient_name = PatientProfile.NAME,
                        ecg_status = statusParaEnvio,
                        bp_value = simulatedBP,
                        spo2_value = simulatedSpO2,
                        location_type = PatientProfile.REGIAO_ATUAL.name,
                        timestamp = timestamp
                    )

                    val response = RetrofitClient.api.sendVitals(dadosParaEnvio)
                    if (maxIndex == 1) iaResponseText = "⚠️ DETECTADO: $acaoLogistica"
                    else iaResponseText = response.medical_advice.take(80) + "..."

                } catch (e: Exception) {
                    Log.e("HeartGuardDebug", "Erro na API: ${e.message}")
                    iaResponseText = "Modo Offline."
                }

                updateUI(labelECG, simulatedBP, simulatedSpO2, color, iaResponseText)
                currentIndex += inputSize
                delay(2000)
            }

            interpreter.close()
            onFinish()

        } catch (e: Exception) {
            // AQUI É ONDE VAMOS PEGAR O ERRO REAL
            Log.e("HeartGuardDebug", "ERRO FATAL: ${e.message}")
            e.printStackTrace()
            updateUI("ERRO FATAL", "--", "--", Color.Red, "Erro: ${e.message}")
        }
    }
    private fun loadModelFile(filename: String): MappedByteBuffer {
        val fileDescriptor: AssetFileDescriptor = assets.openFd(filename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadEcgData(filename: String): List<Float> {
        val dataList = mutableListOf<Float>()
        val reader = BufferedReader(InputStreamReader(assets.open(filename)))
        reader.forEachLine { line ->
            if (line.isNotEmpty()) {
                try {
                    dataList.add(line.trim().toFloat())
                } catch (e: NumberFormatException) { }
            }
        }
        return dataList
    }
}