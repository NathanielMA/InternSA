
//TODO:
// - Make connection non-reliant on every computer being present (DONE)
// - Integrate azimuth calculations using coordinates (DONE)
// - Tie coordinate data to a specific operator (DONE)
// - Figure out JavaSoundSampled panning tools (IN PROGRESS)

//region IMPORTS
/**lwjgl imports will require OpenAl64.dll and lwjgl64.dll to be added
 * to SDK bin directory (EX: Users\\<User Name>\\.jdks\\azul-15.0.3\\bin)
 *
 * These .dll files can be found in lwhjgl-2.9.3\\native\\windows folder
 */
import org.lwjgl.openal.AL
import org.lwjgl.openal.AL10
import org.lwjgl.util.WaveData
import java.io.BufferedInputStream

import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.*
import java.util.*
import javax.sound.sampled.*
import javax.swing.JFrame
import javax.swing.JLabel
import kotlin.ConcurrentModificationException
import kotlin.concurrent.timerTask
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

//endregion

object Operator{
    //region OPERATORS DATACLASS
    /**Data class that stores operator information
     * Stores:
     *      IP
     *      Port
     *      Host Name
     *      Longitude, Latitude, Distancem Azimuth and Nose
     * Note:
     *      offset, activeTime, isAcvtive are used for dynamic port removal
     */
    data class opInfo(var OperatorName: String, var OperatorPort: String = "") {
        var OperatorIP: String = ""
        var OperatorLongitude: Double = 0.0
        var OperatorLatitude: Double = 0.0
        var OperatorNose: Double = 0.0
        var OperatorAzimuth: Double = 0.0
        var OperatorDistance: Double = 0.0
        var offset: Int = 0
        var activeTime: Int = 0
        var isActive: Boolean = false
    }
    //endregion

    //region GLOBAL VARIABLES
    //Global variables used within global functions
    var operators = mutableMapOf<String, opInfo>()  // List of operators connected to server
    val portsAudio = mutableSetOf<String>()         // List of connected ports
    val addresses = mutableSetOf<String>()          // List of IP's
    var opDetected = false                          // Boolean: Detects new connection request
    var timeOutOp = false                           // Boolean: Determines whether server has been initialized
    var selfAdded = false                           // Boolean: Determines if self has been initialized
    var opNotFound = false
    var opGPS = mutableListOf<String>("","","","","")
    var suspended: Boolean = false
    //endregion

    @Throws(IOException::class)
    @JvmStatic
    fun main(args: Array<String>) {
        //region AUDIO FORMAT AND DATALINE
        val format = AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
            44100F,
            16,
            2,
            4,
            44100F,
            true)
        var mic: TargetDataLine
        val output: SourceDataLine
        val output2: SourceDataLine
        val output3: SourceDataLine
        val output4: SourceDataLine
        val output5: SourceDataLine
        val output6: SourceDataLine
        val output7: SourceDataLine
        val output8: SourceDataLine
        //endregion

        try {
            //region INITIALIZATION
            //region MICROPHONE/HEADSET IO
            // Initialize values
            val out = ByteArrayOutputStream()
            val out2 = ByteArrayOutputStream()
            val out3 = ByteArrayOutputStream()
            val out4 = ByteArrayOutputStream()
            val out5 = ByteArrayOutputStream()
            val out6 = ByteArrayOutputStream()
            val out7 = ByteArrayOutputStream()
            val out8 = ByteArrayOutputStream()
            var numBytesRead: Int
            val buffer = 1024

            // Initialize input (microphone)
            mic = AudioSystem.getTargetDataLine(format)
            val info = DataLine.Info(TargetDataLine::class.java, format)
            mic = AudioSystem.getLine(info) as TargetDataLine
            mic.open(format)
            val data = ByteArray(mic.bufferSize / 5)
            mic.start()

            // Initialize output (speakers/headphones)
            val dataLineInfo = DataLine.Info(SourceDataLine::class.java, format)        // OP-1 audio format
            output = AudioSystem.getLine(dataLineInfo) as SourceDataLine
            output.open(format)
            output.start()

            val dataLineInfo2 = DataLine.Info(SourceDataLine::class.java, format)       // OP-2 audio format
            output2 = AudioSystem.getLine(dataLineInfo2) as SourceDataLine
            output2.open(format)
            output2.start()

            val dataLineInfo3 = DataLine.Info(SourceDataLine::class.java, format)       // OP-3 audio format
            output3 = AudioSystem.getLine(dataLineInfo3) as SourceDataLine
            output3.open(format)
            output3.start()

            val dataLineInfo4 = DataLine.Info(SourceDataLine::class.java, format)       // OP-4 audio format
            output4 = AudioSystem.getLine(dataLineInfo4) as SourceDataLine
            output4.open(format)
            output4.start()

            val dataLineInfo5 = DataLine.Info(SourceDataLine::class.java, format)       // OP-4 audio format
            output5 = AudioSystem.getLine(dataLineInfo5) as SourceDataLine
            output5.open(format)
            output5.start()

            val dataLineInfo6 = DataLine.Info(SourceDataLine::class.java, format)       // OP-4 audio format
            output6 = AudioSystem.getLine(dataLineInfo6) as SourceDataLine
            output6.open(format)
            output6.start()

            val dataLineInfo7 = DataLine.Info(SourceDataLine::class.java, format)       // OP-4 audio format
            output7 = AudioSystem.getLine(dataLineInfo7) as SourceDataLine
            output7.open(format)
            output7.start()

            val dataLineInfo8 = DataLine.Info(SourceDataLine::class.java, format)       // OP-4 audio format
            output8 = AudioSystem.getLine(dataLineInfo8) as SourceDataLine
            output8.open(format)
            output8.start()
            //endregion

            //region HOST: ADDRESS & PORT CONFIG
            val findIP = """(\d+)\.(\d+)\.(\d+)\.(\d+)""".toRegex()                         // Locates Host IP octet
            val findName = """([A-Z])\w+""".toRegex()                                       // Locates Host Name
            val hostName = findName.find(Inet4Address.getLocalHost().toString())?.value     // Host Name
            val hostAdd = findIP.find(Inet4Address.getLocalHost().toString())?.value        // Host address
            val Host = opInfo(hostName.toString())                                          // Set Host
            Host.OperatorIP = hostAdd.toString()                                            // Set Host IP
            //endregion

            //region AUDIO SOCKET CREATION (8 PORTS)
            var portAudio = 6011
            val socketRecAudio = DatagramSocket(6011)
            val socketRecAudio2 = DatagramSocket(6012)
            val socketRecAudio3 = DatagramSocket(6013)
            val socketRecAudio4 = DatagramSocket(6014)
            val socketRecAudio5 = DatagramSocket(6015)
            val socketRecAudio6 = DatagramSocket(6016)
            val socketRecAudio7 = DatagramSocket(6017)
            val socketRecAudio8 = DatagramSocket(6018)
            //endregion

            //region STRING SOCKET CREATIONS
            val portString = 8000     // String port for coordinates
            val socketString = DatagramSocket(portString)
            val socketSendAudio = DatagramSocket()
            //endregion

            //region HYPER IMU
            /** Hyper IMU port and socket creation
             * Note:
             *      hyperIMUport must equal to the port set within the Hyper IMU app
             */
            val hyperIMUport = 9000
            val socket = DatagramSocket(hyperIMUport)
            //endregion

            //region MULTICAST SERVER CREATION
            val portConnect = 8010
            val socketConnect = MulticastSocket(portConnect)
            socketConnect.joinGroup(InetSocketAddress("230.0.0.0",portConnect),null)
            //endregion

            //region PUSH-TO-TALK
            /** Note:
             *      PTT won't work without the window being visible.
             *      Setting frame.isVisible = false will deactivate PTT functionality
             *      In the final version, after integration with ATAK or Android, JFrame will not be used.
             */
            var voice_Chat = 0                                   // Determines whether PTT is activated

            JFrame.setDefaultLookAndFeelDecorated(true)
            val frame = JFrame("KeyListener Example")       //Set-up title for separate window
            frame.setSize(300, 150)                 //Set-up dimensions of window
            val label = JLabel()
            frame.add(label)
            frame.addKeyListener(object : KeyListener {
                override fun keyTyped(ke: KeyEvent) {           // Detects key typed
                }
                override fun keyPressed(ke: KeyEvent) {         // Detects Key Pressed
                    if(ke.keyChar == 't') {
                        voice_Chat = 1
                    }
                }
                override fun keyReleased(ke: KeyEvent) {        // Detects if key pressed was released
                    if(ke.keyChar == 't') {
                        voice_Chat = 0
                    }
                }
            })
            frame.isVisible = true                              //Open Window
            //endregion
            //endregion

            //region THREADS

            //region ConnectThread: SEND OP REQUESTS OVER MULTICAST SERVER
            class ConnectThread: Runnable {
                override fun run() {
                    while (true) {
                        Thread.sleep(1000)

                        // Initialize first operator (self) on server
                        if(timeOutOp) {
                            Thread.sleep(1000)
                            if (addresses.isNullOrEmpty()) {
                                /** Send own information over server
                                 * This is used until at least one operator joins
                                 */
                                val dataString = "OP REQUEST: OPNAME: $hostName IP: $hostAdd PORT_AUDIO: $portAudio"
//                                println("[Line: ${LineNumberGetter()}] $dataString")
                                val datagramPacket = DatagramPacket(
                                    dataString.toByteArray(),
                                    dataString.toByteArray().size,
                                    InetAddress.getByName("230.0.0.0"),
                                    portConnect
                                )
                                socketConnect.send(datagramPacket)

                                /**Set own port and Add own port to list of operators
                                 */
                                portsAudio.add(portAudio.toString())
                                Host.OperatorPort = portAudio.toString()
                                allocatePort(hostName.toString(), portAudio.toString(), hostAdd.toString())
                                selfAdded = true
                                Host.isActive = true // Will always be true
                            }
                        } else if (opDetected && selfAdded && !timeOutOp) {
                            /** Send all operator information over server
                             * This is used until all operators leave the server
                             */
                            val dataString =
                                "OP REQUEST: OPNAME: $hostName IP: $hostAdd PORT_AUDIO: $portAudio PORTS_CONNECTED: $portsAudio"
//                            println("[Line: ${LineNumberGetter()}] $dataString")
                            val datagramPacket = DatagramPacket(
                                dataString.toByteArray(),
                                dataString.toByteArray().size,
                                InetAddress.getByName("230.0.0.0"),
                                portConnect
                            )
                            socketConnect.send(datagramPacket)
                            if(!Host.isActive){
                                Host.isActive = true
                            }
                            opDetected = false
                            timeOutOp = false
                        }
                    }
                }
            }
            //endregion

            //region ConnectRecThread: RECEIVE OPERATOR REQUESTS
            class ConnectRecThread: Runnable{
                override fun run(){
                    while(true) {

                        // Wait 5 seconds before server initialization
                        if(!opDetected && !selfAdded){
                            Thread.sleep(100)
                            Timer().schedule(timerTask {
                                timeOutOp = true
                            }, 1000 * 5)
                        }

                        // Initialize values and receive coordinates
                        val buffer2 = ByteArray(1024)
                        val response2 = DatagramPacket(buffer2, 1024)
                        socketConnect.receive(response2)
                        val data2 = response2.data
                        val dataString = String(data2, 0, data2.size)

                        // Identify IP and ports //
                        val sample = arrayOf<String>("","","","")   // Initialize array for storage of multiple regex's
                        when {
                            """OP REQUEST: """.toRegex()            // Identifying address of operators
                                .containsMatchIn(dataString) -> {

                                // Cancel 5 second Timer if server has been initialize by another operator
                                if(!selfAdded) {
                                    Timer().cancel()
                                    println("[Line: ${LineNumberGetter()}] Timer Cancelled.")
                                }

                                /** Variables used to store and recognize parsed data from received packets
                                 * Variables will Regex:
                                 *      operator IP, Name, Port and total Ports on server
                                 */
                                val opIP = """(\d+)\.(\d+)\.(\d+)\.(\d+)""".toRegex().find(dataString)?.value
                                val opName = """(?<=OPNAME: )\w+""".toRegex().find(dataString)?.value.toString()
                                val opPort = """(?<=PORT_AUDIO: )\d\d\d\d""".toRegex().find(dataString)?.value.toString()
                                val opPortR = """\d\d\d\d""".toRegex()
                                val patt = opPortR.findAll(dataString)

                                if(!addresses.contains(opIP)) { // New operator detected
                                    try {
                                        if (opIP != hostAdd) {  // New operator is not self
                                            var i = 0

                                            /** Sort through all Ports found
                                             * Add all ports to portsAudio set
                                             */
                                            patt.forEach { f ->
                                                sample[i] = f.value
                                                if(sample[i] != "") {
                                                    portsAudio.add(sample[i])
                                                    i++
                                                }
                                            }

                                            allocatePort(opName, opPort, opIP.toString())   // Set operator information
                                            addresses.add(opIP.toString())                  // Add IP to addresses set
                                            println("[Line: ${LineNumberGetter()}] OP FOUND @ $opIP $opPort")
                                        }

                                        /** Determine whether to take port 6011
                                         * Will only be used if port 6011 has left server and removed from portsAudio set
                                         */
                                        if(!portsAudio.contains(6011.toString()) && !selfAdded){
                                            portsAudio.add(portAudio.toString())
                                            Host.OperatorPort = portAudio.toString()
                                            allocatePort(hostName.toString(), portAudio.toString(), hostAdd.toString())
                                            selfAdded = true
                                        }

                                    } catch (e: BindException) { // Catch a Bind exception if portAudio is already bound
                                        println("[Line: ${LineNumberGetter()}] Port $opPortR is already bound.")
                                        println("[Line: ${LineNumberGetter()}] " + e.message)
                                    }

                                    /** Dynamically set port
                                     * In order of statements:
                                     *      First:
                                     *          Compare own port, starting at port 6011, to received ports.
                                     *          If port exists, own port is increased.
                                     *          Repeats until own port does not exist within set.
                                     *          Will not exceed 8.
                                     *      Second:
                                     *          If there exists more ports than operators on server.
                                     *          Compare existing ports to current operators.
                                     *          Remove extra port.
                                     */
                                    val portsInUse = portsAudio.toList()
                                    if(!selfAdded) {
                                        Thread.sleep(100)
                                        Timer().schedule(timerTask {
                                            if (portsAudio.contains(portAudio.toString()) && !selfAdded) {
                                                for (i in 0 until portsAudio.size) {
                                                    if (portsAudio.contains(portAudio.toString())) {
                                                        portAudio = portAudio + 1
                                                    }
                                                }
                                                portsAudio.add(portAudio.toString())
                                                Host.OperatorPort = portAudio.toString()
                                                allocatePort(hostName.toString(), portAudio.toString(), hostAdd.toString())
                                                selfAdded = true
                                            }
                                        }, (1000..3000).random().toLong())
                                    }else if (operators.size < portsAudio.size && selfAdded){
                                        for(i in 0 until portsAudio.size) {
                                            removePort(portsInUse[i])
                                        }
                                    }
                                }
                                opDetected = true

                                // If no operators are on server except self
                                timeOutOp = false
                            }
                        }
                    }
                }
            }
            //endregion

            //region SendStringThread: SEND COORDINATES OVER MULTICAST SERVER
            class SendStringThread: Runnable {
                override fun run() {
                    while (true) {
                        // Obtain GPS data from Hyper IMU
                        val myData = getData()

                        // Time since joined server
                        Host.activeTime += 1
                        println("[Line: ${LineNumberGetter()}] Host: Time-${Host.activeTime} portsAudio: $portsAudio addresses: $addresses operators: $operators")

                        // Initialize values and send coordinates
                        val time = Date().toString()
                        val messageTo = "OP-$hostName IP: $hostAdd PORT_AUDIO: $portAudio COORDs: $myData-- "
                        val mes1 = (messageTo + time).toByteArray()
//                        //println("[Line: ${LineNumberGetter()}] $messageTo")
                        for(i in 0 until addresses.size) {
                            val request = DatagramPacket(
                                mes1,
                                mes1.size,
                                Inet4Address.getByName(addresses.elementAtOrNull(i)),
                                portString
                            )
                            Thread.sleep(1000)
                            socketString.send(request)
                        }
                        /** This statement will investigate each operator contained within operators.
                         * Will calculate the time the operator has been inactive.
                         * to determine whether or not the operator should be removed from sets.
                         * By doing so, will open up a port for Audio connection for new operators.
                         */
                        try {
                            for (key in operators.keys) {

                                if ((Host.activeTime - operators[key]!!.activeTime) - operators[key]!!.offset > 1 && operators[key]?.OperatorName != Host.OperatorName) {
                                    operators[key]!!.isActive = false
                                    portsAudio.remove(operators[key]!!.OperatorPort)
                                    addresses.remove(operators[key]?.OperatorIP)
                                    operators.remove(key)
                                    println("[Line: ${LineNumberGetter()}] PortsAudio: $portsAudio addresses: $addresses operators: $operators")
                                }
                                try {
                                    if (operators[key]?.OperatorName != Host.OperatorName) {
                                        println("[Line: ${LineNumberGetter()}] Op active? ${operators[key]?.isActive} Time Active: ${operators[key]?.activeTime} offset: ${(Host.activeTime - operators[key]?.activeTime!!.toInt()) - operators[key]!!.offset}")
                                    }
                                } catch (e: NullPointerException) {
                                    println("[Line: ${LineNumberGetter()}] $key has timed out! $key as been removed!")
                                }
                            }
                        } catch (e: ConcurrentModificationException){
                            println("[Line: ${LineNumberGetter()}] Caught Exception.")
                        }
                    }
                }

                fun getData(): List<Double> {
                    var azimuthData = arrayOf("", "", "", "", "", "")
                    var Longitude: Double
                    var Latitude: Double
                    var Nose: Double

                    val buffer2 = ByteArray(1024)
                    val packet = DatagramPacket(buffer2, buffer2.size)

                    //Listen for packet on port 9000

                    socket.receive(packet)

                    val message = packet.data
                    val dataString = String(message, 0, message.size)
                    val azimuthRegex = """-?(\d+)\.\d+""".toRegex()
                    val patt = azimuthRegex.findAll(dataString)
                    var i = 0

                    patt.forEach { f ->
                        azimuthData[i] = f.value
                        i++
                        if (i > 5) {
                            i = 0
                        }
                    }

                    try {
                        Longitude = azimuthData[4].toDouble()
                        Latitude = azimuthData[3].toDouble()
                        Nose = azimuthData[0].toDouble()

                        Host.OperatorLongitude = Longitude
                        Host.OperatorLatitude = Latitude
                        Host.OperatorNose = Nose
                    } catch (e: NumberFormatException){

                        println("[Line: ${LineNumberGetter()}] Caught NumberFormatException.")
                        println("[Line: ${LineNumberGetter()}] " + e.message)
                        Longitude = 0.0
                        Latitude = 0.0
                        Nose = 0.0
                    }
                    return listOf(Longitude, Latitude, Nose)
                }
            }
            //endregion

            //region RecStringThread: RECEIVE OPERATOR COORDINATES
            class RecStringThread: Runnable {
                override fun run() {
                    while (true) {
                        val buffer2 = ByteArray(1024)
                        val response2 = DatagramPacket(buffer2, 1024)
//                        println("[Line: ${LineNumberGetter()}] Point A: Waiting for response")
                        socketString.receive(response2)
//                        println("[Line: ${LineNumberGetter()}] Point B: Received response")

                        val data2 = response2.data
                        val dataString = String(data2, 0, data2.size)
                        println("[Line: ${LineNumberGetter()}] Printing received response: $dataString")

                        /** Variables used to store and recognize parsed data from received packets
                         * Variables will Regex:
                         *      operator IP, Name, Port and Coordinates
                         */
                        val opName = """(?<=OP-)\w+""".toRegex().find(dataString)?.value.toString()
                        val opIP =
                            """(?<=IP: )(\d+).(\d+).(\d+).(\d+)""".toRegex().find(dataString)?.value.toString()
                        val opPort = """(?<=PORT_AUDIO: )\d+""".toRegex().find(dataString)?.value.toString()
                        val opCoords = """-?(\d+)\.\d+""".toRegex()
                        val patt = opCoords.findAll(dataString)

                        //println("[Line: ${LineNumberGetter()}] $opName, $opIP, $opPort")
                        var i = 0
                        patt.forEach { f ->
                            opGPS[i] = f.value
                            i++
                        }

                        //Allocate received coordinates to correct operator
                        allocateCoords(opPort)

                        for (key in operators.keys) {
                            if (operators[key]?.OperatorName != hostName) {

                                println("[Line: ${LineNumberGetter()}] Printing Operator GPS data: Op: ${operators[key]?.OperatorName} Long: ${operators[key]?.OperatorLongitude} Lat: ${operators[key]?.OperatorLatitude}")

                                // Calculate Azimuth between self and operator
                                operators[key]?.OperatorAzimuth = AzimuthCalc(
                                    Host.OperatorLongitude,
                                    Host.OperatorLatitude,
                                    operators[key]!!.OperatorLongitude,
                                    operators[key]!!.OperatorLatitude,
                                    Host.OperatorNose
                                )

                                //Calculate distance between self and operator
                                operators[key]?.OperatorDistance = OperatorDistance(
                                    Host.OperatorLongitude,
                                    Host.OperatorLatitude,
                                    operators[key]!!.OperatorLongitude,
                                    operators[key]!!.OperatorLatitude
                                )

                                println("[Line: ${LineNumberGetter()}] $operators")
                                println("[Line: ${LineNumberGetter()}] ${operators[key]?.OperatorName}")
                                println("[Line: ${LineNumberGetter()}] Host:     ${Host.OperatorName} Nose: ${Host.OperatorNose} Port: ${Host.OperatorPort}")
                                println("[Line: ${LineNumberGetter()}] Operator: ${operators[key]?.OperatorName} Azimuth: ${operators[key]?.OperatorAzimuth}")
                                println("[Line: ${LineNumberGetter()}] Host      Longitude: ${Host.OperatorLongitude} Latitude: ${Host.OperatorLatitude}")
                                println("[Line: ${LineNumberGetter()}] Operator  Distance: ${operators[key]?.OperatorDistance} feet")
                                println("\n")
                            }
                        }

                        if (!portsAudio.contains(opPort)) {
                            if(!opDetected && !operators.containsKey(opPort)){
                                Timer().schedule(timerTask {
                                    opNotFound = true
                                }, 1000 * 5)
                            }
                        }
                        if (opNotFound == true){
                            portsAudio.add(opPort)
                            addresses.add(opIP)
                            allocatePort(opName, opPort, opIP)
                            opNotFound = false
                        }

                        operatorTimeOut(opName)
                    }
                }
                //region operatorTimeOut: Function
                fun operatorTimeOut(Name: String) {
                    for(key in operators.keys){
                        if (operators[key]!!.OperatorName == Name) {
                            if(!operators[key]!!.isActive) {
                                operators[key]?.isActive = true
                            }
                            operators[key]!!.activeTime += 1

                            operators[key]!!.offset = Host.activeTime - operators[key]!!.activeTime
                        }
                    }
                }
                //endregion
            }
            //endregion

            //region SendThread: RECEIVING COORDINATES
            class SendThread: Runnable {
                override fun run() {
                    while (true) {
//                        println("[Line: ${LineNumberGetter()}] Running...")
                        numBytesRead = mic.read(data, 0, buffer)
                        for (i in 0 until addresses.size) {
                            if (addresses.elementAtOrNull(i) != hostAdd) {
                                val request = DatagramPacket(
                                    data,
                                    numBytesRead,
                                    InetAddress.getByName(addresses.elementAtOrNull(i)),
                                    portsAudio.elementAtOrNull(i)!!.toInt()
                                )
                                if (voice_Chat == 1) {
                                    socketSendAudio.send(request)
                                } else {
                                    val request2 = DatagramPacket(ByteArray(0),
                                        0,
                                        InetAddress.getByName(addresses.elementAtOrNull(i)),
                                        portsAudio.elementAtOrNull(i)!!.toInt()
                                    )
                                    socketSendAudio.send(request2)
                                    Thread.sleep(1000)
                                }
                            }
                        }
                    }
                }
            }
            val thread3 = Thread(SendThread())
            //endregion

            //region PTTThread: ACTIVATES PTT ELSE SUSPENDS SendThread
            class PTTThread: Runnable {
                override fun run() {
                    while (true){
//                        println("$suspended $voice_Chat")
                        Thread.sleep(100)
                        if (suspended == false && voice_Chat == 0) {
                            println("[Line: ${LineNumberGetter()}] SendThread Suspended!")
                            thread3.suspend()
                            suspended = true
                        } else if (suspended == true && voice_Chat == 1){
                            println("[Line: ${LineNumberGetter()}] SendThread Resumed!")
                            thread3.resume()
                            suspended = false
                        }
                    }
                }
            }
            //endregion

            //region RecThread: ALL RECEIVING THREADS FOR AUDIO

            // Receiving OP-1 audio
            class RecThread: Runnable{
                override fun run(){
                    while (true) {
                        val bufferRec = ByteArray(1024)
                        val responseRec = DatagramPacket(bufferRec, bufferRec.size)
                        socketRecAudio.receive(responseRec)
                        out.write(responseRec.data, 0, responseRec.data.size)
                        output.write(responseRec.data, 0, responseRec.data.size)
                    }
                }
            }

            // Receiving OP-2 audio
            class RecThread2: Runnable{
                override fun run(){
                    while (true) {
                        val bufferRec = ByteArray(1024)
                        val responseRec = DatagramPacket(bufferRec, bufferRec.size)
                        socketRecAudio2.receive(responseRec)
                        out2.write(responseRec.data, 0, responseRec.data.size)
                        output2.write(responseRec.data, 0, responseRec.data.size)
                    }
                }
            }

            // Receiving OP-3 audio
            class RecThread3: Runnable{
                override fun run(){
                    while (true) {
                        val bufferRec = ByteArray(1024)
                        val responseRec = DatagramPacket(bufferRec, bufferRec.size)
                        socketRecAudio3.receive(responseRec)
                        out3.write(responseRec.data, 0, responseRec.data.size)
                        output3.write(responseRec.data, 0, responseRec.data.size)
                    }
                }
            }

            // Receiving OP-4 audio
            class RecThread4: Runnable {
                override fun run() {
                    while (true) {
                        val bufferRec = ByteArray(1024)
                        val responseRec = DatagramPacket(bufferRec, bufferRec.size)
                        socketRecAudio4.receive(responseRec)
                        out4.write(responseRec.data, 0, responseRec.data.size)
                        output4.write(responseRec.data, 0, responseRec.data.size)
                    }
                }
            }

            // Receiving OP-5 audio
            class RecThread5: Runnable {
                override fun run() {
                    while (true) {
                        val bufferRec = ByteArray(1024)
                        val responseRec = DatagramPacket(bufferRec, bufferRec.size)
                        socketRecAudio5.receive(responseRec)
                        out5.write(responseRec.data, 0, responseRec.data.size)
                        output5.write(responseRec.data, 0, responseRec.data.size)
                    }
                }
            }

            // Receiving OP-6 audio
            class RecThread6: Runnable {
                override fun run() {
                    while (true) {
                        val bufferRec = ByteArray(1024)
                        val responseRec = DatagramPacket(bufferRec, bufferRec.size)
                        socketRecAudio6.receive(responseRec)
                        out6.write(responseRec.data, 0, responseRec.data.size)
                        output6.write(responseRec.data, 0, responseRec.data.size)
                    }
                }
            }

            // Receiving OP-7 audio
            class RecThread7: Runnable {
                override fun run() {
                    while (true) {
                        val bufferRec = ByteArray(1024)
                        val responseRec = DatagramPacket(bufferRec, bufferRec.size)
                        socketRecAudio7.receive(responseRec)
                        out7.write(responseRec.data, 0, responseRec.data.size)
                        output7.write(responseRec.data, 0, responseRec.data.size)
                    }
                }
            }

            // Receiving OP-8 audio
            class RecThread8: Runnable {
                override fun run() {
                    while (true) {
                        val bufferRec = ByteArray(1024)
                        val responseRec = DatagramPacket(bufferRec, bufferRec.size)
                        socketRecAudio8.receive(responseRec)
                        out8.write(responseRec.data, 0, responseRec.data.size)
                        output8.write(responseRec.data, 0, responseRec.data.size)
                    }
                }
            }
            //endregion

            //region RUNNING THREADS
            val thread1 = Thread(SendStringThread())
            val thread2 = Thread(RecStringThread())
            val thread4 = Thread(RecThread())
            val thread5 = Thread(RecThread2())
            val thread6 = Thread(RecThread3())
            val thread7 = Thread(RecThread4())
            val thread8 = Thread(RecThread5())
            val thread9 = Thread(RecThread6())
            val thread10 = Thread(RecThread7())
            val thread11 = Thread(RecThread8())
            val thread12 = Thread(ConnectThread())
            val thread13 = Thread(ConnectRecThread())
            val thread14 = Thread(PTTThread())
            thread1.start()
            thread2.start()
            thread3.start()
            thread4.start()
            thread5.start()
            thread6.start()
            thread7.start()
            thread8.start()
            thread9.start()
            thread10.start()
            thread11.start()
            thread12.start()
            thread13.start()
            thread14.start()


            //endregion
            //endregion

        } catch (e: SocketTimeoutException) {       // Timeout error
            println("[Line: ${LineNumberGetter()}] Timeout error: " + e.message)
            e.printStackTrace()
        } catch (e: IOException){       // I/O error
            println("[Line: ${LineNumberGetter()}] Client error: " + e.message)
            e.printStackTrace()
        } catch (e: ConcurrentModificationException){
            println("[Line: ${LineNumberGetter()}] Client error: " + e.message)
            e.printStackTrace()
        }
    }

    //region FUNCTIONS
    //region removePort: FUNCTION
    fun removePort(Port: String){
        println(Port)
        when(Port.toInt()) {
            6011 -> {
                if (!operators.containsKey("OP1")) {
                    portsAudio.remove(Port)
                }
            }
            6012 -> {
                if (!operators.containsKey("OP2")) {
                    portsAudio.remove(Port)
                }
            }
            6013 -> {
                if (!operators.containsKey("OP3")) {
                    portsAudio.remove(Port)
                }
            }
            6014 -> {
                if (!operators.containsKey("OP4")) {
                    portsAudio.remove(Port)
                }
            }
            6015 -> {
                if (!operators.containsKey("OP5")) {
                    portsAudio.remove(Port)
                }
            }
            6016 -> {
                if (!operators.containsKey("OP6")) {
                    portsAudio.remove(Port)
                }
            }
            6017 -> {
                if (!operators.containsKey("OP7")) {
                    portsAudio.remove(Port)
                }
            }
            6018 -> {
                if (!operators.containsKey("OP8")) {
                    portsAudio.remove(Port)
                }
            }
        }
    }
    //endregion

    //region allocateCoords: FUNCTION
    fun allocateCoords(Port: String) {
        when (Port.toInt()) {
            6011 -> {
                operators["OP1"]?.OperatorLongitude = opGPS[2].toDouble()
                operators["OP1"]?.OperatorLatitude = opGPS[3].toDouble()
            }

            6012 -> {
                operators["OP2"]?.OperatorLongitude = opGPS[2].toDouble()
                operators["OP2"]?.OperatorLatitude = opGPS[3].toDouble()
            }

            6013 -> {
                operators["OP3"]?.OperatorLongitude = opGPS[2].toDouble()
                operators["OP3"]?.OperatorLatitude = opGPS[3].toDouble()
            }

            6014 -> {
                operators["OP4"]?.OperatorLongitude = opGPS[2].toDouble()
                operators["OP4"]?.OperatorLatitude = opGPS[3].toDouble()
            }

            6015 -> {
                operators["OP5"]?.OperatorLongitude = opGPS[2].toDouble()
                operators["OP5"]?.OperatorLatitude = opGPS[3].toDouble()
            }

            6016 -> {
                operators["OP6"]?.OperatorLongitude = opGPS[2].toDouble()
                operators["OP6"]?.OperatorLatitude = opGPS[3].toDouble()
            }

            6017 -> {
                operators["OP7"]?.OperatorLongitude = opGPS[2].toDouble()
                operators["OP7"]?.OperatorLatitude = opGPS[3].toDouble()
            }

            6018 -> {
                operators["OP8"]?.OperatorLongitude = opGPS[2].toDouble()
                operators["OP8"]?.OperatorLatitude = opGPS[3].toDouble()
            }
        }
    }
    //endregion

    //region allocatePort: FUNCTION
    fun allocatePort(Name: String, Port: String, IP: String){
        when (Port.toInt()) {
            6011 -> {operators["OP1"] = opInfo(OperatorName = Name)
                operators["OP1"]?.OperatorPort = Port
                operators["OP1"]?.OperatorIP = IP}

            6012 -> {operators["OP2"] = opInfo(OperatorName = Name)
                operators["OP2"]?.OperatorPort = Port
                operators["OP2"]?.OperatorIP = IP
            }

            6013 -> {operators["OP3"] = opInfo(OperatorName = Name)
                operators["OP3"]?.OperatorPort =Port
                operators["OP3"]?.OperatorIP = IP}

            6014 -> {operators["OP4"] = opInfo(OperatorName = Name)
                operators["OP4"]?.OperatorPort = Port
                operators["OP4"]?.OperatorIP = IP}

            6015 -> {operators["OP5"] = opInfo(OperatorName = Name)
                operators["OP5"]?.OperatorPort = Port
                operators["OP5"]?.OperatorIP = IP}

            6016 -> {operators["OP6"] = opInfo(OperatorName = Name)
                operators["OP6"]?.OperatorPort = Port
                operators["OP6"]?.OperatorIP = IP}

            6017 -> {operators["OP7"] = opInfo(OperatorName = Name)
                operators["OP7"]?.OperatorPort = Port
                operators["OP7"]?.OperatorIP = IP}

            6018 -> {operators["OP8"] = opInfo(OperatorName = Name)
                operators["OP8"]?.OperatorPort = Port
                operators["OP8"]?.OperatorIP = IP}
        }
    }
    //endregion

    //region AzimuthCalc: FUNCTION
    fun AzimuthCalc(myLongitude: Double, myLatitude: Double, opLongitude: Double, opLatitude: Double, Nose: Double): Double {
        val endLongitude: Double = Math.toRadians(opLongitude - myLongitude)
        val endLatitude: Double = Math.toRadians(opLatitude)
        val startLatitude = Math.toRadians(myLatitude)
        var phi = Math.toDegrees(atan2(sin(endLongitude) * cos(endLatitude), cos(startLatitude) * sin(endLatitude) - sin(startLatitude) * cos(endLatitude) * cos(endLongitude)))

        (phi + 360) % 360

        if(Nose < phi){
            phi = phi - Nose
        } else if(Nose > phi && ((Nose - phi) < 180)){
            phi = Nose - phi
        } else if (Nose > phi && ((Nose - phi) > 180)){
            phi = 360 - (Nose - phi)
        }

        return(phi)
    }
    //endregion

    //region OperatorDistance: FUNCTION
    fun OperatorDistance(myLongitude: Double, myLatitude: Double, opLongitude: Double, opLatitude: Double): Double{
        val dLat: Double = Math.toRadians(opLatitude - myLatitude)
        val dLong: Double = Math.toRadians(opLongitude - myLongitude)
        val a = Math.pow(Math.sin(dLat/2), 2.0) + Math.cos(myLatitude) * Math.cos(opLatitude) * Math.pow(Math.sin(dLong/2), 2.0)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        val R = 6300000
        val feet = ((R * c) * 100)/(2.54 * 12)
//                    println(R * c)
        return (feet)
    }
    //endregion
//endregion

    //region LineNumberGetter: FUNCTION
    fun LineNumberGetter(): Int{
        return __thisIsMyLineNumber()
    }

    private fun __thisIsMyLineNumber(): Int{
        val elements = Thread.currentThread().stackTrace
        var Detected = false
        var thisLine = false

        for (element in elements){
            val elementName = element.methodName
            val line = element.lineNumber

            if (Detected && thisLine){
                return line
            } else if(Detected){
                thisLine = true
            }
            if (elementName == "__thisIsMyLineNumber"){
                Detected = true
            }
        }
        return -1
    }
//endregion
}

