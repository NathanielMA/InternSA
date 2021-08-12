
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
import org.lwjgl.openal.AL10.*
import org.lwjgl.util.WaveData
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.io.*
import java.lang.IllegalStateException
import java.net.*
import java.util.*
import javax.sound.sampled.*
import javax.swing.JFrame
import javax.swing.JLabel
import javax.xml.transform.Source
import kotlin.concurrent.timerTask
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

//endregion

object Operator{
    /**Data class that stores operator information
     * Stores:
     *      IP
     *      Port
     *      Host Name
     *      Longitude, Latitude, Distancem Azimuth and Nose
     * Note:
     *      offset, activeTime, isAcvtive are used for dynamic port removal
     */
    //region OPERATORS DATACLASS
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
    var opNotFound = false                          // Boolean: Determines if operator is already contained
    val potentialOP = listOf<String>("OP1","OP2","OP3","OP4","OP5","OP6","OP7","OP8")
    var opGPS = mutableListOf<String>("","","","","")
    var suspended: Boolean = false
    val format = AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
        44100F,
        16,
        2,
        4,
        44100F,
        true)

    val outDataBuffer = listOf<ByteArrayOutputStream>(ByteArrayOutputStream(),
        ByteArrayOutputStream(),
        ByteArrayOutputStream(),
        ByteArrayOutputStream(),
        ByteArrayOutputStream(),
        ByteArrayOutputStream(),
        ByteArrayOutputStream(),
        ByteArrayOutputStream())

    val socketRecAudio = listOf<DatagramSocket>(DatagramSocket(6011),
        DatagramSocket(6012),
        DatagramSocket(6013),
        DatagramSocket(6014),
        DatagramSocket(6015),
        DatagramSocket(6016),
        DatagramSocket(6017),
        DatagramSocket(6018))

    var buffer3D: Int = 0
    var incPort: Int = 6011

    //endregion

    @Throws(IOException::class)
    @JvmStatic
    fun main(args: Array<String>) {
        AL.create()

        try {
            /**
             * Contains all initializations.
             */
            //region INITIALIZATION

            /**
             * Sets the TARGETDATALINE for use in audio recording to be sent to connected operators.
             */
            //region MICROPHONE IO
            // Initialize values
            var numBytesRead: Int
            val buffer = 1024

            // Initialize input (microphone)
            var mic: TargetDataLine
            mic = AudioSystem.getTargetDataLine(format)
            val info = DataLine.Info(TargetDataLine::class.java, format)
            mic = AudioSystem.getLine(info) as TargetDataLine
            mic.open(format)
            val data = ByteArray(mic.bufferSize / 5)
            mic.start()

            //endregion
            /**
             * Handles own Host Name and IP credentials.
             */
            //region HOST: ADDRESS & PORT CONFIG
            val findIP = """(\d+)\.(\d+)\.(\d+)\.(\d+)""".toRegex()                         // Locates Host IP octet
            val findName = """([A-Z])\w+""".toRegex()                                       // Locates Host Name
            val hostName = findName.find(Inet4Address.getLocalHost().toString())?.value     // Host Name
            val hostAdd = findIP.find(Inet4Address.getLocalHost().toString())?.value        // Host address
            val Host = opInfo(hostName.toString())                                          // Set Host
            Host.OperatorIP = hostAdd.toString()                                            // Set Host IP
            //endregion

            /**
             * Handles the initial audio port to be used. Also initializes the audio DATAGRAMSOCKET
             * to be used for sending audio over the multicast network.
             */
            //region AUDIO SOCKET CREATION
            var portAudio = 6011
            val socketSendAudio = DatagramSocket()
            //endregion

            /**
             * Handles the socket creation for sending audio over the multicast network
             */
            //region STRING SOCKET CREATIONS
            val portString = 8000     // String port for coordinates
            val socketString = DatagramSocket(portString)
            //endregion

            /** Hyper IMU port and socket creation
             * Note:
             *      hyperIMUport must equal to the port set within the Hyper IMU app
             */
            //region HYPER IMU
            val hyperIMUport = 9000
            val socket = DatagramSocket(hyperIMUport)
            //endregion

            /**
             * Handles the creation of the multicast network.
             */
            //region MULTICAST SERVER CREATION
            val portConnect = 8010
            val socketConnect = MulticastSocket(portConnect)
            socketConnect.joinGroup(InetSocketAddress("230.0.0.0",portConnect),null)
            //endregion

            /** Note:
             *      PTT won't work without the window being visible.
             *      Setting frame.isVisible = false will deactivate PTT functionality
             *      In the final version, after integration with ATAK or Android, JFrame will not be used.
             */
            //region PUSH-TO-TALK
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

            /**
             * Contains all running threads.
             */
            //region THREADS

            /**
             * This THREAD's primary purpose is to send requests over the multicast network to other
             * connected operators. This will allow operators to add self to their DATABASE.
             */
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

            /**
             * This THREAD's primary purpose to receive operators connected to the multicast network
             * and allocate the operators to their repective audio ports.
             */
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

            /**
             * This THREAD's primary purpose is to send OWN GPS data over the multicast network to other connected
             * operators for use in their azimuth calculations.
             *
             * This THREAD will also determine when an operator disconnects based on the time since their last
             * response. Each response is sent/received every 1 second.
             */
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

            /**
             * This THREAD's primary purpose is to receive operators GPS data and allocate it to their respective
             * DATA CLASS
             */
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

            /**
             * This THREAD's primary purpose is to send own microphone audio over the multicast
             * network to other connected operators.
             */
            //region SendThread: Send TARGETDATALINE audio
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
                                }
                            }
                        }
                    }
                }
            }
            val thread3 = Thread(SendThread())
            //endregion

            /**
             * This THREAD's primary purpose is to enable push-to-talk and suspend/resume the send thread
             * to prevent sending microphone data when not pushed.
             */
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

            /**
             * These THREAD's handle all received operators audio and processes it for Spatial Audio purposes.
             */
            //region RecThread: ALL RECEIVING THREADS FOR AUDIO
            // Receiving OP-1 audio
            class RecThread: Runnable{
                override fun run(){
                    recAudio("OP1")
                }
            }

            // Receiving OP-2 audio
            class RecThread2: Runnable{
                override fun run(){
                    recAudio("OP2")
                }
            }

            // Receiving OP-3 audio
            class RecThread3: Runnable{
                override fun run(){
                    recAudio("OP3")
                }
            }

            // Receiving OP-4 audio
            class RecThread4: Runnable {
                override fun run() {
                    recAudio("OP4")
                }
            }

            // Receiving OP-5 audio
            class RecThread5: Runnable {
                override fun run() {
                    recAudio("OP5")
                }
            }

            // Receiving OP-6 audio
            class RecThread6: Runnable {
                override fun run() {
                    recAudio("OP6")
                }
            }

            // Receiving OP-7 audio
            class RecThread7: Runnable {
                override fun run() {
                    recAudio("OP7")
                }
            }

            // Receiving OP-8 audio
            class RecThread8: Runnable {
                override fun run() {
                    recAudio("OP8")
                }
            }
            //endregion

            /**
             * These are all current running threads.
             */
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


        } catch (e: IOException){       // I/O error
            println("[Line: ${LineNumberGetter()}] Client error: " + e.message)
            e.printStackTrace()
        } catch (e: ConcurrentModificationException){
            println("[Line: ${LineNumberGetter()}] Client error: " + e.message)
            e.printStackTrace()
        }
    }

    //region FUNCTIONS

    /**
     * This FUNCTION primary use is to handle received audio for processing
     */
    //region recAudio: FUNCTION
    fun recAudio(operator: String){
        /**
         * Set buffer and initialize a type of Datagram packet to receive
         */
        val bufferRec = ByteArray(1024)
        val responseRec = DatagramPacket(bufferRec, bufferRec.size)

        /**
         * Create variables for use with creating a buffer to input into OpenAL.
         *
         * opRecording: Detect if audio is being received
         *
         * reset: Detect whether buffers for OpenAL have been flushed
         *
         * opSocket/opOutput: audio socket the operator is connected to and
         *          the ByteArrayOutputStream() the audio data is being recorded to
         *
         * startOutputSize: size of buffer that contains operator audio data
         */
        var opRecording: Boolean = false
        var reset: Int = 1
        var opSocket = DatagramSocket()
        var opOutput = ByteArrayOutputStream()
        var startOutputSize = opOutput.size()
        var call: Int = 0

        //Direct to the correct Port the operator is sending audio on and allocate Data to the correct buffer
        for (i in 0 until potentialOP.size){
            when (operator) {
                potentialOP[i] -> {
                    opSocket = socketRecAudio[i]
                    opOutput = outDataBuffer[i]
                }
            }
        }

        //Creates a timer for when to move past a .receive() call
        opSocket.setSoTimeout(250)

        while (true) {
            try {
                //Receive audio on connected port
                opSocket.receive(responseRec)

                //Write audio to specified ByteArrayOutputStream()
                opOutput.write(responseRec.data, 0, responseRec.data.size)

                //Assign current size of ByteArrayOutputStream() to a variable
                var currentOutputSize = opOutput.size()

                //Process audio whenever enough data has been generated
                if(currentOutputSize - startOutputSize > 1023){
                    try {
                        SpatialAudioFormat(opOutput, operators[operator]!!.OperatorAzimuth)
                    } catch (e: java.lang.NullPointerException){
                        if(call == 0) {
                            println("[Line: ${LineNumberGetter()}] Not receiving Azimuth data from $operator! Azimuth set to 0.0!")
                        }
                        call = 1
                        SpatialAudioFormat(opOutput, 0.0)
                    }

                    //Reset buffersize offset
                    startOutputSize = currentOutputSize

                    //Reset ByteArrayOutputStream() to allow for new data
                    //Prevents repeating of data
                    opOutput.reset()
                }
                opRecording = true
                reset = 0
            } catch (e: SocketTimeoutException){
                opRecording = false
            }

            //Clear all buffers
            if(opRecording == false && reset == 0) {
                reset = 1
                opOutput.reset()
                alDeleteBuffers(buffer3D)
            }
        }
    }
    //endregion

    /**
     * This FUNCTION takes input data from the designated ByteArrayOutputStream() and the Azimuth data contained
     * within the operators class. This FUNCTION will, in turn, process the audio with respect to the azimuth to that
     * operator. Outputting the audio from their respective direction.
     */
    //region SpatialAudioFormat: FUNCTION
    fun SpatialAudioFormat(audioDataOutput: ByteArrayOutputStream, azimuth: Double){

        //Calulate the operators position in space based on azimuth
        var x: Float = Math.cos(Math.toRadians(azimuth)).toFloat()
        var y: Float = Math.sin(Math.toRadians(azimuth)).toFloat()
        val Quad1: Double = 0.0
        val Quad2: Double = 90.0
        val Quad3: Double = 180.0
        val Quad4: Double = 270.0

        //Get frameSize from the current audio format
        val frameSizeInBytes = format.frameSize
        buffer3D = alGenBuffers()

        //Convert designated ByteArrayOutputStream() into a ByteArrayInputStream()
        var bais = ByteArrayInputStream(audioDataOutput.toByteArray())

        //Utilize the newly created input stream and generate an AudioInputStream()
        var audioInputStream = AudioInputStream(bais, format, (audioDataOutput.toByteArray().size / frameSizeInBytes).toLong())

        //Use WaveData to generate the correct format for Spatial Audio purposes
        val sound = WaveData.create(audioInputStream)

        //Assign data from WaveData into a buffer for augmentation
        alBufferData(buffer3D, AL_FORMAT_MONO16, sound.data, sound.samplerate*2)

        //Dispose of WaveData information as it is no longer required
        sound.dispose()
        val source = alGenSources()

        alSourcei(source,AL_BUFFER,buffer3D)
        alSourcef(source,AL_GAIN,1f)

        //Set initial listener orientation, position and orientation
        if(azimuth >= Quad1 && azimuth <= Quad2){
            alSource3f(source, AL_POSITION, y*2, 0f, x)
        } else if (azimuth > Quad2 && azimuth < Quad3){
            alSource3f(source, AL_POSITION, y*2, 0f, x*2)
        } else if (azimuth >= Quad3 && azimuth < Quad4){
            alSource3f(source, AL_POSITION, y*2, 0f, x*2)
        } else if (azimuth >= Quad4){
            alSource3f(source, AL_POSITION, y*2, 0f, x)
        }

        //Set Position and Orientation of self
        alListener3f(AL_POSITION, 0f,0f,0f)
        alListener3f(AL_ORIENTATION, 0f,0f,0f)

        //Play received audio in buffer
        alSourcePlay(source)

        //Allow for the continuous change in operator position due to operator movement
        while(true) {
            if (alGetSourcei(source, AL_SOURCE_STATE) == AL_PLAYING) {
                if(azimuth >= Quad1 && azimuth <= Quad2){
                    alSource3f(source, AL_POSITION, y*2, 0f, x)
                } else if (azimuth > Quad2 && azimuth < Quad3){
                    alSource3f(source, AL_POSITION, y*2, 0f, x*2)
                } else if (azimuth >= Quad3 && azimuth < Quad4){
                    alSource3f(source, AL_POSITION, y*2, 0f, x*2)
                } else if (azimuth >= Quad4){
                    alSource3f(source, AL_POSITION, y*2, 0f, x)
                }
            } else break
        }
    }
    //endregion

    /**
     * This FUNCTION will remove an operator and disassociate them from their port if their
     * connection is interrupted and disconnect.
     *
     * FUNCTION CALL: ConnectRecThread()
     */
    //region removePort: FUNCTION
    fun removePort(Port: String){
        for (i in 0 until potentialOP.size) {
            when (Port.toInt()) {
                incPort + i -> {
                    if (!operators.containsKey(potentialOP[i])) {
                        portsAudio.remove(Port)
                    }
                }
            }
        }
    }
    //endregion

    /**
     * This FUNCTION will assign each operator their unique Longitude and Latitude data
     * based upon their coordinates sent via Hyper IMU.
     *
     * FUNCTION CALL: RecStringThread()
     */
    //region allocateCoords: FUNCTION
    fun allocateCoords(Port: String) {
        for (i in 0 until potentialOP.size){
            when (Port.toInt()) {
                incPort + i -> {
                    operators[potentialOP[i]]?.OperatorLongitude = opGPS[2].toDouble()
                    operators[potentialOP[i]]?.OperatorLatitude = opGPS[3].toDouble()
                }
            }
        }
    }
    //endregion

    /**
     * This FUNCTION will allocate an operators Name, Port and IP appropriately according to their received data.
     *
     * FUNCTION CALL: ConnectThread(), ConnectRecThread(), RecStringThread()
     */
    //region allocatePort: FUNCTION
    fun allocatePort(Name: String, Port: String, IP: String){
        for(i in 0 until potentialOP.size) {
            when (Port.toInt()) {
                incPort + i -> {
                    operators[potentialOP[i]] = opInfo(OperatorName = Name)
                    operators[potentialOP[i]]?.OperatorPort = Port
                    operators[potentialOP[i]]?.OperatorIP = IP
                }
            }
        }
    }
    //endregion

    /**
     * This FUNCTION will utilize the GPS data held in the operators DATA CLASS to calculate their azimuth.
     * The azimuth is taken with respect to self and altered based on direction self is facing.
     *
     * FUNCTION CALL: RecStringThread()
     */
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

    /**
     * This FUNCTION will utilize the GPS data held in the operators DATA CLASS to calculate their distance. The
     * distance is relative to self.
     *
     * FUNCTION CALL: RecStringThread()
     */
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

    /**
     * This FUNCTION returns the line number that it is called in.
     */
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

//endregion
}

