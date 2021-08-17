//TODO:
// - Make connection non-reliant on every computer being present (DONE)
// - Integrate azimuth calculations using coordinates (DONE)
// - Tie coordinate data to a specific operator (DONE)
// - Figure out JavaSoundSampled panning tools (IN PROGRESS)

//region IMPORTS
/*lwjgl imports will require OpenAl64.dll and lwjgl64.dll to be added
 * to SDK bin directory (EX: Users\\<User Name>\\.jdks\\azul-15.0.3\\bin)
 *
 * These .dll files can be found in lwhjgl-2.9.3\\native\\windows folder
 */


import org.lwjgl.openal.AL
import java.io.IOException
import SpatialAudio.SpatialAudioFun as SpatialAudio

//endregion

object Operator{
    @Throws(IOException::class)
    @JvmStatic
    fun main(args: Array<String>) {
        AL.create()
        val Host = SpatialAudio.getHost()
        try {

            //region MICROPHONE IO
            SpatialAudio.initMic()

            val portString = 8000
            SpatialAudio.initStringPort(portString)
            /* Hyper IMU port and socket creation
             * Note:
             *      hyperIMUport must equal to the port set within the Hyper IMU app
             */
            val HyperIMUPort = 9000
            SpatialAudio.initHyperIMU(HyperIMUPort)

            // Handles the creation of the multicast network.
            val portConnect = 8010
            SpatialAudio.initMulticast("230.0.0.0", portConnect)

            //Handles PTT initialization and listens for the spefied key
            SpatialAudio.initPTT('t')

            //region THREADS: Contains all running threads.

            /**
             * This THREAD's primary purpose is to send requests over the multicast network to other
             * connected operators. This will allow operators to add self to their DATABASE.
             */
            //region ConnectThread: SEND OP REQUESTS OVER MULTICAST SERVER
            class ConnectThread: Runnable {
                override fun run() {
                    while (true) {
                        Thread.sleep(1000)
                        SpatialAudio.sendRequest(Host, portConnect)
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

                        SpatialAudio.receiveOP(Host)

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
                        SpatialAudio.sendData(Host, portString)
                    }
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
                        SpatialAudio.receiveData(Host)
                    }
                }
            }

            /**
             * This THREAD's primary purpose is to send own microphone audio over the multicast
             * network to other connected operators.
             */
            //region SendThread: Send TARGETDATALINE audio
            class SendThread: Runnable {
                override fun run() {
                    while (true) {
                        SpatialAudio.sendAudio()
                    }
                }
            }
            val threadSendThread = Thread(SendThread())
            //endregion

            /*
             * This THREAD's primary purpose is to enable push-to-talk and suspend/resume the send thread
             * to prevent sending microphone data when not pushed.
             */
            //region PTTThread: ACTIVATES PTT ELSE SUSPENDS SendThread
            class PTTThread: Runnable {
                override fun run() {
                    while (true){
                        SpatialAudio.suspendThread(threadSendThread)
                    }
                }
            }
            threadSendThread.start()
            //endregion

            /**
             * These THREAD's handle all received operators audio and processes it for Spatial Audio purposes.
             */
            //region RecThread: ALL RECEIVING THREADS FOR AUDIO
            // Receiving OP-1 audio
            class RecThread: Runnable{
                override fun run(){
                    SpatialAudio.recAudio("OP1")
                }
            }

            // Receiving OP-2 audio
            class RecThread2: Runnable{
                override fun run(){
                    SpatialAudio.recAudio("OP2")
                }
            }

            // Receiving OP-3 audio
            class RecThread3: Runnable{
                override fun run(){
                    SpatialAudio.recAudio("OP3")
                }
            }

            // Receiving OP-4 audio
            class RecThread4: Runnable {
                override fun run() {
                    SpatialAudio.recAudio("OP4")
                }
            }

            // Receiving OP-5 audio
            class RecThread5: Runnable {
                override fun run() {
                    SpatialAudio.recAudio("OP5")
                }
            }

            // Receiving OP-6 audio
            class RecThread6: Runnable {
                override fun run() {
                    SpatialAudio.recAudio("OP6")
                }
            }

            // Receiving OP-7 audio
            class RecThread7: Runnable {
                override fun run() {
                    SpatialAudio.recAudio("OP7")
                }
            }

            // Receiving OP-8 audio
            class RecThread8: Runnable {
                override fun run() {
                    SpatialAudio.recAudio("OP8")
                }
            }
            //endregion

            //region RUNNING THREADS: All current running threads
            val Threads = listOf<Thread>(Thread(SendStringThread()),
                Thread(RecStringThread()),
                Thread(RecThread()),
                Thread(RecThread2()),
                Thread(RecThread3()),
                Thread(RecThread4()),
                Thread(RecThread5()),
                Thread(RecThread6()),
                Thread(RecThread7()),
                Thread(RecThread8()),
                Thread(ConnectThread()),
                Thread(ConnectRecThread()),
                Thread(PTTThread())
            )

            for (i in 0 until Threads.size) {
                Threads[i].start()
            }
            //endregion
            //endregion


        } catch (e: IOException){       // I/O error
            println("[Line: ${SpatialAudio.LineNumberGetter()}] Client error: " + e.message)
            e.printStackTrace()
        } catch (e: ConcurrentModificationException){
            println("[Line: ${SpatialAudio.LineNumberGetter()}] Client error: " + e.message)
            e.printStackTrace()
        }
    }
}